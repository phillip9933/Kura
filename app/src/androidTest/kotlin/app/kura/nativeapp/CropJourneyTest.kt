package app.kura.nativeapp

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test

class CropJourneyTest {
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
    private fun authorize() {
        // Real system credential UI; no auth bypass or test master key injection.
        val pin = device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 15000)
        assertNotNull("Expected system credential prompt", pin)
        for (digit in listOf("2", "4", "6", "8")) device.findObject(By.text(digit).pkg("com.android.systemui")).click()
        device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
        assertTrue(device.wait(Until.hasObject(By.desc("Settings")), 20000))
    }

    private fun fixture(): ByteArray {
        val matrix = com.google.zxing.MultiFormatWriter().encode("Kura crop fixture",com.google.zxing.BarcodeFormat.QR_CODE,320,320)
        val bitmap = android.graphics.Bitmap.createBitmap(640,640,android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        val pixels = IntArray(320*320) { i -> if(matrix[i%320,i/320]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        try {
            bitmap.setPixels(pixels,0,320,160,160,320,320)
            return java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
        } finally { pixels.fill(0); bitmap.eraseColor(android.graphics.Color.TRANSPARENT); bitmap.recycle() }
    }
    private fun stageImage(bytes: ByteArray) {
        instrumentation.runOnMainSync {
            val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().single()
            // Inject only a synthetic captured image; real system authentication has already completed.
            androidx.lifecycle.ViewModelProvider(activity)[VaultController::class.java].pending.cropBytes = bytes
        }
    }
    @Test fun cropControlsStageConfirmationAndCancelWipesInput() {
        device.wakeUp()
        val context = instrumentation.targetContext
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        authorize()
        val bytes = fixture(); stageImage(bytes)
        assertTrue(device.wait(Until.hasObject(By.text("Scan captured image")),10000))
        val slider = device.findObject(UiSelector().description("Crop size"))
        val bounds = slider.bounds
        device.click(bounds.centerX(),bounds.centerY())
        assertTrue(device.wait(Until.gone(By.text("Crop: 100%")),5000))
        assertFalse(device.hasObject(By.desc("Horizontal crop position")))
        assertFalse(device.hasObject(By.desc("Vertical crop position")))
        val crop=device.findObject(By.desc("Drag crop area")).visibleBounds
        device.swipe(crop.centerX(),crop.centerY(),crop.centerX()+12,crop.centerY()+8,12)
        click("Scan")
        assertTrue(device.wait(Until.hasObject(By.text("Import 1 item(s)?")),15000))
        assertTrue(bytes.all { it == 0.toByte() })
        click("Cancel")
        val canceled = fixture(); stageImage(canceled)
        assertTrue(device.wait(Until.hasObject(By.text("Scan captured image")),10000))
        click("Cancel")
        assertTrue(canceled.all { it == 0.toByte() })
        device.pressHome(); Thread.sleep(1200)

    }
    @Test fun cropDecoderRejectsInvalidCoordinatesAndMalformedImages() {
        val bytes = fixture()
        try {
            for(fraction in listOf(Float.NaN,0f,1.1f)) {
                try { CapturedImageScanner.scan(bytes,fraction,.5f,.5f); org.junit.Assert.fail() }
                catch(_: IllegalArgumentException) {}
            }
            try { CapturedImageScanner.scan(byteArrayOf(1,2),1f,.5f,.5f); org.junit.Assert.fail() }
            catch(_: IllegalArgumentException) {}
        } finally { bytes.fill(0) }
    }
    @Test fun cropDecoderReadsAllFourFormats() {
        for(format in listOf(com.google.zxing.BarcodeFormat.QR_CODE,com.google.zxing.BarcodeFormat.AZTEC,
            com.google.zxing.BarcodeFormat.PDF_417,com.google.zxing.BarcodeFormat.CODE_128)) {
            val matrix = com.google.zxing.MultiFormatWriter().encode("KURA123456",format,640,320)
            val width = matrix.width; val height = matrix.height
            val bitmap = android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width*height) { i -> if(matrix[i%width,i/width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
            try {
                bitmap.setPixels(pixels,0,width,0,0,width,height)
                val bytes = java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
                try { assertEquals("KURA123456",CapturedImageScanner.scan(bytes,1f,.5f,.5f).text) }
                finally { bytes.fill(0) }
            } finally { pixels.fill(0); bitmap.eraseColor(android.graphics.Color.TRANSPARENT); bitmap.recycle() }
        }
    }
}
