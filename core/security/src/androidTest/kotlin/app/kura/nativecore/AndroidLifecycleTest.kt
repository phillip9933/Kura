package app.kura.nativecore

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class AndroidLifecycleTest {
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
    @Test fun headlessAndroidLifecycleGuardsPickerAndLocksAfterCancellation() = runBlocking {
        val coordinator = VaultSessionCoordinator()
        coordinator.unlock({ ByteArray(32) }) { VaultResources {} }
        withContext(Dispatchers.Main) {
            val owner = Owner()
            owner.registry.currentState = Lifecycle.State.RESUMED
            val adapter = AndroidVaultLifecycle(InstrumentationRegistry.getInstrumentation().targetContext, coordinator, owner.lifecycle)
            try {
                val guard = adapter.begin(ExternalOperation.PICKER)
                owner.registry.currentState = Lifecycle.State.CREATED
                assertTrue(coordinator.state.value is VaultState.Unlocked)
                adapter.complete(guard)
                assertEquals(VaultState.Locked, coordinator.state.value)
            } finally { adapter.dispose() }
        }
    }
    @Test fun processStopLocksWithoutGuardAndCloseRunsOffMain() = runBlocking {
        val coordinator = VaultSessionCoordinator()
        var closed = false
        coordinator.unlock({ ByteArray(32) }) { VaultResources {
            assertNotEquals(Looper.getMainLooper().thread, Thread.currentThread()); closed = true
        } }
        lateinit var adapter: AndroidVaultLifecycle
        withContext(Dispatchers.Main) {
            val owner = Owner(); owner.registry.currentState = Lifecycle.State.RESUMED
            adapter = AndroidVaultLifecycle(InstrumentationRegistry.getInstrumentation().targetContext, coordinator, owner.lifecycle)
            owner.registry.currentState = Lifecycle.State.CREATED
        }
        withTimeout(5000) { coordinator.state.first { it == VaultState.Locked } }
        assertTrue(closed)
        withContext(Dispatchers.Main) { adapter.dispose() }
    }
}