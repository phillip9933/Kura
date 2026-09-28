package app.kura.feature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Neutral palette from the Flutter ThemeProvider; deliberately independent of wallpaper colors. */
fun kuraColorScheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Color.White, onPrimary = Color.Black, background = Color.Black,
    surface = Color.Black, onSurface = Color.White, onBackground = Color.White,
    surfaceVariant = Color(0xFF171717), onSurfaceVariant = Color(0xFFAAAAAA),
    secondaryContainer = Color(0xFF252525), onSecondaryContainer = Color.White,
    outline = Color(0xFF333333)
) else lightColorScheme(
    primary = Color.Black, onPrimary = Color.White, background = Color.White,
    surface = Color.White, onSurface = Color.Black, onBackground = Color.Black,
    surfaceVariant = Color(0xFFF5F5F5), onSurfaceVariant = Color(0xFF666666),
    secondaryContainer = Color(0xFFE6E6E6), onSecondaryContainer = Color.Black,
    outline = Color(0xFFE5E5E5)
)

/** Small vector icons: no fonts, bitmap allocations, or extra icon-library dependency. */
@Composable
internal fun KuraIcon(kind: String, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.8f)
            fun line(a: Float, b: Float, c: Float, d: Float) =
                drawLine(tint, Offset(a,b), Offset(c,d), 1.8f, StrokeCap.Round)
            when(kind) {
                "add" -> { line(12f,5f,12f,19f); line(5f,12f,19f,12f) }
                "back" -> { line(15f,4f,7f,12f); line(7f,12f,15f,20f) }
                "down" -> { line(6f,9f,12f,15f); line(12f,15f,18f,9f) }
                "grid" -> for(x in listOf(3f,14f)) for(y in listOf(3f,14f))
                    drawRoundRect(tint, Offset(x,y), Size(7f,7f), androidx.compose.ui.geometry.CornerRadius(1.5f))
                "settings" -> {
                    drawCircle(tint,7f,Offset(12f,12f),style=stroke)
                    drawCircle(tint,2.5f,Offset(12f,12f),style=stroke)
                    for(i in 0..7) {
                        val angle = i*Math.PI/4
                        line((12+7*kotlin.math.cos(angle)).toFloat(),(12+7*kotlin.math.sin(angle)).toFloat(),
                            (12+10*kotlin.math.cos(angle)).toFloat(),(12+10*kotlin.math.sin(angle)).toFloat())
                    }
                }
                else -> {
                    drawRoundRect(tint,Offset(2f,5f),Size(20f,14f),androidx.compose.ui.geometry.CornerRadius(2f),style=stroke)
                    when(kind) {
                        "wallets" -> { line(3f,10f,21f,10f); line(5f,15f,10f,15f) }
                        "identities" -> {
                            drawCircle(tint,2f,Offset(8f,11f),style=stroke)
                            line(5f,16f,11f,16f); line(15f,10f,19f,10f); line(15f,14f,19f,14f)
                        }
                        else -> for(y in listOf(8f,12f,16f)) line(9f,y,9f,y+.5f)
                    }
                }
            }
        }
    }
}

@Composable
internal fun WalletPreview(item: VaultItem, compact: Boolean, open: () -> Unit, loadImage: suspend (String) -> ByteArray?, displayMode: String = "front", currency: String = "EUR", onLongClick:(()->Unit)?=null) {
    val json = remember(item.json) { JSONObject(item.json) }
    val barcode = if(item.table == "passes" && !json.optString("fields").contains("primaryFields")) rememberPassBarcode(item) else null
    val base = remember(item.json) { passColor(json.optString(if(item.table == "passes") "backgroundColor" else "color")) ?: Color(0xFF171717) }
    val foreground = if(item.table == "passes") passColor(json.optString("foregroundColor")) ?: if(base.luminance() > .45f) Color.Black else Color.White else Color.White
    Column(Modifier.fillMaxWidth()) {
        Surface(modifier = Modifier.combinedClickable(onClick=open,onLongClick=onLongClick).semantics { contentDescription = "Open " + item.title }, shape = RoundedCornerShape(15.dp), color = base, contentColor = foreground) {
            Box {
            Column(Modifier.fillMaxWidth().aspectRatio(1.586f)
                .background(Brush.linearGradient(listOf(lerp(base,Color.White,.09f),base,lerp(base,Color.Black,.18f))))
                .padding(if(compact) 12.dp else 22.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if(item.table == "passes" && !json.isNull("logoImagePath") && json.optString("logoImagePath").isNotBlank())
                        Box(Modifier.size(if(compact) 24.dp else 32.dp)) { VaultImage(json.getString("logoImagePath"),"Pass logo",loadImage) }
                    else KuraIcon(item.table, tint = foreground.copy(alpha = .8f))
                    Text(if(item.table == "wallets") json.optString("network").uppercase() else when(item.category) { "boardingPass" -> "Boarding Pass"; "storeCard" -> "Store Card"; "eventTicket" -> "Event Ticket"; else -> item.category.replaceFirstChar { it.uppercase() } },
                        fontSize = if(compact) 10.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start=8.dp), fontWeight=FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
                if(item.table == "wallets") {
                    Text("••••  ••••  ••••  " + item.subtitle.takeLast(4),
                        fontFamily = FontFamily.Monospace, fontSize = if(compact) 11.sp else 21.sp,
                        maxLines=1, overflow=TextOverflow.Ellipsis)
                    Spacer(Modifier.height(if(compact) 8.dp else 20.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(item.title.uppercase(),Modifier.weight(1f),fontSize=if(compact) 10.sp else 13.sp,
                            maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(json.optString("expiry").let { if(it.length==4) it.take(2)+"/"+it.takeLast(2) else it },
                            fontSize=if(compact) 10.sp else 13.sp)
                    }
                } else {
                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = if(compact) 15.sp else 23.sp,
                        maxLines=2,overflow=TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    if(item.table == "identities") Text("•••• " + item.subtitle.takeLast(4),fontFamily=FontFamily.Monospace)
                    else {
                        if(barcode != null && !json.optString("fields").contains("primaryFields")) {
                            androidx.compose.foundation.Image(barcode.asImageBitmap(),"Barcode preview",
                                Modifier.fillMaxWidth().height(if(compact) 42.dp else 72.dp).background(Color.White))
                        }
                        val fields = remember(item.json) { runCatching { JSONObject(json.optString("fields","{}")).optJSONArray("primaryFields") }.getOrNull() }
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            for(index in 0 until minOf(fields?.length() ?: 0,2)) {
                                val field = fields!!.optJSONObject(index) ?: continue
                                Column(Modifier.weight(1f)) {
                                    Text(field.optString("label"),fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    Text(formattedField(field),fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
                val front = json.optString("frontImagePath").takeIf { !json.isNull("frontImagePath") && it.isNotBlank() }
                if(front != null && displayMode == "front") VaultImage(front,"Card front",loadImage,Modifier.fillMaxWidth().aspectRatio(1.586f))
            }
        }
        if(item.table == "passes") {
            Text(item.title,Modifier.padding(top=8.dp),fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(item.subtitle.isNotBlank()) Text(item.subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}
