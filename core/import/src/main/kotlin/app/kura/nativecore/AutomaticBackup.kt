package app.kura.nativecore

import java.io.*
import java.security.MessageDigest
import java.util.UUID

data class BackupDocument(val id:String,val name:String, val isFile: Boolean = true)
interface BackupDestination {
    fun list():List<BackupDocument>
    fun create(name:String):BackupDocument
    fun write(id:String):OutputStream
    fun read(id:String):InputStream
    fun rename(id:String,name:String):BackupDocument
    fun delete(id:String)
}
/** Retention occurs only after a new encrypted file is completely written and read-back verified. */
class AutomaticBackup(private val scratch:File) {
    companion object {
        private val pattern=Regex("Kura_autobackup_([0-9]+)\\.wbk")
        fun sequence(name:String)=pattern.matchEntire(name)?.groupValues?.get(1)?.toLongOrNull()
        fun prepare(file: File, payload: BackupPayload, password: CharArray, checkActive: () -> Unit) {
            file.outputStream().use {output->
                val checked=object:FilterOutputStream(output) {
                    override fun write(b:Int) {checkActive();out.write(b)}
                    override fun write(b:ByteArray,off:Int,len:Int) {checkActive();out.write(b,off,len)}
                    override fun close() {flush()}
                }
                StreamingBackup.write(payload,checked,password);output.fd.sync()
            }
        }

        fun publish(file: File, retention: Int, destination: BackupDestination, checkActive: () -> Unit = {}): String {
            require(retention in 1..1000)
            var pending: BackupDocument? = null
            var committed = false
            try {
                val documents = destination.list()
                val previous = documents.filter { it.isFile && sequence(it.name) != null }
                val occupiedNames = documents.mapTo(hashSetOf()) { it.name }
                var next = previous.maxOfOrNull { sequence(it.name)!! } ?: 0L
                var name: String
                do {
                    next = Math.addExact(next, 1L)
                    name = "Kura_autobackup_" + next.toString().padStart(4, '0') + ".wbk"
                } while (name in occupiedNames)
                pending=destination.create(name+".pending-"+UUID.randomUUID())
                destination.write(pending.id).use {output->file.inputStream().use {input->
                    val buffer=ByteArray(64*1024)
                    try {while(true) {checkActive();val count=input.read(buffer);if(count<0) break;output.write(buffer,0,count)};output.flush()}
                    finally {buffer.fill(0)}
                }}
                fun digest(input:InputStream):ByteArray=input.use {
                    val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(64*1024);var count=0L
                    try {while(true) {checkActive();val n=it.read(buffer);if(n<0) break;count+=n;require(count<=file.length());digest.update(buffer,0,n)}
                        require(count==file.length());digest.digest()
                    } finally {buffer.fill(0)}
                }
                require(MessageDigest.isEqual(digest(file.inputStream()),digest(destination.read(pending.id)))) {"Backup verification failed"}
                checkActive()
                pending=destination.rename(pending.id,name);require(pending.name==name) {"Backup provider changed the filename"}
                committed=true
                (previous+pending).sortedBy {sequence(it.name)}.dropLast(retention).forEach {checkActive();destination.delete(it.id)}
                return name
            } finally {
                if(!committed) pending?.let {runCatching {destination.delete(it.id)}}
            }
        }
    }
    fun write(payload:BackupPayload,password:CharArray,retention:Int,destination:BackupDestination,checkActive:()->Unit={}):String {
        require(retention in 1..1000)
        scratch.mkdirs()
        val file=File.createTempFile("auto-backup-",".wbk",scratch)
        try {
            prepare(file, payload, password, checkActive)
            checkActive()
            return publish(file, retention, destination, checkActive)
        } finally {
            file.delete()
        }
    }
}
