package app.kura.nativecore

import android.content.Context
import android.util.AtomicFile
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Owns native generation files. Migration is explicit backup import; old application storage is never accessed. */
class VaultStore internal constructor(
    private val context: Context,
    private val writePointer: (java.io.FileOutputStream, ByteArray) -> Unit
) {
    constructor(context: Context) : this(context, { output, bytes -> output.write(bytes) })

    private val root = File(context.noBackupFilesDir, "native-vault").apply { mkdirs() }
    private val pointer = AtomicFile(File(root, "active"))
    private val mutex = Mutex()
    var onMutation:()->Unit = {}
    private fun changed(vault:Opened) {if(active()?.canonicalFile==vault.directory.canonicalFile) onMutation()}
    data class Opened(val directory: File, val rooms: RoomVault, val key: SensitiveBytes) : VaultResources {
        override suspend fun close() = rooms.close()
    }
    fun active(): File? {
        if (!pointer.baseFile.exists() && !File(pointer.baseFile.path + ".bak").exists()) return null
        val name = pointer.openRead().use { Envelope.readBounded(it, 100).toString(Charsets.US_ASCII) }
        require(name.matches(Regex("[0-9a-f-]{36}")))
        return File(root, name).also { require(File(it, "snapshot.complete").isFile) }
    }
    private fun password(key: SensitiveBytes) = SensitiveBytes(key.useBytes { Envelope.encode(it).toByteArray(Charsets.UTF_8) })
    suspend fun open(key: SensitiveBytes): Opened = withContext(Dispatchers.IO) {
        mutex.withLock {
            val existing = active()
            val dir: File = existing ?: File(root, UUID.randomUUID().toString())
            val rooms = password(key).use {
                if (existing == null) NewDatabases.create(context, dir, it)
                else VaultDatabases.open(context, dir, it)
            }
            try {
                if (existing == null) publish(dir)
                Opened(dir, rooms, key)
            } catch (e: Throwable) { rooms.close(); throw e }
        }
    }
    private fun publish(dir: File) {
        require(dir.parentFile?.canonicalFile == root.canonicalFile && File(dir, "snapshot.complete").isFile)
        active()?.let { File(it, "generation.valid").writeText("1") }
        File(dir, "generation.valid").outputStream().use { it.write(byteArrayOf(49)); it.fd.sync() }
        VerifiedAtomicWrite.write(pointer,dir.name.toByteArray(Charsets.US_ASCII),writePointer)
    }

    fun settings(vault: Opened): JSONObject {
        val file = File(vault.directory, "settings.json.enc")
        if(!file.isFile) return JSONObject()
        val bytes = EncryptedMediaStorage(vault.directory).read(file.name, vault.key)
        try { return JSONObject(bytes.toString(Charsets.UTF_8)) } finally { bytes.fill(0) }
    }
    private fun secretFile(directory:File,name:String):AtomicFile {
        require(name in setOf("auto-backup-password.enc","legacy-transfer-key.enc"))
        require(directory.canonicalFile.parentFile==root.canonicalFile)
        return AtomicFile(File(directory,name))
    }
    fun deviceSecret(vault:Opened,name:String):ByteArray? {
        val file=secretFile(vault.directory,name)
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return null
        val encoded=file.openRead().use {Envelope.readBounded(it,16*1024)}
        try {return vault.key.useBytes {Envelope.decrypt(encoded.toString(Charsets.UTF_8),it)}}
        finally {encoded.fill(0)}
    }
    private fun writeSecret(directory:File,key:SensitiveBytes,name:String,bytes:ByteArray) {
        require(bytes.size<=8192)
        val encoded=key.useBytes {Envelope.encrypt(bytes,it).toByteArray()}
        try {VerifiedAtomicWrite.write(secretFile(directory,name),encoded)} finally {encoded.fill(0)}
    }
    fun saveDeviceSecret(vault:Opened,name:String,bytes:ByteArray)=writeSecret(vault.directory,vault.key,name,bytes)
    fun saveSettings(vault: Opened, value: JSONObject) {
        val bytes = value.toString().toByteArray()
        val encoded = try { vault.key.useBytes { Envelope.encrypt(bytes, it).toByteArray() } } finally { bytes.fill(0) }
        val atomic = AtomicFile(File(vault.directory, "settings.json.enc"))
        try { VerifiedAtomicWrite.write(atomic,encoded);changed(vault) }
        finally { encoded.fill(0) }
    }
    data class GenerationInfo(val name: String, val timestamp: Long, val current: Boolean, val recoverable: Boolean)
    fun generations(): List<GenerationInfo> {
        val current = active()
        return root.listFiles().orEmpty().filter { it.isDirectory && it.parentFile?.canonicalFile == root.canonicalFile && it.name.matches(Regex("[0-9a-f-]{36}")) }
            .map { GenerationInfo(it.name, it.lastModified(), it == current,
                it == current || File(it,"generation.valid").isFile || File(it,"restore.validated").isFile) }.sortedByDescending { it.timestamp }
    }
    suspend fun validateRecovery(name: String, key: SensitiveBytes): File {
        require(generations().any { it.name == name && it.recoverable })
        val dir = File(root, name)
        val rooms = password(key).use { VaultDatabases.open(context, dir, it) }
        try { for(table in listOf("wallets","passes","identities")) rows(Opened(dir, rooms, key), table) }
        finally { rooms.close() }
        File(dir,"restore.validated").writeText("1")
        return dir
    }
    fun discardGeneration(name: String) {
        require(generations().any { it.name == name && !it.current })
        val dir = File(root, name).canonicalFile
        require(dir.parentFile == root.canonicalFile && dir != active()?.canonicalFile)
        val files = dir.walkBottomUp().toList()
        require(files.all { it.canonicalPath == dir.path || it.canonicalPath.startsWith(dir.path + File.separator) })
        files.forEach { check(it.delete()) { "Could not remove generation file" } }
    }

    /** Call only after the coordinator has closed every native handle. Never touches app_flutter. */
    fun purgeNative() {
        val base=root.canonicalFile
        require(base.parentFile==context.noBackupFilesDir.canonicalFile && base.name=="native-vault") {"Unsafe native root"}
        val files=base.walkBottomUp().toList()
        require(files.all {it.canonicalFile==base || it.canonicalPath.startsWith(base.path+File.separator)}) {"Unsafe native path"}
        files.forEach {check(it.delete()) {"Could not remove native vault data"}}
        root.mkdirs()
    }

    private fun db(vault: Opened, table: String): SupportSQLiteDatabase = when (table) {
        "wallets" -> vault.rooms.wallets.openHelper.writableDatabase
        "passes" -> vault.rooms.passes.openHelper.writableDatabase
        "identities" -> vault.rooms.identities.openHelper.writableDatabase
        else -> error("Unknown table")
    }
    private fun encrypted(table: String, column: String): Boolean = when(table) {
        "wallets" -> column !in setOf("id", "orderIndex", "isArchived", "frontImagePath", "backImagePath")
        "passes" -> column in setOf("organizationName", "description", "logoText", "barcodeValue", "barcodeAltText", "relevantDate", "expiry_date", "fields")
        "identities" -> column in setOf("name", "value", "cardType", "expiry_date", "customFields")
        else -> false
    }
    fun rows(vault: Opened, table: String, id: Long? = null): List<JSONObject> {
        val rows = mutableListOf<JSONObject>()
        val database=db(vault,table)
        val cursor=if(id==null) database.query("SELECT * FROM "+table+" ORDER BY orderIndex")
            else database.query("SELECT * FROM "+table+" WHERE id=? ORDER BY orderIndex",arrayOf(id))
        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                val json = JSONObject()
                for (i in 0 until cursor.columnCount) {
                    val name = cursor.getColumnName(i)
                    val value: Any = when {
                        cursor.isNull(i) -> JSONObject.NULL
                        name == "isArchived" -> cursor.getInt(i) != 0
                        name == "id" || name == "orderIndex" -> cursor.getLong(i)
                        encrypted(table, name) -> {
                            val bytes = vault.key.useBytes { LegacyColumnCodec.decrypt(cursor.getString(i), it)!! }
                            try { bytes.toString(Charsets.UTF_8) } finally { bytes.fill(0) }
                        }
                        else -> cursor.getString(i)
                    }
                    json.put(name, value)
                }
                rows.add(json)
            }
        }
        return rows
    }
    fun insert(vault: Opened, table: String, row: JSONObject) = write(vault, table, row, false)
    fun update(vault: Opened, table: String, row: JSONObject) = write(vault, table, row, true)
    private fun exactInteger(value: Any): Long {
        require(value is Number && value.toString().matches(Regex("-?[0-9]+"))) { "Invalid integer" }
        return value.toString().toLong()
    }
    private fun write(vault: Opened, table: String, row: JSONObject, update: Boolean) {
        val database = db(vault, table)
        val columns = mutableListOf<String>()
        database.query("PRAGMA table_info(" + table + ")").use { while(it.moveToNext()) columns.add(it.getString(1)) }
        for (name in row.keys()) require(name in columns) { "Unknown record field: " + name }
        val present = columns.filter { row.has(it) }
        require(present.isNotEmpty())
        val values = present.map<String, Any?> { name ->
            val value = row.opt(name)
            when {
                value == null || value == JSONObject.NULL -> null
                name == "isArchived" -> if (value is Boolean) if(value) 1 else 0 else exactInteger(value).also { require(it in 0..1) }
                name == "id" || name == "orderIndex" -> exactInteger(value).also { if(name == "orderIndex") require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) }
                else -> {
                    require(value is String) { "Record field must be text: " + name }
                    if (name == "fields" || name == "customFields") {
                        if (value.isNotEmpty()) require(value.trim().startsWith("{") || value.trim().startsWith("["))
                        if (value.trim().startsWith("{")) JSONObject(value)
                        if (value.trim().startsWith("[")) JSONArray(value)
                    }
                    if (encrypted(table, name)) {
                        val bytes = value.toByteArray()
                        try { vault.key.useBytes { Envelope.encrypt(bytes, it) } } finally { bytes.fill(0) }
                    } else value
                }
            }
        }.toTypedArray()
        if(update) {
            val id = exactInteger(row.get("id"))
            val assignments = present.indices.filter { present[it] != "id" }
            require(assignments.isNotEmpty())
            database.execSQL("UPDATE " + table + " SET " + assignments.joinToString(",") { present[it] + "=?" } + " WHERE id=?",
                (assignments.map { values[it] } + id).toTypedArray())
            database.query("SELECT changes()").use { check(it.moveToFirst() && it.getInt(0) == 1) { "Record no longer exists" } }
        } else database.execSQL("INSERT INTO " + table + " (" + present.joinToString(",") + ") VALUES (" + present.joinToString(",") { "?" } + ")", values)
        changed(vault)
    }
    fun archive(vault: Opened, table: String, id: Long, archived: Boolean) {
        db(vault, table).execSQL("UPDATE " + table + " SET isArchived=? WHERE id=?", arrayOf(if(archived) 1 else 0, id))
        changed(vault)
    }

    fun forgetPresentation(vault:Opened,table:String,id:Long,includeOrder:Boolean=false) {
        val settings=settings(vault);val identity=table+":"+id;var dirty=false
        val maps=listOf("nativeCategoryOverrides","nativeCategorySections")+if(includeOrder) listOf("nativeFavorites","nativeAddedAt") else emptyList()
        for(name in maps) settings.optJSONObject(name)?.let {if(it.has(identity)) {it.remove(identity);dirty=true}}
        if(includeOrder) settings.optJSONArray("nativeItemOrder")?.let {order->
            val kept=(0 until order.length()).map {order.getString(it)}.filter {it!=identity}
            if(kept.size!=order.length()) {settings.put("nativeItemOrder",JSONArray(kept));dirty=true}
        }
        if(dirty) saveSettings(vault,settings)
    }
    fun deleteRecord(vault:Opened,table:String,id:Long) {
        forgetPresentation(vault,table,id,true)
        db(vault,table).execSQL("DELETE FROM "+table+" WHERE id=?",arrayOf(id));changed(vault)
    }
    /** Snapshot-scoped deletion: restored records and newly archived records are excluded. */
    fun deleteArchived(vault:Opened,selection:List<Pair<String,Long>>) {
        require(selection.all {it.first in setOf("wallets","passes","identities") && it.second>=0})
        val targets=selection.distinct().filter {(table,id)->
            db(vault,table).query("SELECT id FROM "+table+" WHERE id=? AND isArchived=1",arrayOf(id)).use {it.moveToFirst()}
        }
        if(targets.isEmpty()) return
        // Clear stale overlays before IDs can be reused, in one atomic settings write.
        val identities=targets.map {(table,id)->table+":"+id}.toSet()
        val preferences=settings(vault)
        for(name in listOf("nativeCategoryOverrides","nativeCategorySections","nativeFavorites","nativeAddedAt")) preferences.optJSONObject(name)?.let {map->identities.forEach(map::remove)}
        preferences.optJSONArray("nativeItemOrder")?.let {order->
            preferences.put("nativeItemOrder",JSONArray((0 until order.length()).map {order.getString(it)}.filter {it !in identities}))
        }
        saveSettings(vault,preferences)
        for((table,records) in targets.groupBy {it.first}) {
            val database=db(vault,table)
            database.beginTransaction()
            try {
                records.forEach {(_,id)->database.execSQL("DELETE FROM "+table+" WHERE id=? AND isArchived=1",arrayOf(id))}
                database.setTransactionSuccessful()
            } finally {database.endTransaction()}
            changed(vault)
        }
        // Shared media and recovery generations are deliberately retained, as with single-item deletion.
    }
    fun setItemImage(vault:Opened,table:String,id:Long,column:String,bytes:ByteArray?) {
        require(column in setOf("frontImagePath","backImagePath"))
        val row=rows(vault,table,id).singleOrNull() ?: error("Item no longer exists")
        var created:File?=null
        try {
            if(bytes!=null) {
                val media=EncryptedMediaStorage(File(vault.directory,"media"));media.validateImage(bytes)
                created=media.write(UUID.randomUUID().toString()+".jpg.enc",bytes,vault.key)
            }
            row.put(column,created?.name ?: JSONObject.NULL)
            update(vault,table,row)
        } catch(e:Throwable) {created?.delete();throw e}
    }
    fun deletePass(vault: Opened, id: Long) {
        // Delete only the selected native record; retain assets that other passes may reference.
        db(vault,"passes").execSQL("DELETE FROM passes WHERE id=?",arrayOf(id))
    }
    fun export(vault: Opened, passId: Long? = null): BackupPayload {
        val selected = passId?.let { rows(vault,"passes",it).also { records -> require(records.size==1) { "Pass no longer exists" } } }
        val data = JSONObject().put("version", "4.0").put("timestamp", System.currentTimeMillis()).put("settings", JSONObject())
        if(passId!=null) data.put("scope","single-pass")
        val settingsFile = File(vault.directory, "settings.json.enc")
        if (passId == null && settingsFile.isFile) {
            val settingsBytes = EncryptedMediaStorage(vault.directory).read(settingsFile.name, vault.key)
            try { data.put("settings", JSONObject(settingsBytes.toString(Charsets.UTF_8))) } finally { settingsBytes.fill(0) }
        }
        val currentSettings=settings(vault)
        val order=currentSettings.optJSONArray("nativeItemOrder")
        val ranks=(0 until (order?.length() ?: 0)).associate {order!!.getString(it) to it}
        val images = linkedMapOf<String, String>()
        try {
            for (table in listOf("wallets", "passes", "identities")) {
                val array = JSONArray()
                for (raw in if(selected==null) rows(vault,table) else if(table=="passes") selected else emptyList()) {
                    val row=PresentationEdits.category(table,raw,currentSettings)
                    ranks[table+":"+row.optLong("id")]?.let {row.put("orderIndex",it)}
                    for (column in row.keys().asSequence().filter { it.endsWith("ImagePath") }.toList()) {
                        if (row.isNull(column) || row.optString(column).isEmpty()) continue
                        val name = File(row.getString(column)).name
                        val archiveName = name.removeSuffix(".enc")
                        if (!images.containsKey(archiveName)) images[archiveName] = name
                        row.put(column, archiveName)
                    }
                    ItemMedia.mapPaths(table,row) {path->val name=path.removeSuffix(".enc");images[name]=path;name}
                    array.put(row)
                }
                data.put(table, array)
            }
            return BackupPayload(data, emptyMap(), images.keys, { name -> EncryptedMediaStorage(File(vault.directory, "media")).read(images.getValue(name), vault.key) })
        } catch(e: Throwable) { throw e }
    }
    /** Stages and validates all three databases and assets. Caller must confirm before activate. */
    suspend fun stage(
        payload: BackupPayload,
        key: SensitiveBytes,
        onMissingImage: (() -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        // Strict by default. Recovery callers must disclose omissions before activation.
        var missingImages = 0
        fun imagePath(path: String): Any {
            val entry = payload.imageEntry(path)
            if (entry != null) return entry.removeSuffix(".enc") + ".enc"
            requireNotNull(onMissingImage) { "Missing referenced image" }.invoke()
            missingImages++
            return JSONObject.NULL
        }
        BackupCodec.validate(payload.data)
        val dir = File(root, UUID.randomUUID().toString())
        val rooms = password(key).use { NewDatabases.create(context, dir, it) }
        val staged = Opened(dir, rooms, key)
        try {
            val settings = payload.data.optJSONObject("settings") ?: JSONObject()
            for (name in settings.keys()) {
                val value = settings.get(name)
                val stringList=value is JSONArray && (0 until value.length()).all {value.opt(it) is String}
                val nativeMap=name in setOf("nativeCategoryOverrides","nativeCategorySections","nativeFavorites","nativeAddedAt") && value is JSONObject && value.keys().asSequence().all { id->
                    id.matches(Regex("(wallets|passes|identities):[0-9]+")) && when(name) {
                        "nativeFavorites" -> value.opt(id) is Boolean
                        "nativeAddedAt" -> runCatching {exactInteger(value.get(id))>=0}.getOrDefault(false)
                        else -> value.opt(id) is String
                    }
                }
                require(value is String || value is Number || value is Boolean || value == JSONObject.NULL || stringList || nativeMap) { "Invalid backup setting" }
            }
            val settingsBytes = settings.toString().toByteArray()
            try { EncryptedMediaStorage(dir).write("settings.json.enc", settingsBytes, key) } finally { settingsBytes.fill(0) }
            val media = EncryptedMediaStorage(File(dir, "media"))
            val attachments=mutableMapOf<String,Long>()
            val requiredImages=mutableSetOf<String>()
            for(table in listOf("wallets","passes","identities")) payload.data.optJSONArray(table)?.let {rows->
                for(i in 0 until rows.length()) {
                    val row=rows.getJSONObject(i)
                    ItemMedia.attachments(table,row).forEach {file->
                        val path=file.getString("path").removeSuffix(".enc");val size=file.getLong("size")
                        require(attachments[path]==null || attachments[path]==size);attachments[path]=size
                    }
                    ItemMedia.references(table,row).filter {it.second}.forEach {requiredImages.add(it.first.removeSuffix(".enc"))}
                    row.keys().asSequence().filter {it.endsWith("ImagePath") && !row.isNull(it) && row.optString(it).isNotBlank()}.forEach {requiredImages.add(File(row.getString(it)).name.removeSuffix(".enc"))}
                }
            }
            for (name in payload.imageNames) { payload.consumeImage(name) { bytes ->
                BackupCodec.safeImageName(name)
                val normalized=name.removeSuffix(".enc")
                attachments[normalized]?.let {require(bytes.size.toLong()==it) {"Attachment size mismatch"}}
                if(normalized !in attachments || normalized in requiredImages) media.validateImage(bytes)
                media.write(name.removeSuffix(".enc") + ".enc", bytes, key)
            } }
            for (table in listOf("wallets", "passes", "identities")) {
                val rows = payload.data.optJSONArray(table) ?: JSONArray()
                for (i in 0 until rows.length()) {
                    ensureActive()
                    val row = JSONObject(rows.getJSONObject(i).toString())
                    for (field in listOf("fields", "customFields")) if (row.opt(field) is JSONObject || row.opt(field) is JSONArray) row.put(field, row.get(field).toString())
                    for (column in row.keys().asSequence().filter { it.endsWith("ImagePath") }.toList()) {
                        if (row.isNull(column) || row.optString(column).isEmpty()) continue
                        row.put(column, imagePath(row.getString(column)))
                    }
                    ItemMedia.mapPaths(table,row) {path->
                        val name=path.removeSuffix(".enc");require(name in payload.imageNames || name+".enc" in payload.imageNames) {"Missing attachment or logo"};name+".enc"
                    }
                    insert(staged, table, row)
                }
                check(rows.length() == rows(staged, table).size)
            }
            val loyalties = payload.data.optJSONArray("loyalties") ?: JSONArray()
            for (i in 0 until loyalties.length()) {
                val old = loyalties.getJSONObject(i)
                val row = JSONObject().put("type", "storeCard").put("organizationName", old.optString("loyaltyName"))
                    .put("barcodeValue", old.optString("loyaltyNumber")).put("barcodeFormat", "PKBarcodeFormatQR")
                    .put("sourceType", "legacy").put("fields", "{}").put("orderIndex", old.optInt("orderIndex", i))
                if (!old.isNull("color")) row.put("backgroundColor", old.getString("color"))
                for (column in listOf("frontImagePath", "backImagePath")) if (!old.isNull(column) && old.optString(column).isNotEmpty()) {
                    row.put(column, imagePath(old.getString(column)))
                }
                insert(staged, "passes", row)
            }
            if (missingImages > 0) {
                val warning = missingImages.toString().toByteArray(Charsets.US_ASCII)
                try { EncryptedMediaStorage(dir).write("restore-warnings.enc", warning, key) }
                finally { warning.fill(0) }
            }
            File(dir, "restore.validated").writeText("1")
        } finally { withContext(NonCancellable) { rooms.close() } }
        // Reopen through Room validation before permitting publication.
        password(key).use { VaultDatabases.open(context, dir, it).close() }
        dir
    }
    fun missingRestoreImages(directory: File, key: SensitiveBytes): Int {
        require(directory.canonicalFile.parentFile == root.canonicalFile)
        if (!File(directory, "restore-warnings.enc").exists()) return 0
        val bytes = EncryptedMediaStorage(directory).read("restore-warnings.enc", key)
        try { return bytes.toString(Charsets.US_ASCII).toInt().also { require(it >= 0) } }
        finally { bytes.fill(0) }
    }
    fun preserveDeviceSecrets(vault:Opened,staged:File) {
        require(staged.canonicalFile.parentFile==root.canonicalFile && File(staged,"restore.validated").isFile)
        for(name in listOf("auto-backup-password.enc","legacy-transfer-key.enc")) {
            val bytes=deviceSecret(vault,name) ?: continue
            try {writeSecret(staged,vault.key,name,bytes)} finally {bytes.fill(0)}
        }
    }
    suspend fun activate(staged: File) = withContext(Dispatchers.IO) {
        mutex.withLock { require(File(staged, "restore.validated").isFile); publish(staged) }
    }
    /** Called only after confirmation; transaction rolls back records and removes newly staged files on failure. */
    fun importPasses(vault: Opened, passes: List<ParsedPass>) {
        val database = db(vault, "passes")
        val files = mutableListOf<File>()
        database.beginTransaction()
        try {
            val media = EncryptedMediaStorage(File(vault.directory, "media"))
            for (pass in passes) {
                val row = JSONObject(pass.record.toString())
                for ((column, bytes) in pass.assets) {
                    media.validateImage(bytes)
                    val name = UUID.randomUUID().toString() + ".png.enc"
                    files += media.write(name, bytes, vault.key); row.put(column, name)
                }
                insert(vault, "passes", row)
            }
            database.setTransactionSuccessful()
        } catch (e: Throwable) { files.forEach { it.delete() }; throw e }
        finally { database.endTransaction() }
    }
}