package app.kura.nativecore

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationLifecycleTest {
    @Test fun externalCredentialGuardSurvivesBackgroundButExpires() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        val registry = ExternalOperationRegistry({ testScheduler.currentTime })
        val engine = VaultLifecycleEngine(coordinator, registry, this)
        val authentication = CompletableDeferred<ByteArray>()
        val opening = async { runCatching { coordinator.unlock({ authentication.await() }) { VaultResources {} } } }
        runCurrent()
        val token = (coordinator.state.value as VaultState.Authenticating).token
        registry.begin(token, ExternalOperation.AUTHENTICATION, 1000)
        engine.background()
        advanceTimeBy(999); runCurrent()
        assertTrue(coordinator.state.value is VaultState.Authenticating)
        advanceTimeBy(1); runCurrent()
        assertEquals(VaultState.Locked, coordinator.state.value)
        assertTrue(opening.await().isFailure)
    }
    @Test fun screenOffOverridesCredentialGuard() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        val registry = ExternalOperationRegistry({ testScheduler.currentTime })
        val engine = VaultLifecycleEngine(coordinator, registry, this)
        val opening = async { runCatching { coordinator.unlock({ awaitCancellation() }) { VaultResources {} } } }
        runCurrent()
        registry.begin((coordinator.state.value as VaultState.Authenticating).token, ExternalOperation.AUTHENTICATION)
        engine.screenOffOrExplicitLock()
        assertEquals(VaultState.Locked, coordinator.state.value)
        assertTrue(opening.await().isFailure)
    }
    @Test fun userTimeoutLocksBackgroundSession() = runTest {
        val coordinator = VaultSessionCoordinator(StandardTestDispatcher(testScheduler))
        val engine = VaultLifecycleEngine(coordinator, ExternalOperationRegistry({ testScheduler.currentTime }), this)
        val token = coordinator.unlock({ ByteArray(32) }) { VaultResources {} }
        engine.autoLockMillis = 30000
        engine.background()
        advanceTimeBy(29999); runCurrent()
        assertEquals(VaultState.Unlocked(token), coordinator.state.value)
        advanceTimeBy(1); runCurrent()
        assertEquals(VaultState.Locked, coordinator.state.value)
    }
}