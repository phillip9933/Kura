package app.kura.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.kura.nativecore.ItemMedia
import org.json.JSONObject

@Composable fun CardLogo(name:String,path:String,load:suspend(String)->ByteArray?,modifier:Modifier=Modifier) {
 Surface(modifier.size(56.dp).semantics {contentDescription="Card logo"},shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer) {
  Box(contentAlignment=Alignment.Center) {
   if(path.isNotBlank()) VaultImage(path,"Logo",load,Modifier.fillMaxSize().clip(CircleShape),androidx.compose.ui.layout.ContentScale.Crop)
   else Text(name.trim().take(1).uppercase().ifBlank {"K"},style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)
  }
 }
}
@Composable fun MediaEditMenu(label:String,hasImage:Boolean,pick:()->Unit,camera:()->Unit,remove:()->Unit,modifier:Modifier=Modifier) {
 var expanded by remember {mutableStateOf(false)}
 Box(modifier) {
  FilledTonalIconButton(onClick={expanded=true},modifier=Modifier.size(36.dp)) {Icon(Icons.Outlined.Edit,"Edit "+label,Modifier.size(20.dp))}
  DropdownMenu(expanded,{expanded=false}) {
   DropdownMenuItem(text={Text("Choose photo")},leadingIcon={Icon(Icons.Outlined.PhotoLibrary,null)},onClick={expanded=false;pick()})
   DropdownMenuItem(text={Text("Take photo")},leadingIcon={Icon(Icons.Outlined.PhotoCamera,null)},onClick={expanded=false;camera()})
   DropdownMenuItem(text={Text("Remove image")},enabled=hasImage,leadingIcon={Icon(Icons.Outlined.Delete,null)},onClick={expanded=false;remove()})
  }
 }
}
fun mediaPreview(table:String,original:JSONObject,drafts:Map<String,String>,removed:List<String>):JSONObject {
 val row=JSONObject(original.toString())
 removed.forEach {if(it=="kuraLogo") ItemMedia.put(table,row,ItemMedia.get(table,row).put("logo","")) else row.put(it,JSONObject.NULL)}
 drafts.forEach {(column,path)->if(column=="kuraLogo") ItemMedia.put(table,row,ItemMedia.get(table,row).put("logo",path)) else row.put(column,path)}
 return row
}
@Composable fun ItemAttachments(item:VaultItem,action:(VaultItem,String,String)->Unit) {
 val files=remember(item.json) {ItemMedia.attachments(item.table,JSONObject(item.json))}
 var deleting by remember {mutableStateOf<String?>(null)}
 Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("Attachments",style=MaterialTheme.typography.titleMedium)
  Text("Files are encrypted in your vault. Opening or saving a copy shares it outside Kura.",style=MaterialTheme.typography.bodySmall)
  files.forEach {file->
   var menu by remember(file.getString("path")) {mutableStateOf(false)}
   Row(verticalAlignment=Alignment.CenterVertically) {
    Icon(Icons.Outlined.AttachFile,null)
    Text(file.getString("name"),Modifier.weight(1f).padding(horizontal=8.dp),style=MaterialTheme.typography.bodyMedium)
    Box {
     IconButton(onClick={menu=true}) {Icon(Icons.Outlined.MoreVert,"Attachment options: "+file.getString("name"))}
     DropdownMenu(menu,{menu=false}) {
      DropdownMenuItem(text={Text("Open")},onClick={menu=false;action(item,file.getString("path"),"openAttachment")})
      DropdownMenuItem(text={Text("Save a copy")},onClick={menu=false;action(item,file.getString("path"),"exportAttachment")})
      DropdownMenuItem(text={Text("Remove")},onClick={menu=false;deleting=file.getString("path")})
     }
    }
   }
  }
  OutlinedButton(onClick={action(item,"","addAttachment")},enabled=files.size<20,shape=RoundedCornerShape(14.dp)) {Icon(Icons.Outlined.AttachFile,null);Text("Add attachment")}
 }}
 deleting?.let {path->AlertDialog(onDismissRequest={deleting=null},title={Text("Remove attachment?")},text={Text("Remove this file from the item? Existing backups retain their copy.")},confirmButton={TextButton(onClick={action(item,path,"removeAttachment");deleting=null}) {Text("Remove")}},dismissButton={TextButton(onClick={deleting=null}) {Text("Cancel")}})}
}
