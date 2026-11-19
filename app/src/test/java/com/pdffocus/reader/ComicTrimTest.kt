package com.pdffocus.reader
import org.junit.Assert.*
import org.junit.Test
/** Fictional synthetic pages only. */
class ComicTrimTest {
 private val w=600;private val h=800
 private fun page(paper:Int,draw:(IntArray)->Unit):IntArray{val p=IntArray(w*h){paper};draw(p);return p}
 private fun fill(p:IntArray,l:Int,t:Int,r:Int,b:Int,c:Int){for(y in t until b)for(x in l until r)p[y*w+x]=c}
 private val whole=Box(0f,0f,1f,1f)
 @Test fun whiteMarginAroundADrawingIsTrimmedButNotItsInsideBackground(){
  val p=page(-1){q->fill(q,150,200,450,600,0xff3a78b0.toInt());fill(q,250,300,350,400,-1)}
  val t=ComicTrim.trim(p,w,h,whole)
  assertEquals(.25f,t.left,.03f);assertEquals(.25f,t.top,.03f);assertEquals(.75f,t.right,.03f);assertEquals(.75f,t.bottom,.03f)
 }
 @Test fun tintedPaperMarginIsTrimmedWithItsOwnBackground(){
  val paper=0xfff2e2b8.toInt()
  val p=page(paper){q->fill(q,100,100,500,700,0xff803020.toInt())}
  val t=ComicTrim.trim(p,w,h,whole)
  assertEquals(100f/w,t.left,.03f);assertEquals(700f/h,t.bottom,.03f)
 }
 @Test fun lightContentIsNotMistakenForMargin(){
  val p=page(-1){q->fill(q,120,120,480,680,0xffd8d0c0.toInt())}
  val t=ComicTrim.trim(p,w,h,whole)
  assertTrue(t.left<=.2f+.03f&&t.top<=.15f+.03f&&t.right>=.8f-.03f&&t.bottom>=.85f-.03f)
 }
 @Test fun textTouchingTheEdgeKeepsItsEdge(){
  val p=page(-1){q->fill(q,200,300,400,500,0xff203040.toInt());fill(q,0,10,300,40,0xff000000.toInt())}
  val t=ComicTrim.trim(p,w,h,whole)
  assertEquals(0f,t.left,.01f);assertTrue(t.top<=.02f)
 }
 @Test fun aTitleAboveTheDrawingIsKept(){
  val p=page(-1){q->fill(q,150,150,450,200,0xff000000.toInt());fill(q,150,260,450,700,0xff3a78b0.toInt())}
  val t=ComicTrim.trim(p,w,h,whole)
  assertTrue(t.top<=150f/h)
 }
 @Test fun blankOrTinyContentKeepsTheDetectedBox(){
  val blank=IntArray(w*h){-1}
  assertEquals(whole,ComicTrim.trim(blank,w,h,whole))
  val dot=page(-1){q->fill(q,300,400,310,410,0)}
  assertEquals(whole,ComicTrim.trim(dot,w,h,whole))
 }
 @Test fun aSubBoxIsTrimmedInsidePageCoordinates(){
  val p=page(-1){q->fill(q,300,100,500,300,0xff3a78b0.toInt())}
  val box=Box(.4f,.05f,.95f,.45f)
  val t=ComicTrim.trim(p,w,h,box)
  assertTrue(t.left>=box.left&&t.right<=box.right&&t.top>=box.top&&t.bottom<=box.bottom)
  assertEquals(300f/w,t.left,.03f);assertEquals(500f/w,t.right,.03f)
 }

 @Test fun blankBandsAreBlankButTitlesAndDrawingsAreNot(){
  val p=page(0xfff2e2b8.toInt()){q->fill(q,300,770,306,782,0xff000000.toInt());fill(q,100,100,500,120,0xff000000.toInt());fill(q,100,300,500,500,0xff3a78b0.toInt())}
  assertTrue(ComicTrim.blank(p,w,h,Box(0f,.9f,1f,1f)))
  assertTrue(ComicTrim.blank(p,w,h,Box(0f,.85f,1f,1f)))
  assertTrue(!ComicTrim.blank(p,w,h,Box(0f,.1f,1f,.2f)))
  assertTrue(!ComicTrim.blank(p,w,h,Box(0f,.3f,1f,.7f)))
 }

 private fun z(id:Int,b:Box,panel:Boolean)=Zone(id,b,Kind.IMAGE,manual=true,panel=panel,complement=!panel)
 @Test fun blankTopAndBottomBandsAreDroppedAndPanelsAreKept(){
  val p=page(-1){q->fill(q,60,160,540,420,0xff3a78b0.toInt());fill(q,60,440,540,700,0xff803020.toInt());fill(q,300,770,306,782,0)}
  val zones=listOf(z(1,Box(0f,0f,1f,.18f),false),z(2,Box(.1f,.18f,.9f,.52f),true),z(3,Box(.1f,.52f,.9f,.88f),true),z(4,Box(0f,.88f,1f,1f),false))
  val out=ComicTrim.withoutBlankBands(zones,p,w,h)
  assertEquals(listOf(2,3),out.map{it.id});assertEquals(zones[1],out[0])
 }
 @Test fun aBandWithATitleIsKeptAndAnAllBlankPageIsLeftAlone(){
  val p=page(-1){q->fill(q,100,30,500,70,0xff000000.toInt());fill(q,60,160,540,700,0xff3a78b0.toInt())}
  val zones=listOf(z(1,Box(0f,0f,1f,.18f),false),z(2,Box(.1f,.18f,.9f,.9f),true))
  assertEquals(listOf(1,2),ComicTrim.withoutBlankBands(zones,p,w,h).map{it.id})
  val blank=IntArray(w*h){-1};val only=listOf(z(1,Box(0f,0f,1f,.5f),false),z(2,Box(0f,.5f,1f,1f),false))
  assertEquals(only,ComicTrim.withoutBlankBands(only,blank,w,h))
 }
 @Test fun aWholePageBlockIsTrimmedAndAPanelOnlyOnItsSides(){
  val p=page(-1){q->fill(q,80,100,520,700,0xff3a78b0.toInt())}
  val out=ComicTrim.withoutBlankBands(listOf(z(1,Box(0f,0f,1f,1f),false)),p,w,h)
  assertEquals(1,out.size);assertEquals(80f/w,out[0].box.left,.03f);assertEquals(700f/h,out[0].box.bottom,.03f)
  val panel=ComicTrim.withoutBlankBands(listOf(z(1,Box(0f,0f,1f,1f),true)),p,w,h)
  // a panel keeps its top and bottom whatever the margins; only the paper at its left and right goes
  assertEquals(0f,panel[0].box.top,0f);assertEquals(1f,panel[0].box.bottom,0f);assertEquals(80f/w,panel[0].box.left,.03f);assertEquals(520f/w,panel[0].box.right,.03f)
 }

 @Test fun aShortTitleOrCaptionInALargeBandIsNotBlank(){
  // 130x6 px of text in a 600x440 band: about 0.3 percent of the area, but 22 percent of the width
  val p=page(-1){q->fill(q,40,40,170,46,0xff000000.toInt())}
  val band=Box(0f,0f,1f,.55f)
  assertTrue(ComicTrim.inkShare(p,w,h,band)<ComicTrim.CONTENT)
  assertTrue(!ComicTrim.blank(p,w,h,band))
  val foot=page(-1){q->fill(q,40,740,190,748,0xff000000.toInt())}
  assertTrue(!ComicTrim.blank(foot,w,h,Box(0f,.88f,1f,1f)))
 }
 @Test fun twoThinLinesOfCaptionAtTheEdgeKeepTheirBand(){
  val p=page(0xfff2e2b8.toInt()){q->fill(q,60,20,260,25,0xff202020.toInt());fill(q,60,32,230,37,0xff202020.toInt())}
  assertTrue(!ComicTrim.blank(p,w,h,Box(0f,0f,1f,.25f)))
 }
 @Test fun aNarrowMarkOutsideTheFooterKeepsItsBand(){
  val oneWord=page(-1){q->fill(q,270,30,330,48,0)}
  assertTrue(!ComicTrim.blank(oneWord,w,h,Box(0f,0f,1f,.2f)))
  val chapterDigit=page(-1){q->fill(q,290,60,310,100,0)}
  assertTrue(!ComicTrim.blank(chapterDigit,w,h,Box(0f,0f,1f,.2f)))
  val sideNote=page(-1){q->fill(q,20,300,40,330,0)}
  assertTrue(!ComicTrim.blank(sideNote,w,h,Box(0f,.1f,.15f,.9f)))
  val midBottomBand=page(-1){q->fill(q,296,640,305,655,0)}
  assertTrue(!ComicTrim.blank(midBottomBand,w,h,Box(0f,.75f,1f,.9f)))
 }
 @Test fun aLoneFolioInTheFooterStripStillCountsAsBlank(){
  val p=page(-1){q->fill(q,296,765,305,780,0)}
  assertTrue(ComicTrim.blank(p,w,h,Box(0f,.9f,1f,1f)))
 }
}
