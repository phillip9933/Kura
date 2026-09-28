package app.kura.nativecore
import org.junit.Assert.*
import org.junit.Test

class LowMemoryBackupTest {
    @Test fun lowHeapRejectsBeforeAllocatingArgonWorkspace() {
        try { BackupCodec.requireArgonHeap(128L*1024*1024); fail("128 MiB cannot hold a 128 MiB KDF plus application state") }
        catch(_: IllegalArgumentException) {}
        BackupCodec.requireArgonHeap(256L*1024*1024)
        if(Runtime.getRuntime().maxMemory() < 160L*1024*1024) {
            val password = "Synthetic low heap".toCharArray()
            try {
                try { BackupCodec.derive(password,ByteArray(16)); fail("Low-heap execution must fail cleanly") }
                catch(error: IllegalArgumentException) { assertTrue(error.message!!.contains("enough memory")) }
            } finally { password.fill('\u0000') }
        }
    }
}
