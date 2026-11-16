package com.pdffocus.reader
import org.junit.Assert.*
import org.junit.Test
class ComicPanelsTest {
 private val w=400;private val h=600
 private fun image(frames:List<Box>,drawn:Boolean=true,color:Boolean=false):IntArray{
  val p=IntArray(w*h){-1}
  fun rect(l:Int,t:Int,r:Int,b:Int,c:Int){for(y in t.coerceAtLeast(0)..b.coerceAtMost(h-1))for(x in l.coerceAtLeast(0)..r.coerceAtMost(w-1))p[y*w+x]=c}
  for(b in frames){val l=(b.left*w).toInt();val r=(b.right*w).toInt();val t=(b.top*h).toInt();val d=(b.bottom*h).toInt()
   if(color)rect(l,t,r,d,0xffeeddad.toInt())
   rect(l,t,r,t+2,0xff202020.toInt());rect(l,d-2,r,d,0xff202020.toInt());rect(l,t,l+2,d,0xff202020.toInt());rect(r-2,t,r,d,0xff202020.toInt())
   if(drawn){val cx=(l+r)/2;val cy=(t+d)/2;rect(cx-12,cy-10,cx+12,cy+30,0xff303030.toInt());rect(l+8,t+9,r-8,t+12,0xff303030.toInt())}
  };return p
 }
 private val six=listOf(Box(.05f,.1f,.3f,.35f),Box(.32f,.1f,.95f,.35f),Box(.05f,.38f,.68f,.63f),Box(.7f,.38f,.95f,.63f),Box(.05f,.66f,.48f,.95f),Box(.5f,.66f,.95f,.95f))
 @Test fun variedWidthPanelsAreWholeImagesInColorAndMonochrome(){for(color in listOf(false,true)){
  val found=ComicPanels.find(image(six,color=color),w,h);assertEquals(6,found.size)
  for(b in six)assertTrue(found.any{it.intersect(b)/b.area>.95f})
 }}
 @Test fun equalGridPanelsAreSupported(){val grid=(0..2).flatMap{y->(0..1).map{x->Box(.05f+x*.46f,.08f+y*.3f,.48f+x*.46f,.35f+y*.3f)}};assertEquals(6,ComicPanels.find(image(grid),w,h).size)}
 @Test fun refusesEmptyTablesSingleFramesSparseFramesAndCancellation(){
  assertTrue(ComicPanels.find(image(six,false),w,h).isEmpty())
  assertTrue(ComicPanels.find(image(listOf(Box(.05f,.1f,.95f,.95f))),w,h).isEmpty())
  assertTrue(ComicPanels.find(image(six.take(2)),w,h).isEmpty())
  assertTrue(ComicPanels.find(image(six),w,h){true}.isEmpty())
 }
 @Test fun nestedObjectsDoNotBecomeExtraPanels(){val b=six+Box(.37f,.16f,.85f,.32f);val found=ComicPanels.find(image(b),w,h);assertEquals(6,found.size)}
 @Test fun thinGrayFramesWithLargeMarginsAndCaptionStripsStayWhole(){
  for(margin in listOf(.12f,.22f))for(stroke in listOf(1,2)){
   val frames=(0..2).flatMap{row->(0..1).map{col->Box(margin+col*(1-2*margin)/2,.2f+row*.19f,margin+(col+1)*(1-2*margin)/2-.012f,.38f+row*.19f)}}
   val p=IntArray(w*h){-1}
   fun rect(l:Int,t:Int,r:Int,b:Int,c:Int){for(y in t..b)for(x in l..r)p[y*w+x]=c}
   for(b in frames){val l=(b.left*w).toInt();val r=(b.right*w).toInt();val t=(b.top*h).toInt();val d=(b.bottom*h).toInt();val gray=0xff969696.toInt()
    rect(l,t,r,t+stroke-1,gray);rect(l,d-stroke+1,r,d,gray);rect(l,t,l+stroke-1,d,gray);rect(r-stroke+1,t,r,d,gray)
    // Fictitious captions above abstract drawings, not source artwork or text.
    rect(l+4,t+9,r-4,t+10,0xff303030.toInt());rect(l+4,t+18,r-10,t+19,0xff303030.toInt())
    rect(l+12,t+34,r-12,d-10,0xff665544.toInt())
   }
   val found=ComicPanels.find(p,w,h);assertEquals("margin=$margin stroke=$stroke",6,found.size)
   for(b in frames)assertTrue(found.any{it.intersect(b)/b.area>.97f})
  }
 }
 @Test fun smallScanDropoutsAreBridgedButOpenFramesAreRejected(){
  val p=image(six)
  for(b in six){val x=((b.left+b.right)*w/2).toInt();val y=(b.top*h).toInt();for(dy in 0..2)p[(y+dy)*w+x]=-1}
  assertEquals(6,ComicPanels.find(p,w,h).size)
  val open=image(six);for(b in six){val x=(b.left*w).toInt();for(y in ((b.top+.04f)*h).toInt()..((b.bottom-.04f)*h).toInt())for(dx in 0..2)open[y*w+x+dx]=-1}
  assertTrue(ComicPanels.find(open,w,h).isEmpty())
 }
 @Test fun textOnlyRuledFormsDoNotBecomeComics(){
  val p=image(six,false)
  for(b in six){val l=(b.left*w).toInt()+8;val r=(b.right*w).toInt()-8;for(y in (b.top*h).toInt()+12 until (b.bottom*h).toInt()-8 step 10)for(x in l..r)for(dy in 0..2)p[(y+dy)*w+x]=0xff333333.toInt()}
  assertTrue(ComicPanels.find(p,w,h).isEmpty())
 }

 @Test fun solidMastheadAboveFramesDoesNotBecomeAnExtraPanel(){
  val p=image(six,color=true)
  for(y in 0..45)for(x in 0 until w)p[y*w+x]=0xff214c3b.toInt()
  assertEquals(6,ComicPanels.find(p,w,h).size)
 }
}
