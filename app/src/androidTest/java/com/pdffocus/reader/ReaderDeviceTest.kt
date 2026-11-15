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
            scenario.onActivity{a->assertNotNull(findButton(a.window.decorView,"Vue page"));assertTrue(findCanvas(a.window.decorView)!!.reading);findButton(a.window.decorView,"Vue page")!!.performClick()}
            scenario.recreate()
            Thread.sleep(2000)
        }
    }
    private fun findButton(v:View,label:String):Button? {if(v is Button&&v.text.toString().startsWith(label))return v;if(v is ViewGroup)for(i in 0 until v.childCount)findButton(v.getChildAt(i),label)?.let{return it};return null}
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
