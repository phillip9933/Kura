package app.kura.nativecore

import java.io.*
import java.util.zip.ZipInputStream

class ImportBudget(var bytes: Int = 10 * 1024 * 1024, var entries: Int = 128)
object BoundedZip {
    fun read(input: InputStream, budget: ImportBudget = ImportBudget(), compressedLimit: Int = 12 * 1024 * 1024): Map<String, ByteArray> {
        var consumed = 0
        val limited = object : InputStream() {
            override fun read(): Int {
                val value = input.read()
                if (value >= 0) { consumed++; require(consumed <= compressedLimit) }
                return value
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val count = input.read(b, off, len)
                if (count > 0) { consumed += count; require(consumed <= compressedLimit) }
                return count
            }
            override fun close() = input.close()
        }
        val files = linkedMapOf<String, ByteArray>()
        val seen = hashSetOf<String>()
        try {
            ZipInputStream(limited).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(--budget.entries >= 0) { "Too many ZIP entries" }
                    val name = entry.name.removeSuffix("/")
                    require(name.length in 1..512 && !name.startsWith("/") && '\\' !in name && ':' !in name &&
                        name.split('/').none { it.isEmpty() || it == "." || it == ".." } && seen.add(name)) { "Unsafe or duplicate ZIP path" }
                    val cap = if (name.substringAfterLast('/') in listOf("pass.json", "pass.strings")) minOf(budget.bytes, 500 * 1024) else budget.bytes
                    require(entry.size < 0 || entry.size <= cap) { "ZIP entry is too large" }
                    val data = Envelope.readBounded(zip, cap)
                    budget.bytes -= data.size
                    if (!entry.isDirectory) files[name] = data else require(data.isEmpty())
                    zip.closeEntry()
                }
            }
            require(files.isNotEmpty()) { "Empty or invalid archive" }
            return files
        } catch (e: Throwable) { files.values.forEach { it.fill(0) }; throw e }
    }
}