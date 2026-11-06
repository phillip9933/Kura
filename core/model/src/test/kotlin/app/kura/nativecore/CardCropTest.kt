package app.kura.nativecore

import org.junit.Assert.*
import org.junit.Test

class CardCropTest {
    @Test fun fullPhotoAndQuarterTurnsMapAllFourLandmarks() {
        val landmarks = listOf(CropPoint(0.0, 0.0), CropPoint(1.0, 0.0), CropPoint(1.0, 1.0), CropPoint(0.0, 1.0))
        for (quarter in 0..3) {
            val crop = CardCrop(800, 400, if (quarter % 2 == 0) 2.0 else .5, degrees = quarter * 90.0)
            crop.corners.forEachIndexed { i, p ->
                val expected = landmarks[(i - quarter + 4) % 4]
                assertEquals(expected.x, p.x, 0.0)
                assertEquals(expected.y, p.y, 0.0)
            }
        }
    }

    @Test fun everyRotatedZoomedBoundaryStaysInsideSourceAndRoundTrips() {
        for (aspect in listOf(.25, 1.0, 1.586, 4.0)) for (angle in -180..180 step 15)
            for (zoom in listOf(1.0, 2.0, 5.0)) for (x in listOf(-1.0, 1.0)) for (y in listOf(-1.0, 1.0)) {
                val crop = CardCrop(1600, 1000, aspect, zoom, angle.toDouble(), x, y)
                crop.corners.forEach { p ->
                    assertTrue("$crop: $p", p.x in -1e-10..1.0000000001 && p.y in -1e-10..1.0000000001)
                    val frame = crop.imageToFrame(p.x, p.y)
                    val back = crop.frameToImage(frame.x, frame.y)
                    assertEquals(p.x, back.x, 1e-10); assertEquals(p.y, back.y, 1e-10)
                }
            }
    }

    @Test fun panningMovesImageInScreenDirectionAtEveryQuarterTurnAndClamps() {
        for (angle in listOf(0.0, 90.0, 180.0, 270.0, 33.0)) {
            val crop = CardCrop(1600, 1000, 1.586, zoom = 3.0, degrees = angle)
            val moved = crop.moveImage(.1, -.1).imageToFrame(.5, .5)
            assertEquals(.6, moved.x, 1e-10); assertEquals(.4, moved.y, 1e-10)
            val edge = crop.moveImage(1000.0, -1000.0)
            assertTrue(edge.corners.all { it.x in -1e-10..1.0000000001 && it.y in -1e-10..1.0000000001 })
        }
    }

    @Test fun invalidOrUnboundedTransformsAreRefused() {
        val invalid = listOf<() -> CardCrop>(
            { CardCrop(0, 10, 1.0) }, { CardCrop(10, 10, Double.NaN) },
            { CardCrop(10, 10, 0.0) }, { CardCrop(10, 10, 1.0, zoom = .9) },
            { CardCrop(10, 10, 1.0, zoom = 5.1) }, { CardCrop(10, 10, 1.0, degrees = Double.POSITIVE_INFINITY) },
            { CardCrop(10, 10, 1.0, positionX = 1.1) },
        )
        invalid.forEach { operation ->
            try { operation(); fail("Invalid transform accepted") } catch (_: IllegalArgumentException) { }
        }
        try { CardCrop(10, 10, 1.0).moveImage(Double.NaN, 0.0); fail("Invalid movement accepted") }
        catch (_: IllegalArgumentException) { }
    }
}
