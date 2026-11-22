package app.kura.nativeapp

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.*
import app.kura.nativecore.StreamingCrypto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

/** The caller owns the files. Only a job identifier and bounded arguments cross Binder. */
internal object FileWorker {
    private val lane = Mutex()

    suspend fun <T> files(context: Context, block: suspend (File) -> T): T = withContext(Dispatchers.IO) {
        val root = File(context.cacheDir.canonicalFile, "card-scans")
        check(root.isDirectory || root.mkdirs() || root.isDirectory)
        val directory = File(root, UUID.randomUUID().toString())
        check(directory.mkdir())
        try { block(directory) } finally { directory.deleteRecursively() }
    }

    suspend fun run(context: Context, directory: File, args: Bundle, deadlineMillis: Long = 120_000): String =
        lane.withLock {
            val result = CompletableDeferred<String>()
            val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
                val value = message.data.getString("result")
                if (value != null) result.complete(value)
                else result.completeExceptionally(IllegalStateException(message.data.getString("error") ?: "File operation interrupted"))
                true
            })
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    if (!result.isActive) return
                    try {
                        Messenger(binder).send(Message.obtain().apply { data = args; replyTo = reply })
                    } catch (failure: RemoteException) { result.completeExceptionally(failure) }
                }
                override fun onServiceDisconnected(name: ComponentName) { result.completeExceptionally(IllegalStateException("File worker stopped")) }
                override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
                override fun onNullBinding(name: ComponentName) = onServiceDisconnected(name)
            }
            val intent = Intent(context, KuraFileService::class.java).setData(Uri.fromParts("kura-job", directory.name, null))
            check(context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) { "Cannot start file worker" }
            try {
                withTimeoutOrNull(deadlineMillis) { result.await() } ?: error("File operation timed out")
            } finally {
                result.cancel()
                context.unbindService(connection)
            }
        }
}

/** Never open vault handles in this process. Its main thread remains available to cancel native or provider I/O. */
class KuraFileService : Service() {
    private val bound = mutableSetOf<String>()
    @Volatile private var active: String? = null

    override fun onBind(intent: Intent): IBinder {
        val id = UUID.fromString(requireNotNull(intent.data?.schemeSpecificPart)).toString()
        bound.add(id)
        return Messenger(Handler(Looper.getMainLooper()) { request ->
            if (id !in bound || active != null) {
                try { request.replyTo.send(Message.obtain()) } catch (_: RemoteException) {}
                return@Handler true
            }
            active = id
            val reply = request.replyTo
            val args = request.data
            Thread {
                val response = Bundle()
                try {
                    val directory = File(File(cacheDir.canonicalFile, "card-scans"), id)
                    check(directory.isDirectory && directory.canonicalFile == directory.absoluteFile)
                    response.putString("result", execute(directory, args))
                } catch (failure: Exception) {
                    // Only send the failure category. Provider messages can contain document names.
                    response.putString("error", when (failure) {
                        is SecurityException -> "File access was denied"
                        is java.io.IOException -> "Could not read or write the selected file"
                        is IllegalArgumentException -> "File input was rejected"
                        else -> "File operation failed"
                    })
                }
                args.getByteArray("key")?.fill(0)
                active = null
                try { reply.send(Message.obtain().apply { data = response }) } catch (_: RemoteException) {}
            }.start()
            true
        }).binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        val id = intent.data?.schemeSpecificPart
        bound.remove(id)
        if (id != null && active == id) Process.killProcess(Process.myPid())
        return false
    }

    private fun execute(directory: File, args: Bundle): String {
        val input = File(directory, "input")
        val operation = args.getString("operation")
        if (operation == "ocr") return recognizeWorkerFile(this, directory, input)
        val uri = Uri.parse(requireNotNull(args.getString("uri")))
        require(uri.scheme == "content")
        when (operation) {
            "folder" -> {
                val document = android.provider.DocumentsContract.buildDocumentUriUsingTree(uri,
                    android.provider.DocumentsContract.getTreeDocumentId(uri))
                return try { displayName(document).orEmpty() } catch (_: android.database.SQLException) { "" }
                    catch (_: UnsupportedOperationException) { "" }
            }
            "read", "attachment" -> {
                val limit = args.getInt("limit")
                require(limit in 1..150 * 1024 * 1024)
                val output = ParcelFileDescriptor.AutoCloseOutputStream(descriptor(args))
                args.encryptedOutput(output).use { encrypted ->
                    requireNotNull(contentResolver.openInputStream(uri)).use { source ->
                        StreamingCrypto.copy(source, encrypted, limit.toLong())
                    }
                }
            }
            "write" -> {
                val inputStream = ParcelFileDescriptor.AutoCloseInputStream(descriptor(args))
                args.decryptedInput(inputStream).use { source ->
                    requireNotNull(contentResolver.openOutputStream(uri, "wt")).use { source.copyTo(it) }
                }
            }
            "automatic" -> return app.kura.nativecore.AutomaticBackup.publish(
                input, args.getInt("retention"), SafBackupDestination(this, uri))
            else -> error("Unknown file operation")
        }
        if (operation == "attachment") return org.json.JSONArray()
            .put(displayName(uri) ?: "Attachment")
            .put(contentResolver.getType(uri)?.take(255) ?: "application/octet-stream").toString()
        return ""
    }

    private fun displayName(uri: Uri): String? = contentResolver.query(uri,
        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0)?.take(255) else null
    }

    @Suppress("DEPRECATION")
    private fun descriptor(args: Bundle): ParcelFileDescriptor = checkNotNull(args.getParcelable("stream"))
}

internal fun documentJob(operation: String, uri: Uri) = Bundle().apply {
    putString("operation", operation)
    putString("uri", uri.toString())
}

internal suspend fun <T> Bundle.withDocumentKey(block: suspend () -> T): T {
    val random = java.security.SecureRandom()
    val key = ByteArray(32).also(random::nextBytes)
    putByteArray("key", key)
    putByteArray("iv", ByteArray(12).also(random::nextBytes))
    return try { block() } finally { key.fill(0) }
}

internal fun Bundle.encryptedOutput(stream: java.io.OutputStream) =
    StreamingCrypto.encrypt(stream, requireNotNull(getByteArray("key")), requireNotNull(getByteArray("iv")))

private fun Bundle.decryptedInput(stream: java.io.InputStream) =
    StreamingCrypto.decrypt(stream, requireNotNull(getByteArray("key")), requireNotNull(getByteArray("iv")))

/** The pathname is removed before the first byte is written, including on process death. */
internal suspend fun <T> withDocumentDescriptor(directory: File, block: suspend (ParcelFileDescriptor) -> T): T {
    val temp = File.createTempFile("document-", null, directory)
    try {
        ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_WRITE).use { descriptor ->
            check(temp.delete())
            return block(descriptor)
        }
    } finally { temp.delete() }
}

internal suspend fun <T> Context.readDocument(uri: Uri, limit: Int, attachment: Boolean = false,
    block: (java.io.InputStream, String) -> T): T = FileWorker.files(this) { directory ->
    withDocumentDescriptor(directory) { fd ->
        val request = documentJob(if (attachment) "attachment" else "read", uri).apply {
            putInt("limit", limit)
            putParcelable("stream", fd)
        }
        request.withDocumentKey {
            val metadata = FileWorker.run(this, directory, request)
            android.system.Os.lseek(fd.fileDescriptor, 0, android.system.OsConstants.SEEK_SET)
            request.decryptedInput(ParcelFileDescriptor.AutoCloseInputStream(fd.dup())).use { block(it, metadata) }
        }
    }
}
