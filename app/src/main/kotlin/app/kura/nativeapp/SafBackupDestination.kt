package app.kura.nativeapp

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.kura.nativecore.*

class SafBackupDestination(context:Context,private val tree:Uri):BackupDestination {
    private val resolver=context.contentResolver
    private val root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
    private fun uri(id:String)=DocumentsContract.buildDocumentUriUsingTree(tree,id)
    override fun list():List<BackupDocument> {
        val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        return resolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use {cursor->
            buildList {while(cursor.moveToNext()) {require(size<10000) {"Backup folder is too large"};add(BackupDocument(cursor.getString(0),cursor.getString(1)))}}
        } ?: error("Cannot read the backup folder. Choose it again.")
    }
    override fun create(name:String):BackupDocument {
        val created=DocumentsContract.createDocument(resolver,root,"application/octet-stream",name) ?: error("Cannot create backup")
        return BackupDocument(DocumentsContract.getDocumentId(created),name)
    }
    override fun write(id:String)=resolver.openOutputStream(uri(id),"wt") ?: error("Cannot write backup")
    override fun read(id:String)=resolver.openInputStream(uri(id)) ?: error("Cannot verify backup")
    override fun rename(id:String,name:String):BackupDocument {
        val renamed=DocumentsContract.renameDocument(resolver,uri(id),name) ?: error("This folder does not support completing backups")
        val actual=resolver.query(renamed,arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst()) it.getString(0) else null}
        return BackupDocument(DocumentsContract.getDocumentId(renamed),actual ?: error("Cannot verify backup filename"))
    }
    override fun delete(id:String) {check(DocumentsContract.deleteDocument(resolver,uri(id))) {"Could not remove an old automatic backup"}}
}
