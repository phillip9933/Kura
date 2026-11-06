package app.kura.nativeapp

import androidx.test.platform.app.InstrumentationRegistry
import app.kura.feature.FormMemory
import app.kura.nativecore.ExternalOperation
import app.kura.nativecore.ExternalOperationRegistry
import app.kura.nativecore.Generation
import app.kura.nativecore.VaultState
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ImageImportRequestTest {
    @Test fun lockInvalidatesResultWithoutReusingItsLauncherOrRetainingCapture() {
        val pending = PendingOperations()
        val token = Generation(7)
        val registry = ExternalOperationRegistry(android.os.SystemClock::elapsedRealtime)
        val form = pending.uiMemory.form.apply { identity = "passes:new"; values["organizationName"] = "Synthetic draft" }
        pending.imageTarget = ImageTarget("passes", -1, "frontImagePath")
        val capture = File.createTempFile("scan-request-", ".jpg", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        capture.writeText("Synthetic capture")
        val request = ImageImportRequest(token, pending.imageTarget, form, capture)
        pending.cameraRequest = request
        pending.guard = registry.begin(token, ExternalOperation.CAMERA).also { request.guard = it }
        try {
            assertTrue(request.isCurrent(VaultState.Unlocked(token), pending))
            pending.clearVaultContent()
            assertSame("Keep an invalidated tombstone until Android returns the outstanding result", request, pending.cameraRequest)
            assertFalse(capture.exists())
            assertTrue(form.values.isEmpty())
            assertFalse(request.isCurrent(VaultState.Locked, pending))
            val next = Generation(8)
            pending.uiMemory.form = FormMemory().apply { identity = "passes:new" }
            pending.imageTarget = ImageTarget("passes", -1, "frontImagePath")
            pending.guard = registry.begin(next, ExternalOperation.CAMERA)
            assertFalse("A new matching destination cannot revive an old capture", request.isCurrent(VaultState.Unlocked(next), pending))
        } finally { pending.close(); capture.delete() }
    }

    @Test fun replacementDraftDestinationAndExpiredCaptureAreRejected() {
        val pending = PendingOperations()
        val token = Generation(1)
        val registry = ExternalOperationRegistry(android.os.SystemClock::elapsedRealtime)
        val form = pending.uiMemory.form.apply { identity = "passes:new" }
        pending.imageTarget = ImageTarget("passes", -1, "backImagePath")
        val request = ImageImportRequest(token, pending.imageTarget, form)
        pending.guard = registry.begin(token, ExternalOperation.PICKER).also { request.guard = it }
        try {
            assertTrue(request.isCurrent(VaultState.Unlocked(token), pending))
            pending.imageTarget = ImageTarget("passes", -1, "frontImagePath")
            assertFalse(request.isCurrent(VaultState.Unlocked(token), pending))
            pending.imageTarget = request.target
            pending.uiMemory.form = FormMemory().apply { identity = "passes:new" }
            assertFalse("Equal schema identity is insufficient: it must be the original form", request.isCurrent(VaultState.Unlocked(token), pending))
            pending.uiMemory.form = form
            form.clear(); form.identity = "passes:new"
            assertFalse("Reusing the same form object cannot revive a previous draft result", request.isCurrent(VaultState.Unlocked(token), pending))
            val current = ImageImportRequest(token, request.target, form).apply { guard = pending.guard }
            val expired = ImageImportRequest(token, request.target, form, deadline = 0).apply { guard = pending.guard }
            assertFalse(expired.isCurrent(VaultState.Unlocked(token), pending))
            pending.guard = null
            assertTrue(current.isCurrentAfterPermission(VaultState.Unlocked(token), pending))
            assertFalse(expired.isCurrentAfterPermission(VaultState.Unlocked(token), pending))
        } finally { request.guard?.close(); pending.close() }
    }
}
