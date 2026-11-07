package app.kura.benchmark

import android.content.Intent
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test

/** External UI test: no access to app classes, test hooks, or unminified entry points. */
class UpdaterDiscoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val target = (InstrumentationRegistry.getArguments().getString("targetPackage")
        ?: "app.kura.wallet.prototype.benchmark").also {
        require(it in setOf("app.kura.wallet.prototype", "app.kura.wallet.prototype.benchmark"))
    }
    private fun click(label: String) {
        repeat(16) { attempt ->
            device.waitForIdle()
            if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
            val control = device.findObject(By.desc(label)) ?: device.findObject(By.text(label))
            if (control != null && control.isEnabled && !control.visibleBounds.isEmpty) {
                control.click(); device.waitForIdle(); return
            }
            device.findObject(By.scrollable(true))?.scroll(if (attempt < 8) Direction.DOWN else Direction.UP, .5f)
        }
        device.dumpWindowHierarchy(java.io.File(instrumentation.context.getExternalFilesDir(null), "updater-discovery-failure.xml"))
        error("Missing $label")
    }
    private fun panel() {
        @Suppress("DEPRECATION")
        val installed = instrumentation.context.packageManager.getPackageInfo(target, 0).versionName
        assertNotNull(device.wait(Until.findObject(By.text("Installed: $installed")), 5000))
        click("Include beta releases")
        // Exercise a real control without making a network request or installing anything.
        click("Include beta releases")
        repeat(6) {
            if (!device.hasObject(By.text("Check for updates"))) device.findObject(By.scrollable(true))?.scroll(Direction.DOWN,.3f)
        }
        assertNotNull(device.findObject(By.text("Check for updates")))
        assertFalse(device.hasObject(By.pkg("com.google.android.packageinstaller")))
        click("Done")
    }
    @Test fun optimizedLockedSettingsAndVersionRoutesExposeManualUpdates() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(device.executeShellCommand("getconf PAGE_SIZE").trim() == "16384")
        val info = instrumentation.context.packageManager.getApplicationInfo(target, 0)
        assertEquals(0, info.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)
        instrumentation.context.startActivity(Intent().setClassName(target, "app.kura.nativeapp.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertNotNull(device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")),15000))
        device.pressBack()
        assertNotNull(device.wait(Until.findObject(By.text("Authentication canceled")), 5000))
        click("OK")
        assertNotNull(device.wait(Until.findObject(By.desc("Unlock Kura")),5000))
        click("App updates"); panel()
        click("Unlock Kura")
        assertNotNull(device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")),15000))
        listOf("2","4","6","8").forEach { device.findObject(By.text(it).pkg("com.android.systemui")).click() }
        device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
        assertNotNull(device.wait(Until.findObject(By.desc("Settings")),20000))
        click("Settings"); click("App updates"); panel()
        click("App Version & Trademark"); click("App updates"); panel(); click("Close")
    }
}
