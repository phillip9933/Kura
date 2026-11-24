package app.kura.nativeapp

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import app.kura.nativecore.VaultResources
import kotlinx.coroutines.*
import app.kura.nativecore.Generation
import app.kura.nativecore.ExternalOperation
import app.kura.nativecore.VaultState
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PendingSecurityTest {
    @Test fun startupRemovesOnlyAbandonedCameraOutputs() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "capture-cleanup-test-${UUID.randomUUID()}").apply { mkdirs() }
        val directory = File(root, "capture").apply { mkdirs() }
        val stale = File(directory, "scan-test.jpg").apply { writeText("synthetic image") }
        val other = File(directory, "unrelated.txt").apply { writeText("retain") }
        val abandoned = File(root, "card-scans/job/ocr").apply { mkdirs() }
        File(abandoned, "image").writeText("synthetic OCR input")
        try {
            CaptureFiles.clearOrphans(root)
            assertFalse(stale.exists())
            assertFalse(File(root, "card-scans").exists())
            assertTrue(other.exists())
        } finally { other.delete(); stale.delete(); directory.delete(); root.delete() }
    }

    @Test fun startupRejectsScannerLinksWithoutDeletingOtherFiles() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir.canonicalFile, "scanner-link-test-${UUID.randomUUID()}").apply { mkdirs() }
        val outside = File(root, "outside").apply { mkdirs() }
        val retained = File(outside, "synthetic.txt").apply { writeText("retain") }
        val scans = File(root, "card-scans").apply { mkdirs() }
        val link = File(scans, "link")
        try {
            android.system.Os.symlink(outside.path, link.path)
            try { CaptureFiles.clearOrphans(root); fail("Scanner links must be rejected") }
            catch (_: IllegalArgumentException) { }
            assertTrue(retained.isFile)
        } finally { link.delete(); retained.delete(); outside.delete(); scans.delete(); root.delete() }
    }

    @Test fun lockClearsAttachmentGrantsBuffersAndCaptureWithoutActivity() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        androidx.test.uiautomator.UiDevice.getInstance(instrumentation).pressHome()
        withContext(Dispatchers.Main) {
            // A preceding UI test may still have a delayed process ON_STOP pending.
            // Settle that transition before constructing this headless controller.
            withTimeout(5_000) {
                while (androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                        .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) delay(25)
            }
            val vm = VaultController(app)
            val export = ByteArray(32) { 42 }
            val shared = ByteArray(32) { 43 }
            val crop = ByteArray(32) { 44 }
            val capture = File(app.cacheDir, "security-capture-${UUID.randomUUID()}")
            try {
                vm.coordinator.unlock({ ByteArray(32) }, { VaultResources {} })
                yield()
                val session = vm.state.value as VaultState.Unlocked
                val guard = vm.lifecycle.begin(ExternalOperation.SAF_EXPORT)
                val request = DocumentExport(session, guard, Long.MAX_VALUE, ExportPayload(bytes = export))
                vm.pending.exports.reserve(ExportKind.ATTACHMENT, request)
                vm.pending.cropBytes = crop
                capture.writeText("synthetic camera data")
                vm.pending.capture = capture
                val uri = AttachmentContent.publish(app, "test.txt", "text/plain", shared)
                assertEquals("text/plain", app.contentResolver.getType(uri))
                vm.coordinator.lock()
                yield()
                assertTrue(export.all { it == 0.toByte() })
                assertTrue(shared.all { it == 0.toByte() })
                assertTrue(crop.all { it == 0.toByte() })
                assertTrue(ExportKind.ATTACHMENT in vm.pending.exports.kinds)
                val replacementBytes = ByteArray(16) { 7 }
                val replacement = DocumentExport(VaultState.Unlocked(Generation(session.token.value + 1)), guard,
                    Long.MAX_VALUE, ExportPayload(bytes = replacementBytes))
                assertThrows(IllegalStateException::class.java) { vm.pending.exports.reserve(ExportKind.ATTACHMENT, replacement) }
                assertNull(vm.pending.exports.take(ExportKind.ATTACHMENT))
                vm.pending.exports.reserve(ExportKind.ATTACHMENT, replacement)
                assertSame(replacement, vm.pending.exports.take(ExportKind.ATTACHMENT))
                assertTrue(replacementBytes.all { it == 7.toByte() })
                replacement.payload.clear()
                assertFalse(request.current(vm.state.value, guard, 0))
                assertFalse(request.current(session, null, 0))
                assertFalse(request.current(session, guard, Long.MAX_VALUE))
                assertNull(vm.pending.capture)
                assertFalse(capture.exists())
                assertNull(app.contentResolver.getType(uri))
            } finally {
                capture.delete()
                vm.pending.close()
                androidx.lifecycle.ViewModelStore().apply { put("test", vm); clear() }
                yield()
            }
        }
    }
}
