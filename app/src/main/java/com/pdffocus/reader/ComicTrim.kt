package com.pdffocus.reader
import kotlin.math.*

/** Conservative border-paper removal. Uses the PAGE border colour, never the colour of a drawing or a band.
 * Every raster pixel is inspected. Any mark, even a folio or one pixel, stops removal on that side.
 * Noise is kept too: incomplete trimming is preferable to deleting ambiguous content.
 * Low-contrast marks within PAPER_DISTANCE of paper cannot be distinguished by this method.
 */
object ComicTrim {
 const val PAPER_DISTANCE=12
 private data class Paper(val r:Int,val g:Int,val b:Int){
  fun differs(p:Int)=abs(((p ushr 16)and 255)-r)+abs(((p ushr 8)and 255)-g)+abs((p and 255)-b)>PAPER_DISTANCE
 }
 private fun paper(pixels:IntArray,w:Int,h:Int):Paper?{
  val edge=ArrayList<Int>();val ring=max(1,min(w,h)/100)
  for(y in 0 until h)for(x in 0 until w)if(x<ring||x>=w-ring||y<ring||y>=h-ring)edge+=pixels[y*w+x]
  val groups=edge.groupBy{((it ushr 20)and 15)*256+((it ushr 12)and 15)*16+((it ushr 4)and 15)}
  val mode=groups.values.maxByOrNull{it.size}?:return null
  fun median(sh:Int)=mode.map{(it ushr sh)and 255}.sorted()[mode.size/2]
  val p=Paper(median(16),median(8),median(0))
  // Do not mistake a dark/full-bleed page edge for paper. A mixed border means no confident trim.
  if(min(p.r,min(p.g,p.b))<160||max(p.r,max(p.g,p.b))-min(p.r,min(p.g,p.b))>85)return null
  if(edge.count{!p.differs(it)}<edge.size*.8f)return null
  return p
 }
 private fun bounds(pixels:IntArray,w:Int,h:Int,box:Box,paper:Paper):Box?{
  val l=floor(box.left*w).toInt().coerceIn(0,w-1);val r=ceil(box.right*w).toInt().coerceIn(l+1,w)
  val t=floor(box.top*h).toInt().coerceIn(0,h-1);val b=ceil(box.bottom*h).toInt().coerceIn(t+1,h)
  var x0=r;var x1=l;var y0=b;var y1=t
  for(y in t until b)for(x in l until r)if(paper.differs(pixels[y*w+x])){
   x0=min(x0,x);x1=max(x1,x+1);y0=min(y0,y);y1=max(y1,y+1)
  }
  return if(x0>=x1||y0>=y1)null else Box(x0.toFloat()/w,y0.toFloat()/h,x1.toFloat()/w,y1.toFloat()/h)
 }
 private fun checked(pixels:IntArray,w:Int,h:Int){require(w>0&&h>0&&pixels.size==w*h)}
 private fun padded(b:Box,limit:Box,pad:Float)=Box(max(limit.left,b.left-pad*limit.width),max(limit.top,b.top-pad*limit.height),min(limit.right,b.right+pad*limit.width),min(limit.bottom,b.bottom+pad*limit.height))
 fun trim(pixels:IntArray,width:Int,height:Int,box:Box,pad:Float=.012f):Box{
  checked(pixels,width,height);val p=paper(pixels,width,height)?:return box
  val ink=bounds(pixels,width,height,box,p)?:return box
  return padded(ink,box,pad)
 }
 /** No density/size/location inference: a folio, chapter digit or annotation is content, even in the footer. */
 fun blank(pixels:IntArray,width:Int,height:Int,box:Box):Boolean{
  checked(pixels,width,height);val p=paper(pixels,width,height)?:return false
  return bounds(pixels,width,height,box,p)==null
 }
 fun withoutBlankBands(zones:List<Zone>,pixels:IntArray?,width:Int,height:Int):List<Zone>{
  if(pixels==null||pixels.size!=width*height||zones.isEmpty())return zones
  val p=paper(pixels,width,height)?:return zones
  val measured=zones.map{it to bounds(pixels,width,height,it.box,p)}
  val kept=measured.filter{(z,b)->z.panel||!z.complement||b!=null}
  if(kept.isEmpty())return zones
  return kept.map{(z,b)->
   if(b==null)z else{
    val t=padded(b,z.box,.012f)
    z.copy(box=if(z.panel)z.box.copy(left=t.left,right=t.right)else t)
   }
  }
 }
}
