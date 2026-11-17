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

    @Test fun labelStripsDoNotInterleaveColumns(){
        // Fictional magazine where detection split each column into a narrow full-height
        // label strip and stacked text chunks, with a left-aligned title that is not wide
        // enough to span the page. Chained overlap would glue title, strips and chunks of
        // two columns into one pseudo-column and interleave them; columns must stay contiguous.
        val zones=listOf(
            zone(1,.0573f,.1235f,.4078f,.1785f),                          // left-aligned title, 35% wide
            zone(2,.0557f,.1998f,.1284f,.9203f),zone(3,.1224f,.1998f,.3046f,.3457f),  // left strip + upper chunk
            zone(4,.3557f,.1998f,.4332f,.9203f),zone(5,.4272f,.1998f,.6093f,.3457f),  // centre strip + upper chunk
            zone(6,.6557f,.1998f,.7268f,.9203f),zone(7,.7264f,.1998f,.9117f,.9237f),  // right strip + text
            zone(8,.1176f,.3098f,.3078f,.8137f),zone(9,.4224f,.3098f,.6125f,.8137f),  // middle chunks
            zone(10,.1176f,.7778f,.3078f,.9237f),zone(11,.4224f,.7778f,.6125f,.9237f))// bottom chunks
        assertEquals(listOf(1,2,3,8,10,4,5,9,11,6,7),AutoOrder.propose(zones))
    }

    @Test fun twoByTwoNewspaperReadsLeftColumnFirst(){
        // Fictional two-page-column layout: title above two columns split mid-page.
        val zones=listOf(
            zone(1,.06f,.10f,.75f,.18f),
            zone(2,.06f,.22f,.47f,.55f),zone(3,.53f,.22f,.94f,.55f),
            zone(4,.06f,.60f,.47f,.93f),zone(5,.53f,.60f,.94f,.93f))
        assertEquals(listOf(1,2,4,3,5),AutoOrder.propose(zones))
    }

    @Test fun indentedFragmentReadsWithItsColumn(){
        // A short fragment flush left of its column's text must not become its own column.
        val zones=listOf(
            zone(1,.06f,.10f,.70f,.16f),
            zone(2,.06f,.20f,.15f,.45f),zone(3,.16f,.20f,.33f,.90f),
            zone(4,.40f,.20f,.67f,.90f))
        val proposal=AutoOrder.propose(zones)
        assertEquals(listOf(1,2,3,4),proposal)
    }

    @Test fun continuousColumnsWithGapsReadContiguously(){
        // Fictional continuous prose: columns of uneven height, a mid-column paragraph gap
        // that splits the centre column into stacked pieces, and a one-line fragment at the
        // left column's right edge. No piece may be pulled into a neighbouring column.
        val zones=listOf(
            zone(1,.0573f,.1235f,.4078f,.1785f),                          // left-aligned title, 35% wide
            zone(2,.0557f,.1770f,.3141f,.7621f),                          // left column, tall
            zone(3,.3557f,.1998f,.6355f,.5993f),                          // centre column, upper piece
            zone(4,.6557f,.1998f,.9355f,.9237f),                          // right column, full height
            zone(5,.2764f,.3805f,.3355f,.4018f),                          // one-line fragment at the left column's edge
            zone(6,.3557f,.5978f,.5911f,.7262f),zone(7,.3557f,.7217f,.6355f,.9203f),  // centre column, lower pieces
            zone(8,.0557f,.7576f,.3355f,.9237f))                          // left column, lower piece
        assertEquals(listOf(1,2,5,8,3,6,7,4),AutoOrder.propose(zones))
    }

    @Test fun columnSplitIntoShortPiecesStillReadsContiguously(){
        // A column whose blocks are all short must still form its own column: attaching its
        // pieces to the nearest anchored column would interleave the page.
        val zones=listOf(
            zone(1,.06f,.10f,.33f,.50f),                                  // left column, one tall block
            zone(2,.37f,.10f,.64f,.28f),zone(3,.37f,.30f,.64f,.48f),      // centre column, four
            zone(4,.37f,.50f,.64f,.68f),zone(5,.37f,.70f,.64f,.93f),      // stacked short pieces
            zone(6,.68f,.10f,.95f,.93f))                                  // right column, full height
        assertEquals(listOf(1,2,3,4,5,6),AutoOrder.propose(zones))
    }

    @Test fun imageStaysWithItsColumn(){
        val zones=listOf(
            zone(1,.06f,.12f,.82f,.16f),
            zone(2,.06f,.20f,.50f,.45f),zone(3,.06f,.47f,.50f,.75f,Kind.IMAGE),zone(4,.54f,.20f,.95f,.75f))
        assertEquals(listOf(1,2,3,4),AutoOrder.propose(zones))
    }
}
