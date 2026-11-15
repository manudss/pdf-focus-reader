package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class BlockMergeTest {
 private val zones=(1..6).map{Zone(it,Box(.1f,it*.1f,.3f,it*.1f+.05f),Kind.TEXT)}
 @Test fun automaticPlaceIsEarliestMemberAndNeverInvented(){assertEquals(1,BlockMerge.position(listOf(6,3,1,5),setOf(3,5)));assertNull(BlockMerge.position(listOf(6,1,5),setOf(2,3)))}
 @Test fun independentMembershipPreservesRestAndExplicitPosition(){val result=BlockMerge.apply(zones,listOf(6,3,1,5),setOf(3,4),2,Kind.TEXT);assertEquals(listOf(6,1,result.merged.id,5),result.order);assertEquals(listOf(1,2,5,6),result.zones.filter{it.id!=result.merged.id}.map{it.id});assertTrue(result.merged.manual)}
 @Test fun unselectedMembersCanBeMergedWithoutDiscardingExistingOrder(){val result=BlockMerge.apply(zones,listOf(6,1,5),setOf(2,3),1,Kind.TEXT);assertEquals(listOf(6,result.merged.id,1,5),result.order)}
 @Test fun fusionDoesNotMutateSourceLists(){val original=listOf(6,3,1,5);BlockMerge.apply(zones,original,setOf(3,4),0,Kind.TEXT);assertEquals(listOf(6,3,1,5),original);assertEquals(6,zones.size)}
}
