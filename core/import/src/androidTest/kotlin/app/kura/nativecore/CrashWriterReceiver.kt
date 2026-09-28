package app.kura.nativecore
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import java.io.File

/** Test APK only. Terminates its separate process at a real pointer-write boundary. */
class CrashWriterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val root = File(requireNotNull(intent.getStringExtra("root"))).canonicalFile
        require(root.path.startsWith(context.cacheDir.canonicalPath + File.separator))
        val generation = requireNotNull(intent.getStringExtra("generation"))
        require(generation.matches(Regex("[0-9a-f-]{36}")))
        val pointer = AtomicFile(File(root,"native-vault/active"))
        val output = pointer.startWrite()
        output.write(generation.toByteArray()); output.fd.sync()
        if(intent.getBooleanExtra("commit",false)) pointer.finishWrite(output)
        File(root,"crash-pid").outputStream().use {
            it.write(android.os.Process.myPid().toString().toByteArray()); it.fd.sync()
        }
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
