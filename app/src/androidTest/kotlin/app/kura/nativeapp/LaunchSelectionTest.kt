package app.kura.nativeapp

import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.kura.nativecore.VaultState
import app.kura.nativecore.DeviceUnlock
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real credentials and encrypted settings; delay work to expose first-render ordering. */
class LaunchSelectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    // Call only on the application main thread.
    private fun owner(): MainActivity =
        listOf(Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED)
            .flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
            .filterIsInstance<MainActivity>().distinct().single()

    private fun controller(): VaultController {
        lateinit var vm: VaultController
        instrumentation.runOnMainSync { vm = ViewModelProvider(owner())[VaultController::class.java] }
        return vm
    }

    private fun legacyCredential(timeout: Long) = when {
        Build.VERSION.SDK_INT < 30 -> device.wait(Until.findObject(By.res("com.android.settings", "password_entry")), timeout)
        Build.VERSION.SDK_INT <= 35 -> device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile("com\\.android\\.systemui:id/(lockPassword|pinEntry)"))), timeout)
        else -> null
    }

    private fun launch() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        if (Build.VERSION.SDK_INT <= 35) assertNotNull(legacyCredential(15000))
        else assertNotNull(device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 15000))
    }

    private fun authorize() {
        if (Build.VERSION.SDK_INT <= 35) {
            requireNotNull(legacyCredential(5000)).click()
            device.executeShellCommand("input text 2468")
            device.pressEnter()
        } else {
            listOf("2", "4", "6", "8").forEach { device.findObject(By.text(it).pkg("com.android.systemui")).click() }
            device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
        }
    }

    private fun ready(vm: VaultController) = runBlocking {
        withTimeout(15000) { while (vm.busy.value || vm.state.value !is VaultState.Unlocked) delay(25) }
    }

    private fun table(vm: VaultController): String {
        instrumentation.waitForIdleSync()
        var selected = ""
        instrumentation.runOnMainSync { selected = vm.pending.uiMemory.table.value }
        return selected
    }

    @Test fun coldLaunchPreferencesPrecedeUnlockedEvenWhenSessionWorkIsDelayed() {
        check(context.packageName == "app.kura.wallet.prototype")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val phase = InstrumentationRegistry.getArguments().getString("launchSelectionPhase")
        if (phase != null) {
            coldProcessPhase(phase)
            return
        }
        launch(); authorize()
        var vm = controller()
        ready(vm)
        val saved = runBlocking { vm.work { vm.store.settings(it).toString() } }
        try {
            // Every iteration creates a new Activity/ViewModel with default in-memory preferences.
            for ((index, visible, expected) in listOf(
                Triple(0, setOf("wallets", "passes", "identities"), "wallets"),
                Triple(1, setOf("wallets", "passes", "identities"), "passes"),
                Triple(2, setOf("wallets", "passes", "identities"), "identities"),
                Triple(0, setOf("passes", "identities"), "passes"),
                Triple(0, setOf("identities"), "identities"),
            )) {
                runBlocking {
                    vm.preference("defaultScreenIndex", index)
                    vm.preference("showPaymentsTab", "wallets" in visible)
                    vm.preference("showPassesTab", "passes" in visible)
                    vm.preference("showIdentityTab", "identities" in visible)
                    vm.lock()
                }
                instrumentation.runOnMainSync { owner().finish() }
                device.waitForIdle()
                launch()
                vm = controller()
                val operations = VaultController::class.java.getDeclaredField("operations").run {
                    isAccessible = true
                    get(vm) as Mutex
                }
                assertTrue("No session work before credential acceptance", operations.tryLock())
                try {
                    authorize()
                    runBlocking { withTimeout(15000) { while (vm.state.value !is VaultState.Unlocked) delay(25) } }
                    assertEquals("Launch settings must be ready when Unlocked is observable", expected, vm.presentation.value.initialTable)
                    runBlocking { withTimeout(5000) { while (!vm.pending.uiMemory.initialized) delay(25) } }
                    assertEquals("First rendered section for default $index / $visible", expected, table(vm))
                } finally { operations.unlock() }
                ready(vm)
            }
            // Ordinary preference updates must not reset navigation in an existing session.
            runBlocking {
                vm.preference("showPaymentsTab", true)
                vm.preference("showPassesTab", true)
            }
            clickJourneyControl(device, "Cards")
            assertEquals("wallets", table(vm))
            clickJourneyControl(device, "Identity")
            assertEquals("identities", table(vm))
            runBlocking {
                vm.preference("defaultScreenIndex", 0)
                vm.refresh()
            }
            assertEquals("identities", table(vm))
        } finally {
            ready(vm)
            runBlocking {
                vm.work { vm.store.saveSettings(it, JSONObject(saved)) }
                vm.refresh()
            }
        }
    }

    /** The external runner force-stops the app between prepare and verify. */
    private fun coldProcessPhase(phase: String) {
        val arguments = InstrumentationRegistry.getArguments()
        val index = requireNotNull(arguments.getString("launchIndex")).toInt().also { require(it in 0..2) }
        val phoneOnly = arguments.getString("phoneOnly") == "true"
        when (phase) {
            "prepare" -> {
                assertFalse(DeviceUnlock(context, true).enabled)
                launch(); authorize()
                val vm = controller()
                ready(vm)
                runBlocking {
                    vm.preference("defaultScreenIndex", index)
                    vm.preference("showPaymentsTab", true)
                    vm.preference("showPassesTab", true)
                    vm.preference("showIdentityTab", true)
                }
                if (phoneOnly) {
                    clickJourneyControl(device, "Settings")
                    clickJourneyControl(device, "Require app unlock")
                    clickJourneyControl(device, "Use phone unlock")
                    runBlocking {
                        withTimeout(10000) { while (!DeviceUnlock(context, true).enabled || vm.busy.value) delay(25) }
                    }
                }
            }
            "verify" -> {
                assertEquals(phoneOnly, DeviceUnlock(context, true).enabled)
                try {
                    if (phoneOnly) {
                        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        assertNotNull(device.wait(Until.findObject(By.desc("Settings")), 20000))
                        assertFalse(device.hasObject(By.text("2").pkg("com.android.systemui")))
                    } else {
                        launch(); authorize()
                    }
                    val vm = controller()
                    ready(vm)
                    val expected = listOf("wallets", "passes", "identities")[index]
                    assertEquals(expected, vm.presentation.value.initialTable)
                    assertEquals(expected, table(vm))
                } finally {
                    // Test-owned optional wrapper only; original authenticated envelope is retained.
                    if (phoneOnly) DeviceUnlock(context, true).disable()
                }
            }
            else -> error("Unknown launch-selection phase")
        }
    }
}
