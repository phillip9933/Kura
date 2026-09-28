package app.kura.feature

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import com.google.zxing.*
import uk.org.okapibarcode.backend.*

val allBarcodeFormats=barcodeFormats.keys.toList()+listOf("ITF-14","GS1-128","ISBN","Telepen","POSTNET","RM4SCC","EAN-2","EAN-5")
/** CPU-only encoding. Caller owns and erases the returned bitmap. */
fun renderBarcode(content:String,formatKey:String):Bitmap? = runCatching {
 require(content.isNotBlank() && content.length<=8192)
 val symbol:Symbol?=when(formatKey) {
  "Telepen"->Telepen()
  "POSTNET"->Postnet().also {require(content.length in setOf(5,9,11))}
  "RM4SCC"->RoyalMail4State()
  "ITF-14"->Code2Of5(Code2Of5.ToFMode.ITF14)
  "EAN-2","EAN-5"->EanUpcAddOn().also {require(content.length==if(formatKey=="EAN-2") 2 else 5)}
  else->null
 }
 if(symbol!=null) {
  require(content.length<=512)
  symbol.humanReadableLocation=HumanReadableLocation.NONE
  symbol.barHeight=40
  val data=if(formatKey=="ITF-14" && content.length==14) {
   require(checkDigit(content.dropLast(1))==content.last());content.dropLast(1)
  } else content
  symbol.content=data
  val width=symbol.width+20;val height=symbol.height+20
  require(width in 1..4096 && height in 1..4096)
  val scale=(960/width).coerceAtLeast(1)
  val bitmap=Bitmap.createBitmap(width*scale,height*scale,Bitmap.Config.ARGB_8888)
  bitmap.eraseColor(Color.WHITE)
  val canvas=Canvas(bitmap);val paint=Paint().apply {color=Color.BLACK;isAntiAlias=false}
  for(rect in symbol.rectangles) canvas.drawRect(((rect.x+10)*scale).toFloat(),((rect.y+10)*scale).toFloat(),
   ((rect.x+rect.width+10)*scale).toFloat(),((rect.y+rect.height+10)*scale).toFloat(),paint)
  bitmap
 } else {
  val format=when(formatKey) {"GS1-128"->BarcodeFormat.CODE_128;"ISBN"->BarcodeFormat.EAN_13;else->barcodeFormat(formatKey) ?: error("Unsupported format")}
  val data=when(formatKey) {
   "GS1-128"->content.replace(Regex("\\(([^)]+)\\)")) {"\u00f1"+it.groupValues[1]}
   "ISBN"->isbn13(content)
   else->content
  }
  val square=format in setOf(BarcodeFormat.QR_CODE,BarcodeFormat.AZTEC,BarcodeFormat.DATA_MATRIX)
  val matrix=MultiFormatWriter().encode(data,format,if(format==BarcodeFormat.DATA_MATRIX) 0 else 960,if(format==BarcodeFormat.DATA_MATRIX) 0 else if(square) 960 else 320,mapOf(EncodeHintType.MARGIN to if(square) 4 else 24))
  if(format==BarcodeFormat.DATA_MATRIX) {
   // DataMatrixWriter omits quiet zones. Reserve two whole modules on every side.
   val scale=(960/(maxOf(matrix.width,matrix.height)+4)).coerceAtLeast(1)
   val bitmap=Bitmap.createBitmap((matrix.width+4)*scale,(matrix.height+4)*scale,Bitmap.Config.ARGB_8888)
   bitmap.eraseColor(Color.WHITE);val canvas=Canvas(bitmap);val paint=Paint().apply {color=Color.BLACK;isAntiAlias=false}
   for(y in 0 until matrix.height) for(x in 0 until matrix.width) if(matrix[x,y]) canvas.drawRect(((x+2)*scale).toFloat(),((y+2)*scale).toFloat(),((x+3)*scale).toFloat(),((y+3)*scale).toFloat(),paint)
   bitmap
  } else {
  val pixels=IntArray(matrix.width*matrix.height) {i->if(matrix[i%matrix.width,i/matrix.width]) Color.BLACK else Color.WHITE}
  try {Bitmap.createBitmap(matrix.width,matrix.height,Bitmap.Config.ARGB_8888).apply {setPixels(pixels,0,matrix.width,0,0,matrix.width,matrix.height)}}
  finally {pixels.fill(0)}
  }
 }
}.getOrNull()
private fun checkDigit(value:String):Char {
 require(value.all(Char::isDigit))
 return ('0'.code+(10-value.reversed().mapIndexed {i,c->c.digitToInt()*if(i%2==0) 3 else 1}.sum()%10)%10).toChar()
}
private fun isbn13(value:String):String {
 val text=value.replace("-","").replace(" ","")
 if(text.length!=10) return text
 require(text.take(9).all(Char::isDigit))
 val last=if(text.last().uppercaseChar()=='X') 10 else text.last().digitToInt()
 require((text.take(9).mapIndexed {i,c->c.digitToInt()*(10-i)}.sum()+last)%11==0)
 val base="978"+text.take(9);return base+checkDigit(base)
}
