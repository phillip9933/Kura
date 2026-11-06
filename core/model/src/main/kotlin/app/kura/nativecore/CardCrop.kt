package app.kura.nativecore

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class CropPoint(val x: Double, val y: Double)

/** A stationary rectangular frame over an affine image, in normalized coordinates. */
data class CardCrop(
    val imageWidth: Int,
    val imageHeight: Int,
    val aspect: Double,
    val zoom: Double = 1.0,
    val degrees: Double = 0.0,
    val positionX: Double = 0.0,
    val positionY: Double = 0.0,
) {
    init {
        require(imageWidth > 0 && imageHeight > 0)
        require(aspect.isFinite() && aspect > 0)
        require(zoom.isFinite() && zoom in 1.0..5.0)
        require(degrees.isFinite() && degrees in -360.0..360.0)
        require(positionX.isFinite() && positionX in -1.0..1.0)
        require(positionY.isFinite() && positionY in -1.0..1.0)
    }

    // Exact quarter turns must retain boundary pixels when the renderer floors
    // output dimensions. cos(90°)'s residual otherwise loses a row or column.
    private val quarter = if (degrees % 90.0 == 0.0) ((degrees.toInt() / 90) % 4 + 4) % 4 else null
    private val c = when (quarter) { 0 -> 1.0; 1, 3 -> 0.0; 2 -> -1.0; else -> cos(Math.toRadians(degrees)) }
    private val s = when (quarter) { 0, 2 -> 0.0; 1 -> 1.0; 3 -> -1.0; else -> sin(Math.toRadians(degrees)) }
    // Inscribe the frame in the source at every angle: no empty/transparent edges.
    val sourceUnits = minOf(
        imageWidth / (abs(c) * aspect + abs(s)),
        imageHeight / (abs(s) * aspect + abs(c)),
    ) / zoom
    private val travelX = (imageWidth - sourceUnits * (abs(c) * aspect + abs(s))).coerceAtLeast(0.0) / 2
    private val travelY = (imageHeight - sourceUnits * (abs(s) * aspect + abs(c))).coerceAtLeast(0.0) / 2
    private val centerX = imageWidth / 2.0 + positionX * travelX
    private val centerY = imageHeight / 2.0 + positionY * travelY

    fun frameToImage(x: Double, y: Double): CropPoint {
        val dx = (x - .5) * sourceUnits * aspect
        val dy = (y - .5) * sourceUnits
        return CropPoint((centerX + c * dx + s * dy) / imageWidth,
            (centerY - s * dx + c * dy) / imageHeight)
    }

    fun imageToFrame(x: Double, y: Double): CropPoint {
        val dx = x * imageWidth - centerX
        val dy = y * imageHeight - centerY
        return CropPoint(.5 + (c * dx - s * dy) / (sourceUnits * aspect),
            .5 + (s * dx + c * dy) / sourceUnits)
    }

    /** Positive movement moves the photo right/down, in frame-width/height units. */
    fun moveImage(dx: Double, dy: Double): CardCrop {
        require(dx.isFinite() && dy.isFinite())
        val sourceX = -(c * dx * aspect + s * dy) * sourceUnits
        val sourceY = (s * dx * aspect - c * dy) * sourceUnits
        return copy(
            positionX = if (travelX > 1e-8) (positionX + sourceX / travelX).coerceIn(-1.0, 1.0) else 0.0,
            positionY = if (travelY > 1e-8) (positionY + sourceY / travelY).coerceIn(-1.0, 1.0) else 0.0,
        )
    }

    val corners: List<CropPoint> get() = listOf(frameToImage(0.0, 0.0), frameToImage(1.0, 0.0),
        frameToImage(1.0, 1.0), frameToImage(0.0, 1.0))
}
