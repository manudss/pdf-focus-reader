package com.pdffocus.reader
import android.app.*
import android.content.DialogInterface
import android.graphics.*
import android.view.*
import android.widget.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],qualifiers="fr-rFR-w393dp-h852dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class BlockEditingUiTest {
 private fun set(a:MainActivity,name:String,value:Any){MainActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.set(a,value)}
 private fun call(a:MainActivity,name:String,vararg values:Any){val types=values.map{if(it is Boolean)Boolean::class.javaPrimitiveType!!else it.javaClass}.toTypedArray();MainActivity::class.java.getDeclaredMethod(name,*types).apply{isAccessible=true}.invoke(a,*values)}
 private fun get(a:MainActivity,name:String)=MainActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(a)
 private fun page(v:View):PageCanvas?{if(v is PageCanvas)return v;if(v is ViewGroup)for(i in 0 until v.childCount)page(v.getChildAt(i))?.let{return it};return null}
 private fun button(v:View,label:String):Button?{if(v is Button&&v.text.toString()==label)return v;if(v is ViewGroup)for(i in 0 until v.childCount)button(v.getChildAt(i),label)?.let{return it};return null}
 private val zones=listOf(Zone(1,Box(.08f,.15f,.45f,.42f),Kind.TEXT),Zone(2,Box(.08f,.44f,.45f,.74f),Kind.TEXT),Zone(3,Box(.55f,.15f,.92f,.74f),Kind.TEXT))
 private fun setup(a:MainActivity){val b=Bitmap.createBitmap(600,900,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);val c=Canvas(b);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=20f};for(i in 0..23){c.drawText("Une ligne de texte fictif",50f,160f+i*24f,paint);c.drawText("Une autre colonne",335f,160f+i*24f,paint)};set(a,"preview",b);set(a,"count",1);set(a,"zones",zones);set(a,"order",listOf(3,1,2));set(a,"pageReady",true);page(a.window.decorView)!!.image=b;call(a,"sync",false)}
 private fun snapshot(a:MainActivity,name:String){val root=a.window.decorView;root.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2556,View.MeasureSpec.EXACTLY));root.layout(0,0,1179,2556);val b=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(b));val dir=File("/tmp/pdf-native-ui").apply{mkdirs()};File(dir,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
 @Test fun handlesResizeAndCancelPreserveReadingOrder(){val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();val a=controller.get();setup(a);call(a,"beginAdjustment",zones[0]);snapshot(a,"adjust-handles");val p=page(a.window.decorView)!!;val matrix=PageCanvas::class.java.getDeclaredField("matrix").apply{isAccessible=true}.get(p)as Matrix;val xy=floatArrayOf(.265f*600,.42f*900);matrix.mapPoints(xy);val now=android.os.SystemClock.uptimeMillis();p.onTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,xy[0],xy[1],0));p.onTouchEvent(MotionEvent.obtain(now,now+100,MotionEvent.ACTION_MOVE,xy[0],xy[1]-60,0));p.onTouchEvent(MotionEvent.obtain(now,now+150,MotionEvent.ACTION_UP,xy[0],xy[1]-60,0));assertTrue(p.editBox!!.bottom<.42f);button(a.window.decorView,"Valider")!!.performClick();@Suppress("UNCHECKED_CAST")val result=get(a,"zones")as List<Zone>;assertTrue(result.first{it.id==1}.manual);assertEquals(listOf(3,1,2),get(a,"order"));call(a,"beginAdjustment",result.first());p.editBox=Box(.1f,.1f,.9f,.9f);button(a.window.decorView,"Annuler")!!.performClick();assertEquals(result,get(a,"zones"));controller.pause().stop().destroy()}
 @Test fun newBlockIsNotCommittedBeforeValidation(){val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();val a=controller.get();setup(a);val new=Zone(4,Box(.1f,.8f,.4f,.9f),Kind.TEXT);call(a,"beginNewAdjustment",new);assertEquals(zones,get(a,"zones"));button(a.window.decorView,"Annuler")!!.performClick();assertEquals(zones,get(a,"zones"));call(a,"beginNewAdjustment",new);button(a.window.decorView,"Valider")!!.performClick();assertEquals(listOf(3,1,2,4),get(a,"order"));controller.pause().stop().destroy()}
 @Test fun fusionSelectionIsSeparateAndResultUsesEarliestPosition(){val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();val a=controller.get();setup(a);call(a,"beginMerge",zones[0]);val p=page(a.window.decorView)!!;p.onZoneTap!!.invoke(zones[1]);assertEquals(listOf(3,1,2),get(a,"order"));assertEquals(setOf(1,2),p.mergeIds);snapshot(a,"fusion-preview");call(a,"confirmMerge");val dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();@Suppress("UNCHECKED_CAST")val result=get(a,"order")as List<Int>;assertEquals(3,result.first());assertEquals(2,result.size);assertEquals(listOf(3,4),result);assertFalse(p.mergeMode);controller.pause().stop().destroy()}
}
