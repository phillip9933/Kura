package app.kura.nativecore

import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.io.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import java.security.MessageDigest

class StreamingBackupTest {
    private fun scratch() = java.nio.file.Files.createTempDirectory("kura-stream-test").toFile()
    @Test fun streamingV4InteroperatesWithOriginalCodecBothDirections() {
        val root = scratch()
        val payload = BackupPayload(JSONObject("""{"version":"4.0","wallets":[]}"""), mapOf("x.png" to byteArrayOf(3, 4, 5)))
        val bytes = ByteArrayOutputStream().also { StreamingBackup.write(payload, it, "password".toCharArray()) }.toByteArray()
        BackupCodec.read(bytes.inputStream(), "password".toCharArray()).use { assertArrayEquals(byteArrayOf(3,4,5), it.images.getValue("x.png")) }
        val old = BackupCodec.write(payload, "password".toCharArray())
        StreamingBackup.read(old.inputStream(), "password".toCharArray(), root).use {
            it.consumeImage("x.png") { bytes -> assertArrayEquals(byteArrayOf(3,4,5), bytes) }
        }
        assertTrue(root.listFiles()!!.isEmpty()); root.delete()
    }
    @Test fun tamperNeverReturnsPayloadAndDeletesEncryptedScratch() {
        val root = scratch()
        val payload = BackupPayload(JSONObject("""{"version":"4.0","wallets":[]}"""), emptyMap())
        val bytes = ByteArrayOutputStream().also { StreamingBackup.write(payload, it, "password".toCharArray()) }.toByteArray()
        val index = bytes.size - 8
        bytes[index] = if(bytes[index] == 65.toByte()) 66 else 65
        try { StreamingBackup.read(bytes.inputStream(), "password".toCharArray(), root); fail("Tamper accepted") } catch(_: IOException) {}
        assertTrue(root.listFiles()!!.isEmpty()); root.delete()
    }
    @Test fun oldSha256CbcTextAndRawAreReadable() {
        val root = scratch()
        var key = "old:wallet_app_aes256_salt_v1".toByteArray()
        repeat(10000) { key = MessageDigest.getInstance("SHA-256").digest(key) }
        val iv = ByteArray(16) { 0xfe.toByte() }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
        val encrypted = cipher.doFinal("""{"version":"1.0","wallets":[]}""".toByteArray())
        val text = (Envelope.encode(iv) + ":" + Envelope.encode(encrypted)).toByteArray()
        for(bytes in listOf(text, iv + encrypted)) StreamingBackup.read(bytes.inputStream(), "old".toCharArray(), root).use {
            assertEquals("1.0", it.data.getString("version"))
        }
        key.fill(0); assertTrue(root.listFiles()!!.isEmpty()); root.delete()
    }
    @Test fun base64StreamsHandleChunkBoundariesAndRejectTrailingJunk() {
        for(size in listOf(0,1,2,3,4,8191,8192,8193)) {
            val bytes = ByteArray(size) { (it * 37).toByte() }
            val output = ByteArrayOutputStream()
            Base64Streams.encode(output).use { it.write(bytes) }
            assertEquals(Envelope.encode(bytes), output.toString("US-ASCII"))
            assertArrayEquals(bytes, Base64Streams.decode(output.toByteArray().inputStream()).readBytes())
        }
        try { Base64Streams.decode("Zg==X".byteInputStream()).readBytes(); fail() } catch(_: IllegalArgumentException) {}
    }
    @Test fun streamsFortyMiBWithoutAnArchiveByteArray() {
        val root = scratch(); val archive = File(root, "large.wbk"); val temp = File(root, "temp")
        val names = (0..4).map { "image-" + it + ".png" }.toSet()
        val payload = BackupPayload(JSONObject("""{"version":"4.0","wallets":[]}"""), emptyMap(), names, {
            ByteArray(8 * 1024 * 1024).also { java.util.Random(42).nextBytes(it) }
        })
        archive.outputStream().use { StreamingBackup.write(payload, it, "large".toCharArray()) }
        StreamingBackup.read(archive.inputStream(), "large".toCharArray(), temp).use {
            assertEquals(names, it.imageNames)
            for(name in names) it.consumeImage(name) { bytes -> assertEquals(8*1024*1024, bytes.size) }
        }
        assertTrue(temp.listFiles()!!.isEmpty()); archive.delete(); temp.delete(); root.delete()
    }

    @Test fun exactHundredMiBExpansionRoundTripAndOneByteOverIsRejected() {
        val root = scratch(); val archive = File(root, "boundary.wbk"); val temp = File(root, "temp")
        val data = JSONObject().put("version","4.0").put("wallets",org.json.JSONArray())
        val jsonSize = data.toString().toByteArray().size
        val names = (0..9).map { "image-" + it }.toSet()
        fun payload(extra: Int) = BackupPayload(data, emptyMap(), names, { name ->
            val size = 10*1024*1024 - if(name == "image-9") jsonSize - extra else 0
            ByteArray(size) { (it * 31).toByte() }
        })
        try {
            payload(0).use { value -> archive.outputStream().use { StreamingBackup.write(value,it,"boundary".toCharArray()) } }
            StreamingBackup.read(archive.inputStream(),"boundary".toCharArray(),temp).use { value ->
                var total = jsonSize.toLong()
                for(name in names) value.consumeImage(name) { bytes ->
                    total += bytes.size
                    assertEquals(31.toByte(),bytes[1])
                    assertEquals(((bytes.size-1)*31).toByte(),bytes.last())
                }
                assertEquals(StreamingBackup.MAX_EXPANSION,total)
            }
            assertTrue(temp.listFiles()!!.isEmpty())
            try {
                payload(1).use { value -> StreamingBackup.write(value,object: OutputStream() {
                    override fun write(value: Int) {}
                    override fun write(bytes: ByteArray,offset: Int,length: Int) {}
                },"boundary".toCharArray()) }
                fail("One byte over the combined budget must fail")
            } catch(_: IllegalArgumentException) {}
        } finally { archive.delete(); temp.delete(); root.delete() }
    }
}
