package app.kura.nativecore

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RestoreSafetyTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun failedRestoreNeverPublishesOrChangesCurrentRecords() = runBlocking {
        val store = VaultStore(context)
        SensitiveBytes(ByteArray(32) { 7 }).use { key ->
            val opened = store.open(key)
            val before = store.active()
            try {
                store.insert(opened, "wallets", JSONObject().put("name", "Keep me " + UUID.randomUUID()).put("customFields", """[{"label":"Test","value":"Private"}]"""))
                val rows = store.rows(opened, "wallets").map { it.toString() }
                val bad = BackupPayload(JSONObject("""{"version":"4.0","wallets":[{"name":"first"},{"name":"second","frontImagePath":"missing.png"}],"passes":[],"identities":[]}"""), emptyMap())
                try { store.stage(bad, key); fail("Missing image must reject") } catch (_: IllegalArgumentException) {}
                assertEquals(before, store.active()); assertEquals(rows, store.rows(opened, "wallets").map { it.toString() })
            } finally { opened.close() }
        }
    }
    @Test fun incompleteFlutterBackupRecoversRecordsAndPresentImagesWithoutPublishing() = runBlocking {
        val store = VaultStore(context)
        SensitiveBytes(ByteArray(32) { 7 }).use { key ->
            val before = store.active()
            val bitmap = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
            val output = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            val data = JSONObject("""{"version":"4.0","wallets":[{"name":"Card","frontImagePath":"/data/user/0/app.kura.wallet/app_flutter/front.enc.png.enc"}],"passes":[{"organizationName":"Pass","logoImagePath":"/old/logo.png.enc","footerImagePath":"/old/footer.png.enc","backImagePath":""}],"identities":[{"name":"ID","frontImagePath":"/old/missing.png.enc"}],"loyalties":[{"loyaltyName":"Club","frontImagePath":"/old/club.png.enc"}]}""")
            BackupPayload(data, mapOf("front.png" to output.toByteArray())).use { payload ->
                assertThrows(IllegalArgumentException::class.java) { runBlocking { store.stage(payload, key) } }
                var omissions = 0
                val directory = store.stage(payload, key, onMissingImage = { omissions++ })
                assertEquals(4, omissions)
                assertEquals(4, store.missingRestoreImages(directory, key))
                assertEquals(before, store.active())
                val password = SensitiveBytes(key.useBytes { Envelope.encode(it).toByteArray() })
                val rooms = password.use { VaultDatabases.open(context, directory, it) }
                try {
                    val staged = VaultStore.Opened(directory, rooms, key)
                    assertEquals("front.png.enc", store.rows(staged, "wallets").single().getString("frontImagePath"))
                    val pass = store.rows(staged, "passes").first { it.optString("organizationName") == "Pass" }
                    assertTrue(pass.isNull("logoImagePath")); assertTrue(pass.isNull("footerImagePath"))
                    assertEquals("", pass.getString("backImagePath"))
                    assertEquals(2, store.rows(staged, "passes").size)
                    assertTrue(store.rows(staged, "identities").single().isNull("frontImagePath"))
                    val restored = EncryptedMediaStorage(java.io.File(directory, "media")).read("front.png.enc", key)
                    try { assertArrayEquals(payload.images.getValue("front.png"), restored) } finally { restored.fill(0) }
                    assertEquals("/old/logo.png.enc", data.getJSONArray("passes").getJSONObject(0).getString("logoImagePath"))
                } finally { rooms.close() }
            }
        }
    }

    @Test fun missingImageRecoveryDoesNotPermitMissingAttachmentsOrCorruptImages() = runBlocking {
        val store = VaultStore(context)
        SensitiveBytes(ByteArray(32) { 7 }).use { key ->
            val before = store.active()
            val row = JSONObject().put("name", "Keep attachment integrity")
            ItemMedia.put("wallets", row, JSONObject().put("attachments", org.json.JSONArray().put(
                JSONObject().put("path", "00000000-0000-0000-0000-000000000001.bin")
                    .put("name", "synthetic.txt").put("mime", "text/plain").put("size", 3))))
            BackupPayload(JSONObject().put("version", "4.0").put("wallets", org.json.JSONArray().put(row)), emptyMap()).use {
                assertThrows(IllegalArgumentException::class.java) { runBlocking { store.stage(it, key, onMissingImage = {}) } }
            }
            BackupPayload(JSONObject("""{"version":"4.0","wallets":[{"frontImagePath":"bad.png"}]}"""), mapOf("bad.png" to byteArrayOf(1, 2, 3))).use {
                assertThrows(IllegalArgumentException::class.java) { runBlocking { store.stage(it, key, onMissingImage = {}) } }
            }
            assertEquals(before, store.active())
        }
    }

    @Test fun stagedRestoreRoundTripsAllThreeTablesBeforeActivation() = runBlocking {
        val store = VaultStore(context)
        SensitiveBytes(ByteArray(32) { 7 }).use { key ->
            val before = store.active()
            val payload = BackupPayload(JSONObject("""{"version":"4.0","wallets":[{"id":51,"name":"Card","number":null,"customFields":"{\"secret\":\"✓\"}","isArchived":true}],"passes":[{"id":52,"type":"generic","barcodeValue":"ABC","fields":"{\"backFields\":[]}"}],"identities":[{"id":53,"name":"ID","customFields":"[]"}]}"""), emptyMap())
            payload.data.put("settings", JSONObject().put("themePreference", "dark")
                .put("nativeFavorites",JSONObject().put("wallets:51",true).put("passes:52",false))
                .put("nativeAddedAt",JSONObject().put("wallets:51",0L).put("passes:52",1234567890000L))
                .put("nativeAddedInitialized",true).put("favoritesFirst",true).put("passesSortMode","addedDesc")
                .put("nativeItemOrder",org.json.JSONArray(listOf("passes:52","identities:53","wallets:51"))))
            val directory = store.stage(payload, key)
            assertEquals(before, store.active())
            store.activate(directory)
            val opened = store.open(key)
            try {
                assertEquals("Card", store.rows(opened, "wallets").single().getString("name"))
                assertTrue(store.rows(opened, "wallets").single().isNull("number"))
                assertEquals("ABC", store.rows(opened, "passes").single().getString("barcodeValue"))
                assertEquals("[]", store.rows(opened, "identities").single().getString("customFields"))
                store.export(opened).use {
                    val settings=it.data.getJSONObject("settings")
                    assertEquals("dark",settings.getString("themePreference"))
                    assertTrue(settings.getJSONObject("nativeFavorites").getBoolean("wallets:51"))
                    assertFalse(settings.getJSONObject("nativeFavorites").getBoolean("passes:52"))
                    assertEquals(1234567890000L,settings.getJSONObject("nativeAddedAt").getLong("passes:52"))
                    assertEquals(0L,settings.getJSONObject("nativeAddedAt").getLong("wallets:51"))
                    assertEquals("passes:52",settings.getJSONArray("nativeItemOrder").getString(0))
                    assertEquals(51L,store.rows(opened,"wallets").single().getLong("id"))
                    assertEquals(52L,store.rows(opened,"passes").single().getLong("id"))
                    assertEquals("addedDesc",settings.getString("passesSortMode"))
                    val restoredAgain=store.stage(it,key)
                    assertTrue(java.io.File(restoredAgain,"restore.validated").isFile)
                }
            } finally { opened.close() }
        }
    }
    @Test fun malformedOrganizationMetadataCannotReplaceActiveVault() = runBlocking {
        val store=VaultStore(context)
        SensitiveBytes(ByteArray(32) {7}).use {key->
            val opened=store.open(key)
            try {
                val before=store.active();val rows=store.rows(opened,"wallets").map {it.toString()}
                val bad=listOf(
                    "nativeFavorites" to JSONObject().put("passes:52","true"),
                    "nativeFavorites" to JSONObject().put("foreign:52",true),
                    "nativeFavorites" to JSONObject().put("passes:52",JSONObject()),
                    "nativeAddedAt" to JSONObject().put("passes:52",-1),
                    "nativeAddedAt" to JSONObject().put("passes:52",1.5),
                    "nativeAddedAt" to JSONObject().put("passes:52","123"),
                    "unknownMap" to JSONObject())
                for((name,value) in bad) {
                    val payload=BackupPayload(JSONObject().put("version","4.0").put("wallets",org.json.JSONArray())
                        .put("settings",JSONObject().put(name,value)),emptyMap())
                    try {store.stage(payload,key);fail("Malformed metadata must reject: "+name)} catch(_:IllegalArgumentException) {}
                    assertEquals(before,store.active());assertEquals(rows,store.rows(opened,"wallets").map {it.toString()})
                }
            } finally {opened.close()}
        }
    }

    @Test fun argonBenchmarkAndRoundTripOnAndroid() {
        val start = System.nanoTime()
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val peak = java.util.concurrent.atomic.AtomicLong(android.os.Debug.getPss())
        val baseline = peak.get()
        val monitor = Thread {
            while (running.get()) {
                peak.updateAndGet { maxOf(it, android.os.Debug.getPss()) }
                Thread.sleep(50)
            }
        }.apply { start() }
        val data = BackupPayload(JSONObject("""{"version":"4.0","wallets":[]}"""), emptyMap())
        val bytes = BackupCodec.write(data, "instrumentation password".toCharArray())
        BackupCodec.read(bytes.inputStream(), "instrumentation password".toCharArray()).use {
            assertEquals("4.0", it.data.getString("version"))
        }
        running.set(false); monitor.join()
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("stream", "\nKURA_ARGON2_ROUNDTRIP_MS=" + (System.nanoTime() - start)/1_000_000 +
                " BASELINE_PSS_KIB=" + baseline + " PEAK_PSS_KIB=" + peak.get() + "\n")
        })
    }

    @Test fun encryptedPassStagingIsInvisibleUntilConfirmedAndCancelRemovesFiles() {
        val root = java.io.File(context.cacheDir, "staging-test-" + UUID.randomUUID())
        SensitiveBytes(ByteArray(32) { 7 }).use { key ->
            val staged = PassStagingArea.create(root, listOf(ParsedPass(JSONObject().put("organizationName", "Private staged pass").put("type", "generic"), emptyMap(), false)), key)
            val files = root.walkTopDown().filter { it.isFile }.toList()
            assertTrue(files.isNotEmpty())
            assertTrue(files.none { it.readText().contains("Private staged pass") })
            val recovered = staged.read(key)
            assertEquals("Private staged pass", recovered.single().record.getString("organizationName"))
            recovered.forEach { it.close() }; staged.close()
            assertTrue(root.listFiles()!!.isEmpty())
            root.delete()
        }
    }


    @Test fun recordEditingRejectsFractionalIdsAndRecoveryRestoresPreviousGeneration() = runBlocking {
        val store = VaultStore(context)
        SensitiveBytes(ByteArray(32) { 7 }).use { key ->
            val initial = store.open(key); val original = initial.directory.name
            try {
                store.insert(initial,"identities",JSONObject().put("name","Original").put("value","Value"))
                val row = store.rows(initial,"identities").last().put("name","Edited")
                store.update(initial,"identities",row)
                assertEquals("Edited",store.rows(initial,"identities").last().getString("name"))
                try { store.update(initial,"identities",JSONObject(row.toString()).put("id",1.5)); fail() } catch(_: IllegalArgumentException) {}
            } finally { initial.close() }
            val next = store.stage(BackupPayload(JSONObject("""{"version":"4.0","wallets":[],"passes":[],"identities":[]}"""), emptyMap()),key)
            store.activate(next)
            assertTrue(store.generations().any { it.name == original && it.recoverable })
            val recovery = store.validateRecovery(original,key); store.activate(recovery)
            val reopened = store.open(key)
            try { assertEquals("Edited",store.rows(reopened,"identities").last().getString("name")) } finally { reopened.close() }
            store.discardGeneration(next.name)
            assertFalse(next.exists())
            try { store.discardGeneration(original); fail() } catch(_: IllegalArgumentException) {}
        }
    }
    @Test fun streamingBackupExceedsOldLimitOnAndroid() {
        val root = java.io.File(context.cacheDir,"streaming-" + UUID.randomUUID()).apply { mkdirs() }
        val file = java.io.File(root,"backup.wbk")
        val names = (1..5).map { "image-" + it }.toSet()
        val start = System.nanoTime()
        val payload = BackupPayload(JSONObject("""{"version":"4.0","wallets":[]}"""),emptyMap(),names,
            { ByteArray(8*1024*1024).also { java.util.Random(123).nextBytes(it) } })
        try {
            file.outputStream().use { StreamingBackup.write(payload,it,"instrumentation".toCharArray()) }
            file.inputStream().use { StreamingBackup.read(it,"instrumentation".toCharArray(),java.io.File(root,"scratch")) }.use {
                assertEquals(names,it.imageNames)
                for(name in names) it.consumeImage(name) { bytes -> assertEquals(8*1024*1024,bytes.size) }
            }
            InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply {
                putString("stream","\nSTREAM_40_MIB_ROUNDTRIP_MS=" + (System.nanoTime()-start)/1_000_000 + " PSS_KIB=" + android.os.Debug.getPss() + "\n")
            })
        } finally { file.delete(); java.io.File(root,"scratch").delete(); root.delete() }
    }


    @Test fun selectedPassExportAndDeletionLeaveOtherRecordsUntouched() = runBlocking {
        val store=VaultStore(context)
        SensitiveBytes(ByteArray(32) {7}).use { key ->
            val opened=store.open(key)
            val name="Selected-"+UUID.randomUUID()
            try {
                val bitmap=android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.BLUE)
                val image=java.io.ByteArrayOutputStream().also {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray()
                bitmap.eraseColor(0);bitmap.recycle()
                val imageName=UUID.randomUUID().toString()+".png.enc"
                try {EncryptedMediaStorage(java.io.File(opened.directory,"media")).write(imageName,image,key)} finally {image.fill(0)}
                store.insert(opened,"passes",JSONObject().put("organizationName",name).put("barcodeValue","SELECTED").put("logoImagePath",imageName))
                store.insert(opened,"passes",JSONObject().put("organizationName",name+"-other").put("barcodeValue","OTHER"))
                val selected=store.rows(opened,"passes").single {it.optString("organizationName")==name}
                val id=selected.getLong("id")
                val wallets=store.rows(opened,"wallets").map {it.toString()}
                val identities=store.rows(opened,"identities").map {it.toString()}
                val otherPasses=store.rows(opened,"passes").filter {it.getLong("id")!=id}.map {it.toString()}
                store.export(opened,id).use { exported ->
                    assertEquals(setOf(imageName.removeSuffix(".enc")),exported.imageNames.toSet())
                    assertEquals(1,exported.data.getJSONArray("passes").length())
                    assertEquals("SELECTED",exported.data.getJSONArray("passes").getJSONObject(0).getString("barcodeValue"))
                    assertEquals(0,exported.data.getJSONArray("wallets").length())
                    assertEquals(0,exported.data.getJSONArray("identities").length())
                    assertEquals(0,exported.data.getJSONObject("settings").length())
                    BackupCodec.validate(exported.data)
                    val before=store.rows(opened,"passes").size
                    PassStagingArea.fromExport(java.io.File(context.cacheDir,"single-pass-test"),exported,key).use { staged ->
                        val decoded=staged.read(key)
                        try {
                            assertFalse(decoded.single().record.has("id"))
                            assertTrue(decoded.single().assets.getValue("logoImagePath").isNotEmpty())
                            store.importPasses(opened,decoded)
                        } finally {decoded.forEach {it.close()}}
                    }
                    assertEquals(before+1,store.rows(opened,"passes").size)
                    // Remove only the imported duplicate to keep the following isolation assertion exact.
                    val duplicate=store.rows(opened,"passes").single {it.optString("organizationName")==name && it.getLong("id")!=id}
                    store.deletePass(opened,duplicate.getLong("id"))
                    exported.data.getJSONArray("wallets").put(JSONObject().put("name","Unexpected"))
                    try {PassStagingArea.fromExport(java.io.File(context.cacheDir,"single-pass-test"),exported,key);fail("Mixed export must reject")}
                    catch(_: IllegalArgumentException) {}

                }
                store.deletePass(opened,id)
                assertEquals(otherPasses,store.rows(opened,"passes").map {it.toString()})
                assertEquals(wallets,store.rows(opened,"wallets").map {it.toString()})
                assertEquals(identities,store.rows(opened,"identities").map {it.toString()})
                try {store.export(opened,id);fail("Missing pass must not export the entire vault")} catch(_: IllegalArgumentException) {}
            } finally {opened.close()}
        }
    }
}