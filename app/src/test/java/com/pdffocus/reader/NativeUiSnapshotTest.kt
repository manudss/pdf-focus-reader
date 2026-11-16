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
    @Test fun continuousTilesScrollAndResetWithoutReusingOldResults(){
        val reader=ContinuousReaderView(org.robolectric.RuntimeEnvironment.getApplication())
        reader.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1700,View.MeasureSpec.EXACTLY));reader.layout(0,0,1080,1700)
        val source=Bitmap.createBitmap(600,2600,Bitmap.Config.ARGB_8888);source.eraseColor(Color.WHITE);val c=Canvas(source);val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=25f}
        for(n in 1..65)c.drawText("Ligne $n : une lecture qui continue.",18f,35f+n*38,p)
        var requests=0
        reader.onRequest={tile,revision->requests++;val b=Bitmap.createBitmap(kotlin.math.ceil(tile.rect.width()).toInt(),kotlin.math.ceil(tile.rect.height()).toInt(),Bitmap.Config.ARGB_8888);Canvas(b).drawBitmap(source,Rect((tile.source.left*600).toInt(),(tile.source.top*2600).toInt(),(tile.source.right*600).toInt(),(tile.source.bottom*2600).toInt()),Rect(0,0,b.width,b.height),Paint(Paint.FILTER_BITMAP_FLAG));reader.accept(tile,revision,b)}
        reader.setDocument(listOf(Zone(1,Box(0f,0f,1f,1f),Kind.TEXT)),600,2600)
        snapshot(reader,"continuous-priming");snapshot(reader,"continuous-top")
        val now=android.os.SystemClock.uptimeMillis();reader.onTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,200f,1400f,0));reader.onTouchEvent(MotionEvent.obtain(now,now+200,MotionEvent.ACTION_MOVE,200f,200f,0));reader.onTouchEvent(MotionEvent.obtain(now,now+250,MotionEvent.ACTION_UP,200f,200f,0))
        assertTrue(reader.currentPosition()>0);snapshot(reader,"continuous-scroll-priming");snapshot(reader,"continuous-scrolled");assertTrue(requests>1)
        reader.clear();assertEquals(0,reader.currentPosition());source.recycle()
    }
    @Test fun ocrModeDrawsReflowedTextWithRealFont(){
        val reader=ContinuousReaderView(org.robolectric.RuntimeEnvironment.getApplication());reader.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1700,View.MeasureSpec.EXACTLY));reader.layout(0,0,1080,1700)
        reader.setDocument(listOf(Zone(1,Box(0f,0f,1f,1f),Kind.TEXT)),600,2600)
        reader.showOcr(mapOf(1 to "Le plaisir de lire commence par un écran confortable. Le texte reconnu utilise une vraie police, et revient automatiquement à la ligne. Les accents restent présents : été, où, français.\n\n".repeat(10)))
        snapshot(reader,"ocr-real-font");reader.zoomBy(1.1f);snapshot(reader,"ocr-larger-font");reader.clear()
    }
    @Test fun readingKeepsColumnsApartUnderAWideTitle(){
        // Fictional magazine geometry mirroring a reported layout: a wide short title
        // left-aligned above the first of three text columns. Content is invented.
        val reader=ContinuousReaderView(org.robolectric.RuntimeEnvironment.getApplication())
        reader.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1700,View.MeasureSpec.EXACTLY));reader.layout(0,0,1080,1700)
        val source=Bitmap.createBitmap(900,1273,Bitmap.Config.ARGB_8888);source.eraseColor(Color.WHITE)
        val c=Canvas(source);val titlePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(226,118,27);textSize=40f;typeface=Typeface.create("sans-serif",Typeface.BOLD)}
        c.drawText("Un titre large et inventé",42f,200f,titlePaint)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=22f}
        for((x,label)in listOf(42f to "Colonne une",325f to "Colonne deux",628f to "Colonne trois"))for(line in 0..29)c.drawText("$label, note ${line+1}.",x,320f+line*30,p)
        val requested=mutableMapOf<Int,MutableList<Box>>()
        reader.onRequest={tile,revision->requested.getOrPut(tile.chunkKey){mutableListOf()}.add(tile.source)
            val b=Bitmap.createBitmap(kotlin.math.ceil(tile.rect.width()).toInt(),kotlin.math.ceil(tile.rect.height()).toInt(),Bitmap.Config.ARGB_8888)
            Canvas(b).drawBitmap(source,Rect((tile.source.left*900).toInt(),(tile.source.top*1273).toInt(),(tile.source.right*900).toInt(),(tile.source.bottom*1273).toInt()),Rect(0,0,b.width,b.height),Paint(Paint.FILTER_BITMAP_FLAG));reader.accept(tile,revision,b)}
        val col1=Zone(2,Box(.047f,.191f,.331f,.947f),Kind.TEXT);val col2=Zone(3,Box(.361f,.219f,.669f,.930f),Kind.TEXT);val col3=Zone(4,Box(.698f,.219f,1f,.930f),Kind.TEXT)
        reader.setDocument(listOf(Zone(1,Box(.047f,.128f,.538f,.175f),Kind.TEXT),col1,col2,col3),900,1273)
        snapshot(reader,"wide-title-priming");snapshot(reader,"wide-title-reading-top")
        var now=android.os.SystemClock.uptimeMillis()
        fun drag(dy:Float){reader.onTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,540f,1400f,0));now+=200;reader.onTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_MOVE,540f,1400f-dy,0));now+=50;reader.onTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_UP,540f,1400f-dy,0))}
        drag(4150f);snapshot(reader,"wide-title-middle-priming");snapshot(reader,"wide-title-reading-middle")
        drag(4000f);snapshot(reader,"wide-title-end-priming");snapshot(reader,"wide-title-reading-end")
        // Each column keeps its own extent as the tile source: no widening toward the
        // title, no strip subtracted by a neighbour, whatever the scroll position.
        assertTrue(requested.getValue(2).isNotEmpty());assertTrue(requested.getValue(3).isNotEmpty());assertTrue(requested.getValue(4).isNotEmpty())
        assertTrue(requested.getValue(2).all{it.left>=.046f&&it.right<=.332f})
        assertTrue(requested.getValue(3).all{it.left>=.360f&&it.right<=.670f})
        assertTrue(requested.getValue(4).all{it.left>=.697f})
        val tiles=requested.values.flatten();for(i in tiles.indices)for(j in i+1 until tiles.size)assertEquals(0f,tiles[i].intersect(tiles[j]),.000001f)
        assertTrue(reader.currentPosition()>7000);reader.clear();source.recycle()
    }
    private fun snapshot(v:View,name:String){val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));val out=File("build/reports/ui-snapshots").apply{mkdirs()};File(out,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
    private fun findCanvas(v:View):PageCanvas?{if(v is PageCanvas)return v;if(v is ViewGroup)for(i in 0 until v.childCount)findCanvas(v.getChildAt(i))?.let{return it};return null}
}
