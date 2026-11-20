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

 // Near-twin same-column text blocks: detection-grown boxes often overlap by a few lines.
 // The later block's leading rows are removed only up to the last line an earlier piece
 // already shows entirely; anything else stays repeated rather than cut.
 private fun tz(id:Int,b:Box,lines:List<Float>,manual:Boolean=false)=Zone(id,b,Kind.TEXT,lineBottoms=lines,manual=manual)
 @Test fun sameColumnOverlapDropsLeadingLinesAlreadyFullyShownEarlier(){
  // Block 1 shows lines ending at .30,.32,.34,.36; block 2 (same column, edges off by .0006)
  // starts at .35 and repeats the lines ending at .36 and .38 (both fully inside block 1).
  val a=tz(1,Box(.1f,.20f,.4f,.385f),listOf(.30f,.32f,.34f,.36f,.38f))
  val b=tz(2,Box(.1f,.35f,.4006f,.60f),listOf(.36f,.38f,.40f,.42f,.44f))
  val out=ContinuousLayout.bands(listOf(a,b))
  val p1=out.filter{it.zoneId==1}.flatMap{it.pieces};val p2=out.filter{it.zoneId==2}.flatMap{it.pieces}
  assertEquals(1,p1.size)
  // Block 1 is trimmed to the straddling line's top: no sliver of it renders twice.
  assertEquals(Box(.1f,.20f,.4f,.38f),p1[0])
  // The repeated lines go, but the cut never severs the line starting at .38, which
  // straddles block 1's bottom: block 2 resumes at that line's top and keeps it whole.
  assertEquals(1,p2.size);assertEquals(.38f,p2[0].top,.0001f);assertEquals(b.box.bottom,p2[0].bottom,.000001f)
  assertEquals(b.box.left,p2[0].left,.000001f);assertEquals(b.box.right,p2[0].right,.000001f)
  // No line repeats or splits: every line sits entirely in exactly one piece, and the
  // pieces tile the column with zero intersection at the seam.
  val seq=(a.lineBottoms+b.lineBottoms).distinct().sorted()
  for(i in 0 until seq.lastIndex){val t=seq[i];val bo=seq[i+1]
      val owners=(p1+p2).count{it.top<=t+.001f&&it.bottom>=bo-.001f}
      assertEquals("line $t..$bo must live whole in exactly one piece",1,owners)}
  val pieces=p1+p2;for(i in pieces.indices)for(j in i+1 until pieces.size)assertEquals(0f,pieces[i].intersect(pieces[j]),.000001f)
 }
 @Test fun sameColumnSeamNeverSeversANumberedLine(){
  // Numbered distinct lines, block 1 ending mid-line (.392 inside the line .38..40):
  // the seam must keep every line readable whole in exactly one piece, with no split
  // and no full repeat, whatever the block geometry.
  val a=tz(1,Box(.1f,.20f,.4f,.392f),listOf(.30f,.32f,.34f,.36f,.38f))
  val b=tz(2,Box(.1f,.35f,.4006f,.60f),listOf(.36f,.38f,.40f,.42f,.44f))
  val out=ContinuousLayout.bands(listOf(a,b))
  val pieces=out.flatMap{it.pieces}
  val seq=(a.lineBottoms+b.lineBottoms).distinct().sorted()
  for(i in 0 until seq.lastIndex){val t=seq[i];val bo=seq[i+1]
      val owners=pieces.count{it.top<=t+.001f&&it.bottom>=bo-.001f}
      assertEquals("numbered line $t..$bo must live whole in exactly one piece",1,owners)}
  val later=out.filter{it.zoneId==2}.flatMap{it.pieces}
  assertEquals("later block resumes at the straddling line's top",.38f,later[0].top,.0001f)
  val earlier=out.filter{it.zoneId==1}.flatMap{it.pieces}
  assertEquals("earlier piece is trimmed to the straddling line's top",.38f,earlier[0].bottom,.0001f)
  for(i in pieces.indices)for(j in i+1 until pieces.size)assertEquals(0f,pieces[i].intersect(pieces[j]),.000001f)
 }
 @Test fun lineStraddlingTheEarlierBlockBottomStaysWithTheLaterBlock(){
  // Block 1 ends mid-line at .365: the line ending at .38 straddles its bottom edge.
  // Only the fully-shown line ending at .36 is removed from block 2; the straddler stays.
  val a=tz(1,Box(.1f,.20f,.4f,.365f),listOf(.30f,.32f,.34f,.36f))
  val b=tz(2,Box(.1f,.35f,.4018f,.60f),listOf(.38f,.40f,.42f))
  val out=ContinuousLayout.bands(listOf(a,b))
  val p1=out.filter{it.zoneId==1}.flatMap{it.pieces}
  val p2=out.filter{it.zoneId==2}.flatMap{it.pieces}
  // The fully-shown band goes, and the cut never severs the straddling line: block 2
  // resumes at the straddler's top (.36) and keeps the whole line. Block 1 is trimmed
  // to the straddler's top too: no sliver of the line renders twice at the seam.
  assertEquals(1,p1.size);assertEquals(Box(.1f,.20f,.4f,.36f),p1[0])
  assertEquals(1,p2.size);assertEquals(.36f,p2[0].top,.0001f);assertEquals(b.box.bottom,p2[0].bottom,.000001f)
 }
 @Test fun offsetEdgeSameColumnOverlapKeepsRepeat(){
  // Same column but both edges are offset by more than .8% of the page: not a near-twin.
  val a=tz(1,Box(.1f,.20f,.4f,.385f),listOf(.30f,.32f,.34f,.36f,.38f))
  val b=tz(2,Box(.109f,.35f,.409f,.60f),listOf(.36f,.38f,.40f,.42f))
  val out=ContinuousLayout.bands(listOf(a,b))
  val p2=out.filter{it.zoneId==2}.flatMap{it.pieces}
  assertEquals(b.box.area,p2.sumOf{it.area.toDouble()}.toFloat(),.000001f)
 }
 @Test fun narrowerSameColumnOverlapKeepsRepeat(){
  // Width ratio .968: below the .97 near-twin threshold, so nothing is cut.
  val a=tz(1,Box(.1f,.20f,.4f,.385f),listOf(.30f,.32f,.34f,.36f,.38f))
  val b=tz(2,Box(.1f,.35f,.41f,.60f),listOf(.36f,.38f,.40f,.42f))
  val out=ContinuousLayout.bands(listOf(a,b))
  val p2=out.filter{it.zoneId==2}.flatMap{it.pieces}
  assertEquals(b.box.area,p2.sumOf{it.area.toDouble()}.toFloat(),.000001f)
 }
 @Test fun differentColumnsSharingTheLineGridKeepRepeats(){
  // Two columns on the same line grid: identical line bottoms prove nothing across columns.
  val a=tz(1,Box(.05f,.20f,.30f,.385f),listOf(.30f,.32f,.34f,.36f,.38f))
  val b=tz(2,Box(.55f,.35f,.80f,.60f),listOf(.36f,.38f,.40f,.42f))
  val out=ContinuousLayout.bands(listOf(a,b))
  val p2=out.filter{it.zoneId==2}.flatMap{it.pieces}
  assertEquals(b.box.area,p2.sumOf{it.area.toDouble()}.toFloat(),.000001f)
 }
 @Test fun imageBlocksAndManualBlocksAreNeverLineCut(){
  // Boxes wider than the earlier block so the pre-existing exact full-width rule cannot
  // fire: only the new text-only near-twin rule could cut, and it must not.
  val a=tz(1,Box(.1f,.20f,.4f,.385f),listOf(.30f,.32f,.34f,.36f,.38f))
  val img=Zone(2,Box(.1f,.35f,.405f,.60f),Kind.IMAGE)
  val man=tz(3,Box(.1f,.35f,.405f,.60f),listOf(.36f,.38f,.40f,.42f),manual=true)
  val manFirst=tz(4,Box(.1f,.20f,.399f,.385f),listOf(.30f,.32f,.34f,.36f,.38f),manual=true)
  val b=tz(5,Box(.1f,.35f,.4f,.60f),listOf(.36f,.38f,.40f,.42f))
  for(pair in listOf(listOf(a,img),listOf(a,man),listOf(manFirst,b))){
   val out=ContinuousLayout.bands(pair)
   val p2=out.filter{it.zoneId==pair[1].id}.flatMap{it.pieces}
   assertEquals(pair[1].box.area,p2.sumOf{it.area.toDouble()}.toFloat(),.000001f)
  }
 }
 @Test fun lineVisibleInNoEarlierPieceIsNeverCut(){
  // The earlier block was itself top-cut by a first block, so the line ending at .36 is
  // split across two earlier pieces: no SINGLE earlier piece shows it entirely. Block 3
  // must keep its repeat rather than cut (conservative stop at the first unproven line).
  val first=tz(1,Box(.1f,.20f,.399f,.355f),listOf(.30f,.32f,.34f))
  val second=tz(2,Box(.1f,.30f,.399f,.385f),listOf(.36f,.38f))
  val third=tz(3,Box(.1f,.34f,.4f,.60f),listOf(.36f,.38f,.40f,.42f))
  val out=ContinuousLayout.bands(listOf(first,second,third))
  val p3=out.filter{it.zoneId==3}.flatMap{it.pieces}
  assertEquals(third.box.area,p3.sumOf{it.area.toDouble()}.toFloat(),.000001f)
 }
}
