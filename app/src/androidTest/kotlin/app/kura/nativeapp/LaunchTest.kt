package app.kura.nativeapp

import org.json.JSONObject
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test

class LaunchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private fun click(text: String) {
        if(text in setOf("Edit","Delete","Archive","Unarchive","Share securely","Export encrypted pass","Export as .pkpass") && !device.hasObject(By.text(text)) && !device.hasObject(By.desc(text))) {
            (device.findObject(By.desc("More pass actions")) ?: device.findObject(By.desc("More item actions")))?.let {it.click();device.waitForIdle()}
        }
        if(text=="Settings") assertTrue(device.wait(Until.hasObject(By.desc("Settings")),10000))
        repeat(8) { attempt->
            val target = device.findObject(By.desc(text)) ?: device.findObject(By.text(text))
            try { if(target != null && !target.visibleBounds.isEmpty) {
                target.click(); device.waitForIdle(); return }
            } catch (_: StaleObjectException) {}
            val scroll = device.findObject(By.scrollable(true))
            if(scroll != null) scroll.scroll(if(attempt<4) Direction.DOWN else Direction.UP,.8f) else device.wait(Until.hasObject(By.text(text)),2000)
        }
        error("Missing UI: " + text)
    }
    private fun captureSynthetic(name: String) {
        require(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        val activity = arrayOfNulls<android.app.Activity>(1)
        instrumentation.runOnMainSync {
            activity[0] = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
            assertTrue(activity[0]!!.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
            activity[0]!!.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        try {
            device.waitForIdle()
            Thread.sleep(300)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            assertNotNull(screenshot)
            try {
                java.io.File(instrumentation.targetContext.getExternalFilesDir(null),name+".png").outputStream().use {
                    screenshot!!.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
            } finally { if(screenshot?.isMutable == true) screenshot.eraseColor(0); screenshot?.recycle() }
        } finally {
            instrumentation.runOnMainSync { activity[0]!!.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        }
    }
    private fun capturePassDetail() {
        require(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        if(android.os.Build.VERSION.SDK_INT<29) return
        val windows=mutableListOf<Pair<android.view.View,Int>>()
        instrumentation.runOnMainSync {
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach { view ->
                val params=view.layoutParams as? android.view.WindowManager.LayoutParams ?: return@forEach
                windows.add(view to params.flags)
                params.flags=params.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE.inv()
                view.context.getSystemService(android.view.WindowManager::class.java).updateViewLayout(view,params)
            }
        }
        try {
            device.waitForIdle(); Thread.sleep(300)
            val bitmap=instrumentation.uiAutomation.takeScreenshot()
            assertNotNull(bitmap)
            try {
                java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"pass-detail.png").outputStream().use {
                    bitmap!!.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                }
            } finally {if(bitmap?.isMutable==true) bitmap.eraseColor(0);bitmap?.recycle()}
        } finally {
            instrumentation.runOnMainSync {
                windows.forEach { (view,flags) ->
                    val params=view.layoutParams as android.view.WindowManager.LayoutParams
                    params.flags=flags
                    view.context.getSystemService(android.view.WindowManager::class.java).updateViewLayout(view,params)
                }
            }
        }
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
        val context = instrumentation.targetContext
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        authorize()
        click("Settings"); click("Load synthetic fixtures")
        val navigation = device.wait(Until.findObject(By.text("Cards")),10000)
        assertNotNull(navigation)
        assertTrue("Navigation must be at the bottom",navigation.visibleBounds.centerY()>device.displayHeight*3/4)
        assertFalse("Add options remain collapsed",device.hasObject(By.text("Import File")))
        click("Settings"); click("General Display"); click("App Theme"); click("Light"); click("Done")
        captureSynthetic("home-light")
        click("Settings"); click("General Display"); click("App Theme"); click("Dark"); click("Done")
        captureSynthetic("home-dark")
        click("Passes")
        if(device.hasObject(By.desc("Use two columns"))) click("Use two columns")
        captureSynthetic("passes-dark")
        click("Settings")
        assertTrue(device.wait(Until.hasObject(By.text("DATA & SECURITY")),5000))

        click("Done")
        click("Passes")
        UiScrollable(UiSelector().scrollable(true)).scrollToBeginning(20)
        assertTrue(device.wait(Until.hasObject(By.desc("Open Kura Test Transit 0")), 15000))
        val activity = arrayOfNulls<android.app.Activity>(1)
        instrumentation.runOnMainSync {
            activity[0] = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
        }
        val frames = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val uiWork = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val thread = android.os.HandlerThread("FrameMetrics").apply { start() }
        val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            frames.add(metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION))
            uiWork.add(metrics.getMetric(android.view.FrameMetrics.LAYOUT_MEASURE_DURATION) +
                metrics.getMetric(android.view.FrameMetrics.DRAW_DURATION) +
                metrics.getMetric(android.view.FrameMetrics.ANIMATION_DURATION))
        }
        instrumentation.runOnMainSync { activity[0]!!.window.addOnFrameMetricsAvailableListener(listener, android.os.Handler(thread.looper)) }
        try {
            repeat(3) { device.swipe(device.displayWidth/2, device.displayHeight*3/4, device.displayWidth/2, device.displayHeight/3, 30) }
            repeat(3) { device.swipe(device.displayWidth/2, device.displayHeight/3, device.displayWidth/2, device.displayHeight*3/4, 30) }
            device.waitForIdle()
        } finally {
            instrumentation.runOnMainSync { activity[0]!!.window.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely(); thread.join()
        }
        fun percentile(values: List<Long>): Double = values.sorted().let { if(it.isEmpty()) 0.0 else it[((it.size-1)*.95).toInt()]/1_000_000.0 }
        instrumentation.sendStatus(0, android.os.Bundle().apply {
            putString("stream", "\nKURA_SCROLL_FRAMES=" + frames.size + " TOTAL_P95_MS=" + percentile(frames) +
                " UI_WORK_P95_MS=" + percentile(uiWork) + " PSS_KIB=" + android.os.Debug.getPss() + "\n")
        })

        UiScrollable(UiSelector().scrollable(true)).scrollToBeginning(20)
        device.waitForIdle()
        val visiblePass = device.findObject(UiSelector().description("Open Kura Test Transit 0"))
        assertTrue("Expected the first pass after returning to the top",visiblePass.waitForExists(10000))
        visiblePass.click()
        assertTrue(device.wait(Until.hasObject(By.desc("Pass barcode")), 10000))
        capturePassDetail()
        assertTrue(device.hasObject(By.desc("More pass actions")));assertFalse(device.hasObject(By.desc("Export encrypted pass")))
        click("Delete")
        assertTrue(device.wait(Until.hasObject(By.text("Delete pass?")),5000))
        click("Cancel")
        assertFalse(device.hasObject(By.text("Full brightness")))
        click("Pass barcode")
        assertTrue(device.wait(Until.hasObject(By.desc("Fullscreen barcode")),10000))
        click("Rotate"); click("Copy barcode")
        click("Close barcode")
        click("Copy ticket code")
        instrumentation.runOnMainSync {
            val clipboard=context.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals("KURA-TEST-0",clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        click("Close")
        instrumentation.runOnMainSync { assertTrue(activity[0]!!.window.attributes.screenBrightness<1f) }
        device.findObject(UiSelector().description("Open Kura Test Transit 0")).click()
        click("Export encrypted pass")
        assertTrue(device.wait(Until.hasObject(By.text("Encrypt pass")),5000))
        click("Cancel")
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
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        authorize()
        click("Passes")
        UiScrollable(UiSelector().scrollable(true)).scrollToBeginning(20)
        assertTrue(device.wait(Until.hasObject(By.desc("Open Kura Test Transit 0")), 15000))
        val controller=arrayOfNulls<VaultController>(1)
        instrumentation.runOnMainSync {
            val current=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
            controller[0]=androidx.lifecycle.ViewModelProvider(current)[VaultController::class.java]
        }
        kotlinx.coroutines.runBlocking {
            controller[0]!!.work { vault ->
                for(type in listOf("boardingPass","coupon","storeCard","eventTicket","generic")) {
                    if(controller[0]!!.store.rows(vault,"passes").none {it.optString("organizationName")=="UI example "+type}) {
                        val fields=JSONObject().put("primaryFields",org.json.JSONArray()
                            .put(JSONObject().put("label",if(type=="boardingPass") "FROM" else "OFFER").put("value",if(type=="boardingPass") "TYO" else "20% OFF"))
                            .put(JSONObject().put("label","TO").put("value","KIX")))
                            .put("headerFields",org.json.JSONArray().put(JSONObject().put("label","TIER").put("value","VIP")))
                        controller[0]!!.store.insert(vault,"passes",JSONObject().put("organizationName","UI example "+type).put("type",type)
                            .put("fields",fields.toString()).put("barcodeValue","TEST").put("barcodeFormat","PKBarcodeFormatQR")
                            .put("backgroundColor",if(type=="coupon") "rgb(255,255,255)" else "rgb(0,45,35)")
                            .put("foregroundColor","#ffffff").put("thumbnailImagePath",if(type=="generic") "missing.png.enc" else JSONObject.NULL)
                            .put("relevantDate","2026-12-01T12:00:00Z"))
                    }
                }
            }
            controller[0]!!.refresh()
        }
        if(device.hasObject(By.desc("Use two columns"))) click("Use two columns")
        val search=device.findObject(UiSelector().className("android.widget.EditText"))
        search.setText("UI example")
        assertTrue(device.wait(Until.hasObject(By.desc("Open UI example boardingPass")),10000))
        try { captureSynthetic("pass-types-dark") } finally { search.setText(""); removeUiGalleryFixtures() }

    }
}