package com.pdffocus.reader
import android.graphics.Bitmap
import kotlin.math.*

/** Optional local pixel filter. Never OCR text or document changes. Operates on one rendered tile. */
object TextCleaner {
    fun clean(source:Bitmap,thicker:Boolean):Bitmap {
        val width=source.width;val height=source.height
        val pixels=IntArray(width*height);source.getPixels(pixels,0,width,0,0,width,height)
        val filtered=filter(pixels,width,height,thicker)
        return Bitmap.createBitmap(filtered,width,height,Bitmap.Config.ARGB_8888)
    }
    internal fun filter(pixels:IntArray,width:Int,height:Int,thicker:Boolean):IntArray {
        require(pixels.size==width*height&&width>0&&height>0)
        val gray=IntArray(pixels.size){i->val p=pixels[i];val r=(p shr 16)and 255;val g=(p shr 8)and 255;val b=p and 255;(((r*299+g*587+b*114)/1000-75)*255/115).coerceIn(0,255)}
        val median=IntArray(gray.size);val values=IntArray(9)
        for(y in 0 until height)for(x in 0 until width){var i=0;for(dy in -1..1)for(dx in -1..1)values[i++]=gray[(y+dy).coerceIn(0,height-1)*width+(x+dx).coerceIn(0,width-1)];java.util.Arrays.sort(values);median[y*width+x]=values[4]}
        return IntArray(pixels.size){i->
            val p=pixels[i];val r=(p shr 16)and 255;val g=(p shr 8)and 255;val b=p and 255
            if(maxOf(r,g,b)-minOf(r,g,b)>35)p // Leave colored text and annotations unchanged.
            else {
                var value=median[i]
                if(thicker){val y=i/width;val x=i%width;var darkest=value;for(dy in -1..1)for(dx in -1..1)darkest=min(darkest,median[(y+dy).coerceIn(0,height-1)*width+(x+dx).coerceIn(0,width-1)]);value=(value*.65f+darkest*.35f).roundToInt()}
                (p and -0x1000000)or(value shl 16)or(value shl 8)or value
            }
        }
    }
}
