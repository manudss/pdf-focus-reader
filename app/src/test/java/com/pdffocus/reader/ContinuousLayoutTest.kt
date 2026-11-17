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
 @Test fun offsetOverlapsKeepFullWidthsEvenWhenSourcePixelsRepeat(){
  val a=Box(.1f,.1f,.4f,.5f);val b=Box(.3f,.3f,.6f,.7f);val bands=ContinuousLayout.bands(listOf(z(1,a),z(2,b)))
  assertEquals(a.area+b.area,bands.flatMap{it.pieces}.sumOf{it.area.toDouble()}.toFloat(),.000001f)
 }
 @Test fun tallNeighbourCannotClipEitherSideAtAnyVerticalPosition(){
  val left=Zone(10,Box(.59f,.15f,.81f,.50f),Kind.TEXT)
  val right=Zone(11,Box(.77f,.15f,.94f,.47f),Kind.TEXT)
  for(zones in listOf(listOf(left,right),listOf(right,left))){
   val out=ContinuousLayout.bands(zones)
   for(z in zones){
    val pieces=out.filter{it.zoneId==z.id}.flatMap{it.pieces}
    assertFalse(pieces.isEmpty())
    assertTrue(pieces.all{abs(it.left-z.box.left)<.000001f&&abs(it.right-z.box.right)<.000001f})
    assertEquals(z.box.area,pieces.sumOf{it.area.toDouble()}.toFloat(),.000001f)
   }
  }
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
 @Test fun quoteBelowWideTitleKeepsOwnScaleReference(){
  // A short quote/fragment under a wide title must keep its own width: the title is ABOVE,
  // not the column it tops. Borrowing the title's width would render its text tiny.
  val title=z(1,Box(.05f,.12f,.76f,.32f));val quote=z(2,Box(.05f,.34f,.22f,.37f))
  val out=ContinuousLayout.bands(listOf(title,quote))
  assertTrue(out.first{it.zoneId==2}.original.width<.2f)
 }
 @Test fun headingStillBorrowsScaleFromColumnBelow(){
  val title=z(1,Box(.1f,.1f,.25f,.14f));val body=z(2,Box(.1f,.15f,.48f,.4f))
  val out=ContinuousLayout.bands(listOf(title,body))
  assertEquals(.38f,out.first().original.width,.00001f)
 }
    @Test fun overlappingBlockBoundaryPreservesSourceOnceButSplitsCrossingGlyph(){
        // Source-layout characterization. A glyph spans y=.695..705,
        // crossing the first block's bottom. The source union keeps it exactly once,
        // but as two bands with different ordinals. The reader must join them without a gap.
        val zones=listOf(Zone(1,Box(.41f,.50f,.59f,.70f),Kind.TEXT),Zone(2,Box(.41f,.675f,.59f,.75f),Kind.TEXT))
        val bands=ContinuousLayout.bands(zones)
        for(y in listOf(.695f,.6999f,.7001f,.705f)){
            val owners=bands.flatMap{b->b.pieces}.count{p->.5f>=p.left&&.5f<p.right&&y>=p.top&&y<p.bottom}
            assertEquals("Every sampled glyph source row must survive exactly once",1,owners)
        }
        val above=bands.single{b->b.pieces.any{p->.6999f>=p.top&&.6999f<p.bottom}}
        val below=bands.single{b->b.pieces.any{p->.7001f>=p.top&&.7001f<p.bottom}}
        assertEquals(.70f,above.bottom,.00001f);assertEquals(.70f,below.top,.00001f)
        assertNotEquals("Source pieces have distinct block ordinals and require a contiguous raster join",above.ordinal,below.ordinal)
    }
}