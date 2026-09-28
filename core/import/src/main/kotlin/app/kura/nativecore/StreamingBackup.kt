package app.kura.nativecore

import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.nio.CharBuffer
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import org.json.JSONObject

/** v4 envelopes remain byte-format compatible. No unauthenticated plaintext is parsed. */
object StreamingBackup {
    const val MAX_EXPANSION = 100L * 1024 * 1024
    const val MAX_JSON = 8 * 1024 * 1024
    private const val MAX_CIPHER = 110L * 1024 * 1024
    fun write(payload: BackupPayload, output: OutputStream, password: CharArray) {
        BackupCodec.validate(payload.data)
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val key = BackupCodec.derive(password, salt)
        try {
            output.write(("argon2:" + Envelope.encode(salt) + ":" + Envelope.encode(iv) + ":").toByteArray(Charsets.US_ASCII))
            StreamingCrypto.encrypt(Base64Streams.encode(output), key, iv).use { encrypted ->
                ZipOutputStream(encrypted).use { zip ->
                    var total = 0L
                    fun entry(name: String, bytes: ByteArray) {
                        total += bytes.size; require(total <= MAX_EXPANSION)
                        zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                    }
                    val json = payload.data.toString().toByteArray()
                    try { require(json.size <= MAX_JSON); entry("data.json", json) } finally { json.fill(0) }
                    for(name in payload.imageNames) {
                        BackupCodec.safeImageName(name)
                        payload.consumeImage(name) { require(it.size <= 10 * 1024 * 1024); entry("images/" + name, it) }
                    }
                }
            }
        } finally { key.fill(0) }
    }
    fun read(source: InputStream, password: CharArray, scratch: File): BackupPayload {
        var rawBytes = 0L
        val boundedSource = object : InputStream() {
            override fun read(): Int {
                val value = source.read()
                if(value >= 0) { rawBytes++; require(rawBytes <= 150L * 1024 * 1024) { "Encrypted backup exceeds size budget" } }
                return value
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = source.read(b, off, len)
                if(n > 0) { rawBytes += n; require(rawBytes <= 150L * 1024 * 1024) { "Encrypted backup exceeds size budget" } }
                return n
            }
            override fun close() = source.close()
        }

        val input = PushbackInputStream(BufferedInputStream(boundedSource), 256)
        val consumed = ByteArrayOutputStream()
        fun token(): String? {
            val value = StringBuilder()
            repeat(65) {
                val n = input.read()
                if(n < 0) return null
                consumed.write(n)
                if(n == 58) return value.toString()
                if(n !in 32..126) return null
                value.append(n.toChar())
            }
            return null
        }
        val first = token()
        var iv: ByteArray
        val key: ByteArray
        val encrypted: InputStream
        if(first == "argon2") {
            val salt = Envelope.decode(token() ?: error("Missing backup salt"))
            iv = Envelope.decode(token() ?: error("Missing backup IV")); require(iv.size == 12)
            key = BackupCodec.derive(password, salt)
            encrypted = Base64Streams.decode(input)
        } else if(first != null && runCatching { Envelope.decode(first).size == 16 }.getOrDefault(false)) {
            val firstBytes = consumed.size()
            val second = token()
            if(second != null && runCatching { Envelope.decode(second).size in listOf(12, 16) }.getOrDefault(false)) {
                iv = Envelope.decode(second)
                key = BackupCodec.derive(password, Envelope.decode(first), false)
            } else {
                val prefix = consumed.toByteArray()
                input.unread(prefix, firstBytes, prefix.size - firstBytes)
                iv = Envelope.decode(first); key = legacyKey(password)
            }
            encrypted = Base64Streams.decode(input)
        } else {
            input.unread(consumed.toByteArray())
            iv = ByteArray(16)
            DataInputStream(input).readFully(iv)
            key = legacyKey(password); encrypted = input
        }
        val plain: EncryptedSpool
        try {
            val decrypted = if(iv.size == 12) StreamingCrypto.decrypt(encrypted, key, iv)
                else CipherInputStream(encrypted, Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                    init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                })
            // Drain through authentication before reading even one ZIP or JSON byte.
            plain = decrypted.use { EncryptedSpool.create(scratch, it, MAX_CIPHER) }
        } finally { key.fill(0); iv.fill(0) }
        val entries = linkedMapOf<String, EncryptedSpool>()
        try {
            plain.open().use { stream ->
                val sniff = PushbackInputStream(stream, 1)
                var firstByte = sniff.read()
                while(firstByte in listOf(9, 10, 13, 32)) firstByte = sniff.read()
                require(firstByte >= 0)
                sniff.unread(firstByte)
                if(firstByte == 123) {
                    val json = Envelope.readBounded(sniff, MAX_JSON)
                    try { return BackupPayload(JSONObject(json.toString(Charsets.UTF_8)), emptyMap()).also { BackupCodec.validate(it.data) } }
                    finally { json.fill(0) }
                }
                val zipSource = if(firstByte == 80) sniff else Base64Streams.decode(sniff)
                ZipInputStream(zipSource).use { zip ->
                    var remaining = MAX_EXPANSION
                    var count = 0
                    val seen = hashSetOf<String>()
                    while(true) {
                        val entry = zip.nextEntry ?: break
                        require(++count <= 4096)
                        val name = entry.name
                        require(name.length <= 512)
                        if(entry.isDirectory) {
                            require(name == "images/" && seen.add(name)); require(zip.read() == -1); continue
                        }
                        require(seen.add(name)) { "Duplicate backup path" }
                        if(name != "data.json") {
                            require(name.startsWith("images/")); BackupCodec.safeImageName(name.removePrefix("images/"))
                        }
                        val max = minOf(remaining, if(name == "data.json") MAX_JSON.toLong() else 10L * 1024 * 1024)
                        val spool = EncryptedSpool.create(scratch, zip, max)
                        entries[name] = spool; remaining -= spool.size
                        zip.closeEntry()
                    }
                }
            }
            val bytes = entries.getValue("data.json").open().use { Envelope.readBounded(it, MAX_JSON) }
            val data = try { JSONObject(bytes.toString(Charsets.UTF_8)) } finally { bytes.fill(0) }
            BackupCodec.validate(data)
            val names = entries.keys.filter { it != "data.json" }.map { it.removePrefix("images/") }.toSet()
            return BackupPayload(data, emptyMap(), names,
                { name -> entries.getValue("images/" + name).open().use { Envelope.readBounded(it, 10 * 1024 * 1024) } },
                { entries.values.forEach { it.close() } })
        } catch(e: Throwable) { entries.values.forEach { it.close() }; throw e }
        finally { plain.close() }
    }
    private fun legacyKey(password: CharArray): ByteArray {
        val utf8 = Charsets.UTF_8.encode(CharBuffer.wrap(password))
        val bytes = ByteArray(utf8.remaining()).also(utf8::get)
        var result = bytes + ":wallet_app_aes256_salt_v1".toByteArray()
        bytes.fill(0); if(utf8.hasArray()) utf8.array().fill(0)
        repeat(10000) { val next = MessageDigest.getInstance("SHA-256").digest(result); result.fill(0); result = next }
        return result
    }
}