package app.kura.nativeapp

import android.content.Context
import app.kura.nativecore.CardAutofill
import app.kura.nativecore.CardField
import app.kura.nativecore.CardSuggestion
import dev.offlinescan.core.*
import dev.offlinescan.ocr.TesseractTextRecognizer
import dev.offlinescan.processing.OpenCvProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** All plaintext rasters and OCR job/model copies are private, bounded and operation-owned. */
internal class CardScanEngine(private val context: Context) {
    private val config = ScanConfig(mode = ScanMode.CARD, maxPages = 1, maxInputPixels = 16_000_000,
        maxOutputDimension = 2048, maxRenderPixels = 4_000_000, maxWorkingBytes = 96_000_000)

    private suspend fun <T> workspace(bytes: ByteArray, block: (File, File, Cancellation) -> T): T = withContext(Dispatchers.IO) {
        require(bytes.size in 1..10 * 1024 * 1024)
        val job = currentCoroutineContext()
        val cancellation = Cancellation { job.ensureActive() }
        val root = File(context.cacheDir.canonicalFile, "card-scans")
        check(root.canonicalFile == root.absoluteFile)
        check(root.isDirectory || root.mkdirs())
        val directory = File(root, UUID.randomUUID().toString())
        check(directory.mkdir())
        try {
            cancellation.check()
            val input = File(directory, "input.jpg")
            input.outputStream().use { it.write(bytes) }
            cancellation.check()
            block(directory, input, cancellation).also { result ->
                try { cancellation.check() } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    if (result is ByteArray) result.fill(0)
                    throw cancelled
                }
            }
        } finally { directory.deleteRecursively() }
    }

    suspend fun detect(bytes: ByteArray): Quad = workspace(bytes) { _, input, cancellation ->
        OpenCvProcessor().detect(input, config, cancellation).corners ?: Quad.FULL
    }

    suspend fun crop(bytes: ByteArray, corners: Quad, rotation: Int): ByteArray = workspace(bytes) { directory, input, cancellation ->
        Geometry.requireValid(corners)
        val output = File(directory, "cropped.jpg")
        OpenCvProcessor().render(input, Edits(corners, rotation, Preset.ORIGINAL), output, ExportFormat.JPEG, config, cancellation)
        check(output.length() in 1..10L * 1024 * 1024)
        output.readBytes()
    }

    suspend fun recognize(bytes: ByteArray, fields: List<CardField>): List<CardSuggestion> =
        CardAutofill.suggest(recognizeText(bytes), fields)

    internal suspend fun recognizeText(bytes: ByteArray): String = workspace(bytes) { directory, input, cancellation ->
        val models = File(directory, "ocr/tessdata").apply { check(mkdirs()) }
        val hashes = mapOf(
            "eng" to "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
            "jpn" to "1f5de9236d2e85f5fdf4b3c500f2d4926f8d9449f28f5394472d9e8d83b91b4d")
        hashes.forEach { (language, expected) ->
            cancellation.check()
            val file = File(models, "$language.traineddata")
            context.assets.open("card-ocr/$language.traineddata").use { source -> file.outputStream().use { source.copyTo(it) } }
            check(file.length() <= 16L * 1024 * 1024)
            val hash = file.inputStream().use { stream ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                try { while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }; digest.digest() }
                finally { buffer.fill(0) }
            }.joinToString("") { "%02x".format(it) }
            check(hash == expected) { "Recognition model integrity check failed" }
        }
        val page = TesseractTextRecognizer(models.parentFile!!).recognize(input, "card",
            OcrConfig(layout = OcrLayout.SPARSE, maxInputPixels = 4_000_000, maxInputBytes = 10L * 1024 * 1024,
                maxInputDimension = 2048), cancellation)
        cancellation.check()
        check(page.text.length <= 64_000)
        page.text
    }
}
