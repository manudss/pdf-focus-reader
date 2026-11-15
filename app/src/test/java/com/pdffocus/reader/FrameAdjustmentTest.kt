package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class FrameAdjustmentTest {
 private val b=Box(.2f,.3f,.5f,.7f)
 @Test fun verySmallDetectedFrameCanStillBeAdjusted(){val small=Box(0f,0f,.003f,.004f);val r=FrameAdjustment.change(small,-1,-1,-.01f,-.01f);assertTrue(r.width>0&&r.height>0)}
 @Test fun bottomHandleLeavesAllOtherEdgesAlone(){val r=FrameAdjustment.change(b,0,1,0f,-.12f);assertEquals(b.left,r.left);assertEquals(b.top,r.top);assertEquals(b.right,r.right);assertEquals(.58f,r.bottom,.00001f)}
 @Test fun movementKeepsSizeAndCannotLoseFrame(){val r=FrameAdjustment.change(b,0,0,2f,-2f,true);assertEquals(b.width,r.width,.00001f);assertEquals(b.height,r.height,.00001f);assertEquals(1f,r.right);assertEquals(0f,r.top)}
 @Test fun allEightHandlesStayInPageAndNeverInvert(){for(x in -1..1)for(y in -1..1)if(x!=0||y!=0)for(delta in listOf(-2f,2f)){val r=FrameAdjustment.change(b,x,y,delta,delta);assertTrue(r.width>.007f&&r.height>.007f);assertTrue(r.left>=0&&r.top>=0&&r.right<=1&&r.bottom<=1)}}
}
