package app.kura.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import dev.offlinescan.core.Quad
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
                val rotated = rotateCardImage(original, angle)
                val cropped = CardScanEngine(context).crop(rotated, Quad.FULL, 0)
                try {
                    val bitmap = decode(cropped)
                    try {
                        assertEquals(if (angle == 90f || angle == -90f) 40 else 80, bitmap.width)
                        assertEquals(if (angle == 90f || angle == -90f) 80 else 40, bitmap.height)
                        colors.forEachIndexed { i, color -> closeColor(color, bitmap.getPixel(bitmap.width * (if (i % 2 == 0) 1 else 3) / 4,
                            bitmap.height * (if (i / 2 == 0) 1 else 3) / 4)) }
                    } finally { bitmap.eraseColor(0); bitmap.recycle() }
                } finally { rotated.fill(0); cropped.fill(0) }
            }
            assertArrayEquals("Original photo is never resampled in place", copy, original)
        } finally { original.fill(0); copy.fill(0) }
    }
    @Test fun customAngleMatchesPreviewBoundsAndRepeatedEditsUseOriginal() = runBlocking {
        guard(); val original = source()
        val first = rotateCardImage(original, 30f); val second = rotateCardImage(original, 30f)
        try {
            assertArrayEquals(first, second)
            val bitmap = decode(first)
            try {
                assertEquals(90, bitmap.width); assertEquals(75, bitmap.height)
                closeColor(Color.WHITE, bitmap.getPixel(1, 1))
                val previewBounds = cardRotationMatrix(80, 40, 30f).second
                assertTrue(abs(bitmap.width - previewBounds.width()) <= 1)
                assertTrue(abs(bitmap.height - previewBounds.height()) <= 1)
            } finally { bitmap.eraseColor(0); bitmap.recycle() }
        } finally { original.fill(0); first.fill(0); second.fill(0) }
    }
    @Test fun rotatedWorkingRasterStaysWithinScannerBudget() = runBlocking {
        guard(); val original = source(3000, 1500); val rotated = rotateCardImage(original, 45f)
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
            for (angle in listOf(Float.NaN, Float.POSITIVE_INFINITY, 181f)) {
                try { rotateCardImage(original, angle); fail("Invalid angle accepted") } catch (_: IllegalArgumentException) { }
            }
            try { rotateCardImage(byteArrayOf(1, 2, 3), 90f); fail("Invalid photo accepted") } catch (_: IllegalArgumentException) { }
        } finally { original.fill(0) }
    }
}
