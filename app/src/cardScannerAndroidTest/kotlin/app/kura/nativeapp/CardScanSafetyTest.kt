package app.kura.nativeapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.kura.feature.FormMemory
import app.kura.nativecore.*
import dev.offlinescan.core.Point
import dev.offlinescan.core.Quad
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class CardScanSafetyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private fun disposable() {
        check(context.packageName == "app.kura.wallet.prototype")
        check(Build.HARDWARE in setOf("ranchu", "goldfish"))
    }
    private fun image(color: Int, text: Boolean = true): ByteArray {
        val bitmap = Bitmap.createBitmap(1600, 1000, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(color)
            if (text) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; textSize = 70f }
                canvas.drawText("Member number: 00001234", 100f, 350f, paint)
                canvas.drawText("Expires: 2030-12-31", 100f, 600f, paint)
            }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            return output.toByteArray()
        } finally { bitmap.eraseColor(0); bitmap.recycle(); output.close() }
    }
    private fun open(): VaultController {
        disposable()
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val legacy = when {
            Build.VERSION.SDK_INT < 30 -> device.wait(Until.findObject(By.res("com.android.settings", "password_entry")), 5000)
            Build.VERSION.SDK_INT <= 35 -> device.wait(Until.findObject(By.res(java.util.regex.Pattern.compile("com\\.android\\.systemui:id/(lockPassword|pinEntry)"))), 5000)
            else -> null
        }
        val pin = if (legacy == null) device.wait(Until.findObject(By.text("2").pkg("com.android.systemui")), 5000) else null
        if (legacy != null) {
            legacy.click(); device.executeShellCommand("input text 2468"); device.pressEnter()
        } else if (pin != null) {
            listOf("2", "4", "6", "8").forEach { device.findObject(By.text(it).pkg("com.android.systemui")).click() }
            device.findObject(By.desc("Enter").pkg("com.android.systemui")).click()
        }
        assertNotNull(device.wait(Until.findObject(By.desc("Settings")), 20000))
        lateinit var vm: VaultController
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            vm = ViewModelProvider(activity)[VaultController::class.java]
        }
        runBlocking { withTimeout(10000) { while (vm.busy.value) delay(25) } }
        return vm
    }
    private fun form(vm: VaultController, name: String): FormMemory {
        lateinit var form: FormMemory
        instrumentation.runOnMainSync {
            form = FormMemory().apply {
                initialize("passes", null, "wallets", vm.presentation.value.preferences)
                values["organizationName"] = name
                values["barcodeValue"] = "00005555"
            }
            vm.pending.uiMemory.apply {
                this.form = form; manualForms["passes"] = form
                manualSection.value = "wallets"; manualCategory.value = "Membership"
                table.value = "wallets"; adding.value = true; addCardKind.value = "passes"
            }
        }
        return form
    }
    private fun scan(vm: VaultController, column: String, bytes: ByteArray) {
        instrumentation.runOnMainSync {
            vm.pending.imageTarget = ImageTarget("passes", -1, column)
            vm.pending.cropBytes = bytes
        }
        assertNotNull(device.wait(Until.findObject(By.text("Use crop").enabled(true)), 20000))
    }
    private fun click(label: String) = clickJourneyControl(device, label)
    private fun crop() {
        click("Use full image"); click("Use crop")
        assertNotNull(device.wait(Until.findObject(By.text("Autofill card details?")), 20000))
    }
    private fun assertColor(bytes: ByteArray, red: Boolean) {
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inMutable = true }))
        try {
            val pixel = bitmap.getPixel(10, 10)
            assertTrue(if (red) Color.red(pixel) > Color.blue(pixel) + 60 else Color.blue(pixel) > Color.red(pixel) + 60)
        } finally { bitmap.eraseColor(0); bitmap.recycle(); bytes.fill(0) }
    }

    /** A separable optical blur fixture; resizing alone leaves misleading sharp pixel edges. */
    private fun blurPhoto(bytes: ByteArray): ByteArray {
        val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val small = Bitmap.createScaledBitmap(source, 512, 320, true)
        val pixels = IntArray(512 * 320)
        val horizontal = IntArray(pixels.size)
        val result = IntArray(pixels.size)
        val bitmap = Bitmap.createBitmap(512, 320, Bitmap.Config.ARGB_8888)
        val output = ByteArrayOutputStream()
        try {
            small.getPixels(pixels, 0, 512, 0, 0, 512, 320)
            for (y in 0 until 320) for (x in 0 until 512) {
                var total = 0
                for (dx in -4..4) {
                    val color = pixels[y * 512 + (x + dx).coerceIn(0, 511)]
                    total += (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
                }
                horizontal[y * 512 + x] = total / 9
            }
            for (y in 0 until 320) for (x in 0 until 512) {
                var total = 0
                for (dy in -4..4) total += horizontal[(y + dy).coerceIn(0, 319) * 512 + x]
                val gray = total / 9
                result[y * 512 + x] = Color.rgb(gray, gray, gray)
            }
            bitmap.setPixels(result, 0, 512, 0, 0, 512, 320)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            return output.toByteArray()
        } finally {
            pixels.fill(0); horizontal.fill(0); result.fill(0); output.close()
            listOf(bitmap, small, source).distinct().forEach { if (it.isMutable) it.eraseColor(0); it.recycle() }
        }
    }

    @Test fun nativeQualityWarnsAboutLightWithoutCallingBlankCardsBlurry() = runBlocking {
        disposable()
        val engine = CardScanEngine(context)
        for ((color, expected) in listOf(Color.WHITE to "very bright", Color.BLACK to "dark")) {
            val bytes = image(color, text = false)
            try {
                val quality = engine.quality(bytes, Quad.FULL)
                val warnings = cardPhotoWarnings(quality, Quad.FULL)
                assertTrue(warnings.any { expected in it })
                assertFalse("Blank photos provide no reliable focus evidence", warnings.any { "blurry" in it })
                val clipped = Quad(Point(.001, .05), Point(.95, .05), Point(.95, .95), Point(.001, .95))
                assertTrue(cardPhotoWarnings(quality, clipped).any { "cut-off" in it })
            } finally { bytes.fill(0) }
        }
        val sharp = image(Color.BLUE)
        val soft = blurPhoto(sharp)
        try {
            val clearQuality = engine.quality(sharp, Quad.FULL)
            val blurQuality = engine.quality(soft, Quad.FULL)
            assertTrue("Synthetic detail must retain focus evidence: $blurQuality", blurQuality.informativeTiles >= 2)
            assertTrue("Native blur signal must decrease: $clearQuality / $blurQuality", clearQuality.sharpness > blurQuality.sharpness)
            assertTrue("Warn about the native blurred-text fixture: $blurQuality", cardPhotoWarnings(blurQuality, Quad.FULL).any { "blurry" in it })
        } finally { sharp.fill(0); soft.fill(0) }
        assertTrue(File(context.cacheDir, "card-scans").listFiles().orEmpty().isEmpty())
    }

    @Test fun typedInvalidAndOversizedPhotosLeaveNoWorkspace() = runBlocking {
        disposable()
        val engine = CardScanEngine(context)
        for ((bytes, expected) in listOf(ByteArray(32) to dev.offlinescan.core.ErrorCode.INVALID_IMAGE,
            ByteArray(10 * 1024 * 1024 + 1) to dev.offlinescan.core.ErrorCode.RESOURCE_LIMIT)) {
            try {
                try { engine.crop(bytes, Quad.FULL, 0); fail("Invalid input must fail") }
                catch (failure: dev.offlinescan.core.ScanException) {
                    assertEquals(expected, failure.error.code)
                    assertTrue(cardScanFailureMessage(failure, "fallback").contains("Retake"))
                }
                assertTrue(File(context.cacheDir, "card-scans").listFiles().orEmpty().isEmpty())
            } finally { bytes.fill(0) }
        }
    }

    @Test fun queuedImageCannotAttachToAReplacementDraftWithTheSameSchema() {
        val vm = open()
        val originalForm = form(vm, "Synthetic original draft")
        val target = ImageTarget("passes", -1, "frontImagePath")
        val bytes = image(Color.BLUE)
        val directory = runBlocking { vm.work { File(it.directory, "media") } }
        val before = directory.listFiles().orEmpty().map { it.name }.toSet()
        val operations = VaultController::class.java.getDeclaredField("operations").run { isAccessible = true; get(vm) as kotlinx.coroutines.sync.Mutex }
        var held = true
        var job: Job? = null
        runBlocking { operations.lock() }
        try {
            instrumentation.runOnMainSync { job = vm.launch { vm.imageChange(target, bytes) } }
            instrumentation.runOnMainSync {
                // The same form object and schema identity can be reused after Cancel.
                originalForm.clear()
                originalForm.initialize("passes", null, "wallets", vm.presentation.value.preferences)
                originalForm.values["organizationName"] = "Synthetic replacement draft"
            }
            operations.unlock(); held = false
            runBlocking { withTimeout(15000) { job!!.join() } }
            assertTrue("Queued photo work must not publish into a replacement draft", vm.pending.draftImages.isEmpty())
            assertEquals("Rejected image must leave no encrypted orphan", before, directory.listFiles().orEmpty().map { it.name }.toSet())
        } finally {
            if (held) operations.unlock()
            runBlocking { withTimeout(15000) { job?.join() } }
            bytes.fill(0)
            instrumentation.runOnMainSync { vm.error.value = null; vm.pending.discardDraft(); vm.pending.uiMemory.clear() }
        }
    }

    @Test fun frontBackAndConflictingFieldsSurviveExplicitSaveAndEncryptedBackup() {
        val vm = open()
        val marker = "Scanner safety " + UUID.randomUUID().toString().take(8)
        val form = form(vm, marker)
        val backup = File.createTempFile("scanner-roundtrip-", ".wbk", context.cacheDir)
        val password = "Synthetic scanner backup password".toCharArray()
        var staged: File? = null
        try {
            scan(vm, "frontImagePath", image(Color.RED)); crop(); click("Review autofill")
            assertNotNull(device.wait(Until.findObject(By.text("Review recognized details")), 45000))
            assertNotNull(device.wait(Until.findObject(By.text("Member number")), 5000))
            assertTrue(form.customValues["Member number"].orEmpty().isBlank())
            // Simulate an intervening correction/source result after suggestions were
            // produced. Applying OCR must recheck current values rather than overwrite it.
            instrumentation.runOnMainSync { form.customValues["Member number"] = "00009999" }
            click("Apply selected details and use image")
            assertTrue(device.wait(Until.gone(By.text("Review recognized details")), 20000))
            assertEquals("00009999", form.customValues["Member number"])
            assertEquals("00005555", form.values["barcodeValue"])
            assertEquals(marker, form.values["organizationName"])
            scan(vm, "backImagePath", image(Color.BLUE)); crop(); click("Use image only")
            assertTrue(device.wait(Until.gone(By.text("Autofill card details?")), 20000))
            val front = requireNotNull(vm.pending.draftImages["frontImagePath"])
            val back = requireNotNull(vm.pending.draftImages["backImagePath"])
            assertNotEquals(front, back)
            assertTrue(front.name.endsWith(".enc") && back.name.endsWith(".enc"))
            runBlocking { assertColor(vm.image(front.name), true); assertColor(vm.image(back.name), false) }
            assertTrue(runBlocking { vm.work { vm.store.rows(it, "passes").none { row -> row.optString("organizationName") == marker } } })
            click("Save")
            runBlocking {
                withTimeout(10000) { while (vm.busy.value) delay(25) }
                vm.work { opened ->
                    val row = vm.store.rows(opened, "passes").single { it.optString("organizationName") == marker }
                    assertEquals("00005555", row.getString("barcodeValue"))
                    assertTrue(row.getString("fields").contains("00009999"))
                    val previous = vm.store.active()
                    vm.store.export(opened).use { payload -> backup.outputStream().use { StreamingBackup.write(payload, it, password) } }
                    backup.inputStream().use { input ->
                        StreamingBackup.read(input, password, context.cacheDir).use { payload ->
                            staged = vm.store.stage(payload, opened.key)
                        }
                    }
                    assertEquals("Backup staging must preserve the current wallet", previous, vm.store.active())
                    val databasePassword = SensitiveBytes(opened.key.useBytes { Envelope.encode(it).toByteArray() })
                    val rooms = databasePassword.use { VaultDatabases.open(context, staged!!, it) }
                    val restored = VaultStore.Opened(staged!!, rooms, opened.key)
                    try {
                        val restoredRow = vm.store.rows(restored, "passes").single { it.optString("organizationName") == marker }
                        assertEquals("00005555", restoredRow.getString("barcodeValue"))
                        assertTrue(restoredRow.getString("fields").contains("00009999"))
                        val media = EncryptedMediaStorage(File(staged, "media"))
                        assertColor(media.read(restoredRow.getString("frontImagePath"), opened.key), true)
                        assertColor(media.read(restoredRow.getString("backImagePath"), opened.key), false)
                    } finally { restored.close() }
                }
            }
        } finally {
            password.fill('\u0000'); backup.delete(); staged?.deleteRecursively()
            if (vm.state.value is VaultState.Unlocked) runBlocking {
                vm.work { opened -> vm.store.rows(opened, "passes").filter { it.optString("organizationName") == marker }.forEach { vm.store.deleteRecord(opened, "passes", it.getLong("id")) } }
                vm.refresh()
            }
            instrumentation.runOnMainSync { vm.pending.discardDraft(); vm.pending.uiMemory.clear() }
        }
    }

    @Test fun lockDuringRecognitionDiscardsDraftAndRejectsExpiredGeneration() {
        val phase = InstrumentationRegistry.getArguments().getString("scannerDeathPhase")
        if (phase != null) { processDeathPhase(phase); return }
        val vm = open()
        val form = form(vm, "Synthetic lock draft")
        val original = image(Color.BLUE)
        val token = (vm.state.value as VaultState.Unlocked).token
        scan(vm, "frontImagePath", original); crop()
        runBlocking {
            // Observe the native workspace before clicking; a fast engine can finish
            // before a generic UI-idle helper returns from the tap.
            val locking = async(Dispatchers.IO) { awaitRecognitionWorkspace(); vm.lock() }
            try {
                requireNotNull(device.findObject(By.text("Review autofill"))).click()
                locking.await()
            } finally { locking.cancelAndJoin() }
        }
        assertNotNull(device.wait(Until.findObject(By.desc("Unlock Kura")), 10000))
        instrumentation.waitForIdleSync()
        assertTrue(original.all { it == 0.toByte() })
        assertTrue(form.values.isEmpty() && form.customValues.isEmpty())
        assertNull(vm.pending.cropBytes); assertTrue(vm.pending.draftImages.isEmpty())
        assertTrue(File(context.cacheDir, "card-scans").listFiles().orEmpty().isEmpty())
        var lateMutation = false
        try { runBlocking { vm.coordinator.run(token) { lateMutation = true } }; fail("Expired generation accepted work") }
        catch (_: IllegalStateException) { }
        assertFalse(lateMutation)
        assertFalse(device.hasObject(By.text("Apply selected details and use image")))
    }

    @Test fun retakeKeepsBackDestinationAndCancelDiscardsOwnedCapture() {
        val vm = open()
        form(vm, "Synthetic retake draft")
        try {
            device.executeShellCommand("pm grant ${context.packageName} android.permission.CAMERA")
            val original = image(Color.BLUE)
            scan(vm, "backImagePath", original)
            click("Retake photo")
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Retake: tapped; foreground=${device.currentPackageName}\n") })
            runBlocking { withTimeout(10000) { while (vm.pending.cameraRequest == null) delay(25) } }
            val request = requireNotNull(vm.pending.cameraRequest)
            assertEquals("backImagePath", request.target?.column)
            assertTrue(original.all { it == 0.toByte() })
            assertNull(vm.pending.cropBytes)
            assertTrue(requireNotNull(request.file).exists())
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Retake: camera request captured; foreground=${device.currentPackageName}\n") })
            // Android8's preinstalled Camera asks for location on its first use.
            // This is the camera app's prompt, not a Kura permission. Decline it
            // before cancelling capture; Back alone does not dismiss that prompt.
            if (Build.VERSION.SDK_INT <= 28 && device.wait(Until.hasObject(By.res("com.android.packageinstaller", "permission_message").textContains("location")), 5000)) {
                requireNotNull(device.findObject(By.res("com.android.packageinstaller", "permission_deny_button"))).click()
            }
            if (Build.VERSION.SDK_INT <= 28) {
                val tagging = device.wait(Until.findObject(By.res("com.android.camera2", "check_box")), 2000)
                if (tagging != null) {
                    if (tagging.isChecked) tagging.click()
                    requireNotNull(device.findObject(By.res("com.android.camera2", "confirm_button"))).click()
                    assertNotNull(device.wait(Until.findObject(By.desc("Shutter").pkg("com.android.camera2")), 10000))
                }
            }
            device.pressBack()
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Retake: Back; foreground=${device.currentPackageName}\n") })
            runBlocking { withTimeout(10000) { while (vm.pending.cameraRequest != null || vm.busy.value) delay(25) } }
            assertFalse(request.file!!.exists())
            assertTrue(vm.pending.draftImages.isEmpty())
        } finally {
            if (vm.pending.cameraRequest != null) {
                device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "retake-failure.xml"))
                instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Retake failure: pending camera; foreground=${device.currentPackageName}\n") })
            }
            // The disposable runner owns runtime-permission reset between scenarios;
            // revoking this UID's camera grant here would kill its instrumentation.
            instrumentation.runOnMainSync { vm.pending.discardDraft() }
        }
    }

    private suspend fun awaitRecognitionWorkspace() {
        withTimeout(10000) {
            while (File(context.cacheDir, "card-scans").listFiles().orEmpty().none { File(it, "ocr/tessdata").exists() }) delay(1)
        }
    }

    private fun processDeathPhase(phase: String) {
        when (phase) {
            "prepare" -> {
                val vm = open()
                val count = runBlocking { vm.work { vm.store.rows(it, "passes").size } }
                context.getSharedPreferences("scanner-death-fixture", 0).edit().putInt("rows", count).commit()
                form(vm, "Synthetic process-death draft")
                scan(vm, "frontImagePath", image(Color.RED)); crop()
                runBlocking {
                    val ready = async(Dispatchers.IO) {
                        awaitRecognitionWorkspace()
                        val capture = File(context.cacheDir, "capture").apply { mkdirs() }.let { File(it, "scan-death-fixture.jpg") }
                        val bytes = image(Color.RED)
                        try { capture.writeBytes(bytes) } finally { bytes.fill(0) }
                        instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "SCANNER_PROCESS_DEATH_READY\n") })
                    }
                    requireNotNull(device.findObject(By.text("Review autofill"))).click()
                    ready.await()
                    awaitCancellation()
                }
            }
            "verify" -> {
                val vm = open()
                assertTrue(File(context.cacheDir, "card-scans").listFiles().orEmpty().isEmpty())
                assertFalse(File(context.cacheDir, "capture/scan-death-fixture.jpg").exists())
                assertNull(vm.pending.cropBytes); assertNull(vm.pending.imageTarget)
                assertTrue(vm.pending.draftImages.isEmpty())
                assertTrue(vm.pending.uiMemory.form.values.isEmpty())
                val count = context.getSharedPreferences("scanner-death-fixture", 0).getInt("rows", -1)
                assertEquals(count, runBlocking { vm.work { vm.store.rows(it, "passes").size } })
                context.getSharedPreferences("scanner-death-fixture", 0).edit().clear().commit()
            }
            else -> error("Unknown scanner death phase")
        }
    }
}
