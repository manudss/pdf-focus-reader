package com.pdffocus.reader
import android.graphics.*
import android.view.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import android.os.Looper
import java.time.Duration
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],qualifiers="fr-rFR-w393dp-h852dp-xxhdpi") @LooperMode(LooperMode.Mode.PAUSED)
class TwoFingerPhysicalTest {
 @Test fun wideStationaryPairWithTouchJitterArmsRemovalBeforeDrag(){
  val v=PageCanvas(RuntimeEnvironment.getApplication());v.layout(0,0,1080,1700)
  val image=Bitmap.createBitmap(700,1000,Bitmap.Config.ARGB_8888);v.image=image
  v.zones=listOf(Zone(1,Box(.2f,.1f,.42f,.9f),Kind.TEXT),Zone(2,Box(.52f,.1f,.75f,.9f),Kind.TEXT));v.selection=listOf(1,2)
  v.draw(Canvas(Bitmap.createBitmap(1080,1700,Bitmap.Config.ARGB_8888)))
  val removed=mutableListOf<Int>();v.onZoneSweepRemove={removed+=it.id;v.selection=v.selection-it.id}
  val down=android.os.SystemClock.uptimeMillis()
  fun event(action:Int,x0:Float,y0:Float,x1:Float=0f,y1:Float=0f){
   val n=if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_UP)1 else 2
   val pp=Array(n){k->MotionEvent.PointerProperties().apply{id=k;toolType=MotionEvent.TOOL_TYPE_FINGER}}
   val pc=Array(n){k->MotionEvent.PointerCoords().apply{x=if(k==0)x0 else x1;y=if(k==0)y0 else y1;pressure=1f;size=.1f}}
   val e=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,n,pp,pc,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);v.dispatchTouchEvent(e);e.recycle()
  }
  // 60dp between two fingertips, unlike the old 80px narrow-pair test.
  event(MotionEvent.ACTION_DOWN,390f,500f)
  event(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),390f,500f,600f,500f)
  repeat(5){k->shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80));event(MotionEvent.ACTION_MOVE,390f+k,500f,600f+k,500f)}
  shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
  event(MotionEvent.ACTION_MOVE,390f,1100f,600f,1100f)
  event(MotionEvent.ACTION_POINTER_UP or (1 shl 8),390f,1100f,600f,1100f)
  event(MotionEvent.ACTION_UP,390f,1100f)
  assertTrue("A wide held pair must remove crossed selections despite ordinary touch samples",2 in removed)
  image.recycle()
 }
}
