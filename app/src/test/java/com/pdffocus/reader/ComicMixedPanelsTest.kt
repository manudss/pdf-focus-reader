package com.pdffocus.reader
import org.junit.Assert.*
import org.junit.Test
/** Invented mixed-layout pages only, no private source pixels. */
class ComicMixedPanelsTest {
 @Test fun threeClosedFramesDoNotSuppressThreeFramelessPanelsElsewhere(){
  val w=600;val h=840;val p=IntArray(w*h){-1}
  fun rect(l:Int,t:Int,r:Int,b:Int,c:Int){for(y in t until b)for(x in l until r)p[y*w+x]=c}
  for(row in 0..1)for(col in 0..2){val l=40+col*180;val t=100+row*330;val r=l+165;val b=t+300
   rect(l,t,r,b,0xffe8cf90.toInt());rect(l+40,t+70,l+100,t+220,0xff406655.toInt())
   if(row==0){rect(l,t,r,t+2,0xff202020.toInt());rect(l,b-2,r,b,0xff202020.toInt());rect(l,t,l+2,b,0xff202020.toInt());rect(r-2,t,r,b,0xff202020.toInt())}
  }
  val framed=ComicPanels.find(p,w,h);assertEquals(3,framed.size)
  assertEquals(6,ComicGutters.find(p,w,h).size)
  val all=ComicGutters.panels(p,w,h,true);assertEquals(6,all.size)
  assertTrue("The three reliable frames must stay unchanged",all.containsAll(framed))
  assertEquals(framed,ComicGutters.panels(p,w,h,false))
 }
 @Test fun aGroupFrameCanBeRefinedOnlyByHighCoverageSeparatedSubpanels(){
  val frame=Box(.1f,.2f,.9f,.8f)
  val a=Box(.1f,.2f,.49f,.8f);val b=Box(.51f,.2f,.9f,.8f)
  assertEquals(listOf(a,b),ComicGutters.reconcile(listOf(frame),listOf(a,b)))
  val inset=Box(.2f,.3f,.4f,.5f)
  assertEquals(listOf(frame),ComicGutters.reconcile(listOf(frame),listOf(inset)))
  assertEquals(listOf(frame),ComicGutters.reconcile(listOf(frame),listOf(inset,Box(.6f,.3f,.7f,.5f))))
 }
 @Test fun crossingOrOverlappingAlternativesDoNotReplaceClosedFrames(){
  val frame=Box(.1f,.1f,.5f,.5f)
  val crossing=Box(.3f,.3f,.9f,.9f)
  assertEquals(listOf(frame),ComicGutters.reconcile(listOf(frame),listOf(crossing)))
  val inside1=Box(.1f,.1f,.4f,.5f);val inside2=Box(.3f,.1f,.5f,.5f)
  assertEquals(listOf(frame),ComicGutters.reconcile(listOf(frame),listOf(inside1,inside2)))
 }
 @Test fun emptyAlternativeAndCancellationKeepSafeResults(){
  val frame=Box(.1f,.1f,.5f,.5f)
  assertEquals(listOf(frame),ComicGutters.reconcile(listOf(frame),emptyList()))
  assertEquals(listOf(frame),ComicGutters.reconcile(emptyList(),listOf(frame)))
  assertTrue(ComicGutters.panels(IntArray(200*300){-1},200,300,true){true}.isEmpty())
 }
}
