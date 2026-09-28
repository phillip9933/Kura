package app.kura.nativeapp

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in capture of real views with fictional data. Never included in a release APK. */
class StoreScreenshotsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    private fun click(label: String) {
        val control = device.wait(Until.findObject(By.desc(label)), 1000)
            ?: device.wait(Until.findObject(By.text(label)), 5000)
        assertNotNull("Missing $label", control)
        control!!.click()
        device.waitForIdle()
    }

    private fun capture(name: String) {
        val windows = mutableListOf<Pair<android.view.View, Int>>()
        instrumentation.runOnMainSync {
            WindowInspector.getGlobalWindowViews().forEach { view ->
                val params = view.layoutParams as? WindowManager.LayoutParams ?: return@forEach
                windows += view to params.flags
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                view.context.getSystemService(WindowManager::class.java).updateViewLayout(view, params)
            }
        }
        try {
            device.waitForIdle()
            Thread.sleep(500)
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                assertEquals(1080, bitmap.width)
                assertEquals(1920, bitmap.height)
                val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "store-screenshots")
                check(directory.isDirectory || directory.mkdirs())
                File(directory, "$name.jpg").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it))
                }
            } finally {
                bitmap.recycle()
            }
        } finally {
            instrumentation.runOnMainSync {
                windows.forEach { (view, flags) ->
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.flags = flags
                    view.context.getSystemService(WindowManager::class.java).updateViewLayout(view, params)
                }
            }
        }
    }

    @Test fun captureStoreListing() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("storeScreenshots") == "true")
        require(Build.HARDWARE in setOf("ranchu", "goldfish") && Build.VERSION.SDK_INT >= 34)
        require(instrumentation.targetContext.packageName == "app.kura.wallet.prototype")
        val context = instrumentation.targetContext
        device.wakeUp()
        context.startActivity(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertNotNull(device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 15000))
        device.waitForIdle()
        for (digit in listOf("2", "4", "6", "8")) {
            device.findObject(By.text(digit).pkg("com.android.systemui")).click()
        }
        device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
        assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 20000))
        lateinit var controller: VaultController
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            controller = ViewModelProvider(activity)[VaultController::class.java]
        }
        val saved = runBlocking { controller.work { controller.store.settings(it).toString() } }
        val ids = mutableListOf<Long>()
        try {
            runBlocking {
                val settings = JSONObject()
                    .put("themePreference", "dark").put("defaultScreenIndex", 1)
                    .put("isExpiryNotificationEnabled", false).put("showUpcomingPasses", false)
                    .put("paymentsGridColumns", 1).put("passesGridColumns", 1)
                    .put("identityGridColumns", 1).put("autoBackupEnabled", false)
                controller.work { vault ->
                    controller.store.saveSettings(vault, settings)
                    fun add(name: String, type: String, color: String, fields: JSONObject) {
                        val row = JSONObject().put("organizationName", name).put("type", type)
                            .put("sourceType", "pkpass").put("description", "Fictional demonstration pass")
                            .put("backgroundColor", color).put("foregroundColor", "#ffffff")
                            .put("labelColor", "#eeeeee").put("fields", fields.toString())
                            .put("barcodeValue", "KURA-DEMO-" + ids.size).put("barcodeFormat", "PKBarcodeFormatQR")
                        controller.store.insert(vault, "passes", row)
                        ids += controller.store.rows(vault, "passes").last { it.optString("organizationName") == name }.getLong("id")
                    }
                    fun field(label: String, value: String) = JSONObject().put("label", label).put("value", value)
                    fun primary(label: String, value: String) = JSONObject().put("primaryFields", JSONArray().put(field(label, value)))
                    add("Kura Air", "boardingPass", "#184c66", JSONObject()
                        .put("headerFields", JSONArray().put(field("FLIGHT", "KA 204")))
                        .put("primaryFields", JSONArray().put(field("TOKYO", "HND")).put(field("OSAKA", "KIX")))
                        .put("secondaryFields", JSONArray().put(field("PASSENGER", "Alex Example")).put(field("BOARDING", "09:30")))
                        .put("auxiliaryFields", JSONArray().put(field("GATE", "12")).put(field("SEAT", "18A")).put(field("GROUP", "2")))
                        .put("backFields", JSONArray().put(field("INFORMATION", "Fictional pass for demonstration. Not valid for travel."))))
                    add("Evening Sessions", "eventTicket", "#57356e", primary("LIVE MUSIC", "Garden concert"))
                    add("City Rail", "generic", "#245647", primary("DAY TICKET", "Explore the city"))
                    add("Neighbourhood Library", "storeCard", "#3d4874", primary("MEMBER", "Alex Example"))
                    add("Morning Coffee", "storeCard", "#674233", primary("REWARDS", "Your daily ritual"))
                    add("The Garden Club", "storeCard", "#375e43", primary("MEMBERSHIP", "Alex Example"))
                }
                controller.presentation.value = PresentationSettings.parse(settings)
                controller.refresh()
                // Only capture owned fictional rows, even if this disposable vault has other test fixtures.
                controller.items.value = controller.items.value.filter { it.table == "passes" && it.id in ids }
            }
            click("Passes")
            assertTrue(device.wait(Until.hasObject(By.desc("Open Kura Air")), 10000))
            capture("1-passes-dark")
            click("Open Kura Air")
            assertTrue(device.wait(Until.hasObject(By.desc("Pass barcode")), 10000))
            capture("2-boarding-pass")
            click("Pass barcode")
            assertTrue(device.wait(Until.hasObject(By.desc("Fullscreen barcode")), 10000))
            capture("3-fullscreen-barcode")
            click("Close barcode")
            click("Close")
            click("Cards")
            assertTrue(device.wait(Until.hasObject(By.desc("Open Neighbourhood Library")), 10000))
            capture("4-cards-dark")
            runBlocking { controller.preference("themePreference", "light") }
            device.waitForIdle()
            capture("5-cards-light")
        } finally {
            runBlocking {
                controller.work { vault ->
                    ids.forEach { controller.store.deleteRecord(vault, "passes", it) }
                    controller.store.saveSettings(vault, JSONObject(saved))
                }
                controller.presentation.value = PresentationSettings.parse(JSONObject(saved))
                controller.refresh()
            }
            device.pressHome()
        }
    }
}
