package com.pdffocus.reader
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
/** Seam-cut panel detection. All pages are invented: flat colours, scribbles and dashed
 * pseudo-text on a paper colour. No real comic content. */
class SeamPanelsTest {
 private val w=900;private val h=1200
 private val PAPER=0xfff5f0e8.toInt()
 private fun blank()=IntArray(w*h){PAPER}
 /** Opaque "artwork": a solid panel colour plus dark scribbles. */
 private fun art(p:IntArray,l:Int,t:Int,r:Int,b:Int,color:Int,seed:Int){
  val rnd=Random(seed)
  for(y in t until b)for(x in l until r)p[y*w+x]=color
  for(i in 0 until 60){
   val x0=l+rnd.nextInt(maxOf(1,r-l-40));val y0=t+rnd.nextInt(maxOf(1,b-t-20))
   val x1=(x0+rnd.nextInt(10,60)).coerceAtMost(r-1);val y1=(y0+rnd.nextInt(-15,16)).coerceIn(t,b-1)
   for(yy in minOf(y0,y1)..maxOf(y0,y1))for(xx in minOf(x0,x1)..maxOf(x0,x1))p[yy*w+xx]=0xff141419.toInt()
  }
 }
 /** Unbordered pseudo-text: dashed dark strokes grouped in lines, like lettering. */
 private fun caption(p:IntArray,x:Int,y:Int,lines:Int,wid:Int=340){
  val rnd=Random(9)
  for(i in 0 until lines){
   val yy=y+i*34;val ln=if(i<lines-1)wid else wid/2;var cx=x
   while(cx<x+ln){
    val seg=minOf(rnd.nextInt(9,15),x+ln-cx)
    for(dy in -6..6)for(dx in 0 until seg)p[(yy+dy)*w+cx+dx]=0xff1e1e1e.toInt()
    cx+=seg+rnd.nextInt(4,7)
   }
  }
 }
 /** Bordered white balloon with a tail and pseudo-text. */
 private fun balloon(p:IntArray,l:Int,t:Int,r:Int,b:Int,tailX:Int,tailY:Int){
  for(y in t until b)for(x in l until r)p[y*w+x]=-1
  for(x in l until r){for(d in 0 until 4){p[(t+d)*w+x]=0xff0f0f0f.toInt();p[(b-1-d)*w+x]=0xff0f0f0f.toInt()}}
  for(y in t until b){for(d in 0 until 4){p[y*w+l+d]=0xff0f0f0f.toInt();p[y*w+r-1-d]=0xff0f0f0f.toInt()}}
  caption(p,l+30,t+28,3,(r-l)-60)
  for(i in 0 until 90){val f=i/90f;val cx=(l+(r-l)/2+(tailX-(l+(r-l)/2))*f).toInt();val cy=(b+(tailY-b)*f).toInt()
   for(dy in -1..1)for(dx in -1..1)p[(cy+dy)*w+cx+dx]=0xff0f0f0f.toInt()}
 }
 /** Every dark pixel of the region must lie inside one single returned box. */
 private fun pixelsInsideOneBox(p:IntArray,boxes:List<Box>,l:Int,t:Int,r:Int,b:Int):Boolean{
  val dark=(0 until (b-t)*(r-l)).mapNotNull{val x=l+it%(r-l);val y=t+it/(r-l);if(p[y*w+x]==0xff1e1e1e.toInt()||p[y*w+x]==0xff0f0f0f.toInt())x to y else null}
  if(dark.isEmpty())return false
  return boxes.any{bx->
   dark.all{(x,y)->x>=bx.left*w-1&&x<=bx.right*w+1&&y>=bx.top*h-1&&y<=bx.bottom*h+1}
  }
 }
 @Test fun fourPanelsWithThreePixelGuttersAreFound(){
  val p=blank();val gw=3
  art(p,60,60,440,560,0xff506478.toInt(),1);art(p,463,60,840,560,0xff785050.toInt(),2)
  art(p,60,583,440,1150,0xff506450.toInt(),3);art(p,463,583,840,1150,0xff645078.toInt(),4)
  val found=SeamPanels.find(p,w,h)
  assertEquals(4,found.size)
 }
 @Test fun balloonCrossingAGutterIsNotSliced(){
  val p=blank()
  art(p,60,60,840,520,0xff506478.toInt(),1);art(p,60,640,840,1150,0xff785050.toInt(),2)
  // balloon hangs from the upper panel across the gutter; its tail tip sits in the paper
  // gap, so a clean detour exists below it
  balloon(p,250,460,560,600,430,614)
  val found=SeamPanels.find(p,w,h)
  assertEquals(2,found.size)
  assertTrue("the balloon must stay inside one panel",pixelsInsideOneBox(p,found,240,450,570,630))
 }
 @Test fun balloonTailBuriedInOtherPanelRefusesTheCut(){
  val p=blank()
  art(p,60,60,840,520,0xff506478.toInt(),1);art(p,60,620,840,1150,0xff785050.toInt(),2)
  // tail tip buried in the lower panel's art: no clean cut exists, refuse rather than slice
  balloon(p,250,470,560,610,430,780)
  assertTrue(SeamPanels.find(p,w,h).isEmpty())
 }
 @Test fun balloonStraddlingTwoPanelsKeepsThemMergedInsteadOfSlicing(){
  val p=blank()
  art(p,60,60,840,560,0xff506478.toInt(),1);art(p,60,620,840,1150,0xff785050.toInt(),2)
  // balloon drawn over BOTH panels' art: no clean cut exists, refuse rather than slice
  balloon(p,250,500,650,660,700,500)
  assertTrue(SeamPanels.find(p,w,h).isEmpty())
 }
 @Test fun openCaptionOverArtworkStaysWhole(){
  val p=blank()
  art(p,60,60,840,560,0xff50645a.toInt(),3);art(p,60,640,840,1150,0xff64506e.toInt(),4)
  caption(p,280,530,3) // unbordered lettering straddling the boundary, paper gap below
  val found=SeamPanels.find(p,w,h)
  assertEquals(2,found.size)
  assertTrue("the caption must not be split between zones",pixelsInsideOneBox(p,found,270,520,640,620))
 }
 @Test fun ruledTableAndPlainTextAreNotComics(){
  val table=blank()
  for(i in 0..12){val y=100+i*80;for(x in 60 until 840)for(d in 0 until 2)table[(y+d)*w+x]=0xff202020.toInt()}
  for(j in 0..4){val x=60+j*195;for(y in 100 until 100+12*80)for(d in 0 until 2)table[y*w+x+d]=0xff202020.toInt()}
  assertTrue(SeamPanels.find(table,w,h).isEmpty())
  val text=blank()
  caption(text,90,120,30,720)
  assertTrue(SeamPanels.find(text,w,h).isEmpty())
 }
 @Test fun slantedGutterPanelsAreFound(){
  val p=blank()
  art(p,60,60,470,1150,0xff506478.toInt(),1)
  art(p,530,60,840,1150,0xff785050.toInt(),2)
  // slant the boundary: shift the lower part of both panels by 60px
  for(y in 700 until 1150)for(x in 60 until 470)p[y*w+x]=PAPER
  art(p,120,700,530,1150,0xff506478.toInt(),1)
  for(y in 700 until 1150)for(x in 530 until 840)p[y*w+x]=PAPER
  art(p,590,700,900-60,1150,0xff785050.toInt(),2)
  assertEquals(2,SeamPanels.find(p,w,h).size)
 }
 @Test fun panelsEntryPointUsesSeamCutsBehindFrames(){
  val p=blank()
  art(p,60,60,440,560,0xff506478.toInt(),1);art(p,463,60,840,560,0xff785050.toInt(),2)
  art(p,60,583,440,1150,0xff506450.toInt(),3);art(p,463,583,840,1150,0xff645078.toInt(),4)
  assertEquals(4,ComicGutters.panels(p,w,h,true).size)
  assertTrue(ComicGutters.panels(p,w,h,false).isEmpty())
 }
}
