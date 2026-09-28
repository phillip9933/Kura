package app.kura.nativecore

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ItemMediaTest {
    @Test fun restoreRetainsOriginalSharedPreferenceStringLists() = runBlocking {
        val store=VaultStore(InstrumentationRegistry.getInstrumentation().targetContext)
        SensitiveBytes(ByteArray(32) {7}).use {key->
            val current=store.active()
            val payload=BackupPayload(JSONObject("""{"version":"4.0","wallets":[],"passes":[],"identities":[],"settings":{"paymentsCategories":["Travel","Custom"],"showPassesTab":true}}"""),emptyMap())
            val directory=store.stage(payload,key)
            val bytes=EncryptedMediaStorage(directory).read("settings.json.enc",key)
            try {
                val settings=JSONObject(bytes.toString(Charsets.UTF_8))
                assertEquals("Custom",settings.getJSONArray("paymentsCategories").getString(1))
            } finally {bytes.fill(0)}
            assertEquals(current,store.active())
            val invalid=BackupPayload(JSONObject("""{"version":"4.0","wallets":[],"settings":{"bad":[{"nested":true}]}}"""),emptyMap())
            try {store.stage(invalid,key);fail()} catch(_:IllegalArgumentException) {}
            assertEquals(current,store.active())
        }
    }
    @Test fun bothSidesStayEncryptedAndFailedReplacementPreservesReferences() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=VaultStore(context)
        SensitiveBytes(ByteArray(32) {7}).use {key->
            val opened=store.open(key)
            val bitmap=android.graphics.Bitmap.createBitmap(8,5,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}
            val out=java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out)
            val bytes=out.toByteArray();bitmap.eraseColor(0);bitmap.recycle()
            try {
                for(table in listOf("wallets","passes","identities")) {
                    val name="Image test "+java.util.UUID.randomUUID()
                    val nameKey=if(table=="passes") "organizationName" else "name"
                    store.insert(opened,table,JSONObject().put(nameKey,name))
                    val id=store.rows(opened,table).single {it.optString(nameKey)==name}.getLong("id")
                    try {
                        store.setItemImage(opened,table,id,"frontImagePath",bytes)
                        store.setItemImage(opened,table,id,"backImagePath",bytes)
                        val row=store.rows(opened,table,id).single()
                        val front=row.getString("frontImagePath");val back=row.getString("backImagePath")
                        assertNotEquals(front,back);assertTrue(front.endsWith(".enc"))
                        val media=EncryptedMediaStorage(java.io.File(opened.directory,"media"))
                        val clear=media.read(front,key)
                        try {assertArrayEquals(bytes,clear)} finally {clear.fill(0)}
                        assertFalse(java.io.File(opened.directory,"media/"+front).readBytes().contentEquals(bytes))
                        try {store.setItemImage(opened,table,id,"frontImagePath",byteArrayOf(1,2,3));fail()} catch(_:IllegalArgumentException) {}
                        assertEquals(front,store.rows(opened,table,id).single().getString("frontImagePath"))
                        store.setItemImage(opened,table,id,"frontImagePath",null)
                        val detached=store.rows(opened,table,id).single()
                        assertTrue(detached.isNull("frontImagePath"));assertEquals(back,detached.getString("backImagePath"))
                        assertTrue(java.io.File(opened.directory,"media/"+front).exists())
                    } finally {store.deleteRecord(opened,table,id)}
                }
            } finally {bytes.fill(0);opened.close()}
        }
    }
}
