package app.kura.nativecore

import org.json.JSONObject
import java.security.SecureRandom

data class TransferRecord(val table:String,val data:JSONObject) {
    val title get()=data.optString(if(table=="passes") "organizationName" else "name")
}
object TransferCodec {
    const val MAX_CHUNKS=100
    const val CHUNK_SIZE=1500
    const val MAX_PLAIN=110*1024
    fun payload(table:String,row:JSONObject):ByteArray {
        val type=when(table) {"wallets"->"wallet";"passes"->"pass";"identities"->"identity";else->error("Unknown item type")}
        val copy=JSONObject(row.toString())
        ItemMedia.put(table,copy,JSONObject())
        copy.keys().asSequence().toList().filter {it.endsWith("ImagePath") || it in setOf("id","orderIndex")}.forEach(copy::remove)
        return JSONObject().put("type",type).put("data",copy).toString().toByteArray().also {require(it.size<=MAX_PLAIN) {"Item is too large for QR sharing; use a backup file"}}
    }
    fun encode(data:ByteArray,password:CharArray):List<String> {
        require(data.size<=MAX_PLAIN && password.size>=8)
        val salt=ByteArray(16).also(SecureRandom()::nextBytes)
        val key=BackupCodec.derive(password,salt)
        try {
            val envelope=Envelope.encrypt(data,key).split(':')
            val chunks=envelope[1].chunked(CHUNK_SIZE);require(chunks.size in 1..MAX_CHUNKS)
            return chunks.mapIndexed {index,chunk->"v3:$index:${chunks.size}:${Envelope.encode(salt)}:${envelope[0]}:$chunk"}
        } finally {key.fill(0);salt.fill(0)}
    }
    fun decode(chunks:List<String>,password:CharArray,legacyKey:SensitiveBytes?=null):TransferRecord {
        require(chunks.isNotEmpty())
        val bytes=if(chunks.singleOrNull()?.startsWith("v1:")==true) {
            val parts=chunks.single().split(':');require(parts.size==3 && chunks.single().length<=MAX_PLAIN*2)
            require(Envelope.decode(parts[1]).size==12) {"Invalid legacy transfer nonce"}
            val key=legacyKey ?: error("This v1 transfer requires its original device key")
            key.useBytes {Envelope.decrypt(parts[1]+":"+parts[2],it)}
        } else {
            val collector=TransferCollector()
            try {
                chunks.forEach {collector.add(it)}
                require(collector.complete) {"Transfer is incomplete"}
                val first=collector.ordered().first().split(':')
                val key=BackupCodec.derive(password,Envelope.decode(first[3]),first[0]=="v3")
                try {Envelope.decrypt(first[4]+":"+collector.ordered().joinToString("") {it.split(':')[5]},key)}
                finally {key.fill(0)}
            } finally {collector.close()}
        }
        try {
            require(bytes.size<=MAX_PLAIN)
            val payload=JSONObject(bytes.toString(Charsets.UTF_8))
            val table=when(payload.getString("type")) {"wallet"->"wallets";"pass"->"passes";"identity"->"identities";else->error("Invalid transfer type")}
            val row=JSONObject(payload.getJSONObject("data").toString())
            row.keys().asSequence().toList().filter {it.endsWith("ImagePath") || it in setOf("id","orderIndex")}.forEach(row::remove)
            row.put("isArchived",false)
            for(field in listOf("fields","customFields")) if(row.opt(field) is JSONObject || row.opt(field) is org.json.JSONArray) row.put(field,row.get(field).toString())
            ItemMedia.put(table,row,JSONObject())
            require(row.optString(if(table=="passes") "organizationName" else "name").let {it.isNotBlank() && it!="null"}) {"Transfer has no item name"}
            return TransferRecord(table,row)
        } finally {bytes.fill(0)}
    }
}
/** Bounded and duplicate-idempotent. A different transfer cannot replace an in-progress collection. */
class TransferCollector(private val now:()->Long=System::nanoTime) : AutoCloseable {
    private val chunks=sortedMapOf<Int,String>()
    private var identity:String?=null
    private var started=0L
    var total:Int=0;private set
    val count get()=chunks.size
    val complete get()=total>0 && count==total
    fun add(raw:String) {
        require(raw.length<=1700)
        val parts=raw.split(':');require(parts.size==6 && parts[0] in setOf("v2","v3"))
        val index=parts[1].toInt();val expected=parts[2].toInt()
        require(expected in 1..TransferCodec.MAX_CHUNKS && index in 0 until expected)
        require(Envelope.decode(parts[3]).size==16 && Envelope.decode(parts[4]).size==12)
        require(parts[5].length in 1..TransferCodec.CHUNK_SIZE && parts[5].matches(Regex("[A-Za-z0-9+/]*={0,2}")))
        val signature=listOf(parts[0],parts[2],parts[3],parts[4]).joinToString(":")
        if(identity==null) {identity=signature;total=expected;started=now()}
        check(now()-started<=300_000_000_000L) {"Transfer expired; start scanning again"}
        require(identity==signature) {"This code belongs to a different transfer"}
        require(chunks[index]==null || chunks[index]==raw) {"Conflicting transfer chunk"}
        chunks[index]=raw
    }
    fun ordered():List<String> {check(complete);check(now()-started<=300_000_000_000L) {"Transfer expired; start scanning again"};return chunks.values.toList()}
    override fun close() {chunks.clear();identity=null;total=0;started=0}
}
