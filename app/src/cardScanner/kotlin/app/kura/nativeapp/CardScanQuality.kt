package app.kura.nativeapp

import dev.offlinescan.core.Quad
import dev.offlinescan.processing.DocumentQuality

/** Advisory still-photo signals, never an import gate or a claim of OCR accuracy. */
internal fun cardPhotoWarnings(quality: DocumentQuality, corners: Quad): List<String> = buildList {
    if (quality.brightness < .18) {
        add("Photo is dark. Add even lighting so the card details are readable.")
    } else if (quality.brightness > .90) {
        add("Photo is very bright. Reflections may hide text; tilt the card or move away from direct light.")
    }
    // Blank/low-detail cards provide no reliable evidence of focus. Match the SDK's
    // still-photo floor only when at least two document tiles contain detail.
    if (quality.informativeTiles >= 2 && quality.sharpness.isFinite() && quality.sharpness < 60.0) {
        add("Photo may be blurry. Hold steady, focus on the text and retake if details are unclear.")
    }
    if (corners != Quad.FULL && corners.points.any { it.x < .015 || it.x > .985 || it.y < .015 || it.y > .985 }) {
        add("Card reaches the photo edge. Check for cut-off details; retake with space around all four edges if needed.")
    }
}

/** Keep typed SDK failures actionable without displaying native paths or input text. */
internal fun cardScanFailureMessage(failure: Exception, fallback: String): String =
    when ((failure as? dev.offlinescan.core.ScanException)?.error?.code) {
        dev.offlinescan.core.ErrorCode.INVALID_IMAGE -> "This photo could not be read. Retake it or choose another image."
        dev.offlinescan.core.ErrorCode.RESOURCE_LIMIT -> "This photo is too large to process. Retake it or choose a smaller image."
        dev.offlinescan.core.ErrorCode.STORAGE -> "Temporary photo storage is unavailable. Try again or cancel this import."
        else -> fallback
    }
