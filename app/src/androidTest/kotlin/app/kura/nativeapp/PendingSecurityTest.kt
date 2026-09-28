package app.kura.nativeapp

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import app.kura.nativecore.VaultResources
import kotlinx.coroutines.*
import org.json.JSONObject
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
        try {
            CaptureFiles.clearOrphans(root)
            assertFalse(stale.exists())
            assertTrue(other.exists())
        } finally { other.delete(); stale.delete(); directory.delete(); root.delete() }
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
                vm.pending.attachmentExport = JSONObject() to export
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
                assertNull(vm.pending.attachmentExport)
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
