package app.kura.nativeapp

import android.os.SystemClock
import app.kura.feature.FormMemory
import app.kura.nativecore.ExternalOperationRegistry
import app.kura.nativecore.Generation
import app.kura.nativecore.VaultState
import java.io.File

/** Metadata for one outstanding Android result, never saved across process death. */
internal class ImageImportRequest(
    private val generation: Generation,
    val target: ImageTarget?,
    form: FormMemory,
    val file: File? = null,
    private val deadline: Long = SystemClock.elapsedRealtime() + 120_000,
) {
    private var expectedForm: FormMemory? = form.takeIf { target?.id == -1L }
    private var identity: String? = expectedForm?.identity
    private var formGeneration: Long? = expectedForm?.generation
    private var invalidated = false
    var guard: ExternalOperationRegistry.Guard? = null

    fun isCurrent(state: VaultState, pending: PendingOperations): Boolean =
        matches(state, pending) && pending.guard === guard && guard != null

    fun isCurrentAfterPermission(state: VaultState, pending: PendingOperations): Boolean =
        matches(state, pending) && pending.guard == null

    private fun matches(state: VaultState, pending: PendingOperations): Boolean =
        !invalidated && SystemClock.elapsedRealtime() < deadline &&
            state == VaultState.Unlocked(generation) &&
            pending.imageTarget == target && (target?.id != -1L ||
            (expectedForm != null && identity != null && pending.uiMemory.form === expectedForm && expectedForm?.identity == identity && expectedForm?.generation == formGeneration))

    fun invalidate() {
        invalidated = true
        expectedForm = null
        identity = null
        formGeneration = null
        file?.delete()
    }
}
