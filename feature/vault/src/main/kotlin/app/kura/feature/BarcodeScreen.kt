package app.kura.feature

import android.graphics.Bitmap
import android.view.Window
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.google.zxing.*
import kotlinx.coroutines.*

val barcodeFormats = linkedMapOf("QR Code" to BarcodeFormat.QR_CODE, "Aztec" to BarcodeFormat.AZTEC,
    "PDF417" to BarcodeFormat.PDF_417, "Code 128" to BarcodeFormat.CODE_128, "Data Matrix" to BarcodeFormat.DATA_MATRIX,
    "Code 39" to BarcodeFormat.CODE_39, "Code 93" to BarcodeFormat.CODE_93, "EAN-8" to BarcodeFormat.EAN_8,
    "EAN-13" to BarcodeFormat.EAN_13, "UPC-A" to BarcodeFormat.UPC_A, "UPC-E" to BarcodeFormat.UPC_E,
    "ITF" to BarcodeFormat.ITF, "Codabar" to BarcodeFormat.CODABAR)
fun barcodeFormat(value: String): BarcodeFormat? = when(value) {
    "PKBarcodeFormatQR" -> BarcodeFormat.QR_CODE; "PKBarcodeFormatAztec" -> BarcodeFormat.AZTEC
    "PKBarcodeFormatPDF417","PDF417" -> BarcodeFormat.PDF_417
    "PKBarcodeFormatCode128","CODE128" -> BarcodeFormat.CODE_128
    "" -> BarcodeFormat.QR_CODE
    else -> barcodeFormats[value] ?: BarcodeFormat.entries.find { it.name==value }
}
private val clipboardHandler=android.os.Handler(android.os.Looper.getMainLooper())
private var clipboardGeneration=0L
fun copyPrivateText(context: android.content.Context, value: String) {
    val token=++clipboardGeneration
    val copyId=java.util.UUID.randomUUID().toString()
    context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(
        android.content.ClipData.newPlainText("Kura",value).apply {
            description.extras=android.os.PersistableBundle().apply {putBoolean("android.content.extra.IS_SENSITIVE",true);putString("app.kura.clip",copyId)}
        })
    // Compare an ownership tag, not secret text. Never clear something another app copied later.
    val app=context.applicationContext
    clipboardHandler.postDelayed({
        if(token==clipboardGeneration) runCatching {
            val manager=app.getSystemService(android.content.ClipboardManager::class.java)
            if(manager.primaryClipDescription?.extras?.getString("app.kura.clip")==copyId) {
                if(android.os.Build.VERSION.SDK_INT>=28) manager.clearPrimaryClip()
                else manager.setPrimaryClip(android.content.ClipData.newPlainText("",""))
            }
        }
    },40_000L)
}

@Composable
internal fun rememberBarcode(content: String, formatKey: String): Bitmap? {
    val bitmap by produceState<Bitmap?>(null,content,formatKey) {
        value=withContext(Dispatchers.Default) {
            renderBarcode(content,formatKey)
        }
    }
    DisposableEffect(bitmap) {val owned=bitmap; onDispose {owned?.let {if(!it.isRecycled) {it.eraseColor(0);it.recycle()}}}}
    return bitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(title: String, value: String, originalFormat: String, window: Window, preferences: VaultPreferences, dismiss:()->Unit) {
    var format by remember { mutableStateOf(originalFormat) }
    var flipped by remember { mutableStateOf(preferences.barcodeFlipped) }
    var formats by remember { mutableStateOf(false) }
    val barcode=rememberBarcode(value,format)
    val context=LocalContext.current
    KuraDialog(dismiss) {
        val dialogWindow=(LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window,dialogWindow) {
            val windows=listOfNotNull(window,dialogWindow).distinct()
            val previous=windows.map { it.attributes.screenBrightness }
            if(preferences.barcodeBrightness) windows.forEach {it.attributes=it.attributes.apply {screenBrightness=1f}}
            onDispose { windows.forEachIndexed { index, it -> it.attributes=it.attributes.apply {screenBrightness=previous[index]} } }
        }
        Scaffold(topBar={TopAppBar(title={Text(title,maxLines=1)},navigationIcon={
            IconButton(onClick=dismiss) {Icon(Icons.Default.Close,"Close barcode")}
        },actions={IconButton(onClick={copyPrivateText(context,value)}) {Icon(Icons.Default.ContentCopy,"Copy barcode")}})},
            bottomBar={Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    OutlinedButton(onClick={flipped=!flipped}) {Icon(Icons.Default.Rotate90DegreesCcw,null);Text("Rotate")}
                    Box {
                        OutlinedButton(onClick={formats=true}) {Icon(Icons.Default.QrCode,null);Text(barcodeFormats.entries.find {it.value==barcodeFormat(format)}?.key ?: format.takeIf {it in allBarcodeFormats} ?: "Choose format")}
                        DropdownMenu(formats,{formats=false}) {
                            allBarcodeFormats.forEach { name -> DropdownMenuItem(text={Text(name)},onClick={format=name;formats=false}) }
                        }
                    }
                }
            }}) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                Card(Modifier.fillMaxWidth().rotate(if(flipped) 180f else 0f),shape=RoundedCornerShape(16.dp),
                    colors=CardDefaults.cardColors(containerColor=Color.White,contentColor=Color.Black)) {
                    Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        barcode?.let { Image(it.asImageBitmap(),"Fullscreen barcode",Modifier.fillMaxWidth(),
                            filterQuality=androidx.compose.ui.graphics.FilterQuality.None) }
                            ?: Text("This value cannot be displayed in the selected format.",color=Color.Black)
                        Text(value,Modifier.padding(top=16.dp),fontFamily=FontFamily.Monospace,color=Color.Black)
                    }
                }
            }
        }
    }
}
