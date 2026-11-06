package app.kura.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/** Shared by the live angle preview and the raster used for detection/cropping. */
internal fun cardRotationMatrix(width: Int, height: Int, degrees: Float): Pair<Matrix, RectF> {
    require(degrees.isFinite())
    val matrix = Matrix().apply { setRotate(degrees, width / 2f, height / 2f) }
    val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
    matrix.mapRect(bounds)
    matrix.postTranslate(-bounds.left, -bounds.top)
    return matrix to RectF(0f, 0f, bounds.width(), bounds.height())
}

/** Always rotate the original input, never an already resampled intermediate. */
internal suspend fun rotateCardImage(bytes: ByteArray, degrees: Float): ByteArray {
    var owned: ByteArray? = null
    try {
        withContext(Dispatchers.Default) {
            require(degrees.isFinite() && degrees in -180f..180f)
            require(bytes.size in 1..10 * 1024 * 1024)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000)
            if (degrees == 0f) { owned = bytes.copyOf(); return@withContext }
            var sample = 1
            while (true) {
                val size = cardRotationMatrix(maxOf(1, bounds.outWidth / sample), maxOf(1, bounds.outHeight / sample), degrees).second
                if (ceil(size.width()).toLong() * ceil(size.height()).toLong() <= 4_000_000 && maxOf(size.width(), size.height()) <= 2048) break
                sample *= 2
            }
            ensureActive()
            val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true }))
            var output: Bitmap? = null
            try {
                val (matrix, size) = cardRotationMatrix(source.width, source.height, degrees)
                output = Bitmap.createBitmap(ceil(size.width()).toInt(), ceil(size.height()).toInt(), Bitmap.Config.ARGB_8888)
                Canvas(output).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                }
                ensureActive()
                object : ByteArrayOutputStream() {
                    override fun close() { buf.fill(0); super.close() }
                }.use { stream ->
                    check(output.compress(Bitmap.CompressFormat.JPEG, 95, stream))
                    require(stream.size() <= 10 * 1024 * 1024)
                    owned = stream.toByteArray()
                    ensureActive()
                }
            } finally {
                source.eraseColor(0); source.recycle()
                output?.let { it.eraseColor(0); it.recycle() }
            }
        }
        return requireNotNull(owned).also { owned = null }
    } finally { owned?.fill(0) }
}
