package com.pdffocus.reader
import android.graphics.*
import android.os.Looper
import android.view.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.time.Duration
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageGesturesTest{
 @Test fun longPressSweepAddsOnceAndDoubleTapEditsWithoutSelecting(){
  val v=PageCanvas(RuntimeEnvironment.getApplication());v.image=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888);v.zones=(0..2).map{Zone(it+1,Box(.1f,.1f+it*.25f,.8f,.3f+it*.25f),Kind.TEXT)}
  v.measure(View.MeasureSpec.makeMeasureSpec(600,1073741824),View.MeasureSpec.makeMeasureSpec(900,1073741824));v.layout(0,0,600,900);v.draw(Canvas(Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888)))
  val selected=mutableListOf<Int>();var edited=0;var taps=0;v.onZoneSweep={if(it.id !in selected)selected+=it.id};v.onZoneLongPress={edited=it.id};v.onZoneTap={taps++}
  var time=android.os.SystemClock.uptimeMillis()
  fun send(action:Int,x:Float,y:Float,down:Long=time){val e=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x,y,0);v.dispatchTouchEvent(e);e.recycle()}
  send(0,200f,170f);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));send(2,200f,700f);send(2,200f,170f);send(1,200f,170f);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));assertEquals(listOf(1,2,3),selected);assertEquals(0,taps);assertEquals(0,edited)
  time=android.os.SystemClock.uptimeMillis();send(0,200f,170f);send(1,200f,170f);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80));send(0,200f,170f);send(1,200f,170f);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));assertEquals(1,edited);assertEquals(0,taps)
 }
}
