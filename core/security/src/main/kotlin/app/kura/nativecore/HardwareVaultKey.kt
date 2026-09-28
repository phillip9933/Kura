package app.kura.nativecore

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/** New aliases never collide with Flutter aliases. No key material is exported from Android Keystore. */
class HardwareVaultKey(
    private val context: Context,
    private val alias: String = "app.kura.wallet.native.master.v1",
    private val allowSoftwareForTests: Boolean = false,
    private val biometricOnly: Boolean = false
) {
    private val store get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    fun exists() = store.containsAlias(alias)
    fun delete() {store.deleteEntry(alias)}
    fun provision() {
        if (exists()) { checkHardware(key()); return }
        require(context.getSystemService(KeyguardManager::class.java).isDeviceSecure) { "Set a device PIN, pattern, or password first" }
        fun generate(strongBox: Boolean) {
            val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(true)
                .setRandomizedEncryptionRequired(true)
            if (Build.VERSION.SDK_INT >= 30)
                builder.setUserAuthenticationParameters(0, if (biometricOnly) KeyProperties.AUTH_BIOMETRIC_STRONG else KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
            else {
                // API 24–29 cannot combine a CryptoObject with device credential in BiometricPrompt.
                // A short Keystore-enforced credential window is used; prompt success alone never yields plaintext.
                @Suppress("DEPRECATION")
                builder.setUserAuthenticationValidityDurationSeconds(if(biometricOnly) -1 else 15)
            }
            if (Build.VERSION.SDK_INT >= 28) builder.setIsStrongBoxBacked(strongBox)
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(builder.build()); generateKey() }
        }
        if (Build.VERSION.SDK_INT >= 28 && context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")) {
            try { generate(true) } catch (_: StrongBoxUnavailableException) { generate(false) }
        } else generate(false)
        try { checkHardware(key()) } catch (e: Throwable) { store.deleteEntry(alias); throw e }
    }
    private fun key() = store.getKey(alias, null) as? SecretKey ?: error("Hardware key unavailable")
    fun securityLevel(): String {
        val info = SecretKeyFactory.getInstance("AES", "AndroidKeyStore").getKeySpec(key(), KeyInfo::class.java) as KeyInfo
        return if (Build.VERSION.SDK_INT >= 31) when(info.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> "StrongBox"
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE"
            else -> "Software"
        } else {
            @Suppress("DEPRECATION")
            if (info.isInsideSecureHardware) "TEE" else "Software"
        }
    }
    private fun checkHardware(key: SecretKey) {
        val info = SecretKeyFactory.getInstance("AES", "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo
        require(info.isUserAuthenticationRequired)
        check(allowSoftwareForTests || securityLevel() != "Software") { "Hardware-backed Keystore required" }
    }
    fun cipher(encrypt: Boolean, iv: ByteArray? = null): Cipher {
        checkHardware(key())
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            if (encrypt) init(Cipher.ENCRYPT_MODE, key())
            else { require(iv?.size == 12); init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        }
    }
    fun cryptoObject(encrypt: Boolean, iv: ByteArray? = null): BiometricPrompt.CryptoObject {
        check(Build.VERSION.SDK_INT >= 30 || biometricOnly) { "Use credential intent on API 24–29" }
        return BiometricPrompt.CryptoObject(cipher(encrypt, iv))
    }
    fun credentialIntent(): Intent {
        @Suppress("DEPRECATION")
        return context.getSystemService(KeyguardManager::class.java)
            .createConfirmDeviceCredentialIntent("Unlock Kura", "Authorize the hardware vault key") ?: error("Device credential unavailable")
    }
    /** Called with the exact cipher returned by a successful CryptoObject prompt. */
    fun unwrap(cipher: Cipher, wrapped: ByteArray): ByteArray =
        cipher.doFinal(wrapped).also { if(it.size != 32) { it.fill(0); error("Invalid wrapped master key") } }
    fun wrap(cipher: Cipher, master: SensitiveBytes): ByteArray = master.useBytes { require(it.size == 32); cipher.doFinal(it) }
}