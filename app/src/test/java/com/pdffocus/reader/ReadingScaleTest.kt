package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*

class ReadingScaleTest {
    @Test fun lineHeightUsesMedianOcrLinePitch(){
        val lh=ReadingScale.lineHeight(listOf(.10f,.1105f,.121f,.1315f,.142f),.1f)
        assertEquals(.0105f,lh!!,.0001f)
    }
    @Test fun lineHeightEstimatesALoneLineFromItsBox(){
        assertEquals(.016f,ReadingScale.lineHeight(listOf(.5f),.02f)!!,.0001f)
        assertNull(ReadingScale.lineHeight(emptyList(),.02f))
    }
    @Test fun weightedMedianLetsBodyTextOutvoteLabels(){
        val v=ReadingScale.median(listOf(1f to 1,1.1f to 1,4f to 30,4.1f to 25))
        assertEquals(4f,v!!,.0001f)
    }
    @Test fun sameSizeColumnsAndFragmentShareOneScale(){
        val uniform=4.04f;val bodyLh=.0105f
        val column=ReadingScale.textScale(4.04f,uniform,.0105f,bodyLh)
        val fragment=ReadingScale.textScale(4.03f,uniform,.0105f,bodyLh)
        assertEquals(column,fragment,.02f)
    }
    @Test fun wideTitleStaysWithinScreenWidth(){
        // A wide title needs scale .98 to fit; uniform body scale is 4.04.
        assertEquals(.98f,ReadingScale.textScale(.98f,4.04f,.039f,.0105f),.0001f)
    }
    @Test fun genuinelySmallTextKeepsProportionsAboveAFloor(){
        // Footnote half the body size: renders proportionally, no exaggeration.
        val s=ReadingScale.textScale(4.04f,4.04f,.0052f,.0105f)
        assertEquals(4.04f,s,.0001f) // .0052*4.04 = .021 > floor .019
    }
    @Test fun tinyTextIsLiftedToTheFloorButNeverPastScreenWidth(){
        val s=ReadingScale.textScale(6f,4.04f,.003f,.0105f)
        // floor=.45*.0105*4.04=.0191 -> needs 6.36, capped at fill 6
        assertEquals(6f,s,.0001f)
        val s2=ReadingScale.textScale(4f,4.04f,.003f,.0105f)
        assertEquals(4f,s2,.0001f)
    }
    @Test fun missingLineDataFallsBackToSharedScaleWithinWidth(){
        assertEquals(2f,ReadingScale.textScale(3f,2f,null,null),.0001f)
        assertEquals(2f,ReadingScale.textScale(3f,2f,null,.0105f),.0001f)
    }
}
