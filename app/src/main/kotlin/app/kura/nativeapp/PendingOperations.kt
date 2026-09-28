package app.kura.nativeapp

import android.net.Uri
import androidx.compose.runtime.*
import app.kura.nativecore.*
import java.io.File

/** Activity-result state survives configuration changes with the session owner. Never persisted as plaintext. */
data class ImageTarget(val table:String,val id:Long,val column:String)
class PendingOperations {
    val uiMemory=app.kura.feature.VaultUiMemory()
    val draftImages=mutableStateMapOf<String,File>()
    fun discardDraft() {draftImages.values.forEach {it.delete()};draftImages.clear()}
    fun takeDraft():Map<String,File> = draftImages.toMap().also {draftImages.clear()}
    var attachmentTarget: app.kura.feature.VaultItem? = null
    var attachmentExport: Pair<org.json.JSONObject,ByteArray>? = null
    var imageTarget: ImageTarget? = null
    var authentication: kotlinx.coroutines.CancellableContinuation<javax.crypto.Cipher>? = null
    var credential: kotlinx.coroutines.CancellableContinuation<Boolean>? = null
    var shareTarget by mutableStateOf<app.kura.feature.VaultItem?>(null)
    var pkpassTarget by mutableStateOf<app.kura.feature.VaultItem?>(null)
    var pkpassBytes:ByteArray?=null
    var sharedChunks by mutableStateOf<List<String>?>(null)
    var transferRecord by mutableStateOf<TransferRecord?>(null)
    var transferInput by mutableStateOf<List<String>?>(null)
    val transferCollector=TransferCollector()
    var transferProgress by mutableStateOf("")
    var autoPassword by mutableStateOf(false)
    var deleteConfirmation by mutableStateOf(false)
    var liveScan by mutableStateOf(false)
    var cameraMode = "live"
    var guard: ExternalOperationRegistry.Guard? = null
    var stage by mutableStateOf<PassStagingArea?>(null)
    var passwordAction by mutableStateOf<String?>(null)
    var backupUri: Uri? = null
    var exportPassId: Long? = null
    var exportFile: File? = null
    var restoreMissingImages by mutableIntStateOf(0)
    var restoreDirectory by mutableStateOf<File?>(null)
    var capture: File? = null
    var cropBytes by mutableStateOf<ByteArray?>(null)
    /** Clears decrypted session content without cancelling an in-flight authentication prompt. */
    fun clearVaultContent() {
        if (guard?.operation != ExternalOperation.AUTHENTICATION) { guard?.close(); guard = null }
        discardDraft();uiMemory.clear();attachmentTarget=null;attachmentExport?.second?.fill(0);attachmentExport=null;AttachmentContent.clear()
        liveScan = false; imageTarget = null
        pkpassTarget=null;pkpassBytes?.fill(0);pkpassBytes=null
        shareTarget=null;sharedChunks=null;transferRecord=null;transferInput=null;transferCollector.close();transferProgress="";autoPassword=false;deleteConfirmation=false
        stage?.close(); stage = null
        exportFile?.delete(); exportFile = null
        cropBytes?.fill(0); cropBytes = null
        capture?.delete(); capture = null
        exportPassId = null
        passwordAction = null; backupUri = null; restoreDirectory = null; restoreMissingImages = 0
    }
    fun close() {
        authentication?.cancel(); authentication = null; credential?.cancel(); credential = null
        guard?.close(); guard = null
        clearVaultContent()
    }
}
