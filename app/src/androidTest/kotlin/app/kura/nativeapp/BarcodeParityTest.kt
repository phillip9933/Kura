package app.kura.nativeapp
import app.kura.feature.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class BarcodeParityTest {
 @Test fun everyOriginalWriterRendersOnAndroid() {
  val values=mapOf("EAN-13" to "5901234123457","EAN-8" to "96385074","UPC-A" to "042100005264","UPC-E" to "04252614",
   "ITF" to "123456","ITF-14" to "1234567890123","GS1-128" to "(01)12345678901231","ISBN" to "9780306406157",
   "POSTNET" to "12345","RM4SCC" to "SW1A1AA","EAN-2" to "12","EAN-5" to "51234","Codabar" to "A1234B")
  assertEquals(21,allBarcodeFormats.size)
  for(format in allBarcodeFormats) {
   val bitmap=renderBarcode(values[format] ?: "ABC123",format)
   assertNotNull("Writer failed: "+format,bitmap)
   try {
    assertTrue(bitmap!!.width>0 && bitmap.height>0)
    val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
    try {
     assertTrue(format,pixels.any {it==android.graphics.Color.BLACK});assertTrue(format,pixels.any {it==android.graphics.Color.WHITE})
     if(format in barcodeFormats.keys) {
      val luma=ByteArray(pixels.size) {if(pixels[it]==android.graphics.Color.BLACK) 0 else 255.toByte()}
      try {assertNotNull("Generated code cannot be decoded: "+format,NativeBarcodeDecoder.decode(luma,bitmap.width,bitmap.height))}
      finally {luma.fill(0)}
     }
    } finally {pixels.fill(0)}
   }
   finally {bitmap?.eraseColor(0);bitmap?.recycle()}
  }
  assertNull(renderBarcode("INVALID","POSTNET"));assertNull(renderBarcode("123","EAN-2"))
  assertNull(renderBarcode("123","not-a-format"))
 }
 @Test fun importedArraysKeepDuplicateLabelsAndUnknownMetadataDuringEditing() {
  val raw="""[{"label":"Member","value":"one","issuerMetadata":{"original":true}},{"label":"Member","value":"two"}]"""
  val row=JSONObject().put("id",99).put("name","Array fixture").put("customFields",raw)
  val item=VaultItem("identities",99,"Array fixture","","",false,row.toString())
  val memory=VaultUiMemory()
  memory.form.initialize("identities",item,"identities",VaultPreferences())
  memory.form.customArray!!.getJSONObject(0).put("value","edited")
  memory.form.initialize("identities",item,"identities",VaultPreferences())
  val result=memory.form.customArray!!
  assertEquals("edited",result.getJSONObject(0).getString("value"))
  assertTrue(result.getJSONObject(0).getJSONObject("issuerMetadata").getBoolean("original"))
  assertEquals(listOf("Member" to "edited","Member" to "two"),customFieldEntries(result.toString()))
  memory.clear();assertNull(memory.form.customArray)
 }
 @Test fun arrayCustomFieldsConvertWithoutDroppingAmbiguousData() {
  assertEquals("123",customFieldMap("""[{"label":"Member","value":"123"}]""").getString("Member"))
  try {customFieldMap("""[{"label":"A","value":"1"},{"label":"A","value":"2"}]""");fail()} catch(_:IllegalArgumentException) {}
  try {customFieldMap("""[{"label":"A","value":"1","unknownMetadata":true}]""");fail()} catch(_:IllegalArgumentException) {}
 }
}
