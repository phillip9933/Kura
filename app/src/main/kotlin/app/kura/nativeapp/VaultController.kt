package app.kura.nativeapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.kura.nativecore.*
import app.kura.feature.VaultItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

class VaultController(app: Application) : AndroidViewModel(app) {
    val pending = PendingOperations()
    var autoUnlockArmed = true
    val coordinator = VaultSessionCoordinator()
    val lifecycle = AndroidVaultLifecycle(app, coordinator)
    val store = VaultStore(app)
    val state = coordinator.state
    val presentation = MutableStateFlow(PresentationSettings(theme=app.getSharedPreferences("native-settings",0).getInt("appearance",2)))
    val items = MutableStateFlow<List<VaultItem>>(emptyList())
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val autoBackupStatus=MutableStateFlow("Automatic backup is off")
    val backupPasswordReady=MutableStateFlow(false)
    private var autoBackupJob:Job?=null
    private val mutationVersion=java.util.concurrent.atomic.AtomicLong()
    private val localPrefs=app.getSharedPreferences("native-settings",0)
    private val operations = Mutex()
    private var vault: VaultStore.Opened? = null
    private var token: Generation? = null
    private val startupCleanup = viewModelScope.async(Dispatchers.IO) { CaptureFiles.clearOrphans(app.cacheDir) }
    // Run alongside authentication; never preload keys, records or open database handles.
    private val databaseRuntime = viewModelScope.async(Dispatchers.IO) {
        traced("Kura.prepareRuntime") { VaultDatabases.prepareRuntime() }
    }
    init {
        store.onMutation={queueAutomaticBackup(true)}
        viewModelScope.launch {
            state.collect { if (it !is VaultState.Unlocked) {
                items.value = emptyList();backupPasswordReady.value=false;autoBackupJob?.cancel()
                // The ViewModel observes lock even when no Activity is resumed.
                pending.clearVaultContent()
            } }
        }
    }
    suspend fun unlock(auth: suspend () -> ByteArray) {
        startupCleanup.await()
        token = coordinator.unlock(auth) { key ->
            databaseRuntime.await()
            store.open(key).also { vault = it }
        }
        presentation.value = work { traced("Kura.settings") { PresentationSettings.parse(store.settings(it)) } }
        localPrefs.edit().putInt("appearance",presentation.value.theme).apply()
        backupPasswordReady.value=work {opened->traced("Kura.backupSecret") {store.deviceSecret(opened,"auto-backup-password.enc")?.let {bytes->try {bytes.isNotEmpty()} finally {bytes.fill(0)}} ?: false}}
        refresh()
        queueAutomaticBackup(false)
    }
    suspend fun <T> work(block: suspend (VaultStore.Opened) -> T): T =
        coordinator.run(token ?: error("Vault is locked")) { operations.withLock { block(vault ?: error("Vault is locked")) } }
    suspend fun refresh() {
        val list = work { opened ->
            traced("Kura.refresh") {
            val settings=store.settings(opened)
            val order=settings.optJSONArray("nativeItemOrder")
            val ranks=(0 until (order?.length() ?: 0)).associate {order!!.getString(it) to it}
            val rows=listOf("wallets", "passes", "identities").flatMap { table ->
                store.rows(opened, table).map { raw ->
                    val json=PresentationEdits.category(table,raw,settings)
                    VaultItem(table, json.getLong("id"), json.optString(if(table == "passes") "organizationName" else "name"),
                        json.optString(when(table) { "wallets" -> "number"; "passes" -> "description"; else -> "value" }),
                        json.optString(if(table == "wallets") "category" else if(table == "passes") "type" else "cardType"),
                        json.optBoolean("isArchived"), json.toString())
                }
            }.sortedBy {ranks[it.table+":"+it.id] ?: Int.MAX_VALUE}
            val added=settings.optJSONObject("nativeAddedAt") ?: JSONObject()
            val favorites=settings.optJSONObject("nativeFavorites") ?: JSONObject()
            val live=rows.map {it.table+":"+it.id}.toSet()
            var changed=false
            for(map in listOf(added,favorites)) map.keys().asSequence().toList().filter {it !in live}.forEach {map.remove(it);changed=true}
            val initialized=settings.optBoolean("nativeAddedInitialized",false)
            live.filter { !added.has(it) }.forEach {added.put(it,if(initialized) System.currentTimeMillis() else 0L);changed=true}
            if(changed || !initialized) {
                settings.put("nativeAddedAt",added).put("nativeFavorites",favorites).put("nativeAddedInitialized",true)
                store.saveSettings(opened,settings)
            }
            presentation.value=PresentationSettings.parse(settings)
            rows
            }
        }
        if (state.value is VaultState.Unlocked) items.value = list
    }
    private inline fun <T> traced(name: String, block: () -> T): T {
        android.os.Trace.beginSection(name)
        return try { block() } finally { android.os.Trace.endSection() }
    }
    fun queueAutomaticBackup(dirty:Boolean) {
        if(dirty) {mutationVersion.incrementAndGet();localPrefs.edit().putBoolean("autoBackupDirty",true).apply()}
        viewModelScope.launch {
            autoBackupJob?.cancel()
            if(state.value !is VaultState.Unlocked) return@launch
            if(!presentation.value.preferences.bool("autoBackupEnabled",false)) {autoBackupStatus.value="Automatic backup is off";return@launch}
            if(!localPrefs.getBoolean("autoBackupDirty",false)) {autoBackupStatus.value=localPrefs.getString("lastAutoBackup","Ready").orEmpty();return@launch}
            autoBackupJob=viewModelScope.launch {
                delay(2000)
                try {automaticBackupNow()}
                catch(e:CancellationException) {throw e}
                catch(e:Exception) {autoBackupStatus.value="Backup failed: "+(e.message ?: "Choose the folder again")}
            }
        }
    }
    suspend fun setAutomaticBackupPassword(password:CharArray) {
        try {
            require(password.size>=8) {"Use at least 8 characters"}
            work {opened->
                val encoded=Charsets.UTF_8.encode(java.nio.CharBuffer.wrap(password))
                val bytes=ByteArray(encoded.remaining()).also(encoded::get)
                try {store.saveDeviceSecret(opened,"auto-backup-password.enc",bytes)}
                finally {bytes.fill(0);if(encoded.hasArray()) encoded.array().fill(0)}
            }
            backupPasswordReady.value=true
            queueAutomaticBackup(true)
        } finally {password.fill('\u0000')}
    }
    suspend fun automaticBackupNow() {
        val version=mutationVersion.get()
        autoBackupStatus.value="Creating encrypted backup"
        val name=try {work {opened->
            val settings=store.settings(opened)
            val tree=settings.optString("autoBackupUri").takeIf {it.startsWith("content://")} ?: error("Choose a backup folder")
            val bytes=store.deviceSecret(opened,"auto-backup-password.enc") ?: error("Set an automatic-backup password")
            val charsBuffer=Charsets.UTF_8.decode(java.nio.ByteBuffer.wrap(bytes))
            val chars=CharArray(charsBuffer.remaining()).also(charsBuffer::get)
            bytes.fill(0);if(charsBuffer.hasArray()) charsBuffer.array().fill('\u0000')
            val context=currentCoroutineContext()
            try {store.export(opened).use {payload->
                AutomaticBackup(File(getApplication<Application>().cacheDir,"auto-backup")).write(payload,chars,
                    settings.optInt("autoBackupRetentionCount",5).coerceIn(1,1000),
                    SafBackupDestination(getApplication(),android.net.Uri.parse(tree))) {context.ensureActive()}
            }} finally {chars.fill('\u0000')}
        }
        } catch(e:CancellationException) {autoBackupStatus.value="Backup paused until unlock";throw e}
        catch(e:Exception) {autoBackupStatus.value="Backup failed: "+(e.message ?: "Choose the folder again");throw e}
        val status="Saved "+name
        autoBackupStatus.value=status
        localPrefs.edit().putString("lastAutoBackup",status).putBoolean("autoBackupDirty",mutationVersion.get()!=version).apply()
    }
    suspend fun share(item:VaultItem,password:CharArray):List<String> = try {
        work {opened->
            val row=PresentationEdits.category(item.table,store.rows(opened,item.table,item.id).single(),store.settings(opened))
            val bytes=TransferCodec.payload(item.table,row)
            try {TransferCodec.encode(bytes,password)} finally {bytes.fill(0)}
        }
    } finally {password.fill('\u0000')}
    suspend fun pkpass(item:VaultItem):ByteArray = work {opened->
        require(item.table=="passes")
        val row=PresentationEdits.category(item.table,store.rows(opened,item.table,item.id).single(),store.settings(opened))
        val fields=runCatching {JSONObject(row.optString("fields","{}"))}.getOrDefault(JSONObject())
        val metadata=fields.optJSONObject("_kura") ?: JSONObject()
        metadata.put("section",item.section.key).put("category",item.displayCategory);fields.put("_kura",metadata);row.put("fields",fields.toString())
        val assets=linkedMapOf<String,ByteArray>()
        try {
            for(name in listOf("logo","icon","strip","thumbnail")) {
                val path=row.optString(name+"ImagePath").takeIf {it.isNotBlank() && it!="null"} ?: continue
                val raw=EncryptedMediaStorage(File(opened.directory,"media")).read(File(path).name,opened.key)
                try {
                    val bounds=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
                    android.graphics.BitmapFactory.decodeByteArray(raw,0,raw.size,bounds)
                    require(bounds.outWidth>0 && bounds.outHeight>0)
                    val options=android.graphics.BitmapFactory.Options().apply {inMutable=true;inSampleSize=1
                        while(maxOf(bounds.outWidth,bounds.outHeight)/inSampleSize>1024) inSampleSize*=2}
                    val bitmap=android.graphics.BitmapFactory.decodeByteArray(raw,0,raw.size,options) ?: error("Invalid pass image")
                    try {val output=java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);assets[name+".png"]=output.toByteArray()}
                    finally {bitmap.eraseColor(0);bitmap.recycle()}
                } finally {raw.fill(0)}
            }
            PkpassExport.write(row,assets)
        } finally {assets.values.forEach {it.fill(0)}}
    }
    suspend fun decodeTransfer(chunks:List<String>,password:CharArray):TransferRecord = try {
        work {opened->
            val oldKey=store.deviceSecret(opened,"legacy-transfer-key.enc")?.let(::SensitiveBytes)
            try {TransferCodec.decode(chunks,password,oldKey)} finally {oldKey?.close()}
        }
    } finally {password.fill('\u0000')}
    suspend fun purgeNative() {
        autoBackupJob?.cancelAndJoin();autoUnlockArmed=false
        lock()
        withContext(Dispatchers.IO) {store.purgeNative()}
        localPrefs.edit().clear().apply()
        presentation.value=PresentationSettings()
        autoBackupStatus.value="Automatic backup is off"
    }
    suspend fun edit(item: VaultItem, json: String,images:Map<String,File> = emptyMap()) {
        var committed=false
        try {work {opened->
            require(!app.kura.feature.isImportedPass(item)) {"Imported passes are read-only"}
            val row=JSONObject(json)
            images.forEach {(column,file)->require(column in app.kura.feature.editableImageColumns);require(file.canonicalFile.parentFile==File(opened.directory,"media").canonicalFile && file.isFile);if(column=="kuraLogo") ItemMedia.put(item.table,row,ItemMedia.get(item.table,row).put("logo",file.name)) else row.put(column,file.name)}
            store.update(opened,item.table,row);committed=true;store.forgetPresentation(opened,item.table,item.id)
        };refresh()} finally {if(!committed) images.values.forEach {it.delete()}}
    }
    suspend fun image(path: String): ByteArray = work { EncryptedMediaStorage(File(it.directory, "media")).read(File(path).name, it.key) }
    suspend fun attach(item:app.kura.feature.VaultItem,name:String,mime:String,bytes:ByteArray) {
        work {opened->
            val row=store.rows(opened,item.table,item.id).single();val metadata=ItemMedia.get(item.table,row)
            val files=ItemMedia.attachments(item.table,row);require(files.size<20)
            val cleanName=name.replace(Regex("[\\p{Cntrl}/\\\\]"),"_").take(200).ifBlank {"Attachment"}
            val type=mime.takeIf {it.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"))} ?: "application/octet-stream"
            val file=EncryptedMediaStorage(File(opened.directory,"media")).write(java.util.UUID.randomUUID().toString()+".bin.enc",bytes,opened.key)
            try {
                metadata.put("attachments",org.json.JSONArray(files+JSONObject().put("path",file.name).put("name",cleanName).put("mime",type).put("size",bytes.size)))
                ItemMedia.put(item.table,row,metadata);store.update(opened,item.table,row)
            } catch(e:Throwable) {file.delete();throw e}
        };refresh()
    }
    suspend fun removeAttachment(item:app.kura.feature.VaultItem,path:String) {
        work {opened->val row=store.rows(opened,item.table,item.id).single();val metadata=ItemMedia.get(item.table,row)
            metadata.put("attachments",org.json.JSONArray(ItemMedia.attachments(item.table,row).filter {it.getString("path")!=path}))
            ItemMedia.put(item.table,row,metadata);store.update(opened,item.table,row)
        };refresh()
    }
    suspend fun attachment(item:app.kura.feature.VaultItem,path:String):Pair<JSONObject,ByteArray> = work {opened->
        val row=store.rows(opened,item.table,item.id).single()
        val file=ItemMedia.attachments(item.table,row).single {it.getString("path")==path}
        file to EncryptedMediaStorage(File(opened.directory,"media")).read(path,opened.key)
    }
    suspend fun theme(value: Int) {
        require(value in 0..2)
        work { opened -> val settings = store.settings(opened); settings.put("themePreference", value); store.saveSettings(opened, settings)
            presentation.value = PresentationSettings.parse(settings)
            getApplication<Application>().getSharedPreferences("native-settings",0).edit().putInt("appearance",value).apply() }
    }
    suspend fun deletePass(item: VaultItem) { work { store.deleteRecord(it,item.table,item.id) }; refresh() }
    suspend fun deleteArchived(selection:List<VaultItem>) {
        try {work {opened->store.deleteArchived(opened,selection.map {it.table to it.id})}}
        finally {refresh()}
    }
    suspend fun preference(key:String,value:Any) {
        if(key.startsWith("renameCategory:")) {renameCategory(key.substringAfter(':'),JSONObject(value.toString()));return}
        work {opened->val settings=store.settings(opened)
            if(key=="autoBackupEnabled" && value==true) {
                val uri=settings.optString("autoBackupUri")
                require(uri.startsWith("content://") && getApplication<Application>().contentResolver.persistedUriPermissions.any {it.uri.toString()==uri && it.isReadPermission && it.isWritePermission}) {"Choose a backup folder first"}
                require(store.deviceSecret(opened,"auto-backup-password.enc")?.let {bytes->try {bytes.isNotEmpty()} finally {bytes.fill(0)}}==true) {"Set a backup password first"}
            }
            settings.put(key,value)
            if(key=="selectedCurrencyCode") settings.put("selectedCurrencySymbol",java.util.Currency.getInstance(value.toString()).symbol)
            store.saveSettings(opened,settings);presentation.value=PresentationSettings.parse(settings)
        }
        queueAutomaticBackup(false)
    }
    suspend fun saveRecord(table:String,json:String,images:Map<String,File> = emptyMap()) {
        var committed=false
        try {work {opened->
            val row=JSONObject(json)
            images.forEach { (column,file)->require(column in app.kura.feature.editableImageColumns);require(file.canonicalFile.parentFile==File(opened.directory,"media").canonicalFile && file.isFile);if(column=="kuraLogo") ItemMedia.put(table,row,ItemMedia.get(table,row).put("logo",file.name)) else row.put(column,file.name)}
            store.insert(opened,table,row);committed=true
        };refresh()} finally {if(!committed) images.values.forEach {it.delete()}}
    }
    suspend fun imageChange(target:ImageTarget,bytes:ByteArray?) {
        work {opened->
            if(target.id==-1L) {
                require(target.column in app.kura.feature.editableImageColumns)
                val media=EncryptedMediaStorage(File(opened.directory,"media"))
                val created=bytes?.let {media.validateImage(it);media.write(java.util.UUID.randomUUID().toString()+".jpg.enc",it,opened.key)}
                pending.draftImages.remove(target.column)?.delete()
                if(created!=null) pending.draftImages[target.column]=created
            } else store.setItemImage(opened,target.table,target.id,target.column,bytes)
        }
        refresh()
    }
    suspend fun favorite(item:VaultItem) {
        work {opened->
            require(store.rows(opened,item.table,item.id).isNotEmpty())
            val settings=store.settings(opened);val favorites=settings.optJSONObject("nativeFavorites") ?: JSONObject()
            val key=item.table+":"+item.id
            favorites.put(key,!favorites.optBoolean(key,false));settings.put("nativeFavorites",favorites)
            store.saveSettings(opened,settings);presentation.value=PresentationSettings.parse(settings)
        }
        queueAutomaticBackup(true)
    }
    suspend fun saveOrder(section:String,keys:List<String>) {
        work {opened->
            val settings=store.settings(opened)
            val current=listOf("wallets","passes","identities").flatMap {table->store.rows(opened,table).map {row->
                val json=PresentationEdits.category(table,row,settings)
                VaultItem(table,json.getLong("id"),"","","",json.optBoolean("isArchived"),json.toString())
            }}
            val previous=settings.optJSONArray("nativeItemOrder")
            val ranks=(0 until (previous?.length() ?: 0)).associate {previous!!.getString(it) to it}
            val ordered=current.sortedBy {ranks[it.table+":"+it.id] ?: Int.MAX_VALUE}
            settings.put("nativeItemOrder",org.json.JSONArray(app.kura.feature.mergeSectionOrder(ordered,section,keys)))
            settings.put(app.kura.feature.sectionKey(section)+"SortMode","custom")
            store.saveSettings(opened,settings);presentation.value=PresentationSettings.parse(settings)
        }
        refresh();queueAutomaticBackup(true)
    }
    private suspend fun renameCategory(section:String,request:JSONObject) {
        val old=request.getString("old");val name=request.getString("name").trim()
        require(name.isNotBlank() && name.length<=100)
        val affected=items.value.filter {it.section.key==section && it.displayCategory==old}
        work {opened->
            val settings=store.settings(opened)
            val overrides=settings.optJSONObject("nativeCategoryOverrides") ?: JSONObject()
            val sections=settings.optJSONObject("nativeCategorySections") ?: JSONObject()
            affected.forEach {overrides.put(it.table+":"+it.id,name);sections.put(it.table+":"+it.id,section)}
            settings.put("nativeCategorySections",sections)
            settings.put("nativeCategoryOverrides",overrides)
            val categories=presentation.value.preferences.categories(section).map {if(it==old) name else it}.distinct()
            settings.put(app.kura.feature.sectionKey(section)+"Categories",org.json.JSONArray(categories))
            store.saveSettings(opened,settings);presentation.value=PresentationSettings.parse(settings)
        }
        refresh();queueAutomaticBackup(false)
    }
    suspend fun archive(item: VaultItem) { work { store.archive(it, item.table, item.id, !item.archived) }; refresh() }
    suspend fun fixture() {
        check(BuildConfig.PROTOTYPE)
        work { opened ->
            if(store.rows(opened,"wallets").isEmpty()) {
                store.insert(opened,"wallets",JSONObject().put("name","Sample debit card").put("number","0000000000004242")
                    .put("network","VISA").put("expiry","1230").put("category","Debit").put("color","#183A37"))
                store.insert(opened,"wallets",JSONObject().put("name","Sample travel card").put("number","0000000000001234")
                    .put("network","Mastercard").put("expiry","0631").put("category","Credit").put("color","#25213A"))
            }
            if(store.rows(opened,"identities").isEmpty()) store.insert(opened,"identities",
                JSONObject().put("name","Sample identity").put("value","TEST-000001").put("cardType","National ID"))
            if (store.rows(opened, "passes").isEmpty()) repeat(100) { i ->
                store.insert(opened, "passes", JSONObject().put("type", "boardingPass").put("organizationName", "Kura Test Transit " + i)
                    .put("description", "Synthetic fixture").put("barcodeFormat", "PKBarcodeFormatQR").put("barcodeValue", "KURA-TEST-" + i)
                    .put("transitType", "PKTransitTypeTrain").put("fields", """{"primaryFields":[{"label":"FROM","value":"Tokyo"},{"label":"TO","value":"Kyoto"}],"backFields":[{"label":"Notice","value":"Synthetic test pass"}]}"""))
            }
        }; refresh()
    }
    suspend fun lock() { coordinator.lock(); vault = null; token = null; items.value = emptyList() }
    fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        busy.value = true; error.value = null
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "Operation failed" }
        finally { busy.value = false }
    }
    override fun onCleared() {
        pending.close()
        // ViewModel survives rotations. Process death is handled by OS; final owner disposal closes handles.
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch { lifecycle.dispose() }
    }
}
