package app.kura.nativeapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.WindowManager
import android.view.KeyEvent
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import app.kura.feature.FormMemory
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** A real mobile-layout regression; the same assertion also exercises the old UI. */
class CardCropLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    private fun open(): VaultController {
        check(context.packageName == "app.kura.wallet.prototype")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val legacy = when {
            Build.VERSION.SDK_INT < 30 -> device.wait(Until.findObject(By.res("com.android.settings", "password_entry")), 5000)
            Build.VERSION.SDK_INT <= 35 -> device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile("com\\.android\\.systemui:id/(lockPassword|pinEntry)"))), 5000)
            else -> null
        }
        val pin = if (legacy == null) device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 10_000) else null
        if (legacy != null) {
            legacy.click(); device.executeShellCommand("input text 2468"); device.pressEnter()
        } else if (pin != null) {
            listOf("2", "4", "6", "8").forEach { device.findObject(By.text(it).pkg("com.android.systemui")).click() }
            device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
        }
        assertTrue(waitForJourneyVault(device))
        lateinit var controller: VaultController
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().single()
            controller = ViewModelProvider(activity)[VaultController::class.java]
        }
        return controller
    }

    private fun photo(plain: Boolean = false): ByteArray {
        val bitmap = Bitmap.createBitmap(1600, 1000, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(40, 50, 60))
            if (!plain) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(235, 242, 250) }
            canvas.drawRoundRect(RectF(140f, 110f, 1460f, 890f), 36f, 36f, paint)
            paint.color = Color.rgb(20, 45, 75); paint.textSize = 78f
            canvas.drawText("SYNTHETIC CLUB", 220f, 350f, paint)
            paint.textSize = 64f
            canvas.drawText("Member: 00001234", 220f, 550f, paint)
            }
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        } finally { bitmap.eraseColor(0); bitmap.recycle() }
    }

    private fun <T> screenshot(use: (Bitmap) -> T): T {
        check(context.packageName == "app.kura.wallet.prototype")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
        val windows = mutableListOf<Pair<android.view.View, Int>>()
        instrumentation.runOnMainSync {
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach { view ->
                val params = view.layoutParams as? WindowManager.LayoutParams ?: return@forEach
                windows += view to params.flags
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                view.context.getSystemService(WindowManager::class.java).updateViewLayout(view, params)
            }
        }
        try {
            settleJourneyUi(device)
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { return use(bitmap) } finally { bitmap.recycle() }
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

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureCropPreview") != "true") return
        if (Build.VERSION.SDK_INT < 29) return // Public window inspection is unavailable; controls/output tests still run.
        screenshot { bitmap -> File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        } }
    }

    private fun previewPixels(): IntArray = screenshot { bitmap ->
        val bounds = requireNotNull(device.findObject(By.desc("Card crop preview"))).visibleBounds
        IntArray(bounds.width() * bounds.height()).also { pixels ->
            bitmap.getPixels(pixels, 0, bounds.width(), bounds.left, bounds.top, bounds.width(), bounds.height())
        }
    }

    private fun begin(vm: VaultController, bytes: ByteArray = photo()) {
        instrumentation.runOnMainSync {
            val form = FormMemory().apply { initialize("passes", null, "wallets", vm.presentation.value.preferences) }
            vm.pending.uiMemory.apply {
                this.form = form; manualForms["passes"] = form
                manualSection.value = "wallets"; manualCategory.value = "Membership"
                table.value = "wallets"; adding.value = true; addCardKind.value = "passes"
            }
            vm.pending.imageTarget = ImageTarget("passes", -1, "frontImagePath")
            vm.pending.cropBytes = bytes
        }
        assertNotNull(device.wait(Until.findObject(By.text("Use crop").enabled(true)), 20_000))
    }

    /** Scrolling to read a value must not change it, unlike clicking a preset. */
    private fun findAdjustment(selector: BySelector): UiObject2? {
        repeat(20) { attempt ->
            settleJourneyUi(device)
            device.findObject(selector)?.let { if (!it.visibleBounds.isEmpty) return it }
            device.findObject(By.desc("Crop adjustments"))?.scroll(
                if (attempt < 10) Direction.DOWN else Direction.UP, .35f)
        }
        return null
    }

    @Test fun imageAndAdjustmentControlsRemainVisibleTogether() {
        val vm = open()
        begin(vm)
        try {
            assertNotNull(device.wait(Until.findObject(By.text("Use crop").enabled(true)), 20_000))
            val legacy = device.wait(Until.hasObject(By.text("Drag the corners to adjust the detected card edges.")), 1500)
            clickJourneyControl(device, if (legacy) "Fine-tune" else "Move")
            val vertical = if (legacy) "Corner vertical position" else "Move photo down"
            repeat(8) {
                val control = device.findObject(By.desc(vertical))
                if (control == null || control.visibleBounds.height() < 30) {
                    device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, .35f)
                    settleJourneyUi(device)
                }
            }
            capture("crop-layout-review")
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "crop-layout-review.xml"))
            assertNotNull("Vertical adjustment must be usable", device.findObject(By.desc(vertical)))
            val preview = device.findObject(By.desc(if (legacy) "Drag the four card corners" else "Card crop preview"))
            assertNotNull("The photo must stay visible while adjusting it", preview)
            assertTrue("The visible photo must not collapse to a sliver", preview.visibleBounds.height() >= 120)
            assertTrue("Confirmation stays available beside the live preview", device.findObject(By.text("Use crop")).isEnabled)
        } finally {
            if (device.hasObject(By.text("Cancel import")) || device.hasObject(By.desc("Cancel import"))) clickJourneyControl(device, "Cancel import")
            instrumentation.runOnMainSync { vm.pending.discardDraft() }
        }
    }

    @Test fun fullPhotoRemainsUsableWhenDetectionFindsNoCard() {
        val vm = open()
        begin(vm, photo(plain = true))
        try {
            repeat(8) {
                if (!device.hasObject(By.textStartsWith("No reliable edges found"))) {
                    device.findObject(By.desc("Crop adjustments"))?.scroll(Direction.DOWN, .35f)
                    settleJourneyUi(device)
                }
            }
            assertNotNull(device.wait(Until.findObject(By.textStartsWith("No reliable edges found")), 5000))
            repeat(2) {
                clickJourneyControl(device, "Full photo")
                assertNotNull("Resetting an unchanged source must not disable cropping", device.wait(Until.findObject(By.text("Use crop").enabled(true)), 3000))
            }
            clickJourneyControl(device, "Use crop")
            assertNotNull(device.wait(Until.findObject(By.text("Autofill card details?")), 20_000))
            assertReviewUsable()
            clickJourneyControl(device, "Adjust crop")
            assertNotNull(device.wait(Until.findObject(By.text("Use crop").enabled(true)), 3000))
        } finally {
            clickJourneyControl(device, "Cancel import")
            instrumentation.runOnMainSync { vm.pending.discardDraft() }
        }
    }

    private fun assertReviewUsable() {
        val pane = requireNotNull(device.findObject(By.desc("Card review")))
        assertTrue("Review must retain usable scrolling space", pane.visibleBounds.height() >=
            80 * context.resources.displayMetrics.density)
        capture("crop-autofill-offer")
    }

    @Test fun keyboardCanSelectAnAdjustmentWithoutMovingThePreview() {
        val vm = open()
        begin(vm)
        try {
            clickJourneyControl(device, "Move")
            var zoomFocused = false
            repeat(16) {
                if (!zoomFocused) {
                    device.pressKeyCode(KeyEvent.KEYCODE_TAB)
                    settleJourneyUi(device)
                    val focused = device.findObject(By.focused(true))
                    zoomFocused = focused?.text == "Zoom" || focused?.findObject(By.text("Zoom")) != null
                }
            }
            assertTrue("Keyboard traversal reaches the Zoom mode", zoomFocused)
            device.pressEnter()
            settleJourneyUi(device)
            assertNotNull("Enter activates the focused mode", device.findObject(By.desc("Card zoom")))
            assertNotNull(device.findObject(By.desc("Card crop preview")))
            assertTrue(device.findObject(By.text("Use crop")).isEnabled)
            capture("crop-keyboard-focus")
        } finally {
            clickJourneyControl(device, "Cancel import")
            instrumentation.runOnMainSync { vm.pending.discardDraft() }
        }
    }

    @Test fun cropReviewPreservesShapeRotationZoomAndPosition() {
        val vm = open()
        begin(vm)
        var before: IntArray? = null
        var after: IntArray? = null
        try {
            clickJourneyControl(device, "Shape: Photo proportions")
            clickJourneyControl(device, "Portrait card")
            clickJourneyControl(device, "Rotate")
            clickJourneyControl(device, "270°")
            clickJourneyControl(device, "Rotate right half a degree")
            clickJourneyControl(device, "Zoom")
            clickJourneyControl(device, "Zoom in")
            clickJourneyControl(device, "Zoom in")
            val zoom = requireNotNull(findAdjustment(By.textStartsWith("Zoom:"))).text
            clickJourneyControl(device, "Move")
            clickJourneyControl(device, "Move photo left")
            clickJourneyControl(device, "Move photo down")
            if (Build.VERSION.SDK_INT >= 29) before = previewPixels()
            capture("crop-edited-before-review")
            clickJourneyControl(device, "Use crop")
            assertNotNull(device.wait(Until.findObject(By.text("Autofill card details?")), 20_000))
            assertReviewUsable()
            clickJourneyControl(device, "Adjust crop")
            assertNotNull(device.wait(Until.findObject(By.text("Use crop").enabled(true)), 10_000))
            if (before != null) {
                after = previewPixels()
                assertArrayEquals("Returning from OCR review preserves the actual visible framing", before, after)
            }
            assertNotNull(findAdjustment(By.text("Shape: Portrait card")))
            clickJourneyControl(device, "Zoom")
            assertNotNull(findAdjustment(By.text(zoom)))
            clickJourneyControl(device, "Rotate")
            assertNotNull(findAdjustment(By.checkable(true).checked(true).hasDescendant(By.text("270°"))))
            assertNotNull(findAdjustment(By.text("Fine rotation: 0.5°")))
            clickJourneyControl(device, "Rotate right half a degree")
            assertNotNull(findAdjustment(By.checkable(true).checked(true).hasDescendant(By.text("270°"))))
            assertNotNull(findAdjustment(By.text("Fine rotation: 1.0°")))
            capture("crop-edited-after-review")
        } finally {
            before?.fill(0); after?.fill(0)
            clickJourneyControl(device, "Cancel import")
            instrumentation.runOnMainSync { vm.pending.discardDraft() }
        }
    }
}
