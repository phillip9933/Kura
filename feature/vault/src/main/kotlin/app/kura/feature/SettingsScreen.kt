package app.kura.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(title.uppercase(),Modifier.padding(start=4.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceVariant) {Column(content=content)}
    }
}
@Composable
internal fun SettingsRow(title: String, subtitle: String, icon: ImageVector, action: (()->Unit)?=null, trailing: @Composable (()->Unit)?=null) {
    Row((if(action!=null) Modifier.clickable(onClick=action).semantics {contentDescription=title} else Modifier)
        .fillMaxWidth().heightIn(min=64.dp).padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Icon(icon,null,Modifier.size(24.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(title,style=MaterialTheme.typography.bodyLarge)
            if(subtitle.isNotBlank()) Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(trailing!=null) trailing() else if(action!=null) Icon(Icons.Default.ChevronRight,null,Modifier.size(20.dp))
    }
}
@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(preferences: VaultPreferences, theme: Int, visibleTables: Set<String>, biometrics: Boolean, autoLock: Long,
    preference:(String,Any)->Unit, onTheme:(Int)->Unit, onBiometrics:(Boolean)->Unit, onTimeout:(Long)->Unit,
    archive:()->Unit, backup:()->Unit, restore:()->Unit, recovery:()->Unit,
    fixture:(()->Unit)?, close:()->Unit, openLink:(String)->Unit,
    backupStatus:String="",backupFolder:()->Unit={},backupPassword:()->Unit={},backupNow:()->Unit={},purge:()->Unit={},backupPasswordReady:Boolean=false) {
    val scrollStates=remember {mutableMapOf<String,ScrollState>()}
    var page by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf("Settings")}
    var sectionTable by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf("wallets")}
    var autoSetup by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    var restoreOptions by remember {mutableStateOf(false)}
    fun back() {page=if(page in setOf("Manage Categories","Manage Custom Fields")) sectionNames.getValue(sectionTable)+" Settings" else "Settings"}
    var choices by remember {mutableStateOf<Triple<String,List<Pair<String,Any>>,String>?>(null)}
    var textEdit by remember {mutableStateOf<Triple<String,String,String>?>(null)}
    var renaming by remember {mutableStateOf<Triple<String,String,String>?>(null)}
    var information by remember {mutableStateOf<String?>(null)}
    fun choose(title:String,key:String,values:List<Pair<String,Any>>) {choices=Triple(title,values,key)}
    androidx.activity.compose.BackHandler {if(page=="Settings") close() else back()}
    Scaffold(topBar={TopAppBar(title={Text(page)},navigationIcon={
        IconButton(onClick={if(page=="Settings") close() else back()}) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}
    },actions={TextButton(onClick=close) {Text("Done")}})}) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(scrollStates.getOrPut(page) {ScrollState(0)}).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(18.dp)) {
            when(page) {
                "Settings" -> {
                    SettingsGroup("Data & Security") {
                        SettingsRow("Archive","Restore or permanently delete archived items",Icons.Outlined.Inventory2,archive)
                        SettingsRow("Use biometrics to unlock","Use fingerprint or face recognition when available. When off, unlock with your device PIN, pattern or password. Authentication is always required.",Icons.Outlined.Fingerprint,
                            trailing={KuraSwitch(biometrics,onBiometrics)})
                        SettingsRow("Auto-lock","After leaving Kura: "+when(autoLock) {0L->"Immediately";30000L->"30 seconds";60000L->"1 minute";else->"5 minutes"},Icons.Outlined.Timer,
                            {choose("Auto-lock","autoLock",listOf("Immediately" to 0L,"30 seconds" to 30000L,"1 minute" to 60000L,"5 minutes" to 300000L))})
                        SettingsRow("Backup & Storage","Encrypted backups and recovery",Icons.Outlined.Backup,{page="Backup & Storage"})
                    }
                    SettingsGroup("Section Management") {
                        SettingsRow("Cards Settings","Categories and custom fields",Icons.Outlined.CreditCard,{page="Cards Settings"})
                        SettingsRow("Passes Settings","Categories and custom fields",Icons.Outlined.ConfirmationNumber,{page="Passes Settings"})
                        SettingsRow("Identity Settings","Categories and custom fields",Icons.Outlined.Badge,{page="Identity Settings"})
                    }
                    SettingsGroup("App Preferences") {
                        SettingsRow("General Display","Configure theme, currency, and default tab",Icons.Outlined.Tune,{page="General Display"})
                        SettingsRow("Expiry Alerts","Configure startup expiry alerts and lead time",Icons.Outlined.NotificationsActive,{page="Expiry Alerts"})
                        SettingsRow("Navigation & Layout","Configure tabs, navigation, controls, and search",Icons.Outlined.Visibility,{page="Navigation & Layout"})
                        SettingsRow("Barcode & Scanning","Configure barcode display and QR import scanning",Icons.Outlined.ScreenRotation,{page="Barcode & Scanning"})
                    }
                    SettingsGroup("About") {
                        SettingsRow("App Version & Trademark","Kura "+appVersionLabel(),Icons.Outlined.Info,{information="Kura is an offline, privacy first pass wallet. All product names, logos, and brands belong to their respective owners."})
                        SettingsRow("GitHub & Issue Tracker","View source or report an issue",Icons.Default.Code,{openLink("https://github.com/phillip9933/Kura")})
                        SettingsRow("Buy Me a Coffee","Support development",Icons.Outlined.Coffee,{openLink("https://buymeacoffee.com/phillip9933")})
                    }
                    fixture?.let { SettingsGroup("Testing") {SettingsRow("Load synthetic fixtures","Add sample records for testing",Icons.Outlined.Science,{it();close()})} }
                }
                "General Display" -> SettingsGroup("Display") {
                    SettingsRow("App Theme",listOf("Light","Dark","System")[theme],Icons.Outlined.Brightness6,
                        {choose("App Theme","themePreference",listOf("Light" to 0,"Dark" to 1,"System" to 2))})
                    SettingsRow("Default Currency",preferences.currency,Icons.Outlined.Payments,
                        {choose("Default Currency","selectedCurrencyCode",listOf("EUR","USD","GBP","JPY","BDT","CAD","AUD","CHF","CNY","INR","KRW","BRL","MXN","SEK","NOK","DKK","NZD","SGD","HKD").map {it to it})})
                    SettingsRow("Default Tab on Launch",sectionNames.values.elementAt(preferences.number("defaultScreenIndex",1).coerceIn(0,2)),Icons.Outlined.Home,
                        {choose("Default Tab on Launch","defaultScreenIndex",sectionNames.keys.withIndex().filter {it.value in visibleTables}.map {sectionNames.getValue(it.value) to it.index})})
                }
                "Navigation & Layout" -> {
                    SettingsGroup("Navigation") {
                        SettingsRow("Visible Tabs","Keep at least one section visible",Icons.Outlined.Visibility)
                        FlowRow(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            sectionNames.forEach { (table,label) ->
                                FilterChip(table in visibleTables,{if(table !in visibleTables || visibleTables.size>1) preference(
                                    when(table) {"wallets"->"showPaymentsTab";"passes"->"showPassesTab";else->"showIdentityTab"},table !in visibleTables)},label={Text(label)})
                            }
                        }
                        SettingsToggle("Show Bottom Navigation","Show the section bar","showBottomNavigationBar",preferences,preference,Icons.Outlined.Navigation)
                        SettingsToggle("Keep Favorites First","Pin favorites above the chosen sort order","favoritesFirst",preferences,preference,Icons.Outlined.StarOutline,false)
                        SettingsToggle("Gesture Navigation","Swipe horizontally between visible sections","gestureNavigationEnabled",preferences,preference,Icons.Default.Swipe)
                        SettingsChoices("Control Row Position", "Choose where the controls appear", Icons.Default.SwapVert,
                            preferences.controlPosition,listOf("top" to "Top","bottom" to "Bottom")) {preference("controlRowPosition",it)}
                    }
                    SettingsGroup("Search") {
                        SettingsToggle("Show Search","Search all sections","isPassSearchEnabled",preferences,preference,Icons.Default.Search)
                        if(preferences.search) SettingsChoices("Search Style", "Bar stays visible; button opens a search bubble", Icons.Default.Search,
                            preferences.searchStyle,listOf("alwaysOn" to "Search Bar","icon" to "Search Button")) {preference("passSearchStyle",it)}
                        if(preferences.search && preferences.searchStyle!="icon") SettingsChoices("Search Position", "Choose where the search bar appears", Icons.Default.SwapVert,
                            preferences.searchPosition,listOf("top" to "Top","bottom" to "Bottom")) {preference("searchBarPosition",it)}
                    }
                }
                "Barcode & Scanning" -> SettingsGroup("Barcode Display") {
                    SettingsRow("Default Barcode Orientation",if(preferences.barcodeFlipped) "Flipped" else "Default",Icons.Outlined.ScreenRotation,
                        {preference("defaultBarcodeOrientation",if(preferences.barcodeFlipped) "defaultOrientation" else "flipped")})
                    SettingsToggle("Max Brightness on Barcode View","Temporarily maximize brightness for fullscreen barcodes","maxBrightnessOnBarcodeView",
                        preferences,preference,Icons.Outlined.BrightnessHigh,false)
                }
                "Expiry Alerts" -> SettingsGroup("Expiry notifications") {
                    SettingsToggle("Enable Expiry Alerts","Show expiring items when the vault opens","isExpiryNotificationEnabled",preferences,preference,Icons.Outlined.NotificationsActive)
                    SettingsToggle("Show Expiry Indicators","Mark expiring items on cards, independently of startup alerts","showExpiryIndicators",preferences,preference,Icons.Outlined.EventBusy,false)
                    SettingsRow("Alert Lead Time",preferences.number("expiryNotificationLeadMonths",2).toString()+" months",Icons.Outlined.Event,
                        {choose("Alert Lead Time","expiryNotificationLeadMonths",(0..12).map { "$it months" to it })})
                }
                "Backup & Storage" -> {
                    SettingsGroup("Automatic Backup") {
                        SettingsRow("Enable Auto-Backup","Save encrypted backups automatically after changes",Icons.Outlined.Backup,
                            trailing={KuraSwitch(preferences.bool("autoBackupEnabled",false),{
                                if(it) autoSetup=true else preference("autoBackupEnabled",false)
                            },Modifier.semantics {contentDescription="Enable Auto-Backup"})})
                        if(preferences.bool("autoBackupEnabled",false)) {
                            SettingsRow("Backup Location",preferences.text("autoBackupPath").ifBlank {"Choose a folder"},Icons.Outlined.Folder,backupFolder)
                            SettingsRow("Change Backup Password",if(backupPasswordReady) "Used to unlock future automatic backups" else "Set a password to resume backups",Icons.Outlined.Lock,backupPassword)
                            SettingsRow("Backup Retention","Keep the latest "+preferences.number("autoBackupRetentionCount",5)+" automatic backups",Icons.Default.History,
                                {choose("Backup Retention","autoBackupRetentionCount",listOf(1,3,5,10,20,50,100).map {it.toString() to it})})
                            Text(backupStatus,Modifier.padding(16.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    SettingsGroup("Your Data") {
                        SettingsRow("Create Backup","Choose where to save an encrypted backup file",Icons.Outlined.Backup,backup)
                        SettingsRow("Restore Backup","Restore a backup file or recover a previous vault",Icons.Outlined.Restore,{restoreOptions=true})
                        SettingsRow("Delete All Data","Erase this native vault from the device. Exported backups are kept.",Icons.Outlined.DeleteForever,purge)
                    }
                }
                "Manage Categories" -> CategorySettings(preferences,sectionTable,preference,
                    {renaming=Triple(sectionTable,it,it)},
                    {textEdit=Triple("Edit Categories",sectionKey(sectionTable)+"Categories",preferences.categories(sectionTable).joinToString("\n"))})
                "Manage Custom Fields" -> CustomFieldSettings(preferences,sectionTable,preference)
                else -> {
                    val table=sectionNames.entries.first {page==it.value+" Settings"}.key
                    val prefix=sectionKey(table)
                    if(table=="passes") SettingsGroup("Upcoming") {
                        SettingsToggle("Show Upcoming","Show future and time-sensitive passes when available","showUpcomingPasses",preferences,preference,Icons.Outlined.Event)
                    }
                    SettingsGroup("Card Display") {
                        SettingsRow("Card Display",if(preferences.displayMode(table)=="front") "Front Image" else "Virtual card",Icons.Outlined.Style,
                            {choose("Card Display",prefix+"GridDisplayMode",listOf("Front Image" to "front","Virtual card" to "virtualCards"))})
                    }
                    SettingsGroup("Organization") {
                        SettingsRow("Manage Categories","Add, rename, or reorder categories",Icons.Outlined.Category,{sectionTable=table;page="Manage Categories"})
                        SettingsRow("Manage Custom Fields","Choose additional fields for this section",Icons.Outlined.DynamicForm,{sectionTable=table;page="Manage Custom Fields"})
                    }
                }
            }
        }
    }
    if(autoSetup) AlertDialog(onDismissRequest={autoSetup=false},title={Text("Set up automatic backups")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Choose a folder and password before enabling automatic backups. Keep the password somewhere safe; it is required to restore these files.")
            SettingsRow("Backup Location",preferences.text("autoBackupPath").ifBlank {"Choose a folder"},Icons.Outlined.Folder,backupFolder)
            SettingsRow("Change Backup Password",if(backupPasswordReady) "Password configured" else "Set your backup password",Icons.Outlined.Lock,backupPassword)
            Text("Keep the latest "+preferences.number("autoBackupRetentionCount",5)+" backups. You can change retention after setup.")
        }},confirmButton={TextButton(enabled=backupPasswordReady && preferences.text("autoBackupUri").startsWith("content://"),
            onClick={preference("autoBackupEnabled",true);autoSetup=false}) {Text("Enable backups")}},
        dismissButton={TextButton(onClick={autoSetup=false}) {Text("Cancel")}})
    if(restoreOptions) AlertDialog(onDismissRequest={restoreOptions=false},title={Text("Restore Backup")},
        text={Column {
            SettingsRow("Choose backup file","Open an encrypted .wbk file",Icons.Outlined.FolderOpen,{restoreOptions=false;restore()})
            SettingsRow("Previous vaults and recovery","Inspect saved vaults on this device",Icons.Default.History,{restoreOptions=false;recovery()})
        }},confirmButton={TextButton(onClick={restoreOptions=false}) {Text("Cancel")}})
    choices?.let { (title,values,key) ->
        AlertDialog(onDismissRequest={choices=null},title={Text(title)},text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) { values.forEach { (label,value) ->
                val current=when(key) {
                    "themePreference"->theme.toString(); "autoLock"->autoLock.toString()
                    "defaultScreenIndex"->preferences.number(key,1).toString()
                    "selectedCurrencyCode"->preferences.currency
                    "expiryNotificationLeadMonths"->preferences.number(key,2).toString()
                    "autoBackupRetentionCount"->preferences.number(key,5).toString()
                    else->preferences.text(key,if(key.endsWith("GridDisplayMode")) "front" else "")
                }
                Row(Modifier.fillMaxWidth().clickable {when(key) {"themePreference"->onTheme(value as Int);"autoLock"->onTimeout(value as Long);else->preference(key,value)};choices=null}.semantics {selected=current==value.toString();role=Role.RadioButton}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=current==value.toString(),onClick=null)
                    Text(label,Modifier.padding(start=12.dp))
                }

            }}
        },confirmButton={TextButton(onClick={choices=null}) {Text("Cancel")}})
    }
    textEdit?.let { (title,key,initial) ->
        var text by remember(key) {mutableStateOf(initial)}
        AlertDialog(onDismissRequest={textEdit=null},title={Text(title)},text={OutlinedTextField(text,{text=it},minLines=4,maxLines=10)},
            confirmButton={TextButton(onClick={preference(key,JSONArray(text.lines().map(String::trim).filter(String::isNotBlank).distinct()).toString());textEdit=null}) {Text("Save")}},
            dismissButton={TextButton(onClick={textEdit=null}) {Text("Cancel")}})
    }
    renaming?.let { (section,old,initial)->
        var name by remember(section,old) {mutableStateOf(initial)}
        AlertDialog(onDismissRequest={renaming=null},title={Text("Rename category")},text={OutlinedTextField(name,{name=it},label={Text("Category name")})},
            confirmButton={TextButton(enabled=name.isNotBlank() && name.length<=100,onClick={preference("renameCategory:"+section,JSONObject().put("old",old).put("name",name).toString());renaming=null}) {Text("Rename")}},
            dismissButton={TextButton(onClick={renaming=null}) {Text("Cancel")}})
    }
    information?.let {AlertDialog(onDismissRequest={information=null},title={Text("Kura")},text={Text(it)},confirmButton={TextButton(onClick={information=null}) {Text("Close")}})}
}
@Composable
private fun SettingsToggle(title:String,subtitle:String,key:String,prefs:VaultPreferences,set:(String,Any)->Unit,icon:ImageVector,default:Boolean=true) {
    SettingsRow(title,subtitle,icon,action={set(key,!prefs.bool(key,default))},trailing={KuraSwitch(prefs.bool(key,default),{set(key,it)},Modifier.semantics {contentDescription=title})})
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomFieldSettings(preferences:VaultPreferences,table:String,set:(String,Any)->Unit) {
    var adding by remember {mutableStateOf(false)}
    var editing by remember {mutableStateOf<CustomField?>(null)}
    var deleting by remember {mutableStateOf<CustomField?>(null)}
    val fields=preferences.customFields(table)
    fun save(values:List<CustomField>) {set(sectionKey(table)+"CustomFieldSchemas",JSONArray().apply {
        values.forEach {put(JSONObject().put("name",it.name).put("dataType",it.dataType).put("dateFormat",it.dateFormat))}
    }.toString())}
    SettingsGroup("Custom Fields") {
        if(fields.isEmpty()) Text("Add fields you want to see when creating items in this section.",Modifier.padding(16.dp))
        fields.forEach {field->SettingsRow(field.name,customTypeLabel(field),Icons.Outlined.TextFields,{editing=field},
            trailing={IconButton(onClick={deleting=field}) {Icon(Icons.Outlined.Delete,"Delete "+field.name)}})}
        SettingsRow("Add Custom Field","Text, number, currency, or date",Icons.Outlined.DynamicForm,{adding=true})
    }
    if(adding || editing!=null) {
        val original=editing
        var name by remember(original) {mutableStateOf(original?.name.orEmpty())}
        var type by remember(original) {mutableStateOf(original?.dataType ?: "text")}
        var dateFormat by remember(original) {mutableStateOf(original?.dateFormat ?: "dd/MM/yy")}
        fun cancel() {adding=false;editing=null}
        AlertDialog(onDismissRequest=::cancel,title={Text(if(original==null) "Add Custom Field" else "Edit Custom Field")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(name,{name=it},label={Text("Field name")},enabled=original==null)
            if(original!=null) Text("The name is kept so existing values remain linked.",style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                customFieldTypes.forEach {value->FilterChip(type==value,{type=value},label={Text(value.replaceFirstChar {it.uppercase()})})}
            }
            if(type=="date") {
                Text("Date format")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    customDateFormats.forEach {format->FilterChip(dateFormat==format,{dateFormat=format},
                        label={Text(when(format) {"MM/yy"->"MM/YY";"dd/MM/yy"->"DD/MM/YY";else->"YYYY-MM-DD"})})}
                }
            }
            if(type=="currency") Text("Uses your default currency ("+preferences.currency+").")
        }},confirmButton={TextButton(enabled=name.isNotBlank() && (original!=null || fields.none {it.name.equals(name.trim(),true)}),
            onClick={
                val next=CustomField(name.trim(),type,dateFormat)
                save(if(original==null) fields+next else fields.map {if(it==original) next else it});cancel()
            }) {Text(if(original==null) "Add" else "Save")}},dismissButton={TextButton(onClick=::cancel) {Text("Cancel")}})
    }
    deleting?.let {field->
        AlertDialog(onDismissRequest={deleting=null},title={Text("Delete Custom Field?")},text={Text("Remove this field from future forms? Existing values are retained.")},
            confirmButton={TextButton(onClick={save(fields-field);deleting=null}) {Text("Delete")}},dismissButton={TextButton(onClick={deleting=null}) {Text("Cancel")}})
    }
}

@Composable private fun appVersionLabel():String {
 val context=androidx.compose.ui.platform.LocalContext.current
 return remember {val info=context.packageManager.getPackageInfo(context.packageName,0)
  info.versionName.orEmpty()+" ("+androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)+")"}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SettingsChoices(title:String,subtitle:String,icon:ImageVector,value:String,options:List<Pair<String,String>>,change:(String)->Unit) {
 Column {
  SettingsRow(title,subtitle,icon)
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,bottom=12.dp)) {
   options.forEachIndexed {index,(stored,label)->
    SegmentedButton(selected=value==stored,onClick={change(stored)},shape=SegmentedButtonDefaults.itemShape(index,options.size),
     modifier=Modifier.semantics {contentDescription=title+": "+label}) {Text(label)}
   }
  }
 }
}
