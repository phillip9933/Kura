package app.kura.nativeapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.*
import app.kura.feature.FormMemory
import app.kura.nativecore.CardField
import dev.offlinescan.core.Quad
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class CardScannerIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private fun disposable() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        check(context.packageName == "app.kura.wallet.prototype")
    }
    private fun image(japanese: Boolean = false): ByteArray {
        val bitmap = Bitmap.createBitmap(1800, 1100, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        try {
            val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(35, 42, 50))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawRoundRect(RectF(120f, 110f, 1680f, 990f), 35f, 35f, paint)
            paint.color = Color.BLACK; paint.textSize = 62f
            canvas.drawText(if (japanese) "氏名: テスト 太郎" else "Organization: Synthetic Club", 190f, 310f, paint)
            canvas.drawText(if (japanese) "会員番号: 00001234" else "Member ID: 00001234", 190f, 500f, paint)
            canvas.drawText(if (japanese) "有効期限: 2030-12-31" else "Expires: 2030-12-31", 190f, 690f, paint)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            return output.toByteArray()
        } finally { bitmap.eraseColor(0); bitmap.recycle(); output.close() }
    }
    @Test fun realEngineDetectsCardAndRecognizesExistingFieldsOffline() = runBlocking {
        disposable()
        val bytes = image()
        var cropped: ByteArray? = null
        try {
            val engine = CardScanEngine(context)
            val corners = engine.detect(bytes)
            assertNotEquals("Synthetic card edges should be detected", Quad.FULL, corners)
            assertTrue(corners.points.all { it.x in 0.0..1.0 && it.y in 0.0..1.0 })
            cropped = engine.crop(bytes, corners, 0)
            val results = engine.recognize(cropped, listOf(CardField("member", "Member number"), CardField("expiry", "Expiry date")))
            assertEquals("00001234", results.single { it.field.key == "member" }.value)
            assertEquals("2030-12-31", results.single { it.field.key == "expiry" }.value)
            assertTrue(File(context.cacheDir, "card-scans").listFiles().orEmpty().isEmpty())
        } finally { bytes.fill(0); cropped?.fill(0) }
    }
    @Test fun japaneseLabelAutofillUsesBundledModel() = runBlocking {
        disposable()
        val bytes = image(japanese = true)
        try {
            val text = CardScanEngine(context).recognizeText(bytes)
            val results = app.kura.nativecore.CardAutofill.suggest(text, listOf(CardField("member", "Member number"), CardField("expiry", "Expiry date")))
            assertEquals("Synthetic Japanese fixture OCR: $text", "00001234", results.singleOrNull { it.field.key == "member" }?.value)
            assertEquals("Synthetic Japanese fixture OCR: $text", "2030-12-31", results.singleOrNull { it.field.key == "expiry" }?.value)
        } finally { bytes.fill(0) }
    }
    @Test fun cancellationLeavesNoPlaintextWorkspace() = runBlocking {
        disposable()
        val bytes = image()
        try {
            val job = launch(Dispatchers.Default) { CardScanEngine(context).recognize(bytes, listOf(CardField("n", "Member number"))) }
            delay(100)
            job.cancelAndJoin()
            assertTrue(File(context.cacheDir, "card-scans").listFiles().orEmpty().isEmpty())
        } finally { bytes.fill(0) }
    }
    private fun click(label: String) {
        repeat(12) {
            if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
            val found = device.findObject(By.text(label))
            if (found != null && !found.visibleBounds.isEmpty) { found.click(); return }
            val scroll = device.findObject(By.scrollable(true))?.visibleBounds
            if (scroll != null && !scroll.isEmpty) device.swipe(scroll.centerX(), scroll.bottom - 100, scroll.centerX(), scroll.top + 100, 30)
            device.waitForIdle()
        }
        fail("Missing $label")
    }
    private fun controller(): VaultController {
        lateinit var vm: VaultController
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            vm = ViewModelProvider(activity)[VaultController::class.java]
        }
        return vm
    }
    private fun captureCropPreview(name: String = "crop-dialog") {
        if (InstrumentationRegistry.getArguments().getString("captureCropPreview") != "true") return
        disposable()
        val windows = mutableListOf<Pair<android.view.View, Int>>()
        instrumentation.runOnMainSync {
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach { view ->
                val params = view.layoutParams as? android.view.WindowManager.LayoutParams ?: return@forEach
                windows += view to params.flags
                params.flags = params.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE.inv()
                view.context.getSystemService(android.view.WindowManager::class.java).updateViewLayout(view, params)
            }
        }
        try {
            settleJourneyUi(device)
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        } finally {
            instrumentation.runOnMainSync {
                windows.forEach { (view, flags) ->
                    val params = view.layoutParams as android.view.WindowManager.LayoutParams
                    params.flags = flags
                    view.context.getSystemService(android.view.WindowManager::class.java).updateViewLayout(view, params)
                }
            }
        }
    }
    @Test fun optionalAutofillReviewsExistingFieldsAndEncryptsOnlyAfterConfirmation() {
        disposable()
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // Android 15 with a hardware keyboard exposes a system PIN text field.
        val legacy = when {
            Build.VERSION.SDK_INT < 30 -> device.wait(Until.findObject(By.res("com.android.settings", "password_entry")), 5000)
            Build.VERSION.SDK_INT <= 35 -> device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile("com\\.android\\.systemui:id/(lockPassword|pinEntry)"))), 5000)
            else -> null
        }
        if (legacy != null) {
            legacy.click()
            device.executeShellCommand("input text 2468")
            device.pressEnter()
        } else {
            val pin = device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 10_000)
            if (pin != null) {
                listOf("2", "4", "6", "8").forEach { device.findObject(By.text(it).pkg("com.android.systemui")).click() }
                device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
            }
        }
        assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 20_000))
        val vm = controller()
        val beforeIds = runBlocking { vm.work { vm.store.rows(it, "passes").map { row -> row.getLong("id") }.toSet() } }
        val before = beforeIds.size
        lateinit var form: FormMemory
        fun start() {
            instrumentation.runOnMainSync {
                val memory = vm.pending.uiMemory
                form = FormMemory().apply { initialize("passes", null, "wallets", vm.presentation.value.preferences) }
                memory.manualForms["passes"] = form; memory.form = form
                memory.manualSection.value = "wallets"; memory.manualCategory.value = "Membership"
                memory.table.value = "wallets"; memory.adding.value = true; memory.addCardKind.value = "passes"
                vm.pending.imageTarget = ImageTarget("passes", -1, "frontImagePath")
                vm.pending.cropBytes = image()
            }
            assertNotNull(device.wait(Until.findObject(By.text("Use crop").enabled(true)), 20_000))
            repeat(8) {
                if (!device.hasObject(By.text("Card aligned automatically. Check that every detail fits."))) {
                    device.findObject(By.desc("Crop adjustments"))?.scroll(Direction.DOWN, .35f)
                    settleJourneyUi(device)
                }
            }
            assertTrue(device.hasObject(By.text("Card aligned automatically. Check that every detail fits.")))
        }
        start()
        clickJourneyControl(device, "Rotate")
        clickJourneyControl(device, "90°")
        assertNotNull(device.wait(Until.findObject(By.checkable(true).checked(true).hasDescendant(By.text("90°"))), 5000))
        captureCropPreview("rotated-crop")
        clickJourneyControl(device, "270°")
        assertNotNull(device.wait(Until.findObject(By.checkable(true).checked(true).hasDescendant(By.text("270°"))), 5000))
        val angleSlider = device.findObject(By.desc("Photo rotation angle"))
        assertNotNull(angleSlider)
        val sliderBounds = angleSlider.visibleBounds
        device.swipe(sliderBounds.centerX(), sliderBounds.centerY(), sliderBounds.left + sliderBounds.width() * 2 / 3, sliderBounds.centerY(), 20)
        assertTrue("Custom slider changes the live crop", device.wait(Until.gone(By.text("Fine rotation: 0.0°")), 5000))
        captureCropPreview("rotation-controls")
        clickJourneyControl(device, "0°")
        assertNotNull(device.wait(Until.findObject(By.checkable(true).checked(true).hasDescendant(By.text("0°"))), 5000))
        clickJourneyControl(device, "Zoom")
        assertFalse("Document corner manipulation is no longer required", device.hasObject(By.desc("Corner horizontal position")))
        assertFalse("There is no separate manual crop screen", device.hasObject(By.text("Use manual crop instead")))
        clickJourneyControl(device, "Full photo")
        assertNotNull(findJourneyNode(device, By.text("Shape: Photo proportions")))
        clickJourneyControl(device, "Auto fit")
        assertNotNull(findJourneyNode(device, By.text("Shape: Landscape card")))
        clickJourneyControl(device, "Zoom")
        val mask = device.findObject(By.desc("Card crop preview"))
        assertNotNull(mask)
        val bounds = mask.visibleBounds
        assertFalse("The preview stays visible while adjusting", bounds.isEmpty)
        val beforeZoom = requireNotNull(device.findObject(By.textStartsWith("Zoom:"))).text
        assertTrue(device.findObject(UiSelector().description("Card crop preview")).pinchOut(50, 20))
        assertTrue("Pinching the photo actually changes its scale", device.wait(Until.gone(By.text(beforeZoom)), 5000))
        assertEquals("The mask viewport stays stationary", bounds, device.findObject(By.desc("Card crop preview")).visibleBounds)
        assertTrue("Crop confirmation stays reachable", device.findObject(By.text("Use crop")).isEnabled)
        captureCropPreview()
        click("Use crop")
        assertTrue(device.wait(Until.hasObject(By.text("Autofill card details?")), 20_000))
        click("Cancel import")
        assertTrue(form.customValues["Member number"].orEmpty().isBlank())
        assertTrue(vm.pending.draftImages.isEmpty())
        assertEquals(before, runBlocking { vm.work { vm.store.rows(it, "passes").size } })
        start()
        click("Use crop")
        assertTrue(device.wait(Until.hasObject(By.text("Autofill card details?")), 20_000))
        click("Use image only")
        assertTrue(device.wait(Until.gone(By.text("Autofill card details?")), 20_000))
        assertTrue(form.customValues["Member number"].orEmpty().isBlank())
        assertEquals(before, runBlocking { vm.work { vm.store.rows(it, "passes").size } })
        assertTrue(vm.pending.draftImages["frontImagePath"]!!.name.endsWith(".enc"))
        instrumentation.runOnMainSync { vm.pending.discardDraft() }
        start()
        click("Use crop")
        assertTrue(device.wait(Until.hasObject(By.text("Autofill card details?")), 20_000))
        click("Review autofill")
        assertTrue(device.wait(Until.hasObject(By.text("Review recognized details")), 45_000))
        assertTrue(form.customValues["Member number"].orEmpty().isBlank())
        click("Apply selected details and use image")
        assertTrue(device.wait(Until.gone(By.text("Review recognized details")), 20_000))
        assertEquals("00001234", form.customValues["Member number"])
        assertEquals(before, runBlocking { vm.work { vm.store.rows(it, "passes").size } })
        assertTrue(vm.pending.draftImages["frontImagePath"]!!.name.endsWith(".enc"))
        click("Save")
        device.waitForIdle()
        runBlocking {
            vm.work { opened ->
                val row = vm.store.rows(opened, "passes").single { it.optString("organizationName") == "Synthetic Club" && it.getLong("id") !in beforeIds }
                assertTrue(row.optString("fields").contains("00001234"))
                assertTrue(File(opened.directory, "media/" + row.getString("frontImagePath")).isFile)
            }
        }
    }
}
