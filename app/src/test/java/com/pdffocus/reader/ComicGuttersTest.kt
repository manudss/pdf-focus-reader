package com.pdffocus.reader
import org.junit.Assert.*
import org.junit.Test
/** Fictional synthetic pages only: coloured shapes on a paper colour, no real comic content. */
class ComicGuttersTest {
 private val w=600;private val h=840
 private val six=listOf(Box(.06f,.05f,.46f,.30f),Box(.50f,.05f,.94f,.30f),Box(.06f,.34f,.60f,.64f),Box(.64f,.34f,.94f,.64f),Box(.06f,.68f,.35f,.95f),Box(.39f,.68f,.94f,.95f))
 private fun page(boxes:List<Box>,paper:Int=-1,tint:Int=0xff3a78b0.toInt()):IntArray{
  val p=IntArray(w*h){paper}
  for((i,b) in boxes.withIndex()){
   val l=(b.left*w).toInt();val r=(b.right*w).toInt();val t=(b.top*h).toInt();val d=(b.bottom*h).toInt()
   for(y in t until d)for(x in l until r)p[y*w+x]=if(((x/9+y/7+i)%5)==0)0xff803020.toInt() else tint
  }
  return p
 }
 private fun covers(found:List<Box>,expected:List<Box>)=expected.all{e->found.any{it.intersect(e)/(it.area+e.area-it.intersect(e))>.8f}}
 @Test fun framelessPanelsOnWhiteAndYellowedPaperAreFound(){
  for(paper in listOf(-1,0xfff2e2b8.toInt())){
   val found=ComicGutters.find(page(six,paper),w,h)
   assertEquals(6,found.size);assertTrue(covers(found,six))
  }
 }
 @Test fun panelsKeepClosedFramesWhenTheyExistAndGuttersAreOptional(){
  val framed=IntArray(400*600){-1}
  fun rect(l:Int,t:Int,r:Int,b:Int){for(y in t..b)for(x in l..r)framed[y*400+x]=0xff202020.toInt()}
  val grid=(0..2).flatMap{y->(0..1).map{x->Box(.05f+x*.46f,.08f+y*.3f,.48f+x*.46f,.35f+y*.3f)}}
  for(b in grid){val l=(b.left*400).toInt();val r=(b.right*400).toInt();val t=(b.top*600).toInt();val d=(b.bottom*600).toInt()
   rect(l,t,r,t+2);rect(l,d-2,r,d);rect(l,t,l+2,d);rect(r-2,t,r,d);rect((l+r)/2-12,(t+d)/2-10,(l+r)/2+12,(t+d)/2+30)}
  assertEquals(ComicPanels.find(framed,400,600),ComicGutters.panels(framed,400,600,true))
 }
 @Test fun guttersAreOffUnlessTheModeAsksForThem(){
  assertTrue(ComicGutters.panels(page(six),w,h,false).isEmpty())
  assertEquals(6,ComicGutters.panels(page(six),w,h,true).size)
 }
 @Test fun wholePageArtworkBlankPagesAndSinglePanelsAreNotCut(){
  assertTrue(ComicGutters.find(page(listOf(Box(.04f,.04f,.96f,.96f))),w,h).isEmpty())
  assertTrue(ComicGutters.find(IntArray(w*h){-1},w,h).isEmpty())
  assertTrue(ComicGutters.find(page(listOf(Box(.1f,.1f,.9f,.45f))),w,h).isEmpty())
 }
 @Test fun touchingPanelsWithoutGutterStayWholeInsteadOfBeingGuessed(){
  val touching=listOf(Box(.06f,.1f,.5f,.9f),Box(.5f,.1f,.94f,.9f))
  assertTrue(ComicGutters.find(page(touching),w,h).isEmpty())
 }
 @Test fun narrowPageAndCancellationReturnNothing(){
  assertTrue(ComicGutters.find(IntArray(60*60){-1},60,60).isEmpty())
  assertTrue(ComicGutters.find(page(six),w,h){true}.isEmpty())
 }
 @Test fun proposedPanelsFeedThePartitionAsAnWholePageTiling(){
  val anchors=ComicGutters.find(page(six),w,h).mapIndexed{i,b->Zone(i+1,b,Kind.IMAGE,panel=true)}
  val zones=ComicPagePartition.propose(anchors,w,h).zones
  assertEquals(1.0,zones.sumOf{it.box.area.toDouble()},.001)
  assertTrue(zones.size>=6)
 }
}
