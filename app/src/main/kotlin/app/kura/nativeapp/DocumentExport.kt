package app.kura.nativeapp

import app.kura.nativecore.ExternalOperationRegistry
import app.kura.nativecore.VaultState
import java.io.File

internal enum class ExportKind { ATTACHMENT, PASS, BACKUP }

internal class ExportPayload(private var bytes: ByteArray? = null, private var file: File? = null) {
    fun stage(output: java.io.OutputStream) {
        val source = file
        if (source != null) source.inputStream().use { it.copyTo(output) } else output.write(checkNotNull(bytes))
    }
    fun clear() {
        bytes?.fill(0)
        bytes = null
        file?.delete()
        file = null
    }
}

internal class DocumentExport(
    private val session: VaultState.Unlocked,
    val guard: ExternalOperationRegistry.Guard,
    private val deadline: Long,
    val payload: ExportPayload,
) {
    fun current(state: VaultState, active: ExternalOperationRegistry.Guard?, now: Long) =
        session == state && guard === active && now < deadline
}

/** Keep an expired launcher reserved until Android delivers its outstanding result. */
internal class ExportRequests {
    private val outstanding = mutableMapOf<ExportKind, DocumentExport?>()
    val kinds: Set<ExportKind> get() = outstanding.keys

    fun reserve(kind: ExportKind, request: DocumentExport) {
        check(kind !in outstanding) { "The previous export is still pending" }
        outstanding[kind] = request
    }

    fun take(kind: ExportKind): DocumentExport? = outstanding.remove(kind)

    fun restore(kind: ExportKind) {
        if (kind !in outstanding) outstanding[kind] = null
    }

    fun invalidate() {
        outstanding.entries.forEach { entry ->
            entry.value?.payload?.clear()
            entry.setValue(null)
        }
    }
}
