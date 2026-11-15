package com.pdffocus.reader
import android.graphics.*
import android.view.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadingEndTest{
 @Test fun trimOnlyPaperNeverInkOrImages(){val b=Bitmap.createBitmap(100,200,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);b.setPixel(35,45,Color.rgb(244,244,244));b.setPixel(65,154,Color.BLACK);val z=Zone(1,Box(0f,0f,1f,1f),Kind.TEXT,manual=true);val t=ReadingWhitespace.trim(b,z);assertTrue(t.box.top<45f/200);assertTrue(t.box.bottom>154f/200);assertEquals(z.box,Box(0f,0f,1f,1f));assertEquals(z.copy(kind=Kind.IMAGE),ReadingWhitespace.trim(b,z.copy(kind=Kind.IMAGE)))}
 @Test fun lastRasterAndOcrLineRemainAboveBottomAtEveryViewportAndZoom(){
  val c=RuntimeEnvironment.getApplication();val v=ContinuousReaderView(c)
  fun field(n:String)=ContinuousReaderView::class.java.getDeclaredField(n).apply{isAccessible=true}
  fun bottom(){val m=ContinuousReaderView::class.java.getDeclaredMethod("move",Float::class.javaPrimitiveType).apply{isAccessible=true};m.invoke(v,Float.MAX_VALUE);val total=field("totalHeight").getFloat(v);assertEquals(maxOf(0f,total-v.height),v.currentPosition().toFloat(),1f);val tiles=field("tiles").get(v)as List<ContinuousReaderView.Tile>;val items=field("textItems").get(v)as List<ContinuousReaderView.TextItem>;val end=maxOf(tiles.maxOfOrNull{it.rect.bottom}?:0f,items.maxOfOrNull{it.top+it.layout.height}?:0f);assertTrue("Last content needs bottom breathing room",end-v.currentPosition()<=v.height-12f)}
  for(h in listOf(600,380,600)){
   v.measure(View.MeasureSpec.makeMeasureSpec(360,1073741824),View.MeasureSpec.makeMeasureSpec(h,1073741824));v.layout(0,0,360,h)
   v.setDocument(listOf(Zone(1,Box(0f,0f,.5f,1f),Kind.TEXT),Zone(2,Box(.5f,0f,1f,1f),Kind.TEXT)),700,4000);bottom();v.zoomBy(1.8f);bottom();v.showOcr(mapOf(1 to "Lire sans perdre la dernière ligne. ".repeat(100),2 to "Le dernier paragraphe. ".repeat(80)));bottom();v.zoomBy(1.5f);bottom()
  }
 }
}
