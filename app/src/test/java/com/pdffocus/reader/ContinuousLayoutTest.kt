package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class ContinuousLayoutTest {
 private fun z(id:Int,b:Box)=Zone(id,b,Kind.TEXT)
 @Test fun noGapsOrRepeatedAreaAtOverlappingBlocks(){
  val zones=listOf(z(7,Box(.1f,.1f,.4f,.5f)),z(4,Box(.1f,.45f,.4f,.8f)),z(9,Box(.1f,.2f,.4f,.3f)))
  val bands=ContinuousLayout.bands(zones);val pieces=bands.flatMap{it.pieces}
  assertEquals(.3f*.7f,pieces.sumOf{it.area.toDouble()}.toFloat(),.000001f)
  for(i in pieces.indices)for(j in i+1 until pieces.size)assertEquals(0f,pieces[i].intersect(pieces[j]),.000001f)
  assertFalse(bands.any{it.zoneId==9});assertEquals(7,bands.first().zoneId)
 }
 @Test fun offsetOverlapsPreserveAllUnseenPixels(){
  val a=Box(.1f,.1f,.4f,.5f);val b=Box(.3f,.3f,.6f,.7f);val bands=ContinuousLayout.bands(listOf(z(1,a),z(2,b)))
  assertEquals(a.area+b.area-a.intersect(b),bands.flatMap{it.pieces}.sumOf{it.area.toDouble()}.toFloat(),.000001f)
 }
 @Test fun touchingBlocksStayInRequestedOrder(){val out=ContinuousLayout.bands(listOf(z(2,Box(.5f,.1f,.8f,.8f)),z(1,Box(.1f,.1f,.5f,.8f))));assertEquals(listOf(2,1),out.map{it.zoneId})}
}
