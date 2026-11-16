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
}
