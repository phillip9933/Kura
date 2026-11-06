package app.kura.nativeapp

import java.io.File

/** Deletes only this app's abandoned camera outputs; never follows links out of its cache. */
internal object CaptureFiles {
    fun clearOrphans(cache: File) {
        val scanRoot = File(cache.canonicalFile, "card-scans")
        require(scanRoot.canonicalFile == scanRoot.absoluteFile)
        // This private root is owned only by card-import jobs. Startup precedes vault work.
        fun deleteScan(file: File, depth: Int) {
            require(depth <= 12 && file.canonicalFile == file.absoluteFile &&
                (file.path == scanRoot.path || file.path.startsWith(scanRoot.path + File.separator))) { "Unexpected scanner cache link" }
            if (file.isDirectory) file.listFiles().orEmpty().forEach { deleteScan(it, depth + 1) }
            if (file.exists()) check(file.delete()) { "Could not clear an abandoned card scan" }
        }
        if (scanRoot.exists()) deleteScan(scanRoot, 0)
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
