package com.pdffocus.reader
import kotlin.math.*

/** Trims the uniform margin around a detected block, from the outside in only.
 * The background is the colour of the block's own outer ring, so tinted paper and white paper both work.
 * Only rows and columns on the edge are removed while they hold (almost) no content: a title, a bubble or text
 * touching the edge stops the trim, and a background colour inside a drawing is never touched because the scan
 * never goes past the first content row or column. No pixels are kept. */
object ComicTrim {
 private const val W=300
 /** [box] is in page fractions. Returns the same box when nothing safe can be trimmed. */
 fun trim(pixels:IntArray,width:Int,height:Int,box:Box,pad:Float=.012f):Box{
  require(pixels.size==width*height)
  if(width<40||height<40)return box
  val l0=(box.left*width).toInt().coerceIn(0,width-1);val t0=(box.top*height).toInt().coerceIn(0,height-1)
  val r0=(box.right*width).toInt().coerceIn(l0+1,width);val b0=(box.bottom*height).toInt().coerceIn(t0+1,height)
  val bw=r0-l0;val bh=b0-t0
  if(bw<24||bh<24)return box
  val sx=max(1,bw/W);val sy=max(1,bh/W)
  val w=bw/sx;val h=bh/sy
  fun px(x:Int,y:Int)=pixels[(t0+min(bh-1,y*sy))*width+l0+min(bw-1,x*sx)]
  // Background: most common colour (16 levels per channel) on the outer ring, then its median.
  val ring=max(1,min(w,h)/50);val votes=HashMap<Int,MutableList<Int>>()
  for(y in 0 until h)for(x in 0 until w)if(y<ring||y>=h-ring||x<ring||x>=w-ring){val p=px(x,y);votes.getOrPut(((p ushr 20)and 15)*256+((p ushr 12)and 15)*16+((p ushr 4)and 15)){mutableListOf()}+=p}
  val mode=votes.values.maxByOrNull{it.size}?:return box
  fun med(sh:Int)=mode.map{(it ushr sh)and 255}.sorted()[mode.size/2]
  val br=med(16);val bg=med(8);val bb=med(0)
  fun ink(x:Int,y:Int):Boolean{val p=px(x,y);return abs(((p ushr 16)and 255)-br)+abs(((p ushr 8)and 255)-bg)+abs((p and 255)-bb)>36}
  // A line holds content when more than 0.8% of its pixels differ from the background.
  val colInk=IntArray(w){x->var n=0;for(y in 0 until h)if(ink(x,y))n++;n}
  val rowInk=IntArray(h){y->var n=0;for(x in 0 until w)if(ink(x,y))n++;n}
  fun hasInk(count:Int,len:Int)=count>max(1,(len*.008f).toInt())
  var t=0;while(t<h&&!hasInk(rowInk[t],w))t++
  var b=h;while(b>t&&!hasInk(rowInk[b-1],w))b--
  var l=0;while(l<w&&!hasInk(colInk[l],h))l++
  var r=w;while(r>l&&!hasInk(colInk[r-1],h))r--
  // Nothing found, or the content is tiny: keep the detected box rather than guess.
  if(r-l<w*.25f||b-t<h*.25f)return box
  val px2=pad*w;val py2=pad*h
  val nl=max(0f,l-px2)/w;val nt=max(0f,t-py2)/h;val nr=min(w.toFloat(),r+px2)/w;val nb=min(h.toFloat(),b+py2)/h
  val bwf=box.right-box.left;val bhf=box.bottom-box.top
  return Box(box.left+nl*bwf,box.top+nt*bhf,box.left+nr*bwf,box.top+nb*bhf)
 }

 /** Share of the box that differs from its own background (outer ring colour), 0..1. A band holding only
 * white paper, or a lone page number, stays near 0; a title line or a drawing is well above [CONTENT]. */
 fun inkShare(pixels:IntArray,width:Int,height:Int,box:Box):Float{
  require(pixels.size==width*height)
  val l0=(box.left*width).toInt().coerceIn(0,width-1);val t0=(box.top*height).toInt().coerceIn(0,height-1)
  val r0=(box.right*width).toInt().coerceIn(l0+1,width);val b0=(box.bottom*height).toInt().coerceIn(t0+1,height)
  val bw=r0-l0;val bh=b0-t0
  val sx=max(1,bw/W);val sy=max(1,bh/W);val w=max(1,bw/sx);val h=max(1,bh/sy)
  fun px(x:Int,y:Int)=pixels[(t0+min(bh-1,y*sy))*width+l0+min(bw-1,x*sx)]
  val ring=max(1,min(w,h)/50);val votes=HashMap<Int,MutableList<Int>>()
  for(y in 0 until h)for(x in 0 until w)if(y<ring||y>=h-ring||x<ring||x>=w-ring){val p=px(x,y);votes.getOrPut(((p ushr 20)and 15)*256+((p ushr 12)and 15)*16+((p ushr 4)and 15)){mutableListOf()}+=p}
  val mode=votes.values.maxByOrNull{it.size}?:return 0f
  fun med(sh:Int)=mode.map{(it ushr sh)and 255}.sorted()[mode.size/2]
  val br=med(16);val bg=med(8);val bb=med(0)
  var n=0
  for(y in 0 until h)for(x in 0 until w){val p=px(x,y);if(abs(((p ushr 16)and 255)-br)+abs(((p ushr 8)and 255)-bg)+abs((p and 255)-bb)>36)n++}
  return n.toFloat()/(w*h)
 }
 const val CONTENT=.005f
 /** True when a band holds nothing worth reading: white or tinted paper, with at most a page number. */
 fun blank(pixels:IntArray,width:Int,height:Int,box:Box)=inkShare(pixels,width,height,box)<CONTENT

 /** Drops complement bands (top, bottom or side margins that are not panels) holding only paper, and trims a
 * lone remaining block to its content. Panels are never dropped or resized, so no drawing is cut. If every
 * band would go, the zones are returned unchanged. */
 fun withoutBlankBands(zones:List<Zone>,pixels:IntArray?,width:Int,height:Int):List<Zone>{
  if(pixels==null||pixels.size!=width*height||zones.isEmpty())return zones
  val kept=zones.filter{!(it.complement&&!it.panel&&blank(pixels,width,height,it.box))}
  if(kept.isEmpty())return zones
  if(kept.size==1&&!kept[0].panel){val t=trim(pixels,width,height,kept[0].box);return listOf(kept[0].copy(box=t))}
  return kept
 }
}
