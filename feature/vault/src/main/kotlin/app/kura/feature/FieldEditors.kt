package app.kura.feature
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject

/** Explicit conversion accepts only unambiguous label/value arrays and preserves the source on refusal. */
fun customFieldMap(raw:String):JSONObject {
 if(!raw.trim().startsWith("[")) return runCatching {JSONObject(raw).apply {remove(app.kura.nativecore.ItemMedia.KEY)}}.getOrDefault(JSONObject())
 val array=JSONArray(raw);val out=JSONObject()
 for(i in 0 until array.length()) {
  val entry=array.getJSONObject(i)
  val key=entry.optString("key").ifBlank {entry.optString("name").ifBlank {entry.optString("label")}}
  require(key.isNotBlank() && !out.has(key)) {"Custom fields contain missing or duplicate names"}
  require(entry.keys().asSequence().all {it in setOf("key","name","label","value")}) {"Custom field metadata cannot be converted without loss"}
  if(key!=app.kura.nativecore.ItemMedia.KEY) out.put(key,entry.opt("value") ?: "")
 }
 return out
}
private fun originalStructuredField(original:JSONObject,group:String,field:JSONObject):JSONObject? {
 val fields=original.optJSONArray(group) ?: return null
 return (0 until fields.length()).mapNotNull {fields.optJSONObject(it)}.firstOrNull {it.optString("key")==field.optString("key")}
}
fun structuredFieldIsNumeric(field:JSONObject,original:JSONObject?=null):Boolean =
 field.has("currencyCode") || field.has("numberStyle") || field.opt("value") is Number || original?.opt("value") is Number
/** Convert edited numeric JSON values only at save, keeping partial input and original drafts intact. */
fun structuredFieldsForSave(groups:JSONObject,original:JSONObject):JSONObject {
 val result=JSONObject(groups.toString())
 passFieldGroups.keys.forEach {group->
  val fields=result.optJSONArray(group) ?: return@forEach
  for(i in 0 until fields.length()) {
   val field=fields.optJSONObject(i) ?: continue
   val prior=originalStructuredField(original,group,field)
   if(prior?.opt("value") is Number && field.opt("value") is String && field.optString("value").isNotBlank()) {
    field.optString("value").toBigDecimalOrNull()?.let {field.put("value",it)}
   }
  }
 }
 return result
}
fun validStructuredFields(groups:JSONObject,original:JSONObject):Boolean = passFieldGroups.keys.all {group->
 val fields=groups.optJSONArray(group) ?: JSONArray()
 val old=original.optJSONArray(group) ?: JSONArray()
 (0 until fields.length()).all {index->
  val field=fields.optJSONObject(index) ?: return@all true
  val value=field.optString("value")
  val unchanged=(0 until old.length()).any {i->old.optJSONObject(i)?.let {it.optString("key")==field.optString("key") && it.optString("value")==value}==true}
  unchanged || value.isBlank() || when {
   structuredFieldIsNumeric(field,originalStructuredField(original,group,field))->value.toBigDecimalOrNull()!=null
   field.has("dateStyle") || field.has("timeStyle")->eventLocal(value)!=null
   else->true
  }
 }
}
val passFieldGroups=linkedMapOf("headerFields" to "Header fields","primaryFields" to "Primary fields","secondaryFields" to "Secondary fields","auxiliaryFields" to "Auxiliary fields","backFields" to "Back fields")
fun customFieldEntries(raw:String):List<Pair<String,String>> = runCatching {
 if(raw.trim().startsWith("[")) {
  val array=JSONArray(raw)
  (0 until array.length()).map {i->
   val entry=array.opt(i)
   if(entry is JSONObject) entry.optString("label").ifBlank {entry.optString("name").ifBlank {entry.optString("key").ifBlank {"Field "+(i+1)}}} to
    (entry.opt("value")?.takeUnless {it==JSONObject.NULL}?.toString().orEmpty())
   else "Field "+(i+1) to (entry?.takeUnless {it==JSONObject.NULL}?.toString().orEmpty())
  }
 } else {val map=JSONObject(raw);map.keys().asSequence().map {it to map.optString(it)}.toList()}
}.getOrDefault(emptyList()).filter {it.first!=app.kura.nativecore.ItemMedia.KEY}

/** Edit values in place; duplicate labels and unknown imported metadata keep their identity and order. */
@Composable fun CustomArrayEditor(array:JSONArray,revision:Int,schema:List<CustomField> = emptyList(),currency:String="",changed:()->Unit) {
 Text("Custom fields",style=MaterialTheme.typography.titleMedium)
 for(index in 0 until array.length()) {
  val entry=array.opt(index)
  val label=if(entry is JSONObject) entry.optString("label").ifBlank {entry.optString("name").ifBlank {entry.optString("key")}} else ""
  if(label==app.kura.nativecore.ItemMedia.KEY) continue
  val value=if(entry is JSONObject) entry.opt("value") else entry
  val text=value?.takeUnless {it==JSONObject.NULL}?.toString().orEmpty()
  if(value is JSONArray || value is JSONObject) {
   Text(label.ifBlank {"Field "+(index+1)},style=MaterialTheme.typography.labelMedium)
   Text(text)
  } else ConfiguredFieldInput(schema.find {it.name==label} ?: CustomField(label.ifBlank {"Field "+(index+1)},"text"),text,currency) {updated->
   if(entry is JSONObject) entry.put("value",updated) else array.put(index,updated);changed()
  }
 }
}
