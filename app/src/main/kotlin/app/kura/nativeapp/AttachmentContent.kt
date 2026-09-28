package app.kura.nativeapp

import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Explicit external-open grants stream from bounded memory; plaintext is never cached on disk. */
class AttachmentContent:ContentProvider() {
 companion object {
  private data class Entry(val name:String,val mime:String,val bytes:ByteArray)
  private val entries=mutableMapOf<String,Entry>()
  private val worker=Executors.newFixedThreadPool(2)
  private val expiry=Executors.newSingleThreadScheduledExecutor()
  private val pipes=mutableSetOf<ParcelFileDescriptor>()
  private val readerThread=android.os.HandlerThread("attachment-reader").apply {start()}
  fun publish(context:android.content.Context,name:String,mime:String,bytes:ByteArray):Uri {
   require(bytes.size<=app.kura.nativecore.ItemMedia.MAX_FILE)
   clear()
   val token=UUID.randomUUID().toString()
   synchronized(entries) {entries[token]=Entry(name,mime,bytes)}
   expiry.schedule({synchronized(entries) {if(token in entries) clear()}},60,TimeUnit.SECONDS)
   return Uri.parse("content://"+context.packageName+".attachments/"+token)
  }
  fun clear() {synchronized(entries) {entries.values.forEach {it.bytes.fill(0)};entries.clear();pipes.forEach {runCatching {it.close()}};pipes.clear()}}
 }
 override fun onCreate()=true
 override fun getType(uri:Uri)=synchronized(entries) {entries[uri.lastPathSegment]?.mime}
 override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):android.database.Cursor {
  val columns=projection ?: arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
  return synchronized(entries) {val e=entries[uri.lastPathSegment] ?: throw java.io.FileNotFoundException();MatrixCursor(columns).apply {addRow(columns.map {when(it) {OpenableColumns.DISPLAY_NAME->e.name;OpenableColumns.SIZE->e.bytes.size;else->null}})}}
 }
 override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
  require(mode=="r")
  if(android.os.Build.VERSION.SDK_INT>=26) {
   var descriptor:ParcelFileDescriptor?=null
   val token=uri.lastPathSegment
   synchronized(entries) {if(token !in entries || pipes.size>=2) throw java.io.FileNotFoundException()}
   val callback=object:android.os.ProxyFileDescriptorCallback() {
    override fun onGetSize():Long=synchronized(entries) {entries[token]?.bytes?.size?.toLong() ?: throw android.system.ErrnoException("Attachment closed",android.system.OsConstants.ENOENT)}
    override fun onRead(offset:Long,size:Int,data:ByteArray):Int=synchronized(entries) {
     val entry=entries[token] ?: throw android.system.ErrnoException("Attachment closed",android.system.OsConstants.ENOENT)
     if(offset<0 || size<0) throw android.system.ErrnoException("Invalid offset",android.system.OsConstants.EINVAL)
     if(offset>=entry.bytes.size) return@synchronized 0
     val count=minOf(size,data.size,entry.bytes.size-offset.toInt());entry.bytes.copyInto(data,0,offset.toInt(),offset.toInt()+count);count
    }
    override fun onRelease() {synchronized(entries) {descriptor?.let {pipes.remove(it)}}}
   }
   val opened=context!!.getSystemService(android.os.storage.StorageManager::class.java).openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY,callback,android.os.Handler(readerThread.looper))
   descriptor=opened
   synchronized(entries) {if(token !in entries || pipes.size>=2) {opened.close();throw java.io.FileNotFoundException()};pipes.add(opened)}
   return opened
  }
  val bytes=synchronized(entries) {entries[uri.lastPathSegment]?.bytes?.copyOf() ?: throw java.io.FileNotFoundException()}
  val pipe=try {ParcelFileDescriptor.createPipe()} catch(e:Throwable) {bytes.fill(0);throw e}
  synchronized(entries) {if(uri.lastPathSegment !in entries || pipes.size>=2) {bytes.fill(0);pipe.forEach {it.close()};throw java.io.FileNotFoundException()};pipes.add(pipe[1])}
  worker.execute {try {ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use {it.write(bytes)}} catch(_:java.io.IOException) {} finally {bytes.fill(0);synchronized(entries) {pipes.remove(pipe[1])}}}
  return pipe[0]
 }
 override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException()
 override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=throw UnsupportedOperationException()
 override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=throw UnsupportedOperationException()
}
