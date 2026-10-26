package app.kura.nativeapp

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.work.Configuration
import io.github.phillip9933.appupdater.UpdateConfig
import io.github.phillip9933.appupdater.UpdateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

internal val kuraUpdateConfig = UpdateConfig(
    owner = "phillip9933",
    repository = "Kura",
    tagPrefix = "v",
    assetPrefix = "Kura-",
)

/** Update metadata has no reference to the vault, its controller or authentication. */
class KuraUpdateApplication : Application(), Configuration.Provider {
    internal val updatePanelVisible = MutableStateFlow(false)

    lateinit var updateManager: UpdateManager
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        // Network activity is opt-in on the first run, including background workers.
        val preferences = getSharedPreferences(kuraUpdateConfig.stateName, MODE_PRIVATE)
        if (!preferences.contains("automatic")) {
            preferences.edit().putBoolean("automatic", false).apply()
        }
        updateManager = UpdateManager.get(this, kuraUpdateConfig)
        updateManager.initialize()
    }
}

@Composable
fun AppUpdateEntry() {
    val context = LocalContext.current
    val application = remember { context.applicationContext as KuraUpdateApplication }
    val state by application.updateManager.state.collectAsState()
    TextButton(onClick = { application.updatePanelVisible.value = true }) {
        Text(if (state.available != null) "App updates · update available" else "App updates")
    }
}

@Composable
fun AppUpdateNotice() {
    val context = LocalContext.current
    val application = remember { context.applicationContext as KuraUpdateApplication }
    val updater = application.updateManager
    val state by updater.state.collectAsState()
    val visible by application.updatePanelVisible.collectAsState()
    fun closePanel() {
        updater.dismissOffer()
        application.updatePanelVisible.value = false
    }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Returning from permission settings never automatically starts an installation.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (lifecycleState == Lifecycle.State.RESUMED && state.offer && !visible) {
        AlertDialog(
            onDismissRequest = { updater.dismissOffer() },
            title = { Text("New version available") },
            text = { Text("Kura ${state.available?.tag} is available. Review the update before downloading or installing it.") },
            confirmButton = { TextButton(onClick = { updater.dismissOffer(); application.updatePanelVisible.value = true }) { Text("View update") } },
            dismissButton = { TextButton(onClick = { updater.dismissOffer() }) { Text("Later") } },
        )
    }
    if (!visible) return
    AlertDialog(
        onDismissRequest = { closePanel() },
        title = { Text("App updates") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Installed: ${state.installedVersion}")
                Text("Checks contact GitHub for public Kura releases. Wallet contents are never sent. Installation always needs your confirmation.")
                if (BuildConfig.PROTOTYPE) Text("This testing app cannot install production Kura updates. Use the production APK for in-app upgrades.")
                Row {
                    Checkbox(state.automaticChecks, { updater.setAutomaticChecks(it) })
                    Text("Check automatically (about daily)")
                }
                Row {
                    Checkbox(state.wifiDownload, { updater.setWifiDownload(it) })
                    Text("Download updates automatically on Wi-Fi")
                }
                TextButton(onClick = { scope.launch { updater.checkNow() } }, enabled = !state.checking && !state.downloading) {
                    Text(if (state.checking) "Checking…" else "Check for updates")
                }
                state.available?.let { release ->
                    Text("Available: ${release.tag}")
                    Text(release.notes.take(4000))
                    if (state.downloading) {
                        Text("Downloaded ${state.bytesDownloaded / 1024} KiB")
                        TextButton(onClick = { updater.cancelDownload() }) { Text("Cancel download") }
                    } else if (!state.ready) {
                        TextButton(onClick = { scope.launch { updater.download() } }, enabled = !BuildConfig.PROTOTYPE) {
                            Text("Download update")
                        }
                    } else {
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    if (!context.packageManager.canRequestPackageInstalls()) {
                                        message = "Allow Kura to install updates, then return and tap Install update again."
                                        launcher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                                    } else {
                                        val intent = updater.prepareInstall()
                                        if (intent != null) launcher.launch(intent)
                                        else message = "The update could not be verified. Check again and download a fresh copy."
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    message = "Android could not open the installer or permission settings."
                                }
                            }
                        }, enabled = !BuildConfig.PROTOTYPE) { Text("Install update") }
                    }
                }
                state.error?.let { Text("Update could not complete: ${it.name.lowercase().replace('_', ' ')}. Your installed app and wallet are unchanged.") }
                message?.let { Text(it) }
                TextButton(onClick = {
                    try { launcher.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/phillip9933/Kura/releases"))) }
                    catch (_: Exception) { message = "No browser is available." }
                }) { Text("View releases on GitHub") }
            }
        },
        confirmButton = { TextButton(onClick = { closePanel() }) { Text("Done") } },
    )
}
