package com.pdffocus.reader
import android.graphics.*
import android.content.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],qualifiers="fr-rFR-w393dp-h852dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArticleImageExportTest{
 private val context get()=RuntimeEnvironment.getApplication()
 private fun reader():ContinuousReaderView=ContinuousReaderView(context).apply{layout(0,0,700,900);setDocument(listOf(Zone(1,Box(.1f,.1f,.9f,.3f),Kind.TEXT),Zone(2,Box(.1f,.4f,.5f,.9f),Kind.IMAGE)),700,1500)}
 private fun output()=File.createTempFile("article-test",".png",context.cacheDir)
 @Test fun pngContainsEveryOffscreenBlockAndNoFooter(){
  val r=reader();r.endTitle="Fin de la page";r.reprocess();val position=r.currentPosition();val plan=r.articleImagePlan();assertTrue(plan.height>r.height);val file=output();val rendered=mutableSetOf<Int>()
  val size=ArticleImageExporter.write(plan,file){tile->rendered+=tile.ordinal;Bitmap.createBitmap(kotlin.math.ceil(tile.rect.width()).toInt(),kotlin.math.ceil(tile.rect.height()).toInt(),Bitmap.Config.ARGB_8888).apply{eraseColor(if(tile.ordinal==0)Color.RED else Color.BLUE)}}
  val png=BitmapFactory.decodeFile(file.absolutePath);assertNotNull(png);assertEquals(size.height,png.height);assertEquals(setOf(0,1),rendered)
  assertEquals(Color.RED,png.getPixel(png.width/2,5));assertEquals(Color.BLUE,png.getPixel(png.width/2,png.height-2));assertEquals(position,r.currentPosition());png.recycle();file.delete()
 }
 @Test fun zoomExportsFullBlockWidthWithoutViewportClipping(){val r=reader();r.zoomBy(2f);val plan=r.articleImagePlan();val size=plan.size();val extent=plan.tiles.maxOf{it.rect.right}-plan.tiles.minOf{it.rect.left};assertTrue(size.width>=extent*size.scale-1);assertTrue(size.width>700)}
 @Test fun ocrRendersTextAndRefusesPartialStream(){
  val r=reader();val plan=listOf(OcrChunk(1,1,0,Box(.1f,.1f,.9f,.9f),Kind.TEXT));r.showOcrStream(plan,mapOf(1 to "Tous les blocs de la lecture. ".repeat(200)),true)
  try{r.articleImagePlan();fail("Partial OCR cannot be silently shared")}catch(_:IllegalStateException){}
  r.showOcrStream(plan,mapOf(1 to "Tous les blocs de la lecture. ".repeat(200)),false);val snapshot=r.articleImagePlan();assertTrue(snapshot.tiles.isEmpty());assertTrue(snapshot.texts.isNotEmpty());val file=output();ArticleImageExporter.write(snapshot,file){error("OCR text must be drawn, not rerendered as the source PDF")};assertTrue(file.length()>1000);assertNotNull(BitmapFactory.decodeFile(file.absolutePath));file.delete()
 }
 @Test fun failedOrCancelledRenderLeavesNoPartialImage(){for(cancel in listOf(false,true)){val file=output();try{ArticleImageExporter.write(reader().articleImagePlan(),file,{cancel}){null};fail()}catch(_:Exception){};assertFalse(file.exists())}}
 @Test fun excessiveLengthFailsRatherThanCroppingOrSharingUnreadableOutput(){val p=reader().articleImagePlan().copy(height=1_000_000f);try{p.size();fail()}catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("trop long"))}}
 @Test fun providerIsReadOnlyAndCannotExposePdfOrSession(){
  val provider=Robolectric.buildContentProvider(ArticleImageProvider::class.java).create().get()
  val dir=File(context.cacheDir,"article-images").apply{mkdirs()};val file=File(dir,"article-${java.util.UUID.randomUUID()}.png");file.writeBytes(byteArrayOf(1,2,3));val uri=ArticleImageProvider.uri(context,file)
  assertEquals("image/png",provider.getType(uri));provider.openFile(uri,"r").close()
  for(bad in listOf(uri.buildUpon().path("../current.pdf").build(),uri.buildUpon().path("session.json").build())){try{provider.openFile(bad,"r");fail()}catch(_:Exception){}}
  try{provider.openFile(uri,"rw");fail()}catch(_:IllegalArgumentException){};file.delete()
 }
}
