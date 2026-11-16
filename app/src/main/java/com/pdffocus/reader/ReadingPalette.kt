package com.pdffocus.reader
import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*
/** Dominant local paper/background only. Source pixels and OCR text are never altered. */
object ReadingPalette {
 fun background(bitmap:Bitmap,box:Box):Int{
  val b=box.clamp();val counts=IntArray(4096);val reds=IntArray(4096);val greens=IntArray(4096);val blues=IntArray(4096)
  val l=(b.left*bitmap.width).toInt().coerceIn(0,bitmap.width-1);val r=ceil(b.right*bitmap.width).toInt().coerceIn(l+1,bitmap.width)
  val t=(b.top*bitmap.height).toInt().coerceIn(0,bitmap.height-1);val d=ceil(b.bottom*bitmap.height).toInt().coerceIn(t+1,bitmap.height)
  var total=0
  for(y in t until d step max(1,(d-t)/40))for(x in l until r step max(1,(r-l)/40)){
   val p=bitmap.getPixel(x,y);val red=Color.red(p);val green=Color.green(p);val blue=Color.blue(p)
   val key=((red/16)shl 8)or((green/16)shl 4)or(blue/16);counts[key]++;reds[key]+=red;greens[key]+=green;blues[key]+=blue;total++
  }
  val key=counts.indices.maxByOrNull{counts[it]}?:return Color.WHITE;val n=counts[key]
  if(n<total*.22f)return Color.WHITE // photographic/noisy background: no invented flat colour
  return Color.rgb(reds[key]/n,greens[key]/n,blues[key]/n)
 }
 fun foreground(background:Int):Int=if(Color.red(background)*299+Color.green(background)*587+Color.blue(background)*114<135000)Color.rgb(250,249,246)else Color.rgb(37,31,33)
}
