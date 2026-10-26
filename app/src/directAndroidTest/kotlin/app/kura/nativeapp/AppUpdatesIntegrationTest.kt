package app.kura.nativeapp

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import io.github.phillip9933.appupdater.UpdateManager
import org.junit.Assert.*
import org.junit.Test

class AppUpdatesIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun releaseIdentityUsesOnlyKuraUniversalAssets() {
        assertEquals("Kura-2.2.0.apk", kuraUpdateConfig.assetName("v2.2.0"))
        assertNull(kuraUpdateConfig.assetName("android-v2.2.0"))
        assertNull(kuraUpdateConfig.assetName("v2.2.0/other"))
        assertEquals("Kura", kuraUpdateConfig.repository)
    }

    @Test fun directManifestScopesProviderAndRequiresUserInstallPermission() {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val requested = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        for (permission in listOf(Manifest.permission.INTERNET, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.REQUEST_INSTALL_PACKAGES)) {
            assertTrue(permission in requested)
        }
        @Suppress("DEPRECATION")
        val provider = pm.resolveContentProvider("${context.packageName}.android-updates", PackageManager.GET_META_DATA)
        assertNotNull(provider)
        assertFalse(provider!!.exported)
        assertTrue(provider.grantUriPermissions)
        assertEquals("io.github.phillip9933.appupdater.UpdateFileProvider", provider.name)
    }

    @Test fun applicationRegistersWorkerSourceWithoutVaultAuthentication() {
        assertTrue(context.applicationContext is KuraUpdateApplication)
        assertTrue(context.applicationContext is androidx.work.Configuration.Provider)
        assertNotNull(androidx.work.WorkManager.getInstance(context))
        val manager = UpdateManager.get(context, kuraUpdateConfig)
        assertEquals(kuraUpdateConfig, manager.config)
        // This suite uses a fresh disposable installation: first-run networking is disabled.
        assertFalse(manager.state.value.automaticChecks)
        assertFalse(manager.state.value.wifiDownload)
        assertEquals(0L, manager.state.value.lastChecked)
    }
}
