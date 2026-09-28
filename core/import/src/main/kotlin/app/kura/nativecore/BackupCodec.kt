package app.kura.nativecore

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.digests.SHA256Digest
import org.json.JSONObject
import java.io.*
import java.nio.CharBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupPayload(
    val data: JSONObject,
    val images: Map<String, ByteArray>,
    private val lazyNames: Set<String> = emptySet(),
    private val loadImage: ((String) -> ByteArray)? = null,
    private val cleanup: () -> Unit = {}
) : AutoCloseable {
    val imageNames: Set<String> get() = images.keys + lazyNames
    fun <T> consumeImage(name: String, block: (ByteArray) -> T): T {
        images[name]?.let { return block(it) }
        require(name in lazyNames)
        val bytes = loadImage!!(name)
        try { return block(bytes) } finally { bytes.fill(0) }
    }
    /** Old Flutter archives removed every .enc occurrence, while records kept absolute paths. */
    internal fun imageEntry(path: String): String? {
        val filename = path.replace('\\', '/').substringAfterLast('/')
        val plain = filename.removeSuffix(".enc")
        return listOf(plain, plain + ".enc", filename.replace(".enc", ""))
            .firstOrNull { it in imageNames }
    }
    override fun close() { images.values.forEach { it.fill(0) }; cleanup() }
}
object BackupCodec {
    const val MAX_BYTES = 32 * 1024 * 1024
    internal fun requireArgonHeap(maxHeap: Long) {
        require(maxHeap >= 160L * 1024 * 1024) { "This device does not have enough memory to process an Argon2 backup." }
    }
    private val kdfLock = Any()
    fun derive(password: CharArray, salt: ByteArray, argon: Boolean = true): ByteArray = synchronized(kdfLock) {
        require(salt.size == 16)
        if(argon) requireArgonHeap(Runtime.getRuntime().maxMemory())
        val encoded = Charsets.UTF_8.encode(CharBuffer.wrap(password))
        val bytes = ByteArray(encoded.remaining()).also(encoded::get)
        val output = ByteArray(32)
        try {
            if (argon) {
                val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13).withSalt(salt)
                    .withMemoryAsKB(131072).withIterations(3).withParallelism(4).build()
                try { Argon2BytesGenerator().apply { init(parameters) }.generateBytes(bytes, output) }
                finally { parameters.clear() }
            } else {
                val generator = PKCS5S2ParametersGenerator(SHA256Digest())
                generator.init(bytes, salt, 600000)
                val derived = (generator.generateDerivedParameters(256) as KeyParameter).key
                derived.copyInto(output); derived.fill(0)
            }
            output
        } catch (e: Throwable) { output.fill(0); if(e is OutOfMemoryError) throw IllegalStateException("Not enough memory to process this backup.",e); throw e }
        finally { bytes.fill(0); if (encoded.hasArray()) encoded.array().fill(0) }
    }
    fun write(payload: BackupPayload, password: CharArray): ByteArray {
        validate(payload.data)
        val stream = ByteArrayOutputStream()
        ZipOutputStream(stream).use { zip ->
            fun entry(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            val json = payload.data.toString().toByteArray()
            try { entry("data.json", json) } finally { json.fill(0) }
            var size = 0L
            for (name in payload.imageNames) { payload.consumeImage(name) { bytes ->
                safeImageName(name); size += bytes.size; require(size <= MAX_BYTES)
                entry("images/" + name, bytes)
            } }
        }
        val plain = stream.toByteArray()
        require(plain.size <= MAX_BYTES)
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val key = derive(password, salt)
        return try { ("argon2:" + Envelope.encode(salt) + ":" + Envelope.encrypt(plain, key)).toByteArray() }
        finally { key.fill(0); plain.fill(0) }
    }
    fun read(input: InputStream, password: CharArray): BackupPayload {
        val encoded = Envelope.readBounded(input, 48 * 1024 * 1024)
        var plain: ByteArray? = null
        try {
            val text = encoded.toString(Charsets.UTF_8)
            val argon = text.startsWith("argon2:")
            val parts = text.removePrefix("argon2:").split(':')
            val key: ByteArray
            val envelope: String
            if (parts.size == 3) {
                key = derive(password, Envelope.decode(parts[0]), argon)
                envelope = parts[1] + ":" + parts[2]
                if (argon) require(Envelope.decode(parts[1]).size == 12)
            } else {
                require(!argon)
                val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(password))
                var digest = ByteArray(buffer.remaining()).also(buffer::get) + ":wallet_app_aes256_salt_v1".toByteArray()
                if (buffer.hasArray()) buffer.array().fill(0)
                repeat(10000) {
                    val next = MessageDigest.getInstance("SHA-256").digest(digest); digest.fill(0); digest = next
                }
                key = digest
                envelope = if (parts.size == 2) text else {
                    require(encoded.size > 16)
                    Envelope.encode(encoded.copyOfRange(0, 16)) + ":" + Envelope.encode(encoded.copyOfRange(16, encoded.size))
                }
            }
            try { plain = Envelope.decrypt(envelope, key) } finally { key.fill(0) }
            return parsePlain(plain!!)
        } finally { encoded.fill(0); plain?.fill(0) }
    }
    private fun parsePlain(plain: ByteArray): BackupPayload {
        require(plain.size <= MAX_BYTES)
        if (plain.firstOrNull() == '{'.code.toByte()) return BackupPayload(JSONObject(plain.toString(Charsets.UTF_8)), emptyMap()).also { validate(it.data) }
        val zipped = if (plain.size >= 2 && plain[0] == 80.toByte() && plain[1] == 75.toByte()) plain else Envelope.decode(plain.toString(Charsets.US_ASCII))
        val entries = try { BoundedZip.read(zipped.inputStream(), ImportBudget(MAX_BYTES, 4096), MAX_BYTES) }
        finally { if (zipped !== plain) zipped.fill(0) }
        try {
            val data = JSONObject(entries.getValue("data.json").toString(Charsets.UTF_8))
            validate(data)
            val images = linkedMapOf<String, ByteArray>()
            for ((name, bytes) in entries) {
                if (name == "data.json") continue
                require(name.startsWith("images/"))
                safeImageName(name.removePrefix("images/"))
                images[name.removePrefix("images/")] = bytes.copyOf()
            }
            return BackupPayload(data, images)
        } finally { entries.values.forEach { it.fill(0) } }
    }
    fun safeImageName(name: String) { require(name.isNotBlank() && name.toByteArray().size <= 255 && name !in listOf(".", "..") && name.none { it == '/' || it == '\\' || it == ':' }) }
    fun validate(data: JSONObject) {
        require(data.optString("version").substringBefore('.') in listOf("1", "2", "3", "4"))
        for (name in listOf("wallets", "passes", "identities", "loyalties")) {
            if (!data.has(name)) continue
            val rows = data.getJSONArray(name); require(rows.length() <= 10000)
            for (i in 0 until rows.length()) require(rows.get(i) is JSONObject)
        }
        require(data.has("wallets") || data.has("passes") || data.has("identities") || data.has("loyalties"))
    }
}