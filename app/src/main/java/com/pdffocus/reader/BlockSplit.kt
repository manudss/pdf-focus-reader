package com.pdffocus.reader

/** Pure, atomic edit. A split is not applied while the preview line moves. */
object BlockSplit {
 data class Result(val zones:List<Zone>,val order:List<Int>)
 fun apply(zones:List<Zone>,order:List<Int>,id:Int,vertical:Boolean,fraction:Float):Result {
  require(fraction.isFinite()&&fraction>0f&&fraction<1f)
  val z=zones.first{it.id==id};val b=z.box
  val a:Box;val c:Box
  if(vertical){val x=b.left+b.width*fraction;a=Box(b.left,b.top,x,b.bottom);c=Box(x,b.top,b.right,b.bottom)}
  else{val y=b.top+b.height*fraction;a=Box(b.left,b.top,b.right,y);c=Box(b.left,y,b.right,b.bottom)}
  val next=(zones.maxOfOrNull{it.id}?:0)+1
  fun lines(box:Box)=z.lineBottoms.filter{it>=box.top&&it<=box.bottom}
  val pieces=listOf(z.copy(box=a,manual=true,lineBottoms=lines(a)),z.copy(id=next,box=c,manual=true,lineBottoms=lines(c)))
  val index=zones.indexOf(z);val out=zones.toMutableList().apply{removeAt(index);addAll(index,pieces)}
  val position=order.indexOf(id)
  return Result(out,if(position<0)order else order.toMutableList().apply{add(position+1,next)})
 }
}
