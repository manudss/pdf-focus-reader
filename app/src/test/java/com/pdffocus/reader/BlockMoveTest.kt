package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class BlockMoveTest {
 @Test fun movedBlockGoesRightAfterTheChosenTarget(){assertEquals(listOf(1,2,4,5,3,6),BlockMove.after(listOf(1,2,3,4,5,6),3,5))}
 @Test fun movingBackwardsShiftsFollowingBlocksUp(){assertEquals(listOf(1,4,2,3),BlockMove.after(listOf(1,2,3,4),4,1))}
 @Test fun firstBlockCanMoveToTheEnd(){assertEquals(listOf(2,3,1),BlockMove.after(listOf(1,2,3),1,3))}
 @Test fun blockAlreadyAfterTheTargetStaysPut(){assertEquals(listOf(1,2,3),BlockMove.after(listOf(1,2,3),2,1))}
 @Test fun choosingTheBlockItselfChangesNothing(){assertEquals(listOf(1,2,3),BlockMove.after(listOf(1,2,3),2,2))}
 @Test fun unknownBlockOrTargetLeavesTheOrderUntouched(){val order=listOf(1,2,3);assertEquals(order,BlockMove.after(order,9,2));assertEquals(order,BlockMove.after(order,2,9))}
 @Test fun aSingleBlockOrderIsStable(){assertEquals(listOf(7),BlockMove.after(listOf(7),7,7))}
}
