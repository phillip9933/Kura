package app.kura.feature

import android.view.Window
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject

private val paymentFields=linkedMapOf("name" to "Card name","number" to "Card number","expiry" to "Expiry","network" to "Card network",
    "issuer" to "Issuer","category" to "Category","cardtype" to "Card type","spends" to "Spending","rewards" to "Rewards",
    "annualFeeWaiver" to "Annual fee waiver","maxlimit" to "Credit limit","billdate" to "Billing day of month","color" to "Card color")
private val identityFields=linkedMapOf("name" to "Full name","value" to "Document number","cardType" to "Category","expiry_date" to "Expiry date","color" to "Card color")
private val passFields=linkedMapOf("organizationName" to "Name / organization","description" to "Description","type" to "Pass layout",
    "barcodeValue" to "Barcode value","barcodeFormat" to "Barcode format","barcodeAltText" to "Alternative text","expiry_date" to "Expiry date",
    "logoText" to "Logo text","relevantDate" to "Relevant date","transitType" to "Transit type","backgroundColor" to "Background color",
    "foregroundColor" to "Text color","labelColor" to "Label color")
fun moneyValue(value:String,currencyCode:String):String = runCatching {
    val selectedCurrency = java.util.Currency.getInstance(currencyCode)
    java.text.NumberFormat.getCurrencyInstance().apply {
        currency = selectedCurrency
        maximumFractionDigits = selectedCurrency.defaultFractionDigits.coerceAtLeast(0)
        minimumFractionDigits=maximumFractionDigits
        roundingMode=java.math.RoundingMode.HALF_UP
    }.format(value.toBigDecimal())
}.getOrDefault(value)
fun recordFields(table:String)=when(table) {"wallets"->paymentFields;"passes"->passFields;else->identityFields}

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
fun RecordForm(table:String,item:VaultItem?,preferences:VaultPreferences,dismiss:()->Unit,initialSection:String=item?.section?.key ?: table,draftImages:Map<String,String> = emptyMap(),draftImage:(String,String)->Unit={_,_->},discardDraft:()->Unit={},memory:FormMemory?=null,loadImage:suspend(String)->ByteArray?={null},manualCategory:String?=null,changeKind:((String,String)->Unit)?=null,embedded:Boolean=false,removeDraftImage:(String)->Unit={},preference:(String,Any)->Unit={_,_->},save:(String)->Unit) {
    if(item!=null && isImportedPass(item)) return
    val retained=memory ?: remember {FormMemory()}
    retained.initialize(table,item,initialSection,preferences)
    if(item==null && manualCategory!=null && (retained.category.value!=manualCategory || retained.section.value!=initialSection)) {
        retained.section.value=initialSection;retained.category.value=manualCategory
        if(table!="passes") retained.values[if(table=="wallets") "category" else "cardType"]=manualCategory
    }
    val original=retained.original
    var formSection by retained.section
    var formCategory by retained.category
    val fields=recordFields(table)
    val values=retained.values
    val groups=retained.groups
    val conversion=remember(item) {runCatching {customFieldMap(original.optString("customFields","{}"))}}
    val legacyArray=table!="passes" && original.optString("customFields").trim().startsWith("[")
    var groupRevision by retained.revision
    val originalCustom=remember(item) {if(table=="passes") groups.optJSONObject("customFields") ?: JSONObject()
        else conversion.getOrDefault(JSONObject())}
    val currentCategory=if(table=="passes") formCategory else values[if(table=="wallets") "category" else "cardType"].orEmpty()
    val configured=preferences.customFields(formSection)
    val suggestions=suggestedFields(formSection,currentCategory)
    val schema=(configured+suggestions).distinctBy {it.name}
    val originalGroups=remember(item) {runCatching {JSONObject(original.optString("fields","{}"))}.getOrDefault(JSONObject())}
    val customValues=retained.customValues
    val customKeys=(configured.map {it.name}+suggestions.map {it.name}+
        originalCustom.keys().asSequence().toList()+customValues.filterValues {it.isNotBlank()}.keys).distinct()
    var manageCustom by remember {mutableStateOf(false)}
    LaunchedEffect(formSection,formCategory) {
        val classification=formSection+":"+formCategory
        if(item==null && table=="passes" && retained.defaultClassification!=classification) {
            retained.defaultClassification=classification
            values["type"]=initialPassLayout(formSection,formCategory)
            values["transitType"]=when {formCategory.equals("Transit",true)->"PKTransitTypeTrain";formCategory.equals("Boarding Pass",true)->"PKTransitTypeAir";else->""}
        }
    }
    val titleField=if(table=="passes") "organizationName" else "name"
    val structuredValid=remember(groupRevision,originalGroups) {validStructuredFields(groups,originalGroups)}
    val valid=values[titleField]?.isNotBlank()==true && (table!="passes" || structuredValid) && fields.keys.all {key->
        val value=values[key].orEmpty();value==original.optString(key).takeUnless {it=="null"}.orEmpty() || validRecordField(key,value)
    } && schema.filter {it.name in customKeys}.all {field->
        val value=customValues[field.name].orEmpty()
        value==originalCustom.optString(field.name) || validCustomValue(field,value)
    }
    val classificationControls: @Composable ()->Unit = {
                if(table=="passes" || changeKind!=null) {
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("wallets","passes","identities").forEach {section->
                            FilterChip(formSection==section,{if(changeKind!=null) changeKind(section,defaultFormCategory(section,preferences)) else {formSection=section;formCategory=if(section=="wallets") preferences.categories(section).firstOrNull {it=="Membership"} ?: defaultFormCategory(section,preferences) else defaultFormCategory(section,preferences)}},label={Text(sectionNames.getValue(section))})
                        }
                    }
                    var categoryMenu by remember {mutableStateOf(false)}
                    Box {
                        OutlinedSelector("Category",formCategory,{categoryMenu=true}) {
                            IconButton(onClick={categoryMenu=true}) {Icon(Icons.Default.ExpandMore,"Choose Category")}
                        }
                        DropdownMenu(categoryMenu,{categoryMenu=false}) {
                            preferences.categories(formSection).forEach {category->
                                DropdownMenuItem(text={Text(category)},onClick={if(changeKind!=null) changeKind(formSection,category) else formCategory=category;categoryMenu=false})
                            }
                        }
                    }
                }
    }
    val content: @Composable ()->Unit = {
        Scaffold(topBar={TopAppBar(title={Text(if(item==null) "Manual Input" else "Edit item")},navigationIcon={
            IconButton(onClick=dismiss) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Cancel")}
        },actions={OutlinedButton(shape=RoundedCornerShape(14.dp),enabled=valid,onClick={
            val row=JSONObject(original.toString())
            fields.keys.forEach {row.put(it,values[it]?.takeIf(String::isNotBlank) ?: JSONObject.NULL)}
            if(table=="wallets" && values["expiry"].orEmpty()!=original.optString("expiry").takeUnless {it=="null"}.orEmpty()) row.put("expiry",values["expiry"]?.takeIf {it.isNotBlank()}?.let(::paymentExpiry) ?: JSONObject.NULL)
            val custom=JSONObject(originalCustom.toString())
            customKeys.forEach {key->val value=customValues[key].orEmpty()
                if(value!=originalCustom.optString(key) && (value.isNotBlank() || originalCustom.has(key))) custom.put(key,value)
            }
            if(table=="passes") {
                groups.put("customFields",custom)
                val metadata=groups.optJSONObject("_kura") ?: JSONObject()
                metadata.put("section",formSection).put("category",formCategory)
                groups.put("_kura",metadata)
                row.put("fields",structuredFieldsForSave(groups,originalGroups).toString())
                if(item==null) row.put("sourceType","manual")
            } else if(!legacyArray) row.put("customFields",custom.toString())
            else retained.customArray?.let {array->
                val copy=org.json.JSONArray(array.toString())
                val existing=(0 until copy.length()).mapNotNull {i->copy.optJSONObject(i)?.let {it.optString("key").ifBlank {it.optString("name").ifBlank {it.optString("label")}}}}.toSet()
                customKeys.filter {it !in existing && customValues[it].orEmpty().isNotBlank()}.forEach {key->copy.put(JSONObject().put("name",key).put("value",customValues[key]))}
                row.put("customFields",copy.toString())
            }
            app.kura.nativecore.ItemMedia.put(table,row,app.kura.nativecore.ItemMedia.get(table,original))
            retained.removedImages.forEach {if(it=="kuraLogo") app.kura.nativecore.ItemMedia.put(table,row,app.kura.nativecore.ItemMedia.get(table,row).put("logo","")) else row.put(it,JSONObject.NULL)}
            save(row.toString())
        }) {Text("Save")}})}) {padding->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(item==null) {Text(sectionNames.getValue(formSection),style=MaterialTheme.typography.titleLarge);classificationControls()}
                val cardEditor=formSection=="wallets" && (item==null || !isImportedPass(item))
                val preview=mediaPreview(table,original,draftImages,retained.removedImages)
                if(cardEditor) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Box {
                            CardLogo(values[titleField].orEmpty(),app.kura.nativecore.ItemMedia.logo(table,preview),loadImage)
                            MediaEditMenu("logo",app.kura.nativecore.ItemMedia.logo(table,preview).isNotBlank(),{draftImage("kuraLogo","picker")},{draftImage("kuraLogo","camera")},{retained.removedImages.add("kuraLogo");removeDraftImage("kuraLogo")},Modifier.align(Alignment.BottomEnd))
                        }
                        OutlinedTextField(values[titleField].orEmpty(),{values[titleField]=it},label={Text(fields.getValue(titleField))},shape=RoundedCornerShape(14.dp),modifier=Modifier.weight(1f))
                    }
                }
                if(table=="passes") {
                    if(!cardEditor) RecordFieldInput(titleField,fields.getValue(titleField),values[titleField].orEmpty(),preferences,formSection) {values[titleField]=it}
                    val bitmap=rememberBarcode(values["barcodeValue"].orEmpty(),values["barcodeFormat"].orEmpty())
                    if(cardEditor && bitmap!=null) Card(colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color.White)) {Image(bitmap.asImageBitmap(),"Barcode preview",Modifier.fillMaxWidth().height(140.dp).padding(12.dp))}
                    OutlinedTextField(values["barcodeValue"].orEmpty(),{values["barcodeValue"]=it},label={Text("Barcode value")},shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth(),trailingIcon={Row {
                        IconButton(onClick={draftImage("barcodeValue","picker")}) {Icon(Icons.Outlined.PhotoLibrary,"Read barcode from photo")}
                        IconButton(onClick={draftImage("barcodeValue","camera")}) {Icon(Icons.Outlined.QrCodeScanner,"Scan barcode with camera")}
                    }})
                    RecordFieldInput("barcodeFormat",fields.getValue("barcodeFormat"),values["barcodeFormat"].orEmpty(),preferences,formSection) {values["barcodeFormat"]=it}
                }
                Text("Details",style=MaterialTheme.typography.titleMedium)
                if(item!=null) classificationControls()
                val basic=basicFormFields(table,formSection,currentCategory).filter {(changeKind==null || it !in setOf("category","cardType")) && it !in setOf("barcodeValue","barcodeFormat") && (!(cardEditor || table=="passes") || it!=titleField)}
                basic.forEach {key->key(key) {
                    RecordFieldInput(key,fields.getValue(key),values[key].orEmpty(),preferences,formSection) {values[key]=it}
                }}
                ItemColorControl(fields.keys.filter {it in setOf("color","backgroundColor","foregroundColor","labelColor")},values) {key,value->values[key]=value}
                if(legacyArray) retained.customArray?.let {CustomArrayEditor(it,groupRevision,schema,preferences.currency) {groupRevision++}}
                    ?: Text("The imported custom-field data is malformed and is retained unchanged.")
                if(customKeys.isNotEmpty() && !legacyArray) Text("Custom fields",style=MaterialTheme.typography.titleMedium)
                val legacyNames=retained.customArray?.let {array->(0 until array.length()).mapNotNull {i->array.optJSONObject(i)?.let {it.optString("key").ifBlank {it.optString("name").ifBlank {it.optString("label")}}}}.toSet()}.orEmpty()
                customKeys.filter {!legacyArray || it !in legacyNames}.forEach {key->
                    ConfiguredFieldInput(schema.find {it.name==key} ?: CustomField(key,"text"),customValues[key].orEmpty(),preferences.currency) {customValues[key]=it}
                }
                OutlinedButton(onClick={manageCustom=true},shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text("Add custom field")
                }
                if(manageCustom) AlertDialog(onDismissRequest={manageCustom=false},title={Text("Custom fields for "+sectionNames.getValue(formSection))},
                    text={Column(Modifier.verticalScroll(rememberScrollState())) {CustomFieldSettings(preferences,formSection,preference)}},
                    confirmButton={TextButton(onClick={manageCustom=false}) {Text("Done")}})
                val displayed=(item ?: VaultItem(table,-1,values[titleField].orEmpty(),"","",false,"{}")).copy(json=preview.toString())
                ItemImages(displayed,loadImage,{_,column,source->draftImage(column,source)}, {_,column->retained.removedImages.add(column);removeDraftImage(column)},editable=true)
                if(retained.removedImages.isNotEmpty()) TextButton(onClick={retained.removedImages.clear()}) {Text("Undo image removals")}

            }
        }
    }
    if(embedded) content() else KuraDialog(dismiss,content)
}

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
fun ItemDetailScreen(item:VaultItem,window:Window,preferences:VaultPreferences,dismiss:()->Unit,archive:()->Unit,delete:()->Unit,edit:()->Unit,
    loadImage:suspend(String)->ByteArray?,image:(VaultItem,String,String)->Unit,removeImage:(VaultItem,String)->Unit,share:()->Unit={},favorite:Boolean=false,onFavorite:()->Unit={}) {
    val json=remember(item) {JSONObject(item.json)}
    var barcode by remember {mutableStateOf(false)}
    var deletion by remember {mutableStateOf(false)}
    val context=androidx.compose.ui.platform.LocalContext.current
    KuraDialog(dismiss) {
        Scaffold(topBar={TopAppBar(title={Text(item.title,maxLines=1)},navigationIcon={IconButton(onClick=dismiss) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Close")}},
            actions={ItemActionsMenu("More item actions",favorite,item.archived,onFavorite,edit,archive,{deletion=true},share) })}) {padding->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(item.section==VaultSection.CARDS) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {CardLogo(item.title,app.kura.nativecore.ItemMedia.logo(item.table,json),loadImage);Text(item.title,style=MaterialTheme.typography.titleLarge)}
                else WalletPreview(item,false,{},loadImage,"virtualCards",preferences.currency)
                if(item.subtitle.isNotBlank()) {
                    val bitmap=rememberBarcode(item.subtitle,"QR_CODE")
                    Card(onClick={barcode=true},colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color.White)) {
                        bitmap?.let {Image(it.asImageBitmap(),"Item barcode",Modifier.fillMaxWidth().height(160.dp).padding(16.dp))}
                        Text("Tap to view fullscreen",Modifier.padding(16.dp),color=androidx.compose.ui.graphics.Color.Black)
                    }
                }
                Card {
                    Column(Modifier.padding(16.dp)) {
                        recordFields(item.table).forEach { (key,label)->
                            val value=json.optString(key).takeUnless {it.isBlank() || it=="null"} ?: return@forEach
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(if(key in setOf("spends","annualFeeWaiver","maxlimit")) moneyValue(value,preferences.currency) else value)}
                                IconButton(onClick={copyPrivateText(context,value)}) {Icon(Icons.Outlined.ContentCopy,"Copy "+label)}
                            }
                        }
                        for((label,value) in customFieldEntries(json.optString("customFields","{}"))) {Text(label,style=MaterialTheme.typography.labelMedium);Text(customDisplayValue((preferences.customFields(item.section.key)+suggestedFields(item.section.key,item.displayCategory)).find {it.name==label},value,preferences.currency),Modifier.padding(bottom=12.dp))}
                    }
                }
                ItemImages(item,loadImage,image,removeImage)
                ItemAttachments(item,image)

            }
        }
        if(deletion) AlertDialog(onDismissRequest={deletion=false},title={Text("Delete item?")},text={Text("Remove this item from the current vault?")},
            confirmButton={TextButton(onClick={deletion=false;delete()}) {Text("Delete item")}},
            dismissButton={TextButton(onClick={deletion=false}) {Text("Cancel")}})
    }
    if(barcode) BarcodeScreen(item.title,item.subtitle,"QR_CODE",window,preferences,{barcode=false})
}


@Composable
fun ItemImages(item:VaultItem,load:suspend(String)->ByteArray?,image:(VaultItem,String,String)->Unit,remove:(VaultItem,String)->Unit,editable:Boolean=false) {
    val json=remember(item) {JSONObject(item.json)}
    var enlarged by remember {mutableStateOf<Pair<String,String>?>(null)}
    var removing by remember {mutableStateOf<String?>(null)}
    val slots=itemImageSlots(item)
    val existing=slots.filter {it.third.isNotBlank()}
    val shown=if(editable || existing.isEmpty()) slots else existing
    if(shown.isNotEmpty()) Card {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(isImportedPass(item)) "Pass images" else "Card images",style=MaterialTheme.typography.titleMedium)
            shown.chunked(2).forEach {pair->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                pair.forEach {(column,label,path)->Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(label,style=MaterialTheme.typography.labelMedium)
                    Box(Modifier.fillMaxWidth().aspectRatio(1.586f).clipToBounds(),contentAlignment=Alignment.Center) {
                        Surface(Modifier.fillMaxSize(),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                            if(path.isNotBlank()) Box(Modifier.clickable {enlarged=path to label}.semantics {contentDescription="View "+label}) {VaultImage(path,label,load,Modifier.fillMaxSize())}
                            else Box(contentAlignment=Alignment.Center) {Icon(Icons.Outlined.Photo,null)}
                        }
                        if(editable || existing.isEmpty()) MediaEditMenu(label,path.isNotBlank(),{image(item,column,"picker")},{image(item,column,"camera")},{removing=column},Modifier.align(Alignment.BottomEnd).padding(4.dp))
                    }
                }}
                if(pair.size==1) Spacer(Modifier.weight(1f))
            }}
        }
    }
    enlarged?.let { (path,label)->KuraDialog({enlarged=null}) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.safeDrawingPadding().padding(16.dp)) {
            IconButton(onClick={enlarged=null}) {Icon(Icons.Default.Close,"Close image")}
            var zoom by remember {mutableFloatStateOf(1f)}
            var x by remember {mutableFloatStateOf(0f)}
            var y by remember {mutableFloatStateOf(0f)}
            val transform=rememberTransformableState {scale,pan,_->
                zoom=(zoom*scale).coerceIn(1f,5f)
                x=(x+pan.x).coerceIn(-800f*(zoom-1),800f*(zoom-1))
                y=(y+pan.y).coerceIn(-800f*(zoom-1),800f*(zoom-1))
            }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().transformable(transform),contentAlignment=Alignment.Center) {
                VaultImage(path,label,load,Modifier.fillMaxSize().graphicsLayer {scaleX=zoom;scaleY=zoom;translationX=x;translationY=y})
            }
            TextButton(onClick={zoom=1f;x=0f;y=0f}) {Text("Reset zoom")}
        }}
    }}
    removing?.let {column->AlertDialog(onDismissRequest={removing=null},title={Text("Remove image?")},text={Text("Remove this image from the item?")},
        confirmButton={TextButton(onClick={remove(item,column);removing=null}) {Text("Remove")}},dismissButton={TextButton(onClick={removing=null}) {Text("Cancel")}})}
}

val editableImageColumns=setOf("frontImagePath","backImagePath","stripImagePath","thumbnailImagePath","footerImagePath","kuraLogo")
fun itemImageSlots(item:VaultItem):List<Triple<String,String,String>> {
 val row=JSONObject(item.json)
 val slots=listOf("frontImagePath" to "Front image","backImagePath" to "Back image","thumbnailImagePath" to "Portrait","stripImagePath" to "Strip image","footerImagePath" to "Footer image")
 return slots.map {(column,label)->Triple(column,label,row.optString(column).takeUnless {it=="null"}.orEmpty())}.filter {
  it.third.isNotBlank() || (!isImportedPass(item) && it.first in setOf("frontImagePath","backImagePath"))
 }
}
