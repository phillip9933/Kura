package app.kura.nativeapp

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.kura.nativecore.DeviceUnlock
import app.kura.nativecore.VaultState
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real Android credential UI, synthetic data and a disposable emulator only. */
class AppUnlockJourneyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    private fun click(text: String) {
        val node = device.wait(Until.findObject(By.text(text)), 10_000)
        assertNotNull("Missing $text", node)
        node!!.click()
    }

    private fun launch() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun authorize() {
        val legacy = if (Build.VERSION.SDK_INT < 30) {
            device.wait(Until.findObject(By.res("com.android.settings", "password_entry")), 5000)
        } else null
        if (legacy != null) {
            legacy.click()
            legacy.text = "2468"
            device.pressEnter()
        } else {
            assertNotNull("Expected device credential prompt", device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 15_000))
            for (digit in listOf("2", "4", "6", "8")) device.findObject(By.text(digit).pkg("com.android.systemui")).click()
            device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
        }
        assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 20_000) || device.hasObject(By.desc("Require app unlock")))
    }

    private fun controller(): VaultController {
        lateinit var result: VaultController
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().single()
            result = ViewModelProvider(activity)[VaultController::class.java]
        }
        return result
    }

    @Test fun phoneUnlockRetainsDataAndCanRestoreAppAuthentication() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(context.packageName == "app.kura.wallet.prototype")
        device.wakeUp()
        launch()
        authorize()
        val vm = controller()
        runBlocking {
            vm.work { opened ->
                vm.store.insert(opened, "passes", JSONObject().put("organizationName", "Phone unlock sentinel").put("type", "generic"))
            }
            vm.refresh()
        }
        clickSettings()
        val toggle = device.wait(Until.findObject(By.desc("Require app unlock")), 5_000)!!
        assertFalse("App unlock is required by default", DeviceUnlock(context, true).enabled)
        toggle.click()
        assertNotNull(device.wait(Until.findObject(By.text("Use phone unlock only?")), 5_000))
        click("Cancel")
        assertFalse(DeviceUnlock(context, true).enabled)
        device.findObject(By.desc("Require app unlock")).click()
        click("Use phone unlock")
        waitForMode(enabled = true)
        click("Done")
        device.pressHome()
        Thread.sleep(1500)
        assertEquals(VaultState.Locked, vm.state.value)
        launch()
        assertTrue("Phone-only mode should open without another prompt", device.wait(Until.hasObject(By.desc("Settings")), 20_000))
        runBlocking {
            vm.work { opened -> assertTrue(vm.store.rows(opened, "passes").any { it.optString("organizationName") == "Phone unlock sentinel" }) }
        }
        encryptedBackupRoundTrip(vm)
        // The wrapper itself must refuse access once the device is locked.
        device.sleep()
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        repeat(50) { if (!keyguard.isDeviceLocked) Thread.sleep(100) }
        assertTrue("Phone must lock for the boundary check", keyguard.isDeviceLocked)
        try {
            DeviceUnlock(context, true).open().also { it.fill(0) }
            fail("A locked phone must not release the vault key")
        } catch (_: IllegalStateException) { }
        device.wakeUp()
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 20)
        authorize()
        clickSettings()
        device.findObject(By.desc("Require app unlock")).click()
        authorize()
        waitForMode(enabled = false)
        click("Done")
        device.pressHome()
        Thread.sleep(1500)
        launch()
        authorize()
        runBlocking {
            controller().work { opened -> assertTrue(vm.store.rows(opened, "passes").any { it.optString("organizationName") == "Phone unlock sentinel" }) }
        }
    }

    private fun clickSettings() {
        device.wait(Until.findObject(By.desc("Settings")), 10_000)!!.click()
        assertNotNull(device.wait(Until.findObject(By.desc("Require app unlock")), 10_000))
    }

    private fun encryptedBackupRoundTrip(vm: VaultController) {
        val backup = java.io.File.createTempFile("phone-mode-synthetic-", ".wbk", context.cacheDir)
        val password = "Synthetic phone-mode backup password".toCharArray()
        try {
            runBlocking {
                val previous = vm.store.active()
                vm.work { opened ->
                    vm.store.export(opened).use { payload ->
                        assertFalse(payload.data.getJSONObject("settings").has("requireAppUnlock"))
                        backup.outputStream().use { app.kura.nativecore.StreamingBackup.write(payload, it, password) }
                    }
                }
                val encrypted = backup.readBytes()
                try {
                    assertTrue(encrypted.toString(Charsets.US_ASCII).startsWith("argon2:"))
                    assertFalse(encrypted.toString(Charsets.US_ASCII).contains("Phone unlock sentinel"))
                } finally { encrypted.fill(0) }
                try {
                    val wrong = "Wrong synthetic password".toCharArray()
                    try {
                        backup.inputStream().use { app.kura.nativecore.StreamingBackup.read(it, wrong, context.cacheDir).close() }
                        fail("Wrong backup password must fail")
                    } finally { wrong.fill('\u0000') }
                } catch (_: Exception) { }
                assertEquals(previous, vm.store.active())
                val staged = vm.work { opened ->
                    backup.inputStream().use { input ->
                        app.kura.nativecore.StreamingBackup.read(input, password, context.cacheDir).use { payload ->
                            assertTrue(payload.imageNames.none { it.contains("device-unlock") })
                            vm.store.stage(payload, opened.key).also { vm.store.preserveDeviceSecrets(opened, it) }
                        }
                    }
                }
                assertEquals("Staging must not replace the active vault", previous, vm.store.active())
                vm.lock()
                vm.store.activate(staged)
            }
            assertTrue(device.wait(Until.hasObject(By.desc("Unlock Kura")), 10_000))
            device.findObject(By.desc("Unlock Kura")).click()
            assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 20_000))
            assertTrue("Restoring data must not change this device's unlock policy", DeviceUnlock(context, true).enabled)
            runBlocking {
                vm.work { opened -> assertTrue(vm.store.rows(opened, "passes").any { it.optString("organizationName") == "Phone unlock sentinel" }) }
            }
        } finally {
            password.fill('\u0000')
            backup.delete()
        }
    }

    /** Run with phase=prepare, stop the app process externally, then run with phase=verify. */
    @Test fun phoneUnlockColdProcessAcceptance() {
        val phase = InstrumentationRegistry.getArguments().getString("phoneUnlockPhase")
        org.junit.Assume.assumeTrue(phase in setOf("prepare", "verify"))
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(context.packageName == "app.kura.wallet.prototype")
        launch()
        if (phase == "prepare") {
            authorize()
            clickSettings()
            device.findObject(By.desc("Require app unlock")).click()
            click("Use phone unlock")
            waitForMode(enabled = true)
        } else {
            assertTrue("Cold process should open using phone unlock", device.wait(Until.hasObject(By.desc("Settings")), 20_000))
            assertFalse(device.hasObject(By.text("2").pkg("com.android.systemui")))
            assertTrue(DeviceUnlock(context, true).enabled)
            runBlocking {
                controller().work { opened ->
                    assertTrue(controller().store.rows(opened, "passes").any { it.optString("organizationName") == "Phone unlock sentinel" })
                }
            }
            clickSettings()
            device.findObject(By.desc("Require app unlock")).click()
            authorize()
            waitForMode(enabled = false)
        }
    }

    private fun waitForMode(enabled: Boolean) {
        repeat(100) {
            if (DeviceUnlock(context, true).enabled == enabled && !controller().busy.value) return
            Thread.sleep(100)
        }
        fail("Unlock mode did not change")
    }
}
