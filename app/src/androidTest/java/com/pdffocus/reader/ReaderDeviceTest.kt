package com.pdffocus.reader

import android.content.Intent
import android.graphics.*
import android.view.*
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderDeviceTest {
    @Test fun bundledOcrWorksWithoutNetworkAndFindsSyntheticText(){
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        c.assets.open("tessdata_best/fra-words.txt").bufferedReader().use{assertTrue("Packaged repair lexicon must be readable",it.readLines().size>10000)}
        val b=Bitmap.createBitmap(900,1400,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(b);canvas.drawColor(Color.WHITE);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=28f}
        for(i in 0..14){canvas.drawText("Un journal simple et lisible",35f,100f+i*50,paint);canvas.drawText("Une autre colonne de texte",470f,100f+i*50,paint)}
        val zones=ZoneDetector(c).detect(b){false}
        assertTrue("Bundled OCR must find text",zones.any{it.kind==Kind.TEXT})
        assertTrue(zones.all{it.box.left>=0&&it.box.right<=1&&it.box.top>=0&&it.box.bottom<=1})
        b.recycle()
    }
    @Test fun opensSyntheticDemoSelectsBlocksAndShowsReader(){
        val instrumentation=InstrumentationRegistry.getInstrumentation();val c=instrumentation.targetContext
        File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            screenshot(scenario,"home")
            scenario.onActivity{a->findButton(a.window.decorView,"Essayer un exemple")!!.performClick()}
            val end=System.currentTimeMillis()+90000
            var ready=false
            while(System.currentTimeMillis()<end){
                scenario.onActivity{a->val page=findCanvas(a.window.decorView);ready=page!=null&&page.zones.isNotEmpty()}
                if(ready)break
                Thread.sleep(250)
            }
            assertTrue("Synthetic PDF must load and detect blocks",ready)
            screenshot(scenario,"detected")
            scenario.onActivity{a->
                val page=findCanvas(a.window.decorView)!!
                val selected=page.zones.filter{it.kind==Kind.TEXT&&it.box.height>.1f}.take(2)
                assertTrue(selected.isNotEmpty())
                selected.reversed().forEach{page.onZoneTap!!.invoke(it)}
                assertEquals(selected.reversed().map{it.id},page.selection)
            }
            screenshot(scenario,"selected")
            scenario.onActivity{a->
                findButton(a.window.decorView,"Lire (")!!.performClick()
            }
            Thread.sleep(1500);instrumentation.waitForIdleSync();screenshot(scenario,"reader")
            scenario.onActivity{a->val toggle=findButton(a.window.decorView,"Original")!!;toggle.performClick();assertNotNull(findButton(a.window.decorView,"Nettoyé"))}
            Thread.sleep(800);screenshot(scenario,"reader-original")
            scenario.onActivity{a->findButton(a.window.decorView,"Nettoyé")!!.performClick();assertNotNull(findButton(a.window.decorView,"Original"))}
            Thread.sleep(800);screenshot(scenario,"reader-cleaned")
            var ocrHeight=0
            scenario.onActivity{a->
                findButton(a.window.decorView,"Texte OCR")!!.performClick()
                val reader=findReader(a.window.decorView)!!
                val field=ContinuousReaderView::class.java.getDeclaredField("textItems").apply{isAccessible=true}
                val items=field.get(reader) as List<*>
                assertTrue("OCR mode must render real recognized text",items.isNotEmpty())
                ocrHeight=(items.first() as ContinuousReaderView.TextItem).layout.height
                findButton(a.window.decorView,"Texte +")!!.performClick()
                val grown=field.get(reader) as List<*>
                assertTrue("OCR text must reflow at larger font size",(grown.first() as ContinuousReaderView.TextItem).layout.height>ocrHeight)
            }
            Thread.sleep(800);screenshot(scenario,"reader-ocr")
            scenario.onActivity{a->findButton(a.window.decorView,"OCR amélioré")!!.performClick()}
            val finish=System.currentTimeMillis()+90000;var complete=false
            while(System.currentTimeMillis()<finish){scenario.onActivity{a->val f=MainActivity::class.java.getDeclaredField("ocrLoading").apply{isAccessible=true};complete=!(f.get(a) as Boolean)};if(complete)break;Thread.sleep(100)}
            assertTrue("Improved local OCR must complete",complete)
            screenshot(scenario,"reader-ocr-improved")
            scenario.onActivity{a->assertFalse("Improved OCR must not fail silently",findText(a.window.decorView,"OCR partiel"));val f=MainActivity::class.java.getDeclaredField("improvedChunks").apply{isAccessible=true};assertTrue("Improved chunks must actually be produced",(f.get(a) as Map<*,*>).isNotEmpty());assertNotNull(findButton(a.window.decorView,"OCR amélioré ✓"));findButton(a.window.decorView,"OCR brut")!!.performClick();assertNotNull(findButton(a.window.decorView,"OCR brut ✓"))}

            scenario.onActivity{a->findButton(a.window.decorView,"Original")!!.performClick()}
            Thread.sleep(500)
            scenario.onActivity{a->val reader=findReader(a.window.decorView)!!;val now=android.os.SystemClock.uptimeMillis();reader.onTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,200f,600f,0));reader.onTouchEvent(MotionEvent.obtain(now,now+100,MotionEvent.ACTION_MOVE,200f,150f,0));reader.onTouchEvent(MotionEvent.obtain(now,now+150,MotionEvent.ACTION_UP,200f,150f,0));assertTrue("Swipe must move continuous stream",reader.currentPosition()>0)}
            Thread.sleep(1000);screenshot(scenario,"reader-scrolled")
            scenario.onActivity{a->assertNotNull(findButton(a.window.decorView,"Vue page"));assertTrue(findReader(a.window.decorView)!!.visibility==View.VISIBLE);findButton(a.window.decorView,"Vue page")!!.performClick()}
            scenario.recreate()
            Thread.sleep(2000)
        }
    }
    @Test fun pageNavigationDoesNotWaitForDetection(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        val file=File(c.cacheDir,"navigation.pdf")
        val pdf=android.graphics.pdf.PdfDocument()
        for(n in 1..3){val p=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(700,1000,n).create());p.canvas.drawColor(Color.WHITE);p.canvas.drawText("Document de test $n",35f,120f,Paint().apply{color=Color.BLACK;textSize=30f});pdf.finishPage(p)}
        file.outputStream().use{pdf.writeTo(it)};pdf.close()
        File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        val latch=java.util.concurrent.CountDownLatch(1)
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            scenario.onActivity{a->
                val field=MainActivity::class.java.getDeclaredField("detectionWorker").apply{isAccessible=true}
                (field.get(a) as java.util.concurrent.ExecutorService).execute{latch.await(20,java.util.concurrent.TimeUnit.SECONDS)}
                // ActivityScenario matches lifecycle events to its launch intent.
                val launchIntent=Intent(a.intent)
                MainActivity::class.java.getDeclaredMethod("onNewIntent",Intent::class.java).apply{isAccessible=true}.invoke(a,Intent(Intent.ACTION_VIEW,android.net.Uri.fromFile(file)))
                a.intent=launchIntent
            }
            try{
                fun awaitText(value:String){val end=System.currentTimeMillis()+7000;var ok=false;while(System.currentTimeMillis()<end){scenario.onActivity{a->ok=findText(a.window.decorView,value)};if(ok)return;Thread.sleep(50)};assertTrue("Page update must not wait for OCR: $value",ok)}
                awaitText("Détection locale en cours")
                scenario.onActivity{a->val next=findButton(a.window.decorView,"Page ›")!!;assertTrue(next.isEnabled);next.performClick()}
                awaitText("Page 2 sur 3")
                scenario.onActivity{a->findButton(a.window.decorView,"Page ›")!!.performClick()}
                awaitText("Page 3 sur 3")
                assertEquals(1L,latch.count)
                screenshot(scenario,"navigation-with-ocr-pending")
            }finally{latch.countDown()}
        }
    }
    @Test fun tilesSurviveLargeViewportSwipeAndRepeatedZoom(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        val reader=ContinuousReaderView(c)
        var requests=0
        i.runOnMainSync{
            reader.measure(View.MeasureSpec.makeMeasureSpec(1440,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));reader.layout(0,0,1440,2400)
            reader.onRequest={tile,revision->requests++;val b=Bitmap.createBitmap(kotlin.math.ceil(tile.rect.width()).toInt(),kotlin.math.ceil(tile.rect.height()).toInt(),Bitmap.Config.ARGB_8888);b.eraseColor(Color.rgb(40,80,60));reader.accept(tile,revision,b)}
            reader.setDocument(listOf(Zone(1,Box(0f,0f,1f,1f),Kind.TEXT)),600,12000)
            val out=Bitmap.createBitmap(1440,2400,Bitmap.Config.ARGB_8888)
            for(step in 0..8){reader.zoomBy(if(step%2==0)2f else .6f);repeat(2){reader.draw(Canvas(out))};val now=android.os.SystemClock.uptimeMillis();reader.onTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,700f,1900f,0));reader.onTouchEvent(MotionEvent.obtain(now,now+180,MotionEvent.ACTION_MOVE,500f,500f,0));reader.onTouchEvent(MotionEvent.obtain(now,now+220,MotionEvent.ACTION_UP,500f,500f,0));repeat(2){reader.draw(Canvas(out))};assertTrue("Source pixels must remain visible after zoom and swipe",Color.red(out.getPixel(720,1200))<100)}
            val settled=requests;repeat(3){reader.draw(Canvas(out))};assertEquals("Stationary frame must not reload itself",settled,requests)
            out.recycle();reader.clear()
        }
    }
    @Test fun textCropRetainsDescendersAndRasterBottom(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        val f=File(c.cacheDir,"crop.pdf");val pdf=android.graphics.pdf.PdfDocument();val p=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(400,600,1).create())
        p.canvas.drawColor(Color.WHITE);p.canvas.drawRect(50f,290f,150f,309f,Paint().apply{color=Color.BLACK});pdf.finishPage(p);f.outputStream().use{pdf.writeTo(it)};pdf.close()
        PdfDocument(f).use{doc->val preview=doc.preview(0);val safe=TextCoverage.safeTextBox(preview,Box(.1f,.4f,.4f,.5f));assertTrue(safe.bottom*600>=309);val tile=doc.region(0,safe,600,800)
            var lowest=-1;for(y in 0 until tile.height)for(x in 0 until tile.width)if(Color.red(tile.getPixel(x,y))<100)lowest=maxOf(lowest,y)
            assertTrue(lowest>=0);assertTrue("Whitespace must remain under ink, not crop through it",lowest<tile.height-1);preview.recycle();tile.recycle()}
    }
    @Test fun localOcrPublishesInSelectionOrderAndStopsWithoutLateChunks(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val image=Bitmap.createBitmap(900,1400,Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE)
        val canvas=Canvas(image);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=32f}
        for(line in 0..15)canvas.drawText("Une lecture locale et progressive",30f,90f+line*70,paint)
        val plan=listOf(OcrChunk(1,1,0,Box(0f,0f,1f,.4f),Kind.TEXT),OcrChunk(2,1,0,Box(0f,.4f,1f,.8f),Kind.TEXT))
        val calls=mutableListOf<Int>();val first=java.util.concurrent.atomic.AtomicLong(0);val start=System.currentTimeMillis()
        ZoneDetector(context).recognizeChunks(plan,image,true,{false}){chunk,words->assertTrue(words.isNotEmpty());if(calls.isEmpty())first.set(System.currentTimeMillis()-start);calls+=chunk.key}
        assertEquals(listOf(1,2),calls);println("First local OCR chunk: ${first.get()}ms")
        var stop=false;val cancelled=mutableListOf<Int>()
        ZoneDetector(context).recognizeChunks(plan,image,false,{stop}){chunk,_->cancelled+=chunk.key;stop=true}
        assertEquals(listOf(1),cancelled);image.recycle()
    }
    private fun findText(v:View,value:String):Boolean{if(v is TextView&&v.text.toString().contains(value))return true;if(v is ViewGroup)for(j in 0 until v.childCount)if(findText(v.getChildAt(j),value))return true;return false}
    private fun findButton(v:View,label:String):Button? {if(v is Button&&v.text.toString().startsWith(label))return v;if(v is ViewGroup)for(i in 0 until v.childCount)findButton(v.getChildAt(i),label)?.let{return it};return null}
    private fun findReader(v:View):ContinuousReaderView?{if(v is ContinuousReaderView)return v;if(v is ViewGroup)for(i in 0 until v.childCount)findReader(v.getChildAt(i))?.let{return it};return null}
    private fun findCanvas(v:View):PageCanvas?{if(v is PageCanvas)return v;if(v is ViewGroup)for(i in 0 until v.childCount)findCanvas(v.getChildAt(i))?.let{return it};return null}
    private fun screenshot(scenario:ActivityScenario<MainActivity>,name:String){
        val i=InstrumentationRegistry.getInstrumentation();i.waitForIdleSync()
        scenario.onActivity{a->
            val root=a.window.decorView
            val b=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
            root.draw(Canvas(b))
            val dir=File(a.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
            File(dir,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
            b.recycle()
            assertTrue(File(dir,"$name.png").length()>1000)
        }
    }
}
