package app.kura.feature

import android.view.Window
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.TransformOrigin
import org.json.JSONObject

@Composable
fun PassCard(item: VaultItem, open: () -> Unit, loadImage: suspend (String) -> ByteArray?, openBarcode: (()->Unit)?=null, onLongClick:(()->Unit)?=null) {
    if(item.section==VaultSection.CARDS) {CardSectionPreview(item,open,loadImage,onLongClick);return}
    val pass = remember(item) { item.passPresentation ?: PassPresentation.from(item) }
    val colors = passPalette(pass,MaterialTheme.colorScheme)
    ElevatedCard(modifier=Modifier.fillMaxWidth().combinedClickable(onClick=open,onLongClick=onLongClick).semantics { contentDescription="Open "+item.title },
        shape=RoundedCornerShape(16.dp),colors=CardDefaults.elevatedCardColors(containerColor=colors.background,contentColor=colors.foreground)) {
        Column(Modifier.fillMaxWidth().heightIn(min=156.dp).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                if(!isImportedPass(item)) CardLogo(pass.title,app.kura.nativecore.ItemMedia.logo(item.table,JSONObject(item.json)),loadImage,Modifier.size(24.dp))
                else if(pass.logo.isNotBlank()) PassAsset(pass.logo,"Pass logo",Modifier.size(24.dp),loadImage,4)
                else Icon(Icons.Default.ConfirmationNumber,null,Modifier.size(24.dp))
                Text(pass.title,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                pass.header.firstOrNull()?.let {
                    Column(Modifier.widthIn(max=72.dp),horizontalAlignment=Alignment.End) {
                        Text(it.label,style=MaterialTheme.typography.labelSmall,color=colors.label,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(it.value,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    if(pass.type=="boardingPass" && pass.primary.size>=2) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            for(index in 0..1) {
                                if(index==1) Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(18.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(pass.primary[index].label,style=MaterialTheme.typography.labelSmall,color=colors.label,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    Text(pass.primary[index].value,style=MaterialTheme.typography.headlineMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                                }
                            }
                        }
                    } else {
                        Text(pass.primary.firstOrNull()?.value?.ifBlank { pass.description } ?: pass.description.ifBlank { pass.title },
                            style=if(pass.type in setOf("coupon","storeCard")) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                            maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                    val subtext = pass.secondary.firstOrNull()?.let { it.label+" "+it.value }.orEmpty().ifBlank { pass.relevantDate }
                    if(subtext.isNotBlank()) Text(subtext,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                val photo=passPhotoPath(item)
                if(photo.isNotBlank()) PassAsset(photo,"Pass photo",Modifier.weight(.65f).aspectRatio(.8f).heightIn(max=112.dp),loadImage,8)
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(pass.expiry.takeIf(String::isNotBlank)?.let { "Expires "+it }
                    ?: pass.auxiliary.firstOrNull()?.value.orEmpty().ifBlank { if(pass.secondary.isNotEmpty()) pass.relevantDate else "" },
                    Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=colors.label,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(pass.barcode.isNotBlank()) Icon(Icons.Default.QrCode,"Barcode available",Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PassAsset(path: String, label: String, modifier: Modifier, load: suspend (String)->ByteArray?, radius: Int = 12,
    crop: Boolean = false) {
    // Reserve geometry before decode and retain it on missing/invalid images.
    Box(modifier.clip(RoundedCornerShape(radius.dp)),contentAlignment=Alignment.Center) {
        VaultImage(path,label,load,Modifier.fillMaxSize(),if(crop) androidx.compose.ui.layout.ContentScale.Crop else androidx.compose.ui.layout.ContentScale.Fit)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldGroup(title: String, fields: List<PassField>, label: Color, primary: Boolean=false) {
    if(fields.isEmpty()) return
    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        fields.forEach {
            Column(Modifier.widthIn(min=120.dp,max=280.dp)) {
                Text(it.label,style=if(primary) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,color=label)
                Text(it.value,style=if(primary) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassDetailScreen(item: VaultItem, window: Window, dismiss:()->Unit, archive:()->Unit,
    delete:()->Unit, export:()->Unit, edit:()->Unit, openLink:(String)->Unit, loadImage:suspend(String)->ByteArray?, preferences:VaultPreferences=VaultPreferences(),
    onImage:(VaultItem,String,String)->Unit={_,_,_->}, onRemoveImage:(VaultItem,String)->Unit={_,_->}, share:()->Unit={},pkpass:()->Unit={},favorite:Boolean=false,onFavorite:()->Unit={}) {
    val pass = remember(item) { item.passPresentation ?: PassPresentation.from(item) }
    val colors = passPalette(pass,MaterialTheme.colorScheme)
    val membership=item.section==VaultSection.CARDS && !isImportedPass(item)
    val barcode = rememberPassBarcode(item)
    var fullscreen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var backExpanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val barcodePanel: @Composable ()->Unit = {
                if(pass.barcode.isNotBlank()) {
                    Card(onClick={fullscreen=true},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White,contentColor=Color.Black)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            barcode?.let {
                                Image(it.asImageBitmap(),"Pass barcode",Modifier.fillMaxWidth().heightIn(max=300.dp),
                                    filterQuality=androidx.compose.ui.graphics.FilterQuality.None)
                            } ?: Text("Barcode unavailable",color=Color.Black)
                            val code=pass.altText.ifBlank { pass.barcode }
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Text(code,Modifier.weight(1f),fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodyMedium,color=Color.Black)
                                IconButton(onClick={copyPrivateText(context,code)}) {Icon(Icons.Default.ContentCopy,"Copy ticket code",tint=Color.Black)}
                            }
                        }
                    }

                }
    }
    KuraDialog(dismiss) {
        Scaffold(topBar={
            TopAppBar(title={Text(pass.title,maxLines=1,overflow=TextOverflow.Ellipsis)},
                navigationIcon={IconButton(onClick=dismiss) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Close")}},
                actions={ItemActionsMenu("More pass actions",favorite,item.archived,onFavorite,edit,archive,{confirmDelete=true},share,export,pkpass,editable=!isImportedPass(item))})
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Card(colors=CardDefaults.cardColors(containerColor=colors.background,contentColor=colors.foreground)) {
                    PassInformationBody(item,pass,colors,membership,loadImage,barcodePanel)
                }
                if(!membership) barcodePanel()
                if(pass.back.isNotEmpty()) Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick={backExpanded=!backExpanded},modifier=Modifier.fillMaxWidth().semantics { stateDescription=if(backExpanded) "Expanded" else "Collapsed" }) {Text("Terms and additional information",Modifier.weight(1f));Icon(if(backExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,if(backExpanded) "Collapse information" else "Expand information")}
                        if(backExpanded) pass.back.forEach { field ->
                            Text(field.label,style=MaterialTheme.typography.labelMedium)
                            Text(field.value)
                            passLinks(field.value).forEach { uri -> TextButton(onClick={openLink(uri)}) {Text(uri)} }
                            HorizontalDivider()
                        }
                    }
                }
                if(item.section==VaultSection.CARDS && !isImportedPass(item)) MembershipDetails(item,preferences)
                ItemImages(item,loadImage,onImage,onRemoveImage)
                ItemAttachments(item,onImage)
                val custom=remember(item) {runCatching {JSONObject(JSONObject(item.json).optString("fields","{}")).optJSONObject("customFields")}.getOrNull()}
                if(item.section!=VaultSection.CARDS || isImportedPass(item)) custom?.let {values->for(key in values.keys()) {Text(key,style=MaterialTheme.typography.labelMedium);Text(customDisplayValue((preferences.customFields(item.section.key)+suggestedFields(item.section.key,item.displayCategory)).find {it.name==key},values.optString(key),preferences.currency))}}
            }
        }
        if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete pass?")},
            text={Text("This removes this pass from the current vault. Existing backups and previous vaults are unchanged.")},
            confirmButton={TextButton(onClick={confirmDelete=false;delete()}) {Text("Delete pass")}},
            dismissButton={TextButton(onClick={confirmDelete=false}) {Text("Cancel")}})
    }
    if(fullscreen) BarcodeScreen(pass.title,pass.barcode,pass.format,window,preferences,{fullscreen=false})
}

fun passLinks(text: String): List<String> {
    val urls=Regex("""https?://[^\s<>"]+""",RegexOption.IGNORE_CASE).findAll(text).map { val value=it.value.trimEnd('.',',',';',')'); value.substringBefore("://").lowercase()+"://"+value.substringAfter("://") }.toList()
    val phones=Regex("""(?<!\w)\+?\d[\d ()-]{6,}\d(?!\w)""").findAll(text)
        .filter { match -> urls.none { it.contains(match.value) } }
        .map { "tel:"+it.value.filter { c -> c.isDigit() || c=='+' } }.toList()
    return (urls+phones).distinct()
}

fun isImportedPass(item:VaultItem):Boolean {
 if(item.table!="passes") return false
 val row=runCatching {JSONObject(item.json)}.getOrDefault(JSONObject())
 return row.optString("sourceType").lowercase() in setOf("pkpass","pkpasses") ||
  (row.optString("sourceType")!="manual" && listOf("passTypeIdentifier","serialNumber","teamIdentifier").any {row.optString(it).let {v->v.isNotBlank() && v!="null"}})
}

fun passPhotoPath(item:VaultItem):String {
 if(!isImportedPass(item)) return ""
 val row=JSONObject(item.json)
 return listOf("thumbnailImagePath","frontImagePath").firstNotNullOfOrNull {key->row.optString(key).takeUnless {it.isBlank() || it=="null"}}.orEmpty()
}
@Composable private fun MembershipDetails(item:VaultItem,prefs:VaultPreferences) {
 val json=remember(item.json) {JSONObject(item.json)}
 val context=androidx.compose.ui.platform.LocalContext.current
 val details=linkedMapOf("Card type" to item.displayCategory,"Organization" to item.title,"Description" to json.optString("description"),"Member / barcode number" to json.optString("barcodeValue"),"Expiry" to json.optString("expiry_date"))
 val presentation=PassPresentation.from(item)
 (presentation.header+presentation.primary+presentation.secondary+presentation.auxiliary).forEach {details[it.label.ifBlank {"Information"}]=it.value}
 if(presentation.relevantDate.isNotBlank()) details["Relevant date"]=presentation.relevantDate
 val fields=runCatching {JSONObject(json.optString("fields","{}"))}.getOrDefault(JSONObject())
 fields.optJSONObject("customFields")?.let {custom->custom.keys().forEach {key->details[key]=customDisplayValue((prefs.customFields(item.section.key)+suggestedFields(item.section.key,item.displayCategory)).find {it.name==key},custom.optString(key),prefs.currency)}}
 Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text("Details",style=MaterialTheme.typography.titleMedium)
  details.filterValues {it.isNotBlank() && it!="null"}.forEach {(label,value)->Row(verticalAlignment=Alignment.CenterVertically) {
   Column(Modifier.weight(1f)) {Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,style=MaterialTheme.typography.bodyLarge)}
   IconButton(onClick={copyPrivateText(context,value)}) {Icon(Icons.Default.ContentCopy,"Copy "+label)}
  }}
 }}
}

/** The same issuer-provided face is used in Details and in Cards previews. */
@Composable private fun PassInformationBody(item:VaultItem,pass:PassPresentation,colors:PassPalette,membership:Boolean,loadImage:suspend(String)->ByteArray?,barcodePanel:@Composable ()->Unit) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            val headerImage=pass.logo
                            if(membership) CardLogo(pass.title,app.kura.nativecore.ItemMedia.logo(item.table,JSONObject(item.json)),loadImage)
                            else if(headerImage.isNotBlank()) PassAsset(headerImage,if(membership) "Card icon" else "Pass logo",Modifier.size(if(membership) 54.dp else 40.dp),loadImage)
                            else if(membership) Icon(Icons.Default.CreditCard,null,Modifier.size(40.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pass.title,style=MaterialTheme.typography.titleLarge)
                                if(!membership && pass.description.isNotBlank()) Text(pass.description,style=MaterialTheme.typography.bodyMedium)
                            }
                            if(!membership && pass.header.isNotEmpty()) Column(Modifier.widthIn(max=132.dp),horizontalAlignment=Alignment.End) {pass.header.forEach {Text(it.label,style=MaterialTheme.typography.labelSmall,color=colors.label);Text(it.value,style=MaterialTheme.typography.titleMedium)}}
                        }
                        if(!membership) {
                        if(passPhotoPath(item).isNotBlank()) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                if(pass.type!="boardingPass") pass.primary.forEach {Text(it.label,style=MaterialTheme.typography.labelSmall,color=colors.label);Text(it.value,style=MaterialTheme.typography.titleLarge)}
                            }
                            PassAsset(passPhotoPath(item),"Pass photo",Modifier.width(100.dp).height(128.dp),loadImage)
                        }
                        if(pass.strip.isNotBlank()) PassAsset(pass.strip,"Pass strip",Modifier.fillMaxWidth().aspectRatio(3f),loadImage,crop=true)
                        if(pass.type=="boardingPass" && pass.primary.size>=2) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {FieldGroup("",pass.primary.take(1),colors.label,true)}
                                Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.padding(horizontal=12.dp))
                                Column(Modifier.weight(1f)) {FieldGroup("",pass.primary.drop(1).take(1),colors.label,true)}
                            }
                            FieldGroup("",pass.primary.drop(2),colors.label,true)
                        } else if(membership || passPhotoPath(item).isBlank()) FieldGroup("",pass.primary,colors.label,true)
                        FieldGroup("Secondary",pass.secondary,colors.label)
                        FieldGroup("Auxiliary",pass.auxiliary,colors.label)
                        if(pass.relevantDate.isNotBlank()) Text(pass.relevantDate)
                        if(pass.expiry.isNotBlank()) Text("Expires "+pass.expiry)
                        if(pass.footer.isNotBlank()) PassAsset(pass.footer,"Pass footer",Modifier.fillMaxWidth().heightIn(min=32.dp,max=80.dp),loadImage)
                        }
                        if(membership) barcodePanel()
                    }
}

/** Credit-card geometry is independent of grid column count or decoded image size. */
const val CardAspectRatio=1.586f
@Composable internal fun CardFaceThumbnail(content:@Composable ()->Unit) {
 Layout(content={Box {content()}},modifier=Modifier.fillMaxWidth().aspectRatio(CardAspectRatio)) {measurables,constraints->
  val width=constraints.maxWidth
  val height=constraints.maxHeight
  val face=measurables.single().measure(Constraints.fixedWidth(360.dp.roundToPx()))
  val scale=minOf(width.toFloat()/face.width.coerceAtLeast(1),height.toFloat()/face.height.coerceAtLeast(1))
  layout(width,height) {
   face.placeWithLayer(((width-face.width*scale)/2).toInt(),((height-face.height*scale)/2).toInt()) {
    scaleX=scale;scaleY=scale;transformOrigin=TransformOrigin(0f,0f)
   }
  }
 }
}
@Composable private fun CardSectionPreview(item:VaultItem,open:()->Unit,loadImage:suspend(String)->ByteArray?,onLongClick:(()->Unit)?) {
 val pass=item.passPresentation ?: remember(item) {PassPresentation.from(item)}
 val colors=passPalette(pass,MaterialTheme.colorScheme)
 ElevatedCard(modifier=Modifier.fillMaxWidth().combinedClickable(onClick=open,onLongClick=onLongClick).semantics {contentDescription="Open "+item.title},
  shape=RoundedCornerShape(16.dp),colors=CardDefaults.elevatedCardColors(containerColor=colors.background,contentColor=colors.foreground)) {
  CardFaceThumbnail {
   PassInformationBody(item,pass,colors,!isImportedPass(item),loadImage) {
    if(pass.description.isNotBlank()) Text(pass.description)
    if(pass.barcode.isNotBlank()) Icon(Icons.Default.QrCode,"Barcode available")
   }
  }
 }
}
