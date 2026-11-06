package app.kura.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import app.kura.nativecore.CardCrop
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs

class CardRotationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun guard() {
        check(context.packageName == "app.kura.wallet.prototype")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
    }
    private fun source(width: Int = 80, height: Int = 40): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap); val paint = Paint()
            listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW).forEachIndexed { i, color ->
                paint.color = color
                val x = (i % 2) * width / 2f; val y = (i / 2) * height / 2f
                canvas.drawRect(x, y, x + width / 2f, y + height / 2f, paint)
            }
            ByteArrayOutputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); stream.toByteArray() }
        } finally { bitmap.eraseColor(0); bitmap.recycle() }
    }
    private fun decode(bytes: ByteArray) = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inMutable = true }))
    private fun closeColor(expected: Int, actual: Int) {
        assertTrue("Red", abs(Color.red(expected) - Color.red(actual)) < 40)
        assertTrue("Green", abs(Color.green(expected) - Color.green(actual)) < 40)
        assertTrue("Blue", abs(Color.blue(expected) - Color.blue(actual)) < 40)
    }
    @Test fun quarterTurnsRotatePixelsBeforeFullImageCrop() = runBlocking {
        guard(); val original = source(); val copy = original.copyOf()
        try {
            val expected = listOf(
                0f to listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW),
                90f to listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN),
                180f to listOf(Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED),
                -90f to listOf(Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE))
            for ((angle, colors) in expected) {
                val framing = CardCrop(80, 40, if (angle == 90f || angle == -90f) .5 else 2.0, degrees = angle.toDouble())
                val cropped = CardScanEngine(context).crop(original, framing.quad(), 0)
                try {
                    val bitmap = decode(cropped)
                    try {
                        assertEquals(if (angle == 90f || angle == -90f) 40 else 80, bitmap.width)
                        assertEquals(if (angle == 90f || angle == -90f) 80 else 40, bitmap.height)
                        colors.forEachIndexed { i, color -> closeColor(color, bitmap.getPixel(bitmap.width * (if (i % 2 == 0) 1 else 3) / 4,
                            bitmap.height * (if (i / 2 == 0) 1 else 3) / 4)) }
                    } finally { bitmap.eraseColor(0); bitmap.recycle() }
                } finally { cropped.fill(0) }
            }
            assertArrayEquals("Original photo is never resampled in place", copy, original)
        } finally { original.fill(0); copy.fill(0) }
    }
    @Test fun customAngleMatchesPreviewBoundsAndRepeatedEditsUseOriginal() = runBlocking {
        guard()
        val bitmap = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(400 * 240) { i -> Color.rgb((i % 400) * 255 / 399, (i / 400) * 255 / 239, 64) }
        val original = try {
            bitmap.setPixels(pixels, 0, 400, 0, 0, 400, 240)
            ByteArrayOutputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); stream.toByteArray() }
        } finally { pixels.fill(0); bitmap.eraseColor(0); bitmap.recycle() }
        val copy = original.copyOf()
        try {
            val engine = CardScanEngine(context)
            for (angle in listOf(-30.0, 0.0, 33.0, 90.0, 180.0, 270.0)) for (zoom in listOf(1.0, 2.5)) {
                val framing = CardCrop(400, 240, 1.586, zoom, angle, .7, -.5)
                val first = engine.crop(original, framing.quad(), 0)
                val second = engine.crop(original, framing.quad(), 0)
                try {
                    assertArrayEquals("Repeated edits sample the same source", first, second)
                    val output = decode(first)
                    try {
                        assertEquals(1.586, output.width.toDouble() / output.height, .05)
                        for (x in listOf(.2, .5, .8)) for (y in listOf(.2, .5, .8)) {
                            val point = framing.frameToImage(x, y)
                            val color = output.getPixel(((output.width - 1) * x).toInt(), ((output.height - 1) * y).toInt())
                            assertTrue("Output red matches visible source at $angle/$zoom", abs(Color.red(color) - point.x * 255) < 7)
                            assertTrue("Output green matches visible source at $angle/$zoom", abs(Color.green(color) - point.y * 255) < 7)
                            val preview = floatArrayOf((point.x * 400).toFloat(), (point.y * 240).toFloat())
                            cardCropMatrix(framing, 400, 240, Rect(20f, 30f, 320f, 230f)).mapPoints(preview)
                            assertEquals((20 + 300 * x).toFloat(), preview[0], .01f)
                            assertEquals((30 + 200 * y).toFloat(), preview[1], .01f)
                        }
                    } finally { output.eraseColor(0); output.recycle() }
                } finally { first.fill(0); second.fill(0) }
            }
            assertArrayEquals("Original photo is not resampled in place", copy, original)
        } finally { original.fill(0); copy.fill(0) }
    }
    @Test fun rotatedWorkingRasterStaysWithinScannerBudget() = runBlocking {
        guard(); val original = source(3000, 1500)
        val rotated = CardScanEngine(context).crop(original, CardCrop(3000, 1500, 2.0, degrees = 45.0).quad(), 0)
        try {
            val bitmap = decode(rotated)
            try {
                assertTrue(bitmap.width <= 2048 && bitmap.height <= 2048)
                assertTrue(bitmap.width.toLong() * bitmap.height <= 4_000_000)
            } finally { bitmap.eraseColor(0); bitmap.recycle() }
        } finally { original.fill(0); rotated.fill(0) }
    }
    @Test fun invalidAnglesAndImagesAreRefused() = runBlocking {
        guard(); val original = source()
        try {
            for (angle in listOf(Double.NaN, Double.POSITIVE_INFINITY, 361.0)) {
                try { CardCrop(80, 40, 2.0, degrees = angle); fail("Invalid angle accepted") } catch (_: IllegalArgumentException) { }
            }
            try { CardScanEngine(context).crop(byteArrayOf(1, 2, 3), CardCrop(80, 40, 2.0).quad(), 0); fail("Invalid photo accepted") }
            catch (failure: dev.offlinescan.core.ScanException) { assertEquals(dev.offlinescan.core.ErrorCode.INVALID_IMAGE, failure.error.code) }
        } finally { original.fill(0) }
    }
}
