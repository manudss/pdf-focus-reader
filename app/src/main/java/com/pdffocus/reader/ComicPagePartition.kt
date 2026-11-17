package com.pdffocus.reader
import kotlin.math.*
/** A complete rectangular partition, independent from semantic text/image detection.
 * Panels guide cuts only when they can be kept whole. Every pixel has exactly one owner. */
object ComicPagePartition{
 data class Proposal(val zones:List<Zone>,val framed:Boolean,val uncertain:Boolean)
 fun propose(panels:List<Zone>,width:Int,height:Int,direction:ReadingDirection=ReadingDirection.LEFT_TO_RIGHT,quietRow:((Float)->Float)?=null):Proposal{
  require(width>0&&height>0)
  val anchors=panels.filter{it.panel&&it.box.area>0}.map{it.copy(box=it.box.clamp())}
  val overlapping=anchors.indices.any{i->(i+1 until anchors.size).any{j->anchors[i].box.intersect(anchors[j].box)>.000001f}}
  val output=mutableListOf<Pair<Box,Boolean>>()
  fun fallback(region:Box){
   val ratio=region.height*height/(region.width*width)
   val count=ceil(ratio/1.45f).toInt().coerceIn(1,16)
   var top=region.top
   for(i in 1..count){
    val expected=region.top+region.height*i/count
    val end=if(i==count)region.bottom else if(quietRow==null)expected else{
     val radius=region.height/count*.18f
     (0..24).map{expected-radius+2*radius*it/24}.minWithOrNull(compareBy<Float>{quietRow(it)}.thenBy{abs(it-expected)})!!.coerceIn(top+.01f,region.bottom)
    }
    output+=Box(region.left,top,region.right,end) to false;top=end
   }
  }
  fun partition(region:Box,items:List<Zone>){
   if(items.isEmpty()){fallback(region);return}
   if(items.size==1){output+=region to true;return}
   fun cut(horizontal:Boolean):Float?{
    fun start(z:Zone)=if(horizontal)z.box.top else z.box.left
    fun end(z:Zone)=if(horizontal)z.box.bottom else z.box.right
    val sorted=items.sortedBy{start(it)};var furthest=end(sorted.first());val gaps=mutableListOf<Pair<Float,Float>>()
    for(i in 1 until sorted.size){val next=start(sorted[i]);if(next>furthest+.00001f)gaps+=(furthest+next)/2 to next-furthest;furthest=max(furthest,end(sorted[i]))}
    return gaps.maxByOrNull{it.second}?.first
   }
   val horizontal=cut(true);val vertical=if(horizontal==null)cut(false)else null
   when{
    horizontal!=null->{partition(region.copy(bottom=horizontal),items.filter{it.box.bottom<=horizontal});partition(region.copy(top=horizontal),items.filter{it.box.top>=horizontal})}
    vertical!=null->{partition(region.copy(right=vertical),items.filter{it.box.right<=vertical});partition(region.copy(left=vertical),items.filter{it.box.left>=vertical})}
    else->fallback(region)
   }
  }
  if(anchors.size>=2&&!overlapping){
   // A title/footer outside the panel extent stays a full-width independent piece.
   val top=anchors.minOf{it.box.top};val bottom=anchors.maxOf{it.box.bottom}
   val contentTop=if(top>.07f){output+=Box(0f,0f,1f,top)to false;top}else 0f
   val contentBottom=if(bottom<.93f)bottom else 1f
   partition(Box(0f,contentTop,1f,contentBottom),anchors)
   if(contentBottom<1f)output+=Box(0f,contentBottom,1f,1f)to false
  }else fallback(Box(0f,0f,1f,1f))
  val zones=output.mapIndexed{i,(b,p)->Zone(i+1,b,Kind.IMAGE,manual=true,panel=p,complement=!p)}
  val ordered=PageReadingOrder.propose(zones,PageMode.COMIC,direction)
  return Proposal(ordered.map{id->zones.single{it.id==id}},anchors.size>=2&&!overlapping,overlapping||anchors.size<2||output.any{!it.second&&it.first.height*height/(it.first.width*width)>1.6f})
 }
}
