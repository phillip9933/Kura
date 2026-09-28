package app.kura.nativecore

import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.util.encoders.Base64

object Envelope {
    fun encode(bytes: ByteArray): String = Base64.toBase64String(bytes)
    fun decode(value: String): ByteArray = Base64.decode(value)
    fun encrypt(bytes: ByteArray, key: ByteArray): String {
        require(key.size == 32)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(bytes)
        return encode(iv) + ":" + encode(encrypted)
    }
    fun decrypt(value: String, key: ByteArray): ByteArray {
        require(key.size == 32)
        val parts = value.split(':')
        require(parts.size == 2)
        val iv = decode(parts[0]); val data = decode(parts[1])
        val cipher = when (iv.size) {
            12 -> Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            }
            16 -> Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            }
            else -> error("Unsupported IV")
        }
        return cipher.doFinal(data)
    }
    fun readBounded(input: InputStream, limit: Int): ByteArray {
        require(limit >= 0)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(n <= limit - output.size()) { "Input exceeds allowed size" }
                output.write(buffer, 0, n)
            }
            return output.toByteArray()
        } finally { buffer.fill(0) }
    }
}

/** Names are preserved; callers commit only after confirmation. Never overwrites an existing asset. */
class EncryptedMediaStorage(private val root: File) {
    init { require(root.isDirectory || root.mkdirs()) }
    private fun file(name: String): File {
        require(name.isNotBlank() && name !in listOf(".", "..") && name.none { it == '/' || it == '\\' || it == ':' })
        return File(root, name).canonicalFile.also { require(it.parentFile == root.canonicalFile) }
    }
    fun write(name: String, bytes: ByteArray, key: SensitiveBytes): File {
        require(bytes.size <= 10 * 1024 * 1024)
        val target = file(name)
        check(!target.exists()) { "Asset already exists" }
        val encrypted = key.useBytes { Envelope.encrypt(bytes, it).toByteArray(Charsets.US_ASCII) }
        val atomic = AtomicFile(target)
        try { VerifiedAtomicWrite.write(atomic,encrypted) }
        finally { encrypted.fill(0) }
        return target
    }
    fun read(name: String, key: SensitiveBytes): ByteArray {
        val encoded = file(name).inputStream().use { Envelope.readBounded(it, 16 * 1024 * 1024) }
        if (!name.endsWith(".enc")) return encoded // Read-only compatibility for media already retained by earlier native prototypes.
        return try { key.useBytes { Envelope.decrypt(encoded.toString(Charsets.US_ASCII), it) } }
        finally { encoded.fill(0) }
    }
    fun validateImage(bytes: ByteArray) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000) { "Invalid or oversized image" }
    }
}