package app.kura.nativecore

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ImportCodecTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()
    private val json = """{"formatVersion":1,"organizationName":"Rail","description":"Journey","boardingPass":{"transitType":"PKTransitTypeTrain","primaryFields":[{"label":"FROM","value":"Tokyo"}]},"barcodes":[{"format":"PKBarcodeFormatQR","message":"ORIGINAL"}],"backgroundColor":"rgb(1, 20, 255)"}"""
    private fun rejects(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }
    @Test fun parsesTransitGroupsBarcodeAndColor() {
        PkpassParser.parse(zip("pass.json" to json.toByteArray()).inputStream()).single().use {
            assertEquals("boardingPass", it.record.getString("type")); assertEquals("#0114ff", it.record.getString("backgroundColor"))
            assertEquals("ORIGINAL", it.record.getString("barcodeValue")); assertEquals("PKTransitTypeTrain", it.record.getString("transitType"))
            assertEquals(1, JSONObject(it.record.getString("fields")).getJSONArray("primaryFields").length())
            assertFalse(it.manifestChecked)
        }
    }
    @Test fun localizesLabelsWithoutChangingBarcodePayload() {
        val archive = zip("pass.json" to json.toByteArray(), "ja.lproj/pass.strings" to """"Rail" = "鉄道"; "FROM" = "出発"; "ORIGINAL" = "DO NOT APPLY";""".toByteArray())
        PkpassParser.parse(archive.inputStream(), "ja").single().use {
            assertEquals("鉄道", it.record.getString("organizationName")); assertEquals("ORIGINAL", it.record.getString("barcodeValue"))
            assertEquals("出発", JSONObject(it.record.getString("fields")).getJSONArray("primaryFields").getJSONObject(0).getString("label"))
        }
    }
    @Test fun blocksTraversalAndOversizedJson() {
        rejects { BoundedZip.read(zip("../pass.json" to json.toByteArray()).inputStream()) }
        rejects { BoundedZip.read(zip("pass.json" to ByteArray(500 * 1024 + 1)).inputStream()) }
    }
    @Test fun blocksZipBombByActualDecompressedBytes() {
        rejects { BoundedZip.read(zip("bomb" to ByteArray(10 * 1024 * 1024 + 1)).inputStream()) }
    }
    @Test fun sharesBudgetAcrossNestedPasses() {
        val inner = zip("pass.json" to json.toByteArray())
        assertEquals(2, PkpassParser.parse(zip("a.pkpass" to inner, "b.pkpass" to inner).inputStream()).size)
        var nested = inner
        repeat(5) { nested = zip("a.pkpass" to nested) }
        rejects { PkpassParser.parse(nested.inputStream()) }
    }
    @Test fun rejectsManifestTampering() {
        rejects { PkpassParser.parse(zip("pass.json" to json.toByteArray(),
            "manifest.json" to """{"pass.json":"0000000000000000000000000000000000000000"}""".toByteArray()).inputStream()) }
    }
    @Test fun rejectsAmbiguousPassAndInvalidColor() {
        rejects { PkpassParser.parse(zip("pass.json" to json.replace("\"boardingPass\":", "\"generic\":{},\"boardingPass\":").toByteArray()).inputStream()) }
        rejects { PkpassParser.color("rgb(999, 0, 0)") }
    }
    @Test fun authenticatedMediaRoundTripAndTamperRejection() {
        val key = ByteArray(32) { it.toByte() }; val bytes = "private image".toByteArray()
        val encrypted = Envelope.encrypt(bytes, key)
        assertArrayEquals(bytes, Envelope.decrypt(encrypted, key))
        val parts = encrypted.split(':'); val cipher = Envelope.decode(parts[1]); cipher[0] = (cipher[0].toInt() xor 1).toByte()
        try { Envelope.decrypt(parts[0] + ":" + Envelope.encode(cipher), key); fail() } catch (_: javax.crypto.AEADBadTagException) {}
    }
    @Test fun readsLegacyCbcMedia() {
        val key = ByteArray(32) { it.toByte() }; val iv = ByteArray(16) { 1 }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
        val encrypted = Envelope.encode(iv) + ":" + Envelope.encode(cipher.doFinal("legacy".toByteArray()))
        assertEquals("legacy", Envelope.decrypt(encrypted, key).toString(Charsets.UTF_8))
    }
    @Test fun backupArgonV4RoundTripAndWrongPassword() {
        val data = JSONObject("""{"version":"4.0","wallets":[],"passes":[{"fields":"{\"primaryFields\":[]}","isArchived":false}],"identities":[]}""")
        val encrypted = BackupCodec.write(BackupPayload(data, emptyMap()), "Kura fixture password".toCharArray())
        BackupCodec.read(encrypted.inputStream(), "Kura fixture password".toCharArray()).use {
            assertEquals("4.0", it.data.getString("version")); assertEquals(1, it.data.getJSONArray("passes").length())
        }
        try { BackupCodec.read(encrypted.inputStream(), "wrong password".toCharArray()); fail() } catch (_: javax.crypto.AEADBadTagException) {}
    }
    @Test fun matchesIndependentArgon2CffiVector() {
        val result = BackupCodec.derive("Kura fixture password".toCharArray(), ByteArray(16) { it.toByte() })
        assertEquals("07560990ff51b5d3f6da137ac43194489490efc859d01bbc33daf06b110bebb5", result.joinToString("") { "%02x".format(it) })
        result.fill(0)
    }
    @Test fun readsPbkdf2CbcBackup() {
        val salt = ByteArray(16) { it.toByte() }; val key = BackupCodec.derive("old password".toCharArray(), salt, false)
        val iv = ByteArray(16) { 2 }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
        val encrypted = Envelope.encode(salt) + ":" + Envelope.encode(iv) + ":" + Envelope.encode(cipher.doFinal("""{"version":"2.0","wallets":[]}""".toByteArray()))
        BackupCodec.read(encrypted.byteInputStream(), "old password".toCharArray()).use { assertEquals("2.0", it.data.getString("version")) }
        key.fill(0)
    }
    @Test fun rejectsOversizedEntryCounts() {
        rejects { BoundedZip.read(zip("a" to byteArrayOf(1), "b" to byteArrayOf(2)).inputStream(), ImportBudget(entries = 1)) }
    }
}