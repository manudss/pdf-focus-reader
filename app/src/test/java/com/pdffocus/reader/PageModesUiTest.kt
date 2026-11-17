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
class PageModesUiTest{
 private fun field(a:MainActivity,n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}
 private fun call(a:MainActivity,n:String,vararg values:Any){MainActivity::class.java.declaredMethods.first{it.name==n}.apply{isAccessible=true}.invoke(a,*values)}
 @Test fun sixModesVisibleBeforePdfAndManualDoesNotClearOrder(){
  val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();val a=controller.get()
  val strip=field(a,"modeControls").get(a)as ViewGroup
  val labels=mutableListOf<String>();fun walk(v:View){if(v is Button)labels+=v.text.toString();if(v is ViewGroup)for(k in 0 until v.childCount)walk(v.getChildAt(k))};walk(strip)
  assertEquals(listOf("Auto","BD","Manga","Journal","Livre","Manuel"),labels);assertEquals(View.VISIBLE,strip.visibility)
  field(a,"zones").set(a,listOf(Zone(1,Box(.1f,.1f,.9f,.8f),Kind.TEXT)));field(a,"order").set(a,listOf(1));field(a,"count").set(a,1)
  call(a,"choosePageMode",PageMode.MANUAL);assertEquals(listOf(1),field(a,"order").get(a));assertEquals(PageMode.MANUAL,field(a,"pageMode").get(a))
  assertEquals(View.GONE,(field(a,"autoButton").get(a)as View).visibility);controller.pause().stop().destroy()
 }
 @Test fun nativeComicMarkersAndSixModeLayout(){render()}
 @Test @Config(qualifiers="fr-rFR-w320dp-h640dp-xhdpi") fun narrowNativeComicMarkersAndSixModes(){render()}
 private fun render(){
  val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();val a=controller.get()
  val image=Bitmap.createBitmap(700,1050,Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);val c=Canvas(image);val p=Paint(Paint.ANTI_ALIAS_FLAG)
  p.color=Color.BLACK;p.textSize=32f;c.drawText("Le jardin du quartier",70f,75f,p)
  val panels=(0..2).flatMap{r->(0..1).map{k->Zone(r*2+k+1,Box(.04f+k*.48f,.13f+r*.25f,.48f+k*.48f,.36f+r*.25f),Kind.IMAGE,panel=true)}}
  for((n,z)in panels.withIndex()){val b=z.box;p.color=Color.rgb(210+n*5,226-n*4,205+n*5);c.drawRect(b.left*700,b.top*1050,b.right*700,b.bottom*1050,p);p.color=Color.rgb(50,109,93);c.drawCircle((b.left+b.right)*350,(b.top+b.bottom)*525,45f,p)}
  val proposal=ComicPagePartition.propose(panels,700,1050);val zones=proposal.zones
  for((n,v)in mapOf("count" to 1,"pageReady" to true,"preview" to image,"zones" to zones,"order" to zones.map{it.id},"pageMode" to PageMode.COMIC))field(a,n).set(a,v)
  val canvas=field(a,"canvas").get(a)as PageCanvas;canvas.image=image;canvas.zones=zones;canvas.selection=zones.map{it.id};call(a,"sync",false)
  val root=a.window.decorView;val d=a.resources.displayMetrics.density;val w=(a.resources.configuration.screenWidthDp*d).toInt();val h=(a.resources.configuration.screenHeightDp*d).toInt();root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h)
  val dir=File("/tmp/pdf-comic-native").apply{mkdirs()};repeat(2){val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(Canvas(b));if(it==1)File(dir,"modes-${a.resources.configuration.screenWidthDp}.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
  val strip=field(a,"modeControls").get(a)as ViewGroup;val row=strip.getChildAt(0)as ViewGroup
  for(k in 0 until row.childCount){val button=row.getChildAt(k)as Button;assertTrue(button.width>=40*d);assertTrue(button.height>=44*d)}
  val controls=field(a,"controls").get(a)as ViewGroup
  fun noCut(v:View){if(v is Button){val layout=v.layout;if(layout!=null)for(line in 0 until layout.lineCount)assertEquals("Command must not be ellipsized: ${v.text}",0,layout.getEllipsisCount(line));assertTrue("Command text fits available width: ${v.text}",v.paint.measureText(v.text.toString())<=v.width-v.compoundPaddingLeft-v.compoundPaddingRight+1)};if(v is ViewGroup)for(k in 0 until v.childCount)noCut(v.getChildAt(k))};noCut(controls)
  controller.pause().stop().destroy()
 }
}
