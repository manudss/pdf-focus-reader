package com.pdffocus.reader
import android.graphics.Bitmap
import kotlin.math.*
/** Fallback for saved selections before any words are available. Cut only on white row gaps. */
object OcrRasterLines {
 fun chunks(bitmap:Bitmap,z:Zone):List<Box>{
  val b=z.box.clamp();val x0=(b.left*bitmap.width).toInt().coerceIn(0,bitmap.width-1);val x1=ceil(b.right*bitmap.width).toInt().coerceIn(x0+1,bitmap.width)
  val y0=(b.top*bitmap.height).toInt().coerceIn(0,bitmap.height-1);val y1=ceil(b.bottom*bitmap.height).toInt().coerceIn(y0+1,bitmap.height)
  val row=IntArray(x1-x0);val runs=mutableListOf<Pair<Int,Int>>();var begin=-1;var blank=0
  for(y in y0 until y1){bitmap.getPixels(row,0,row.size,x0,y,row.size,1);val ink=row.count{minOf((it shr 16)and 255,(it shr 8)and 255,it and 255)<150}>max(1,row.size/100)
   if(ink){if(begin<0)begin=y;blank=0}else if(begin>=0){blank++;if(blank>=2){runs+=begin to y-1;begin=-1;blank=0}}}
  if(begin>=0)runs+=begin to y1-1
  if(runs.size<7)return listOf(b)
  val out=mutableListOf<Box>();var top=y0
  for(end in 6 until runs.size step 6){val cut=(runs[end-1].second+runs[end].first)/2;out+=Box(b.left,top.toFloat()/bitmap.height,b.right,cut.toFloat()/bitmap.height);top=cut}
  out+=Box(b.left,top.toFloat()/bitmap.height,b.right,b.bottom);return out.filter{it.height>0}
 }
}
