package app.kura.feature

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.json.JSONObject

@androidx.compose.runtime.Immutable
data class PassField(val label: String, val value: String)
@androidx.compose.runtime.Immutable
data class PassPresentation(
    val type: String, val title: String, val description: String,
    val header: List<PassField>, val primary: List<PassField>, val secondary: List<PassField>,
    val auxiliary: List<PassField>, val back: List<PassField>,
    val relevantDate: String, val expiry: String, val logo: String, val strip: String,
    val thumbnail: String, val footer: String, val barcode: String, val format: String, val altText: String,
    val background: String, val foreground: String, val label: String
) {
    companion object {
        fun from(item: VaultItem): PassPresentation {
            val json = runCatching { JSONObject(item.json) }.getOrDefault(JSONObject())
            fun text(key: String) = if(json.isNull(key)) "" else json.optString(key)
            val fields = runCatching { JSONObject(text("fields")) }.getOrDefault(JSONObject())
            fun group(key: String): List<PassField> {
                val values = fields.optJSONArray(key) ?: return emptyList()
                return (0 until values.length()).mapNotNull { index ->
                    values.optJSONObject(index)?.let {
                        PassField(if(it.isNull("label")) "" else it.optString("label"),formattedField(it))
                    }
                }
            }
            fun date(key: String) = text(key).takeIf(String::isNotBlank)?.let {
                formattedField(JSONObject().put("value",it).put("dateStyle","PKDateStyleShort"))
            }.orEmpty()
            return PassPresentation(text("type").ifBlank { "generic" },text("logoText").ifBlank { text("organizationName").ifBlank { item.title.takeUnless { it=="null" }.orEmpty().ifBlank { "Pass" } } },
                text("description").ifBlank { item.subtitle.takeUnless { it=="null" }.orEmpty() }, group("headerFields"),group("primaryFields"),group("secondaryFields"),group("auxiliaryFields"),group("backFields"),
                date("relevantDate"),date("expiry_date"),text("logoImagePath").ifBlank { text("iconImagePath") },
                text("stripImagePath"),text("thumbnailImagePath"),text("footerImagePath"),text("barcodeValue"),text("barcodeFormat"),
                text("barcodeAltText"),text("backgroundColor"),text("foregroundColor"),text("labelColor"))
        }
    }
}

/** PKPASS rgb()/rgba(), CSS hex and Android ARGB hex. Reject malformed/out-of-range input. */
fun parsePassColor(raw: String): Color? = runCatching {
    val text = raw.trim()
    val match = Regex("""(?i)(rgb|rgba)\(\s*([^)]*)\s*\)""").matchEntire(text)
    if(match != null) {
        val values = match.groupValues[2].split(",").map { it.trim().toFloat() }
        require(values.size == if(match.groupValues[1].equals("rgba",true)) 4 else 3)
        require(values.take(3).all { it.isFinite() && it in 0f..255f })
        val alpha = values.getOrElse(3) { 1f }; require(alpha.isFinite() && alpha in 0f..1f)
        Color(values[0]/255f,values[1]/255f,values[2]/255f,alpha)
    } else Color(if(text.startsWith("0x")) text.substring(2).toLong(16).toInt() else android.graphics.Color.parseColor(text))
}.getOrNull()

fun contrastRatio(a: Color, b: Color): Float {
    val foreground = a.compositeOver(b).luminance()
    val background = b.luminance()
    return (maxOf(foreground,background)+.05f)/(minOf(foreground,background)+.05f)
}
data class PassPalette(val background: Color, val foreground: Color, val label: Color)
fun passPalette(pass: PassPresentation, scheme: ColorScheme): PassPalette {
    val fallback = PassPalette(scheme.surfaceVariant,scheme.onSurfaceVariant,scheme.onSurfaceVariant)
    val background = parsePassColor(pass.background)?.compositeOver(scheme.surfaceVariant) ?: return fallback
    val foreground = parsePassColor(pass.foreground) ?: if(contrastRatio(Color.White,background)>=4.5f) Color.White else Color.Black
    if(contrastRatio(foreground,background) < 4.5f) return fallback
    val label = parsePassColor(pass.label)?.takeIf { contrastRatio(it,background) >= 4.5f } ?: foreground
    return PassPalette(background,foreground,label)
}
