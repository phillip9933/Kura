package app.kura.benchmark

import android.content.Intent
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.regex.Pattern

/** External UI driver: denied capture and cancelled picker must return safely to the vault. */
class PermissionJourneyTest {
    @Test fun deniedCameraAndCancelledImportRemainUsable() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("permissionAcceptance") == "true")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val target = args.getString("targetPackage") ?: "app.kura.wallet.prototype.benchmark"
        require(target in setOf("app.kura.wallet.prototype", "app.kura.wallet.prototype.benchmark"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        fun click(label: String) {
            val control = device.wait(Until.findObject(By.desc(label)), 3000)
                ?: device.wait(Until.findObject(By.text(label)), 5000)
            assertNotNull("Expected $label", control)
            control!!.click()
            device.waitForIdle()
        }
        device.executeShellCommand("am force-stop $target")
        device.executeShellCommand("pm revoke $target android.permission.CAMERA")
        device.executeShellCommand("pm clear-permission-flags $target android.permission.CAMERA user-set user-fixed")
        try {
            device.wakeUp()
            instrumentation.context.startActivity(Intent().setClassName(target, "app.kura.nativeapp.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            if (Build.VERSION.SDK_INT < 30) {
                val pin = device.wait(Until.findObject(By.res("com.android.settings", "password_entry")), 15000)
                assertNotNull("Expected device credential", pin)
                pin!!.text = "2468"
                device.pressEnter()
            } else {
                assertNotNull(device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 15000))
                listOf("2", "4", "6", "8").forEach { device.findObject(By.text(it).pkg("com.android.systemui")).click() }
                device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
            }
            assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 20000))
            click("Add")
            click("Scan for Sharing or Import")
            val deny = device.wait(Until.findObject(By.res(Pattern.compile(".*:id/permission_deny_button"))), 10000)
            assertNotNull("Expected real system camera-permission choice", deny)
            deny!!.click()
            assertTrue(device.wait(Until.hasObject(By.text("Camera permission was not granted")), 10000))
            assertFalse(device.hasObject(By.text("Live barcode scanner")))
            click("OK")
            assertTrue(device.hasObject(By.desc("Settings")))
            click("Add")
            click("Import File")
            assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 10000)
                || device.hasObject(By.pkg("com.android.documentsui")))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 15000))
        } finally {
            device.executeShellCommand("pm clear-permission-flags $target android.permission.CAMERA user-set user-fixed")
            device.pressHome()
            // Leave the next external journey an explicitly stopped app, not a pending background transition.
            device.executeShellCommand("am force-stop $target")
        }
    }
}
