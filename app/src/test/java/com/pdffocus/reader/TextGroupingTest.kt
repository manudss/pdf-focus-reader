package com.pdffocus.reader
import org.junit.Assert.*
import org.junit.Test
class TextGroupingTest {
 private fun z(id:Int,l:Float,t:Float,r:Float,b:Float)=Zone(id,Box(l,t,r,b),Kind.TEXT)
 @Test fun joinsSameColumnButNotAdjacentColumns(){val a=z(1,.05f,.2f,.22f,.4f);val b=z(2,.05f,.405f,.22f,.6f);val c=z(3,.25f,.2f,.42f,.6f);val out=TextGrouping.merge(listOf(a,b,c));assertEquals(2,out.size);assertTrue(out.any{it.box==Box(.05f,.2f,.22f,.6f)})}
 @Test fun absorbsShortLineWithoutDuplicateCoveredRegion(){val a=z(1,.05f,.2f,.22f,.4f);val b=z(2,.10f,.405f,.22f,.42f);val c=z(3,.05f,.423f,.22f,.6f);assertEquals(1,TextGrouping.merge(listOf(a,b,c)).size)}
 @Test fun preservesPicturesAndLargeGaps(){val a=z(1,.05f,.2f,.22f,.4f);val b=z(2,.05f,.5f,.22f,.6f);val p=a.copy(id=3,kind=Kind.IMAGE);assertEquals(3,TextGrouping.merge(listOf(a,b,p)).size)}
 @Test fun doesNotUseWideHeadingToBridgeColumns(){val a=z(1,.05f,.2f,.22f,.4f);val b=z(2,.25f,.2f,.42f,.4f);val title=z(3,.05f,.15f,.42f,.195f);assertEquals(3,TextGrouping.merge(listOf(a,b,title)).size)}
}
