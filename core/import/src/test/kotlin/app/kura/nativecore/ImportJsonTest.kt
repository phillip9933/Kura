package app.kura.nativecore

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportJsonTest {
    @Test fun countsContainersAndIgnoresEscapedStringContent() {
        val quoted = org.json.JSONObject.quote("brackets []{} and a quoted \\".repeat(100))
        assertEquals(1, ImportJson.arrayFrom("[".repeat(64) + quoted + "]".repeat(64)).length())
        assertThrows(IllegalArgumentException::class.java) { ImportJson.arrayFrom("[".repeat(65) + "]".repeat(65)) }
    }

    @Test fun passImportRejectsOverLimitBeforeMaterializingDocument() {
        val document = """{"formatVersion":1,"generic":{},"userInfo":""" + "[".repeat(64) + "]".repeat(64) + "}"
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use {
            it.putNextEntry(ZipEntry("pass.json"))
            it.write(document.toByteArray())
            it.closeEntry()
        }
        val error = assertThrows(IllegalArgumentException::class.java) { PkpassParser.parse(buffer.toByteArray().inputStream()) }
        assertTrue(error.message.orEmpty().contains("nesting"))
    }
}
