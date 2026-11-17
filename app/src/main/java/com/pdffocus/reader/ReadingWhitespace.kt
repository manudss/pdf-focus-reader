package com.pdffocus.reader
import android.graphics.Bitmap
import kotlin.math.*
/** Reading-only edge trim: keep every non-paper pixel and a safety row. Stored frames never change. */
object ReadingWhitespace{
 fun trim(bitmap:Bitmap,z:Zone):Zone{
  if(z.kind!=Kind.TEXT)return z
  val b=z.box;val w=bitmap.width;val h=bitmap.height
  val l=floor(b.left*w).toInt().coerceIn(0,w-1);val r=ceil(b.right*w).toInt().coerceIn(l+1,w)
  val top=floor(b.top*h).toInt().coerceIn(0,h-1);val bottom=ceil(b.bottom*h).toInt().coerceIn(top+1,h)
  val row=IntArray(r-l)
  fun paper(y:Int):Boolean{bitmap.getPixels(row,0,row.size,l,y,row.size,1);return row.all{p->((p shr 16)and 255)>=245&&((p shr 8)and 255)>=245&&(p and 255)>=245}}
  var first=top;while(first<bottom&&paper(first))first++
  if(first==bottom)return z // Keep blank blocks, too. They can be intentional.
  var last=bottom-1;while(last>first&&paper(last))last--
  return z.copy(box=b.copy(top=max(b.top,(first-1).coerceAtLeast(top).toFloat()/h),bottom=min(b.bottom,(last+2).coerceAtMost(bottom).toFloat()/h)))
 }
}
