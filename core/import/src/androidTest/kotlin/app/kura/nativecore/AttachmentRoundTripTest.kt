package app.kura.nativecore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class AttachmentRoundTripTest {
 @Test fun encryptedFilesAndLogosSurviveAllSchemasAndBackupStaging()=runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val store=VaultStore(context)
  SensitiveBytes(ByteArray(32) {7}).use {key->
   val opened=store.open(key);val ids=mutableListOf<Pair<String,Long>>()
   val bytes="Synthetic attachment contents".toByteArray()
   val bitmap=android.graphics.Bitmap.createBitmap(16,16,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}
   val image=java.io.ByteArrayOutputStream().also {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
   try {
    for(table in listOf("wallets","passes","identities")) {
     val media=EncryptedMediaStorage(File(opened.directory,"media"))
     val file=media.write(UUID.randomUUID().toString()+".bin.enc",bytes,key)
     val logo=media.write(UUID.randomUUID().toString()+".png.enc",image,key)
     val name="Attachment fixture "+UUID.randomUUID()
     val nameKey=if(table=="passes") "organizationName" else "name"
     val row=JSONObject().put(nameKey,name)
     if(table=="wallets") row.put("customFields","""[{"name":"Original","value":"Retained"}]""")
     ItemMedia.put(table,row,JSONObject().put("logo",logo.name).put("attachments",JSONArray().put(JSONObject().put("path",file.name).put("name","Notes.txt").put("mime","text/plain").put("size",bytes.size))))
     store.insert(opened,table,row)
     val persisted=store.rows(opened,table).single {it.optString(nameKey)==name};ids.add(table to persisted.getLong("id"))
     assertFalse(file.readBytes().contentEquals(bytes));assertEquals(logo.name,ItemMedia.logo(table,persisted))
     val transfer=TransferCodec.payload(table,persisted)
     try {assertEquals(0,ItemMedia.get(table,JSONObject(transfer.toString(Charsets.UTF_8)).getJSONObject("data")).length())} finally {transfer.fill(0)}
    }
    val before=store.active()
    store.export(opened).use {payload->
     val dir=store.stage(payload,key)
     val password=SensitiveBytes(key.useBytes {Envelope.encode(it).toByteArray()})
     val rooms=password.use {VaultDatabases.open(context,dir,it)}
     try {val restored=VaultStore.Opened(dir,rooms,key)
      ids.forEach {(table,id)->
       val row=store.rows(restored,table,id).single()
       val file=ItemMedia.attachments(table,row).single()
       val decoded=EncryptedMediaStorage(File(dir,"media")).read(file.getString("path"),key)
       try {assertArrayEquals(bytes,decoded)} finally {decoded.fill(0)}
       assertTrue(ItemMedia.logo(table,row).endsWith(".enc"))
       if(table=="wallets") assertEquals("Retained",JSONArray(row.getString("customFields")).getJSONObject(0).getString("value"))
      }
     } finally {rooms.close()}
    }
    assertEquals(before,store.active())
   } finally {ids.forEach {(table,id)->store.deleteRecord(opened,table,id)};bytes.fill(0);image.fill(0);opened.close()}
  }
 }
 @Test fun rejectsUnsafeAndMissingAttachmentReferencesWithoutActivation()=runBlocking {
  val store=VaultStore(InstrumentationRegistry.getInstrumentation().targetContext);val before=store.active()
  SensitiveBytes(ByteArray(32) {7}).use {key->
   for(path in listOf("../private.txt",UUID.randomUUID().toString()+".bin")) {
    val row=JSONObject().put("organizationName","Invalid")
    ItemMedia.put("passes",row,JSONObject().put("attachments",JSONArray().put(JSONObject().put("path",path).put("name","File").put("mime","text/plain").put("size",1))))
    val payload=BackupPayload(JSONObject().put("version","4.0").put("passes",JSONArray().put(row)),emptyMap())
    assertTrue(runCatching {store.stage(payload,key)}.isFailure);assertEquals(before,store.active())
   }
  }
 }
}
