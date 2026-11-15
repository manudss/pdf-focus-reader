package com.pdffocus.reader
import android.graphics.*
import android.view.*
import android.widget.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Layout snapshots only. Device instrumentation separately tests native OCR/PDF execution. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],qualifiers="fr-rFR-w393dp-h852dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeUiSnapshotTest {
    @Test fun renderActualAndroidHomeAndZoneCanvas(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible();val a=controller.get()
        val root=a.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2556,View.MeasureSpec.EXACTLY));root.layout(0,0,1179,2556)
        snapshot(root,"native-home")
        val page=findCanvas(root)!!
        val image=Bitmap.createBitmap(900,1350,Bitmap.Config.ARGB_8888);val c=Canvas(image);c.drawColor(Color.WHITE);val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=34f}
        c.drawText("Un journal fictif",40f,75f,p);p.textSize=25f
        for(col in 0..1)for(line in 0..20)c.drawText(if(col==0)"Lire à son rythme." else "Un bloc après l'autre.",40f+col*430f,150f+line*38,p)
        p.color=Color.rgb(75,145,122);c.drawRect(40f,1020f,860f,1280f,p)
        page.image=image;page.zones=listOf(Zone(1,Box(.04f,.08f,.46f,.72f),Kind.TEXT),Zone(2,Box(.52f,.08f,.96f,.72f),Kind.TEXT),Zone(3,Box(.04f,.75f,.96f,.95f),Kind.IMAGE));page.selection=listOf(2,1,3)
        snapshot(root,"native-canvas-fixture")
        controller.pause().stop().destroy()
    }
    @Test fun pictureCandidatesExcludeRecognizedText(){
        val context=org.robolectric.RuntimeEnvironment.getApplication()
        val b=Bitmap.createBitmap(400,600,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE)
        val random=java.util.Random(7)
        for(y in 100 until 320)for(x in 20 until 380)if(x<170||x>210)b.setPixel(x,y,Color.rgb(30+random.nextInt(160),30+random.nextInt(160),30+random.nextInt(160)))
        val text=Box(.04f,.15f,.44f,.55f)
        val boxes=ZoneDetector(context).pictureCandidates(b,listOf(text)){false}
        assertTrue(boxes.any{it.left>.45f})
        assertTrue(boxes.none{it.intersect(text)>.1f*it.area})
        b.recycle()
    }
    private fun snapshot(v:View,name:String){val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val out=File("build/ui-snapshots").apply{mkdirs()};File(out,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    private fun findCanvas(v:View):PageCanvas?{if(v is PageCanvas)return v;if(v is ViewGroup)for(i in 0 until v.childCount)findCanvas(v.getChildAt(i))?.let{return it};return null}
}
