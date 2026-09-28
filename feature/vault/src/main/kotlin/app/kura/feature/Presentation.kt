package app.kura.feature

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Locale

fun passColor(value: String): Color? = runCatching {
    Color(if(value.startsWith("0x")) value.substring(2).toLong(16).toInt() else android.graphics.Color.parseColor(value))
}.getOrNull()

fun formattedField(field: JSONObject): String {
    val value = field.opt("value")
    if(value == null || value == JSONObject.NULL) return ""
    if(field.has("currencyCode")) return runCatching {
        NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(field.getString("currencyCode")) }
            .format(value.toString().toBigDecimal())
    }.getOrDefault(value.toString())
    if(field.has("numberStyle")) return runCatching {
        val format = if(field.getString("numberStyle") == "PKNumberStylePercent") NumberFormat.getPercentInstance() else NumberFormat.getNumberInstance()
        format.format(value.toString().toBigDecimal())
    }.getOrDefault(value.toString())
    if(field.has("dateStyle") || field.has("timeStyle")) return runCatching {
        val date = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd")
            .firstNotNullOfOrNull { pattern -> runCatching { SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false }.parse(value.toString()) }.getOrNull() }
            ?: error("Invalid date")
        fun style(name: String) = when(name) { "PKDateStyleShort" -> DateFormat.SHORT; "PKDateStyleLong", "PKDateStyleFull" -> DateFormat.LONG; else -> DateFormat.MEDIUM }
        val dateStyle = field.optString("dateStyle", "PKDateStyleNone")
        val timeStyle = field.optString("timeStyle", "PKDateStyleNone")
        when {
            dateStyle == "PKDateStyleNone" -> DateFormat.getTimeInstance(style(timeStyle))
            timeStyle == "PKDateStyleNone" -> DateFormat.getDateInstance(style(dateStyle))
            else -> DateFormat.getDateTimeInstance(style(dateStyle), style(timeStyle))
        }.format(date)
    }.getOrDefault(value.toString())
    return value.toString()
}
@Composable
fun VaultImage(path: String, label: String, load: suspend (String) -> ByteArray?, modifier: Modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit) {
    val bitmap by produceState<Bitmap?>(null, path) {
        value = try { withContext(Dispatchers.Default) {
            val bytes = load(path) ?: return@withContext null
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong()*bounds.outHeight <= 16_000_000)
                val options = BitmapFactory.Options().apply {
                    inMutable = true; inSampleSize = 1
                    while(maxOf(bounds.outWidth, bounds.outHeight)/inSampleSize > 1024) inSampleSize *= 2
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } finally { bytes.fill(0) }
        } } catch(e: CancellationException) { throw e } catch (_: Exception) { null }
    }
    DisposableEffect(bitmap) { val owned = bitmap; onDispose { owned?.let { if(!it.isRecycled) { it.eraseColor(0); it.recycle() } } } }
    bitmap?.let { Image(it.asImageBitmap(), label, modifier, contentScale = contentScale) }
}
