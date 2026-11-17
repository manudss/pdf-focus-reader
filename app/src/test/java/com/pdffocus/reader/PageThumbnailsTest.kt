package com.pdffocus.reader
import android.graphics.*
import android.os.Looper
import android.view.*
import android.widget.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],qualifiers="fr-rFR-w393dp-h852dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageThumbnailsTest {
 private val c get()=RuntimeEnvironment.getApplication() as android.content.Context
 private fun fixture(n:Int):Bitmap{val b=Bitmap.createBitmap(240,340,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);val canvas=Canvas(b);val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.DKGRAY;textSize=17f};canvas.drawText("Journal fictif",12f,28f,p);p.textSize=8f;for(i in 1..26){canvas.drawText("Un texte pour découvrir",12f,45f+i*10,p);canvas.drawText("la page et son numéro.",125f,45f+i*10,p)};if(n%3==0){p.color=Color.rgb(180,208,191);canvas.drawRect(12f,46f,228f,120f,p)};return b}
 @Test fun jobsAreBoundedAndCloseCancelsUnstartedRenders(){val jobs=mutableListOf<()->Unit>();var calls=0;val a=PageThumbnails(c,68,16,{jobs+=it},{calls++;fixture(it)});val parent=GridView(c);a.visible(15,9);for(n in 0..67)a.getView(n,null,parent);assertTrue(a.pendingCount()<=24);jobs.toList().forEach{it()};Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(calls<=12);assertTrue(a.cachedBytes()<=3*1024*1024);a.close();val count=calls;val b=PageThumbnails(c,68,16,{jobs+=it},{calls++;fixture(it)});b.getView(16,null,parent);b.close();jobs.last()();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(count,calls)}
 @Test fun nativeGridAndSelectionNumbers(){val jobs=mutableListOf<()->Unit>();val a=PageThumbnails(c,68,16,{jobs+=it},{fixture(it)});val grid=GridView(c).apply{numColumns=3;horizontalSpacing=18;verticalSpacing=24;adapter=a};fun layout(){grid.measure(View.MeasureSpec.makeMeasureSpec(1179,1073741824),View.MeasureSpec.makeMeasureSpec(1800,1073741824));grid.layout(0,0,1179,1800)};layout();grid.setSelection(15);a.visible(15,9);layout();var done=0;repeat(5){val batch=jobs.drop(done);done=jobs.size;batch.forEach{it()};Shadows.shadowOf(Looper.getMainLooper()).idle();layout()};val image=Bitmap.createBitmap(1179,1800,Bitmap.Config.ARGB_8888);image.eraseColor(Color.rgb(250,249,246));grid.draw(Canvas(image));File("build/ui-snapshots").mkdirs();File("build/ui-snapshots/page-summary-grid.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};assertEquals(68,a.count);assertEquals(67L,a.getItemId(67));assertTrue(a.getView(16,null,grid).contentDescription.toString().contains("Page 17, page actuelle"));a.close();image.recycle()}
 @Test fun summaryCancelKeepsReadingPositionAndOrder(){val ctl=Robolectric.buildActivity(MainActivity::class.java).setup().visible();val a=ctl.get();fun set(n:String,v:Any){MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.set(a,v)}
fun get(n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.get(a)
set("count",68);set("page",16);set("reading",true);set("order",listOf(3,1,2));set("scrollPosition",620);set("frameIndex",2);val method=MainActivity::class.java.getDeclaredMethod("showPageSummary").apply{isAccessible=true};method.invoke(a);val dialog=get("pageSummary")as android.app.Dialog;assertTrue(dialog.isShowing);dialog.cancel();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(16,get("page"));assertEquals(true,get("reading"));assertEquals(listOf(3,1,2),get("order"));assertEquals(620,get("scrollPosition"));assertEquals(2,get("frameIndex"));assertNull(get("pageSummary"));ctl.pause().stop().destroy()}
 @Test fun visibleCellsRetainTheirRasterWhenCacheEvictsIt(){val jobs=mutableListOf<()->Unit>();var calls=0;val a=PageThumbnails(c,68,16,{jobs+=it},{calls++;fixture(it)});a.visible(10,18);val parent=GridView(c);val cells=(10..27).associateWith{a.getView(it,null,parent)};jobs.toList().forEach{it()};Shadows.shadowOf(Looper.getMainLooper()).idle();val count=calls;for((n,cell)in cells)a.getView(n,cell,parent);assertEquals(count,calls);assertEquals(0,a.pendingCount());assertTrue(a.cachedBytes()<=3*1024*1024);a.close()}
 @Test fun fullDialogSnapshotUsesActualAndroidLayoutWithSyntheticPages(){
 val ctl=Robolectric.buildActivity(MainActivity::class.java).setup().visible();val a=ctl.get()
 for((n,value)in mapOf("count" to 68,"page" to 16))MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.set(a,value)
 MainActivity::class.java.getDeclaredMethod("showPageSummary").apply{isAccessible=true}.invoke(a)
 val dialog=MainActivity::class.java.getDeclaredField("pageSummary").apply{isAccessible=true}.get(a)as android.app.Dialog
 fun find(v:View):GridView?{if(v is GridView)return v;if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let{return it};return null}
 val root=dialog.window!!.decorView;val grid=find(root)!!;(grid.adapter as PageThumbnails).close();val jobs=mutableListOf<()->Unit>();val adapter=PageThumbnails(c,68,16,{jobs+=it},{fixture(it)});grid.adapter=adapter
 fun layout(){root.measure(View.MeasureSpec.makeMeasureSpec(1179,1073741824),View.MeasureSpec.makeMeasureSpec(2556,1073741824));root.layout(0,0,1179,2556)}
 layout();grid.setSelection(15);adapter.visible(15,12);layout();var done=0;repeat(4){val batch=jobs.drop(done);done=jobs.size;batch.forEach{it()};Shadows.shadowOf(Looper.getMainLooper()).idle();layout()}
 val image=Bitmap.createBitmap(1179,2556,Bitmap.Config.ARGB_8888);root.draw(Canvas(image));File("build/ui-snapshots").mkdirs();File("build/ui-snapshots/page-summary-dialog.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle();adapter.close();dialog.dismiss();ctl.pause().stop().destroy()
 }
}
