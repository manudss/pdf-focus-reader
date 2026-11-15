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
 private fun shot(a:MainActivity,name:String){val v=a.window.decorView;val density=a.resources.displayMetrics.density;val w=(a.resources.configuration.screenWidthDp*density).toInt();val h=(a.resources.configuration.screenHeightDp*density).toInt();v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);repeat(2){val image=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);fun dirty(q:View){q.invalidate();if(q is ViewGroup)for(i in 0 until q.childCount)dirty(q.getChildAt(i))};dirty(v);v.draw(Canvas(image));if(it==1)File("/tmp/pdf-native-ui/$name-${a.resources.configuration.screenWidthDp}.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()};System.gc()}
 @Test @Config(qualifiers="fr-rFR-w360dp-h800dp-xhdpi") fun mediumDensityControls(){compactControlsStayContextualAndOcrSizeSurvivesSwitch()}
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
  fun checkSegments(){
   val controls=get(a,"controls")as ViewGroup
   for(i in 0 until controls.childCount){val row=controls.getChildAt(i)as? ViewGroup?:continue
    for(j in 0 until row.childCount){val child=row.getChildAt(j)as? Button?:continue
     assertNull(child.stateListAnimator);assertEquals(0f,child.elevation);assertEquals(0f,child.translationZ)
     assertTrue(child.top>=row.paddingTop);assertTrue(child.bottom<=row.height-row.paddingBottom)
    }
   }
  }
  shot(a,"redesign-ocr-settings");checkSegments();b(v,"Texte OCR ✓")!!.isPressed=true;shot(a,"redesign-ocr-pressed");checkSegments();b(v,"Texte OCR ✓")!!.isPressed=false
  set(a,"enhancedOcr",true);call(a,"updateControls");shot(a,"redesign-ocr-improved");checkSegments()
  set(a,"enhancedOcr",false);call(a,"updateControls");shot(a,"redesign-ocr-settings");b(v,"Fermer")!!.performClick();assertTrue((get(a,"status")as TextView).text.contains("erreurs possibles"));assertEquals(View.VISIBLE,(get(a,"status")as View).visibility);shot(a,"redesign-ocr-closed")
  b(v,"Original")!!.performClick();assertNull(b(v,"Texte +"));assertEquals(font,ContinuousReaderView::class.java.getDeclaredField("fontSize").apply{isAccessible=true}.getFloat(reader))
  assertNotNull(b(v,"Original ✓"));shot(a,"redesign-original");checkSegments();b(v,"Nettoyé")!!.performClick();assertNull(b(v,"Texte −"));assertNotNull(b(v,"Nettoyé ✓"));shot(a,"redesign-cleaned");checkSegments()
  // End-of-document must stay reachable above controls in every mode and viewport.
  val move=ContinuousReaderView::class.java.getDeclaredMethod("move",Float::class.javaPrimitiveType).apply{isAccessible=true}
  fun lastLine(name:String){move.invoke(reader,Float.MAX_VALUE);shot(a,name);val loc=IntArray(2);reader.getLocationOnScreen(loc);val controls=get(a,"controls")as View;val below=IntArray(2);controls.getLocationOnScreen(below);assertTrue("Controls cannot overlay the reader viewport",loc[1]+reader.height<=below[1]);val tiles=ContinuousReaderView::class.java.getDeclaredField("tiles").apply{isAccessible=true}.get(reader)as List<ContinuousReaderView.Tile>;val texts=ContinuousReaderView::class.java.getDeclaredField("textItems").apply{isAccessible=true}.get(reader)as List<ContinuousReaderView.TextItem>;val end=maxOf(tiles.maxOfOrNull{it.rect.bottom}?:0f,texts.maxOfOrNull{it.top+it.layout.height}?:0f);assertTrue(end-reader.currentPosition()<reader.height)}
  lastLine("end-cleaned");b(v,"Réglages")!!.performClick();lastLine("end-cleaned-open");b(v,"Original")!!.performClick();lastLine("end-original-open");reader.zoomBy(1.7f);lastLine("end-original-zoom");b(v,"Texte OCR")!!.performClick();reader.showOcr(mapOf(1 to "Le texte final reste visible. ".repeat(100)));lastLine("end-ocr-open");b(v,"Texte +")!!.performClick();lastLine("end-ocr-large");b(v,"Fermer")!!.performClick();lastLine("end-ocr-closed")
  controller.pause().stop().destroy()
 }
}
