package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class ComicPagePartitionTest{
 private val panels=listOf(Box(.04f,.12f,.31f,.36f),Box(.33f,.12f,.96f,.36f),Box(.04f,.38f,.66f,.62f),Box(.68f,.38f,.96f,.62f),Box(.04f,.64f,.49f,.9f),Box(.51f,.64f,.96f,.9f)).mapIndexed{i,b->Zone(i+1,b,Kind.IMAGE,panel=true)}
 private fun complete(p:ComicPagePartition.Proposal){
  val boxes=p.zones.map{it.box};assertEquals(1f,boxes.sumOf{it.area.toDouble()}.toFloat(),.00001f)
  for(i in boxes.indices)for(j in i+1 until boxes.size)assertEquals(0f,boxes[i].intersect(boxes[j]),.000001f)
  for(y in 0..100)for(x in 0..100){val px=(x+.33f)/101;val py=(y+.33f)/101;assertEquals("Every source point, including gutters and margins, must be covered once",1,boxes.count{it.contains(px,py)})}
 }
 @Test fun sixPanelsPlusTitleAndFooterCoverWholePage(){val p=ComicPagePartition.propose(panels,700,1100);complete(p);assertTrue(p.framed);for(z in panels)assertTrue(p.zones.any{it.box.intersect(z.box)>z.box.area-.000001f});assertEquals(8,p.zones.size)}
 @Test fun framelessPageStillHasNoMissingDrawingOrText(){val p=ComicPagePartition.propose(emptyList(),700,1900);complete(p);assertTrue(p.uncertain);assertTrue(p.zones.size>1)}
 @Test fun overlappingAndNestedAnchorsFallbackWithoutDuplicatingPixels(){complete(ComicPagePartition.propose(panels+panels.first().copy(id=9,box=Box(.1f,.15f,.25f,.3f)),700,1800))}
 @Test fun borderlessScanChoosesQuietSeamNearTarget(){val p=ComicPagePartition.propose(emptyList(),700,1800){y->kotlin.math.abs(y-.53f)};complete(p);assertEquals(.53f,p.zones.first().box.bottom,.01f)}
 @Test fun fullHeightAndStackedPanelsRemainWhole(){val boxes=listOf(Box(.03f,.03f,.4f,.97f),Box(.42f,.03f,.97f,.49f),Box(.42f,.51f,.97f,.97f));val p=ComicPagePartition.propose(boxes.mapIndexed{i,b->Zone(i+1,b,Kind.IMAGE,panel=true)},700,1200);complete(p);for(b in boxes)assertTrue(p.zones.any{it.box.intersect(b)>b.area-.000001f})}
}
