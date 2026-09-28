package app.kura.nativecore

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.*

/** Headless process-lifecycle binding. Instantiate and dispose on Android's main thread. */
class AndroidVaultLifecycle(
    suppliedContext: Context,
    private val coordinator: VaultSessionCoordinator,
    private val lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
) : DefaultLifecycleObserver {
    private val context = suppliedContext.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val registry = ExternalOperationRegistry(SystemClock::elapsedRealtime)
    private val engine = VaultLifecycleEngine(coordinator, registry, scope)
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) scope.launch { engine.screenOffOrExplicitLock() }
        }
    }
    init {
        checkMain()
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        lifecycle.addObserver(this)
    }
    override fun onStart(owner: LifecycleOwner) { engine.foreground() }
    override fun onStop(owner: LifecycleOwner) { scope.launch { engine.background() } }
    fun begin(operation: ExternalOperation, timeoutMillis: Long = 120_000): ExternalOperationRegistry.Guard {
        checkMain()
        val token = when (val state = coordinator.state.value) { is VaultState.Unlocked -> state.token; is VaultState.Authenticating -> { check(operation == ExternalOperation.AUTHENTICATION); state.token }; else -> error("Vault is locked") }
        return registry.begin(token, operation, timeoutMillis)
    }
    fun setAutoLockMillis(value: Long) { checkMain(); engine.autoLockMillis = value.coerceIn(0, 300_000) }
    suspend fun complete(guard: ExternalOperationRegistry.Guard) { checkMain(); engine.operationFinished(guard) }
    suspend fun dispose() {
        checkMain(); lifecycle.removeObserver(this); context.unregisterReceiver(screenOff)
        try { engine.screenOffOrExplicitLock() } finally { scope.cancel() }
    }
    private fun checkMain() { check(Looper.myLooper() == Looper.getMainLooper()) }
}