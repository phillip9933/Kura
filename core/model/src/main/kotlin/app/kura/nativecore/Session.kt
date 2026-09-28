package app.kura.nativecore

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Owns the supplied array. Callers must not retain aliases or immutable key strings. */
class SensitiveBytes(private val bytes: ByteArray) : Closeable {
    private var closed = false
    @Synchronized fun <T> useBytes(block: (ByteArray) -> T): T {
        check(!closed) { "Secret is closed" }
        return block(bytes)
    }
    @Synchronized override fun close() { bytes.fill(0); closed = true }
}

private class SessionWork : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SessionWork>
}

data class Generation(val value: Long)
sealed interface VaultState {
    data object Locked : VaultState
    data class Authenticating(val token: Generation) : VaultState
    data class Opening(val token: Generation) : VaultState
    data class Unlocked(val token: Generation) : VaultState
    data object Closing : VaultState
}

/** close must release every handle, even if one close fails; openers own partial failures. */
fun interface VaultResources { suspend fun close() }

class VaultSessionCoordinator(private val io: CoroutineDispatcher = Dispatchers.IO) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<VaultState>(VaultState.Locked)
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var cleanupFailed = false
    private var session: CompletableJob? = null
    private var resources: VaultResources? = null
    private var secret: SensitiveBytes? = null
    private val buffers = mutableListOf<SensitiveBytes>()
    private var closing: CompletableDeferred<Unit>? = null

    suspend fun unlock(authenticate: suspend () -> ByteArray,
                       open: suspend (SensitiveBytes) -> VaultResources): Generation {
        val (token, job) = mutex.withLock {
            check(mutableState.value == VaultState.Locked && !cleanupFailed) { "Session is busy or cleanup failed" }
            val token = Generation(++generation)
            val job = SupervisorJob()
            session = job
            mutableState.value = VaultState.Authenticating(token)
            token to job
        }
        val result = CoroutineScope(job + io + SessionWork()).async {
            var ownedSecret: SensitiveBytes? = null
            var ownedResources: VaultResources? = null
            try {
                ownedSecret = SensitiveBytes(authenticate())
                ensureActive()
                mutex.withLock {
                    check(generation == token.value)
                    mutableState.value = VaultState.Opening(token)
                }
                ownedResources = open(ownedSecret)
                ensureActive()
                mutex.withLock {
                    check(generation == token.value)
                    resources = ownedResources
                    secret = ownedSecret
                    ownedResources = null
                    ownedSecret = null
                    mutableState.value = VaultState.Unlocked(token)
                }
                token
            } finally {
                withContext(NonCancellable + io) {
                    val cleanupError = runCatching { ownedResources?.close() }.exceptionOrNull()
                    ownedSecret?.close()
                    mutex.withLock {
                        if (cleanupError != null) cleanupFailed = true
                        if (generation == token.value && mutableState.value !is VaultState.Unlocked) {
                            mutableState.value = VaultState.Locked
                            session = null
                            job.cancel()
                        }
                    }
                }
            }
        }
        try { return result.await() } catch (e: CancellationException) {
            withContext(NonCancellable) { lock(token) }
            throw e
        }
    }

    /** Transfer ownership of decrypted buffers to this session so lock overwrites them. */
    suspend fun own(token: Generation, bytes: ByteArray): SensitiveBytes = mutex.withLock {
        if (mutableState.value != VaultState.Unlocked(token)) {
            bytes.fill(0)
            error("Expired session")
        }
        SensitiveBytes(bytes).also(buffers::add)
    }
    /** Reject stale callers and keep all their work in the cancellable session scope. */
    suspend fun <T> run(token: Generation, block: suspend CoroutineScope.() -> T): T {
        val task = mutex.withLock {
            check(mutableState.value == VaultState.Unlocked(token)) { "Expired session" }
            CoroutineScope(session!! + io + SessionWork()).async(block = block)
        }
        val result = try { task.await() } catch (e: CancellationException) { task.cancel(); throw e }
        mutex.withLock { check(mutableState.value == VaultState.Unlocked(token)) { "Expired session" } }
        return result
    }

    suspend fun lock(expected: Generation? = null) {
        check(currentCoroutineContext()[SessionWork] == null) { "Request lock from the lifecycle owner, not session work" }
        withContext(NonCancellable) {
        var owner = false
        var job: CompletableJob? = null
        var handles: VaultResources? = null
        var key: SensitiveBytes? = null
        var plaintext: List<SensitiveBytes> = emptyList()
        val completion = mutex.withLock {
            if (expected != null && generation != expected.value) return@withLock null
            closing?.let { return@withLock it }
            if (mutableState.value == VaultState.Locked) return@withLock null
            owner = true
            ++generation
            mutableState.value = VaultState.Closing
            job = session; handles = resources; key = secret
            session = null; resources = null; secret = null
            plaintext = buffers.toList(); buffers.clear()
            CompletableDeferred<Unit>().also { closing = it }
        }
        if (owner) {
            var failure: Throwable? = null
            try {
                job?.cancelAndJoin()
                withContext(io) { handles?.close() }
            } catch (e: Throwable) { failure = e }
            finally {
                key?.close(); plaintext.forEach { it.close() }
                mutex.withLock {
                    mutableState.value = VaultState.Locked
                    closing = null
                    cleanupFailed = cleanupFailed || failure != null
                    if (failure == null) completion!!.complete(Unit)
                    else completion!!.completeExceptionally(failure!!)
                }
            }
        }
        completion?.await()
        Unit
    }
    }
}
