package app.kura.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** Long hold to drag; a tap-selected item reserves tap-to-move while swipes scroll normally. */
@Composable internal fun ReorderTile(item:VaultItem,active:Boolean,selected:Boolean,dragEnabled:Boolean,bounds:MutableMap<String,Rect>,select:()->Unit,begin:()->Unit,hover:(String)->Unit,end:()->Unit,cancel:()->Unit,autoScroll:(Float)->Unit,modifier:Modifier=Modifier,content:@Composable ()->Unit) {
 val id=item.stableKey()
 var point by remember {mutableStateOf(Offset.Zero)}
 var localStart by remember {mutableStateOf(Offset.Zero)}
 var slot by remember {mutableStateOf(Rect.Zero)}
 var dragging by remember {mutableStateOf(false)}
 val currentHover by rememberUpdatedState(hover)
 val currentBegin by rememberUpdatedState(begin)
 val currentEnd by rememberUpdatedState(end)
 val currentCancel by rememberUpdatedState(cancel)
 val scroll by rememberUpdatedState(autoScroll)
 DisposableEffect(id) {onDispose {bounds.remove(id)}}
 Box(modifier.fillMaxWidth().onGloballyPositioned {slot=it.boundsInRoot();bounds[id]=slot}.zIndex(if(dragging) 2f else 0f)) {
  if(dragging) Surface(Modifier.matchParentSize(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.secondaryContainer,border=BorderStroke(2.dp,MaterialTheme.colorScheme.primary)) {
   Box(contentAlignment=Alignment.Center) {Text("Drop here",style=MaterialTheme.typography.labelLarge)}
  }
  Box(Modifier.fillMaxWidth().graphicsLayer {
   translationX=if(dragging) point.x-localStart.x-slot.left else 0f
   translationY=if(dragging) point.y-localStart.y-slot.top else 0f
   alpha=if(dragging) .8f else 1f
  }) {
   content()
   if(active) Box(Modifier.matchParentSize().border(if(selected || dragging) 3.dp else 1.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(16.dp))
    .clickable(onClick=select).semantics {contentDescription="Reorder "+item.title;this.selected=selected}
    .pointerInput(id,dragEnabled) {
     if(dragEnabled) {
      var lastTarget:String?=null
      detectDragGesturesAfterLongPress(onDragStart={localStart=it;point=slot.topLeft+it;dragging=true;lastTarget=null;currentBegin()},
       onDragCancel={val interruptedDrag=dragging;dragging=false;if(interruptedDrag) currentCancel()},onDragEnd={dragging=false;currentEnd()},
       onDrag={change,amount->
        change.consume();point+=amount
        val target=bounds.entries.firstOrNull {it.key!=id && it.value.contains(point)}?.key
        if(target!=lastTarget) {lastTarget=target;if(target!=null) currentHover(target)}
        scroll(point.y)
       })
     }
    },contentAlignment=Alignment.TopEnd) {
    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primaryContainer) {Icon(if(selected) Icons.Default.CheckCircle else Icons.Default.DragIndicator,null,Modifier.padding(6.dp))}
   }
  }
 }
}

fun previewImagePath(item:VaultItem,mode:String):String {
 if(mode!="front" || isImportedPass(item)) return ""
 return org.json.JSONObject(item.json).optString("frontImagePath").takeUnless {it=="null"}.orEmpty()
}
