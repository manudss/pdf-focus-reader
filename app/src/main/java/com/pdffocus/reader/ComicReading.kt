package com.pdffocus.reader
import kotlin.math.*

/** Per-case presentation only. Never changes the selected source boxes or reading order. */
object ComicReading {
 data class State(val detail:Boolean=false,val scale:Float=0f,val pan:Float=Float.NaN,val sourceY:Float=.5f)
 fun fit(sourceWidth:Float,sourceHeight:Float,availableWidth:Float,availableHeight:Float):Float =
  min(availableWidth/sourceWidth,availableHeight/sourceHeight).coerceAtLeast(.001f)
 fun commonScale(widths:List<Float>,availableWidth:Float):Float {
  val valid=widths.filter{it>0}.sorted()
  return if(valid.isEmpty())1f else availableWidth/valid[(valid.size-1)/2]
 }
 fun pan(value:Float,sourceWidth:Float,scale:Float,availableWidth:Float,rtl:Boolean):Float {
  val limit=max(0f,sourceWidth*scale-availableWidth)
  return if(value.isNaN())if(rtl)limit else 0f else value.coerceIn(0f,limit)
 }
 /** Once selected, an axis remains selected until finger-up. A diagonal never steals a scroll. */
 fun axis(dx:Float,dy:Float,slop:Float):Int = if(max(abs(dx),abs(dy))<slop)0 else if(abs(dx)>abs(dy)*1.2f)1 else 2
}
