package app.kura.feature

import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureTransferScreen(chunks:List<String>,close:()->Unit) {
    var index by remember(chunks) {mutableIntStateOf(0)}
    var playing by remember {mutableStateOf(false)}
    LaunchedEffect(playing,chunks) {while(playing) {kotlinx.coroutines.delay(1500);index=(index+1)%chunks.size}}
    val bitmap=rememberBarcode(chunks[index],"QR Code")
    KuraDialog(close) {
        Scaffold(topBar={TopAppBar(title={Text("Share securely")},navigationIcon={IconButton(onClick=close) {Icon(Icons.Default.Close,"Close secure sharing")}})}) {padding->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("The receiver scans every code, then enters the transfer password. Images are not included.")
                Card(colors=CardDefaults.cardColors(containerColor=Color.White)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(16.dp),contentAlignment=Alignment.Center) {
                        if(bitmap==null) CircularProgressIndicator() else Image(bitmap.asImageBitmap(),"Encrypted transfer code",Modifier.fillMaxSize())
                    }
                }
                Text("Code "+(index+1)+" of "+chunks.size)
                if(chunks.size>1) {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick={playing=false;index=(index+chunks.size-1)%chunks.size}) {Text("Previous")}
                        OutlinedButton(onClick={playing=false;index=(index+1)%chunks.size}) {Text("Next")}
                    }
                    FilledTonalButton(onClick={playing=!playing}) {Text(if(playing) "Pause slideshow" else "Play slideshow")}
                }
                Text("Share the password separately.")
            }
        }
    }
}
