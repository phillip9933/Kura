package app.kura.nativecore

import android.os.Bundle
import android.system.Os
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID

class AtomicCommitSafetyTest {
    @Test fun deniedCommitCannotReportSuccessBeforeRollingBackOnRead() {
        deniedCommit(byteArrayOf(5, 6, 7, 8))
    }

    @Test fun deniedCommitIsReportedEvenWhenReplacementBytesAreUnchanged() {
        deniedCommit(byteArrayOf(1, 2, 3, 4))
    }

    private fun deniedCommit(replacement: ByteArray) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.cacheDir, "atomic-" + UUID.randomUUID())
        assertTrue(directory.mkdir())
        val base = File(directory, "value")
        val original = byteArrayOf(1, 2, 3, 4)
        val file = AtomicFile(base)
        try {
            VerifiedAtomicWrite.write(file, original)
            var reportedSuccess = false
            var backupRemained = false
            var newRemained = false
            var baseMatchedReplacement = false
            try {
                try {
                    VerifiedAtomicWrite.write(file, replacement) { output, bytes ->
                        output.write(bytes)
                        Os.chmod(directory.path, 320) // 0500: finish cannot delete/rename entries.
                    }
                    reportedSuccess = true
                } catch (_: IOException) {
                    // The caller must learn that the replacement was not committed.
                }
                backupRemained = File(base.path + ".bak").exists()
                newRemained = File(base.path + ".new").exists()
                baseMatchedReplacement = base.readBytes().contentEquals(replacement)
            } finally {
                Os.chmod(directory.path, 448) // 0700: permit authoritative recovery and retry.
            }
            val recoveredOriginal = AtomicFile(base).openRead().use { it.readBytes().contentEquals(original) }
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stream", "\nATOMIC_COMMIT_OBSERVATION success=$reportedSuccess" +
                    " backup=$backupRemained new=$newRemained baseMatches=$baseMatchedReplacement" +
                    " recoveredOriginal=$recoveredOriginal\n")
            })
            assertTrue("The denied commit must leave a recoverable transaction", backupRemained || newRemained)
            assertTrue("Reopening must retain the previously committed value", recoveredOriginal)
            assertFalse("A write that rolls back on read must not report success", reportedSuccess)
            VerifiedAtomicWrite.write(file, replacement)
            assertArrayEquals(replacement, AtomicFile(base).openRead().use { it.readBytes() })
            assertFalse(File(base.path + ".bak").exists())
            assertFalse(File(base.path + ".new").exists())
        } finally {
            Os.chmod(directory.path, 448)
            directory.deleteRecursively()
            original.fill(0)
            replacement.fill(0)
        }
    }
}
