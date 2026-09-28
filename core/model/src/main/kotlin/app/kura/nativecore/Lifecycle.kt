package app.kura.nativecore

import kotlinx.coroutines.*
import java.io.Closeable

enum class ExternalOperation { CAMERA, PICKER, SAF_IMPORT, SAF_EXPORT, CROP, AUTHENTICATION }

class ExternalOperationRegistry(private val nowMillis: () -> Long, private val maxMillis: Long = 120_000) {
    init { require(maxMillis in 1..600_000) }
    private data class Entry(val generation: Generation, val deadline: Long)
    private val entries = mutableMapOf<Long, Entry>()
    private var sequence = 0L
    inner class Guard internal constructor(val id: Long, val operation: ExternalOperation) : Closeable {
        override fun close() { synchronized(this@ExternalOperationRegistry) { entries.remove(id) } }
    }
    @Synchronized fun begin(token: Generation, operation: ExternalOperation, timeoutMillis: Long = maxMillis): Guard {
        require(timeoutMillis in 1..maxMillis)
        val id = ++sequence
        entries[id] = Entry(token, Math.addExact(nowMillis(), timeoutMillis))
        return Guard(id, operation)
    }
    @Synchronized fun remaining(token: Generation): Long {
        val now = nowMillis()
        entries.entries.removeAll { it.value.deadline <= now || it.value.generation != token }
        return entries.values.maxOfOrNull { it.deadline - now } ?: 0
    }
    @Synchronized fun clear() { entries.clear() }
}

/** Call visibility events on one lifecycle dispatcher. No Activity or screen dependencies. */
class VaultLifecycleEngine(
    private val coordinator: VaultSessionCoordinator,
    private val registry: ExternalOperationRegistry,
    private val scope: CoroutineScope,
) {
    var autoLockMillis: Long = 0
    private var background = false
    private var deadline: Job? = null
    fun foreground() { background = false; deadline?.cancel() }
    suspend fun background() {
        background = true
        evaluate()
    }
    suspend fun operationFinished(guard: ExternalOperationRegistry.Guard) {
        guard.close()
        if (background) evaluate()
    }
    suspend fun screenOffOrExplicitLock() {
        deadline?.cancel(); registry.clear(); coordinator.lock()
    }
    private suspend fun evaluate() {
        deadline?.cancel()
        val token = when (val state = coordinator.state.value) { is VaultState.Unlocked -> state.token; is VaultState.Authenticating -> state.token; else -> null }
        val remaining = maxOf(token?.let(registry::remaining) ?: 0, autoLockMillis.coerceIn(0, 300_000))
        if (remaining == 0L) coordinator.lock()
        else deadline = scope.launch {
            delay(remaining)
            if (background) { registry.clear(); coordinator.lock() }
        }
    }
}
