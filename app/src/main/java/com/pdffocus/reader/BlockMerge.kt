package com.pdffocus.reader
/** Fusion membership is independent of the reading order. Existing first-member position wins; unselected groups need confirmation. */
object BlockMerge {
 fun position(order:List<Int>,members:Set<Int>):Int?=order.indexOfFirst{it in members}.takeIf{it>=0}
 data class Result(val zones:List<Zone>,val order:List<Int>,val merged:Zone)
 fun apply(zones:List<Zone>,order:List<Int>,members:Set<Int>,position:Int,kind:Kind):Result {
  val chosen=zones.filter{it.id in members};require(chosen.size>=2)
  val rest=order.filterNot{it in members};require(position in 0..rest.size)
  val merged=Zone((zones.maxOfOrNull{it.id}?:0)+1,chosen.map{it.box}.reduce{a,b->a.union(b)},kind,manual=true)
  return Result(zones.filterNot{it.id in members}+merged,rest.toMutableList().apply{add(position,merged.id)},merged)
 }
}
