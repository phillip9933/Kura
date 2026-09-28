package app.kura.nativecore

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.security.KeyStore
import java.util.UUID

class HardwareVaultKeyTest {
    @Test fun perOperationKeyRejectsCryptoWithoutAuthentication() {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alias = "app.kura.prototype.test." + UUID.randomUUID()
        val wrapper = HardwareVaultKey(context, alias, allowSoftwareForTests = true)
        try {
            wrapper.provision()
            println("KURA_KEYSTORE_SECURITY_LEVEL=" + wrapper.securityLevel())
            val crypto = wrapper.cryptoObject(true)
            try {
                SensitiveBytes(ByteArray(32) { 1 }).use { wrapper.wrap(crypto.cipher!!, it) }
                fail("Unauthenticated cipher must not wrap a key")
            } catch (_: javax.crypto.IllegalBlockSizeException) {
            } catch (_: android.security.keystore.UserNotAuthenticatedException) {}
        } finally { KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) } }
    }
    @Test fun deletingOneNativeAliasPreservesOtherKeys() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val wrapper=HardwareVaultKey(context,"app.kura.prototype.delete."+UUID.randomUUID(),allowSoftwareForTests=true)
        val preserved=HardwareVaultKey(context,"app.kura.prototype.preserved."+UUID.randomUUID(),allowSoftwareForTests=true)
        try {
            wrapper.provision();preserved.provision()
            assertTrue(wrapper.exists());assertTrue(preserved.exists())
            wrapper.delete();assertFalse(wrapper.exists());assertTrue(preserved.exists())
            wrapper.delete();assertTrue(preserved.exists())
        } finally {wrapper.delete();preserved.delete()}
    }
    @Test fun invalidIvFailsBeforeDecrypt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alias = "app.kura.prototype.test." + UUID.randomUUID()
        val wrapper = HardwareVaultKey(context, alias, allowSoftwareForTests = true)
        try {
            wrapper.provision()
            try { wrapper.cipher(false, ByteArray(16)); fail() } catch (_: IllegalArgumentException) {}
        } finally { KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) } }
    }
}