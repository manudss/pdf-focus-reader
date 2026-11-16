package com.pdffocus.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** AutoOrder geometry is entirely fictional: invented blocks, never real documents. */
class AutoOrderTest {
    private fun zone(id:Int,l:Float,t:Float,r:Float,b:Float,kind:Kind=Kind.TEXT)=Zone(id,Box(l,t,r,b),kind)

    @Test fun emptyPageProposesNothing(){
        assertEquals(emptyList<Int>(),AutoOrder.propose(emptyList()))
    }

    @Test fun singleColumnReadsTopDown(){
        val zones=listOf(zone(1,.08f,.05f,.92f,.07f),zone(2,.08f,.10f,.92f,.50f),zone(3,.08f,.55f,.92f,.90f))
        assertEquals(listOf(1,2,3),AutoOrder.propose(zones))
    }

    @Test fun wideShortTitleReadsBeforeItsThreeColumns(){
        // Fictional magazine: a wide short title above three tall columns.
        val zones=listOf(
            zone(1,.06f,.12f,.82f,.16f),              // title, short and spanning all columns
            zone(2,.06f,.20f,.33f,.60f),zone(3,.37f,.20f,.64f,.60f),zone(4,.68f,.20f,.95f,.60f),
            zone(5,.06f,.65f,.33f,.92f),zone(6,.37f,.65f,.64f,.92f),zone(7,.68f,.65f,.95f,.92f))
        assertEquals(listOf(1,2,5,3,6,4,7),AutoOrder.propose(zones))
    }

    @Test fun narrowColumnUnderWideTitleKeepsItsPlace(){
        // Fictional asymmetric layout: narrow left column, wide right column, wide short title on top.
        val zones=listOf(
            zone(1,.06f,.12f,.82f,.24f),              // two-line title, still short overall
            zone(2,.06f,.25f,.33f,.60f),zone(3,.37f,.25f,.93f,.60f),
            zone(4,.06f,.62f,.33f,.91f),zone(5,.37f,.62f,.93f,.91f))
        assertEquals(listOf(1,2,4,3,5),AutoOrder.propose(zones))
    }

    @Test fun titleBetweenSectionsSeparatesTheBands(){
        val zones=listOf(
            zone(1,.06f,.05f,.50f,.30f),zone(2,.54f,.05f,.95f,.30f),   // first band, two columns
            zone(3,.06f,.34f,.95f,.38f),                                // mid-page wide short title
            zone(4,.06f,.42f,.50f,.80f),zone(5,.54f,.42f,.95f,.80f))   // second band
        assertEquals(listOf(1,2,3,4,5),AutoOrder.propose(zones))
    }

    @Test fun shortFolioIsProposedNeverExcluded(){
        // A tiny page-number block at the bottom must stay in the proposal, attached to the nearest column.
        val zones=listOf(
            zone(1,.06f,.12f,.82f,.16f),
            zone(2,.06f,.20f,.50f,.80f),zone(3,.54f,.20f,.95f,.80f),
            zone(4,.48f,.95f,.55f,.97f))
        val proposal=AutoOrder.propose(zones)
        assertEquals(4,proposal.size)
        assertTrue(4 in proposal)
        assertEquals(listOf(1,2,3,4),proposal)
    }

    @Test fun imageStaysWithItsColumn(){
        val zones=listOf(
            zone(1,.06f,.12f,.82f,.16f),
            zone(2,.06f,.20f,.50f,.45f),zone(3,.06f,.47f,.50f,.75f,Kind.IMAGE),zone(4,.54f,.20f,.95f,.75f))
        assertEquals(listOf(1,2,3,4),AutoOrder.propose(zones))
    }
}
