package app.kura.nativeapp

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Opt-in measurement on an empty, disposable prototype installation with synthetic PIN 2468. */
class UnlockTimingTest {
    @Test fun emptyVaultUnlockTrace() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("unlockTiming") == "true")
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "app.kura.wallet.prototype")
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        repeat(4) {
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val pin = device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 15000)
            if (pin == null) {
                device.dumpWindowHierarchy(java.io.File(context.filesDir, "unlock-test-window.xml"))
            }
            assertNotNull(pin)
            for (digit in listOf("2", "4", "6", "8")) {
                device.findObject(By.text(digit).pkg("com.android.systemui")).click()
            }
            device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 20000))
            instrumentation.waitForIdleSync()
            device.pressHome()
            // Allow ProcessLifecycleOwner's delayed background event to lock the vault.
            Thread.sleep(1500)
        }
    }
}
