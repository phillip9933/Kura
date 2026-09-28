package app.kura.nativecore

import java.io.*
import java.security.SecureRandom
import java.util.UUID
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.io.CipherInputStream

/** Portable strict Base64 streams; avoids API-26-only java.util.Base64 and whole-envelope Strings. */
object Base64Streams {
    private const val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    fun decode(source: InputStream): InputStream = object : InputStream() {
        val input = BufferedInputStream(source)
        val decoded = ByteArray(3)
        var position = 0; var available = 0; var ended = false
        fun symbol(): Int {
            while (true) {
                val n = input.read()
                if (n == -1 || (n != 9 && n != 10 && n != 13 && n != 32)) return n
            }
        }
        override fun read(): Int {
            if (position < available) return decoded[position++].toInt() and 255
            if (ended) return -1
            val a = symbol()
            if (a < 0) { ended = true; return -1 }
            val b = symbol(); val c = symbol(); val d = symbol()
            val x = alphabet.indexOf(a.toChar()); val y = alphabet.indexOf(b.toChar())
            require(x >= 0 && y >= 0 && c >= 0 && d >= 0) { "Invalid Base64" }
            val z = if(c == 61) 0 else alphabet.indexOf(c.toChar())
            val w = if(d == 61) 0 else alphabet.indexOf(d.toChar())
            require(z >= 0 && w >= 0 && (c != 61 || d == 61))
            decoded[0] = ((x shl 2) or (y shr 4)).toByte()
            decoded[1] = ((y shl 4) or (z shr 2)).toByte()
            decoded[2] = ((z shl 6) or w).toByte()
            available = if(c == 61) 1 else if(d == 61) 2 else 3
            position = 0
            if(available < 3) {
                require(if(available == 1) y and 15 == 0 else z and 3 == 0)
                require(symbol() == -1) { "Trailing Base64 data" }; ended = true
            }
            return decoded[position++].toInt() and 255
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if(len == 0) return 0
            var count = 0
            while(count < len) { val v = read(); if(v < 0) break; b[off + count++] = v.toByte() }
            return if(count == 0) -1 else count
        }
        override fun close() { decoded.fill(0); input.close() }
    }
    fun encode(target: OutputStream): OutputStream = object : OutputStream() {
        val output = BufferedOutputStream(target)
        val pending = ByteArray(3)
        var count = 0
        fun emit() {
            val a = pending[0].toInt() and 255; val b = pending[1].toInt() and 255; val c = pending[2].toInt() and 255
            output.write(alphabet[a shr 2].code)
            output.write(alphabet[((a and 3) shl 4) or (b shr 4)].code)
            output.write(if(count > 1) alphabet[((b and 15) shl 2) or (c shr 6)].code else 61)
            output.write(if(count > 2) alphabet[c and 63].code else 61)
            count = 0; pending.fill(0)
        }
        override fun write(value: Int) { pending[count++] = value.toByte(); if(count == 3) emit() }
        override fun write(b: ByteArray, off: Int, len: Int) { for(i in off until off + len) write(b[i].toInt()) }
        override fun flush() = output.flush()
        override fun close() { if(count > 0) emit(); output.close(); pending.fill(0) }
    }
}
object StreamingCrypto {
    fun encrypt(output: OutputStream, key: ByteArray, iv: ByteArray): OutputStream {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance()).apply { init(true, AEADParameters(KeyParameter(key), 128, iv)) }
        return object : OutputStream() {
            val buffer = ByteArray(8192 + 32)
            var closed = false
            override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                check(!closed)
                var position = off
                while(position < off + len) {
                    val size = minOf(8192, off + len - position)
                    val n = cipher.processBytes(b, position, size, buffer, 0)
                    output.write(buffer, 0, n); buffer.fill(0); position += size
                }
            }
            override fun close() {
                if(closed) return
                closed = true
                try { val n = cipher.doFinal(buffer, 0); output.write(buffer, 0, n); output.flush() }
                finally { buffer.fill(0); output.close() }
            }
        }
    }
    fun decrypt(input: InputStream, key: ByteArray, iv: ByteArray): InputStream {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance()).apply { init(false, AEADParameters(KeyParameter(key), 128, iv)) }
        val stream = CipherInputStream(input, cipher)
        return object : FilterInputStream(stream) {
            var closed = false
            override fun close() {
                if(closed) return; closed = true
                val buffer = ByteArray(8192)
                try { while(stream.read(buffer) >= 0) {} } finally { buffer.fill(0); stream.close() }
            }
        }
    }
    fun copy(input: InputStream, output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        try {
            while(true) {
                if(Thread.currentThread().isInterrupted) throw InterruptedIOException("Cancelled")
                val n = input.read(buffer); if(n < 0) break
                total += n; require(total <= limit) { "Stream exceeds size budget" }
                output.write(buffer, 0, n)
            }
            return total
        } finally { buffer.fill(0) }
    }
}

/** Only ciphertext reaches disk. The temporary key is never persisted and is erased on close. */
class EncryptedSpool private constructor(private val file: File, private val key: ByteArray, private val iv: ByteArray) : AutoCloseable {
    var size: Long = 0; private set
    fun open(): InputStream = StreamingCrypto.decrypt(file.inputStream(), key, iv)
    override fun close() { key.fill(0); iv.fill(0); file.delete() }
    companion object {
        fun create(root: File, input: InputStream, limit: Long): EncryptedSpool {
            require(root.isDirectory || root.mkdirs())
            val key = ByteArray(32).also(SecureRandom()::nextBytes)
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val file = File(root, UUID.randomUUID().toString() + ".tmp.enc")
            val spool = EncryptedSpool(file, key, iv)
            try {
                StreamingCrypto.encrypt(file.outputStream(), key, iv).use { spool.size = StreamingCrypto.copy(input, it, limit) }
                return spool
            } catch(e: Throwable) { spool.close(); throw e }
        }
    }
}