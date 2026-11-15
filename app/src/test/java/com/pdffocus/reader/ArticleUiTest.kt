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
class ArticleUiTest{
 private fun set(a:MainActivity,n:String,v:Any){MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.set(a,v)}
 private fun get(a:MainActivity,n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.get(a)
 private fun call(a:MainActivity,n:String,vararg args:Any){val m=MainActivity::class.java.declaredMethods.first{it.name==n};m.isAccessible=true;m.invoke(a,*args)}
 private fun button(v:View,label:String):View?{if(v is Button&&v.text.toString()==label)return v;if(v is ViewGroup)for(i in 0 until v.childCount)button(v.getChildAt(i),label)?.let{return it};return null}
 private fun preview(v:View):SplitPreview?{if(v is SplitPreview)return v;if(v is ViewGroup)for(i in 0 until v.childCount)preview(v.getChildAt(i))?.let{return it};return null}
 private fun shot(a:MainActivity,d:Dialog,name:String){val v=d.window!!.decorView;val density=a.resources.displayMetrics.density;val w=(a.resources.configuration.screenWidthDp*density).toInt();val h=(a.resources.configuration.screenHeightDp*density).toInt();v.measure(View.MeasureSpec.makeMeasureSpec(w,1073741824),View.MeasureSpec.makeMeasureSpec(h,1073741824));v.layout(0,0,w,h);val image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);v.draw(Canvas(image));File("/tmp/pdf-native-ui").mkdirs();File("/tmp/pdf-native-ui/$name-${a.resources.configuration.screenWidthDp}.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle();System.gc()}
 @Test fun articlePreviewAndOrderCancelAndAppend(){
 val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();val a=controller.get();val image=Bitmap.createBitmap(700,1000,Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);val c=Canvas(image);val paint=Paint().apply{color=Color.BLACK;textSize=25f};for(i in 0..20)c.drawText("Lire à son rythme.",45f,80f+i*35,paint)
 val z=listOf(Zone(1,Box(.04f,.04f,.45f,.4f),Kind.TEXT),Zone(2,Box(.04f,.41f,.45f,.8f),Kind.TEXT),Zone(3,Box(.55f,.04f,.94f,.8f),Kind.TEXT));set(a,"preview",image);set(a,"count",1);set(a,"zones",z);set(a,"order",listOf(3));val canvas=get(a,"canvas")as PageCanvas;canvas.image=image;canvas.zones=z
 call(a,"beginArticle",z[0]);assertEquals(listOf(1,2),get(a,"articleIds"));call(a,"editArticleOrder");val d=ShadowDialog.getLatestDialog();shot(a,d,"article-order");button(d.window!!.decorView,"Descendre")!!.performClick();assertEquals(listOf(2,1),get(a,"articleIds"));d.dismiss();call(a,"finishArticle",false);assertEquals(listOf(3),get(a,"order"))
 call(a,"beginArticle",z[0]);canvas.onZoneTap!!.invoke(z[2]);assertEquals(listOf(1,2,3),get(a,"articleIds"));call(a,"finishArticle",true);assertEquals(listOf(3,1,2),get(a,"order"));assertEquals(z,get(a,"zones"));controller.pause().stop().destroy()
 }
}
