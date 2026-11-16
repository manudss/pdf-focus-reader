package com.pdffocus.reader
import kotlin.math.abs
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

 @Test fun nestedTitleUsesContainerWidthWithoutHolesOrRepetition(){
  val title=z(1,Box(.2f,.12f,.5f,.18f));val parent=Zone(2,Box(.05f,.05f,.95f,.3f),Kind.IMAGE)
  val out=ContinuousLayout.bands(listOf(title,parent));val pieces=out.flatMap{it.pieces}
  assertEquals(.9f,out.first().original.width,.00001f)
  assertTrue(pieces.all{abs(it.width-.9f)<.00001f})
  assertEquals(parent.box.area,pieces.sumOf{it.area.toDouble()}.toFloat(),.00001f)
  for(i in pieces.indices)for(j in i+1 until pieces.size)assertEquals(0f,pieces[i].intersect(pieces[j]),.000001f)
  assertEquals(1,out.first().zoneId)
 }
 @Test fun shortHeadingKeepsParagraphScaleAndColumnBoundary(){
  val title=z(1,Box(.1f,.1f,.25f,.14f));val body=z(2,Box(.1f,.15f,.48f,.4f));val other=z(3,Box(.52f,.1f,.9f,.4f))
  val out=ContinuousLayout.bands(listOf(title,body,other))
  assertEquals(.38f,out.first().original.width,.00001f)
  assertEquals(.38f,out.first().pieces.single().width,.00001f)
  assertTrue(out.filter{it.zoneId==1}.all{it.original.right<.5f})
 }

 @Test fun wideTitleAboveAlignedColumnsKeepsColumnExtent(){
  // Magazine layout: a wide short headline left-aligned with the first of several columns.
  // The columns must keep their own horizontal extent; the title must not stretch them.
  val title=z(1,Box(.047f,.128f,.538f,.175f))
  val col1=z(2,Box(.047f,.191f,.331f,.947f));val col2=z(3,Box(.361f,.219f,.669f,.930f));val col3=z(4,Box(.698f,.219f,1f,.930f))
  val out=ContinuousLayout.bands(listOf(title,col1,col2,col3))
  val p1=out.filter{it.zoneId==2}.flatMap{it.pieces};val p2=out.filter{it.zoneId==3}.flatMap{it.pieces};val p3=out.filter{it.zoneId==4}.flatMap{it.pieces}
  assertTrue(p1.all{it.left>=.046f&&it.right<=.332f});assertTrue(p2.all{it.left>=.360f&&it.right<=.670f});assertTrue(p3.all{it.left>=.697f})
  assertEquals(col1.box.area,p1.sumOf{it.area.toDouble()}.toFloat(),.000001f)
  assertEquals(col2.box.area,p2.sumOf{it.area.toDouble()}.toFloat(),.000001f)
  assertEquals(col3.box.area,p3.sumOf{it.area.toDouble()}.toFloat(),.000001f)
  // A tall column keeps its own width as the reading scale reference: full-screen column, not the title's width.
  assertEquals(.284f,out.first{it.zoneId==2}.original.width,.000001f)
  val pieces=out.flatMap{it.pieces};for(i in pieces.indices)for(j in i+1 until pieces.size)assertEquals(0f,pieces[i].intersect(pieces[j]),.000001f)
 }
 @Test fun narrowColumnUnderWideTwoLineTitleKeepsOwnScale(){
  // A wide two-line title above a narrow left column and a wide right column. The narrow
  // column must keep its own scale reference: borrowing the title's width would shrink its
  // text to a strip with a blank right side, then the scale would jump at the next block.
  val title=z(1,Box(.067f,.120f,.819f,.243f))
  val colL1=z(2,Box(.067f,.251f,.333f,.601f));val colR1=z(3,Box(.367f,.251f,.933f,.601f))
  val colL2=z(4,Box(.067f,.616f,.333f,.910f));val colR2=z(5,Box(.367f,.616f,.933f,.910f))
  val out=ContinuousLayout.bands(listOf(title,colL1,colR1,colL2,colR2))
  for(id in listOf(2,4))assertEquals(.266f,out.first{it.zoneId==id}.original.width,.0001f)
  for(id in listOf(3,5))assertEquals(.566f,out.first{it.zoneId==id}.original.width,.0001f)
  val p2=out.filter{it.zoneId==2}.flatMap{it.pieces}
  assertTrue(p2.all{it.left>=.066f&&it.right<=.334f})
  assertEquals(colL1.box.area,p2.sumOf{it.area.toDouble()}.toFloat(),.000001f)
  val pieces=out.flatMap{it.pieces};for(i in pieces.indices)for(j in i+1 until pieces.size)assertEquals(0f,pieces[i].intersect(pieces[j]),.000001f)
 }
}
