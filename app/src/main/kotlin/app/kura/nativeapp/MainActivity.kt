package app.kura.nativeapp

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.AtomicFile
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.combine
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.kura.feature.VaultScreen
import app.kura.nativecore.*
import com.google.zxing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : FragmentActivity() {
    private val vm: VaultController by viewModels()
    private val prefs by lazy { getSharedPreferences("native-settings", MODE_PRIVATE) }
    private val hardware by lazy { HardwareVaultKey(this, allowSoftwareForTests = BuildConfig.PROTOTYPE && Build.HARDWARE in setOf("ranchu", "goldfish")) }
    private val envelope by lazy { AtomicFile(File(noBackupFilesDir, "native-key-envelope")) }
    private val oldBiometric by lazy { HardwareVaultKey(this, "app.kura.wallet.native.master.biometric.v1", BuildConfig.PROTOTYPE && Build.HARDWARE in setOf("ranchu", "goldfish"), biometricOnly = true) }
    private val biometricEnvelope by lazy { AtomicFile(File(noBackupFilesDir, "native-biometric-envelope")) }
    private class CredentialFallback : Exception()
    private var guard: ExternalOperationRegistry.Guard?
        get() = vm.pending.guard
        set(value) { vm.pending.guard = value }
    private lateinit var biometricPrompt: BiometricPrompt
    private var credentialResult: CancellableContinuation<Boolean>?
        get() = vm.pending.credential
        set(value) { vm.pending.credential = value }
    private var pendingStage: PassStagingArea?
        get() = vm.pending.stage
        set(value) { vm.pending.stage = value }
    private var passwordAction: String?
        get() = vm.pending.passwordAction
        set(value) { vm.pending.passwordAction = value }
    private var backupUri: Uri?
        get() = vm.pending.backupUri
        set(value) { vm.pending.backupUri = value }
    private var exportFile: File?
        get() = vm.pending.exportFile
        set(value) { vm.pending.exportFile = value }
    private var restoreDirectory: File?
        get() = vm.pending.restoreDirectory
        set(value) { vm.pending.restoreDirectory = value; if (value == null) vm.pending.restoreMissingImages = 0 }
    private var capture: File?
        get() = vm.pending.capture
        set(value) { vm.pending.capture = value }
    private var cropBytes: ByteArray?
        get() = vm.pending.cropBytes
        set(value) { vm.pending.cropBytes = value }
    private var biometricPreference by mutableStateOf(true)
    private var autoLockPreference by mutableLongStateOf(0L)

    private val credential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        credentialResult?.let { pending -> if(pending.isActive) pending.resume(it.resultCode == Activity.RESULT_OK) }
        credentialResult = null
    }
    private val backupFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri->
        vm.launch {try {
            if(uri!=null) {
                contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                vm.preference("autoBackupUri",uri.toString())
                vm.preference("autoBackupPath",android.provider.DocumentsContract.getTreeDocumentId(uri))
                vm.queueAutomaticBackup(true)
            }
        } finally {finishGuard()}}
    }
    private val passPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.launch {
            try {
                if (uri != null) pendingStage = vm.work { opened ->
                    PassStagingArea.create(File(cacheDir, "pass-staging"), contentResolver.openInputStream(uri)!!.use { stream -> PkpassParser.parse(stream, resources.configuration.locales[0].language) }, opened.key)
                }
            } finally { finishGuard() }
        }
    }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.launch {
            try {
                if(uri!=null && vm.pending.imageTarget!=null) {
                    var owned:ByteArray?=null
                    try {
                        vm.work {
                            val raw=contentResolver.openInputStream(uri)!!.use {Envelope.readBounded(it,10*1024*1024)}
                            owned=try {CapturedImageScanner.crop(raw,1f,.5f,.5f)} finally {raw.fill(0)}
                        }
                        cropBytes=owned;owned=null
                    } finally {owned?.fill(0)}
                } else vm.pending.imageTarget=null
            } finally {finishGuard()}
        }
    }
    private val attachmentPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        val target=vm.pending.attachmentTarget;vm.pending.attachmentTarget=null
        vm.launch {try {if(uri!=null && target!=null) {
            val data=withContext(Dispatchers.IO) {contentResolver.openInputStream(uri)!!.use {Envelope.readBounded(it,ItemMedia.MAX_FILE)}}
            try {
                val name=contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst()) it.getString(0) else null} ?: "Attachment"
                vm.attach(target,name,contentResolver.getType(uri) ?: "application/octet-stream",data)
            } finally {data.fill(0)}
        }} finally {finishGuard()}}
    }
    private val attachmentExporter=registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri->
        val data=vm.pending.attachmentExport;vm.pending.attachmentExport=null
        vm.launch {try {if(uri!=null && data!=null) vm.work {contentResolver.openOutputStream(uri,"wt")!!.use {it.write(data.second)}}} finally {data?.second?.fill(0);finishGuard()}}
    }
    private val attachmentViewer=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {vm.launch {AttachmentContent.clear();finishGuard()}}
    private fun itemMediaAction(item:app.kura.feature.VaultItem,column:String,source:String) {
        when(source) {
            "addAttachment"->{vm.pending.attachmentTarget=item;external(ExternalOperation.PICKER) {attachmentPicker.launch(arrayOf("*/*"))}}
            "removeAttachment"->vm.launch {vm.removeAttachment(item,column)}
            "openAttachment","exportAttachment"->vm.launch {
                val (metadata,bytes)=vm.attachment(item,column)
                if(source=="exportAttachment") {
                    vm.pending.attachmentExport=metadata to bytes
                    try {external(ExternalOperation.SAF_EXPORT) {attachmentExporter.launch(metadata.getString("name"))}}
                    catch(e:Exception) {vm.pending.attachmentExport=null;bytes.fill(0);finishGuard();throw e}
                } else {
                    val uri=AttachmentContent.publish(this@MainActivity,metadata.getString("name"),metadata.getString("mime"),bytes)
                    try {external(ExternalOperation.PICKER) {attachmentViewer.launch(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri,metadata.getString("mime")).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))}}
                    catch(e:Exception) {AttachmentContent.clear();finishGuard();throw e}
                }
            }
            else->{vm.pending.imageTarget=ImageTarget(item.table,item.id,column)
                if(source=="camera") requestCamera(if(column=="barcodeValue") "live" else "capture") else external(ExternalOperation.PICKER) {imagePicker.launch(arrayOf("image/*"))}
            }
        }
    }
    private val restorePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.launch { try { if(uri != null) { backupUri = uri; passwordAction = "restore" } } finally { finishGuard() } }
    }
    private val linkLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.launch { finishGuard() } }
    private val pkpassExporter=registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.apple.pkpass")) {uri->
        val bytes=vm.pending.pkpassBytes;vm.pending.pkpassBytes=null
        vm.launch {try {if(uri!=null && bytes!=null) vm.work {contentResolver.openOutputStream(uri,"wt")!!.use {it.write(bytes)}}}
            finally {bytes?.fill(0);finishGuard()}}
    }
    private val exporter = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        vm.launch {
            try {
                val file = exportFile ?: return@launch
                if (uri != null) vm.work { contentResolver.openOutputStream(uri, "wt")!!.use { output -> file.inputStream().use { it.copyTo(output) } } }
            } finally { exportFile?.delete(); exportFile = null; finishGuard() }
        }
    }
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        vm.launch {
            try {
                if(ok) {
                    var owned:ByteArray?=null
                    try {
                        vm.work {
                            val raw=capture!!.inputStream().use {Envelope.readBounded(it,10*1024*1024)}
                            owned=if(vm.pending.imageTarget!=null) try {CapturedImageScanner.crop(raw,1f,.5f,.5f)} finally {raw.fill(0)} else raw
                        }
                        cropBytes=owned;owned=null
                    } finally {owned?.fill(0)}
                } else vm.pending.imageTarget=null
            } finally { capture?.delete(); capture = null; finishGuard() }
        }
    }
    private var recovery by mutableStateOf<List<VaultStore.GenerationInfo>?>(null)
    private var discard by mutableStateOf<VaultStore.GenerationInfo?>(null)
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.launch {
            finishGuard()
            if(granted && vm.state.value is VaultState.Unlocked) {
                if(vm.pending.cameraMode == "capture") captureImage() else vm.pending.liveScan = true
            } else if(!granted) {vm.pending.imageTarget=null;vm.error.value = "Camera permission was not granted"}
        }
    }
    private fun requestCamera(mode: String) {
        vm.pending.cameraMode = mode
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if(mode == "capture") captureImage() else vm.pending.liveScan = true
        } else external(ExternalOperation.CAMERA) { cameraPermission.launch(android.Manifest.permission.CAMERA) }
    }
    private fun scanned(result: com.google.zxing.Result) {
        vm.pending.liveScan = false
        if(vm.pending.imageTarget?.column=="barcodeValue") {
            vm.pending.uiMemory.form.values["barcodeValue"]=result.text
            vm.pending.uiMemory.form.values["barcodeFormat"]=result.barcodeFormat.name
            vm.pending.imageTarget=null;return
        }
        if(result.text.startsWith("v1:")) {
            vm.pending.transferInput=listOf(result.text)
            vm.launch {
                try {val record=vm.decodeTransfer(listOf(result.text),charArrayOf())
                    if(vm.state.value is VaultState.Unlocked) vm.pending.transferRecord=record
                } finally {vm.pending.transferInput=null}
            }
            return
        }
        if(result.text.startsWith("v2:") || result.text.startsWith("v3:")) {
            try {
                vm.pending.transferCollector.add(result.text)
                vm.pending.transferProgress="Scanned "+vm.pending.transferCollector.count+" of "+vm.pending.transferCollector.total+" codes"
                if(vm.pending.transferCollector.complete) {
                    vm.pending.transferInput=vm.pending.transferCollector.ordered()
                    vm.pending.transferCollector.close();vm.pending.transferProgress=""
                }
            } catch(e:Exception) {vm.error.value=e.message}
            return
        }
        vm.launch {
            pendingStage = vm.work { opened ->
                val format = when(result.barcodeFormat) {
                    BarcodeFormat.QR_CODE -> "PKBarcodeFormatQR"; BarcodeFormat.AZTEC -> "PKBarcodeFormatAztec"
                    BarcodeFormat.PDF_417 -> "PKBarcodeFormatPDF417"; BarcodeFormat.CODE_128 -> "PKBarcodeFormatCode128"
                    else -> result.barcodeFormat.name.also {require(result.barcodeFormat in app.kura.feature.barcodeFormats.values) {"Unsupported barcode"}}
                }
                PassStagingArea.create(File(cacheDir,"pass-staging"), listOf(ParsedPass(JSONObject().put("type","generic")
                    .put("organizationName","Scanned pass").put("barcodeValue",result.text).put("barcodeFormat",format)
                    .put("fields","{}"), emptyMap(), false)), opened.key)
            }
        }
    }
    @Composable private fun RecoveryDialog() {
        recovery?.let { generations ->
            AlertDialog(onDismissRequest = { recovery = null }, title = { Text("Previous vaults") },
                text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    for(generation in generations) {
                        Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(generation.timestamp)) +
                            if(generation.current) " - Current" else if(!generation.recoverable) " - Incomplete import" else "")
                        Row {
                            if(!generation.current && generation.recoverable) TextButton(onClick = {
                                recovery = null
                                vm.launch {
                                    val recovered = vm.work {
                                        val directory = vm.store.validateRecovery(generation.name, it.key)
                                        directory to vm.store.missingRestoreImages(directory, it.key)
                                    }
                                    restoreDirectory = recovered.first
                                    vm.pending.restoreMissingImages = recovered.second
                                }
                            }) { Text("Recover") }
                            if(!generation.current) TextButton(onClick = { discard = generation }) { Text("Delete") }
                        }
                    }
                } }, confirmButton = { TextButton(onClick = { recovery = null }) { Text("Done") } })
        }
        discard?.let { generation ->
            AlertDialog(onDismissRequest = { discard = null }, title = { Text("Delete this saved vault?") },
                text = { Text("This permanently removes the selected previous generation. Your current vault remains available.") },
                confirmButton = { TextButton(onClick = {
                    discard = null
                    vm.launch { vm.work { vm.store.discardGeneration(generation.name) }; recovery = vm.work { vm.store.generations() } }
                }) { Text("Delete saved vault") } },
                dismissButton = { TextButton(onClick = { discard = null }) { Text("Cancel") } })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        biometricPrompt = createBiometricPrompt()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        biometricPreference = prefs.getBoolean("biometrics", true)
        autoLockPreference=prefs.getLong("autoLock",0)
        vm.lifecycle.setAutoLockMillis(autoLockPreference)
        lifecycleScope.launch {
            vm.state.collectLatest { state ->
                if(state !is VaultState.Unlocked) {
                    vm.pending.liveScan = false; recovery = null; discard = null
                    vm.pending.clearVaultContent()
                }
            }
        }
        setContent {
            val presentation by vm.presentation.collectAsState()
            val dark = presentation.theme == 1 || (presentation.theme == 2 && androidx.compose.foundation.isSystemInDarkTheme())
            androidx.compose.runtime.SideEffect {
                val taskBackground=if(dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                app.kura.feature.styleKuraWindow(window,dark,taskBackground)
                @Suppress("DEPRECATION")
                setTaskDescription(if(Build.VERSION.SDK_INT>=33) android.app.ActivityManager.TaskDescription.Builder()
                    .setLabel("Kura").setBackgroundColor(taskBackground).setPrimaryColor(taskBackground)
                    .setStatusBarColor(taskBackground).setNavigationBarColor(taskBackground).build()
                else android.app.ActivityManager.TaskDescription("Kura",null as Bitmap?,taskBackground))
            }
            MaterialTheme(colorScheme = app.kura.feature.kuraColorScheme(dark)) {
                val state by vm.state.collectAsState()
                val items by vm.items.collectAsState()
                val busy by vm.busy.collectAsState()
                val backupStatus by vm.autoBackupStatus.collectAsState()
                val backupPasswordReady by vm.backupPasswordReady.collectAsState()
                val error by vm.error.collectAsState()
                Surface(Modifier.fillMaxSize()) {
                    if(state is VaultState.Unlocked) {
                        VaultScreen(items, busy, window, { vm.launch { vm.archive(it) } },
                            { external(ExternalOperation.PICKER) { passPicker.launch(arrayOf("*/*")) } },
                            { external(ExternalOperation.SAF_IMPORT) { restorePicker.launch(arrayOf("*/*")) } },
                            { vm.pending.exportPassId = null; passwordAction = "export" }, { requestCamera("capture") },
                            { autoLockPreference=it; prefs.edit().putLong("autoLock", it).apply(); vm.lifecycle.setAutoLockMillis(it) },
                            biometricPreference, { biometricPreference = it; prefs.edit().putBoolean("biometrics", it).apply() },
                            if(BuildConfig.PROTOTYPE) ({ vm.launch { vm.fixture() } }) else null,
                            theme = presentation.theme, initialTable = presentation.initialTable, visibleTables = presentation.visibleTables,
                            onTheme = { vm.launch { vm.theme(it) } },
                            onRecovery = { vm.launch { recovery = vm.work { vm.store.generations() } } },
                            onLiveScan = { vm.pending.imageTarget=null;requestCamera("live") }, onEdit = { item, json -> val images=vm.pending.takeDraft();vm.launch { vm.edit(item, json,images) } },
                            onDelete = { item -> vm.launch { vm.deletePass(item) } },
                            onDeleteArchived={items->vm.launch {vm.deleteArchived(items)}},
                            onExportPass = { item -> vm.pending.exportPassId=item.id; passwordAction="export" },
                            onOpenLink = { target ->
                                val uri=Uri.parse(target)
                                if(uri.scheme in setOf("https","http","tel")) {
                                    try { external(ExternalOperation.PICKER) {
                                        linkLauncher.launch(android.content.Intent(if(uri.scheme=="tel") android.content.Intent.ACTION_DIAL else android.content.Intent.ACTION_VIEW,uri))
                                    } } catch(e: Exception) { vm.launch { finishGuard() }; vm.error.value="No app is available to open this link." }
                                }
                            },
                            loadImage = { vm.image(it) }, preferences=presentation.preferences, autoLock=autoLockPreference,
                            onPreference={key,value->vm.launch {vm.preference(key,value)}},
                            onSaveRecord={table,json->val images=vm.pending.takeDraft();vm.launch {vm.saveRecord(table,json,images)}},
                            draftImages=vm.pending.draftImages.mapValues {it.value.name},onDiscardDraft={vm.pending.discardDraft()},
                            onSaveOrder={section,keys->try {vm.saveOrder(section,keys);true} catch(e:kotlinx.coroutines.CancellationException) {throw e} catch(e:Exception) {vm.error.value=e.message ?: "Unable to save order";false}},onFavorite={item->vm.launch {vm.favorite(item)}},
                            onImage=::itemMediaAction,
                            onRemoveImage={item,column->if(item.id==-1L) {val file=vm.pending.draftImages.remove(column);vm.launch {withContext(Dispatchers.IO) {file?.delete()}}} else vm.launch {vm.imageChange(ImageTarget(item.table,item.id,column),null)}},
                            backupPasswordReady=backupPasswordReady,uiMemory=vm.pending.uiMemory,onPkpass={vm.pending.pkpassTarget=it},onShare={vm.pending.shareTarget=it},backupStatus=backupStatus,
                            onBackupFolder={external(ExternalOperation.SAF_EXPORT) {backupFolderPicker.launch(android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents","primary:Documents"))}},
                            onBackupPassword={vm.pending.autoPassword=true},onBackupNow={vm.launch {vm.automaticBackupNow()}},
                            onPurge={vm.pending.deleteConfirmation=true})
                    } else {
                        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),verticalArrangement=Arrangement.Center,
                            horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.kura_mark),"Kura logo",
                                Modifier.size(104.dp).clickable(enabled=state==VaultState.Locked && !busy,onClick=::unlock)
                                    .semantics {contentDescription="Unlock Kura"},colorFilter=androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onBackground))
                            Text("Kura",style=MaterialTheme.typography.displayLarge,modifier=Modifier.padding(top=20.dp))
                            Text("Privacy first pass wallet.",Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if(error != null) AlertDialog(onDismissRequest = { vm.error.value = null }, title = { Text("Could not complete operation") },
                        text = { Text(error!!) }, confirmButton = { TextButton(onClick = { vm.error.value = null }) { Text("OK") } })
                    if(state is VaultState.Unlocked) {
                        if(vm.pending.liveScan) LiveScanner({ vm.pending.liveScan = false;vm.pending.imageTarget=null },
                            { vm.pending.liveScan = false; requestCamera("capture") }, ::scanned)
                        RecoveryDialog()
                        ImportConfirmation()
                        PasswordDialog()
                        SecurityManagementDialogs()
                        CropDialog()
                        restoreDirectory?.let { directory ->
                            AlertDialog(onDismissRequest = { restoreDirectory = null }, title = { Text("Replace vault records?") },
                                text = { Text(buildString {
                                    if (vm.pending.restoreMissingImages > 0) {
                                        append("This backup is missing ${vm.pending.restoreMissingImages} image reference(s). Those images cannot be recovered from this file. Records and available images will be restored without them.\n\n")
                                    }
                                    append("The backup has been staged and validated. The previous encrypted vault remains on this device for recovery.")
                                }) },
                                confirmButton = { TextButton(onClick = {
                                    restoreDirectory = null
                                    vm.launch {vm.work {vm.store.preserveDeviceSecrets(it,directory)};vm.lock(); vm.store.activate(directory);vm.queueAutomaticBackup(true); unlock() }
                                }) { Text("Restore") } },
                                dismissButton = { TextButton(onClick = { restoreDirectory = null }) { Text("Cancel") } })
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                combine(vm.state,vm.busy) {state,busy->state to busy}.collect { (state,busy)->
                    if(state==VaultState.Locked && !busy && vm.autoUnlockArmed && vm.pending.authentication==null && vm.pending.credential==null) {
                        vm.autoUnlockArmed=false;unlock()
                    }
                }
            }
        }
    }
    override fun onStop() {
        if(!isChangingConfigurations && vm.pending.authentication==null && vm.pending.credential==null) vm.autoUnlockArmed=true
        super.onStop()
    }
    private fun unlock() = vm.launch {
        vm.autoUnlockArmed=false
        var authenticationGuard: ExternalOperationRegistry.Guard? = null
        try {
            vm.unlock authentication@ {
                withContext(Dispatchers.Main.immediate) {
                    authenticationGuard = vm.lifecycle.begin(ExternalOperation.AUTHENTICATION)
                }
                hardware.provision()
                val existing = if(envelope.baseFile.exists()) envelope.openRead().use { JSONObject(Envelope.readBounded(it, 2048).toString(Charsets.UTF_8)) } else null
                if (Build.VERSION.SDK_INT < 30 && existing != null && biometricPreference && biometricEnvelope.baseFile.exists()) {
                    val biometricMaster = try {
                        val document = biometricEnvelope.openRead().use { JSONObject(Envelope.readBounded(it, 2048).toString(Charsets.UTF_8)) }
                        val cipher = authenticate(oldBiometric.cryptoObject(false, Envelope.decode(document.getString("iv"))))
                        oldBiometric.unwrap(cipher, Envelope.decode(document.getString("wrapped")))
                    } catch (_: CredentialFallback) { null }
                    catch (_: android.security.keystore.KeyPermanentlyInvalidatedException) { null }
                    if (biometricMaster != null) return@authentication biometricMaster
                }
                val iv = existing?.let { Envelope.decode(it.getString("iv")) }
                val cipher = if(Build.VERSION.SDK_INT >= 30) {
                    val crypto = hardware.cryptoObject(existing == null, iv)
                    authenticate(crypto)
                } else {
                    check(confirmCredential()) { "Authentication cancelled" }
                    hardware.cipher(existing == null, iv)
                }
                if(existing != null) {
                    val master = hardware.unwrap(cipher, Envelope.decode(existing.getString("wrapped")))
                    try { enrollOldBiometric(master); master } catch(e: Throwable) { master.fill(0); throw e }
                }
                else {
                    // Existing native keys are unwrapped above; old installations migrate by backup import.
                    val master = ByteArray(32).also(SecureRandom()::nextBytes)
                    try {
                        val encrypted = SensitiveBytes(master.copyOf()).use { hardware.wrap(cipher, it) }
                        val document = JSONObject().put("iv", Envelope.encode(cipher.iv)).put("wrapped", Envelope.encode(encrypted))
                        VerifiedAtomicWrite.write(envelope,document.toString().toByteArray())
                        enrollOldBiometric(master)
                        master
                    } catch(e: Throwable) { master.fill(0); throw e }
                }
            }
        } finally { withContext(Dispatchers.Main.immediate) { authenticationGuard?.let { vm.lifecycle.complete(it) } } }
    }
    private fun createBiometricPrompt() = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val continuation = vm.pending.authentication ?: return
                vm.pending.authentication = null
                if (continuation.isActive) {
                    val cipher = result.cryptoObject?.cipher
                    if(cipher != null) continuation.resume(cipher)
                    else continuation.resumeWithException(IllegalStateException("No authenticated cipher"))
                }
            }
            override fun onAuthenticationError(code: Int, message: CharSequence) {
                val continuation = vm.pending.authentication ?: return
                vm.pending.authentication = null
                if(continuation.isActive) continuation.resumeWithException(
                    if(code == BiometricPrompt.ERROR_NEGATIVE_BUTTON && Build.VERSION.SDK_INT < 30) CredentialFallback()
                    else IllegalStateException(message.toString()))
            }
        })

    private suspend fun authenticate(crypto: BiometricPrompt.CryptoObject): Cipher = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            vm.pending.authentication = continuation
            val prompt = biometricPrompt
            continuation.invokeOnCancellation { runOnUiThread { prompt.cancelAuthentication() } }
            val authenticators = if(Build.VERSION.SDK_INT < 30) BiometricManager.Authenticators.BIOMETRIC_STRONG else BiometricManager.Authenticators.DEVICE_CREDENTIAL or
                if(biometricPreference) BiometricManager.Authenticators.BIOMETRIC_STRONG else 0
            val builder = BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Kura")
                .setSubtitle("Authorize your encrypted vault key").setAllowedAuthenticators(authenticators)
            if (Build.VERSION.SDK_INT < 30) builder.setNegativeButtonText("Use device PIN")
            prompt.authenticate(builder.build(), crypto)
        }
    }
    private suspend fun enrollOldBiometric(master: ByteArray) {
        if(Build.VERSION.SDK_INT >= 30 || !biometricPreference || biometricEnvelope.baseFile.exists() ||
            BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) return
        oldBiometric.provision()
        val cipher = try { authenticate(oldBiometric.cryptoObject(true)) } catch (_: CredentialFallback) { return }
        val wrapped = SensitiveBytes(master.copyOf()).use { oldBiometric.wrap(cipher, it) }
        VerifiedAtomicWrite.write(biometricEnvelope,JSONObject().put("iv", Envelope.encode(cipher.iv)).put("wrapped", Envelope.encode(wrapped)).toString().toByteArray())
    }
    private suspend fun confirmCredential(): Boolean = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            credentialResult = continuation
            continuation.invokeOnCancellation { credentialResult = null }
            credential.launch(hardware.credentialIntent())
        }
    }
    private fun external(operation: ExternalOperation, action: () -> Unit) {
        try {
            check(guard == null) { "An external operation is already active" }
            guard = vm.lifecycle.begin(operation)
            action()
        } catch(e: Exception) { guard?.close(); guard = null; vm.error.value = e.message }
    }
    private suspend fun finishGuard() { guard?.let { vm.lifecycle.complete(it) }; guard = null }
    private fun captureImage() {
        capture = File(cacheDir, "capture").apply { mkdirs() }.let { File.createTempFile("scan-", ".jpg", it) }
        val uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".files", capture!!)
        external(ExternalOperation.CAMERA) { camera.launch(uri) }
    }
    @Composable private fun ImportConfirmation() {
        val staged = pendingStage ?: return
        AlertDialog(onDismissRequest = { pendingStage?.close(); pendingStage = null },
            title = { Text("Import " + staged.titles.size + " item(s)?") },
            text = { Text(staged.titles.joinToString("\n") + "\n\nCards, Passes and Identity are selected by item type. Imported pass details are read-only." +
                "\n\nIssuer signatures are not verified. Only import passes from a source you trust.") },
            confirmButton = { TextButton(onClick = {
                pendingStage = null
                vm.launch { try { vm.work { opened -> val passes = staged.read(opened.key); try { vm.store.importPasses(opened, passes) } finally { passes.forEach { it.close() } } }; vm.refresh() } finally { staged.close() } }
            }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { pendingStage?.close(); pendingStage = null }) { Text("Cancel") } })
    }
    @Composable private fun SecurityManagementDialogs() {
        val pending=vm.pending
        pending.pkpassTarget?.let {item->AlertDialog(onDismissRequest={pending.pkpassTarget=null},title={Text("Export as .pkpass?")},
            text={Text("This file is unencrypted and unsigned. It includes the pass details and artwork. Anyone with the file can read it.")},
            confirmButton={TextButton(onClick={pending.pkpassTarget=null;vm.launch {pending.pkpassBytes=vm.pkpass(item);external(ExternalOperation.SAF_EXPORT) {pkpassExporter.launch("Kura-pass.pkpass")}}}) {Text("Export file")}},
            dismissButton={TextButton(onClick={pending.pkpassTarget=null}) {Text("Cancel")}})}
        pending.sharedChunks?.let {chunks->app.kura.feature.SecureTransferScreen(chunks) {pending.sharedChunks=null;pending.shareTarget=null}}
        if(pending.transferProgress.isNotEmpty() && !pending.liveScan) AlertDialog(
            onDismissRequest={pending.transferCollector.close();pending.transferProgress=""},
            title={Text("Receive secure transfer")},text={Text(pending.transferProgress)},
            confirmButton={TextButton(onClick={requestCamera("live")}) {Text("Scan next code")}},
            dismissButton={TextButton(onClick={pending.transferCollector.close();pending.transferProgress=""}) {Text("Cancel transfer")}})
        val purpose=when {
            pending.autoPassword->"auto"
            pending.shareTarget!=null && pending.sharedChunks==null->"share"
            pending.transferInput!=null && pending.transferInput!!.firstOrNull()?.startsWith("v1:")!=true->"receive"
            else->null
        }
        purpose?.let {kind->
            var password by remember(kind,pending.shareTarget?.id) {mutableStateOf("")}
            val busy by vm.busy.collectAsState()
            fun cancel() {password="";pending.autoPassword=false;pending.shareTarget=null;pending.transferInput=null}
            AlertDialog(onDismissRequest={if(!busy) cancel()},
                title={Text(when(kind) {"auto"->"Automatic backup password";"share"->"Set transfer password";else->"Enter transfer password"})},
                text={OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,visualTransformation=PasswordVisualTransformation(),
                    supportingText={Text(if(kind=="receive") "Enter the sender's password" else "At least 8 characters")})},
                confirmButton={TextButton(enabled=!busy && password.length>=if(kind=="receive") 1 else 8,onClick={
                    val chars=password.toCharArray();password=""
                    vm.launch {try {
                        when(kind) {
                            "auto"->{vm.setAutomaticBackupPassword(chars);pending.autoPassword=false}
                            "share"->{val chunks=vm.share(pending.shareTarget ?: error("Item is unavailable"),chars);if(vm.state.value is VaultState.Unlocked) pending.sharedChunks=chunks}
                            else->{val record=vm.decodeTransfer(pending.transferInput ?: error("Transfer is unavailable"),chars)
                                pending.transferInput=null;if(vm.state.value is VaultState.Unlocked) pending.transferRecord=record}
                        }
                    } finally {chars.fill('\u0000')}}
                }) {Text(if(busy) "Working" else "Continue")}},
                dismissButton={TextButton(enabled=!busy,onClick={cancel()}) {Text("Cancel")}})
        }
        pending.transferRecord?.let {record->
            AlertDialog(onDismissRequest={pending.transferRecord=null},title={Text("Import "+record.title+"?")},
                text={Text("This adds a new item. Existing records are kept.")},
                confirmButton={TextButton(onClick={pending.transferRecord=null;vm.launch {vm.saveRecord(record.table,record.data.toString())}}) {Text("Import item")}},
                dismissButton={TextButton(onClick={pending.transferRecord=null}) {Text("Cancel")}})
        }
        if(pending.deleteConfirmation) AlertDialog(onDismissRequest={pending.deleteConfirmation=false},
            title={Text("Delete All Data?")},
            text={Text("Delete this vault, images, saved recovery versions and encryption keys. Exported backups and any preserved pre-upgrade data remain untouched. You must authenticate again.")},
            confirmButton={TextButton(onClick={pending.deleteConfirmation=false;vm.launch {deleteNativeData()}}) {Text("Delete native data")}},
            dismissButton={TextButton(onClick={pending.deleteConfirmation=false}) {Text("Cancel")}})
    }
    private suspend fun deleteNativeData() {
        val authenticationGuard=vm.lifecycle.begin(ExternalOperation.AUTHENTICATION)
        try {
            val document=envelope.openRead().use {JSONObject(Envelope.readBounded(it,2048).toString(Charsets.UTF_8))}
            val iv=Envelope.decode(document.getString("iv"))
            val cipher=if(Build.VERSION.SDK_INT>=30) authenticate(hardware.cryptoObject(false,iv))
                else {check(confirmCredential()) {"Authentication cancelled"};hardware.cipher(false,iv)}
            hardware.unwrap(cipher,Envelope.decode(document.getString("wrapped"))).fill(0)
        } finally {vm.lifecycle.complete(authenticationGuard)}
        vm.purgeNative()
        withContext(Dispatchers.IO) {
            hardware.delete();oldBiometric.delete();envelope.delete();biometricEnvelope.delete()
            val base=cacheDir.canonicalFile
            val roots=listOf("pass-staging","backup-staging","capture","auto-backup").map {File(base,it)}
            for(root in roots) if(root.exists()) {
                require(root.canonicalFile==root.absoluteFile) {"Unexpected cache directory link"}
                val files=root.walkBottomUp().toList()
                require(files.all {it.canonicalFile==root || it.canonicalPath.startsWith(root.path+File.separator)})
                files.forEach {check(it.delete())}
            }
        }
        vm.pending.close();biometricPreference=true;autoLockPreference=0;vm.lifecycle.setAutoLockMillis(0)
    }

    @Composable private fun PasswordDialog() {
        val action = passwordAction ?: return
        val passId = vm.pending.exportPassId
        var password by remember(action,passId) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { passwordAction = null; password = "" }, title = { Text(if(action == "export") { if(passId!=null) "Encrypt pass" else "Encrypt backup" } else "Decrypt backup") },
            text = { OutlinedTextField(password, { password = it }, label = { Text("Backup password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) },
            confirmButton = { TextButton(enabled = password.isNotEmpty(), onClick = {
                val chars = password.toCharArray(); password = ""; passwordAction = null
                vm.launch {
                    try {
                        if(action == "export") {
                            exportFile = vm.work { opened ->
                                val file = File.createTempFile("backup-", ".wbk", cacheDir)
                                try {
                                    vm.store.export(opened,passId).use { payload -> file.outputStream().use { StreamingBackup.write(payload, it, chars) } }
                                    file
                                } catch(e: Throwable) { file.delete(); throw e }
                            }
                            external(ExternalOperation.SAF_EXPORT) { exporter.launch(if(passId!=null) "Kura-pass.wbk" else "Kura.wbk") }
                        } else {
                            val uri = backupUri ?: error("Select a backup")
                            var stagedPass: PassStagingArea? = null
                            var missingImages = 0
                            try {
                                val directory = vm.work { opened ->
                                    contentResolver.openInputStream(uri)!!.use { StreamingBackup.read(it, chars, File(cacheDir, "backup-staging")) }.use { payload ->
                                        if(payload.data.optString("scope")=="single-pass") {
                                            stagedPass=PassStagingArea.fromExport(File(cacheDir,"pass-staging"),payload,opened.key)
                                            null
                                        } else vm.store.stage(payload, opened.key, onMissingImage = { missingImages++ })
                                    }
                                }
                                // Publish only after session work returns successfully on the UI coroutine.
                                restoreDirectory=directory
                                vm.pending.restoreMissingImages = missingImages
                                pendingStage=stagedPass
                                stagedPass=null
                            } finally {
                                stagedPass?.let { abandoned -> withContext(NonCancellable+Dispatchers.IO) {abandoned.close()} }
                            }
                        }
                    } finally { chars.fill('\u0000'); backupUri = null; vm.pending.exportPassId=null }
                }
            }) { Text("Continue") } }, dismissButton = { TextButton(onClick = { passwordAction = null; password = "" }) { Text("Cancel") } })
    }
    @Composable private fun CropDialog() {
        val bytes = cropBytes ?: return
        val targetColumn=vm.pending.imageTarget?.column
        val cropAspect=when(targetColumn) {"kuraLogo"->1f;"frontImagePath","backImagePath"->1.586f;else->0f}
        var fraction by remember { mutableFloatStateOf(1f) }
        var horizontal by remember { mutableFloatStateOf(.5f) }
        var vertical by remember { mutableFloatStateOf(.5f) }
        val preview by produceState<Bitmap?>(null, bytes) {
            value = withContext(Dispatchers.Default) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 16_000_000) null
                else BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 4; inMutable = true })
            }
        }
        DisposableEffect(preview) { val owned = preview; onDispose { if(owned != null && !owned.isRecycled) { owned.eraseColor(android.graphics.Color.TRANSPARENT); owned.recycle() } } }

        AlertDialog(onDismissRequest = { bytes.fill(0); cropBytes = null; vm.pending.imageTarget=null }, title = { Text(if(vm.pending.imageTarget?.column=="kuraLogo") "Crop logo" else if(vm.pending.imageTarget?.column in setOf("frontImagePath","backImagePath")) "Crop card image" else "Scan captured image") },
            text = { Column {
                preview?.let { bitmap ->
                    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(200.dp).semantics {contentDescription="Drag crop area"}.pointerInput(bitmap,cropAspect) {
                        detectTransformGestures {_,pan,zoom,_->
                            val scale=minOf(size.width.toFloat()/bitmap.width,size.height.toFloat()/bitmap.height)
                            val w=bitmap.width*scale;val h=bitmap.height*scale
                            val cw=(if(cropAspect>0) minOf(w,h*cropAspect) else w)*fraction
                            val ch=(if(cropAspect>0) minOf(h,w/cropAspect) else h)*fraction
                            horizontal=(horizontal+pan.x/(w-cw).coerceAtLeast(1f)).coerceIn(0f,1f)
                            vertical=(vertical+pan.y/(h-ch).coerceAtLeast(1f)).coerceIn(0f,1f)
                            fraction=(fraction*zoom).coerceIn(.25f,1f)
                        }
                    }) {
                        val scale = minOf(size.width/bitmap.width, size.height/bitmap.height)
                        val w = bitmap.width*scale; val h = bitmap.height*scale
                        val left = (size.width-w)/2; val top = (size.height-h)/2
                        drawImage(bitmap.asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()))
                        val cw=(if(cropAspect>0) minOf(w,h*cropAspect) else w)*fraction
                        val ch=(if(cropAspect>0) minOf(h,w/cropAspect) else h)*fraction
                        val x=left+(w-cw)*horizontal;val y=top+(h-ch)*vertical
                        val mask=androidx.compose.ui.graphics.Path().apply {
                            if(targetColumn=="kuraLogo") addOval(androidx.compose.ui.geometry.Rect(x,y,x+cw,y+ch))
                            else addRoundRect(androidx.compose.ui.geometry.RoundRect(x,y,x+cw,y+ch,androidx.compose.ui.geometry.CornerRadius(if(cropAspect>0) cw*.045f else 0f)))
                        }
                        val shade=androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Difference,
                            androidx.compose.ui.graphics.Path().apply {addRect(androidx.compose.ui.geometry.Rect(left,top,left+w,top+h))},mask)
                        drawPath(shade,androidx.compose.ui.graphics.Color.Black.copy(alpha=.55f))
                        drawPath(mask,androidx.compose.ui.graphics.Color.White,style=androidx.compose.ui.graphics.drawscope.Stroke(3f))
                    }
                }
                Text("Crop: " + (fraction * 100).toInt() + "%")
                Slider(fraction, { fraction = it }, valueRange = .25f..1f, modifier = Modifier.semantics { contentDescription = "Crop size" })
                Text("Drag to position. Pinch or use the slider to resize.",style=MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = {
                val imageTarget=vm.pending.imageTarget;vm.pending.imageTarget=null
                cropBytes = null
                vm.launch {
                    try {
                        if(imageTarget?.column=="barcodeValue") {
                            val result=withContext(Dispatchers.Default) {CapturedImageScanner.scan(bytes,fraction,horizontal,vertical)}
                            vm.pending.uiMemory.form.values["barcodeValue"]=result.text;vm.pending.uiMemory.form.values["barcodeFormat"]=result.barcodeFormat.name
                            return@launch
                        }
                        if(imageTarget!=null) {
                            var cropped:ByteArray?=null
                            try {
                                withContext(Dispatchers.Default) {cropped=CapturedImageScanner.crop(bytes,fraction,horizontal,vertical,cropAspect,targetColumn=="kuraLogo")}
                                vm.imageChange(imageTarget,cropped)
                            } finally {cropped?.fill(0)}
                            return@launch
                        }
                        pendingStage = vm.work { opened ->
                            EncryptedMediaStorage(File(opened.directory, "media")).validateImage(bytes)
                            val result = CapturedImageScanner.scan(bytes, fraction, horizontal, vertical)
                            val format = when(result.barcodeFormat) {
                                BarcodeFormat.QR_CODE -> "PKBarcodeFormatQR"; BarcodeFormat.AZTEC -> "PKBarcodeFormatAztec"
                                BarcodeFormat.PDF_417 -> "PKBarcodeFormatPDF417"; BarcodeFormat.CODE_128 -> "PKBarcodeFormatCode128"
                                else -> result.barcodeFormat.name.also {require(result.barcodeFormat in app.kura.feature.barcodeFormats.values) {"Unsupported barcode"}}
                            }
                            PassStagingArea.create(File(cacheDir, "pass-staging"), listOf(ParsedPass(JSONObject().put("type", "generic").put("organizationName", "Scanned pass")
                                .put("barcodeValue", result.text).put("barcodeFormat", format).put("fields", "{}"), emptyMap(), false)), opened.key)
                        }
                    } finally { bytes.fill(0) }
                }
            }) { Text(if(vm.pending.imageTarget?.column=="barcodeValue") "Use barcode" else if(vm.pending.imageTarget!=null) "Save image" else "Scan") } }, dismissButton = { TextButton(onClick = { bytes.fill(0); cropBytes = null; vm.pending.imageTarget=null }) { Text("Cancel") } })
    }
    override fun onDestroy() {
        if (isFinishing) vm.pending.close()
        super.onDestroy()
    }
}