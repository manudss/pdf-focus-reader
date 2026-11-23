package com.pdffocus.reader

import android.content.Intent
import android.graphics.*
import android.os.SystemClock
import android.view.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.*

/** Fictional PDF, native PdfRenderer, actual window touchscreen events. No detection claims. */
@RunWith(AndroidJUnit4::class)
class ComicReaderDeviceTest {
 private val ins get()=InstrumentationRegistry.getInstrumentation()
 private fun field(v:Any,n:String)=v.javaClass.getDeclaredField(n).apply{isAccessible=true}
 private fun states(v:ContinuousReaderView)=field(v,"comicStates").get(v) as Map<Int,ComicReading.State>
 private fun tiles(v:ContinuousReaderView)=field(v,"tiles").get(v) as List<ContinuousReaderView.Tile>
 private fun send(v:View,points:List<Pair<Float,Float>>){
  val loc=IntArray(2);ins.runOnMainSync{v.getLocationOnScreen(loc)};val down=SystemClock.uptimeMillis()
  points.forEachIndexed{i,p->val action=when(i){0->MotionEvent.ACTION_DOWN;points.lastIndex->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE};val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,p.first+loc[0],p.second+loc[1],0);e.source=InputDevice.SOURCE_TOUCHSCREEN;ins.sendPointerSync(e);e.recycle();SystemClock.sleep(60)};ins.waitForIdleSync()
 }
 private fun tap(v:View,x:Float,y:Float)=send(v,listOf(x to y,x to y))
 private fun pinch(v:View,fx:Float,fy:Float,d:Float){
  val loc=IntArray(2);ins.runOnMainSync{v.getLocationOnScreen(loc)};val down=SystemClock.uptimeMillis()
  fun event(action:Int,span:Float,count:Int){
   val pp=Array(count){i->MotionEvent.PointerProperties().apply{id=i;toolType=MotionEvent.TOOL_TYPE_FINGER}}
   val pc=Array(count){i->MotionEvent.PointerCoords().apply{x=loc[0]+fx+if(i==0)-span else span;y=loc[1]+fy;pressure=1f;size=.1f}}
   val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,count,pp,pc,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);ins.sendPointerSync(e);e.recycle();SystemClock.sleep(60)
  }
  event(MotionEvent.ACTION_DOWN,35*d,1);event(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),35*d,2)
  for(n in 1..10)event(MotionEvent.ACTION_MOVE,(35+n*10)*d,2)
  event(MotionEvent.ACTION_POINTER_UP or (1 shl 8),135*d,2);event(MotionEvent.ACTION_UP,135*d,1);ins.waitForIdleSync()
 }
 private fun capture(v:View,name:String){ins.waitForIdleSync();SystemClock.sleep(350);ins.runOnMainSync{val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));File(File(ins.targetContext.getExternalFilesDir(null),"screenshots").apply{mkdirs()},name+".png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
 private fun fictionalPdf():File {
  val f=File(ins.targetContext.cacheDir,"comic-continuous-fiction.pdf")
  val p=android.graphics.pdf.PdfDocument();val page=p.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(1200,1800,1).create());val c=page.canvas;val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  c.drawColor(Color.rgb(243,223,181));paint.color=Color.BLACK;paint.style=Paint.Style.STROKE;paint.strokeWidth=7f
  c.drawRect(4f,4f,1196f,625f,paint);c.drawRect(4f,640f,590f,1796f,paint);c.drawRect(610f,640f,1196f,1796f,paint);paint.style=Paint.Style.FILL
  for(n in 0..5){paint.textSize=36f;paint.color=Color.BLACK;c.drawText("GAUCHE ${n+1}",30f,80f+n*95f,paint);c.drawText("DROITE ${n+1}",880f,80f+n*95f,paint)}
  for(n in 0..10){paint.textSize=30f;c.drawText("Suite fictive ${n+1}",30f,700f+n*90,paint);c.drawText("Autre case ${n+1}",630f,700f+n*90,paint)}
  paint.color=Color.rgb(50,109,93);c.drawCircle(450f,300f,75f,paint);paint.color=Color.rgb(190,80,40);c.drawCircle(750f,300f,75f,paint)
  p.finishPage(page);f.outputStream().use{p.writeTo(it)};p.close();return f
 }
 private fun exercise(rtl:Boolean){
  val context=ins.targetContext
  // The debug applicationId differs from the Activity package, so getLocalClassName is qualified.
  for(name in listOf("MainActivity",MainActivity::class.java.name))context.getSharedPreferences(name,0).edit().putBoolean("tutorialSeen",true).commit()
  val f=fictionalPdf();val doc=PdfDocument(f)
  ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use{scenario->
   lateinit var reader:ContinuousReaderView
   scenario.onActivity{a->reader=ContinuousReaderView(a)
    // Android 15 is edge-to-edge. Keep this standalone test host below system bars,
    // just like the real Activity root, or a header tap hits the status-bar window.
    val root=android.widget.FrameLayout(a)
    val bars=a.window.decorView.rootWindowInsets?.getInsets(WindowInsets.Type.systemBars())
    if(bars!=null)root.setPadding(bars.left,bars.top,bars.right,bars.bottom)
    root.addView(reader,android.widget.FrameLayout.LayoutParams(-1,-1));a.setContentView(root)
    reader.onRequest={t,r->reader.accept(t,r,doc.readingRegion(0,t.source,ceil(t.rect.width()).toInt().coerceAtLeast(1),ceil(t.rect.height()).toInt().coerceAtLeast(1),false))};reader.setDocument(listOf(Zone(1,Box(0f,0f,1f,.35f),Kind.IMAGE,panel=true),Zone(2,Box(0f,.35f,.5f,1f),Kind.IMAGE,panel=true),Zone(3,Box(.5f,.35f,1f,1f),Kind.IMAGE,panel=true)),1200,1800,comicDirection=if(rtl)-1 else 1)}
   // Synchronize on observable states, not on a fixed delay: layout and window focus are
   // separate conditions, and a loaded CI emulator may take long to deliver either.
   var laidOut=false;var focused=false
   // A SystemUI/app ANR dialog holds window focus for the whole run (seen on CI logcat);
   // answer "Wait" so the session survives the stall, and give a slow emulator more time.
   val device=UiDevice.getInstance(ins)
   val focusDeadline=SystemClock.uptimeMillis()+60000
   while(SystemClock.uptimeMillis()<focusDeadline){if(device.findObject(By.textContains("isn't responding"))!=null)device.findObject(By.text("Wait"))?.click();ins.waitForIdleSync();ins.runOnMainSync{laidOut=reader.width>0&&reader.height>0&&reader.isShown;focused=reader.hasWindowFocus()};if(laidOut&&focused)break;SystemClock.sleep(100)}
   assertTrue("Reader must be laid out before injecting input (focused=$focused)",laidOut)
   assertTrue("Activity window must be focused before injecting input (laidOut=$laidOut)",focused)
   val d=context.resources.displayMetrics.density;val w=reader.width.toFloat();val y=150*d
   capture(reader,"comic-native-${if(rtl)"manga" else "bd"}-01-entire")
   ins.runOnMainSync{assertTrue(states(reader).isEmpty());assertTrue(tiles(reader).all{it.rect.left>=0&&it.rect.right<=w})}
   tap(reader,70*d,y);ins.runOnMainSync{assertTrue(states(reader).getValue(1).detail)}
   capture(reader,"comic-native-${if(rtl)"manga" else "bd"}-02-detail")
   var before=0;var beforeX=0f;ins.runOnMainSync{before=reader.currentPosition();beforeX=tiles(reader).filter{it.chunkKey==1}.minOf{it.rect.left}}
   val sx=if(rtl)80*d else w-60*d;val ex=if(rtl)w-60*d else 80*d
   send(reader,listOf(sx to (220*d),(sx+ex)/2 to (220*d),ex to (220*d),ex to (220*d)))
   ins.runOnMainSync{assertEquals(before,reader.currentPosition());assertTrue(abs(tiles(reader).filter{it.chunkKey==1}.minOf{it.rect.left}-beforeX)>20*d)}
   capture(reader,"comic-native-${if(rtl)"manga" else "bd"}-03-pan")
   var scale=0f;var pan=0f;ins.runOnMainSync{pan=states(reader).getValue(1).pan;scale=states(reader).getValue(1).scale}
   tap(reader,w-45*d,20*d);ins.runOnMainSync{assertFalse(states(reader).getValue(1).detail)}
   capture(reader,"comic-native-${if(rtl)"manga" else "bd"}-04-entire-again")
   tap(reader,w-45*d,20*d);ins.runOnMainSync{assertEquals(pan,states(reader).getValue(1).pan,.01f);assertEquals(scale,states(reader).getValue(1).scale,.001f)}
   pinch(reader,w/2,250*d,d);ins.runOnMainSync{assertTrue(states(reader).getValue(1).scale>scale);scale=states(reader).getValue(1).scale}
   capture(reader,"comic-native-${if(rtl)"manga" else "bd"}-05-pinch-persistent")
   send(reader,listOf(100*d to 410*d,101*d to 330*d,100*d to 240*d,100*d to 240*d))
   ins.runOnMainSync{assertTrue(reader.currentPosition()>before);assertEquals(scale,states(reader).getValue(1).scale,.001f)}
   capture(reader,"comic-native-${if(rtl)"manga" else "bd"}-06-vertical")
   // "Bloc n sur m": 0 at the top, never backwards, every case reachable, and the last case shown once the end is reached.
   val seen=mutableListOf<Int>()
   ins.runOnMainSync{
    val frames=field(reader,"comicFrames").get(reader) as List<*>
    fun f(o:Any,n:String)=o.javaClass.getDeclaredField(n).apply{isAccessible=true}.get(o)
    val ordinals=frames.filterNotNull().map{f(it,"ordinal") as Int}.toSortedSet()
    val diag="frames=${frames.filterNotNull().map{"#"+f(it,"ordinal")+"["+f(it,"top")+","+f(it,"bottom")+"]"}} total=${field(reader,"totalHeight").get(reader)} view=${reader.height}"
    reader.onProgress={o,_->seen.add(o)}
    reader.move(0f);assertEquals("top $diag",ordinals.first(),seen.last())
    var y=0f;while(y<5000f){reader.move(y);y+=40f}
    reader.move(1e6f)
    assertEquals("end $diag seen=$seen",ordinals.last(),seen.last())
    assertEquals("monotonic $seen",seen.sorted(),seen)
    assertEquals("every case reachable $diag seen=${seen.toSortedSet()}",ordinals,seen.toSortedSet())
   }
   ins.runOnMainSync{reader.clear()}
  };doc.close();f.delete()
 }
 @Test fun continuousComicWithNativePdfKeepsZoomAndIndependentAxes()=exercise(false)
 @Test fun continuousMangaStartsAtRightAndReturnsToSameDetail()=exercise(true)
}
