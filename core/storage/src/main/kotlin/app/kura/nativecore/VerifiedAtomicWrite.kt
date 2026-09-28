package app.kura.nativecore

import android.util.AtomicFile
import java.io.FileOutputStream
import java.io.IOException

/** AtomicFile logs some failures instead of throwing; verify the committed bytes. */
object VerifiedAtomicWrite {
    fun write(file: AtomicFile, bytes: ByteArray, writer: (FileOutputStream,ByteArray) -> Unit = { output,value -> output.write(value) }) {
        val output = file.startWrite()
        try {
            writer(output,bytes)
            output.fd.sync()
            file.finishWrite(output)
            val buffer = ByteArray(8192)
            try {
                file.baseFile.inputStream().use { input ->
                    var offset = 0
                    while(offset < bytes.size) {
                        val count = input.read(buffer,0,minOf(buffer.size,bytes.size-offset))
                        if(count <= 0) throw IOException("Atomic file commit was incomplete")
                        for(index in 0 until count) if(buffer[index] != bytes[offset+index]) throw IOException("Atomic file commit did not persist")
                        offset += count
                    }
                    if(input.read() != -1) throw IOException("Atomic file commit length differs")
                }
            } finally { buffer.fill(0) }
        } catch(error: Throwable) { file.failWrite(output); throw error }
    }
}
