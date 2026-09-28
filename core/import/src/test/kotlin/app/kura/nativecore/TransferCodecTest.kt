package app.kura.nativecore
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class TransferCodecTest {
 private val password="fixture password".toCharArray()
 private fun rejects(block:()->Unit) {try {block();fail("Expected rejection")} catch(e:Exception) {}}
 @Test fun v3RoundTripAllTypesStripsDevicePathsAndIdentity() {
  for(table in listOf("wallets","passes","identities")) {
   val row=JSONObject().put(if(table=="passes") "organizationName" else "name","Fixture 日本")
    .put("id",42).put("orderIndex",7).put("frontImagePath","/private/file.enc").put("isArchived",true)
    .put("customFields",JSONObject().put("Member","A123"))
   val payload=TransferCodec.payload(table,row)
   val chunks=TransferCodec.encode(payload,password)
   val decoded=TransferCodec.decode(chunks.reversed(),password)
   assertEquals(table,decoded.table);assertEquals("Fixture 日本",decoded.title)
   assertFalse(decoded.data.has("id"));assertFalse(decoded.data.has("frontImagePath"))
   assertFalse(decoded.data.getBoolean("isArchived"));assertEquals("A123",JSONObject(decoded.data.getString("customFields")).getString("Member"))
   rejects {TransferCodec.decode(chunks,"wrong".toCharArray())}
   payload.fill(0)
  }
 }
 @Test fun v1AndV2UseExactLegacyEnvelopeAndKdf() {
  val payload=TransferCodec.payload("wallets",JSONObject().put("name","Old card"))
  val key=ByteArray(32) {it.toByte()}
  SensitiveBytes(key.copyOf()).use {owned->assertEquals("Old card",TransferCodec.decode(listOf("v1:"+Envelope.encrypt(payload,key)),charArrayOf(),owned).title)}
  rejects {TransferCodec.decode(listOf("v1:"+Envelope.encrypt(payload,key)),password)}
  val salt=ByteArray(16) {it.toByte()}
  val derived=BackupCodec.derive(password,salt,false)
  val parts=Envelope.encrypt(payload,derived).split(':')
  assertEquals("Old card",TransferCodec.decode(listOf("v2:0:1:"+Envelope.encode(salt)+":"+parts[0]+":"+parts[1]),password).title)
  key.fill(0);derived.fill(0);payload.fill(0)
 }
 @Test fun collectorRejectsMixingConflictLimitsAndExpiry() {
  var time=0L
  val prefix="v3:0:2:"+Envelope.encode(ByteArray(16))+":"+Envelope.encode(ByteArray(12))+":"
  val c=TransferCollector {time};c.add(prefix+"AAAA");c.add(prefix+"AAAA");assertEquals(1,c.count)
  rejects {c.add(prefix+"BBBB")}
  rejects {c.add(prefix.replace(":2:",":3:")+"AAAA")}
  rejects {c.ordered()}
  c.add(prefix.replace(":0:2:",":1:2:")+"BBBB");assertTrue(c.complete)
  time=300_000_000_001L;rejects {c.ordered()};rejects {c.add(prefix+"AAAA")}
  c.close();assertEquals(0,c.count)
  rejects {c.add(prefix.replace(":2:",":101:")+"AAAA")}
  rejects {c.add(prefix+"A".repeat(1501))}
 }
 @Test fun malformedTypesAndUnauthenticatedCipherReject() {
  val bad=TransferCodec.encode("""{"type":"unknown","data":{"name":"x"}}""".toByteArray(),password)
  rejects {TransferCodec.decode(bad,password)}
  val good=TransferCodec.encode(TransferCodec.payload("wallets",JSONObject().put("name","Card")),password).toMutableList()
  good[0]=good[0].dropLast(4)+"AAAA"
  rejects {TransferCodec.decode(good,password)}
 }
}
