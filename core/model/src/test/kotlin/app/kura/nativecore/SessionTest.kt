package app.kura.nativecore

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionTest {
    @Test fun lockWipesOwnedArrayAndClosesExactlyOnce() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        val bytes = ByteArray(32) { 7 }
        var closes = 0
        val token = coordinator.unlock({ bytes }) { VaultResources { closes++ } }
        assertEquals(VaultState.Unlocked(token), coordinator.state.value)
        assertEquals(42, coordinator.run(token) { 42 })
        coordinator.lock(); coordinator.lock()
        assertTrue(bytes.all { it == 0.toByte() }); assertEquals(1, closes)
        assertEquals(VaultState.Locked, coordinator.state.value)
        assertFails { coordinator.run(token) { 1 } }
    }
    @Test fun lockDuringAuthenticationRejectsLateNonCancellableResult() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        val bytes = ByteArray(32) { 9 }
        val auth = CompletableDeferred<Unit>()
        var opens = 0
        val unlock = async {
            runCatching { coordinator.unlock({ withContext(NonCancellable) { auth.await(); bytes } }) {
                opens++; VaultResources {}
            } }
        }
        runCurrent()
        assertTrue(coordinator.state.value is VaultState.Authenticating)
        val lock = launch { coordinator.lock() }
        runCurrent(); assertEquals(VaultState.Closing, coordinator.state.value)
        auth.complete(Unit); advanceUntilIdle(); lock.join()
        assertTrue(unlock.await().isFailure); assertEquals(0, opens)
        assertTrue(bytes.all { it == 0.toByte() })
    }
    @Test fun lockDuringOpenClosesLateHandle() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        val release = CompletableDeferred<Unit>()
        var closes = 0
        val unlock = async { runCatching { coordinator.unlock({ ByteArray(32) }) {
            withContext(NonCancellable) { release.await(); VaultResources { closes++ } }
        } } }
        runCurrent(); assertTrue(coordinator.state.value is VaultState.Opening)
        val lock = launch { coordinator.lock() }; runCurrent()
        release.complete(Unit); advanceUntilIdle(); lock.join()
        assertTrue(unlock.await().isFailure); assertEquals(1, closes)
    }
    @Test fun lockCancelsSessionWorkBeforeClosing() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        var cancelled = false
        val token = coordinator.unlock({ ByteArray(32) }) { VaultResources { assertTrue(cancelled) } }
        val work = async { runCatching { coordinator.run(token) { try { awaitCancellation() } finally { cancelled = true } } } }
        runCurrent(); coordinator.lock()
        assertTrue(work.await().isFailure)
    }
    @Test fun concurrentUnlockIsRejectedAndFailureAllowsRetry() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        val blocked = launch { runCatching { coordinator.unlock({ awaitCancellation() }) { VaultResources {} } } }
        runCurrent()
        assertFails { coordinator.unlock({ ByteArray(32) }) { VaultResources {} } }
        coordinator.lock(); blocked.join()
        assertFails { coordinator.unlock({ error("denied") }) { VaultResources {} } }
        coordinator.unlock({ ByteArray(32) }) { VaultResources {} }; coordinator.lock()
    }
    @Test fun concurrentLocksWaitForCleanupAndPreventEarlyReopen() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        val close = CompletableDeferred<Unit>()
        coordinator.unlock({ ByteArray(32) }) { VaultResources { close.await() } }
        val first = launch { coordinator.lock() }; runCurrent()
        val second = launch { coordinator.lock() }; runCurrent()
        assertFalse(second.isCompleted)
        assertFails { coordinator.unlock({ ByteArray(32) }) { VaultResources {} } }
        close.complete(Unit); first.join(); second.join()
        val token = coordinator.unlock({ ByteArray(32) }) { VaultResources {} }
        assertTrue(token.value >= 3); coordinator.lock()
    }
    @Test fun closeFailureStillWipesSecret() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        val bytes = ByteArray(32) { 1 }
        coordinator.unlock({ bytes }) { VaultResources { error("close failed") } }
        assertFails { coordinator.lock() }
        assertTrue(bytes.all { it == 0.toByte() })
    }
    @Test fun guardTimeoutAndScreenOffCannotLeaveVaultOpen() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        val registry = ExternalOperationRegistry({ testScheduler.currentTime }, 1000)
        val engine = VaultLifecycleEngine(coordinator, registry, backgroundScope)
        val token = coordinator.unlock({ ByteArray(32) }) { VaultResources {} }
        registry.begin(token, ExternalOperation.CAMERA)
        engine.background(); advanceTimeBy(999); runCurrent()
        assertTrue(coordinator.state.value is VaultState.Unlocked)
        advanceTimeBy(1); runCurrent(); assertEquals(VaultState.Locked, coordinator.state.value)
        val second = coordinator.unlock({ ByteArray(32) }) { VaultResources {} }
        registry.begin(second, ExternalOperation.SAF_EXPORT)
        engine.background(); engine.screenOffOrExplicitLock()
        assertEquals(VaultState.Locked, coordinator.state.value)
    }
    @Test fun foregroundAndNestedGuardsPreserveSessionButStaleTokensDoNot() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        val registry = ExternalOperationRegistry({ testScheduler.currentTime }, 1000)
        val engine = VaultLifecycleEngine(coordinator, registry, backgroundScope)
        val token = coordinator.unlock({ ByteArray(32) }) { VaultResources {} }
        val first = registry.begin(token, ExternalOperation.PICKER, 500)
        val second = registry.begin(token, ExternalOperation.CROP)
        engine.background(); engine.operationFinished(first)
        assertTrue(coordinator.state.value is VaultState.Unlocked)
        engine.foreground(); engine.operationFinished(second)
        advanceTimeBy(2000); runCurrent()
        assertTrue(coordinator.state.value is VaultState.Unlocked)
        registry.begin(Generation(-1), ExternalOperation.SAF_IMPORT)
        engine.background(); assertEquals(VaultState.Locked, coordinator.state.value)
    }
    @Test fun invalidGuardBoundsAreRejected() {
        val registry = ExternalOperationRegistry({ 0 }, 100)
        assertThrows(IllegalArgumentException::class.java) { registry.begin(Generation(1), ExternalOperation.PICKER, 101) }
        assertThrows(IllegalArgumentException::class.java) { registry.begin(Generation(1), ExternalOperation.PICKER, 0) }
    }
    @Test fun lockWipesRegisteredPlaintextAndRejectsStaleOwnership() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        val token = coordinator.unlock({ ByteArray(32) }) { VaultResources {} }
        val image = ByteArray(1000) { 42 }
        coordinator.own(token, image)
        coordinator.lock()
        assertTrue(image.all { it == 0.toByte() })
        val stale = ByteArray(100) { 9 }
        assertFails { coordinator.own(token, stale) }
        assertTrue(stale.all { it == 0.toByte() })
    }
    @Test fun staleLockCannotCloseNewSession() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        val old = coordinator.unlock({ ByteArray(32) }) { VaultResources {} }
        coordinator.lock()
        val current = coordinator.unlock({ ByteArray(32) }) { VaultResources {} }
        coordinator.lock(old)
        assertEquals(VaultState.Unlocked(current), coordinator.state.value)
        coordinator.lock()
    }
    @Test fun failedCloseQuarantinesCoordinator() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        coordinator.unlock({ ByteArray(32) }) { VaultResources { error("cannot close") } }
        assertFails { coordinator.lock() }
        assertFails { coordinator.unlock({ ByteArray(32) }) { VaultResources {} } }
    }
    @Test fun lockFromSessionWorkIsRejectedInsteadOfDeadlocking() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        val token = coordinator.unlock({ ByteArray(32) }) { VaultResources {} }
        assertFails { coordinator.run(token) { coordinator.lock() } }
        assertEquals(VaultState.Unlocked(token), coordinator.state.value)
        coordinator.lock()
    }
    private suspend fun assertFails(block: suspend () -> Any?) { assertTrue(runCatching { block() }.isFailure) }
}
