package com.pdffocus.reader
import android.graphics.*
import android.view.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],qualifiers="fr-rFR-w393dp-h852dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class OcrReflowUiTest {
 private fun line(s:String,y:Float,x:Float=.1f)=s.split(' ').let{ts->ts.mapIndexed{i,t->OcrWord(t,Box(x+i*(.9f-x)/ts.size,y,x+(i+1)*(.9f-x)/ts.size-.001f,y+.02f),Box(x,y,.9f,y+.02f))}}
 @Test fun realRegressionPatternsReflowWithoutSourceWidthBreaks(){
  val repair=OcrEnhancer(listOf("brutalités","intimidation","gouvernemental","notamment","Saint-Denis"));val words=line("Des bru-",0f)+line("talités et des gestes d'inti-",.029f)+line("midation sont évoqués.",.058f)+line("Un débat gouver-",.12f,.14f)+line("nemental commence à Saint-",.149f)+line("Denis. Les mots retrouvent",.178f)+line("leur place dans le paragraphe.",.207f)
  val fixed=repair.text(words);assertTrue(fixed.contains("brutalités"));assertTrue(fixed.contains("d'intimidation"));assertTrue(fixed.contains("gouvernemental"));assertTrue(fixed.contains("Saint-Denis"));assertEquals(2,fixed.split("\n\n").size)
  val view=ContinuousReaderView(RuntimeEnvironment.getApplication());view.measure(View.MeasureSpec.makeMeasureSpec(1179,1073741824),View.MeasureSpec.makeMeasureSpec(2200,1073741824));view.layout(0,0,1179,2200);val box=Box(0f,0f,1f,1f);val raw=OcrFlow.textByZone(listOf(Zone(1,box,Kind.TEXT)),words)[1]!!;val review=OcrReview.compare(raw,fixed,box);view.showOcrStream(listOf(OcrChunk(1,1,0,box,Kind.TEXT)),mapOf(1 to fixed),false,reviewTexts=mapOf(1 to review));val image=Bitmap.createBitmap(1179,2200,Bitmap.Config.ARGB_8888);view.draw(Canvas(image));File("build/ui-snapshots").mkdirs();File("build/ui-snapshots/ocr-paragraph-regression.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
 }
}
