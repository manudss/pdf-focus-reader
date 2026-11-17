package com.pdffocus.reader
import android.graphics.*
import android.view.*
import android.os.SystemClock
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File

/** Native Android View + real MotionEvents, fictional source only, not PDF recognition proof. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],qualifiers="mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComicReaderUiTest {
 private fun field(v:Any,n:String)=v.javaClass.getDeclaredField(n).apply{isAccessible=true}
 private fun tiles(v:ContinuousReaderView)=field(v,"tiles").get(v) as List<ContinuousReaderView.Tile>
 private fun states(v:ContinuousReaderView)=field(v,"comicStates").get(v) as Map<Int,ComicReading.State>
 private var clock=SystemClock.uptimeMillis()
 private fun gesture(v:ContinuousReaderView,points:List<Pair<Float,Float>>){clock+=1200;val start=clock;points.forEachIndexed{i,p->val action=when(i){0->MotionEvent.ACTION_DOWN;points.lastIndex->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE};val e=MotionEvent.obtain(start,start+i*80L,action,p.first,p.second,0);v.onTouchEvent(e);e.recycle()};clock+=points.size*80}
 private fun tap(v:ContinuousReaderView,x:Float,y:Float)=gesture(v,listOf(x to y,x to y))
 private fun pinch(v:ContinuousReaderView,fx:Float,fy:Float,cancel:Boolean=false){
  clock+=1200;val start=clock
  fun send(action:Int,span:Float,count:Int,time:Long){
   val pp=Array(count){i->MotionEvent.PointerProperties().apply{id=i;toolType=MotionEvent.TOOL_TYPE_FINGER}}
   val pc=Array(count){i->MotionEvent.PointerCoords().apply{x=fx+if(i==0)-span else span;y=fy;pressure=1f;size=.1f}}
   val e=MotionEvent.obtain(start,start+time,action,count,pp,pc,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);v.onTouchEvent(e);e.recycle()
  }
  send(MotionEvent.ACTION_DOWN,60f,1,0);send(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),60f,2,50)
  for(n in 1..8)send(MotionEvent.ACTION_MOVE,60f+n*12,2,50+n*50L)
  if(cancel){send(MotionEvent.ACTION_CANCEL,156f,2,500);clock+=600;return}
  send(MotionEvent.ACTION_POINTER_UP or (1 shl 8),156f,2,500);send(MotionEvent.ACTION_UP,156f,1,550);clock+=600
 }
 private fun setup(w:Int=390,rtl:Boolean=false):ContinuousReaderView {
  val v=ContinuousReaderView(RuntimeEnvironment.getApplication());v.measure(View.MeasureSpec.makeMeasureSpec(w,1073741824),View.MeasureSpec.makeMeasureSpec(760,1073741824));v.layout(0,0,w,760)
  val source=Bitmap.createBitmap(1200,1800,Bitmap.Config.ARGB_8888);val cv=Canvas(source);cv.drawColor(Color.rgb(243,223,181));val p=Paint(Paint.ANTI_ALIAS_FLAG)
  p.style=Paint.Style.STROKE;p.strokeWidth=8f;p.color=Color.BLACK;cv.drawRect(4f,4f,1196f,625f,p);cv.drawRect(4f,640f,590f,1796f,p);cv.drawRect(610f,640f,1196f,1796f,p);p.style=Paint.Style.FILL
  for((x,t,color)in listOf(25f to "GAUCHE" to Color.rgb(40,120,100),820f to "DROITE" to Color.rgb(190,80,40)).map{Triple(it.first.first,it.first.second,it.second)}){
   p.color=Color.WHITE;cv.drawRoundRect(RectF(x,35f,x+345,155f),25f,25f,p);p.color=Color.BLACK;p.textSize=42f;cv.drawText(t,x+20f,108f,p);p.color=color;cv.drawCircle(x+160f,360f,100f,p)
  }
  for(n in 0..10){p.color=Color.BLACK;p.textSize=30f;cv.drawText("Repère fictif ${n+1}",25f,700f+n*90f,p);cv.drawText("Suite inventée ${n+1}",630f,700f+n*90f,p)}
  for(x in listOf(340f,560f,780f))for(y in listOf(210f,410f,570f)){p.color=Color.BLACK;p.textSize=22f;cv.drawText("Fictif ${x.toInt()}/${y.toInt()}",x,y,p)}
  v.onRequest={t,r->val b=Bitmap.createBitmap(kotlin.math.ceil(t.rect.width()).toInt().coerceAtLeast(1),kotlin.math.ceil(t.rect.height()).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888);Canvas(b).drawBitmap(source,Rect((t.source.left*1200).toInt(),(t.source.top*1800).toInt(),(t.source.right*1200).toInt(),(t.source.bottom*1800).toInt()),Rect(0,0,b.width,b.height),Paint(Paint.FILTER_BITMAP_FLAG));v.accept(t,r,b)}
  v.setDocument(listOf(Zone(1,Box(0f,0f,1f,.35f),Kind.IMAGE,panel=true),Zone(2,Box(0f,.35f,.5f,1f),Kind.IMAGE,panel=true),Zone(3,Box(.5f,.35f,1f,1f),Kind.IMAGE,panel=true)),1200,1800,comicDirection=if(rtl)-1 else 1)
  return v
 }
 private fun sourcePoint(v:ContinuousReaderView,x:Float,y:Float):Pair<Float,Float>{
  val cy=y+v.currentPosition()-80f
  val t=tiles(v).first{it.rect.contains(x,cy)}
  return (t.source.left+(x-t.rect.left)/t.rect.width()*t.source.width) to (t.source.top+(cy-t.rect.top)/t.rect.height()*t.source.height)
 }
 private fun shot(v:View,name:String){repeat(3){val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));if(it==2){val dir=File(System.getProperty("java.io.tmpdir"),"comic-reader-proof").apply{mkdirs()};File(dir,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}};b.recycle()}}
 @Test fun continuousWholeDetailPanScrollAndResume(){
  for(w in listOf(320,390)){
   val v=setup(w);shot(v,"$w-01-entire")
   val wholeWidth=tiles(v).filter{it.chunkKey==1}.maxOf{it.rect.right}-tiles(v).filter{it.chunkKey==1}.minOf{it.rect.left}
   assertTrue(wholeWidth<=w);assertTrue(states(v).isEmpty())
   // Merely touching is not a zoom, and a vertical scroll remains page scrolling.
   gesture(v,listOf(150f to 200f,150f to 160f,150f to 120f,150f to 120f));assertTrue(states(v).isEmpty());assertTrue(v.currentPosition()>0)
   v.move(0f);tap(v,90f,140f);assertTrue(states(v).getValue(1).detail)
   val widthBefore=tiles(v).filter{it.chunkKey==1}.sumOf{it.rect.width().toDouble()}.toFloat();assertTrue(widthBefore>wholeWidth)
   shot(v,"$w-02-detail-left")
   val before=v.currentPosition();gesture(v,listOf(250f to 220f,190f to 222f,80f to 225f,80f to 225f));assertEquals(before,v.currentPosition());assertTrue(states(v).getValue(1).pan>0)
   shot(v,"$w-03-detail-pan")
   val pan=states(v).getValue(1).pan;val previousOffset=v.currentPosition();tap(v,90f,350f);assertFalse(states(v).getValue(1).detail);shot(v,"$w-04-entire-again")
   tap(v,w-50f,22f);assertTrue(states(v).getValue(1).detail);assertEquals(pan,states(v).getValue(1).pan,.001f);assertEquals(previousOffset,v.currentPosition());shot(v,"$w-05-resume")
   val cancelledState=states(v).getValue(1);pinch(v,w/2f,260f,true);assertEquals(cancelledState,states(v).getValue(1))
   val scale=states(v).getValue(1).scale;val focus=sourcePoint(v,w/2f,260f);pinch(v,w/2f,260f);assertTrue(states(v).getValue(1).scale>scale);val next=sourcePoint(v,w/2f,260f);assertEquals(focus.first,next.first,.002f);assertEquals(focus.second,next.second,.002f);shot(v,"$w-06-pinched")
   val afterPinch=states(v).getValue(1).scale;gesture(v,listOf(120f to 420f,121f to 320f,120f to 220f,120f to 220f));assertTrue(v.currentPosition()>0);assertEquals(afterPinch,states(v).getValue(1).scale,.001f);shot(v,"$w-07-vertical")
   // Export must contain the complete case, not the current horizontally panned crop.
   val export=v.articleImagePlan();assertTrue(export.tiles.all{it.rect.left>=-.001f&&it.rect.right<=w+.001f});assertEquals(setOf(1,2,3),export.tiles.map{it.chunkKey}.toSet())
   v.clear()
  }
 }
 @Test fun mangaAndCancellationDoNotLoseEdgesOrToggle(){val v=setup(rtl=true);tap(v,90f,140f);val right=tiles(v).filter{it.chunkKey==1}.maxOf{it.rect.right};assertEquals(382f,right,.01f);shot(v,"390-manga-right");val s=states(v).getValue(1);clock+=1500;val down=MotionEvent.obtain(clock,clock,MotionEvent.ACTION_DOWN,100f,220f,0);v.onTouchEvent(down);down.recycle();val cancel=MotionEvent.obtain(clock,clock+100,MotionEvent.ACTION_CANCEL,100f,220f,0);v.onTouchEvent(cancel);cancel.recycle();assertEquals(s,states(v).getValue(1));v.clear()}
 @Test fun mixedComicExportKeepsManualTextAndImageOnlyOcrKeepsComicControls(){
  val v=setup();v.showOcr(emptyMap());tap(v,90f,140f);assertTrue(states(v).getValue(1).detail)
  v.setDocument(listOf(Zone(10,Box(0f,0f,1f,.2f),Kind.TEXT,manual=true),Zone(11,Box(0f,.2f,1f,1f),Kind.IMAGE,manual=true)),1200,1800,comicDirection=1)
  val plan=v.articleImagePlan();assertEquals(setOf(10,11),plan.tiles.map{it.chunkKey}.toSet());assertTrue(plan.tiles.all{it.rect.left>=0&&it.rect.right<=390f})
  v.clear()
 }

}
