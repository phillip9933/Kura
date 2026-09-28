package app.kura.feature

import androidx.compose.runtime.Immutable
import org.json.JSONObject

/** Immutable row snapshot; classification and pass formatting are prepared once per refreshed record. */
@Immutable
data class VaultItem(val table: String, val id: Long, val title: String, val subtitle: String, val category: String, val archived: Boolean, val json: String) {
    private val attributes=runCatching {JSONObject(json)}.getOrDefault(JSONObject()).let {row->
        Triple(classifyItem(table,row),row.optString("relevantDate"),row.optString("expiry_date").takeUnless {it.isBlank() || it=="null"} ?: row.optString("expiry"))
    }
    private val classification=attributes.first
    val relevantValue get()=attributes.second
    val expiryValue get()=attributes.third
    val passPresentation=if(table=="passes") PassPresentation.from(this) else null
    val section get() = classification.section
    val displayCategory get() = classification.category
}
