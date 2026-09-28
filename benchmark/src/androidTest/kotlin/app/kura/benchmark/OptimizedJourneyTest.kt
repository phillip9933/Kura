package app.kura.benchmark

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test

class OptimizedJourneyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetPackage = (InstrumentationRegistry.getArguments().getString("targetPackage")
        ?: "app.kura.wallet.prototype.benchmark").also {
        require(it in setOf("app.kura.wallet.prototype.benchmark", "app.kura.wallet.prototype.profile"))
    }
    private val device = UiDevice.getInstance(instrumentation)
    private fun click(text: String) {
        if(text=="Settings") assertTrue(device.wait(Until.hasObject(By.desc("Settings")),10000))
        repeat(8) {
            val target = device.findObject(By.desc(text)) ?: device.findObject(By.text(text))
            try { if(target != null && !target.visibleBounds.isEmpty) {
                target.click(); device.waitForIdle(); return }
            } catch (_: StaleObjectException) {}
            val scroll = device.findObject(By.scrollable(true))
            if(scroll != null) scroll.scroll(Direction.DOWN,.6f) else device.wait(Until.hasObject(By.text(text)),2000)
        }
        error("Missing UI: " + text)
    }
    private fun authorize() {
        // Real system credential UI; no auth bypass or test master key injection.
        val pin = device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 15000)
        assertNotNull("Expected system credential prompt", pin)
        for (digit in listOf("2", "4", "6", "8")) device.findObject(By.text(digit).pkg("com.android.systemui")).click()
        device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
        assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 20000))
    }
    @Test fun authenticatedLaunchFixturesLockAndReopen() {
        device.wakeUp()
        val context = instrumentation.context
        context.startActivity(Intent().setClassName(targetPackage,"app.kura.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        authorize()
        click("Settings"); click("Load synthetic fixtures")
        click("Passes")
        UiScrollable(UiSelector().scrollable(true)).scrollToBeginning(20)
        assertTrue(device.wait(Until.hasObject(By.desc("Open Kura Test Transit 0")), 15000))
        device.executeShellCommand("dumpsys gfxinfo " + targetPackage + " reset")
        repeat(3) { device.swipe(device.displayWidth/2, device.displayHeight*3/4, device.displayWidth/2, device.displayHeight/3, 30) }
        repeat(3) { device.swipe(device.displayWidth/2, device.displayHeight/3, device.displayWidth/2, device.displayHeight*3/4, 30) }
        device.waitForIdle()
        instrumentation.sendStatus(0, android.os.Bundle().apply {
            putString("stream", device.executeShellCommand("dumpsys gfxinfo " + targetPackage)+"\n"+device.executeShellCommand("dumpsys meminfo "+targetPackage))
        })

        UiScrollable(UiSelector().scrollable(true)).scrollToBeginning(20)
        device.waitForIdle()
        val visiblePass = device.findObject(UiSelector().description("Open Kura Test Transit 0"))
        assertTrue("Expected the first pass after returning to the top",visiblePass.waitForExists(10000))
        visiblePass.click()
        assertTrue(device.wait(Until.hasObject(By.desc("Pass barcode")), 10000))
        click("Close")
        click("Add"); click("Scan for Sharing or Import")
        device.wait(Until.findObject(By.textContains("While using")), 4000)?.click()
        assertTrue("Camera never produced frames", device.wait(Until.hasObject(By.text("Camera active")), 20000))
        click("Close scanner")
        click("Add"); click("Import File")
        assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 10000) || device.hasObject(By.pkg("com.android.documentsui")))
        device.setOrientationLeft(); device.waitForIdle(); device.setOrientationNatural(); device.waitForIdle()
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 15000))
        device.pressHome(); Thread.sleep(1200)
        context.startActivity(Intent().setClassName(targetPackage,"app.kura.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        authorize()
        click("Passes")
        UiScrollable(UiSelector().scrollable(true)).scrollToBeginning(20)
        assertTrue(device.wait(Until.hasObject(By.desc("Open Kura Test Transit 0")), 15000))
    }
}