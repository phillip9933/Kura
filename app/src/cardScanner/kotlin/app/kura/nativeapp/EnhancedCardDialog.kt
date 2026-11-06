package app.kura.nativeapp

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.kura.feature.*
import app.kura.nativecore.*
import dev.offlinescan.core.Point
import dev.offlinescan.core.Quad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val enhancedCardScanning = true
private enum class CardStep { CROP, OFFER, REVIEW }

@Composable internal fun EnhancedCardDialog(vm: VaultController, bytes: ByteArray, target: ImageTarget,
    dismiss: () -> Unit, retake: () -> Unit) {
    val context = LocalContext.current
    val engine = remember(context) { CardScanEngine(context) }
    val scope = rememberCoroutineScope()
    val form = remember(bytes) { vm.pending.uiMemory.form }
    val identity = remember(bytes) { form.identity }
    val formGeneration = remember(bytes) { form.generation }
    val presentation by vm.presentation.collectAsState()
    val preferences = presentation.preferences
    val fields = remember(bytes) { recognitionFields(target.table, form, preferences) }
    var step by remember(bytes) { mutableStateOf(CardStep.CROP) }
    var crop by remember(bytes) { mutableStateOf<CardCrop?>(null) }
    val cropControls = remember(bytes) { CardCropControls() }
    var aligned by remember(bytes) { mutableStateOf<ByteArray?>(null) }
    var captureCorners by remember(bytes) { mutableStateOf(Quad.FULL) }
    var originalPhoto by remember(bytes) { mutableStateOf(false) }
    val workingBytes = if (originalPhoto) bytes else aligned ?: bytes
    val alignedOwner = remember(bytes) { arrayOfNulls<ByteArray>(1) }
    DisposableEffect(alignedOwner) { onDispose { alignedOwner[0]?.fill(0); alignedOwner[0] = null } }
    var busy by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Detecting card edges…") }
    var photoWarnings by remember(bytes) { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var cropped by remember(bytes) { mutableStateOf<ByteArray?>(null) }
    var suggestions by remember { mutableStateOf<List<CardSuggestion>>(emptyList()) }
    val edits = remember { mutableStateMapOf<String, String>() }
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val currentCropped = rememberUpdatedState(cropped)
    DisposableEffect(bytes) { onDispose { currentCropped.value?.fill(0); edits.clear(); suggestions = emptyList() } }
    LaunchedEffect(bytes) {
        busy = true; error = null
        var owned: ByteArray? = null
        try {
            val detected = vm.work {
                engine.detect(bytes).also { proposal ->
                    if (proposal != Quad.FULL) owned = engine.crop(bytes, proposal, 0)
                }
            }
            if (owned != null) crop = null
            captureCorners = detected
            alignedOwner[0] = owned; aligned = owned; owned = null
            status = if (detected == Quad.FULL) "No reliable edges found. Frame the photo manually."
                else "Card aligned automatically. Check that every detail fits."
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { status = cardScanFailureMessage(failure, "Automatic alignment unavailable. Frame the original photo.") }
        finally { owned?.fill(0); busy = false }
    }
    LaunchedEffect(workingBytes) {
        crop = null
        cropControls.reset()
        try {
            crop = withContext(Dispatchers.Default) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(workingBytes, 0, workingBytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000)
                CardCrop(bounds.outWidth, bounds.outHeight, bounds.outWidth.toDouble() / bounds.outHeight)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Could not read this photo. Retake it or cancel the import." }
    }
    fun currentForm() = target.id == -1L && identity != null && form.identity == identity && form.generation == formGeneration && vm.pending.uiMemory.form === form
    fun saveImage(apply: Boolean) {
        val image = cropped ?: return
        busy = true; saving = true; error = null
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
            finally { saving = false; busy = false }
        }
    }
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val contentHeight = (configuration.screenHeightDp * .58f).dp
    LaunchedEffect(workingBytes, crop, step, busy) {
        photoWarnings = emptyList()
        val framing = crop ?: return@LaunchedEffect
        if (busy || step != CardStep.CROP) return@LaunchedEffect
        delay(300)
        try { photoWarnings = vm.work { cardPhotoWarnings(engine.quality(workingBytes, framing.quad()), captureCorners) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Advisory only; cropping does not depend on a quality score. */ }
    }
    fun resetFraming() {
        cropControls.reset()
        crop = crop?.let { CardCrop(it.imageWidth, it.imageHeight, it.imageWidth.toDouble() / it.imageHeight) }
    }
    if (step == CardStep.CROP) {
        CardCropEditor(workingBytes, crop, cropControls, busy, status, photoWarnings, error, aligned != null,
            onCropChange = { crop = it },
            onAutomatic = {
                if (workingBytes !== (aligned ?: bytes)) crop = null else resetFraming()
                originalPhoto = false
                status = "Card aligned automatically. Check that every detail fits."
            },
            onFullPhoto = {
                if (workingBytes !== bytes) crop = null else resetFraming()
                originalPhoto = true
                status = "Original photo. Move, zoom or rotate it inside the mask."
            },
            onConfirm = {
                val framing = requireNotNull(crop)
                busy = true; error = null
                scope.launch {
                    var owned: ByteArray? = null
                    try {
                        vm.work {
                            // The automatic proposal is already a bounded,
                            // metadata-free JPEG. Avoid another lossy encode
                            // when its complete frame is accepted unchanged.
                            owned = if (workingBytes === aligned && framing.zoom == 1.0 && framing.degrees == 0.0 &&
                                framing.aspect == framing.imageWidth.toDouble() / framing.imageHeight) workingBytes.copyOf()
                            else engine.crop(workingBytes, framing.quad(), 0)
                        }
                        cropped = owned; owned = null; step = CardStep.OFFER
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = cardScanFailureMessage(failure, "Could not crop this image. Reset the frame or use the full photo.") }
                    finally { owned?.fill(0); busy = false }
                }
            }, onCancel = dismiss, onRetake = retake)
        return
    }
    AlertDialog(
        // The platform's narrow dialog width stacks actions at large text sizes
        // in landscape, leaving almost no height for the scrollable review.
        modifier = if (landscape) Modifier.fillMaxWidth().padding(horizontal = 16.dp) else Modifier,
        properties = DialogProperties(usePlatformDefaultWidth = !landscape),
        onDismissRequest = { if (!saving) dismiss() },
        title = { Text(when (step) {
            CardStep.CROP -> "Crop card image"
            CardStep.OFFER -> "Autofill card details?"
            CardStep.REVIEW -> "Review recognized details"
        }) },
        text = {
            Column(Modifier.heightIn(max = contentHeight).verticalScroll(rememberScrollState())
                .semantics { contentDescription = "Card review" },
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                catch (failure: Exception) { error = cardScanFailureMessage(failure, "Text recognition could not finish. You can still use the image and enter details manually.") }
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
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Working on this device…", style = MaterialTheme.typography.bodySmall) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when (step) {
                CardStep.CROP -> Unit // The fixed-preview crop dialog is rendered above.
                CardStep.OFFER -> Button(enabled = !busy, onClick = { saveImage(false) }) { Text("Use image only") }
                CardStep.REVIEW -> Button(enabled = !busy && suggestions.filter { selected[it.field.key] == true }.all { validSuggestion(target.table, form, preferences, it.field.key, edits[it.field.key].orEmpty()) },
                    onClick = { saveImage(true) }) { Text("Apply selected details and use image") }
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = dismiss) { Text("Cancel import") } },
    )
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

internal fun CardCrop.quad(): Quad = corners.map { Point(it.x.coerceIn(0.0, 1.0), it.y.coerceIn(0.0, 1.0)) }.let {
    Quad(it[0], it[1], it[2], it[3])
}

@Composable private fun CardPreview(bytes: ByteArray) {
    rememberCardPreview(bytes)?.let { Image(it.asImageBitmap(), "Cropped card preview", Modifier.fillMaxWidth().height(220.dp)) }
}
