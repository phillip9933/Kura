package app.kura.nativecore

import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Encrypted, cancellable staging. No staged asset is referenced by a database until confirmation. */
class PassStagingArea private constructor(private val directory: File, private var descriptions: List<String>, private val count: Int) : AutoCloseable {
    val titles get() = descriptions
    fun read(key: SensitiveBytes): List<ParsedPass> {
        check(descriptions.isNotEmpty())
        val media = EncryptedMediaStorage(directory)
        val result = mutableListOf<ParsedPass>()
        try {
            repeat(count) { index ->
                val encoded = media.read(index.toString() + ".json.enc", key)
                val document = try { JSONObject(encoded.toString(Charsets.UTF_8)) } finally { encoded.fill(0) }
                val assets = linkedMapOf<String, ByteArray>()
                try {
                    for (column in document.getJSONObject("assets").keys()) assets[column] = media.read(index.toString() + "-" + column + ".enc", key)
                    result += ParsedPass(document.getJSONObject("record"), assets, document.getBoolean("manifest"))
                } catch (e: Throwable) { assets.values.forEach { it.fill(0) }; throw e }
            }
            return result
        } catch (e: Throwable) { result.forEach { it.close() }; throw e }
    }
    override fun close() {
        descriptions = emptyList()
        // This class creates a flat, private directory. Never follows directories or symlinks.
        directory.listFiles()?.forEach { if(it.canonicalFile.parentFile == directory.canonicalFile && it.isFile) it.delete() }
        directory.delete()
    }
    companion object {
        fun fromExport(root: File, payload: BackupPayload, key: SensitiveBytes): PassStagingArea {
            BackupCodec.validate(payload.data)
            require(payload.data.optString("scope")=="single-pass")
            require(listOf("wallets","identities","loyalties").all { (payload.data.optJSONArray(it)?.length() ?: 0)==0 })
            require((payload.data.optJSONObject("settings")?.length() ?: 0)==0)
            val rows=payload.data.getJSONArray("passes")
            require(rows.length()==1)
            val row=JSONObject(rows.getJSONObject(0).toString()).apply { remove("id");remove("orderIndex");put("isArchived",false) }
            val directory=File(root,UUID.randomUUID().toString())
            val staged=PassStagingArea(directory,listOf(row.optString("organizationName")),1)
            val media=EncryptedMediaStorage(directory)
            try {
                val assets=JSONObject()
                for(column in row.keys().asSequence().filter {it.endsWith("ImagePath")}.toList()) {
                    require(column in setOf("logoImagePath","iconImagePath","stripImagePath","thumbnailImagePath","footerImagePath","frontImagePath","backImagePath"))
                    if(row.isNull(column) || row.optString(column).isBlank()) continue
                    val name=row.getString(column)
                    require(name in payload.imageNames) {"Missing pass image"}
                    payload.consumeImage(name) { bytes ->
                        media.validateImage(bytes)
                        media.write("0-"+column+".enc",bytes,key)
                    }
                    assets.put(column,true)
                }
                val document=JSONObject().put("record",row).put("assets",assets).put("manifest",false).toString().toByteArray()
                try {media.write("0.json.enc",document,key)} finally {document.fill(0)}
                return staged
            } catch(e: Throwable) {staged.close();throw e}
        }

        fun create(root: File, passes: List<ParsedPass>, key: SensitiveBytes): PassStagingArea {
            require(passes.isNotEmpty())
            val directory = File(root, UUID.randomUUID().toString())
            val staged = PassStagingArea(directory, passes.map { it.record.optString("organizationName") }, passes.size)
            val media = EncryptedMediaStorage(directory)
            try {
                passes.forEachIndexed { index, pass ->
                    val assets = JSONObject()
                    for ((column, bytes) in pass.assets) {
                        media.validateImage(bytes)
                        media.write(index.toString() + "-" + column + ".enc", bytes, key); assets.put(column, true)
                    }
                    val document = JSONObject().put("record", pass.record).put("assets", assets).put("manifest", pass.manifestChecked).toString().toByteArray()
                    try { media.write(index.toString() + ".json.enc", document, key) } finally { document.fill(0) }
                }
                return staged
            } catch (e: Throwable) { staged.close(); throw e }
            finally { passes.forEach { it.close() } }
        }
    }
}