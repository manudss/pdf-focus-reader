package com.pdffocus.reader
import android.graphics.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class BlockSeamsTest {
 private fun setup():Pair<Bitmap,List<Zone>>{
  val b=Bitmap.createBitmap(400,600,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE)
  val c=Canvas(b);c.drawRect(40f,100f,340f,300f,Paint().apply{color=Color.DKGRAY})
  c.drawRect(42f,305f,335f,318f,Paint().apply{color=Color.BLACK})
  return b to listOf(Zone(1,Box(.09f,.16f,.86f,.52f),Kind.IMAGE),Zone(2,Box(.1f,.47f,.85f,.58f),Kind.TEXT))
 }
 @Test fun realPaperSeamKeepsPhotoAndCaptionWhole(){val(b,z)=setup();val out=BlockSeams.align(b,z);assertTrue(out[0].box.bottom*600>=300);assertTrue(out[0].box.bottom*600<305);assertEquals(out[0].box.bottom,out[1].box.top);assertEquals(z.map{it.id},out.map{it.id});b.recycle()}
 @Test fun manualFramesStayExact(){val(b,z)=setup();val manual=z.map{it.copy(manual=true)};assertEquals(manual,BlockSeams.align(b,manual));b.recycle()}
 @Test fun sideInkIsRetained(){val(b,z)=setup();Canvas(b).drawRect(36f,303f,39f,310f,Paint().apply{color=Color.BLACK});val out=BlockSeams.align(b,z);assertTrue(out[1].box.left<=.09f);assertTrue(out[1].box.top*600<=303);b.recycle()}
 @Test fun withoutBlankSeparatorNothingMoves(){val(b,z)=setup();Canvas(b).drawRect(40f,280f,340f,320f,Paint().apply{color=Color.BLACK});assertEquals(z,BlockSeams.align(b,z));b.recycle()}
}
