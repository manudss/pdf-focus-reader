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
class OcrReviewUiTest {
 private val context get()=RuntimeEnvironment.getApplication() as android.content.Context
 private fun shot(v:View,name:String,w:Int=1080,h:Int=1400){v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);val image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);v.draw(Canvas(image));val out=File("build/ui-snapshots").apply{mkdirs()};File(out,"$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
 @Test fun tapUsesMarkedWordAndDoesNotSelectPlainText(){
  val view=ContinuousReaderView(context);val box=Box(0f,0f,1f,1f);val r=OcrReview.compare("Une corrlction visible ici.","Une correction visible ici.",box);view.showOcrStream(listOf(OcrChunk(1,1,0,box,Kind.TEXT)),mapOf(1 to r.corrected),false,reviewTexts=mapOf(1 to r));shot(view,"ocr-inline-tap");var hits=0;view.onOcrMark={key,id,_,_->assertEquals(1,key);assertEquals(0,id);hits++}
  val field=ContinuousReaderView::class.java.getDeclaredField("textItems").apply{isAccessible=true};@Suppress("UNCHECKED_CAST")val item=(field.get(view)as List<ContinuousReaderView.TextItem>).first();val m=r.rendered().second.single();val line=item.layout.getLineForOffset(m.start);val density=context.resources.displayMetrics.density;val x=16*density+(item.layout.getPrimaryHorizontal(m.start)+item.layout.getPrimaryHorizontal(m.end))/2;val y=item.top+(item.layout.getLineTop(line)+item.layout.getLineBottom(line))/2f
  fun tap(x:Float,y:Float){val t=android.os.SystemClock.uptimeMillis();val down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0);val up=MotionEvent.obtain(t,t+80,MotionEvent.ACTION_UP,x,y,0);view.onTouchEvent(down);view.onTouchEvent(up);down.recycle();up.recycle()};tap(x,y);assertEquals(1,hits);tap(25*density,y+200*density);assertEquals(1,hits)
 }
 @Test fun nativeReadingAndComparisonWithReversibleChoice(){
  val view=ContinuousReaderView(context);val box=Box(0f,0f,1f,1f);val raw="La lecture du texte corrlgé doit rester simple. Un tap permet de comparer le mot avec la version brute.\n\nLes paragraphes sont conservés, ainsi que les nombres : 50 000 logements.";val fixed=raw.replace("corrlgé","corrigé");val review=OcrReview.compare(raw,fixed,box);val plan=listOf(OcrChunk(1,1,0,box,Kind.TEXT));view.showOcrStream(plan,mapOf(1 to fixed),false,reviewTexts=mapOf(1 to review));shot(view,"ocr-inline-reading");val id=review.edits.single().id;view.chooseRaw(1,id,true);assertEquals(raw,view.review(1)!!.rendered().first);view.showOcrStream(plan,mapOf(1 to fixed),false,reviewTexts=mapOf(1 to review));assertEquals(raw,view.review(1)!!.rendered().first);view.chooseRaw(1,id,false);assertEquals(fixed,view.review(1)!!.rendered().first)
  var keep=false;val popup=OcrComparisonPopup(context,review.edits.single(),false,{keep=it},{ });shot(popup.content,"ocr-inline-comparison",1000,1050);(popup.content.getChildAt(4)as Button).performClick();assertTrue(keep)
 }
}
