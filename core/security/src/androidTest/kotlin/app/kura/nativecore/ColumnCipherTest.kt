package app.kura.nativecore

import android.util.Base64
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ColumnCipherTest {
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun encrypted(key: ByteArray, plain: ByteArray, gcm: Boolean): ByteArray {
        val iv = ByteArray(if (gcm) 12 else 16) { (it + 3).toByte() }
        val cipher = Cipher.getInstance(if (gcm) "AES/GCM/NoPadding" else "AES/CBC/PKCS7Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), if (gcm) GCMParameterSpec(128, iv) else IvParameterSpec(iv))
        return iv + cipher.doFinal(plain)
    }
    @Test fun columnGcmCbcPlaintextAndTamper() {
        val key = ByteArray(32) { it.toByte() }
        val plain = "日本語: {\"custom\":\"✓\"}".toByteArray()
        for (gcm in listOf(true, false)) {
            val encrypted = encrypted(key, plain, gcm); val size = if (gcm) 12 else 16
            val text = b64(encrypted.copyOfRange(0, size)) + ":" + b64(encrypted.copyOfRange(size, encrypted.size))
            assertArrayEquals(plain, LegacyColumnCodec.decrypt(text, key))
            if (gcm) {
                encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
                val bad = b64(encrypted.copyOfRange(0, size)) + ":" + b64(encrypted.copyOfRange(size, encrypted.size))
                assertThrows(Exception::class.java) { LegacyColumnCodec.decrypt(bad, key) }
            }
        }
        assertNull(LegacyColumnCodec.decrypt(null, key))
        assertArrayEquals(byteArrayOf(), LegacyColumnCodec.decrypt("", key))
        assertArrayEquals("legacy:plain".toByteArray(), LegacyColumnCodec.decrypt("legacy:plain", key))
        key.fill(0)
    }
}
