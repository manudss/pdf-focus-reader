package com.pdffocus.reader
import kotlin.math.*
/** Bounded geometric proposals for pages with several closed, upright comic frames.
 * Not semantic recognition and not reading-order inference. Uncertain pages keep the OCR path.
 */
object ComicPanels {
 private data class Rule(val left:Int,val right:Int,val top:Int,var bottom:Int)
 fun find(pixels:IntArray,width:Int,height:Int,cancelled:()->Boolean={false}):List<Box>{
  require(pixels.size==width*height)
  if(width<80||height<80)return emptyList()
  fun dark(x:Int,y:Int):Boolean{val p=pixels[y*width+x];return maxOf((p ushr 16)and 255,(p ushr 8)and 255,p and 255)<120}
  val tolerance=max(2,width/180);val rules=mutableListOf<Rule>()
  for(y in 0 until height){
   if(cancelled())return emptyList()
   var x=0
   while(x<width){if(!dark(x,y)){x++;continue};val start=x;while(x<width&&dark(x,y))x++
    if(x-start<width*.16f)continue
    val old=rules.asReversed().firstOrNull{y-it.bottom<=tolerance+1&&abs(start-it.left)<=tolerance&&abs(x-1-it.right)<=tolerance}
    if(old!=null)old.bottom=y else rules+=Rule(start,x-1,y,y)
   }
  }
  // Dense graphics/noisy scans are outside this conservative rectangular-frame path.
  if(rules.size>500)return emptyList()
  val boxes=mutableListOf<Box>()
  fun coverage(x:Int,top:Int,bottom:Int):Float{var found=0;var gap=0;for(y in top..bottom){if((max(0,x-tolerance)..min(width-1,x+tolerance)).any{dark(it,y)}){found++;gap=0}else{gap++;if(gap>tolerance*2+1)return 0f}};return found.toFloat()/(bottom-top+1)}
  for(top in rules)for(bottom in rules){
   if(cancelled())return emptyList()
   val h=bottom.bottom-top.top;val w=top.right-top.left
   if(h<height*.14f||h>height*.65f||w*h<width*height*.04f||abs(top.left-bottom.left)>tolerance||abs(top.right-bottom.right)>tolerance)continue
   if(coverage(top.left,top.top,bottom.bottom)<.88f||coverage(top.right,top.top,bottom.bottom)<.88f)continue
   val box=Box(top.left.toFloat()/width,top.top.toFloat()/height,top.right.toFloat()/width,bottom.bottom.toFloat()/height)
   boxes+=box
  }
  val outer=boxes.distinct().filter{b->boxes.none{a->a!=b&&a.area>b.area*1.02f&&a.intersect(b)>.96f*b.area}}
   .sortedByDescending{it.area}.fold(mutableListOf<Box>()){out,b->if(out.none{it.intersect(b)>.94f*min(it.area,b.area)})out+=b;out}
  if(outer.size !in 3..16||outer.sumOf{it.area.toDouble()}<.42)return emptyList()
  if(outer.indices.any{i->(i+1 until outer.size).any{j->outer[i].intersect(outer[j])>.03f*min(outer[i].area,outer[j].area)}})return emptyList()
  // Empty form/table cells are not enough: require drawn content in most large frames.
  val illustrated=outer.count{b->
   val l=(b.left*width+b.width*width*.07f).toInt();val r=(b.right*width-b.width*width*.07f).toInt()
   val t=(b.top*height+b.height*height*.07f).toInt();val d=(b.bottom*height-b.height*height*.07f).toInt()
   var ink=0;var samples=0
   for(y in t..d step 2)for(x in l..r step 2){samples++;if(dark(x,y))ink++}
   samples>0&&ink.toFloat()/samples>.035f
  }
  if(illustrated<max(3,(outer.size*.65f).toInt()))return emptyList()
  return outer.sortedWith(compareBy<Box>{(it.top*35).toInt()}.thenBy{it.left})
 }
}
