package com.pdffocus.reader
import android.graphics.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextCoverageTest {
 @Test fun restoresFilteredAndPartiallyCutLines(){
  val a=Zone(1,Box(.1f,.1f,.4f,.25f),Kind.TEXT)
  val cut=Box(.1f,.23f,.4f,.27f);val orphan=Box(.1f,.28f,.4f,.3f)
  val out=TextCoverage.includeLines(listOf(a),listOf(cut,orphan))
  for(line in listOf(cut,orphan))assertTrue(out.any{it.box.intersect(line)>=line.area*.995f})
 }
 @Test fun expandsBottomThroughInkRatherThanCuttingIt(){
  val b=Bitmap.createBitmap(300,600,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE)
  Canvas(b).drawRect(35f,290f,110f,309f,Paint().apply{color=Color.BLACK})
  val safe=TextCoverage.safeTextBox(b,Box(.1f,.2f,.4f,.5f))
  assertTrue(safe.bottom*600>=310);assertTrue(safe.top<=.2f);b.recycle()
 }
 @Test fun neverShrinksAManualBox(){val b=Bitmap.createBitmap(300,600,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);val x=Box(.1f,.1f,.5f,.8f);val out=TextCoverage.safeTextBox(b,x);assertTrue(out.left<=x.left&&out.top<=x.top&&out.right>=x.right&&out.bottom>=x.bottom);b.recycle()}
 @Test fun recoversUnrecognizedGapWithOverlapButNeverAcrossImage(){
  val a=Zone(1,Box(.1f,.4f,.3f,.45f),Kind.TEXT);val b=Zone(2,Box(.1f,.485f,.3f,.6f),Kind.TEXT)
  val covered=TextCoverage.bridgeColumnGaps(listOf(a,b));assertTrue(covered[1].box.top<a.box.bottom)
  val bands=ContinuousLayout.bands(covered);for(i in bands.indices)for(j in i+1 until bands.size)for(x in bands[i].pieces)for(y in bands[j].pieces)assertEquals(0f,x.intersect(y),.000001f)
  assertEquals(b.box,TextCoverage.bridgeColumnGaps(listOf(a,b.copy(manual=true)))[1].box)
  val image=Zone(3,Box(.1f,.455f,.3f,.48f),Kind.IMAGE);assertEquals(b.box,TextCoverage.bridgeColumnGaps(listOf(a,b,image))[1].box)
 }
 @Test fun narrowBlankSeparatorDoesNotGrowIntoNeighbour(){
  val b=Bitmap.createBitmap(300,600,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE)
  val c=Canvas(b);val p=Paint().apply{color=Color.BLACK};c.drawRect(30f,100f,120f,200f,p);c.drawRect(30f,203f,120f,220f,p)
  val box=Box(.1f,201f/600,.4f,225f/600);val once=TextCoverage.safeTextBox(b,box);val twice=TextCoverage.safeTextBox(b,once)
  assertTrue(once.top*600>=200);assertEquals(once.top,twice.top);assertEquals(once.bottom,twice.bottom);b.recycle()
 }
}
