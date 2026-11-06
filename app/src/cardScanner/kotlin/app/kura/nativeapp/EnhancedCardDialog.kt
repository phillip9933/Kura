package app.kura.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import java.util.Locale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.kura.feature.*
import app.kura.nativecore.*
import dev.offlinescan.core.Geometry
import dev.offlinescan.core.Point
import dev.offlinescan.core.Quad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val enhancedCardScanning = true
private enum class CardStep { CROP, OFFER, REVIEW }

@Composable internal fun EnhancedCardDialog(vm: VaultController, bytes: ByteArray, target: ImageTarget,
    dismiss: () -> Unit) {
    val context = LocalContext.current
    val engine = remember(context) { CardScanEngine(context) }
    val scope = rememberCoroutineScope()
    val form = remember(bytes) { vm.pending.uiMemory.form }
    val identity = remember(bytes) { form.identity }
    val presentation by vm.presentation.collectAsState()
    val preferences = presentation.preferences
    val fields = remember(bytes) { recognitionFields(target.table, form, preferences) }
    var step by remember(bytes) { mutableStateOf(CardStep.CROP) }
    var corners by remember(bytes) { mutableStateOf(Quad.FULL) }
    var selectedCorner by remember { mutableIntStateOf(0) }
    var fineTune by remember { mutableStateOf(false) }
    var detectedCorners by remember(bytes) { mutableStateOf(Quad.FULL) }
    var rotation by remember(bytes) { mutableFloatStateOf(0f) }
    var rotationPicker by remember { mutableStateOf(false) }
    var draftRotation by remember { mutableFloatStateOf(0f) }
    var oriented by remember(bytes) { mutableStateOf<ByteArray?>(null) }
    val workingBytes = oriented ?: bytes
    val rotationOwner = remember(bytes) { arrayOfNulls<ByteArray>(1) }
    DisposableEffect(rotationOwner) { onDispose { rotationOwner[0]?.fill(0); rotationOwner[0] = null } }
    var busy by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("Detecting card edges…") }
    var error by remember { mutableStateOf<String?>(null) }
    var cropped by remember(bytes) { mutableStateOf<ByteArray?>(null) }
    var suggestions by remember { mutableStateOf<List<CardSuggestion>>(emptyList()) }
    val edits = remember { mutableStateMapOf<String, String>() }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val currentCropped = rememberUpdatedState(cropped)
    DisposableEffect(bytes) { onDispose { currentCropped.value?.fill(0); edits.clear(); suggestions = emptyList() } }
    LaunchedEffect(bytes) {
        try {
            corners = vm.work { engine.detect(bytes) }
            detectedCorners = corners
            status = if (corners == Quad.FULL) "Edges uncertain. Adjust the four corners or use the full image." else "Card edges detected. Check all four corners before continuing."
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { status = "Automatic detection unavailable. Drag the corners or use the full image." }
        finally { busy = false }
    }
    fun currentForm() = target.id == -1L && identity != null && form.identity == identity && vm.pending.uiMemory.form === form
    fun saveImage(apply: Boolean) {
        val image = cropped ?: return
        busy = true; error = null
        scope.launch {
            try {
                if (target.id == -1L) check(currentForm())
                val chosen = if (apply) suggestions.filter { selected[it.field.key] == true }.map {
                    it.copy(value = edits[it.field.key].orEmpty())
                } else emptyList()
                // Encrypt into the existing draft-media path; the form's Save remains explicit.
                vm.imageChange(target, image)
                if (apply && currentForm()) {
                    val current = recognitionFields(target.table, form, vm.presentation.value.preferences).associate { it.key to it.current }
                    CardAutofill.applicable(chosen, current).forEach { (key, value) ->
                        if (key.startsWith("custom:")) form.customValues[key.removePrefix("custom:")] = value
                        else form.values[key] = value
                    }
                }
                dismiss()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Could not use this scan. Your existing card details are unchanged." }
            finally { busy = false }
        }
    }
    val contentHeight = (LocalConfiguration.current.screenHeightDp * .58f).dp
    val validCrop = runCatching { Geometry.requireValid(corners) }.isSuccess
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(when (step) {
            CardStep.CROP -> "Crop card image"
            CardStep.OFFER -> "Autofill card details?"
            CardStep.REVIEW -> "Review recognized details"
        }) },
        text = {
            Column(Modifier.heightIn(max = contentHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step == CardStep.CROP) {
                    Text("Drag the corners to adjust the detected card edges.")
                    CardCorners(workingBytes, corners, !busy) { corners = it; status = "Crop adjusted. Check all four corners." }
                    Text(status, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = { corners = detectedCorners }, modifier = Modifier.weight(1f)) { Text("Reset edges") }
                        OutlinedButton(enabled = !busy, onClick = { corners = Quad.FULL }, modifier = Modifier.weight(1f)) { Text("Use full image") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = { draftRotation = rotation; rotationPicker = true }, modifier = Modifier.weight(1f)) { Text("Rotate: ${angleLabel(rotation)}") }
                        OutlinedButton(enabled = !busy, onClick = { fineTune = !fineTune }, modifier = Modifier.weight(1f)) { Text(if (fineTune) "Hide fine-tune" else "Fine-tune") }
                    }
                    if (fineTune) {
                        Text("Fine-tune a corner", style = MaterialTheme.typography.labelLarge)
                        listOf(listOf(0, 1), listOf(3, 2)).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { index ->
                                    FilterChip(selected = selectedCorner == index, enabled = !busy,
                                        onClick = { selectedCorner = index }, modifier = Modifier.weight(1f),
                                        label = { Text(listOf("Top left", "Top right", "Bottom right", "Bottom left")[index]) })
                                }
                            }
                        }
                        Text("Horizontal", style = MaterialTheme.typography.labelMedium)
                        Slider(corners.points[selectedCorner].x.toFloat(), { x -> corners = corners.replace(selectedCorner, Point(x.toDouble(), corners.points[selectedCorner].y)) },
                            enabled = !busy, modifier = Modifier.semantics { contentDescription = "Corner horizontal position" })
                        Text("Vertical", style = MaterialTheme.typography.labelMedium)
                        Slider(corners.points[selectedCorner].y.toFloat(), { y -> corners = corners.replace(selectedCorner, Point(corners.points[selectedCorner].x, y.toDouble())) },
                            enabled = !busy, modifier = Modifier.semantics { contentDescription = "Corner vertical position" })
                    }
                    if (!validCrop) Text("Keep the corners in order around the card.", color = MaterialTheme.colorScheme.error)
                } else {
                    cropped?.let { CardPreview(it) }
                    if (step == CardStep.OFFER) {
                        Text("Recognize text on this device and review suggestions for empty fields. Your card is saved only when you save the form.")
                        if (currentForm()) OutlinedButton(enabled = !busy, onClick = {
                            busy = true; error = null
                            scope.launch {
                                try {
                                    suggestions = vm.work { engine.recognize(cropped!!, fields) }
                                    suggestions.forEach { edits[it.field.key] = it.value; selected[it.field.key] = true }
                                    step = CardStep.REVIEW
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { error = "Text recognition could not finish. You can still use the image and enter details manually." }
                                finally { busy = false }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Review autofill") }
                        OutlinedButton(enabled = !busy, onClick = { cropped?.fill(0); cropped = null; step = CardStep.CROP }, modifier = Modifier.fillMaxWidth()) { Text("Adjust crop") }
                    } else {
                        Text("Check every suggestion. Existing values are kept; only selected empty fields will be filled.")
                        if (suggestions.isEmpty()) Text("No values matched empty existing fields. You can continue with the image alone.")
                        suggestions.forEach { suggestion ->
                            val key = suggestion.field.key
                            Row {
                                Checkbox(selected[key] == true, { selected[key] = it }, enabled = !busy)
                                OutlinedTextField(edits[key].orEmpty(), { edits[key] = it }, label = { Text(suggestion.field.label) },
                                    enabled = !busy, modifier = Modifier.weight(1f), isError = selected[key] == true && !validSuggestion(target.table, form, preferences, key, edits[key].orEmpty()))
                            }
                        }
                        OutlinedButton(enabled = !busy, onClick = { saveImage(false) }, modifier = Modifier.fillMaxWidth()) { Text("Keep image without autofill") }
                    }
                }
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Working on this device…", style = MaterialTheme.typography.bodySmall) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when (step) {
                CardStep.CROP -> Button(enabled = !busy && validCrop, onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            var owned: ByteArray? = null
                            try {
                                vm.work { owned = engine.crop(workingBytes, corners, 0) }
                                cropped = owned; owned = null
                                step = CardStep.OFFER
                            } finally { owned?.fill(0) }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = "Could not crop this image. Adjust the corners or use the full image." }
                        finally { busy = false }
                    }
                }) { Text("Use crop") }
                CardStep.OFFER -> Button(enabled = !busy, onClick = { saveImage(false) }) { Text("Use image only") }
                CardStep.REVIEW -> Button(enabled = !busy && suggestions.filter { selected[it.field.key] == true }.all { validSuggestion(target.table, form, preferences, it.field.key, edits[it.field.key].orEmpty()) },
                    onClick = { saveImage(true) }) { Text("Apply selected details and use image") }
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel import") } },
    )
    if (rotationPicker) {
        AlertDialog(
            onDismissRequest = { rotationPicker = false },
            title = { Text("Rotate photo") },
            text = {
                Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * .58f).dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RotationPreview(bytes, draftRotation)
                    listOf(listOf(0f, 90f), listOf(180f, -90f)).forEach { angles ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            angles.forEach { angle ->
                                FilterChip(selected = draftRotation == angle, onClick = { draftRotation = angle },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(if (angle == -90f) "270°" else "${angle.toInt()}°") })
                            }
                        }
                    }
                    Text("Custom angle: ${String.format(Locale.ROOT, "%.1f°", draftRotation)}")
                    Slider(value = draftRotation, onValueChange = { draftRotation = it }, valueRange = -180f..180f,
                        steps = 3599, modifier = Modifier.semantics { contentDescription = "Photo rotation angle" })
                    Text("Rotation updates the photo before cropping and resets the detected edges. You can adjust the mask afterward.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = {
                val angle = draftRotation
                rotationPicker = false; busy = true; error = null
                scope.launch {
                    var owned: ByteArray? = null
                    try {
                        val detected = vm.work {
                            owned = rotateCardImage(bytes, angle)
                            engine.detect(owned!!)
                        }
                        rotationOwner[0]?.fill(0); rotationOwner[0] = owned; oriented = owned; owned = null
                        rotation = angle; corners = detected; detectedCorners = detected
                        status = "Photo rotated. Check the card edges before cropping."
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = "Could not rotate this photo. Your previous crop is unchanged." }
                    finally { owned?.fill(0); busy = false }
                }
            }) { Text("Apply rotation") } },
            dismissButton = { TextButton(onClick = { rotationPicker = false }) { Text("Cancel") } },
        )
    }
}

internal fun recognitionFields(table: String, form: FormMemory, preferences: VaultPreferences): List<CardField> {
    val category = if (table == "passes") form.category.value else form.values[if (table == "wallets") "category" else "cardType"].orEmpty()
    val basic = basicFormFields(table, form.section.value, category).filter { it !in setOf("category", "cardType", "network", "barcodeValue", "barcodeFormat", "relevantDate") }
    val custom = (preferences.customFields(form.section.value) + suggestedFields(form.section.value, category)).distinctBy { it.name }
    val original = form.customValues.keys
    return basic.mapNotNull { key -> recordFields(table)[key]?.let { CardField(key, it, form.values[key].orEmpty()) } } +
        (custom.map { it.name } + original).distinct().map { CardField("custom:$it", it, form.customValues[it].orEmpty()) }
}

private fun validSuggestion(table: String, form: FormMemory, preferences: VaultPreferences, key: String, value: String): Boolean {
    if (value.isBlank()) return false
    if (!key.startsWith("custom:")) return validRecordField(key, value)
    val name = key.removePrefix("custom:")
    val category = if (table == "passes") form.category.value else form.values[if (table == "wallets") "category" else "cardType"].orEmpty()
    val schema = (preferences.customFields(form.section.value) + suggestedFields(form.section.value, category)).firstOrNull { it.name == name }
    return schema == null || validCustomValue(schema, value)
}

private fun Quad.replace(index: Int, point: Point): Quad {
    val points = points.toMutableList(); points[index] = point
    return Quad(points[0], points[1], points[2], points[3])
}

@Composable private fun imagePreview(bytes: ByteArray): Bitmap? {
    val bitmap by produceState<Bitmap?>(null, bytes) {
        var owned: Bitmap? = null
        try {
            withContext(Dispatchers.Default) {
                owned = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 2; inMutable = true })
            }
            value = owned; owned = null
        } finally { owned?.let { it.eraseColor(0); it.recycle() } }
    }
    DisposableEffect(bitmap) { val owned = bitmap; onDispose { owned?.let { if (!it.isRecycled) { it.eraseColor(0); it.recycle() } } } }
    return bitmap
}

@Composable private fun CardPreview(bytes: ByteArray) {
    imagePreview(bytes)?.let { Image(it.asImageBitmap(), "Cropped card preview", Modifier.fillMaxWidth().height(220.dp)) }
}

@Composable private fun CardCorners(bytes: ByteArray, corners: Quad, enabled: Boolean, change: (Quad) -> Unit) {
    val bitmap = imagePreview(bytes) ?: return
    val latest by rememberUpdatedState(corners)
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).semantics { contentDescription = "Drag the four card corners" }.pointerInput(bitmap, enabled) {
        var corner = 0
        fun position(point: Point): Offset {
            val scale = minOf(size.width.toFloat() / bitmap.width, size.height.toFloat() / bitmap.height)
            val width = bitmap.width * scale; val height = bitmap.height * scale
            return Offset((size.width - width) / 2 + point.x.toFloat() * width, (size.height - height) / 2 + point.y.toFloat() * height)
        }
        if (enabled) detectDragGestures(onDragStart = { point ->
            val nearest = latest.points.indices.minBy { (position(latest.points[it]) - point).getDistance() }
            corner = nearest.takeIf { (position(latest.points[it]) - point).getDistance() <= 44.dp.toPx() } ?: -1
        }) { pointer, _ ->
            if (corner < 0) return@detectDragGestures
            pointer.consume()
            val scale = minOf(size.width.toFloat() / bitmap.width, size.height.toFloat() / bitmap.height)
            val width = bitmap.width * scale; val height = bitmap.height * scale
            change(latest.replace(corner, Point(((pointer.position.x - (size.width - width) / 2) / width).coerceIn(0f, 1f).toDouble(),
                ((pointer.position.y - (size.height - height) / 2) / height).coerceIn(0f, 1f).toDouble())))
        }
    }) {
        val scale = minOf(size.width / bitmap.width, size.height / bitmap.height)
        val width = bitmap.width * scale; val height = bitmap.height * scale
        val left = (size.width - width) / 2; val top = (size.height - height) / 2
        drawImage(bitmap.asImageBitmap(), dstOffset = IntOffset(left.toInt(), top.toInt()), dstSize = IntSize(width.toInt(), height.toInt()))
        val points = corners.points.map { Offset(left + it.x.toFloat() * width, top + it.y.toFloat() * height) }
        val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
        val shade = Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            addPath(path)
        }
        drawPath(shade, Color.Black.copy(alpha = .45f))
        drawPath(path, accent, style = Stroke(2.dp.toPx()))
        points.forEach {
            drawCircle(Color.White, 10.dp.toPx(), it)
            drawCircle(accent, 7.dp.toPx(), it)
        }
    }
}

private fun angleLabel(angle: Float): String {
    val positive = (angle + 360f) % 360f
    return if (positive % 1f == 0f) "${positive.toInt()}°" else String.format(Locale.ROOT, "%.1f°", positive)
}

@Composable private fun RotationPreview(bytes: ByteArray, degrees: Float) {
    val bitmap = imagePreview(bytes) ?: return
    Canvas(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
        .semantics { contentDescription = "Rotated photo preview: ${angleLabel(degrees)}" }) {
        val (matrix, bounds) = cardRotationMatrix(bitmap.width, bitmap.height, degrees)
        val scale = minOf(size.width / bounds.width(), size.height / bounds.height())
        matrix.postScale(scale, scale)
        matrix.postTranslate((size.width - bounds.width() * scale) / 2, (size.height - bounds.height() * scale) / 2)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(bitmap, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        }
    }
}
