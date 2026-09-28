package app.kura.nativecore
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.io.File
class InteropTest {
 @Test fun decryptsProductionFlutterAndPublishesNativeFixture() {
  val fixture=javaClass.getResourceAsStream("/interop/flutter-v3.txt") ?: error("Missing committed legacy interoperability fixture")
  val chunks=fixture.bufferedReader().use {it.readLines()}
  assertEquals("Flutter fixture 日本",TransferCodec.decode(chunks,"Kura interop password".toCharArray()).title)
  val data=TransferCodec.payload("wallets",JSONObject().put("name","Native fixture 日本"))
  try {File("build/native-v3.txt").writeText(TransferCodec.encode(data,"Kura interop password".toCharArray()).joinToString("\n"))} finally {data.fill(0)}
 }
 @Test fun pkpassKeepsSectionCustomFieldsAndScannerFormatAliases() {
  val fields=JSONObject().put("_kura",JSONObject().put("section","wallets").put("category","Membership"))
   .put("customFields",JSONObject().put("Member number","123"))
  val row=JSONObject().put("type","generic").put("organizationName","Club").put("barcodeValue","ABC123").put("barcodeFormat","CODE_39").put("fields",fields.toString())
  val bytes=PkpassExport.write(row)
  try {PkpassParser.parse(bytes.inputStream()).single().use {pass->
   val decoded=JSONObject(pass.record.getString("fields"))
   assertEquals("wallets",decoded.getJSONObject("_kura").getString("section"))
   assertEquals("Membership",decoded.getJSONObject("_kura").getString("category"))
   assertFalse(decoded.getJSONObject("_kura").getBoolean("signatureVerified"))
   assertEquals("123",decoded.getJSONObject("customFields").getString("Member number"))
   assertEquals("CODE_39",pass.record.getString("barcodeFormat"))
  }} finally {bytes.fill(0)}
 }
 @Test fun pkpassExportRoundTripsAllLayoutsAndLegacyBarcode() {
  for(type in listOf("boardingPass","eventTicket","coupon","storeCard","generic")) {
   val row=JSONObject().put("organizationName","Export fixture").put("type",type).put("barcodeFormat","Telepen").put("barcodeValue","ABC123")
    .put("fields",JSONObject().put("primaryFields",org.json.JSONArray().put(JSONObject().put("key","a").put("label","Label").put("value",42))).toString())
   val bytes=PkpassExport.write(row)
   try {PkpassParser.parse(bytes.inputStream()).single().use {pass->
    assertEquals(type,pass.record.getString("type"));assertEquals("Telepen",pass.record.getString("barcodeFormat"))
    assertTrue(pass.manifestChecked);assertTrue(pass.assets.containsKey("iconImagePath"));assertEquals(42,JSONObject(pass.record.getString("fields")).getJSONArray("primaryFields").getJSONObject(0).getInt("value"))
   }} finally {bytes.fill(0)}
  }
 }
}
