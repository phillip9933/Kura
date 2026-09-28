package app.kura.nativecore

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.*

class PassCollectionStorageTest {
    private fun collection():ByteArray=ByteArrayOutputStream().also {outer->
        ZipOutputStream(outer).use {zip->
            for(type in listOf("storeCard","eventTicket")) {
                val inner=ByteArrayOutputStream().also {out->ZipOutputStream(out).use {pass->
                    pass.putNextEntry(ZipEntry("pass.json"))
                    pass.write(JSONObject().put("formatVersion",1).put("organizationName","Collection "+type).put(type,JSONObject())
                        .put("barcode",JSONObject().put("format","PKBarcodeFormatQR").put("message","COLLECTION-"+type)).toString().toByteArray())
                    pass.closeEntry()
                }}.toByteArray()
                zip.putNextEntry(ZipEntry(type+".pkpass"));zip.write(inner);zip.closeEntry();inner.fill(0)
            }
        }
    }.toByteArray()
    @Test fun cancelIsReadOnlyAndConfirmedCollectionKeepsBothTypes(): Unit = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val store=VaultStore(context)
        SensitiveBytes(ByteArray(32) {7}).use {key->
            val opened=store.open(key)
            val before=store.rows(opened,"passes").map {it.getLong("id")}.toSet()
            val bytes=collection()
            try {
                val root=File(context.cacheDir,"collection-test-"+java.util.UUID.randomUUID())
                PassStagingArea.create(root,PkpassParser.parse(bytes.inputStream()),key).use {
                    assertEquals(2,it.titles.size)
                    assertEquals(before,store.rows(opened,"passes").map {it.getLong("id")}.toSet())
                }
                assertTrue(root.listFiles().orEmpty().isEmpty())
                PassStagingArea.create(root,PkpassParser.parse(bytes.inputStream()),key).use {staged->
                    val passes=staged.read(key);try {store.importPasses(opened,passes)} finally {passes.forEach {it.close()}}
                }
                val added=store.rows(opened,"passes").filter {it.getLong("id") !in before}
                assertEquals(setOf("storeCard","eventTicket"),added.map {it.getString("type")}.toSet())
                assertTrue(added.all {it.getString("barcodeValue")=="COLLECTION-"+it.getString("type")})
                root.delete()
            } finally {
                store.rows(opened,"passes").filter {it.getLong("id") !in before}.forEach {store.deleteRecord(opened,"passes",it.getLong("id"))}
                bytes.fill(0);opened.close()
            }
        }
    }
    @Test fun encryptedClassificationSurvivesBackupStagingWithoutPublication(): Unit = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val store=VaultStore(context)
        SensitiveBytes(ByteArray(32) {7}).use {key->
            val current=store.active()
            val fields=JSONObject().put("primaryFields",org.json.JSONArray().put(JSONObject().put("value","Original")))
                .put("_kura",JSONObject().put("section","wallets").put("category","Gym").put("signatureVerified",false))
            val row=JSONObject().put("organizationName","Metadata fixture").put("type","generic").put("fields",fields.toString())
            val payload=BackupPayload(JSONObject().put("version","4.0").put("wallets",org.json.JSONArray()).put("identities",org.json.JSONArray())
                .put("passes",org.json.JSONArray().put(row)),emptyMap())
            try {
                val staged=store.stage(payload,key)
                val password=SensitiveBytes(key.useBytes {Envelope.encode(it).toByteArray()})
                val rooms=try {VaultDatabases.open(context,staged,password)} finally {password.close()}
                try {
                    val copied=store.rows(VaultStore.Opened(staged,rooms,key),"passes").single()
                    assertEquals("generic",copied.getString("type"))
                    val restored=JSONObject(copied.getString("fields"))
                    assertEquals("Original",restored.getJSONArray("primaryFields").getJSONObject(0).getString("value"))
                    assertEquals("wallets",restored.getJSONObject("_kura").getString("section"))
                    assertEquals("Gym",restored.getJSONObject("_kura").getString("category"))
                } finally {rooms.close()}
                assertEquals(current,store.active())
            } finally {payload.close()}
        }
    }
    @Test fun failedSecondItemRollsBackWholeCollection(): Unit = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;val store=VaultStore(context)
        SensitiveBytes(ByteArray(32) {7}).use {key->
            val opened=store.open(key)
            try {
                val before=store.rows(opened,"passes").map {it.toString()}
                val good=ParsedPass(JSONObject().put("organizationName","Never committed").put("type","storeCard"),emptyMap(),false)
                val bad=ParsedPass(JSONObject().put("organizationName","Invalid image"),mapOf("frontImagePath" to byteArrayOf(1,2,3)),false)
                try {store.importPasses(opened,listOf(good,bad));fail("Expected invalid image rejection")}
                catch(_:IllegalArgumentException) {} finally {good.close();bad.close()}
                assertEquals(before,store.rows(opened,"passes").map {it.toString()})
            } finally {opened.close()}
        }
    }
}
