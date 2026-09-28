package app.kura.nativecore

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class LargeBackupTest {
    @Test fun hundredMiBExpandedBackupRoundTripsWithinTheAndroidHeap() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.targetContext.cacheDir,"hundred-mib-" + UUID.randomUUID()).apply { mkdirs() }
        val archive = File(root,"backup.wbk"); val scratch = File(root,"scratch")
        val data = JSONObject().put("version","4.0").put("wallets",JSONArray())
        val jsonSize = data.toString().toByteArray().size
        val names = (0..9).map { "image-" + it }.toSet()
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val peak = java.util.concurrent.atomic.AtomicLong(android.os.Debug.getPss())
        val monitor = Thread {
            while(running.get()) { peak.updateAndGet { maxOf(it,android.os.Debug.getPss()) }; Thread.sleep(50) }
        }.apply { start() }
        val start = System.nanoTime()
        try {
            BackupPayload(data,emptyMap(),names,{ name ->
                ByteArray(10*1024*1024 - if(name == "image-9") jsonSize else 0) { (it*31).toByte() }
            }).use { payload -> archive.outputStream().use { StreamingBackup.write(payload,it,"synthetic boundary".toCharArray()) } }
            StreamingBackup.read(archive.inputStream(),"synthetic boundary".toCharArray(),scratch).use { payload ->
                var total = jsonSize.toLong()
                for(name in names) payload.consumeImage(name) { bytes ->
                    total += bytes.size; assertEquals(31.toByte(),bytes[1])
                    assertEquals(((bytes.size-1)*31).toByte(),bytes.last())
                }
                assertEquals(StreamingBackup.MAX_EXPANSION,total)
            }
            assertTrue(scratch.listFiles()!!.isEmpty())
        } finally {
            running.set(false); monitor.join()
            instrumentation.sendStatus(0,android.os.Bundle().apply {
                putString("stream","\nHUNDRED_MIB_MS=" + (System.nanoTime()-start)/1_000_000 +
                    " PEAK_PSS_KIB=" + peak.get() + " MAX_HEAP_BYTES=" + Runtime.getRuntime().maxMemory() + "\n")
            })
            archive.delete(); scratch.delete(); root.delete()
        }
    }
}
