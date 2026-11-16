package com.pdffocus.reader
import org.junit.Assert.*
import org.junit.Test
class AutoGroupingTest {
 private fun z(i:Int,b:Box,kind:Kind=Kind.TEXT)=Zone(i,b,kind)
 @Test fun nestedHeaderBecomesOneSuggestedGroupNotAnImmediateMutation(){
  val zones=listOf(z(1,Box(.1f,.1f,.6f,.15f)),z(2,Box(.1f,.17f,.7f,.2f)),z(3,Box(.02f,.03f,.98f,.24f),Kind.IMAGE))
  val groups=AutoGrouping.propose(zones);assertEquals(1,groups.size);assertEquals(setOf(1,2,3),groups.single().ids)
  assertEquals(3,zones.size);assertEquals(zones,AutoGrouping.apply(zones,listOf(2,1),emptyList()).zones)
  val applied=AutoGrouping.apply(zones,listOf(2,1),groups);assertEquals(1,applied.zones.size);assertEquals(Kind.IMAGE,applied.zones.single().kind);assertEquals(1,applied.order.size)
 }
 @Test fun titleAndBodyAreGroupedPerColumnNeverAcrossGutter(){
  val zones=(0..1).flatMap{x->val left=.05f+x*.48f;listOf(z(1+x*2,Box(left,.1f,left+.2f,.14f)),z(2+x*2,Box(left,.15f,left+.4f,.5f)))}
  val groups=AutoGrouping.propose(zones);assertEquals(2,groups.size);assertTrue(groups.all{it.ids.size==2&&it.box.width<.45f})
  val chosen=groups.take(1);val result=AutoGrouping.apply(zones,listOf(4,1,2,3),chosen)
  assertEquals(3,result.zones.size);assertEquals(4,result.order.first());assertEquals(3,result.order.last())
 }
 @Test fun independentPanelsAndManualEditsStaySeparate(){
  val frames=(0..2).flatMap{y->(0..1).map{x->z(1+x+y*2,Box(.05f+x*.48f,.1f+y*.28f,.48f+x*.48f,.36f+y*.28f),Kind.IMAGE)}}
  assertTrue(AutoGrouping.propose(frames).isEmpty())
  assertTrue(AutoGrouping.propose(listOf(frames[0],frames[0].copy(id=9,manual=true))).isEmpty())
 }
 @Test fun unselectedGroupsDoNotEnterReadingOrder(){
  val zones=listOf(z(1,Box(.1f,.1f,.3f,.2f)),z(2,Box(.08f,.08f,.4f,.25f)))
  assertTrue(AutoGrouping.apply(zones,emptyList(),AutoGrouping.propose(zones)).order.isEmpty())
 }

 @Test fun proposalsAreLimitedToChosenZones(){
  val zones=(0..1).flatMap{x->val left=.05f+x*.48f;listOf(z(1+x*2,Box(left,.1f,left+.2f,.14f)),z(2+x*2,Box(left,.15f,left+.4f,.5f)))}
  assertEquals(2,AutoGrouping.propose(zones).size)
  assertEquals(listOf(setOf(1,2)),AutoGrouping.propose(zones,setOf(1,2)).map{it.ids})
  assertTrue(AutoGrouping.propose(zones,setOf(1,3)).isEmpty())
  assertTrue(AutoGrouping.propose(zones,emptySet()).isEmpty())
 }
 @Test fun groupPaletteIsSixDistinctColoursAwayFromSelectionAndGrey(){
  val colors=AutoGrouping.GroupColors;assertEquals(6,colors.size);assertEquals(colors.size,colors.toSet().size)
  fun ch(c:Int)=intArrayOf((c shr 16)and 255,(c shr 8)and 255,c and 255)
  fun dist(a:Int,b:Int)=ch(a).zip(ch(b)).sumOf{(x,y)->kotlin.math.abs(x-y)}
  val forbidden=listOf(0xFF257A64,0xFF326D5D,0xFF5A6262).map{it.toInt()}
  colors.forEach{c->forbidden.forEach{f->assertTrue("palette colour ${Integer.toHexString(c)} too close to ${Integer.toHexString(f)}",dist(c,f)>=80)}}
 }
 @Test fun indentedParagraphCanJoinItsHeadingButSpanningTitleCannotJoinBothColumns(){
  val heading=z(1,Box(.06f,.1f,.29f,.14f));val body=z(2,Box(.13f,.15f,.88f,.22f))
  assertEquals(1,AutoGrouping.propose(listOf(heading,body)).size)
  val title=z(1,Box(.05f,.1f,.95f,.16f));val left=z(2,Box(.05f,.17f,.48f,.5f));val right=z(3,Box(.52f,.17f,.95f,.5f))
  assertTrue(AutoGrouping.propose(listOf(title,left,right)).none{2 in it.ids&&3 in it.ids})
 }
}
