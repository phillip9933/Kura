package app.kura.nativeapp

import android.content.Intent
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test

class AppUpdateSettingsTest {
    @Test fun settingsAndVersionDialogBothReachManualUpdateControls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "app.kura.wallet.prototype")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertNotNull(device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 15000))
        listOf("2", "4", "6", "8").forEach { device.findObject(By.text(it).pkg("com.android.systemui")).click() }
        device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
        assertTrue(waitForJourneyVault(device))
        clickJourneyControl(device, "Settings")
        clickJourneyControl(device, "App updates")
        assertNotNull(device.wait(Until.findObject(By.text("Check for updates")), 5000))
        clickJourneyControl(device, "Done")
        clickJourneyControl(device, "App Version & Trademark")
        assertNotNull(device.wait(Until.findObject(By.text("Kura")), 5000))
        clickJourneyControl(device, "App updates")
        assertNotNull(device.wait(Until.findObject(By.text("Check for updates")), 5000))
        assertFalse((context.applicationContext as KuraUpdateApplication).updateManager.state.value.automaticChecks)
        clickJourneyControl(device, "Done")
        clickJourneyControl(device, "Close")
    }
}
