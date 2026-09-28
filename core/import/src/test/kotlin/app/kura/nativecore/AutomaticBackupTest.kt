package app.kura.nativecore
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.*
import java.nio.file.Files
class AutomaticBackupTest {
 private class Destination:BackupDestination {
  val files=linkedMapOf<String,ByteArray>();var fault="";val deleted=mutableListOf<String>()
  override fun list()=files.keys.map {BackupDocument(it,it)}
  override fun create(name:String)=BackupDocument(name,name).also {files[name]=byteArrayOf()}
  override fun write(id:String)=object:ByteArrayOutputStream() {override fun close() {if(fault=="write") error("Write failed");files[id]=toByteArray()}}
  override fun read(id:String)=if(fault=="read") byteArrayOf(1).inputStream() else files.getValue(id).inputStream()
  override fun rename(id:String,name:String):BackupDocument {if(fault=="rename") error("Rename failed");files[name]=files.remove(id)!!;return BackupDocument(name,name)}
  override fun delete(id:String) {deleted+=id;files.remove(id)}
 }
 private fun payload()=BackupPayload(JSONObject("""{"version":"4.0","wallets":[],"passes":[],"identities":[]}"""),emptyMap())
 @Test fun verifiesBeforeRetentionAndPreservesManualFiles() {
  val root=Files.createTempDirectory("backup-test").toFile();val d=Destination()
  d.files["Kura_autobackup_0001.wbk"]=byteArrayOf(1);d.files["Kura_autobackup_0002.wbk"]=byteArrayOf(2);d.files["my-backup.wbk"]=byteArrayOf(3)
  try {
   assertEquals("Kura_autobackup_0003.wbk",AutomaticBackup(root).write(payload(),"password123".toCharArray(),2,d))
   assertFalse(d.files.containsKey("Kura_autobackup_0001.wbk"));assertTrue(d.files.containsKey("my-backup.wbk"))
   BackupCodec.read(d.files.getValue("Kura_autobackup_0003.wbk").inputStream(),"password123".toCharArray()).use {assertEquals("4.0",it.data.getString("version"))}
   assertTrue(root.listFiles()!!.isEmpty())
  } finally {root.deleteRecursively()}
 }
 @Test fun failuresNeverRetireExistingBackupsAndRemovePending() {
  val root=Files.createTempDirectory("backup-fault").toFile()
  try {for(fault in listOf("write","read","rename")) {
   val d=Destination();d.files["Kura_autobackup_0001.wbk"]=byteArrayOf(9);d.fault=fault
   try {AutomaticBackup(root).write(payload(),"password123".toCharArray(),1,d);fail()} catch(e:Exception) {}
   assertEquals(setOf("Kura_autobackup_0001.wbk"),d.files.keys);assertArrayEquals(byteArrayOf(9),d.files.values.single())
   assertTrue(root.listFiles()!!.isEmpty())
  }} finally {root.deleteRecursively()}
 }
 @Test fun cancellationDoesNotPublishOrRetire() {
  val root=Files.createTempDirectory("backup-cancel").toFile();val d=Destination();d.files["Kura_autobackup_0001.wbk"]=byteArrayOf(9)
  try {
   try {AutomaticBackup(root).write(payload(),"password123".toCharArray(),1,d) {throw java.util.concurrent.CancellationException()};fail()} catch(e:java.util.concurrent.CancellationException) {}
   assertEquals(setOf("Kura_autobackup_0001.wbk"),d.files.keys);assertTrue(root.listFiles()!!.isEmpty())
  } finally {root.deleteRecursively()}
 }
}
