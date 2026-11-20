package com.pdffocus.reader
/** Repositions one block inside the reading order: it lands right after the chosen block and the following blocks shift. The page layout itself never moves. */
object BlockMove {
 /** Order with [id] placed just after [afterId]; unchanged when the block or the target is absent, or when they are the same. */
 fun after(order:List<Int>,id:Int,afterId:Int):List<Int>{
  if(id==afterId||id !in order)return order
  val rest=order.filterNot{it==id};val at=rest.indexOf(afterId)
  if(at<0)return order
  return rest.toMutableList().apply{add(at+1,id)}
 }
}
