package app.kura.nativeapp

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test

class LiveCameraTest {
    @Test fun barcodeCameraProducesFrames() {
        check(android.os.Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "app.kura.wallet.prototype")
        val device = UiDevice.getInstance(instrumentation)
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertNotNull(device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 15000))
        listOf("2", "4", "6", "8").forEach { device.findObject(By.text(it).pkg("com.android.systemui")).click() }
        device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
        assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 20000))
        device.findObject(By.desc("Add")).click()
        device.wait(Until.findObject(By.desc("Scan for Sharing or Import")), 5000)!!.click()
        device.wait(Until.findObject(By.textContains("While using")), 3000)?.click()
        val active = device.wait(Until.hasObject(By.text("Camera active")), 20000)
        val failure = device.findObject(By.textStartsWith("Camera unavailable"))?.text
        device.findObject(By.text("Close scanner"))?.click()
        assertTrue("Live camera must produce frames: $failure", active)
    }
}
