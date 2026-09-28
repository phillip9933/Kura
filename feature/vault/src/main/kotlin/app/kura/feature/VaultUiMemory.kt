package app.kura.feature
import androidx.compose.runtime.*
import org.json.JSONObject
/** ViewModel-owned only: draft plaintext is never written to Bundle, SavedStateHandle, or disk. */
class VaultUiMemory {
 var initialized=false
 val table=mutableStateOf("passes")
 val adding=mutableStateOf(false)
 val addCardKind=mutableStateOf<String?>(null)
 val editing=mutableStateOf<VaultItem?>(null)
 var form=FormMemory()
 val manualSection=mutableStateOf("passes")
 val manualCategory=mutableStateOf("Other")
 val manualForms=mutableMapOf<String,FormMemory>()
 fun clearManual() {manualForms.values.forEach {it.clear()};manualForms.clear()}
 fun clear() {initialized=false;table.value="passes";adding.value=false;addCardKind.value=null;editing.value=null;form.clear();clearManual()}
}
class FormMemory {
 var identity:String?=null
 var defaultClassification:String?=null
 var original=JSONObject()
 var groups=JSONObject()
 var customArray:org.json.JSONArray?=null
 val section=mutableStateOf("passes")
 val category=mutableStateOf("")
 val removedImages=mutableStateListOf<String>()
 val revision=mutableIntStateOf(0)
 val values=mutableStateMapOf<String,String>()
 val customValues=mutableStateMapOf<String,String>()
 fun clear() {identity=null;defaultClassification=null;original=JSONObject();groups=JSONObject();customArray=null;removedImages.clear();values.clear();customValues.clear();revision.intValue=0}
 fun initialize(table:String,item:VaultItem?,initialSection:String,prefs:VaultPreferences) {
  val key=table+":"+(item?.id ?: "new")
  if(identity==key) return
  clear();identity=key;original=item?.let {JSONObject(it.json)} ?: JSONObject()
  section.value=initialSection
  category.value=if(item!=null) classifyItem(table,original).category else if(initialSection=="wallets" && table=="passes") "Membership" else defaultFormCategory(initialSection,prefs)
  recordFields(table).keys.forEach {values[it]=original.optString(it).takeUnless {v->v=="null"}.orEmpty()}
  if(item==null) {
   values[if(table=="passes") "type" else if(table=="wallets") "category" else "cardType"]=defaultFormCategory(initialSection,prefs)
   if(table=="passes") {values["barcodeFormat"]="PKBarcodeFormatQR";values["type"]=initialPassLayout(initialSection,category.value)}
  }
  customArray=if(table!="passes" && original.optString("customFields").trim().startsWith("[")) runCatching {org.json.JSONArray(original.optString("customFields"))}.getOrNull() else null
  groups=runCatching {JSONObject(original.optString("fields","{}"))}.getOrDefault(JSONObject())
  val custom=if(table=="passes") groups.optJSONObject("customFields") ?: JSONObject() else runCatching {customFieldMap(original.optString("customFields","{}"))}.getOrDefault(JSONObject())
  (prefs.customFields(initialSection).map {it.name}+custom.keys().asSequence().toList()).distinct().forEach {customValues[it]=custom.optString(it)}
 }
}
