package com.pdffocus.reader
import android.graphics.*
import android.view.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderCacheTest {
 @Test fun largeViewportAtDoubleZoomSettlesInsteadOfReloadingForever(){
  val reader=ContinuousReaderView(RuntimeEnvironment.getApplication())
  reader.measure(View.MeasureSpec.makeMeasureSpec(1440,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));reader.layout(0,0,1440,2400)
  data class Work(val tile:ContinuousReaderView.Tile,val revision:Int)
  val work=mutableListOf<Work>();var requests=0
  reader.onRequest={t,r->work+=Work(t,r);requests++}
  ContinuousReaderView::class.java.getDeclaredField("cacheBytes").apply{isAccessible=true}.setInt(reader,32*1024*1024)
  @Suppress("UNCHECKED_CAST") val cache=ContinuousReaderView::class.java.getDeclaredField("bitmaps").apply{isAccessible=true}.get(reader) as android.util.LruCache<Int,Bitmap>;cache.resize(32*1024*1024)
  reader.setDocument(listOf(Zone(1,Box(0f,0f,1f,1f),Kind.TEXT)),600,12000)
  reader.zoomBy(2f)
  val out=Bitmap.createBitmap(1440,2400,Bitmap.Config.ARGB_8888)
  for(frame in 0..8){reader.draw(Canvas(out));val ready=work.toList();work.clear();for(w in ready){val b=Bitmap.createBitmap(kotlin.math.ceil(w.tile.rect.width()).toInt(),kotlin.math.ceil(w.tile.rect.height()).toInt(),Bitmap.Config.ARGB_8888);b.eraseColor(Color.DKGRAY);reader.accept(w.tile,w.revision,b)}}
  val settled=requests
  repeat(3){reader.draw(Canvas(out));val ready=work.toList();work.clear();for(w in ready){val b=Bitmap.createBitmap(kotlin.math.ceil(w.tile.rect.width()).toInt(),kotlin.math.ceil(w.tile.rect.height()).toInt(),Bitmap.Config.ARGB_8888);b.eraseColor(Color.DKGRAY);reader.accept(w.tile,w.revision,b)}}
  println("Large viewport requests before=$settled after=$requests")
  assertEquals("A stationary viewport must not evict and reload its own visible tiles",settled,requests)
  assertTrue("Loaded tiles must contain source pixels",Color.red(out.getPixel(720,1200))<150)
  out.recycle();reader.clear()
 }
}
