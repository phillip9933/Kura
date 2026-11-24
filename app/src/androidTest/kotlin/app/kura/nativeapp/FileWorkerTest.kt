package app.kura.nativeapp

import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.kura.nativecore.VaultResources
import app.kura.nativecore.VaultSessionCoordinator
import app.kura.nativecore.VaultState
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FileWorkerTest {
    @Test fun lockCanFinishWhileADocumentStreamIsWaiting() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val capture = File(context.cacheDir, "capture").apply { mkdirs() }
        val file = File.createTempFile("worker-test-", ".bin", capture)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val session = VaultSessionCoordinator()
        val key = ByteArray(32) { 42 }
        val generation = session.unlock({ key }) { VaultResources {} }
        val pipe = ParcelFileDescriptor.createPipe()
        try {
            file.writeText("waiting")
            val pending = async {
                session.run(generation) {
                    FileWorker.files(context) { directory ->
                        val args = documentJob("write", uri).apply { putParcelable("stream", pipe[0]) }
                        args.withDocumentKey { FileWorker.run(context, directory, args) }
                    }
                }
            }
            // Truncation confirms the service opened its destination and is waiting on the pipe.
            withTimeout(15_000) { while (file.length() != 0L) delay(20) }
            withTimeout(3_000) { session.lock() }
            pending.join()
            assertTrue(pending.isCancelled)
            assertEquals(VaultState.Locked, session.state.value)
            assertTrue(key.all { it == 0.toByte() })
            assertTrue(File(context.cacheDir, "card-scans").listFiles().orEmpty().isEmpty())
            file.writeText("ready")
            val result = context.readDocument(uri, 32) { stream, _ -> stream.bufferedReader().readText() }
            assertEquals("ready", result)
        } finally {
            pipe.forEach { it.close() }
            session.lock()
            file.delete()
        }
    }
}
