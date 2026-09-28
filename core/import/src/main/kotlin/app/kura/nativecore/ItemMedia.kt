package app.kura.nativecore

import org.json.JSONArray
import org.json.JSONObject

/** Additional media references live inside existing encrypted JSON columns, never new schema columns. */
object ItemMedia {
 const val KEY="_kuraMedia"
 const val MAX_FILE=10*1024*1024
 fun column(table:String)=if(table=="passes") "fields" else "customFields"
 fun get(table:String,row:JSONObject):JSONObject {
  val raw=row.optString(column(table),"{}").takeUnless {it.isBlank() || it=="null"} ?: "{}"
  val value=if(raw.trim().startsWith("[")) {
   val list=JSONArray(raw);(0 until list.length()).mapNotNull {list.optJSONObject(it)}.firstOrNull {it.optString("name")==KEY}?.opt("value")
  } else JSONObject(raw).opt(KEY)
  return when(value) {null,JSONObject.NULL->JSONObject();is JSONObject->JSONObject(value.toString());is String->JSONObject(value);else->error("Invalid item media")}
 }
 fun put(table:String,row:JSONObject,media:JSONObject) {
  val key=column(table);val raw=row.optString(key,"{}").takeUnless {it.isBlank() || it=="null"} ?: "{}"
  if(raw.trim().startsWith("[")) {
   val list=JSONArray(raw);val result=JSONArray()
   for(i in 0 until list.length()) if(list.optJSONObject(i)?.optString("name")!=KEY) result.put(list.get(i))
   if(media.length()>0) result.put(JSONObject().put("name",KEY).put("value",media.toString()))
   row.put(key,result.toString())
  } else {val fields=JSONObject(raw);fields.remove(KEY);if(media.length()>0) fields.put(KEY,media);row.put(key,fields.toString())}
 }
 fun logo(table:String,row:JSONObject):String { val media=get(table,row);if(media.has("logo")) return media.optString("logo");return run {
  if(table=="passes") row.optString("iconImagePath").takeUnless {it=="null"}.orEmpty().ifBlank {row.optString("logoImagePath").takeUnless {it=="null"}.orEmpty()} else ""
 }}
 fun attachments(table:String,row:JSONObject):List<JSONObject> {
  val list=get(table,row).optJSONArray("attachments") ?: return emptyList()
  require(list.length()<=20) {"At most 20 attachments per item"}
  return (0 until list.length()).map {list.getJSONObject(it).also {file->
   safePath(file.getString("path"));require(file.getString("name").length in 1..200)
   require(file.getString("mime").matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")))
   require(file.getLong("size") in 0..MAX_FILE.toLong())
  }}
 }
 fun safePath(path:String) {require(path.matches(Regex("[0-9a-fA-F-]{36}\\.(bin|png|jpg)(\\.enc)?"))) {"Invalid item media path"}}
 fun references(table:String,row:JSONObject):List<Pair<String,Boolean>> {
  val m=get(table,row);val logo=m.optString("logo")
  return (if(logo.isBlank()) emptyList() else {safePath(logo);listOf(logo to true)})+attachments(table,row).map {it.getString("path") to false}
 }
 fun mapPaths(table:String,row:JSONObject,change:(String)->String) {
  val m=get(table,row);if(m.length()==0) return
  references(table,row)
  if(m.optString("logo").isNotBlank()) m.put("logo",change(m.getString("logo")))
  m.optJSONArray("attachments")?.let {list->for(i in 0 until list.length()) {val file=list.getJSONObject(i);file.put("path",change(file.getString("path")))}}
  put(table,row,m)
 }
}
