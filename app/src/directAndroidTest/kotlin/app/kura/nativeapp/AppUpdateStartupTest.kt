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
import io.github.phillip9933.appupdater.AndroidUpdateRelease
import io.github.phillip9933.appupdater.UpdateState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class AppUpdateStartupTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    private fun authorize() {
        if (Build.VERSION.SDK_INT <= 35) {
            val field = if (Build.VERSION.SDK_INT < 30) By.res("com.android.settings", "password_entry")
            else By.res(java.util.regex.Pattern.compile("com\\.android\\.systemui:id/(lockPassword|pinEntry)"))
            requireNotNull(device.wait(Until.findObject(field), 15000)).click()
            device.executeShellCommand("input text 2468")
            device.pressEnter()
        } else {
            assertNotNull(device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 15000))
            listOf("2", "4", "6", "8").forEach { device.findObject(By.text(it).pkg("com.android.systemui")).click() }
            device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
        }
    }

    private fun awaitReady(vm: VaultController) = runBlocking {
        withTimeout(15000) { while (vm.busy.value || vm.state.value !is VaultState.Unlocked) delay(25) }
    }

    @Test fun updateControlsWaitForOpeningAndBusyLockScreen() {
        check(context.packageName == "app.kura.wallet.prototype")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        authorize()
        assertTrue(waitForJourneyVault(device))
        lateinit var vm: VaultController
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().single()
            vm = ViewModelProvider(activity)[VaultController::class.java]
        }
        awaitReady(vm)
        val application = context.applicationContext as KuraUpdateApplication
        val manager = application.updateManager
        @Suppress("UNCHECKED_CAST")
        val updates = manager.javaClass.getDeclaredField("_state").apply { isAccessible = true }
            .get(manager) as MutableStateFlow<UpdateState>
        val original = updates.value
        val release = AndroidUpdateRelease("v99.0.0", "Synthetic startup update", "Kura-99.0.0.apk",
            "https://github.com/phillip9933/Kura/releases/download/v99.0.0/Kura-99.0.0.apk", 100L, null, null)
        // Hold only store opening, after the real credential succeeds. No key/auth substitution.
        val storeMutex = vm.store.javaClass.getDeclaredField("mutex").apply { isAccessible = true }.get(vm.store) as Mutex
        var openingHeld = false
        val busyRelease = CompletableDeferred<Unit>()
        var busyJob: Job? = null
        try {
            updates.value = original.copy(offer = false)
            runBlocking { vm.lock() }
            assertNotNull(device.wait(Until.findObject(By.desc("App updates")), 5000))
            assertTrue(storeMutex.tryLock())
            openingHeld = true
            device.findObject(By.desc("Unlock Kura")).click()
            authorize()
            runBlocking { withTimeout(15000) { while (vm.state.value !is VaultState.Opening) delay(25) } }
            assertTrue("No update entry while the vault is opening", device.wait(Until.gone(By.desc("App updates")), 3000))
            updates.value = original.copy(available = release, offer = true)
            instrumentation.waitForIdleSync()
            assertFalse("An update offer must wait for opening", device.hasObject(By.text("New version available")))
            application.updatePanelVisible.value = true
            instrumentation.waitForIdleSync()
            assertFalse("An existing manual panel must wait for opening", device.hasObject(By.text("Check for updates")))
            assertTrue(vm.state.value is VaultState.Opening)
            storeMutex.unlock()
            openingHeld = false
            awaitReady(vm)
            assertNotNull(device.wait(Until.findObject(By.text("Check for updates")), 5000))
            clickJourneyControl(device, "Done")
            // A settled locked screen still allows manual access. A pending operation does not.
            runBlocking { vm.lock() }
            assertNotNull(device.wait(Until.findObject(By.desc("App updates")), 5000))
            instrumentation.runOnMainSync { vm.autoUnlockArmed = true }
            assertTrue("No first-frame entry before automatic unlock starts", device.wait(Until.gone(By.desc("App updates")), 3000))
            instrumentation.runOnMainSync { vm.autoUnlockArmed = false }
            assertNotNull(device.wait(Until.findObject(By.desc("App updates")), 5000))
            clickJourneyControl(device, "App updates")
            assertNotNull(device.wait(Until.findObject(By.text("Check for updates")), 5000))
            instrumentation.runOnMainSync { busyJob = vm.launch { busyRelease.await() } }
            assertTrue(device.wait(Until.gone(By.text("Check for updates")), 3000))
            assertFalse(device.hasObject(By.desc("App updates")))
            assertTrue(vm.busy.value)
            busyRelease.complete(Unit)
            runBlocking { busyJob!!.join() }
            assertNotNull(device.wait(Until.findObject(By.text("Check for updates")), 5000))
            clickJourneyControl(device, "Done")
            assertNotNull(device.wait(Until.findObject(By.desc("App updates")), 5000))
        } finally {
            if (openingHeld) storeMutex.unlock()
            busyRelease.complete(Unit)
            runBlocking { busyJob?.join() }
            application.updatePanelVisible.value = false
            updates.value = original
            context.getSharedPreferences(kuraUpdateConfig.stateName, 0).edit().remove("dismissed_tag").commit()
        }
    }
}
