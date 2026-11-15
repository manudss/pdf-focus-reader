package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class BlockSplitTest{
 private val z=Zone(7,Box(.1f,.2f,.9f,.8f),Kind.IMAGE,listOf(.3f,.5f,.7f))
 @Test fun splitBothAxesAtExactPositionAndPreserveOrder(){for(vertical in listOf(true,false)){
  val zones=listOf(Zone(1,Box(0f,0f,.1f,.1f),Kind.TEXT),z,Zone(9,Box(.9f,.9f,1f,1f),Kind.TEXT));val order=listOf(9,7,1)
  val result=BlockSplit.apply(zones,order,7,vertical,.3f);val a=result.zones[1];val b=result.zones[2]
  assertEquals(listOf(9,7,10,1),result.order);assertEquals(Kind.IMAGE,b.kind);assertTrue(a.manual&&b.manual)
  assertEquals(z.box.area,a.box.area+b.box.area,.00001f);assertEquals(0f,a.box.intersect(b.box),0f)
  if(vertical){assertEquals(.34f,a.box.right,.00001f);assertEquals(a.box.right,b.box.left,0f)}else{assertEquals(.38f,a.box.bottom,.00001f);assertEquals(a.box.bottom,b.box.top,0f)}
  assertEquals(listOf(9,7,1),order);assertEquals(z,zones[1])
 }}
 @Test fun unselectedSplitStaysUnselected(){assertEquals(listOf(2),BlockSplit.apply(listOf(z),listOf(2),7,false,.7f).order)}
 @Test fun textKindAndLineBoundsArePreserved(){val r=BlockSplit.apply(listOf(z.copy(kind=Kind.TEXT)),listOf(7),7,false,.5f);assertTrue(r.zones.all{it.kind==Kind.TEXT});assertTrue(r.zones.all{p->p.lineBottoms.all{it in p.box.top..p.box.bottom}})}
 @Test(expected=IllegalArgumentException::class)fun rejectEmptyHalf(){BlockSplit.apply(listOf(z),emptyList(),7,true,0f)}
}
