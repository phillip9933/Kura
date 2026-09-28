package app.kura.feature

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

val sectionNames = linkedMapOf("wallets" to "Cards", "passes" to "Passes", "identities" to "Identity")
fun sectionKey(table: String) = when(table) { "wallets" -> "payments"; "identities" -> "identity"; else -> "passes" }
val defaultCategories = mapOf(
    "wallets" to listOf("Credit","Debit","Prepaid","Gift Card","Loyalty","Membership","Library"),
    "passes" to listOf("Boarding Pass","Transit","Event","Reservation","Parking","Coupon","Temporary Access","Other"),
    "identities" to listOf("Passport","Driver's License","Residence Card","National ID","Health Insurance","Employee ID","Student ID"))
data class CustomField(val name: String, val dataType: String, val dateFormat:String="yyyy-MM-dd")

/** Legacy preference names are retained; lists are encoded as JSON strings in encrypted backups. */
@androidx.compose.runtime.Immutable
data class VaultPreferences(val source: String = "{}") {
    private val json = runCatching { JSONObject(source) }.getOrDefault(JSONObject())
    fun bool(key: String, default: Boolean = true) = json.optBoolean(key, default)
    fun text(key: String, default: String = "") = json.optString(key,default).takeUnless { it == "null" } ?: default
    fun number(key: String, default: Int = 0) = json.optInt(key,default)
    val bottomNavigation get() = bool("showBottomNavigationBar")
    val gestures get() = bool("gestureNavigationEnabled")
    val search get() = bool("isPassSearchEnabled")
    val searchStyle get() = text("passSearchStyle","alwaysOn")
    val searchPosition get() = text("searchBarPosition","top")
    val controlPosition get() = text("controlRowPosition","top")
    val barcodeBrightness get() = bool("maxBrightnessOnBarcodeView",false)
    val barcodeFlipped get() = text("defaultBarcodeOrientation") == "flipped"
    val currency get() = text("selectedCurrencyCode","EUR")
    fun sortMode(section:String)=text(sectionKey(section)+"SortMode","custom").takeIf {it in itemSortChoices} ?: "custom"
    fun isFavorite(item:VaultItem)=json.optJSONObject("nativeFavorites")?.optBoolean(item.stableKey(),false)==true
    fun addedAt(item:VaultItem)=json.optJSONObject("nativeAddedAt")?.optLong(item.stableKey(),0L) ?: 0L
    fun columns(table: String) = number(sectionKey(table)+"GridColumns", if(table=="passes") number("passGridColumns",1) else 1).coerceIn(1,3)
    fun displayMode(table: String) = text(sectionKey(table)+"GridDisplayMode","front").let {if(it=="virtualCards") it else "front"}
    fun categories(table: String): List<String> = runCatching {
        val key=sectionKey(table)+"Categories"
        if(!json.has(key)) return defaultCategories.getValue(table)
        val array = json.optJSONArray(key) ?: JSONArray(json.getString(key))
        (0 until array.length()).map { array.getString(it) }.filter(String::isNotBlank).distinct().let {categories->
            val old=when(table) {
                "wallets"->listOf("Credit","Debit","Prepaid","Gift Card","Loyalty","Membership","Library","Gym","Store Card","Cash")
                "passes"->listOf("Boarding Pass","Transit","Concert","Sports","Event","Reservation","Parking","Coupon","Temporary Access","Other")
                else->emptyList()
            }
            if(categories==old) defaultCategories.getValue(table) else categories
        }
    }.getOrDefault(defaultCategories.getValue(table))
    fun customFields(table: String): List<CustomField> = runCatching {
        val array=JSONArray(text(sectionKey(table)+"CustomFieldSchemas","[]"))
        (0 until array.length()).map { array.getJSONObject(it) }.map {
            CustomField(it.getString("name"),it.optString("dataType","text").takeIf { type -> type in customFieldTypes } ?: "text",it.optString("dateFormat","yyyy-MM-dd").takeIf {format->format in customDateFormats} ?: "yyyy-MM-dd")
        }
    }.getOrDefault(emptyList())
}

fun itemExpiry(item: VaultItem): LocalDate? = runCatching {
    val raw=item.expiryValue
    when {
        Regex("\\d{4}").matches(raw) -> YearMonth.of(2000+raw.takeLast(2).toInt(),raw.take(2).toInt()).atEndOfMonth()
        Regex("\\d{2}/\\d{2}").matches(raw) -> YearMonth.of(2000+raw.takeLast(2).toInt(),raw.take(2).toInt()).atEndOfMonth()
        else -> LocalDate.parse(raw.take(10),DateTimeFormatter.ISO_LOCAL_DATE)
    }
}.getOrNull()
