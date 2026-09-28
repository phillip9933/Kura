package app.kura.nativecore

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object LegacyColumnCodec {
    fun decrypt(value: String?, key: ByteArray): ByteArray? {
        if (value == null) return null
        val parts = value.split(':')
        // Match Flutter's legacy plaintext detection; authenticated failures never fall back.
        val iv = if (parts.size == 2) runCatching { Base64.decode(parts[0], Base64.DEFAULT) }.getOrNull() else null
        val payload = if (iv?.size in listOf(12, 16)) runCatching { Base64.decode(parts[1], Base64.DEFAULT) }.getOrNull() else null
        if (iv == null || payload == null) return value.toByteArray(Charsets.UTF_8)
        return decryptPayload(iv, payload, key)
    }
    internal fun decryptPayload(iv: ByteArray, payload: ByteArray, key: ByteArray): ByteArray {
        val gcm = iv.size == 12
        require(gcm || iv.size == 16)
        val cipher = Cipher.getInstance(if (gcm) "AES/GCM/NoPadding" else "AES/CBC/PKCS7Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
            if (gcm) GCMParameterSpec(128, iv) else IvParameterSpec(iv))
        return cipher.doFinal(payload)
    }
}
