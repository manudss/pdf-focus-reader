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
                MainActivity::class.java.getDeclaredMethod("onNewIntent",Intent::class.java).apply{isAccessible=true}.invoke(a,Intent(Intent.ACTION_VIEW,android.net.Uri.fromFile(file)))
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
    @Test fun textCropRetainsDescendersAndRasterBottom(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        val f=File(c.cacheDir,"crop.pdf");val pdf=android.graphics.pdf.PdfDocument();val p=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(400,600,1).create())
        p.canvas.drawColor(Color.WHITE);p.canvas.drawRect(50f,290f,150f,309f,Paint().apply{color=Color.BLACK});pdf.finishPage(p);f.outputStream().use{pdf.writeTo(it)};pdf.close()
        PdfDocument(f).use{doc->val preview=doc.preview(0);val safe=TextCoverage.safeTextBox(preview,Box(.1f,.4f,.4f,.5f));assertTrue(safe.bottom*600>=309);val tile=doc.region(0,safe,600,800)
            var lowest=-1;for(y in 0 until tile.height)for(x in 0 until tile.width)if(Color.red(tile.getPixel(x,y))<100)lowest=maxOf(lowest,y)
            assertTrue(lowest>=0);assertTrue("Whitespace must remain under ink, not crop through it",lowest<tile.height-1);preview.recycle();tile.recycle()}
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
