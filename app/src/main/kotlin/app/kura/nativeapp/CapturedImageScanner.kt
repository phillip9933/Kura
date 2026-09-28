package app.kura.nativeapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer

/** Bounded crop decoding; caller owns and wipes the encoded image. */
object CapturedImageScanner {
    fun crop(bytes: ByteArray, fraction:Float, horizontal:Float, vertical:Float, aspect:Float=0f, circle:Boolean=false):ByteArray {
        require(fraction in .25f..1f && horizontal in 0f..1f && vertical in 0f..1f && bytes.size<=10*1024*1024)
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth.toLong()*bounds.outHeight<=16_000_000)
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2048) sample*=2
        var bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply {inSampleSize=sample;inMutable=true})
            ?: error("Invalid image")
        var cropped:Bitmap?=null
        val output=object:java.io.ByteArrayOutputStream() {fun wipe() {buf.fill(0);reset()}}
        try {
            val orientation=runCatching {android.media.ExifInterface(bytes.inputStream()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
            val transform=android.graphics.Matrix()
            when(orientation) {
                2->transform.setScale(-1f,1f)
                3->transform.setRotate(180f)
                4->transform.setScale(1f,-1f)
                5->{transform.setRotate(90f);transform.postScale(-1f,1f)}
                6->transform.setRotate(90f)
                7->{transform.setRotate(-90f);transform.postScale(-1f,1f)}
                8->transform.setRotate(-90f)
            }
            if(!transform.isIdentity) {
                val transformed=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,transform,true)
                if(transformed!==bitmap) {
                    val mutable=if(transformed.isMutable) transformed else transformed.copy(Bitmap.Config.ARGB_8888,true).also {transformed.recycle()}
                    bitmap.eraseColor(0);bitmap.recycle();bitmap=mutable
                }
            }
            val width=((if(aspect>0) minOf(bitmap.width.toFloat(),bitmap.height*aspect) else bitmap.width.toFloat())*fraction).toInt().coerceAtLeast(1)
            val height=((if(aspect>0) minOf(bitmap.height.toFloat(),bitmap.width/aspect) else bitmap.height.toFloat())*fraction).toInt().coerceAtLeast(1)
            cropped=Bitmap.createBitmap(bitmap,((bitmap.width-width)*horizontal).toInt(),((bitmap.height-height)*vertical).toInt(),width,height)
            if(aspect>0) {
                val masked=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                val canvas=android.graphics.Canvas(masked);val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                if(circle) canvas.drawCircle(width/2f,height/2f,minOf(width,height)/2f,paint)
                else canvas.drawRoundRect(android.graphics.RectF(0f,0f,width.toFloat(),height.toFloat()),width*.045f,width*.045f,paint)
                paint.xfermode=android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN);canvas.drawBitmap(cropped,0f,0f,paint)
                cropped?.takeIf {it!==bitmap}?.let {if(it.isMutable) it.eraseColor(0);it.recycle()};cropped=masked
            }
            check(cropped.compress(if(aspect>0 || cropped.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,92,output))
            return output.toByteArray()
        } finally {
            output.wipe()
            cropped?.takeIf {it!==bitmap}?.let {if(it.isMutable) it.eraseColor(0);it.recycle()}
            bitmap.eraseColor(0);bitmap.recycle()
        }
    }

    fun scan(bytes: ByteArray, fraction: Float, horizontal: Float, vertical: Float): Result {
        require(fraction in .25f..1f && horizontal in 0f..1f && vertical in 0f..1f)
        require(bytes.size <= 10 * 1024 * 1024)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 16_000_000)
        var sample = 1
        while ((bounds.outWidth.toLong() / sample) * (bounds.outHeight / sample) > 4_000_000) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true; inPreferredConfig = Bitmap.Config.ARGB_8888 })
            ?: error("Invalid captured image")
        try {
            val width = (bitmap.width * fraction).toInt().coerceAtLeast(1)
            val height = (bitmap.height * fraction).toInt().coerceAtLeast(1)
            val left = ((bitmap.width - width) * horizontal).toInt()
            val top = ((bitmap.height - height) * vertical).toInt()
            val pixels = IntArray(width * height)
            try {
                bitmap.getPixels(pixels, 0, width, left, top, width, height)
                val source = RGBLuminanceSource(width,height,pixels)
                try { return MultiFormatReader().apply {
                    setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to app.kura.feature.barcodeFormats.values.toList(),
                        DecodeHintType.TRY_HARDER to true))
                }.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
                } finally { source.matrix.fill(0) }
            } finally { pixels.fill(0) }
        } finally { bitmap.eraseColor(android.graphics.Color.TRANSPARENT); bitmap.recycle() }
    }
}
