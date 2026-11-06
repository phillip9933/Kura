package app.kura.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.kura.nativecore.CardCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class CropControl { Zoom, Move, Rotate }
// ISO/IEC 7810 ID-1 dimensions, width / height in millimetres.
private const val CreditCardAspectRatio = 85.60 / 53.98
internal enum class CardShape(val label: String, val ratio: Double?) {
    PHOTO("Photo proportions", null), CARD("Landscape card", CreditCardAspectRatio),
    PORTRAIT("Portrait card", 1 / CreditCardAspectRatio), SQUARE("Square", 1.0),
}

/** Owned by the import, so OCR review/re-entry cannot reset the displayed edits. */
@Stable internal class CardCropControls {
    var shape by mutableStateOf(CardShape.CARD)
    var quarter by mutableIntStateOf(0)
    var fineAngle by mutableFloatStateOf(0f)
    fun reset(shape: CardShape = CardShape.CARD) { this.shape = shape; quarter = 0; fineAngle = 0f }
}

@Composable internal fun CardCropEditor(
    bytes: ByteArray,
    crop: CardCrop?,
    controls: CardCropControls,
    busy: Boolean,
    status: String,
    warnings: List<String>,
    error: String?,
    automaticAvailable: Boolean,
    onCropChange: (CardCrop) -> Unit,
    onAutomatic: () -> Unit,
    onFullPhoto: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRetake: () -> Unit,
) {
    var control by remember { mutableStateOf(CropControl.Zoom) }
    val shape = controls.shape
    val quarter = controls.quarter
    val fineAngle = controls.fineAngle
    val bitmap = rememberCardPreview(bytes)
    val enabled = !busy && crop != null && bitmap != null
    val configuration = LocalConfiguration.current
    val sideBySide = configuration.screenWidthDp > configuration.screenHeightDp
    val adjustmentScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp)
            .widthIn(max = 1000.dp), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!sideBySide) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Crop card image", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel import") }
                }
                Box(Modifier.weight(1f)) {
                    val preview: @Composable (Modifier) -> Unit = { modifier ->
                        CardMaskPreview(bitmap, crop, enabled, onCropChange, modifier)
                    }
                    val renderControls: @Composable (Modifier) -> Unit = { modifier ->
                        Column(modifier,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                CropControl.entries.forEach { item ->
                                    OutlinedButton(enabled = enabled,
                                        onClick = {
                                            control = item
                                            scope.launch { adjustmentScroll.scrollTo(0) }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (control == item) MaterialTheme.colorScheme.secondaryContainer
                                                else Color.Transparent,
                                            contentColor = if (control == item) MaterialTheme.colorScheme.onSecondaryContainer
                                                else MaterialTheme.colorScheme.primary),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f).semantics {
                                            role = Role.Tab
                                            selected = control == item
                                        }) { Text(item.name, maxLines = 1) }
                                }
                            }
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(adjustmentScroll)
                                .semantics { contentDescription = "Crop adjustments" },
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (sideBySide) Text("Crop card image", style = MaterialTheme.typography.titleLarge)
                                crop?.let { value ->
                                    when (control) {
                                        CropControl.Zoom -> ZoomControls(value, enabled, onCropChange)
                                        CropControl.Move -> PositionControls(value, enabled, onCropChange)
                                        CropControl.Rotate -> RotationControls(quarter, fineAngle, enabled, { q, fine ->
                                            controls.quarter = q; controls.fineAngle = fine
                                            val photoRatio = value.imageWidth.toDouble() / value.imageHeight
                                            onCropChange(value.copy(degrees = q * 90.0 + fine,
                                                aspect = shape.ratio ?: if (q % 2 == 0) photoRatio else 1 / photoRatio))
                                        })
                                    }
                                }
                                CropShapePicker(shape, enabled) { selected ->
                                    controls.shape = selected
                                    crop?.let {
                                        val photoRatio = it.imageWidth.toDouble() / it.imageHeight
                                        onCropChange(it.copy(aspect = selected.ratio ?: if (quarter % 2 == 0) photoRatio else 1 / photoRatio))
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    TextButton(enabled = enabled && automaticAvailable, onClick = onAutomatic) { Text("Auto fit") }
                                    TextButton(enabled = enabled, onClick = onFullPhoto) { Text("Full photo") }
                                }
                                Text(status, style = MaterialTheme.typography.bodySmall)
                                warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                    if (sideBySide) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            preview(Modifier.weight(1f).fillMaxHeight())
                            renderControls(Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            preview(Modifier.weight(.56f).fillMaxWidth())
                            renderControls(Modifier.weight(.44f).fillMaxWidth())
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (sideBySide) IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel import") }
                    OutlinedButton(enabled = !busy, onClick = onRetake, modifier = Modifier.weight(1f)) { Text("Retake photo") }
                    Button(enabled = enabled, onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("Use crop") }
                }
            }
        }
    }
}

@Composable private fun CropShapePicker(shape: CardShape, enabled: Boolean, change: (CardShape) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(enabled = enabled, onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Shape: ${shape.label}")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            CardShape.entries.forEach { item ->
                DropdownMenuItem(text = { Text(item.label) }, onClick = { change(item); expanded = false })
            }
        }
    }
}

@Composable private fun ZoomControls(crop: CardCrop, enabled: Boolean, change: (CardCrop) -> Unit) {
    Text("Zoom: ${(crop.zoom * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(enabled = enabled && crop.zoom > 1, onClick = { change(crop.copy(zoom = (crop.zoom - .05).coerceAtLeast(1.0))) },
            modifier = Modifier.semantics { contentDescription = "Zoom out" }) { Text("−") }
        Slider(crop.zoom.toFloat(), { change(crop.copy(zoom = it.toDouble())) }, valueRange = 1f..5f,
            enabled = enabled, modifier = Modifier.weight(1f).semantics { contentDescription = "Card zoom" })
        TextButton(enabled = enabled && crop.zoom < 5, onClick = { change(crop.copy(zoom = (crop.zoom + .05).coerceAtMost(5.0))) },
            modifier = Modifier.semantics { contentDescription = "Zoom in" }) { Text("+") }
    }
}

@Composable private fun PositionControls(crop: CardCrop, enabled: Boolean, change: (CardCrop) -> Unit) {
    val movable = listOf(-.02 to 0.0, .02 to 0.0, 0.0 to -.02, 0.0 to .02)
        .any { crop.moveImage(it.first, it.second) != crop }
    Text(if (movable) "Move photo" else "Zoom in to move",
        style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(Triple("Move photo left", "←", -.02 to 0.0), Triple("Move photo up", "↑", 0.0 to -.02),
            Triple("Move photo down", "↓", 0.0 to .02), Triple("Move photo right", "→", .02 to 0.0)).forEach { (label, symbol, delta) ->
            val moved = crop.moveImage(delta.first, delta.second)
            OutlinedButton(enabled = enabled && moved != crop, onClick = { change(moved) },
                contentPadding = PaddingValues(0.dp), modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = label }) { Text(symbol) }
        }
    }
    TextButton(enabled = enabled, onClick = { change(crop.copy(positionX = 0.0, positionY = 0.0)) }) { Text("Centre photo") }
}

@Composable private fun RotationControls(quarter: Int, fine: Float, enabled: Boolean, change: (Int, Float) -> Unit) {
    val columns = if (LocalConfiguration.current.fontScale > 1.4f) 2 else 4
    (0..3).chunked(columns).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { q ->
                FilterChip(selected = quarter == q, enabled = enabled, onClick = { change(q, 0f) },
                    label = { Text("${q * 90}°") }, modifier = Modifier.weight(1f))
            }
        }
    }
    Text("Fine rotation: ${String.format(Locale.ROOT, "%.1f°", fine)}", style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(enabled = enabled && fine > -45, onClick = { change(quarter, (fine - .5f).coerceAtLeast(-45f)) },
            modifier = Modifier.semantics { contentDescription = "Rotate left half a degree" }) { Text("−") }
        Slider(fine, { change(quarter, it) }, valueRange = -45f..45f, enabled = enabled,
            modifier = Modifier.weight(1f).semantics { contentDescription = "Photo rotation angle" })
        TextButton(enabled = enabled && fine < 45, onClick = { change(quarter, (fine + .5f).coerceAtMost(45f)) },
            modifier = Modifier.semantics { contentDescription = "Rotate right half a degree" }) { Text("+") }
    }
}

internal fun cardCropMatrix(crop: CardCrop, width: Int, height: Int, frame: Rect): Matrix {
    val source = floatArrayOf(0f, 0f, width.toFloat(), 0f, 0f, height.toFloat())
    val mapped = listOf(crop.imageToFrame(0.0, 0.0), crop.imageToFrame(1.0, 0.0), crop.imageToFrame(0.0, 1.0))
    val destination = mapped.flatMap { listOf(frame.left + it.x.toFloat() * frame.width,
        frame.top + it.y.toFloat() * frame.height) }.toFloatArray()
    return Matrix().apply { check(setPolyToPoly(source, 0, destination, 0, 3)) }
}

@Composable private fun CardMaskPreview(bitmap: Bitmap?, crop: CardCrop?, enabled: Boolean,
    change: (CardCrop) -> Unit, modifier: Modifier) {
    val latest by rememberUpdatedState(crop)
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .semantics {
            contentDescription = "Card crop preview"
            stateDescription = crop?.let { "Zoom ${(it.zoom * 100).toInt()}%, rotation ${String.format(Locale.ROOT, "%.1f", it.degrees)} degrees" } ?: "Loading photo"
        }.pointerInput(bitmap, enabled) {
            if (enabled) detectTransformGestures { _, pan, zoom, _ ->
                latest?.let { value ->
                    val frame = cardFrame(size.width.toFloat(), size.height.toFloat(), value.aspect)
                    change(value.copy(zoom = (value.zoom * zoom).coerceIn(1.0, 5.0))
                        .moveImage(pan.x / frame.width.toDouble(), pan.y / frame.height.toDouble()))
                }
            }
        }) {
        if (bitmap != null && crop != null) {
            val frame = cardFrame(size.width, size.height, crop.aspect)
            drawIntoCanvas { canvas -> canvas.nativeCanvas.drawBitmap(bitmap,
                cardCropMatrix(crop, bitmap.width, bitmap.height, frame), Paint(Paint.FILTER_BITMAP_FLAG)) }
            val mask = Path().apply { addRoundRect(RoundRect(frame, CornerRadius(minOf(frame.width, frame.height) * .045f))) }
            val shade = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height)); addPath(mask)
            }
            drawPath(shade, Color.Black.copy(alpha = .65f))
            drawPath(mask, Color.White, style = Stroke(4.dp.toPx()))
            drawPath(mask, accent, style = Stroke(2.dp.toPx()))
        }
    }
}

private fun cardFrame(width: Float, height: Float, aspect: Double): Rect {
    val w = minOf(width * .88f, height * .88f * aspect.toFloat()).coerceAtLeast(1f)
    val h = w / aspect.toFloat()
    return Rect((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
}

@Composable internal fun rememberCardPreview(bytes: ByteArray): Bitmap? {
    val state = remember(bytes) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(bytes) {
        var owned: Bitmap? = null
        try {
            withContext(Dispatchers.Default) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                // The dialog owns the visible invalid-photo error. Leave its
                // preview empty rather than throwing from a composition effect.
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 16_000_000) return@withContext
                var sample = 1
                while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 1536) sample *= 2
                owned = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true })
            }
            state.value = owned; owned = null
        } finally { owned?.let { it.eraseColor(0); it.recycle() } }
    }
    DisposableEffect(state) { onDispose {
        state.value?.let { if (!it.isRecycled) { it.eraseColor(0); it.recycle() } }
        state.value = null
    } }
    return state.value
}
