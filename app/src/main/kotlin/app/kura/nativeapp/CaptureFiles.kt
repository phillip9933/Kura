package app.kura.nativeapp

import java.io.File

/** Deletes only this app's abandoned camera outputs; never follows links out of its cache. */
internal object CaptureFiles {
    fun clearOrphans(cache: File) {
        val directory = File(cache.canonicalFile, "capture")
        require(directory.canonicalFile == directory.absoluteFile) { "Unexpected capture directory link" }
        directory.listFiles().orEmpty().forEach { file ->
            require(file.canonicalFile.parentFile == directory && file.canonicalFile == file.absoluteFile) { "Unexpected capture file link" }
            if (file.isFile && file.name.startsWith("scan-") && file.name.endsWith(".jpg")) {
                check(file.delete()) { "Could not clear an abandoned camera image" }
            }
        }
    }
}
