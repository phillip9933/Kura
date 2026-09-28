package app.kura.feature

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable internal fun ItemActionsMenu(label:String,favorite:Boolean,archived:Boolean,onFavorite:()->Unit,edit:()->Unit,archive:()->Unit,delete:()->Unit,share:()->Unit,export:(()->Unit)?=null,pkpass:(()->Unit)?=null,editable:Boolean=true) {
 var open by remember {mutableStateOf(false)}
 Box {
  IconButton(onClick={open=true}) {Icon(Icons.Outlined.MoreVert,label)}
  DropdownMenu(open,{open=false}) {
   fun action(run:()->Unit) {open=false;run()}
   if(editable) DropdownMenuItem(text={Text("Edit")},leadingIcon={Icon(Icons.Outlined.Edit,null)},onClick={action(edit)})
   DropdownMenuItem(text={Text(if(favorite) "Remove favorite" else "Add favorite")},leadingIcon={Icon(if(favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,null)},onClick={action(onFavorite)})
   DropdownMenuItem(text={Text("Share securely")},leadingIcon={Icon(Icons.Outlined.Share,null)},onClick={action(share)})
   export?.let {DropdownMenuItem(text={Text("Export encrypted pass")},leadingIcon={Icon(Icons.Outlined.FileDownload,null)},onClick={action(it)})}
   pkpass?.let {DropdownMenuItem(text={Text("Export as .pkpass")},leadingIcon={Icon(Icons.Outlined.ConfirmationNumber,null)},onClick={action(it)})}
   HorizontalDivider()
   DropdownMenuItem(text={Text(if(archived) "Unarchive" else "Archive")},leadingIcon={Icon(if(archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,null)},onClick={action(archive)})
   DropdownMenuItem(text={Text("Delete")},leadingIcon={Icon(Icons.Outlined.Delete,null)},onClick={action(delete)})
  }
 }
}
