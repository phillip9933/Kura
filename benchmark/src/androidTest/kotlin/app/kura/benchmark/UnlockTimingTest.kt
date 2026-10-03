package app.kura.benchmark

import android.content.Intent
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** External driver so the measured application can retain release optimizations. */
class UnlockTimingTest {
    @Test fun authenticatedVaultReopen() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("unlockTiming") == "true")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val target = arguments.getString("targetPackage")
            ?: "app.kura.wallet.prototype.benchmark"
        require(target in setOf(
            "app.kura.wallet.prototype",
            "app.kura.wallet.prototype.benchmark",
            "app.kura.wallet.prototype.profile"
        ))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        repeat(4) {
            instrumentation.context.startActivity(
                Intent().setClassName(target, "app.kura.nativeapp.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            assertNotNull("Expected credential prompt", device.wait(
                Until.findObject(By.text("2").pkg("com.android.systemui")), 15000
            ))
            for (digit in listOf("2", "4", "6", "8")) {
                device.findObject(By.text(digit).pkg("com.android.systemui")).click()
            }
            device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 20000))
            device.waitForIdle()
            device.pressHome()
            Thread.sleep(1500)
        }
    }
}
