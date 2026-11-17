package com.pdffocus.reader
import kotlin.math.*
/** Sample interval boundaries, not screen pixels: fast motion cannot skip a narrow block. */
object SweepSelection {
 fun crossed(zones:List<Zone>,x0:Float,y0:Float,x1:Float,y1:Float):List<Zone>{
  val dx=x1-x0;val dy=y1-y0
  val cuts=mutableListOf(0f,1f)
  for(z in zones){
   var enter=0f;var exit=1f
   fun axis(p:Float,d:Float,lo:Float,hi:Float):Boolean{if(abs(d)<.0000001f)return p in lo..hi;val a=(lo-p)/d;val b=(hi-p)/d;enter=max(enter,min(a,b));exit=min(exit,max(a,b));return enter<=exit}
   if(axis(x0,dx,z.box.left,z.box.right)&&axis(y0,dy,z.box.top,z.box.bottom)){cuts+=enter;cuts+=exit}
  }
  val sorted=cuts.distinct().sorted();val positions=sorted.zipWithNext().map{(a,b)->(a+b)/2}
  return positions.mapNotNull{t->ReadingLayout.smallestHit(zones,x0+dx*t,y0+dy*t)}.distinctBy{it.id}
 }
}
