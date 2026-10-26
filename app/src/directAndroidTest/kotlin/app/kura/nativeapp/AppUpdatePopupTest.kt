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
import io.github.phillip9933.appupdater.AndroidUpdateRelease
import io.github.phillip9933.appupdater.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AppUpdatePopupTest {
    @Test fun availableUpdatePromptsWhileLockedAndLaterKeepsManualAccess() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "app.kura.wallet.prototype")
        val device = UiDevice.getInstance(instrumentation)
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val legacy = if (Build.VERSION.SDK_INT < 30) device.wait(Until.findObject(By.res("com.android.settings", "password_entry")), 5000) else null
        if (legacy != null) {
            legacy.click()
            legacy.text = "2468"
            device.pressEnter()
        } else {
            val pin = device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 5000)
            if (pin != null) {
                for (digit in listOf("2", "4", "6", "8")) device.findObject(By.text(digit).pkg("com.android.systemui")).click()
                device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
            }
        }
        assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 20_000))
        lateinit var vm: VaultController
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().single()
            vm = ViewModelProvider(activity)[VaultController::class.java]
        }
        runBlocking { vm.lock() }
        val manager = (context.applicationContext as KuraUpdateApplication).updateManager
        // Inject public synthetic release metadata only; do not call a live feed or replace verification.
        @Suppress("UNCHECKED_CAST")
        val state = manager.javaClass.getDeclaredField("_state").apply { isAccessible = true }
            .get(manager) as MutableStateFlow<UpdateState>
        val original = state.value
        val release = AndroidUpdateRelease("v99.0.0", "Synthetic update", "Kura-99.0.0.apk",
            "https://github.com/phillip9933/Kura/releases/download/v99.0.0/Kura-99.0.0.apk", 100L, null, null)
        try {
            state.value = original.copy(available = release, offer = true)
            assertNotNull(device.wait(Until.findObject(By.text("New version available")), 10_000))
            device.findObject(By.text("Later")).click()
            assertTrue(device.wait(Until.gone(By.text("New version available")), 5000))
            assertFalse(manager.state.value.offer)
            assertEquals(release, manager.state.value.available)
            assertEquals(release.tag, context.getSharedPreferences(kuraUpdateConfig.stateName, 0).getString("dismissed_tag", null))
            device.findObject(By.text("App updates · update available")).click()
            assertNotNull(device.wait(Until.findObject(By.text("Available: v99.0.0")), 5000))
            // A new offer arriving during a manual check must stay in this one panel.
            state.value = original.copy(available = release.copy(tag = "v99.0.1"), offer = true)
            assertNotNull(device.wait(Until.findObject(By.text("Available: v99.0.1")), 5000))
            assertFalse(device.hasObject(By.text("New version available")))
            device.findObject(By.text("Done")).click()
            assertFalse(manager.state.value.offer)
            state.value = original.copy(available = release.copy(tag = "v99.0.2"), offer = true)
            assertNotNull(device.wait(Until.findObject(By.text("New version available")), 5000))
            device.findObject(By.text("View update")).click()
            assertNotNull(device.wait(Until.findObject(By.text("Available: v99.0.2")), 5000))
        } finally {
            (context.applicationContext as KuraUpdateApplication).updatePanelVisible.value = false
            state.value = original
            context.getSharedPreferences(kuraUpdateConfig.stateName, 0).edit().remove("dismissed_tag").commit()
        }
    }
}
