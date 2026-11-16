package com.pdffocus.reader
import kotlin.math.*
/** Closed upright comic-frame proposals, not semantic recognition or reading order.
 * Page margins, thin scanned lines and small rule interruptions must not turn panels into OCR.
 * Uncertain or unillustrated layouts still fall back to OCR. No source pixels are retained.
 */
object ComicPanels {
 private data class Rule(val left:Int,val right:Int,val top:Int,var bottom:Int)
 fun find(pixels:IntArray,width:Int,height:Int,cancelled:()->Boolean={false}):List<Box>{
  require(pixels.size==width*height)
  if(width<80||height<80)return emptyList()
  fun dark(x:Int,y:Int):Boolean{val p=pixels[y*width+x];val r=(p ushr 16)and 255;val g=(p ushr 8)and 255;val b=p and 255;return (r*299+g*587+b*114)<175000}
  val tolerance=max(2,width/225);val bridge=max(1,width/450);val rules=mutableListOf<Rule>()
  for(y in 0 until height){
   if(cancelled())return emptyList()
   var x=0
   while(x<width){
    if(!dark(x,y)){x++;continue}
    val start=x;var last=x;var ink=0
    while(x<width){if(dark(x,y)){last=x;ink++}else if(x-last>bridge)break;x++}
    val end=last
    // A tiny scan dropout is allowed, a text baseline or a gutter is not a rule.
    if(end-start<width*.12f||ink.toFloat()/(end-start+1)<.94f)continue
    val old=rules.asReversed().firstOrNull{y-it.bottom<=tolerance+1&&abs(start-it.left)<=tolerance&&abs(end-it.right)<=tolerance}
    if(old!=null)old.bottom=y else rules+=Rule(start,end,y,y)
   }
  }
  if(rules.size>500)return emptyList()
  fun coverage(x:Int,top:Int,bottom:Int):Float{
   var found=0;var gap=0
   for(y in top..bottom){
    if((max(0,x-tolerance)..min(width-1,x+tolerance)).any{dark(it,y)}){found++;gap=0}
    else{gap++;if(gap>bridge+1)return 0f}
   }
   return found.toFloat()/(bottom-top+1)
  }
  val boxes=mutableListOf<Box>()
  for(top in rules)for(bottom in rules){
   if(cancelled())return emptyList()
   val h=bottom.bottom-top.top;val w=top.right-top.left
   if(h<height*.075f||h>height*.7f||w*h<width*height*.012f||abs(top.left-bottom.left)>tolerance||abs(top.right-bottom.right)>tolerance)continue
   if(coverage(top.left,top.top,bottom.bottom)<.94f||coverage(top.right,top.top,bottom.bottom)<.94f)continue
   boxes+=Box(top.left.toFloat()/width,top.top.toFloat()/height,top.right.toFloat()/width,bottom.bottom.toFloat()/height)
  }
  // Flat dark mastheads are not panels: a frame needs a varying interior.
  val candidates=boxes.filter{b->
   var ink=0;var total=0
   val l=((b.left+b.width*.1f)*width).toInt();val r=((b.right-b.width*.1f)*width).toInt()
   val t=((b.top+b.height*.1f)*height).toInt();val d=((b.bottom-b.height*.1f)*height).toInt()
   for(y in t..d step 3)for(x in l..r step 3){total++;if(dark(x,y))ink++}
   total>0&&ink.toFloat()/total<.9f
  }
  val outer=candidates.distinct().filter{b->candidates.none{a->a!=b&&a.area>b.area*1.02f&&a.intersect(b)>.96f*b.area}}
   .sortedByDescending{it.area}.fold(mutableListOf<Box>()){out,b->if(out.none{it.intersect(b)>.94f*min(it.area,b.area)})out+=b;out}
  if(outer.size !in 3..16)return emptyList()
  val area=outer.sumOf{it.area.toDouble()}
  val extent=outer.reduce{a,b->a.union(b)}
  // Judge layout coverage within the framed content, not within the paper margins.
  if(area<.16||area/extent.area<.65)return emptyList()
  if(outer.indices.any{i->(i+1 until outer.size).any{j->outer[i].intersect(outer[j])>.03f*min(outer[i].area,outer[j].area)}})return emptyList()
  // Blank forms and text-only ruled tables are not illustrations. Require interior ink
  // and a substantial vertical mark, not only short horizontal text-like strokes.
  val illustrated=outer.count{b->
   if(cancelled())return emptyList()
   val l=(b.left*width+b.width*width*.07f).toInt();val r=(b.right*width-b.width*width*.07f).toInt()
   val t=(b.top*height+b.height*height*.07f).toInt();val d=(b.bottom*height-b.height*height*.07f).toInt()
   var ink=0;var samples=0;var longest=0
   for(x in l..r step 2){var run=0;for(y in t..d){
    if(dark(x,y)){ink++;run++;longest=max(longest,run)}else run=0
    samples++
   }}
   samples>0&&ink.toFloat()/samples>.035f&&longest>(d-t)*.12f
  }
  if(illustrated<max(3,ceil(outer.size*.65f).toInt()))return emptyList()
  return outer.sortedWith(compareBy<Box>{(it.top*35).toInt()}.thenBy{it.left})
 }
}
