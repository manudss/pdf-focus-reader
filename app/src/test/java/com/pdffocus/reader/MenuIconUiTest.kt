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
class MenuIconUiTest {
 @Test fun fullMenuKeepsEveryIconAndDescription(){render()}
 @Test @Config(qualifiers="fr-rFR-w320dp-h640dp-xhdpi") fun narrowMenuKeepsEveryIconAndDescription(){render()}
 private fun render(){
  val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();val a=controller.get()
  fun set(n:String,v:Any){MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.set(a,v)}
  set("count",1);set("pageReady",true);set("zones",listOf(Zone(1,Box(.1f,.1f,.9f,.9f),Kind.TEXT)));set("order",listOf(1))
  MainActivity::class.java.getDeclaredMethod("menu").apply{isAccessible=true}.invoke(a)
  val d=MainActivity::class.java.getDeclaredField("menuDialog").apply{isAccessible=true}.get(a)as android.app.Dialog
  val root=d.window!!.decorView;val density=a.resources.displayMetrics.density;val w=(a.resources.configuration.screenWidthDp*density).toInt();val h=(a.resources.displayMetrics.heightPixels*.85f).toInt()
  root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h)
  var icons=0;val labels=mutableListOf<String>();var scroll:ScrollView?=null
  fun walk(v:View){if(v is ScrollView)scroll=v;if(v is TextView){labels+=v.text.toString();if(v !is Button&&v.compoundDrawablesRelative[0] is ActionIcon){icons++;assertTrue(v.compoundDrawablesRelative[0].bounds.width()>0);assertTrue(v.width>v.compoundPaddingLeft+v.compoundPaddingRight)}};if(v is ViewGroup)for(k in 0 until v.childCount)walk(v.getChildAt(k))};walk(root)
  assertEquals(12,icons);assertTrue(labels.contains("Licences des composants"));assertTrue(labels.contains("Politique de confidentialité"));assertTrue(labels.contains("Propose des regroupements parmi les blocs choisis. Vous validez ceux à appliquer."))
  val dir=File("/tmp/pdf-menu-visual").apply{mkdirs()}
  for(lower in listOf(false,true)){
   if(lower)scroll!!.scrollTo(0,scroll!!.getChildAt(0).height)
   repeat(2){val image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(Canvas(image));if(it==1)File(dir,"menu-${a.resources.configuration.screenWidthDp}-${if(lower)"lower" else "upper"}.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
  }
  d.dismiss();controller.pause().stop().destroy()
 }
}
