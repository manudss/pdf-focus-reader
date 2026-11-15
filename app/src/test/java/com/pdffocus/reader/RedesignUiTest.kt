package com.pdffocus.reader
import android.graphics.*
import android.view.*
import android.widget.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],qualifiers="fr-rFR-w393dp-h852dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class RedesignUiTest {
 private fun set(a:MainActivity,n:String,v:Any){MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.set(a,v)}
 private fun get(a:MainActivity,n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.get(a)
 private fun call(a:MainActivity,n:String){MainActivity::class.java.getDeclaredMethod(n).apply{isAccessible=true}.invoke(a)}
 private fun b(v:View,label:String):Button?{if(v is Button&&v.text.toString()==label)return v;if(v is ViewGroup)for(i in 0 until v.childCount)b(v.getChildAt(i),label)?.let{return it};return null}
 private fun shot(a:MainActivity,name:String){val v=a.window.decorView;val w=a.resources.configuration.screenWidthDp*3;val h=a.resources.configuration.screenHeightDp*3;v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);repeat(2){val image=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);fun dirty(q:View){q.invalidate();if(q is ViewGroup)for(i in 0 until q.childCount)dirty(q.getChildAt(i))};dirty(v);v.draw(Canvas(image));if(it==1)File("/tmp/pdf-native-ui/$name-${a.resources.configuration.screenWidthDp}.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}}
 @Test @Config(qualifiers="fr-rFR-w320dp-h640dp-xxhdpi") fun narrowControls(){compactControlsStayContextualAndOcrSizeSurvivesSwitch()}
 @Test fun compactControlsStayContextualAndOcrSizeSurvivesSwitch(){
  val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();val a=controller.get();val v=a.window.decorView
  val image=Bitmap.createBitmap(700,1100,Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);val c=Canvas(image);val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=25f};for(i in 0..25)c.drawText("Lire un journal, à son rythme.",30f,80f+i*36,p)
  val zones=listOf(Zone(1,Box(.03f,.04f,.97f,.9f),Kind.TEXT,manual=true));set(a,"count",1);set(a,"preview",image);set(a,"zones",zones);set(a,"order",listOf(1));set(a,"pageReady",true)
  val canvas=get(a,"canvas")as PageCanvas;canvas.image=image;canvas.zones=zones;canvas.selection=listOf(1)
  call(a,"updateControls");shot(a,"redesign-prepare")
  val reader=get(a,"reader")as ContinuousReaderView
  reader.onRequest={tile,r->val crop=Bitmap.createBitmap(kotlin.math.ceil(tile.rect.width()).toInt(),kotlin.math.ceil(tile.rect.height()).toInt(),Bitmap.Config.ARGB_8888);Canvas(crop).drawBitmap(image,Rect((tile.source.left*image.width).toInt(),(tile.source.top*image.height).toInt(),(tile.source.right*image.width).toInt(),(tile.source.bottom*image.height).toInt()),Rect(0,0,crop.width,crop.height),Paint(Paint.FILTER_BITMAP_FLAG));reader.accept(tile,r,crop)}
  b(v,"Lire (1)")!!.performClick();shot(a,"redesign-reader")
  assertNull(b(v,"Texte +"));assertNull(b(v,"Texte −"));assertNotNull(b(v,"Réglages"))
  set(a,"ocrReading",true);set(a,"chunkPlan",listOf(OcrChunk(1,1,0,zones[0].box,Kind.TEXT)));reader.showOcr(mapOf(1 to "Le plaisir de lire à son rythme. ".repeat(40)));call(a,"updateControls")
  b(v,"Réglages")!!.performClick();assertNotNull(b(v,"Texte +"));b(v,"Texte +")!!.performClick()
  val font=ContinuousReaderView::class.java.getDeclaredField("fontSize").apply{isAccessible=true}.getFloat(reader)
  shot(a,"redesign-ocr-settings");b(v,"Fermer")!!.performClick();assertTrue((get(a,"status")as TextView).text.contains("erreurs possibles"));assertEquals(View.VISIBLE,(get(a,"status")as View).visibility);shot(a,"redesign-ocr-closed")
  b(v,"Original")!!.performClick();assertNull(b(v,"Texte +"));assertEquals(font,ContinuousReaderView::class.java.getDeclaredField("fontSize").apply{isAccessible=true}.getFloat(reader))
  assertNotNull(b(v,"Original ✓"));b(v,"Nettoyé")!!.performClick();assertNull(b(v,"Texte −"));assertNotNull(b(v,"Nettoyé ✓"))
  controller.pause().stop().destroy()
 }
}
