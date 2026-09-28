package app.kura.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

fun dragCropPosition(position:Float,delta:Float,extent:Float,fraction:Float):Float =
 if(extent<=0 || fraction>=1f) position else (position+delta/(extent*(1-fraction))).coerceIn(0f,1f)
fun colorCode(color:Color):String = "#%08X".format(java.util.Locale.ROOT,color.toArgb())
fun wheelColor(x:Float,y:Float,radius:Float,value:Float):Color {
 val hue=((atan2(y,x)*180f/PI.toFloat())+360f)%360f
 return Color.hsv(hue,(hypot(x,y)/radius.coerceAtLeast(1f)).coerceIn(0f,1f),value)
}
@Composable internal fun ItemColorControl(keys:List<String>,values:Map<String,String>,change:(String,String)->Unit) {
 var menu by remember {mutableStateOf(false)}
 var selected by remember {mutableStateOf<String?>(null)}
 fun label(key:String)=when(key) {"foregroundColor"->"Text color";"labelColor"->"Label color";else->"Background color"}
 OutlinedButton(shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),onClick={menu=true},modifier=Modifier.fillMaxWidth()) {Icon(Icons.Outlined.Palette,null);Spacer(Modifier.width(8.dp));Text("Item colors")}
 if(menu) AlertDialog(onDismissRequest={menu=false},title={Text("Item colors")},text={Column {
  keys.forEach {key->SettingsRow(label(key),if(values[key].isNullOrBlank()) "Automatic" else "Custom color",Icons.Outlined.Palette,{selected=key},
   trailing={Surface(color=parsePassColor(values[key].orEmpty()) ?: MaterialTheme.colorScheme.surfaceVariant,shape=androidx.compose.foundation.shape.CircleShape,modifier=Modifier.size(28.dp)) {}})}
 }},confirmButton={TextButton(onClick={menu=false}) {Text("Done")}})
 selected?.let {key->
  var code by remember(key) {mutableStateOf(values[key].orEmpty())}
  val parsed=parsePassColor(code)
  val initial=remember(key) {parsePassColor(values[key].orEmpty()) ?: Color.White}
  var brightness by remember(key) {mutableFloatStateOf(maxOf(initial.red,initial.green,initial.blue))}
  val shown=parsed ?: initial
  fun select(point:Offset,width:Float) {code=colorCode(wheelColor(point.x-width/2,point.y-width/2,width/2,brightness))}
  AlertDialog(onDismissRequest={selected=null},title={Text(label(key))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   Canvas(Modifier.fillMaxWidth().aspectRatio(1f).semantics {contentDescription="Color wheel"}
    .pointerInput(brightness) {detectTapGestures {select(it,size.width.toFloat())}}
    .pointerInput(brightness) {detectDragGestures {change,_->change.consume();select(change.position,size.width.toFloat())}}) {
     drawCircle(Brush.sweepGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)))
     drawCircle(Brush.radialGradient(listOf(Color.White,Color.Transparent),radius=size.width/2))
     drawCircle(Color.Black.copy(alpha=1-brightness))
     val hsv=FloatArray(3);android.graphics.Color.colorToHSV(shown.toArgb(),hsv)
     val angle=hsv[0]*PI.toFloat()/180f;val r=hsv[1]*size.width/2
     val point=center+Offset(cos(angle)*r,sin(angle)*r)
     drawCircle(Color.Black,8.dp.toPx(),point);drawCircle(Color.White,5.dp.toPx(),point)
   }
   Text("Brightness")
   Slider(brightness,{next->val hsv=FloatArray(3);android.graphics.Color.colorToHSV(shown.toArgb(),hsv);brightness=next;code=colorCode(Color.hsv(hsv[0],hsv[1],next,shown.alpha))},modifier=Modifier.semantics {contentDescription="Color brightness"})
   OutlinedTextField(code,{code=it;parsePassColor(it)?.let {c->brightness=maxOf(c.red,c.green,c.blue)}},label={Text("Color code")},supportingText={Text("#RRGGBB, #AARRGGBB or rgb / rgba")},isError=code.isNotBlank() && parsed==null,singleLine=true)
   Surface(color=shown,modifier=Modifier.fillMaxWidth().height(28.dp)) {}
   TextButton(onClick={change(key,"");selected=null}) {Text("Use automatic color")}
  }},confirmButton={TextButton(enabled=parsed!=null,onClick={change(key,code);selected=null}) {Text("Apply color")}},dismissButton={TextButton(onClick={selected=null}) {Text("Cancel")}})
 }
}
