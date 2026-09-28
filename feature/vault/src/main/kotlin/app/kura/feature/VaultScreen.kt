package app.kura.feature

import android.graphics.Bitmap
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(items: List<VaultItem>, busy: Boolean, window: Window,
    onArchive:(VaultItem)->Unit, onImport:()->Unit,
    onRestore:()->Unit, onExport:()->Unit, onCamera:()->Unit,
    onTimeout:(Long)->Unit,
    biometrics:Boolean, onBiometrics:(Boolean)->Unit, fixture:(()->Unit)?=null,
    theme:Int=2, initialTable:String="passes", visibleTables:Set<String> = sectionNames.keys,
    onTheme:(Int)->Unit={}, onRecovery:()->Unit={}, onLiveScan:()->Unit={},
    onEdit:(VaultItem,String)->Unit={_,_->}, onDelete:(VaultItem)->Unit={}, onExportPass:(VaultItem)->Unit={},
    onOpenLink:(String)->Unit={}, loadImage:suspend(String)->ByteArray?={null},
    preferences:VaultPreferences=VaultPreferences(), onPreference:(String,Any)->Unit={_,_->}, autoLock:Long=0,
    onSaveRecord:(String,String)->Unit={_,_->}, onImage:(VaultItem,String,String)->Unit={_,_,_->},
    onRemoveImage:(VaultItem,String)->Unit={_,_->},
    onShare:(VaultItem)->Unit={}, backupStatus:String="", onBackupFolder:()->Unit={}, onBackupPassword:()->Unit={}, onBackupNow:()->Unit={}, onPurge:()->Unit={}, draftImages:Map<String,String> = emptyMap(),onDiscardDraft:()->Unit={},onPkpass:(VaultItem)->Unit={},uiMemory:VaultUiMemory?=null,backupPasswordReady:Boolean=false,onDeleteArchived:(List<VaultItem>)->Unit={},onFavorite:(VaultItem)->Unit={},onSaveOrder:suspend(String,List<String>)->Boolean={_,_->true}) {
    val memory=uiMemory ?: remember {VaultUiMemory()}
    if(!memory.initialized) {memory.table.value=initialTable;memory.initialized=true}
    var table by memory.table
    LaunchedEffect(visibleTables) {if(table !in visibleTables) table=visibleTables.first()}
    var query by remember {mutableStateOf("")}
    var category by remember {mutableStateOf("")}
    var reordering by remember {mutableStateOf(false)}
    var draftOrder by remember {mutableStateOf<List<String>>(emptyList())}
    var draftSort by remember {mutableStateOf("custom")}
    var gestureSort by remember {mutableStateOf("custom")}
    var gestureStart by remember {mutableStateOf<List<String>>(emptyList())}
    var savingOrder by remember {mutableStateOf(false)}
    var archive by remember {mutableStateOf(false)}
    var settings by remember {mutableStateOf(false)}
    var selectedKey by remember {mutableStateOf<Pair<String,Long>?>(null)}
    var editing by memory.editing
    var adding by memory.adding
    var addCardKind by memory.addCardKind
    var upcoming by remember {mutableStateOf(false)}
    var now by remember {mutableStateOf(java.time.Instant.now())}
    LaunchedEffect(Unit) {while(true) {now=java.time.Instant.now();kotlinx.coroutines.delay(30_000)}}
    var addOptions by remember {mutableStateOf(false)}
    var searchExpanded by remember {mutableStateOf(false)}
    var backgroundMenu by remember {mutableStateOf(false)}
    var reorderSelection by remember {mutableStateOf<VaultItem?>(null)}
    val reorderBounds=remember {mutableMapOf<String,androidx.compose.ui.geometry.Rect>()}
    val grid=rememberLazyGridState()
    var gridBounds by remember {mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)}
    val scope=rememberCoroutineScope()
    var contextItem by remember {mutableStateOf<VaultItem?>(null)}
    var deleteItem by remember {mutableStateOf<VaultItem?>(null)}
    val context=androidx.compose.ui.platform.LocalContext.current
    var bottomControlsPixels by remember {mutableIntStateOf(0)}
    var bottomSearchPixels by remember {mutableIntStateOf(0)}
    val density=LocalDensity.current
    var expiryDismissed by remember {mutableStateOf(false)}
    val selected=items.firstOrNull {it.table==selectedKey?.first && it.id==selectedKey?.second}
    fun select(item:VaultItem) {selectedKey=item.table to item.id}
    val categoriesInUse=remember(items,table,preferences) {
        val populated=populatedCategories(items,table)
        val configured=preferences.categories(table).filter {it in populated}
        configured+populated.filter {it !in configured}
    }
    LaunchedEffect(categoriesInUse) {if(category !in categoriesInUse) category=""}
    val upcomingDates=remember(items,now) {items.mapNotNull {item->upcomingAt(item,now)?.let {(item.table to item.id) to it}}.toMap()}
    val showUpcoming=table=="passes" && preferences.bool("showUpcomingPasses") && upcomingDates.isNotEmpty()
    LaunchedEffect(showUpcoming) {if(!showUpcoming) upcoming=false}
    val favoritesOnly=preferences.bool(sectionKey(table)+"FavoritesOnly",false)
    LaunchedEffect(table,preferences.sortMode(table),favoritesOnly) {grid.scrollToItem(0)}
    val sectionItems=remember(items,table) {items.filter {it.section.key==table && !it.archived}}
    fun beginReorder() {
        draftOrder=sortedVaultItems(sectionItems,preferences,table,false).map {it.stableKey()}
        draftSort=preferences.sortMode(table)
        reordering=true;reorderSelection=null;addOptions=false;scope.launch {grid.scrollToItem(0)}
    }
    val ordered=remember(sectionItems,preferences,table,reordering,draftOrder) { if(reordering) {
        val ranks=draftOrder.withIndex().associate {it.value to it.index}
        sectionItems.sortedBy {ranks[it.stableKey()] ?: Int.MAX_VALUE}
    } else sortedVaultItems(sectionItems,preferences,table) }
    val filtered=if(reordering) ordered else ordered.filter {
        (category.isEmpty() || it.displayCategory==category) && (!favoritesOnly || preferences.isFavorite(it)) &&
        (!upcoming || !showUpcoming || (it.table to it.id) in upcomingDates) &&
        (!preferences.search || query.isBlank() || (it.title+" "+it.subtitle+" "+it.displayCategory).contains(query,true))
    }
    val expiring=remember(items,preferences,now.atZone(java.time.ZoneId.systemDefault()).toLocalDate()) {items.filter {
        !it.archived && itemExpiry(it)?.isBefore(LocalDate.now().plusMonths(preferences.number("expiryNotificationLeadMonths",2).coerceIn(0,12).toLong()).plusDays(1))==true
    }}
    BackHandler(reordering) {if(!savingOrder) {reordering=false;reorderSelection=null;draftOrder=emptyList()}}
    LaunchedEffect(table) {reordering=false;reorderSelection=null}
    BackHandler(addOptions) {addOptions=false}
    if(settings) {
        SettingsScreen(preferences,theme,visibleTables,biometrics,autoLock,onPreference,onTheme,onBiometrics,onTimeout,
            {settings=false;archive=true},{settings=false;onExport()},{settings=false;onRestore()},{settings=false;onRecovery()},
            fixture,{settings=false},onOpenLink,backupStatus,onBackupFolder,onBackupPassword,onBackupNow,onPurge,backupPasswordReady)
    } else if(archive) {
        ArchiveScreen(items.filter {it.archived},{archive=false},::select,onArchive,onDelete,onDeleteArchived,busy)
    } else {
        val columns=preferences.columns(table)
        val searchBar: @Composable ()->Unit = {
            if(!reordering && preferences.search && preferences.searchStyle!="icon") {
                OutlinedTextField(query,{query=it},placeholder={Text(when(table) {"wallets"->"Search cards...";"passes"->"Search passes...";else->"Search identities..."})},
                    modifier=Modifier.fillMaxWidth().padding(vertical=6.dp).onSizeChanged {bottomSearchPixels=it.height},singleLine=true,shape=RoundedCornerShape(16.dp),
                    trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""}) {Icon(Icons.Default.Close,"Clear search")}})
            }
        }
        val controls: @Composable ()->Unit = {
            Column(Modifier.onSizeChanged {bottomControlsPixels=it.height}) {
            Row(Modifier.fillMaxWidth().padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                var categories by remember {mutableStateOf(false)}
                Box(Modifier.weight(1f)) {
                    Surface(onClick={categories=true},modifier=Modifier.semantics {contentDescription="Categories"},shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text((if(favoritesOnly) "★ " else "")+category.ifEmpty {"All Categories"},Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                            Icon(Icons.Default.ExpandMore,null)
                        }
                    }
                    DropdownMenu(categories,{categories=false}) {
                        DropdownMenuItem(text={Text("Favorites only")},leadingIcon={Checkbox(favoritesOnly,null)},onClick={onPreference(sectionKey(table)+"FavoritesOnly",!favoritesOnly)})
                        HorizontalDivider()
                        (listOf("")+categoriesInUse).forEach {value->
                            DropdownMenuItem(text={Text(value.ifEmpty {"All Categories"})},onClick={category=value;categories=false})
                        }

                    }
                }
                if(preferences.search && preferences.searchStyle=="icon") FilledTonalIconButton(onClick={searchExpanded=true}) {Icon(Icons.Default.Search,"Search")}
                IconButton(onClick={onPreference(sectionKey(table)+"GridColumns",columns%3+1)},modifier=Modifier.semantics {stateDescription="$columns columns"}) {
                    Icon(when(columns) {1->Icons.Outlined.ViewAgenda;2->Icons.Outlined.GridView;else->Icons.Outlined.ViewModule},when(columns) {1->"Use two columns";2->"Use three columns";else->"Use one column"})
                }
                IconButton(onClick={settings=true;addOptions=false}) {Icon(Icons.Outlined.Settings,"Settings")}
            }
            }
        }
        Scaffold(floatingActionButton={
            if(!reordering) Column(Modifier.padding(bottom=with(density) {((if(preferences.controlPosition=="bottom") bottomControlsPixels else 0)+(if(preferences.search && preferences.searchPosition=="bottom" && preferences.searchStyle!="icon") bottomSearchPixels else 0)).toDp()}),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(addOptions) {
                    SmallAddAction("Scan for Sharing or Import",Icons.Default.QrCodeScanner,!busy) {addOptions=false;onLiveScan()}
                    SmallAddAction("Import File",Icons.Outlined.FileUpload,!busy) {addOptions=false;onImport()}
                    SmallAddAction("Manual Input",Icons.Outlined.Edit,!busy) {addOptions=false;memory.manualSection.value=table;memory.manualCategory.value=defaultFormCategory(table,preferences);adding=true}
                }
                FloatingActionButton(onClick={addOptions=!addOptions},shape=RoundedCornerShape(16.dp),
                    modifier=Modifier.semantics {contentDescription=if(addOptions) "Close add options" else "Add"},
                    containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary) {
                    Icon(if(addOptions) Icons.Default.Close else Icons.Default.Add,null)
                }
            }
        },bottomBar={
            if(visibleTables.size>1 && preferences.bottomNavigation) NavigationBar(containerColor=MaterialTheme.colorScheme.background,tonalElevation=0.dp) {
                sectionNames.filterKeys {it in visibleTables}.forEach { (key,label)->
                    NavigationBarItem(table==key,{table=key;category="";query="";addOptions=false},icon={Icon(sectionIcon(key),null)},label={Text(label)},enabled=!reordering)
                }
            }
        }) {padding->
            Column(Modifier.padding(padding).fillMaxSize().padding(horizontal=16.dp).pointerInput(visibleTables,table,preferences.gestures,reordering) {
                if(preferences.gestures && !reordering) {
                    var distance=0f
                    detectHorizontalDragGestures(onDragStart={distance=0f},onHorizontalDrag={_,amount->distance+=amount},onDragEnd={
                        if(kotlin.math.abs(distance)>size.width*.2f) {
                            val tabs=sectionNames.keys.filter {it in visibleTables}
                            table=tabs[(tabs.indexOf(table)+if(distance<0) 1 else -1).coerceIn(0,tabs.lastIndex)]
                            category="";query="";addOptions=false
                        }
                    })
                }
            }) {
                if(!preferences.bottomNavigation && visibleTables.size>1) Row {
                    sectionNames.filterKeys {it in visibleTables}.forEach { (key,label)->TextButton(enabled=!reordering,onClick={table=key;category=""}) {Text(label)}}
                }
                if(preferences.searchPosition!="bottom") searchBar()
                if(!reordering && preferences.controlPosition!="bottom") controls()
                if(showUpcoming && !reordering) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(!upcoming,{upcoming=false},label={Text("All passes")})
                    FilterChip(upcoming,{upcoming=true;category=""},label={Text("Upcoming")})
                }
                if(reordering) Card(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Arrange "+sectionNames.getValue(table).lowercase(),style=MaterialTheme.typography.titleMedium)
                        Text(if(reorderSelection==null) "Hold and drag to preview a position, or tap an item then its destination." else "Tap a destination for "+reorderSelection!!.title+", or tap it again to unselect. Swipe to scroll.",style=MaterialTheme.typography.bodySmall)
                        if(preferences.bool("favoritesFirst",false)) Text("Favorites return to the top after saving.",style=MaterialTheme.typography.labelSmall)
                        ItemSortControls(draftSort,!savingOrder,{mode->
                            draftSort=mode;draftOrder=sortedVaultItems(sectionItems,preferences,table,false,mode).map {it.stableKey()};reorderSelection=null
                            scope.launch {grid.scrollToItem(0)}
                        })
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                            TextButton(enabled=!savingOrder,onClick={reordering=false;reorderSelection=null}) {Text("Cancel")}
                            Button(enabled=!savingOrder,onClick={savingOrder=true;scope.launch {try {if(onSaveOrder(table,draftOrder)) {reordering=false;reorderSelection=null}} finally {savingOrder=false}}}) {Text("Save order")}
                        }
                    }
                }
                if(!reordering && preferences.search && query.isNotBlank() && preferences.searchStyle=="icon" && !searchExpanded) InputChip(selected=true,onClick={searchExpanded=true},label={Text("Search: "+query)},trailingIcon={IconButton(onClick={query=""}) {Icon(Icons.Default.Close,"Clear search")}})
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(filtered.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {detectTapGestures(onLongPress={backgroundMenu=true})}.padding(32.dp),contentAlignment=Alignment.Center) {
                    Text(if(query.isNotBlank() || category.isNotBlank()) "No matching items." else "No items yet.\nTap '+' to add one.",
                        textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
                } else LazyVerticalGrid(GridCells.Fixed(columns),Modifier.weight(1f).onGloballyPositioned {gridBounds=it.boundsInRoot()}.pointerInput(Unit) {detectTapGestures(onLongPress={backgroundMenu=true})},state=grid,contentPadding=PaddingValues(top=8.dp,bottom=240.dp),
                    horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    items(filtered,key={it.table+it.id},contentType={it.table}) {item->
                        val mode=preferences.displayMode(table)
                        val path=previewImagePath(item,mode)
                        ReorderTile(item,reordering,reorderSelection?.stableKey()==item.stableKey(),reorderSelection==null && !savingOrder,reorderBounds,
                            select={if(!savingOrder) {if(reorderSelection==null) reorderSelection=item else {val from=reorderSelection!!;reorderSelection=null;if(from.stableKey()!=item.stableKey()) draftSort="custom";draftOrder=movedKeys(draftOrder,from.stableKey(),item.stableKey())}}},
                            begin={gestureStart=draftOrder;gestureSort=draftSort},hover={target->draftSort="custom";draftOrder=movedKeys(draftOrder,item.stableKey(),target)},end={},cancel={draftOrder=gestureStart;draftSort=gestureSort},
                            autoScroll={y->if(y<gridBounds.top+70 || y>gridBounds.bottom-70) scope.launch {grid.scrollBy(if(y<gridBounds.top+70) -28f else 28f)}},modifier=Modifier.animateItem()) {
                        Box {
                        if(!isImportedPass(item) && mode=="front" && path.isNotBlank()) ElevatedCard(modifier=Modifier.combinedClickable(onClick={select(item)},onLongClick={contextItem=item}).semantics {contentDescription="Open "+item.title}) {
                            Box(Modifier.fillMaxWidth().aspectRatio(CardAspectRatio),contentAlignment=Alignment.Center) {
                                VaultImage(path,item.title,loadImage,Modifier.fillMaxSize(),if(item.section==VaultSection.CARDS) androidx.compose.ui.layout.ContentScale.Crop else androidx.compose.ui.layout.ContentScale.Fit)
                            }
                        } else if(item.table=="passes") PassCard(item,{select(item)},loadImage,onLongClick={contextItem=item})
                        else WalletPreview(item,columns>1,{select(item)},loadImage,mode,preferences.currency,onLongClick={contextItem=item})
                        if(preferences.isFavorite(item)) Surface(Modifier.align(Alignment.BottomEnd).padding(6.dp),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.secondaryContainer) {Icon(Icons.Default.Star,"Favorite",Modifier.padding(4.dp).size(16.dp))}
                        if(preferences.bool("showExpiryIndicators",false) && item in expiring) Surface(
                            modifier=Modifier.align(Alignment.BottomStart).padding(6.dp).semantics {stateDescription=if(itemExpiry(item)?.isBefore(LocalDate.now())==true) "Expired" else "Expiring soon"},shape=RoundedCornerShape(12.dp),
                            color=if(itemExpiry(item)?.isBefore(LocalDate.now())==true) MaterialTheme.colorScheme.errorContainer else androidx.compose.ui.graphics.Color(0xFFFFB74D),contentColor=if(itemExpiry(item)?.isBefore(LocalDate.now())==true) MaterialTheme.colorScheme.onErrorContainer else androidx.compose.ui.graphics.Color(0xFF321900)) {
                            Row(Modifier.padding(horizontal=6.dp,vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Outlined.EventBusy,"Expiry warning for "+item.title,Modifier.size(16.dp))
                                if(columns==1) Text(if(itemExpiry(item)?.isBefore(LocalDate.now())==true) "Expired" else "Expiring soon",Modifier.padding(start=4.dp),style=MaterialTheme.typography.labelSmall)
                            }
                        }
                        }}
                    }
                }
                if(!reordering && preferences.controlPosition=="bottom") controls()
                if(preferences.searchPosition=="bottom") searchBar()
            }
        }
    }
    if(searchExpanded && preferences.search && preferences.searchStyle=="icon") {
        val focus=remember {androidx.compose.ui.focus.FocusRequester()}
        androidx.compose.ui.window.Dialog(onDismissRequest={searchExpanded=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,securePolicy=androidx.compose.ui.window.SecureFlagPolicy.Inherit)) {
            Surface(Modifier.fillMaxWidth().padding(20.dp),shape=RoundedCornerShape(28.dp),tonalElevation=6.dp) {
                OutlinedTextField(query,{query=it},label={Text("Search "+sectionNames.getValue(table).lowercase())},singleLine=true,modifier=Modifier.fillMaxWidth().padding(12.dp).focusRequester(focus),
                    keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={searchExpanded=false}),
                    trailingIcon={IconButton(onClick={searchExpanded=false}) {Icon(Icons.Default.Check,"Show matching items")}})
            }
        }
        LaunchedEffect(Unit) {focus.requestFocus()}
    }
    contextItem?.let {item->
        ModalBottomSheet(onDismissRequest={contextItem=null}) {
            Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
                Text(item.title,Modifier.padding(16.dp),style=MaterialTheme.typography.titleLarge)
                SettingsRow(if(preferences.isFavorite(item)) "Remove favorite" else "Add favorite","",Icons.Outlined.StarOutline,{contextItem=null;onFavorite(item)})
                SettingsRow("Sort / Reorder","",Icons.AutoMirrored.Filled.Sort,{contextItem=null;beginReorder()})
                SettingsRow("Archive","",Icons.Outlined.Archive,{contextItem=null;onArchive(item)})
                if(!isImportedPass(item)) SettingsRow("Edit","",Icons.Outlined.Edit,{contextItem=null;editing=item})
                SettingsRow(when(item.table) {"wallets"->"Copy number";"identities"->"Copy ID value";else->"Copy"},"",Icons.Outlined.ContentCopy,{
                    val json=JSONObject(item.json)
                    copyPrivateText(context,json.optString(when(item.table) {"wallets"->"number";"identities"->"value";else->"barcodeValue"}))
                    contextItem=null
                })
                SettingsRow("Delete","",Icons.Outlined.Delete,{contextItem=null;deleteItem=item})
            }
        }
    }
    deleteItem?.let {item->AlertDialog(onDismissRequest={deleteItem=null},title={Text("Delete item?")},
        text={Text("Delete "+item.title+" from the current vault? Saved backups remain unchanged.")},
        confirmButton={TextButton(onClick={deleteItem=null;onDelete(item)}) {Text("Delete")}},
        dismissButton={TextButton(onClick={deleteItem=null}) {Text("Cancel")}})}
    if(backgroundMenu) ModalBottomSheet(onDismissRequest={backgroundMenu=false}) {
        SettingsRow("Sort / Reorder","Arrange cards in this grid",Icons.AutoMirrored.Filled.Sort,{backgroundMenu=false;beginReorder()})
        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }
    selected?.let {item->
        if(item.table=="passes") PassDetailScreen(item,window,{selectedKey=null},{onArchive(item);selectedKey=null},
            {onDelete(item);selectedKey=null},{onExportPass(item);selectedKey=null},{editing=item;selectedKey=null},onOpenLink,loadImage,preferences,onImage,onRemoveImage,{onShare(item)},{onPkpass(item)},favorite=preferences.isFavorite(item),onFavorite={onFavorite(item)})
        else ItemDetailScreen(item,window,preferences,{selectedKey=null},{onArchive(item);selectedKey=null},{onDelete(item);selectedKey=null},
            {editing=item;selectedKey=null},loadImage,onImage,onRemoveImage,{onShare(item)},favorite=preferences.isFavorite(item),onFavorite={onFavorite(item)})
    }
    editing?.takeUnless(::isImportedPass)?.let { item -> RecordForm(item.table,item,preferences,preference=onPreference,dismiss={editing=null;memory.form.clear();onDiscardDraft()},memory=memory.form,loadImage=loadImage,draftImages=draftImages,
        draftImage={column,source->onImage(item.copy(id=-1),column,source)},removeDraftImage={column->onRemoveImage(item.copy(id=-1),column)}) { json -> onEdit(item,json);editing=null;memory.form.clear() } }
    if(adding && memory.manualSection.value=="wallets" && table=="wallets" && addCardKind==null) AlertDialog(onDismissRequest={adding=false},title={Text("Add card")},
        text={Column {
            TextButton(onClick={addCardKind="wallets";memory.manualCategory.value=defaultFormCategory("wallets",preferences)}) {Text("Payment-card reference")}
            TextButton(onClick={addCardKind="passes";memory.manualCategory.value="Membership"}) {Text("Gift, loyalty or membership card")}
        }},confirmButton={TextButton(onClick={adding=false}) {Text("Cancel")}})
    else if(adding) {
        val entrySection=memory.manualSection.value
        val entryCategory=memory.manualCategory.value
        val storageTable=manualStorageTable(entrySection,entryCategory)
        memory.form=memory.manualForms.getOrPut(storageTable) {FormMemory()}
        KuraDialog({adding=false;addCardKind=null;memory.clearManual();onDiscardDraft()}) { key(storageTable) {RecordForm(storageTable,null,preferences,preference=onPreference,dismiss={adding=false;addCardKind=null;memory.clearManual();onDiscardDraft()},initialSection=entrySection,memory=memory.manualForms.getOrPut(storageTable) {FormMemory()},manualCategory=entryCategory,changeKind={section,category->memory.manualSection.value=section;memory.manualCategory.value=category;addCardKind=manualStorageTable(section,category)},
            embedded=true,loadImage=loadImage,draftImages=draftImages,discardDraft=onDiscardDraft,draftImage={column,source->
                onImage(VaultItem(storageTable,-1,"New item","","",false,"{}"),column,source)
            }) {json->
            onSaveRecord(storageTable,json);adding=false;addCardKind=null;memory.clearManual()
        }}}
    }
    if(preferences.bool("isExpiryNotificationEnabled") && !expiryDismissed && expiring.isNotEmpty() && !settings && !archive) AlertDialog(onDismissRequest={expiryDismissed=true},
        title={Text("Expiry Alerts")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
            expiring.forEach {Text(it.title+" • "+itemExpiry(it),Modifier.padding(vertical=8.dp))}
        }},confirmButton={TextButton(onClick={expiryDismissed=true}) {Text("Done")}})
}
@Composable private fun SmallAddAction(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,enabled:Boolean,action:()->Unit) {
    ExtendedFloatingActionButton(onClick={if(enabled) action()},icon={Icon(icon,null)},text={Text(label)},
        modifier=Modifier.semantics {contentDescription=label},
        containerColor=MaterialTheme.colorScheme.surfaceVariant,contentColor=MaterialTheme.colorScheme.onSurfaceVariant)
}
fun sectionIcon(table:String)=when(table) {"wallets"->Icons.Outlined.CreditCard;"passes"->Icons.Outlined.ConfirmationNumber;else->Icons.Outlined.Badge}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveScreen(items:List<VaultItem>,close:()->Unit,select:(VaultItem)->Unit,restore:(VaultItem)->Unit,delete:(VaultItem)->Unit,deleteAll:(List<VaultItem>)->Unit,busy:Boolean) {
    var sort by remember {mutableStateOf("Name")}
    var descending by remember {mutableStateOf(false)}
    var menu by remember {mutableStateOf(false)}
    var query by remember {mutableStateOf("")}
    var deletion by remember {mutableStateOf<VaultItem?>(null)}
    var clearSelection by remember {mutableStateOf<List<VaultItem>?>(null)}
    BackHandler(onBack=close)
    val sorted=remember(items,sort,descending,query) {
        items.filter {(it.title+" "+it.displayCategory).contains(query,true)}.let {filtered->
            when(sort) {"Section"->filtered.sortedWith(compareBy({it.section.key},{it.title.lowercase()}));"Expiry"->filtered.sortedBy {itemExpiry(it) ?: LocalDate.MAX};else->filtered.sortedBy {it.title.lowercase()}}
        }.let {if(descending) it.reversed() else it}
    }
    Scaffold(topBar={
        TopAppBar(title={Text("Archive")},
            navigationIcon={IconButton(onClick=close) {Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back to vault")}},
            actions={
                IconButton(enabled=items.isNotEmpty() && !busy,onClick={clearSelection=items.toList()}) {Icon(Icons.Outlined.DeleteSweep,"Delete all archived items")}
                IconButton(onClick={descending=!descending}) {Icon(Icons.Default.SwapVert,"Reverse archive order")}
                Box {
                    IconButton(onClick={menu=true}) {Icon(Icons.AutoMirrored.Filled.Sort,"Sort archive")}
                    DropdownMenu(menu,{menu=false}) {
                        listOf("Name","Section","Expiry").forEach {option->
                            DropdownMenuItem(text={Text(option)},onClick={sort=option;menu=false})
                        }
                    }
                }
            })
    }) {padding->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal=16.dp)) {
            OutlinedTextField(query,{query=it},label={Text("Search archive")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            Text("Sorted by "+sort,Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.labelMedium)
            LazyColumn(Modifier.weight(1f)) {
                if(sorted.isEmpty()) item {Text("Your archive is empty.",Modifier.padding(24.dp))}
                items(sorted,key={it.table+it.id}) {item->
                    ListItem(headlineContent={Text(item.title)},supportingContent={Text(sectionNames.getValue(item.section.key)+" • "+item.displayCategory)},
                        leadingContent={Icon(sectionIcon(item.section.key),null)},modifier=Modifier.clickable {select(item)},
                        trailingContent={Row {
                            IconButton(onClick={restore(item)}) {Icon(Icons.Outlined.Unarchive,"Restore "+item.title)}
                            IconButton(onClick={deletion=item}) {Icon(Icons.Outlined.Delete,"Delete "+item.title)}
                        }})
                    HorizontalDivider()
                }
            }
        }
    }
    clearSelection?.let {selection->AlertDialog(onDismissRequest={if(!busy) clearSelection=null},title={Text("Delete all archived items?")},
        text={Text("Delete "+selection.size+" archived items across all three sections, including items hidden by search? Active items, exported backups and previous vaults are kept.")},
        confirmButton={TextButton(enabled=!busy,onClick={clearSelection=null;deleteAll(selection)}) {Text("Delete archived items")}},
        dismissButton={TextButton(onClick={clearSelection=null}) {Text("Cancel")}})}
    deletion?.let {item->AlertDialog(onDismissRequest={deletion=null},title={Text("Delete archived item?")},
        text={Text("Remove "+item.title+" from the current vault? Saved backups remain unchanged.")},
        confirmButton={TextButton(onClick={delete(item);deletion=null}) {Text("Delete")}},
        dismissButton={TextButton(onClick={deletion=null}) {Text("Cancel")}})}
}
@Composable internal fun rememberPassBarcode(item:VaultItem):Bitmap? {
    val json=remember(item.json) {JSONObject(item.json)}
    return rememberBarcode(json.optString("barcodeValue"),json.optString("barcodeFormat"))
}

fun manualStorageTable(section:String,category:String):String=when {
 section=="identities"->"identities"
 section=="wallets" && category.lowercase() in setOf("credit","debit","prepaid","cash")->"wallets"
 else->"passes"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemSortControls(mode:String,enabled:Boolean,onSort:(String)->Unit,onCustom:(()->Unit)?=null) {
 FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
  FilterChip(mode.startsWith("name"),{onSort(toggledSortMode(mode,"name"))},enabled=enabled,
   modifier=Modifier.semantics {contentDescription="Sort by name";stateDescription=if(mode=="nameDesc") "Z to A" else if(mode=="nameAsc") "A to Z" else "Not selected"},
   label={Text(if(mode=="nameDesc") "Name Z–A" else "Name A–Z")})
  FilterChip(mode.startsWith("added"),{onSort(toggledSortMode(mode,"added"))},enabled=enabled,
   modifier=Modifier.semantics {contentDescription="Sort by date added";stateDescription=if(mode=="addedAsc") "Oldest first" else if(mode=="addedDesc") "Newest first" else "Not selected"},
   label={Text(if(mode=="addedAsc") "Date added ↑" else "Date added ↓")})
  if(onCustom!=null) FilterChip(mode=="custom",onCustom,enabled=enabled,label={Text("Custom")},modifier=Modifier.semantics {contentDescription="Customize order"})
 }
 if(mode.startsWith("added")) Text(if(mode=="addedAsc") "Oldest first · unknown dates last" else "Newest first · unknown dates last",style=MaterialTheme.typography.labelSmall)
}
