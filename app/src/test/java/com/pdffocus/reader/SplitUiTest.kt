package com.pdffocus.reader
import android.app.Dialog
import android.graphics.*
import android.view.*
import android.widget.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowDialog
import org.robolectric.RuntimeEnvironment
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],qualifiers="fr-rFR-w393dp-h852dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class SplitUiTest{
 private fun set(a:MainActivity,n:String,v:Any){MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.set(a,v)}
 private fun get(a:MainActivity,n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.get(a)
 private fun call(a:MainActivity,n:String,vararg args:Any){val m=MainActivity::class.java.declaredMethods.first{it.name==n};m.isAccessible=true;m.invoke(a,*args)}
 private fun button(v:View,label:String):View?{if(v is Button&&v.text.toString()==label)return v;if(v is ViewGroup)for(i in 0 until v.childCount)button(v.getChildAt(i),label)?.let{return it};return null}
 private fun preview(v:View):SplitPreview?{if(v is SplitPreview)return v;if(v is ViewGroup)for(i in 0 until v.childCount)preview(v.getChildAt(i))?.let{return it};return null}
 private fun shot(a:MainActivity,d:Dialog,name:String){val v=d.window!!.decorView;val density=a.resources.displayMetrics.density;val w=(a.resources.configuration.screenWidthDp*density).toInt();val h=(a.resources.configuration.screenHeightDp*density).toInt();v.measure(View.MeasureSpec.makeMeasureSpec(w,1073741824),View.MeasureSpec.makeMeasureSpec(h,1073741824));v.layout(0,0,w,h);val image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);v.draw(Canvas(image));File("/tmp/pdf-native-ui").mkdirs();File("/tmp/pdf-native-ui/$name-${a.resources.configuration.screenWidthDp}.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle();System.gc()}
 @Test @Config(qualifiers="fr-rFR-w320dp-h640dp-xxhdpi")fun narrow(){flows()}
 @Test fun largeFont(){RuntimeEnvironment.setFontScale(1.3f);try{flows()}finally{RuntimeEnvironment.setFontScale(1f)}}
 @Test fun flows(){
  val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();val a=controller.get();val image=Bitmap.createBitmap(1000,1500,Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);val c=Canvas(image);val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=28f};for(i in 0..45){c.drawText("Le journal des idées",30f,45f+i*30,p);c.drawText("Une seconde colonne",530f,45f+i*30,p)}
  val z=Zone(2,Box(.02f,.02f,.98f,.96f),Kind.TEXT);val original=listOf(z,Zone(4,Box(.1f,.1f,.2f,.2f),Kind.IMAGE));set(a,"preview",image);set(a,"zones",original);set(a,"order",listOf(4,2));set(a,"count",1)
  call(a,"editZone",z);var d=ShadowDialog.getLatestDialog();shot(a,d,"block-menu");assertNotNull(button(d.window!!.decorView,"Fermer"));
  fun scroll(v:View):ScrollView?{if(v is ScrollView)return v;if(v is ViewGroup)for(i in 0 until v.childCount)scroll(v.getChildAt(i))?.let{return it};return null}
  scroll(d.window!!.decorView)!!.fullScroll(View.FOCUS_DOWN);shot(a,d,"block-menu-bottom");d.dismiss()
  for(vertical in listOf(false,true)){
   call(a,"showSplit",z,vertical);d=ShadowDialog.getLatestDialog();val root=d.window!!.decorView;shot(a,d,if(vertical)"split-v" else  "split-h");val view=preview(root)!!
   val old=view.fraction;val h=view.handlePoint();fun event(action:Int,x:Float,y:Float){val e=MotionEvent.obtain(0,20,action,x,y,0);view.dispatchTouchEvent(e);e.recycle()}
   event(0,h.x,h.y);event(2,h.x+if(vertical)45f else 0f,h.y+if(vertical)0f else 45f);event(1,h.x,h.y);assertTrue(view.fraction!=old)
   // Real two-pointer gesture: zoom must not commit or move the split fraction.
   val beforePinch=view.fraction;val beforeZoom=view.zoom
   fun multi(action:Int,spread:Float){val props=Array(2){idx->MotionEvent.PointerProperties().apply{id=idx;toolType=MotionEvent.TOOL_TYPE_FINGER}};val coords=Array(2){idx->MotionEvent.PointerCoords().apply{x=view.width/2f+(if(idx==0)-spread else spread);y=view.height/2f;pressure=1f;size=1f}};val e=MotionEvent.obtain(0,100+spread.toLong(),action,2,props,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);view.dispatchTouchEvent(e);e.recycle()}
   event(MotionEvent.ACTION_DOWN,view.width/2f-40,view.height/2f);multi(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),40f);multi(MotionEvent.ACTION_MOVE,180f);multi(MotionEvent.ACTION_MOVE,280f);multi(MotionEvent.ACTION_MOVE,340f);multi(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),340f);event(MotionEvent.ACTION_UP,view.width/2f,view.height/2f)
   assertTrue(view.zoom>beforeZoom);assertEquals(beforePinch,view.fraction,0f)
   view.setZoom(2f);val f=view.fraction;button(root,if(vertical)"À gauche" else  "Monter")!!.performClick();assertTrue(view.fraction<f);shot(a,d,if(vertical)"split-v-zoom" else  "split-h-zoom")
   button(root,"Annuler")!!.performClick();assertEquals(original,get(a,"zones"));assertEquals(listOf(4,2),get(a,"order"))
   call(a,"showSplit",z,vertical);d=ShadowDialog.getLatestDialog();val r=d.window!!.decorView;preview(r)!!.setFraction(.63f);button(r,"Confirmer la séparation")!!.performClick();assertEquals(listOf(4,2,5),get(a,"order"));assertEquals(3,(get(a,"zones")as List<*>).size)
   set(a,"zones",original);set(a,"order",listOf(4,2))
  };controller.pause().stop().destroy()
 }
}
