package com.pdffocus.reader
import org.junit.Assert.*
import org.junit.Test
/** Fictional pixels only; not a proxy for the user's original PDFs. */
class ComicTrimTest {
 private val w=600;private val h=800;private val whole=Box(0f,0f,1f,1f)
 private fun page(paper:Int=-1,draw:(IntArray)->Unit={}):IntArray=IntArray(w*h){paper}.also(draw)
 private fun fill(p:IntArray,l:Int,t:Int,r:Int,b:Int,c:Int){for(y in t until b)for(x in l until r)p[y*w+x]=c}
 private fun z(id:Int,b:Box,panel:Boolean)=Zone(id,b,Kind.IMAGE,manual=true,panel=panel,complement=!panel)
 @Test fun whiteMarginsTrimButWhiteInsideDrawingStays(){
  val p=page{fill(it,150,200,450,600,0xff3a78b0.toInt());fill(it,250,300,350,400,-1)}
  val t=ComicTrim.trim(p,w,h,whole)
  assertEquals(.25f,t.left,.02f);assertEquals(.25f,t.top,.02f);assertEquals(.75f,t.right,.02f);assertEquals(.75f,t.bottom,.02f)
 }
 @Test fun tintedPaperAndLightContent(){
  for(paper in listOf(-1,0xfff2e2b8.toInt())){
   val p=page(paper){fill(it,120,120,480,680,0xffd8d0c0.toInt())}
   val t=ComicTrim.trim(p,w,h,whole)
   assertEquals(.2f,t.left,.02f);assertEquals(.15f,t.top,.02f);assertEquals(.8f,t.right,.02f);assertEquals(.85f,t.bottom,.02f)
  }
 }
 @Test fun textAtEdgeAndTitleAboveDrawingStay(){
  val p=page{fill(it,200,300,400,600,0xff203040.toInt());fill(it,0,10,300,40,0xff000000.toInt())}
  val t=ComicTrim.trim(p,w,h,whole);assertEquals(0f,t.left,0f);assertTrue(t.top<=10f/h)
 }
 @Test fun tinyMarkDoesNotVanishBetweenSamplesOrByDensity(){
  val p=page{fill(it,200,300,400,600,0xff203040.toInt());it[13*w+11]=0xff101010.toInt()}
  val t=ComicTrim.trim(p,w,h,whole);assertTrue(t.top<=13f/h);assertTrue(t.left<=11f/w)
  assertTrue(!ComicTrim.blank(p,w,h,Box(0f,0f,1f,.1f)))
 }
 @Test fun faintContentWithSmallColourDifferenceIsKept(){
  val p=page{fill(it,80,30,120,45,0xfff8f8f8.toInt());fill(it,200,300,400,600,0xff203040.toInt())}
  val t=ComicTrim.trim(p,w,h,whole);assertTrue(t.top<=30f/h);assertTrue(t.left<=80f/w)
 }
 @Test fun everyAmbiguousMarkIncludingFooterFolioIsKept(){
  val boxes=listOf(intArrayOf(270,30,330,48),intArrayOf(290,60,310,100),intArrayOf(20,300,40,330),intArrayOf(296,765,305,780))
  for(b in boxes){val p=page{fill(it,b[0],b[1],b[2],b[3],0xff000000.toInt())};assertTrue(!ComicTrim.blank(p,w,h,whole));assertEquals(1,ComicTrim.withoutBlankBands(listOf(z(1,whole,false)),p,w,h).size)}
 }
 @Test fun uniformColouredBandIsContentNotItsOwnBackground(){
  val p=page{fill(it,0,200,600,400,0xffc9d8e0.toInt())}
  assertTrue(!ComicTrim.blank(p,w,h,Box(0f,.25f,1f,.5f)))
 }
 @Test fun trulyBlankBandsGoAndPanelsStay(){
  val p=page{fill(it,60,160,540,700,0xff3a78b0.toInt())}
  val zones=listOf(z(1,Box(0f,0f,1f,.18f),false),z(2,Box(0f,.18f,1f,.88f),true),z(3,Box(0f,.88f,1f,1f),false))
  val out=ComicTrim.withoutBlankBands(zones,p,w,h);assertEquals(listOf(2),out.map{it.id});assertEquals(zones[1].box.top,out[0].box.top,0f);assertEquals(zones[1].box.bottom,out[0].box.bottom,0f);assertEquals(.1f,out[0].box.left,.02f)
 }
 @Test fun emptyPageOrMixedDarkBorderIsLeftAlone(){
  val zones=listOf(z(1,whole,false));assertEquals(zones,ComicTrim.withoutBlankBands(zones,page(),w,h))
  val dark=page(0xff202020.toInt()){fill(it,200,300,400,600,-1)}
  assertEquals(whole,ComicTrim.trim(dark,w,h,whole));assertTrue(!ComicTrim.blank(dark,w,h,whole))
 }
 @Test fun subBoxCoordinatesStayWithinOriginal(){
  val p=page{fill(it,300,100,500,300,0xff3a78b0.toInt())};val b=Box(.4f,.05f,.95f,.45f);val t=ComicTrim.trim(p,w,h,b)
  assertTrue(t.left>=b.left&&t.top>=b.top&&t.right<=b.right&&t.bottom<=b.bottom);assertEquals(.5f,t.left,.02f)
 }
 @Test fun shortTitleAndTwoLineCaptionKeepTheirBands(){
  val p=page{fill(it,40,40,170,46,0xff000000.toInt());fill(it,60,740,260,745,0xff202020.toInt());fill(it,60,752,230,757,0xff202020.toInt())}
  assertTrue(!ComicTrim.blank(p,w,h,Box(0f,0f,1f,.55f)));assertTrue(!ComicTrim.blank(p,w,h,Box(0f,.9f,1f,1f)))
 }
}
