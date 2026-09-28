package app.kura.nativecore
import org.json.JSONObject
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.security.MessageDigest

/** Unsigned, unencrypted interchange, matching legacy Kura export. Never claims issuer authenticity. */
object PkpassExport {
 fun write(row:JSONObject,assets:Map<String,ByteArray> = emptyMap()):ByteArray {
  val type=row.optString("type").takeIf {it in setOf("boardingPass","eventTicket","storeCard","coupon","generic")} ?: "generic"
  val json=JSONObject().put("formatVersion",1).put("passTypeIdentifier","pass.app.kura.wallet").put("teamIdentifier","KURA")
   .put("serialNumber",java.util.UUID.randomUUID().toString()).put("organizationName",row.optString("organizationName"))
   .put("description",row.optString("description").ifBlank {row.optString("organizationName")})
  for(key in listOf("logoText","backgroundColor","foregroundColor","labelColor","relevantDate")) {
   val value=row.optString(key);if(value.isNotBlank() && value!="null") json.put(key,value)
  }
  row.optString("expiry_date").takeIf {it.isNotBlank() && it!="null"}?.let {json.put("expirationDate",it)}
  val fields=runCatching {JSONObject(row.optString("fields","{}"))}.getOrDefault(JSONObject())
  val custom=JSONObject()
  fields.optJSONObject("_kura")?.let {meta->for(key in listOf("section","category")) meta.optString(key).takeIf {it.isNotBlank()}?.let {custom.put(key,it)}}
  fields.optJSONObject("customFields")?.let {custom.put("customFields",it)}
  if(custom.length()>0) json.put("userInfo",JSONObject().put("app.kura.wallet",custom))
  val body=JSONObject()
  for(group in listOf("headerFields","primaryFields","secondaryFields","auxiliaryFields","backFields")) fields.optJSONArray(group)?.let {body.put(group,it)}
  if(type=="boardingPass") body.put("transitType",row.optString("transitType").ifBlank {"PKTransitTypeGeneric"})
  json.put(type,body)
  val barcode=row.optString("barcodeValue")
  if(barcode.isNotBlank() && barcode!="null") json.put("barcodes",JSONArray().put(JSONObject()
   .put("format",row.optString("barcodeFormat").ifBlank {"PKBarcodeFormatQR"}).put("message",barcode)
   .put("messageEncoding","utf-8").put("altText",row.optString("barcodeAltText").takeUnless {it=="null"} ?: barcode)))
  val entries=linkedMapOf("pass.json" to json.toString().toByteArray())
  val fallbackIcon=Envelope.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGD4DwABBAEAX+XDSwAAAABJRU5ErkJggg==")
  entries["icon.png"]=fallbackIcon
  try {
   require(entries.getValue("pass.json").size<=500*1024)
   for((name,bytes) in assets) {require(name in setOf("logo.png","strip.png","thumbnail.png","icon.png"));entries[name]=bytes}
   require(entries.values.sumOf {it.size.toLong()}<=10*1024*1024)
   val manifest=JSONObject()
   entries.forEach { (name,bytes)->manifest.put(name,MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") {"%02x".format(it)})}
   val output=ByteArrayOutputStream()
   ZipOutputStream(output).use {zip->
    entries.forEach { (name,bytes)->zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()}
    zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest.toString().toByteArray());zip.closeEntry()
   }
   return output.toByteArray()
  } finally {entries["pass.json"]?.fill(0);fallbackIcon.fill(0)}
 }
}
