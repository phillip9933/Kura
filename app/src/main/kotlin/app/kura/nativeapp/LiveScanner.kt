package app.kura.nativeapp

import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object NativeBarcodeDecoder {
    fun decode(luma: ByteArray, width: Int, height: Int): Result? {
        require(width > 0 && height > 0 && width.toLong()*height == luma.size.toLong() && luma.size <= 4_000_000)
        val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to app.kura.feature.barcodeFormats.values.toList(),
            DecodeHintType.TRY_HARDER to true, DecodeHintType.ALSO_INVERTED to true)
        fun attempt(bytes: ByteArray, w: Int, h: Int) = runCatching {
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(PlanarYUVLuminanceSource(bytes, w, h, 0, 0, w, h, false))), hints)
        }.getOrNull()
        attempt(luma, width, height)?.let { return it }
        val rotated = ByteArray(luma.size)
        try {
            for(y in 0 until height) for(x in 0 until width) rotated[x*height + height-1-y] = luma[y*width+x]
            return attempt(rotated, height, width)
        } finally { rotated.fill(0) }
    }
}
@Composable
fun LiveScanner(dismiss: () -> Unit, capture: () -> Unit, result: (Result) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current
    var view by remember { mutableStateOf<PreviewView?>(null) }
    var status by remember { mutableStateOf("Starting camera") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Live barcode scanner") },
        text = { Column { Text(status); AndroidView(factory = { PreviewView(it).also { preview -> view = preview } },
            modifier = Modifier.fillMaxWidth().height(380.dp)) } },
        confirmButton = { TextButton(onClick = dismiss) { Text("Close scanner") } },
        dismissButton = { TextButton(onClick = capture) { Text("Capture instead") } })
    DisposableEffect(view, lifecycle) {
        val previewView = view
        val executor = Executors.newSingleThreadExecutor()
        val alive = AtomicBoolean(true); val delivered = AtomicBoolean(false); val seen = AtomicBoolean(false)
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null
        if(previewView != null) {
            val context = previewView.context
            val main = ContextCompat.getMainExecutor(context)
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if(alive.get()) try {
                    provider = future.get()
                    preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    @Suppress("DEPRECATION")
                    val analyzer = ImageAnalysis.Builder().setTargetResolution(Size(1280,720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis = analyzer
                    analyzer.setAnalyzer(executor) { image ->
                        try {
                            if(!alive.get() || delivered.get()) return@setAnalyzer
                            if(seen.compareAndSet(false, true)) main.execute { if(alive.get()) status = "Camera active" }
                            val width = image.width; val height = image.height
                            if(width.toLong()*height > 4_000_000) return@setAnalyzer
                            val plane = image.planes[0]; val buffer = plane.buffer; val start = buffer.position()
                            val bytes = ByteArray(width*height)
                            try {
                                for(y in 0 until height) for(x in 0 until width) bytes[y*width+x] = buffer.get(start + y*plane.rowStride + x*plane.pixelStride)
                                val found = NativeBarcodeDecoder.decode(bytes, width, height)
                                if(found != null && delivered.compareAndSet(false, true)) main.execute { if(alive.get()) result(found) }
                            } finally { bytes.fill(0) }
                        } finally { image.close() }
                    }
                    provider!!.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
                } catch(e: Exception) { status = "Camera unavailable: " + e.javaClass.simpleName }
            }, main)
        }
        onDispose {
            alive.set(false); analysis?.clearAnalyzer()
            val cases = listOfNotNull(preview, analysis).toTypedArray()
            if(cases.isNotEmpty()) provider?.unbind(*cases)
            executor.shutdownNow()
        }
    }
}