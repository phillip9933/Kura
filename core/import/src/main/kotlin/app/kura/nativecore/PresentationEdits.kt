package app.kura.nativecore
import org.json.JSONObject
/** Atomic encrypted preference overlays preserve legacy columns while editing mixed-storage sections. */
object PresentationEdits {
 fun category(table:String,row:JSONObject,settings:JSONObject):JSONObject {
  val value=settings.optJSONObject("nativeCategoryOverrides")?.optString(table+":"+row.optLong("id")).orEmpty()
  if(value.isEmpty()) return row
  val copy=JSONObject(row.toString())
  if(table=="passes") {
   val fields=runCatching {JSONObject(copy.optString("fields","{}"))}.getOrDefault(JSONObject())
   val metadata=fields.optJSONObject("_kura") ?: JSONObject()
   settings.optJSONObject("nativeCategorySections")?.optString(table+":"+row.optLong("id"))?.takeIf {it in setOf("wallets","passes","identities")}?.let {metadata.put("section",it)}
   metadata.put("category",value);fields.put("_kura",metadata);copy.put("fields",fields.toString())
  } else copy.put(if(table=="wallets") "category" else "cardType",value)
  return copy
 }
}
