package com.pdffocus.reader
import kotlin.math.*
enum class PageMode { AUTO, JOURNAL, COMIC, MANGA, MANUAL, BOOK }
enum class ReadingDirection { LEFT_TO_RIGHT, RIGHT_TO_LEFT }
enum class PageProfile { JOURNAL, ILLUSTRATED, COMIC }
/** A panel is a geometric hint, not semantic recognition. Photos alone never select comic order. */
object PageReadingOrder{
 fun profile(zones:List<Zone>):PageProfile{
  val images=zones.filter{it.kind==Kind.IMAGE};val panels=images.filter{it.panel}
  val area=zones.sumOf{it.box.area.toDouble()}.coerceAtLeast(.00001)
  if(panels.size>=3&&panels.sumOf{it.box.area.toDouble()}/area>=.65&&panels.size>=ceil(zones.size*.6))return PageProfile.COMIC
  return if(images.size>zones.size/2||images.sumOf{it.box.area.toDouble()}/area>.6)PageProfile.ILLUSTRATED else PageProfile.JOURNAL
 }
 fun propose(zones:List<Zone>,mode:PageMode=PageMode.AUTO,direction:ReadingDirection=ReadingDirection.LEFT_TO_RIGHT):List<Int> =
  if(mode==PageMode.MANUAL)emptyList()else if(mode==PageMode.COMIC||mode==PageMode.MANGA||mode==PageMode.AUTO&&profile(zones)==PageProfile.COMIC)rows(if(mode==PageMode.MANGA||direction==ReadingDirection.RIGHT_TO_LEFT)zones.map{it.copy(box=Box(1-it.box.right,it.box.top,1-it.box.left,it.box.bottom))}else zones).map{it.id}else if(mode==PageMode.BOOK)zones.sortedWith(compareBy<Zone>{it.box.top}.thenBy{it.box.left}).map{it.id}else AutoOrder.propose(zones)
 /** Prefer full horizontal gutters, so unequal panel widths cannot reorder rows as columns.
  * Where one panel spans multiple rows, recurse into the left/right groups. */
 private fun rows(zones:List<Zone>):List<Zone>{
  if(zones.size<2)return zones
  fun split(horizontal:Boolean):Pair<List<Zone>,List<Zone>>?{
   fun start(z:Zone)=if(horizontal)z.box.top else z.box.left
   fun end(z:Zone)=if(horizontal)z.box.bottom else z.box.right
   val sorted=zones.sortedBy{start(it)};var furthest=end(sorted.first())
   for(i in 1 until sorted.size){val next=start(sorted[i]);if(next-furthest>.002f)return sorted.take(i) to sorted.drop(i);furthest=max(furthest,end(sorted[i]))};return null
  }
  val parts=split(true)?:split(false)
  if(parts!=null)return rows(parts.first)+rows(parts.second)
  // Overlap/no gutter: compare against a fixed top-row anchor, never transitive row chaining.
  val anchor=zones.minByOrNull{it.box.top}!!
  val row=zones.filter{abs(it.box.top-anchor.box.top)<=min(it.box.height,anchor.box.height)*.2f+.002f}.sortedWith(compareBy<Zone>{it.box.left}.thenBy{it.box.top}.thenBy{it.id})
  return row+rows(zones.filter{it !in row})
 }
}
