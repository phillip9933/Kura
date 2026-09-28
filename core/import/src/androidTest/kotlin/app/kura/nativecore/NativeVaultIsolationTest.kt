package app.kura.nativecore

import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class NativeVaultIsolationTest {
    @Test fun freshVaultAndResetIgnoreOldFilesWhileExplicitRestorePreservesNativeSecrets() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(target.cacheDir, "backup-only-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir() = File(root, "no_backup").apply { mkdirs() }
            override fun getApplicationInfo() = ApplicationInfo(target.applicationInfo).apply { dataDir = root.path }
        }
        // Invalid sentinels deliberately make any attempt at automatic adoption fail.
        val oldFiles = listOf("app_flutter/walletbox.db", "app_flutter/passes.db", "app_flutter/identities.db",
            "app_flutter/front.png", "shared_prefs/FlutterSecureStorage.xml", "shared_prefs/FlutterSharedPreferences.xml")
            .map { name -> File(root, name).apply { parentFile!!.mkdirs(); writeText("untouched synthetic $name") } }
        val before = oldFiles.associateWith { it.readBytes().toList() }
        try {
            SensitiveBytes(ByteArray(32) { 7 }).use { key ->
                val store = VaultStore(context)
                val opened = store.open(key)
                try {
                    for (table in listOf("wallets", "passes", "identities")) assertTrue(store.rows(opened, table).isEmpty())
                    assertEquals(0, store.settings(opened).length())
                    assertNull(store.deviceSecret(opened, "auto-backup-password.enc"))
                    assertFalse(File(opened.directory, "media/front.png").exists())
                    store.insert(opened, "wallets", JSONObject().put("name", "Explicit backup card"))
                    val secret = "Synthetic password".toByteArray()
                    try { store.saveDeviceSecret(opened, "auto-backup-password.enc", secret) } finally { secret.fill(0) }
                    val staged = store.export(opened).use { store.stage(it, key) }
                    store.preserveDeviceSecrets(opened, staged)
                    val password = SensitiveBytes(key.useBytes { Envelope.encode(it).toByteArray() })
                    val rooms = password.use { VaultDatabases.open(context, staged, it) }
                    try {
                        val restored = VaultStore.Opened(staged, rooms, key)
                        assertEquals("Explicit backup card", store.rows(restored, "wallets").single().getString("name"))
                        val restoredSecret = store.deviceSecret(restored, "auto-backup-password.enc")!!
                        try { assertEquals("Synthetic password", restoredSecret.toString(Charsets.UTF_8)) }
                        finally { restoredSecret.fill(0) }
                    } finally { rooms.close() }
                } finally { opened.close() }
                store.purgeNative()
                assertNull(store.active())
                val fresh = store.open(key)
                try { assertTrue(store.rows(fresh, "wallets").isEmpty()) } finally { fresh.close() }
                assertEquals(before, oldFiles.associateWith { it.readBytes().toList() })
            }
        } finally { root.deleteRecursively() }
    }
}
