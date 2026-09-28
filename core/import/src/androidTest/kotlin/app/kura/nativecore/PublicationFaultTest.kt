package app.kura.nativecore

import android.content.ContextWrapper
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID

class PublicationFaultTest {
    private fun isolated() = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        private val root = File(baseContext.cacheDir, "publication-" + UUID.randomUUID()).apply { mkdirs() }
        override fun getNoBackupFilesDir(): File = root
    }
    private fun emptyPayload() = BackupPayload(JSONObject().put("version", "4.0").put("wallets", org.json.JSONArray()), emptyMap())

    @Test fun partialWriteFailureRetainsCurrentGenerationAndAllowsRetry() = runBlocking {
        val context = isolated()
        val store = VaultStore(context)
        SensitiveBytes(ByteArray(32) { 31 }).use { key ->
            val current = store.open(key)
            store.insert(current, "identities", JSONObject().put("name", "Keep private record"))
            val before = store.rows(current, "identities").map { it.toString() }
            current.close()
            val staged = emptyPayload().use { store.stage(it, key) }
            val failing = VaultStore(context) { output, bytes ->
                output.write(bytes, 0, 8)
                throw IOException("Injected ENOSPC after partial pointer write")
            }
            try { failing.activate(staged); fail("Publication must fail") } catch (_: IOException) {}
            assertEquals(current.directory, VaultStore(context).active())
            val reopened = store.open(key)
            try { assertEquals(before, store.rows(reopened, "identities").map { it.toString() }) }
            finally { reopened.close() }
            store.activate(staged)
            assertEquals(staged, store.active())
        }
    }
    @Test fun interruptedUncommittedWriteKeepsPreviousVaultAfterRecreation() = runBlocking {
        val context = isolated()
        SensitiveBytes(ByteArray(32) { 31 }).use { key ->
            val store = VaultStore(context)
            val opened = store.open(key); opened.close()
            val staged = emptyPayload().use { store.stage(it, key) }
            val pointer = AtomicFile(File(context.noBackupFilesDir, "native-vault/active"))
            // Emulate a process terminating after writing but before finishWrite/failWrite.
            pointer.startWrite().use { it.write(staged.name.toByteArray()); it.fd.sync() }
            assertEquals(opened.directory, VaultStore(context).active())
            assertTrue(staged.isDirectory)
        }
    }
    @Test fun legacyAtomicBackupIsRecoveredWhenPrimaryPointerIsMissing() = runBlocking {
        val context = isolated()
        SensitiveBytes(ByteArray(32) { 31 }).use { key ->
            val opened = VaultStore(context).open(key); opened.close()
            val pointer = File(context.noBackupFilesDir, "native-vault/active")
            assertTrue(pointer.renameTo(File(pointer.path + ".bak")))
            assertEquals(opened.directory, VaultStore(context).active())
            assertTrue(pointer.isFile)
        }
    }
    @Test fun corruptCommittedPointerFailsClosedWithoutCreatingReplacementVault() = runBlocking {
        val context = isolated()
        SensitiveBytes(ByteArray(32) { 31 }).use { key ->
            val store = VaultStore(context)
            val opened = store.open(key); opened.close()
            val root = File(context.noBackupFilesDir, "native-vault")
            val directories = root.listFiles()!!.filter { it.isDirectory }.map { it.name }.toSet()
            File(root, "active").writeText("corrupt pointer")
            try { VaultStore(context).open(key); fail("Corruption must not create an empty vault") }
            catch (_: IllegalArgumentException) {}
            assertEquals(directories, root.listFiles()!!.filter { it.isDirectory }.map { it.name }.toSet())
            assertTrue(File(opened.directory, "walletbox.db").isFile)
        }
    }

    @Test fun kernelWriteFailurePreservesPublishedVault() = runBlocking {
        val context = isolated()
        SensitiveBytes(ByteArray(32) { 31 }).use { key ->
            val store = VaultStore(context); val original = store.open(key); original.close()
            val staged = emptyPayload().use { store.stage(it,key) }
            val failing = VaultStore(context) { output, bytes ->
                output.write(bytes,0,8)
                                try {
                    val fd = android.system.Os.open("/dev/full",android.system.OsConstants.O_WRONLY,0)
                    try { android.system.Os.write(fd,bytes,0,bytes.size) }
                    finally { android.system.Os.close(fd) }
                } catch(error: android.system.ErrnoException) { throw IOException("Kernel errno=" + error.errno,error) }
            }
            try { failing.activate(staged); fail("Kernel ENOSPC must fail publication") }
            catch(error: IOException) {
                val errno = (error.cause as android.system.ErrnoException).errno
                assertTrue(error.toString(),errno in listOf(android.system.OsConstants.ENOSPC,android.system.OsConstants.EACCES))
                InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply {
                    putString("stream","\nKERNEL_WRITE_FAILURE_ERRNO=" + errno + "\n")
                })
            }
            assertEquals(original.directory,VaultStore(context).active())
        }
    }
    @Test fun syncFailureCannotPublishPointer() = runBlocking {
        val context = isolated()
        SensitiveBytes(ByteArray(32) { 31 }).use { key ->
            val store = VaultStore(context); val original = store.open(key); original.close()
            val staged = emptyPayload().use { store.stage(it,key) }
            val failing = VaultStore(context) { output, bytes -> output.write(bytes); output.close() }
            try { failing.activate(staged); fail("Invalid descriptor must fail checked fsync") }
            catch(_: IOException) {}
            assertEquals(original.directory,VaultStore(context).active())
        }
    }
    @Test fun killedWriterBeforeAndAfterCommitRecoversACompleteGeneration() = runBlocking {
        val context = isolated()
        SensitiveBytes(ByteArray(32) { 31 }).use { key ->
            val store = VaultStore(context); val original = store.open(key); original.close()
            for(commit in listOf(false,true)) {
                val staged = emptyPayload().use { store.stage(it,key) }
                val marker = File(context.noBackupFilesDir,"crash-pid")
                marker.delete()
                context.sendBroadcast(android.content.Intent(context,CrashWriterReceiver::class.java)
                    .putExtra("root",context.noBackupFilesDir.path).putExtra("generation",staged.name).putExtra("commit",commit))
                val deadline = android.os.SystemClock.elapsedRealtime()+10000
                while(!marker.isFile && android.os.SystemClock.elapsedRealtime()<deadline) kotlinx.coroutines.delay(20)
                assertTrue("Writer did not reach crash point",marker.isFile)
                val pid = marker.readText().toInt()
                var dead = false
                while(!dead && android.os.SystemClock.elapsedRealtime()<deadline) {
                    try { android.system.Os.kill(pid,0) }
                    catch(e: android.system.ErrnoException) { if(e.errno == android.system.OsConstants.ESRCH) dead = true else throw e }
                    if(!dead) kotlinx.coroutines.delay(20)
                }
                assertTrue("Separate writer process must terminate",dead)
                val reopened = VaultStore(context).open(key)
                try { assertEquals(if(commit) staged else original.directory,reopened.directory) }
                finally { reopened.close() }
            }
        }
    }

    @Test fun failedRenameIsReportedInsteadOfSilentlyClaimingRestoreSuccess() = runBlocking {
        val context = isolated()
        SensitiveBytes(ByteArray(32) { 31 }).use { key ->
            val store = VaultStore(context); val original = store.open(key); original.close()
            val staged = emptyPayload().use { store.stage(it,key) }
            val root = File(context.noBackupFilesDir,"native-vault")
            val failing = VaultStore(context) { output, bytes ->
                output.write(bytes)
                android.system.Os.chmod(root.path,320) // 0500: readable, but rename is denied.
            }
            try {
                try { failing.activate(staged); fail("A denied rename must be reported") }
                catch(_: IOException) {}
            } finally { android.system.Os.chmod(root.path,448) }
            assertEquals(original.directory,store.active())
            store.activate(staged)
            assertEquals(staged,store.active())
        }
    }
}
