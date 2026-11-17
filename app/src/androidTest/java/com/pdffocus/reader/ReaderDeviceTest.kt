package com.pdffocus.reader

import android.content.Intent
import android.graphics.*
import android.view.*
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderDeviceTest {
    @Before fun keepWalkthroughOutOfUnrelatedTests(){
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("MainActivity",0).edit().putBoolean("tutorialSeen",true).commit()
    }

    @Test fun independentFrenchDictionaryLoadsAndRepairsOffline(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val spelling=FrenchSpelling(context)
        assertTrue(spelling.valid("commence"));assertTrue(spelling.valid("logements"));assertFalse(spelling.valid("comence"))
        val box=Box(0f,0f,1f,.04f)
        val words=listOf(OcrWord("comence",box,box,99f))
        assertEquals("commence",OcrConsensus.reconcile(words,OcrEvidence("Paddle","commence",box,.99f),spelling).single().text)
        val merged=listOf(OcrWord("avecun7livrepourchaquevisiteur",box,box,99f))
        val separated=OcrConsensus.reconcile(merged,OcrEvidence("Paddle","avec un 7 livre pour chaque visiteur",box,.99f),spelling)
        assertEquals("avec un 7 livre pour chaque visiteur",separated.joinToString(" "){it.text})
        assertEquals(box,separated.map{it.box}.reduce{a,b->a.union(b)})
        val fragment=OcrWord("pré-",box,box,99f)
        val following=OcrWord("parer",Box(0f,.05f,1f,.09f),Box(0f,.05f,1f,.09f),99f)
        val guarded=OcrBaseline.protectHyphenFragments(listOf(fragment),listOf(fragment.copy(text="préè-"),following),spelling)
        assertEquals("préparer",OcrEnhancer(emptyList(),spelling::valid).text(guarded))
    }
    @Test fun originalTutorialHasNinePagesAndReadableRasterAndComic(){
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val bytes=c.assets.open("examples/decouverte.pdf").use{it.readBytes()}
        assertEquals("c5428add32c827302a7195aced91354156fa621d819a6f7d083bfe1884cc015f",java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
        val f=File(c.cacheDir,"tutorial-check.pdf");f.writeBytes(bytes)
        PdfDocument(f).use{doc->
            assertEquals(9,doc.count)
            for(index in listOf(2,6,8)){
                val b=doc.preview(index);assertTrue(b.width>500&&b.height>800)
                File(File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()},"tutorial-page-${index+1}.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
                if(index==8){
                    val parts=listOf(OcrChunk(1,1,0,Box(0f,0f,1f,1f),Kind.TEXT));var text=""
                    ZoneDetector(c).recognizeChunks(parts,b,false,{false}){_,words->text=words.joinToString(" "){it.text}}
                    assertTrue("Raster tutorial page must produce offline OCR",text.length>80)
                }
                if(index==6){
                    val case=Zone(1,Box(.5f,.15f,.93f,.36f),Kind.IMAGE,manual=true)
                    val original=doc.readingRegion(index,case.box,600,600,false)
                    val unchanged=doc.readingRegion(index,case.box,600,600,case.kind==Kind.TEXT)
                    assertTrue("Image blocks must preserve original comic pixels",original.sameAs(unchanged));original.recycle();unchanged.recycle()
                    assertEquals(listOf(3,1,2),listOf(3,1,2).fold(emptyList<Int>()){ids,id->ReadingLayout.toggle(ids,id)})
                };b.recycle()
            }
        };f.delete()
    }
    @Test fun framedComicProposesSixWholeImagePanelsWithoutOcrFragments(){
        val instrumentation=InstrumentationRegistry.getInstrumentation();val c=instrumentation.targetContext
        File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            scenario.onActivity{a->findButton(a.window.decorView,"Essayer un exemple")!!.performClick()}
            var ready=false;val end=System.currentTimeMillis()+90000
            while(System.currentTimeMillis()<end){scenario.onActivity{a->ready=MainActivity::class.java.getDeclaredField("pageReady").apply{isAccessible=true}.getBoolean(a)};if(ready)break;Thread.sleep(100)};assertTrue(ready)
            scenario.onActivity{a->MainActivity::class.java.getDeclaredMethod("loadPage",Int::class.javaPrimitiveType,Boolean::class.javaPrimitiveType).apply{isAccessible=true}.invoke(a,6,false)}
            ready=false;val finish=System.currentTimeMillis()+90000
            while(System.currentTimeMillis()<finish){scenario.onActivity{a->ready=MainActivity::class.java.getDeclaredField("pageReady").apply{isAccessible=true}.getBoolean(a)&&findCanvas(a.window.decorView)!!.zones.size==6};if(ready)break;Thread.sleep(100)}
            assertTrue("The full detection path must propose six comic panels",ready)
            clearAutoProposal(scenario)
            scenario.onActivity{a->val page=findCanvas(a.window.decorView)!!;assertTrue(page.zones.all{it.kind==Kind.IMAGE});assertTrue(page.zones.all{it.box.height>.25f});assertTrue(page.selection.isEmpty());page.zones.forEach{page.onZoneTap!!.invoke(it)};assertEquals((1..6).toList(),page.selection)}
            screenshot(scenario,"comic-six-panels")
            scenario.onActivity{a->findButton(a.window.decorView,"Lire (")!!.performClick();findButton(a.window.decorView,"Original")!!.performClick()}
            Thread.sleep(700);screenshot(scenario,"comic-original-reader")
        }
    }
    @Test fun thinInsetComicFramesKeepCaptionsAndDrawingsTogether(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        val pdf=android.graphics.pdf.PdfDocument();val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(600,840,1).create())
        val cv=page.canvas;cv.drawColor(Color.WHITE);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        val expected=(0..2).flatMap{row->(0..1).map{col->Box(.2f+col*.3f,.2f+row*.19f,.49f+col*.3f,.38f+row*.19f)}}
        expected.forEachIndexed{index,b->
            val r=RectF(b.left*600,b.top*840,b.right*600,b.bottom*840)
            paint.color=Color.rgb(225-index*5,219,190+index*8);paint.style=Paint.Style.FILL;cv.drawRect(r,paint)
            paint.color=Color.rgb(140,140,140);paint.style=Paint.Style.STROKE;paint.strokeWidth=.8f;cv.drawRect(r,paint)
            paint.style=Paint.Style.FILL;paint.color=Color.DKGRAY;paint.textSize=10f;cv.drawText("Etape ${index+1} : observer",r.left+6,r.top+14,paint)
            cv.drawText("Une histoire inventee",r.left+6,r.top+27,paint)
            paint.color=Color.rgb(68,99,81);cv.drawCircle(r.centerX(),r.centerY()+4,19f,paint);cv.drawRect(r.centerX()-22,r.centerY()+22,r.centerX()+22,r.bottom-8,paint)
        }
        pdf.finishPage(page);File(c.filesDir,"current.pdf").outputStream().use{pdf.writeTo(it)};pdf.close()
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            var ready=false;val end=System.currentTimeMillis()+90000
            while(System.currentTimeMillis()<end){scenario.onActivity{a->ready=MainActivity::class.java.getDeclaredField("pageReady").apply{isAccessible=true}.getBoolean(a)};if(ready)break;Thread.sleep(100)}
            assertTrue("Synthetic comic detection must complete",ready)
            clearAutoProposal(scenario)
            scenario.onActivity{a->val view=findCanvas(a.window.decorView)!!
                assertEquals("Six panels, not caption fragments",6,view.zones.size);assertTrue(view.zones.all{it.kind==Kind.IMAGE});assertTrue(view.selection.isEmpty())
                for(b in expected)assertTrue("Caption and drawing must be in the same whole panel",view.zones.any{it.box.intersect(b)/b.area>.98f})
                view.zones.reversed().forEach{view.onZoneTap!!.invoke(it)};assertEquals((6 downTo 1).toList(),view.selection)
            }
            screenshot(scenario,"comic-thin-inset-six-panels")
            scenario.onActivity{a->findButton(a.window.decorView,"Lire (")!!.performClick();findButton(a.window.decorView,"Original")!!.performClick()}
            Thread.sleep(900);screenshot(scenario,"comic-thin-inset-reader")
        }
    }
    @Test fun assistedGroupsPreviewSubsetCancelApplyUndoAndFaithfulReading(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        c.assets.open("examples/decouverte.pdf").use{input->File(c.filesDir,"current.pdf").outputStream().use{input.copyTo(it)}}
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            fun field(a:MainActivity,n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.get(a)
            fun call(a:MainActivity,n:String,vararg args:Any)=MainActivity::class.java.declaredMethods.first{it.name==n}.apply{isAccessible=true}.invoke(a,*args)
            fun awaitReady(){var ready=false;val end=System.currentTimeMillis()+90000;while(System.currentTimeMillis()<end){scenario.onActivity{a->ready=field(a,"pageReady")as Boolean};if(ready)break;Thread.sleep(100)};assertTrue(ready)}
            fun captureDialog(name:String){i.waitForIdleSync();scenario.onActivity{a->val dialog=field(a,"groupDialog")as android.app.Dialog;val root=dialog.window!!.decorView;val b=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(b));File(File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()},"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
            fun checks(v:View):List<CheckBox>{if(v is CheckBox)return listOf(v);if(v is ViewGroup)return (0 until v.childCount).flatMap{checks(v.getChildAt(it))};return emptyList()}
            awaitReady();scenario.onActivity{a->call(a,"loadPage",1,false)};awaitReady()
            clearAutoProposal(scenario)
            var original=listOf<Zone>();var originalOrder=listOf<Int>();var groups=listOf<AutoGrouping.Group>()
            scenario.onActivity{a->val canvas=findCanvas(a.window.decorView)!!;canvas.zones.forEach{canvas.onZoneTap!!.invoke(it)};original=canvas.zones;originalOrder=canvas.selection;groups=AutoGrouping.propose(original);File(File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()},"tutorial-proposals.txt").writeText(original.joinToString("\n")+"\nGROUPS\n"+groups.joinToString("\n"));assertTrue("Unchanged tutorial must group header and its four title/body pairs: $groups",groups.size>=5);call(a,"showAutoGroups")}
            captureDialog("grouping-tutorial-preview")
            scenario.onActivity{a->val d=field(a,"groupDialog")as android.app.Dialog;val boxes=checks(d.window!!.decorView);assertEquals(groups.size,boxes.size);if(boxes.size>1){boxes.last().performClick();assertFalse(boxes.last().isChecked)};findButton(d.window!!.decorView,"Annuler")!!.performClick();assertEquals(original,findCanvas(a.window.decorView)!!.zones);assertEquals(originalOrder,findCanvas(a.window.decorView)!!.selection);call(a,"showAutoGroups")}
            scenario.onActivity{a->val d=field(a,"groupDialog")as android.app.Dialog;val boxes=checks(d.window!!.decorView);boxes.drop(1).forEach{it.performClick()};findButton(d.window!!.decorView,"Appliquer")!!.performClick();assertEquals(original.size-groups.first().ids.size+1,findCanvas(a.window.decorView)!!.zones.size);call(a,"undoAutoGroups");assertEquals(original,findCanvas(a.window.decorView)!!.zones);assertEquals(originalOrder,findCanvas(a.window.decorView)!!.selection);call(a,"showAutoGroups");val next=field(a,"groupDialog")as android.app.Dialog;findButton(next.window!!.decorView,"Appliquer")!!.performClick()}
            screenshot(scenario,"grouping-tutorial-applied")
            scenario.onActivity{a->val bs=ContinuousLayout.bands(ReadingLayout.ordered(findCanvas(a.window.decorView)!!.zones,findCanvas(a.window.decorView)!!.selection));assertTrue("Whole groups must not leave partial text-row holes",bs.filter{it.kind==Kind.TEXT}.all{band->val selected=findCanvas(a.window.decorView)!!.zones.first{it.id==band.zoneId}.box;band.pieces.all{it.width>=selected.width-.02f}})}
            scenario.onActivity{a->findButton(a.window.decorView,"Lire (")!!.performClick();findButton(a.window.decorView,"Original")!!.performClick()};Thread.sleep(1300);screenshot(scenario,"faithful-tutorial-original")
            scenario.onActivity{a->findButton(a.window.decorView,"Nettoyé")!!.performClick()};Thread.sleep(1300);screenshot(scenario,"faithful-tutorial-cleaned")
            scenario.onActivity{a->findButton(a.window.decorView,"Texte OCR")!!.performClick()};Thread.sleep(12000);screenshot(scenario,"harmonized-tutorial-ocr")
            scenario.onActivity{a->call(a,"exitReader");call(a,"loadPage",0,false)};awaitReady()
            clearAutoProposal(scenario)
            scenario.onActivity{a->val canvas=findCanvas(a.window.decorView)!!;canvas.zones.forEach{canvas.onZoneTap!!.invoke(it)};if(AutoGrouping.propose(canvas.zones).isNotEmpty()){call(a,"showAutoGroups");val d=field(a,"groupDialog")as android.app.Dialog;findButton(d.window!!.decorView,"Appliquer")!!.performClick()};findButton(a.window.decorView,"Lire (")!!.performClick();findButton(a.window.decorView,"Original")!!.performClick()};Thread.sleep(1500);screenshot(scenario,"faithful-cover-original")
        }
    }
    @Test fun faithfulReaderUsesLocalBackgroundAndCommonHeadingScale(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        i.runOnMainSync{
            val bg=Color.rgb(247,241,223);val reader=ContinuousReaderView(c)
            reader.measure(View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));reader.layout(0,0,700,1000)
            val zones=listOf(Zone(1,Box(.1f,.1f,.25f,.14f),Kind.TEXT),Zone(2,Box(.1f,.15f,.8f,.5f),Kind.TEXT))
            reader.onRequest={tile,revision->val b=Bitmap.createBitmap(kotlin.math.ceil(tile.rect.width()).toInt(),kotlin.math.ceil(tile.rect.height()).toInt(),Bitmap.Config.ARGB_8888);b.eraseColor(bg);reader.accept(tile,revision,b)}
            reader.setDocument(zones,700,1000,backgrounds=mapOf(1 to bg,2 to bg))
            val b=Bitmap.createBitmap(700,1000,Bitmap.Config.ARGB_8888);repeat(3){reader.draw(Canvas(b))}
            assertEquals("Margins around heading must use the source paper",bg,b.getPixel(2,15))
            val tiles=ContinuousReaderView::class.java.getDeclaredField("tiles").apply{isAccessible=true}.get(reader)as List<*>
            val first=tiles.first()as ContinuousReaderView.Tile;assertTrue("Heading must not be blown up to fill the screen",first.rect.width()<350)
            reader.clear();b.recycle()
        }
    }
    @Test fun continuousReaderKeepsSharedScaleForColumnBottomFragment(){
        // Fictional magazine layout: a column and a short overlapping paragraph at its bottom,
        // both with the same source text size. The fragment must render at the body scale,
        // not shrunk by a width rule (real bug: 4.7x smaller than the surrounding text).
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        i.runOnMainSync{
            val reader=ContinuousReaderView(c)
            reader.measure(View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));reader.layout(0,0,700,1000)
            fun lines(top:Float,bottom:Float,step:Float):List<Float>{val out=mutableListOf<Float>();var y=top+step;while(y<=bottom+.0001f){out+=y;y+=step};return out}
            val body=Zone(1,Box(.41f,.50f,.59f,.70f),Kind.TEXT,lines(.50f,.695f,.0105f))
            val fragment=Zone(2,Box(.41f,.675f,.59f,.75f),Kind.TEXT,lines(.675f,.748f,.0105f))
            reader.onRequest={tile,revision->reader.skipRequest(tile,revision)}
            reader.setDocument(listOf(body,fragment),700,1400)
            val tiles=ContinuousReaderView::class.java.getDeclaredField("tiles").apply{isAccessible=true}.get(reader)as List<ContinuousReaderView.Tile>
            val bodyTile=tiles.first{it.chunkKey==1};val fragTile=tiles.first{it.chunkKey==2}
            val bodyScale=bodyTile.rect.height()/((bodyTile.source.bottom-bodyTile.source.top)*1400)
            val fragScale=fragTile.rect.height()/((fragTile.source.bottom-fragTile.source.top)*1400)
            assertEquals("A short column-bottom fragment keeps the body text size",bodyScale,fragScale,bodyScale*.05f)
            reader.clear()
        }
    }
    @Test fun continuousReaderRendersBottomFragmentAtBodySizeVisually(){
        // Fictional page raster with real text: a prose column and a short overlapping
        // fragment at its bottom, ALL drawn at the same pixel size. Real ContinuousReaderView
        // layout + real tile rendering: the fragment must come out at the body text size.
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        i.runOnMainSync{
            val src=Bitmap.createBitmap(700,1400,Bitmap.Config.ARGB_8888)
            val cv=Canvas(src);cv.drawColor(Color.WHITE)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=13f}
            var y=.50f*1400+13f
            var n=0
            val words=listOf("le","conseil","invente","une","histoire","complete","pour","ce","test","visuel","du","parcours")
            while(y<.670f*1400){cv.drawText("${words[n%12]} ${words[(n+3)%12]} ${words[(n+5)%12]} ${words[(n+7)%12]} du corps",.41f*700,y,paint);y+=.0105f*1400;n++}
            var fy=.701f*1400+13f
            while(fy<.748f*1400){cv.drawText("${words[n%12]} ${words[(n+4)%12]} du fragment",.41f*700,fy,paint);fy+=.0105f*1400;n++}
            fun lines(top:Float,bottom:Float,step:Float):List<Float>{val out=mutableListOf<Float>();var yy=top+step;while(yy<=bottom+.0001f){out+=yy;yy+=step};return out}
            val body=Zone(1,Box(.41f,.50f,.59f,.70f),Kind.TEXT,lines(.50f,.695f,.0105f))
            val fragment=Zone(2,Box(.41f,.675f,.59f,.75f),Kind.TEXT,lines(.675f,.748f,.0105f))
            val reader=ContinuousReaderView(c)
            reader.measure(View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));reader.layout(0,0,700,1000)
            reader.onRequest={tile,revision->
                val scaled=Bitmap.createBitmap(maxOf(1,kotlin.math.ceil(tile.rect.width()).toInt()),maxOf(1,kotlin.math.ceil(tile.rect.height()).toInt()),Bitmap.Config.ARGB_8888)
                val raster=Canvas(scaled);raster.drawColor(Color.WHITE)
                // Preserve exact floating source bounds, as PdfRenderer's region matrix
                // does. Integer crop+resize rounds the seam independently on each tile.
                val matrix=android.graphics.Matrix().apply { setRectToRect(RectF(tile.source.left*700,tile.source.top*1400,tile.source.right*700,tile.source.bottom*1400),RectF(0f,0f,scaled.width.toFloat(),scaled.height.toFloat()),android.graphics.Matrix.ScaleToFit.FILL) }
                raster.drawBitmap(src,matrix,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                reader.accept(tile,revision,scaled)
            }
            reader.setDocument(listOf(body,fragment),700,1400)
            val tiles=ContinuousReaderView::class.java.getDeclaredField("tiles").apply{isAccessible=true}.get(reader)as List<ContinuousReaderView.Tile>
            val fragTile=tiles.first{it.chunkKey==2}
            reader.setDocument(listOf(body,fragment),700,1400,(fragTile.rect.top-60).toInt().coerceAtLeast(0))
            val shot=Bitmap.createBitmap(700,1000,Bitmap.Config.ARGB_8888)
            val shotCanvas=Canvas(shot);shotCanvas.drawColor(Color.WHITE)
            // The first draw queues tile requests; the second displays their accepted rasters.
            repeat(2){reader.draw(shotCanvas)}
            val dir=File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
            File(dir,"continuous-fragment-body-size.png").outputStream().use{shot.compress(Bitmap.CompressFormat.PNG,100,it)}
            // The rendered fragment rows must carry real glyph pixels (not a blank or stub tile).
            fun glyphRows(bitmap:Bitmap):Int{
                var rows=0
                for(yy in 0 until bitmap.height){var dark=0;for(xx in 0 until bitmap.width step 7){if(Color.luminance(bitmap.getPixel(xx,yy))<.45)dark++};if(dark>=4)rows++}
                return rows
            }
            val tiles2=ContinuousReaderView::class.java.getDeclaredField("tiles").apply{isAccessible=true}.get(reader)as List<ContinuousReaderView.Tile>
            val frag=tiles2.first{it.chunkKey==2};val bod=tiles2.first{it.chunkKey==1}
            val top=(frag.rect.top-ContinuousReaderView::class.java.getDeclaredField("offset").apply{isAccessible=true}.get(reader)as Float).toInt()
            val bandTop=top.coerceIn(0,shot.height-1)
            val band=Bitmap.createBitmap(shot,frag.rect.left.toInt().coerceAtLeast(0),bandTop,minOf(frag.rect.width(),shot.width.toFloat()).toInt().coerceAtLeast(20),minOf(frag.rect.height().toInt(),shot.height-bandTop).coerceAtLeast(1))
            assertTrue("Fragment band must show rendered text rows",glyphRows(band)>=2)
            val bodyScale=bod.rect.height()/((bod.source.bottom-bod.source.top)*1400)
            val fragScale=frag.rect.height()/((frag.source.bottom-frag.source.top)*1400)
            assertEquals("Fragment renders at the body scale",bodyScale,fragScale,bodyScale*.05f)
            band.recycle();shot.recycle();src.recycle();reader.clear()
        }
    }

    @Test fun continuousReaderPreservesCrossingGlyphAtOverlappingBlockSeam(){
        // Fictional page raster with real text: a prose column and a short overlapping
        // fragment at its bottom, ALL drawn at the same pixel size. Real ContinuousReaderView
        // layout + real tile rendering: the fragment must come out at the body text size.
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        i.runOnMainSync{
            val src=Bitmap.createBitmap(700,1400,Bitmap.Config.ARGB_8888)
            val cv=Canvas(src);cv.drawColor(Color.WHITE)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=13f}
            var y=.50f*1400+13f
            var n=0
            val words=listOf("le","conseil","invente","une","histoire","complete","pour","ce","test","visuel","du","parcours")
            while(y<.670f*1400){cv.drawText("${words[n%12]} ${words[(n+3)%12]} ${words[(n+5)%12]} ${words[(n+7)%12]} du corps",.41f*700,y,paint);y+=.0105f*1400;n++}
            var fy=.675f*1400+13f
            while(fy<.748f*1400){cv.drawText("${words[n%12]} ${words[(n+4)%12]} du fragment",.41f*700,fy,paint);fy+=.0105f*1400;n++}
            fun lines(top:Float,bottom:Float,step:Float):List<Float>{val out=mutableListOf<Float>();var yy=top+step;while(yy<=bottom+.0001f){out+=yy;yy+=step};return out}
            val body=Zone(1,Box(.41f,.50f,.59f,.70f),Kind.TEXT,lines(.50f,.695f,.0105f))
            val fragment=Zone(2,Box(.41f,.675f,.59f,.75f),Kind.TEXT,lines(.675f,.748f,.0105f))
            val reader=ContinuousReaderView(c)
            reader.measure(View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));reader.layout(0,0,700,1000)
            reader.onRequest={tile,revision->
                val scaled=Bitmap.createBitmap(maxOf(1,kotlin.math.ceil(tile.rect.width()).toInt()),maxOf(1,kotlin.math.ceil(tile.rect.height()).toInt()),Bitmap.Config.ARGB_8888)
                val raster=Canvas(scaled);raster.drawColor(Color.WHITE)
                // Preserve exact floating source bounds, as PdfRenderer's region matrix
                // does. Integer crop+resize rounds the seam independently on each tile.
                val matrix=android.graphics.Matrix().apply { setRectToRect(RectF(tile.source.left*700,tile.source.top*1400,tile.source.right*700,tile.source.bottom*1400),RectF(0f,0f,scaled.width.toFloat(),scaled.height.toFloat()),android.graphics.Matrix.ScaleToFit.FILL) }
                raster.drawBitmap(src,matrix,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                reader.accept(tile,revision,scaled)
            }
            reader.setDocument(listOf(body,fragment),700,1400)
            val tiles=ContinuousReaderView::class.java.getDeclaredField("tiles").apply{isAccessible=true}.get(reader)as List<ContinuousReaderView.Tile>
            val fragTile=tiles.first{it.chunkKey==2}
            reader.setDocument(listOf(body,fragment),700,1400,(fragTile.rect.top-60).toInt().coerceAtLeast(0))
            val shot=Bitmap.createBitmap(700,1000,Bitmap.Config.ARGB_8888)
            val shotCanvas=Canvas(shot);shotCanvas.drawColor(Color.WHITE)
            // The first draw queues tile requests; the second displays their accepted rasters.
            repeat(2){reader.draw(shotCanvas)}
            val dir=File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()}
            File(dir,"continuous-overlap-seam.png").outputStream().use{shot.compress(Bitmap.CompressFormat.PNG,100,it)}
            // The rendered fragment rows must carry real glyph pixels (not a blank or stub tile).
            fun glyphRows(bitmap:Bitmap):Int{
                var rows=0
                for(yy in 0 until bitmap.height){var dark=0;for(xx in 0 until bitmap.width step 7){if(Color.luminance(bitmap.getPixel(xx,yy))<.45)dark++};if(dark>=4)rows++}
                return rows
            }
            val tiles2=ContinuousReaderView::class.java.getDeclaredField("tiles").apply{isAccessible=true}.get(reader)as List<ContinuousReaderView.Tile>
            val frag=tiles2.first{it.chunkKey==2};val bod=tiles2.first{it.chunkKey==1}
            val lastBodyBottom=tiles2.filter{it.chunkKey==1}.maxOf{it.rect.bottom}
            val firstFragmentTop=tiles2.filter{it.chunkKey==2}.minOf{it.rect.top}
            assertEquals("Overlap seam must add no white spacer through source glyphs",lastBodyBottom,firstFragmentTop,.01f)
            val top=(frag.rect.top-ContinuousReaderView::class.java.getDeclaredField("offset").apply{isAccessible=true}.get(reader)as Float).toInt()
            val bandTop=top.coerceIn(0,shot.height-1)
            val band=Bitmap.createBitmap(shot,frag.rect.left.toInt().coerceAtLeast(0),bandTop,minOf(frag.rect.width(),shot.width.toFloat()).toInt().coerceAtLeast(20),minOf(frag.rect.height().toInt(),shot.height-bandTop).coerceAtLeast(1))
            assertTrue("Fragment band must show rendered text rows",glyphRows(band)>=2)
            val bodyScale=bod.rect.height()/((bod.source.bottom-bod.source.top)*1400)
            val fragScale=frag.rect.height()/((frag.source.bottom-frag.source.top)*1400)
            assertEquals("Fragment renders at the body scale",bodyScale,fragScale,bodyScale*.05f)
            band.recycle();shot.recycle();src.recycle();reader.clear()
        }
    }

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
            scenario.onActivity{a->MainActivity::class.java.getDeclaredMethod("loadPage",Int::class.javaPrimitiveType,Boolean::class.javaPrimitiveType).apply{isAccessible=true}.invoke(a,2,false)}
            val pageEnd=System.currentTimeMillis()+90000;ready=false
            while(System.currentTimeMillis()<pageEnd){scenario.onActivity{a->ready=MainActivity::class.java.getDeclaredField("pageReady").apply{isAccessible=true}.getBoolean(a)&&findCanvas(a.window.decorView)!!.zones.isNotEmpty()};if(ready)break;Thread.sleep(100)}
            assertTrue("Tutorial column page must finish loading",ready)
            screenshot(scenario,"detected")
            scenario.onActivity{a->
                val page=findCanvas(a.window.decorView)!!
                // Follow the tutorial's manual framing exercise, not automatic column guesses.
                val selected=listOf(Zone(101,Box(.085f,.265f,.47f,.575f),Kind.TEXT,manual=true),Zone(102,Box(.52f,.265f,.91f,.48f),Kind.TEXT,manual=true),Zone(103,Box(.52f,.50f,.91f,.675f),Kind.TEXT,manual=true))
                MainActivity::class.java.getDeclaredField("zones").apply{isAccessible=true}.set(a,selected)
                MainActivity::class.java.getDeclaredField("order").apply{isAccessible=true}.set(a,emptyList<Int>())
                MainActivity::class.java.getDeclaredMethod("sync",Boolean::class.javaPrimitiveType).apply{isAccessible=true}.invoke(a,false)
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
            scenario.onActivity{a->findButton(a.window.decorView,"Texte OCR")!!.performClick()}
            val rawEnd=System.currentTimeMillis()+90000;var rawReady=false
            while(System.currentTimeMillis()<rawEnd){scenario.onActivity{a->
                val reader=findReader(a.window.decorView)!!
                val items=ContinuousReaderView::class.java.getDeclaredField("textItems").apply{isAccessible=true}.get(reader) as List<*>
                rawReady=items.isNotEmpty()
            };if(rawReady)break;Thread.sleep(100)}
            assertTrue("OCR must publish actual text before checking its layout",rawReady)
            scenario.onActivity{a->
                findButton(a.window.decorView,"Réglages")!!.performClick()
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
            scenario.onActivity{a->assertFalse("Improved OCR must not fail silently",findText(a.window.decorView,"OCR partiel"));val f=MainActivity::class.java.getDeclaredField("improvedChunks").apply{isAccessible=true};assertTrue("Improved chunks must actually be produced",(f.get(a) as Map<*,*>).isNotEmpty());val evidence=MainActivity::class.java.getDeclaredField("secondChunks").apply{isAccessible=true}.get(a) as Map<Int,List<OcrEvidence>>;assertTrue("The reading flow must really run the second recognizer",evidence.values.flatten().any{it.engine.startsWith("PaddleOCR")});assertNotNull(findButton(a.window.decorView,"OCR amélioré ✓"));findButton(a.window.decorView,"OCR brut")!!.performClick();assertNotNull(findButton(a.window.decorView,"OCR brut ✓"))}

            scenario.onActivity{a->findButton(a.window.decorView,"Original")!!.performClick()}
            Thread.sleep(500)
            scenario.onActivity{a->val reader=findReader(a.window.decorView)!!;val before=reader.currentPosition();val now=android.os.SystemClock.uptimeMillis();val start=if(before>0)150f else 600f;val end=if(before>0)600f else 150f;reader.onTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,200f,start,0));reader.onTouchEvent(MotionEvent.obtain(now,now+100,MotionEvent.ACTION_MOVE,200f,end,0));reader.onTouchEvent(MotionEvent.obtain(now,now+150,MotionEvent.ACTION_UP,200f,end,0));assertNotEquals("Swipe must change continuous stream position",before,reader.currentPosition())}
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
                scenario.onActivity{a->val next=findButton(a.window.decorView,"Suivante")!!;assertTrue(next.isEnabled);next.performClick()}
                awaitText("Page 2 sur 3")
                scenario.onActivity{a->findButton(a.window.decorView,"Suivante")!!.performClick()}
                awaitText("Page 3 sur 3")
                assertEquals(1L,latch.count)
                screenshot(scenario,"navigation-with-ocr-pending")
            }finally{latch.countDown()}
        }
    }
    @Test fun tilesSurviveLargeViewportSwipeAndRepeatedZoom(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        var requests=0
        i.runOnMainSync{
            val reader=ContinuousReaderView(c)
            reader.measure(View.MeasureSpec.makeMeasureSpec(1440,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));reader.layout(0,0,1440,2400)
            reader.onRequest={tile,revision->requests++;val b=Bitmap.createBitmap(kotlin.math.ceil(tile.rect.width()).toInt(),kotlin.math.ceil(tile.rect.height()).toInt(),Bitmap.Config.ARGB_8888);b.eraseColor(Color.rgb(40,80,60));reader.accept(tile,revision,b)}
            reader.setDocument(listOf(Zone(1,Box(0f,0f,1f,1f),Kind.TEXT)),600,12000)
            val out=Bitmap.createBitmap(1440,2400,Bitmap.Config.ARGB_8888)
            for(step in 0..8){reader.zoomBy(if(step%2==0)2f else .6f);repeat(2){reader.draw(Canvas(out))};val now=android.os.SystemClock.uptimeMillis();reader.onTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,700f,1900f,0));reader.onTouchEvent(MotionEvent.obtain(now,now+180,MotionEvent.ACTION_MOVE,500f,500f,0));reader.onTouchEvent(MotionEvent.obtain(now,now+220,MotionEvent.ACTION_UP,500f,500f,0));repeat(2){reader.draw(Canvas(out))};assertTrue("Source pixels must remain visible after zoom and swipe",Color.red(out.getPixel(720,1200))<100)}
            val settled=requests;repeat(3){reader.draw(Canvas(out))};assertEquals("Stationary frame must not reload itself",settled,requests)
            out.recycle();reader.clear()
        }
    }
    @Test fun readingPinchZoomPreviewsLiveAndRebuildsOnce(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        i.runOnMainSync{
            val reader=ContinuousReaderView(c)
            reader.measure(View.MeasureSpec.makeMeasureSpec(1440,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));reader.layout(0,0,1440,2400)
            // Landmarks drawn from zone coordinates, not tile ids: stripes and "repere N" labels
            // persist when the commit re-tiles the document, so the same content can be tracked
            // across the preview and the rebuild.
            reader.onRequest={tile,revision->
                val w=kotlin.math.ceil(tile.rect.width()).toInt();val h=kotlin.math.ceil(tile.rect.height()).toInt()
                val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val cv=Canvas(b)
                val span=(tile.source.bottom-tile.source.top).coerceAtLeast(.0001f)
                for(stripe in 0..47){
                    val f0=stripe/48f;val f1=(stripe+1)/48f
                    if(f1<tile.source.top||f0>tile.source.bottom)continue
                    val y0=((maxOf(f0,tile.source.top)-tile.source.top)/span)*h;val y1=((minOf(f1,tile.source.bottom)-tile.source.top)/span)*h
                    val band=Paint().apply{color=if(stripe%2==0)Color.rgb(40,80,60)else Color.rgb(226,239,231)}
                    cv.drawRect(0f,y0,w.toFloat(),y1,band)
                    if(stripe==0)continue
                    // Labels scale with the zone, not with the tile bitmap: a fixed zone-fraction
                    // height mapped into this tile, drawn in every tile it overlaps (the canvas
                    // clips), so glyphs survive re-tiling like real rasterized text.
                    val lp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(37,31,33);textSize=(.004f/span)*h}
                    val ly=((f0+.004f-tile.source.top)/span)*h
                    cv.drawText("repere $stripe",((.05f-tile.source.left)/(tile.source.right-tile.source.left))*w,ly,lp)
                    cv.drawText("repere $stripe",((.5f-tile.source.left)/(tile.source.right-tile.source.left))*w,ly,lp)
                }
                reader.accept(tile,revision,b)
            }
            reader.setDocument(listOf(Zone(1,Box(0f,0f,1f,1f),Kind.TEXT)),600,12000)
            val out=Bitmap.createBitmap(1440,2400,Bitmap.Config.ARGB_8888)
            repeat(3){reader.draw(Canvas(out))}
            fun field(n:String)=ContinuousReaderView::class.java.getDeclaredField(n).apply{isAccessible=true}
            // Stripe scan away from the label columns: parity of the red channel.
            fun light(x:Int,y:Int)=Color.red(out.getPixel(x,y))>128
            fun stripeLight(x:Int,y:Int):Boolean{var l=0;for(dx in -2..2)for(dy in -2..2)if(light(x+dx,y+dy))l++;return l>=13}
            fun boundaries(x:Int):List<Float>{val r=mutableListOf<Float>();var prev=light(x,0);for(y in 1 until out.height){val p=light(x,y);if(p!=prev){r+=y-.5f;prev=p}};return r}
            val beforeBounds=boundaries(650)
            assertTrue("Fixture must show several landmarks",beforeBounds.size>=2)
            // Dark text pixels: red and green both low (excludes the green stripe background).
            fun labelBox(boundaryY:Float):Pair<Float,Float>?{
                var top=-1f;var bottom=-1f
                for(y in boundaryY.toInt()+4 until minOf(out.height,boundaryY.toInt()+300)){
                    var dark=0
                    for(x in 60 until minOf(out.width,1380)){val px=out.getPixel(x,y);if(Color.red(px)<100&&Color.green(px)<60)dark++}
                    if(dark>=5){if(top<0f)top=y.toFloat();bottom=y.toFloat()}
                }
                return if(top<0f)null else top to bottom
            }
            // Anchor the gesture focus at the centre of the stripe around mid-viewport.
            val lo=beforeBounds.lastOrNull{it<=1400f}?:0f;val hi=beforeBounds.firstOrNull{it>1400f}?:2400f
            val fy=(lo+hi)/2
            val beforeLabel=labelBox(lo)
            assertNotNull("The stripe boundary above the focus must carry its label",beforeLabel)
            val beforeLight=stripeLight(720,fy.toInt())
            val before=field("revision").getInt(reader)
            val down=android.os.SystemClock.uptimeMillis()
            fun props(id:Int)=MotionEvent.PointerProperties().apply{this.id=id;toolType=MotionEvent.TOOL_TYPE_FINGER}
            fun coords(x:Float,y:Float)=MotionEvent.PointerCoords().apply{this.x=x;this.y=y}
            fun send(action:Int,count:Int,time:Long,vararg points:Pair<Float,Float>){
                val pp=(0 until count).map{props(it)}.toTypedArray();val pc=points.map{coords(it.first,it.second)}.toTypedArray()
                val e=MotionEvent.obtain(down,time,action,count,pp,pc,0,0,1f,1f,0,0,0,0);reader.onTouchEvent(e);e.recycle()
            }
            send(MotionEvent.ACTION_DOWN,1,down,720f to fy)
            send(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),2,down+40,470f to fy,970f to fy) // span 500px, above the minimum scaling span (~27mm)
            // Span grows across several frames: the old code rebuilt the whole layout per frame.
            var span=500f
            // The detector anchors its baseline on the first move, so 8 frames are
            // needed for the accumulated factor to pass the 2x clamp.
            for(step in 1..8){
                span+=100f
                send(MotionEvent.ACTION_MOVE,2,down+40+step*25,720f-span/2 to fy,720f+span/2 to fy)
                assertEquals("Pinch frame $step must not rebuild tiles, layouts or the bitmap cache",before,field("revision").getInt(reader))
                assertTrue("Pinch frame $step must be in live preview",field("pinchLive").getBoolean(reader))
            }
            assertEquals("Magnification stays put during the live preview",1f,field("magnification").getFloat(reader),0.001f)
            repeat(2){reader.draw(Canvas(out))}
            assertEquals("The landmark under the focus must stay put during the preview",beforeLight,stripeLight(720,fy.toInt()))
            File(File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()},"reading-pinch-preview.png").outputStream().use{out.compress(Bitmap.CompressFormat.PNG,100,it)}
            send(MotionEvent.ACTION_POINTER_UP or (1 shl 8),2,down+400,20f to fy,1420f to fy)
            send(MotionEvent.ACTION_UP,1,down+440,20f to fy)
            assertFalse("Preview must end with the gesture",field("pinchLive").getBoolean(reader))
            assertEquals("The whole gesture commits with exactly one rebuild",before+1,field("revision").getInt(reader))
            assertEquals("Commit clamps the accumulated factor at 2x",2f,field("magnification").getFloat(reader),0.001f)
            repeat(3){reader.draw(Canvas(out))}
            // Evidence before assertions: a failure must still ship the post-commit frame.
            File(File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()},"reading-pinch-zoomed.png").outputStream().use{out.compress(Bitmap.CompressFormat.PNG,100,it)}
            // Content continuity across the commit: the same stripes, twice as large, scaled
            // around the focus - the landmark under the focus must still be under the focus.
            assertEquals("The landmark under the focus must survive the commit",beforeLight,stripeLight(720,fy.toInt()))
            val afterBounds=boundaries(650)
            for(b in beforeBounds){
                val expected=fy+2f*(b-fy)
                if(expected<0f||expected>=out.height)continue
                assertTrue("Landmark at $b must reappear near ${expected.toInt()} after the 2x commit (found $afterBounds)",afterBounds.any{kotlin.math.abs(it-expected)<=14f})
            }
            if(beforeBounds.size>=2&&afterBounds.size>=2){
                val p0=beforeBounds[1]-beforeBounds[0];val p1=afterBounds[1]-afterBounds[0]
                assertTrue("Stripe scale must double at commit ($p0 -> $p1)",p1>p0*1.7f&&p1<p0*2.3f)
            }
            // Glyph-level continuity: the same label, twice as tall, at the doubled offset below
            // its boundary, and not clipped by the new tile grid (a clipped label collapses).
            val lo1=fy+2f*(lo-fy)
            val afterLabel=labelBox(lo1)
            assertNotNull("The same label must survive the commit",afterLabel)
            val h0=beforeLabel!!.second-beforeLabel.first;val h1=afterLabel!!.second-afterLabel.first
            assertTrue("Glyph height must double across the commit ($h0 -> $h1)",h1>h0*1.7f&&h1<h0*2.3f)
            assertTrue("Glyph offset below its boundary must scale with the zoom",kotlin.math.abs((afterLabel.first-lo1)-2f*(beforeLabel.first-lo))<=20f)
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
    @Test fun directPdfRasterFeedsOcrWithPageCoordinates(){
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val f=File(c.cacheDir,"source-ocr-fixture.pdf");val pdf=android.graphics.pdf.PdfDocument()
        val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(400,600,1).create())
        page.canvas.drawColor(Color.WHITE);page.canvas.drawText("Lecture des logements",50f,220f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=24f})
        pdf.finishPage(page);f.outputStream().use{pdf.writeTo(it)};pdf.close()
        PdfDocument(f).use{doc->
            val preview=Bitmap.createBitmap(40,60,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.WHITE)}
            val chunk=OcrChunk(1,1,0,Box(.08f,.28f,.97f,.40f),Kind.TEXT);var used=false;var text=""
            ZoneDetector(c).recognizeChunks(listOf(chunk),preview,true,{false},{_,words->
                text=words.joinToString(" "){it.text};assertTrue(words.all{it.box.top>=chunk.box.top&&it.box.bottom<=chunk.box.bottom+.001f})
            },{part->used=true;doc.ocrRegion(0,part.box).also{assertTrue(it.width>preview.width*5);assertTrue(it.width*it.height<=3_010_000)}})
            assertTrue(used);assertTrue("Direct source must recover words absent from the blank preview: $text",text.lowercase().contains("logements"));preview.recycle()
        };f.delete()
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
    @Test fun adjustableSplitDialogGesturesCancelAndConfirm(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            val original=listOf(Zone(3,Box(.04f,.04f,.96f,.96f),Kind.TEXT),Zone(8,Box(.1f,.1f,.2f,.2f),Kind.IMAGE))
            var dialog:android.app.Dialog?=null
            fun set(a:MainActivity,n:String,v:Any){MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.set(a,v)}
            fun get(a:MainActivity,n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.get(a)
            fun invoke(a:MainActivity,n:String,vararg args:Any){MainActivity::class.java.declaredMethods.first{it.name==n}.apply{isAccessible=true}.invoke(a,*args)}
            fun view(v:View):SplitPreview?{if(v is SplitPreview)return v;if(v is ViewGroup)for(k in 0 until v.childCount)view(v.getChildAt(k))?.let{return it};return null}
            fun capture(name:String){i.waitForIdleSync();scenario.onActivity{a->val root=dialog!!.window!!.decorView;val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap));File(File(a.getExternalFilesDir(null),"screenshots").apply{mkdirs()},"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
            // The WindowManager view is returned by each show operation, avoiding test-only app state.
            fun latestDialog(a:MainActivity):android.app.Dialog {
                val field=MainActivity::class.java.getDeclaredField("editDialog").apply{isAccessible=true};return field.get(a)as android.app.Dialog
            }
            scenario.onActivity{a->
                val bitmap=Bitmap.createBitmap(1000,1500,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);val cc=Canvas(bitmap);val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=25f}
                for(line in 0..45){cc.drawText("Lire un journal à son rythme.",40f,60f+line*30,p);cc.drawText("Une autre colonne de texte.",540f,60f+line*30,p)}
                set(a,"preview",bitmap);set(a,"zones",original);set(a,"order",listOf(8,3));set(a,"count",1);invoke(a,"editZone",original[0]);dialog=latestDialog(a)
            };capture("block-menu");scenario.onActivity{dialog!!.dismiss()}
            for(vertical in listOf(false,true)){
                scenario.onActivity{a->invoke(a,"showSplit",original[0],vertical);dialog=latestDialog(a)};capture(if(vertical)"split-v" else "split-h")
                scenario.onActivity{a->val root=dialog!!.window!!.decorView;val v=view(root)!!;val initial=v.fraction;val h=v.handlePoint();val now=android.os.SystemClock.uptimeMillis()
                    fun touch(action:Int,x:Float,y:Float){val event=MotionEvent.obtain(now,now+20,action,x,y,0);v.dispatchTouchEvent(event);event.recycle()}
                    touch(0,h.x,h.y);touch(2,h.x+if(vertical)60f else 0f,h.y+if(vertical)0f else 60f);touch(1,h.x,h.y);assertNotEquals(initial,v.fraction)
                    findButton(root,"Zoomer")!!.performClick();assertTrue(v.zoom>1f);val before=v.fraction;findButton(root,if(vertical)"À gauche" else "Monter")!!.performClick();assertTrue(v.fraction<before)
                };capture(if(vertical)"split-v-zoom" else "split-h-zoom")
                scenario.onActivity{a->findButton(dialog!!.window!!.decorView,"Annuler")!!.performClick();assertEquals(original,get(a,"zones"));assertEquals(listOf(8,3),get(a,"order"));invoke(a,"showSplit",original[0],vertical);dialog=latestDialog(a)}
                i.waitForIdleSync()
                scenario.onActivity{a->val root=dialog!!.window!!.decorView;view(root)!!.setFraction(.63f);findButton(root,"Confirmer la séparation")!!.performClick();assertEquals(listOf(8,3,9),get(a,"order"));val result=get(a,"zones")as List<Zone>;assertEquals(3,result.size);if(vertical)assertEquals(.04f+.92f*.63f,result[0].box.right,.00001f)else assertEquals(.04f+.92f*.63f,result[0].box.bottom,.00001f);set(a,"zones",original);set(a,"order",listOf(8,3))}
            }
        }
    }
    @Test fun sweepSelectionAndArticlePreviewAreReversible(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext;File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            fun set(a:MainActivity,n:String,v:Any){MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.set(a,v)}
            fun get(a:MainActivity,n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.get(a)
            fun call(a:MainActivity,n:String,vararg args:Any){MainActivity::class.java.declaredMethods.first{it.name==n}.apply{isAccessible=true}.invoke(a,*args)}
            val zones=listOf(Zone(1,Box(.1f,.1f,.8f,.3f),Kind.TEXT),Zone(2,Box(.1f,.35f,.8f,.55f),Kind.TEXT),Zone(3,Box(.1f,.6f,.8f,.8f),Kind.TEXT))
            scenario.onActivity{a->val image=Bitmap.createBitmap(700,1000,Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);val cc=Canvas(image);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=23f};for(j in 0..23)cc.drawText("Lire à son rythme, bloc après bloc.",80f,120f+j*27,paint);set(a,"preview",image);set(a,"count",1);set(a,"zones",zones);set(a,"order",emptyList<Int>());val page=findCanvas(a.window.decorView)!!;page.image=image;call(a,"sync",false)}
            i.waitForIdleSync();screenshot(scenario,"gesture-before")
            var down=0L;var start=PointF();var end=PointF()
            fun event(action:Int,p:PointF){scenario.onActivity{a->val v=findCanvas(a.window.decorView)!!;val e=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,p.x,p.y,0);v.dispatchTouchEvent(e);e.recycle()}}
            scenario.onActivity{a->val v=findCanvas(a.window.decorView)!!;val m=PageCanvas::class.java.getDeclaredField("matrix").apply{isAccessible=true}.get(v)as Matrix;val pts=floatArrayOf(220f,180f,220f,700f);m.mapPoints(pts);start=PointF(pts[0],pts[1]);end=PointF(pts[2],pts[3]);down=android.os.SystemClock.uptimeMillis()}
            event(0,start);Thread.sleep(750);event(2,end);event(2,start);event(1,start);Thread.sleep(350)
            scenario.onActivity{a->assertEquals(listOf(1,2,3),get(a,"order"))};screenshot(scenario,"gesture-selected")
            scenario.onActivity{a->call(a,"beginMerge",zones[0]);val v=findCanvas(a.window.decorView)!!;v.onZoneSweep!!.invoke(zones[1]);v.onZoneSweep!!.invoke(zones[2]);assertEquals(listOf(1,2,3),get(a,"order"))};screenshot(scenario,"gesture-merge")
            scenario.onActivity{a->call(a,"finishMerge",false);assertEquals(zones,get(a,"zones"));call(a,"beginArticle",zones[0]);assertNotNull(get(a,"articleIds"))};screenshot(scenario,"article-preview")
            scenario.onActivity{a->val v=findCanvas(a.window.decorView)!!;v.onZoneTap!!.invoke(zones[2]);assertEquals(listOf(1,2,3),get(a,"order"));call(a,"finishArticle",false);assertEquals(listOf(1,2,3),get(a,"order"));call(a,"beginArticle",zones[0]);call(a,"finishArticle",true);assertEquals(listOf(1,2,3),get(a,"order"));assertEquals(zones,get(a,"zones"))}
        }
    }
    @Test fun visualPageSummaryRendersPdfThumbnailsCancelsAndNavigates(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        val file=File(c.filesDir,"current.pdf");val pdf=android.graphics.pdf.PdfDocument()
        for(n in 1..68){val p=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(400,600,n).create());p.canvas.drawColor(Color.WHITE);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=22f};p.canvas.drawText("Journal fictif · page $n",20f,45f,paint);paint.textSize=14f;for(line in 0..24)p.canvas.drawText("Une lecture locale, à son rythme.",20f,85f+line*18,paint);if(n%3==0){paint.color=Color.rgb(180,208,191);p.canvas.drawRect(20f,90f,380f,180f,paint)};pdf.finishPage(p)}
        file.outputStream().use{pdf.writeTo(it)};pdf.close();val store=SessionStore(c);store.clear();store.key="synthetic-summary";val zones=listOf(Zone(1,Box(.04f,.05f,.96f,.95f),Kind.TEXT));for(n in listOf(16,67))store.put(n,PageSession(zones,listOf(1),0,200));store.page=16
        PdfDocument(file).use{doc->val thumb=doc.thumbnail(16);assertTrue(thumb.width<=240&&thumb.height<=360);assertTrue(thumb.byteCount<=240*360*4);var ink=0;for(y in 0 until thumb.height)for(x in 0 until thumb.width)if(Color.red(thumb.getPixel(x,y))<150)ink++;assertTrue("PDF thumbnail must contain source ink",ink>100);thumb.recycle()}
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            fun field(a:MainActivity,n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.get(a)
            val deadline=System.currentTimeMillis()+30000;var ready=false
            while(System.currentTimeMillis()<deadline){scenario.onActivity{a->ready=(field(a,"pageReady")as Boolean)&&field(a,"page")==16};if(ready)break;Thread.sleep(100)};assertTrue(ready)
            fun grid(v:View):GridView?{if(v is GridView)return v;if(v is ViewGroup)for(j in 0 until v.childCount)grid(v.getChildAt(j))?.let{return it};return null}
            scenario.onActivity{a->findButton(a.window.decorView,"Lire (")!!.performClick();findButton(a.window.decorView,"Pages")!!.performClick()};Thread.sleep(2500);i.waitForIdleSync()
            scenario.onActivity{a->val d=field(a,"pageSummary")as android.app.Dialog;val root=d.window!!.decorView;assertEquals(68,grid(root)!!.adapter.count);val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap));File(File(a.getExternalFilesDir(null),"screenshots").apply{mkdirs()},"page-summary.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();findButton(root,"Fermer")!!.performClick();assertEquals(16,field(a,"page"));assertEquals(true,field(a,"reading"));assertEquals(listOf(1),field(a,"order"))}
            i.waitForIdleSync();scenario.onActivity{a->findButton(a.window.decorView,"Pages")!!.performClick()};i.waitForIdleSync();scenario.onActivity{a->val d=field(a,"pageSummary")as android.app.Dialog;val g=grid(d.window!!.decorView)!!;g.performItemClick(g.getChildAt(0),67,67)}
            val end=System.currentTimeMillis()+30000;ready=false;while(System.currentTimeMillis()<end){scenario.onActivity{a->ready=field(a,"page")==67&&(field(a,"pageReady")as Boolean)};if(ready)break;Thread.sleep(100)};assertTrue("Tap must open selected last page",ready);scenario.onActivity{a->assertEquals(listOf(1),field(a,"order"));assertNotNull(SessionStore(a).get(16))};screenshot(scenario,"page-summary-last-page")
        }
    }
    @Test fun ocrReviewIsVisibleReversibleAndSavedWithoutArticleText(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext;File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            val box=Box(0f,0f,1f,1f);val review=OcrReview.compare("Le mot corrlgé reste vérifiable.","Le mot corrigé reste vérifiable.",box)
            scenario.onActivity{a->fun set(n:String,v:Any){MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.set(a,v)};set("count",1);set("order",listOf(1));set("zones",listOf(Zone(1,box,Kind.TEXT)));set("reading",true);set("ocrReading",true);set("enhancedOcr",true);MainActivity::class.java.getDeclaredMethod("updateControls").apply{isAccessible=true}.invoke(a);val reader=findReader(a.window.decorView)!!;findCanvas(a.window.decorView)!!.visibility=View.GONE;reader.visibility=View.VISIBLE;reader.setDocument(listOf(Zone(1,box,Kind.TEXT)),700,1000);reader.showOcrStream(listOf(OcrChunk(1,1,0,box,Kind.TEXT)),mapOf(1 to review.corrected),false,reviewTexts=mapOf(1 to review));reader.chooseRaw(1,0,true);assertEquals("Le mot corrlgé reste vérifiable.",reader.review(1)!!.rendered().first);reader.chooseRaw(1,0,false);assertEquals(review.corrected,reader.review(1)!!.rendered().first);val store=SessionStore(a);store.key="ocr-fixture";store.putOcrChoice(0,review.fingerprint(),0,true);assertEquals(setOf(0),SessionStore(a).ocrChoices(0,review.fingerprint()));assertFalse(File(a.filesDir,"session.json").readText().contains("corrlgé"))};i.waitForIdleSync();screenshot(scenario,"ocr-inline-reading");scenario.onActivity{a->MainActivity::class.java.getDeclaredMethod("showOcrComparison",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Float::class.javaPrimitiveType).apply{isAccessible=true}.invoke(a,1,0,150f)};i.waitForIdleSync();Thread.sleep(200);val shot=i.uiAutomation.takeScreenshot();File(File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()},"ocr-inline-comparison.png").outputStream().use{shot.compress(Bitmap.CompressFormat.PNG,100,it)};shot.recycle()
        }
    }
    @Test fun paddleRecognizerRunsLocallyOnFrenchLineAndReleasesSession(){
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val phrase="Une lecture locale à Grenoble : 50 000 logements."
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=38f;typeface=Typeface.SERIF}
        val ink=Rect();paint.getTextBounds(phrase,0,phrase.length,ink)
        // Recognizer input is a tight text line, as it is in the Tesseract-line flow.
        val image=Bitmap.createBitmap(kotlin.math.ceil(paint.measureText(phrase)).toInt()+12,ink.height()+8,Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE)
        Canvas(image).drawText(phrase,6f,4f-ink.top,paint)
        File(File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()},"paddle-synthetic-input.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
        val box=Box(.1f,.2f,.8f,.23f);val start=android.os.SystemClock.elapsedRealtime();val before=android.os.Debug.getNativeHeapAllocatedSize()
        PaddleLineRecognizer(c).use{engine->val result=engine.recognize(image,box)!!;assertTrue("Paddle must recognize French words: ${result.text}",result.text.contains("lecture")&&result.text.contains("logements"));assertTrue("Expected number spacing: ${result.text}",result.text.contains("50 000"));assertEquals(box,result.source);assertTrue(result.confidence>.5f);assertNull(engine.recognize(image,box){true})}
        println("Second recognizer synthetic line: ${android.os.SystemClock.elapsedRealtime()-start}ms; native heap delta ${android.os.Debug.getNativeHeapAllocatedSize()-before} bytes (not peak or phone timing)")
        image.recycle()
    }
    @Test fun magazineWideTitleKeepsColumnsApartInCleanedReading(){
        // Fictional magazine page: a wide short title left-aligned above three text
        // columns, mirroring a reported reading defect. Content is invented.
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        val pdf=android.graphics.pdf.PdfDocument();val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(840,594,1).create())
        val cv=page.canvas;cv.drawColor(Color.WHITE)
        val titlePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(226,118,27);textSize=30f;typeface=Typeface.create("sans-serif",Typeface.BOLD)}
        cv.drawText("Un grand titre invente",.06f*840,.165f*594,titlePaint)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=9f}
        for((ci,label)in listOf(0 to "Colonne une",1 to "Colonne deux",2 to "Colonne trois")){
            val x=(.06f+ci*.30f)*840
            for(line in 0..39)cv.drawText("$label : phrase fictive numero ${line+1} pour ce test.",x,(.215f+line*.018f)*594,p)
        }
        pdf.finishPage(page);File(c.filesDir,"current.pdf").outputStream().use{pdf.writeTo(it)};pdf.close()
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            var ready=false;val end=System.currentTimeMillis()+90000
            while(System.currentTimeMillis()<end){scenario.onActivity{a->ready=MainActivity::class.java.getDeclaredField("pageReady").apply{isAccessible=true}.getBoolean(a)&&findCanvas(a.window.decorView)!!.zones.isNotEmpty()};if(ready)break;Thread.sleep(100)}
            assertTrue("Magazine fixture must finish native detection",ready)
            clearAutoProposal(scenario)
            scenario.onActivity{a->
                val canvas=findCanvas(a.window.decorView)!!
                val textZones=canvas.zones.filter{it.kind==Kind.TEXT}
                val titles=textZones.filter{it.box.height<.075f&&it.box.width>.35f}
                assertTrue("Fixture must yield a wide short title zone: ${canvas.zones}",titles.isNotEmpty())
                val columns=textZones.filter{it.box.height>.3f}.sortedBy{it.box.left}
                assertTrue("Fixture must yield three tall column zones: ${canvas.zones}",columns.size>=3)
                (titles.sortedBy{it.box.top}+columns.take(3)).forEach{canvas.onZoneTap!!.invoke(it)}
                val chosen=ReadingLayout.ordered(canvas.zones,canvas.selection)
                val bands=ContinuousLayout.bands(chosen)
                // Only tall columns must keep their own horizontal extent. A short heading is
                // allowed to widen toward its column (that is the intended kept behaviour); the
                // defect was a tall column growing, so columns are what this assertion pins.
                for(band in bands.filter{it.kind==Kind.TEXT}){
                    val zone=chosen.first{it.id==band.zoneId}
                    if(zone.box.height<.075f)continue
                    for(piece in band.pieces){
                        assertTrue("Band of zone ${zone.id} exceeds its own extent: $piece in ${zone.box}",piece.left>=zone.box.left-.001f&&piece.right<=zone.box.right+.001f)
                    }
                }
                val pieces=bands.flatMap{it.pieces};for(x in pieces.indices)for(y in x+1 until pieces.size)assertEquals(0f,pieces[x].intersect(pieces[y]),.000001f)
            }
            screenshot(scenario,"magazine-selected")
            scenario.onActivity{a->findButton(a.window.decorView,"Lire (")!!.performClick()}
            Thread.sleep(2000);i.waitForIdleSync()
            scenario.onActivity{a->findButton(a.window.decorView,"Nettoyé")!!.performClick()}
            Thread.sleep(1500);screenshot(scenario,"magazine-cleaned-top")
            scenario.onActivity{a->val reader=findReader(a.window.decorView)!!;val now=android.os.SystemClock.uptimeMillis();reader.onTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,540f,2000f,0));reader.onTouchEvent(MotionEvent.obtain(now,now+150,MotionEvent.ACTION_MOVE,540f,200f,0));reader.onTouchEvent(MotionEvent.obtain(now,now+200,MotionEvent.ACTION_UP,540f,200f,0))}
            Thread.sleep(1500);screenshot(scenario,"magazine-cleaned-middle")
            scenario.onActivity{a->val reader=findReader(a.window.decorView)!!;val now=android.os.SystemClock.uptimeMillis();reader.onTouchEvent(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,540f,2000f,0));reader.onTouchEvent(MotionEvent.obtain(now,now+150,MotionEvent.ACTION_MOVE,540f,200f,0));reader.onTouchEvent(MotionEvent.obtain(now,now+200,MotionEvent.ACTION_UP,540f,200f,0));assertTrue("Reading must scroll through the columns",reader.currentPosition()>1500)}
            Thread.sleep(1500);screenshot(scenario,"magazine-cleaned-end")
        }
    }
    /** Three fictional portrait pages: one orange title + one text column per page. Content invented. */
    private fun writeThreePageFixture(c:android.content.Context){
        File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        val pdf=android.graphics.pdf.PdfDocument()
        for(p in 1..3){
            val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,p).create())
            val cv=page.canvas;cv.drawColor(Color.WHITE)
            val tp=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(226,118,27);textSize=26f;typeface=Typeface.create("sans-serif",Typeface.BOLD)}
            cv.drawText("Titre invente de la page $p",40f,90f,tp)
            val t=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=11f}
            for(line in 0..44)cv.drawText("Page $p, phrase fictive numero ${line+1} pour ce test.",40f,140f+line*14f,t)
            pdf.finishPage(page)
        }
        File(c.filesDir,"current.pdf").outputStream().use{pdf.writeTo(it)};pdf.close()
    }
    private fun awaitDetection(scenario:ActivityScenario<MainActivity>){
        var ready=false;val end=System.currentTimeMillis()+90000
        while(System.currentTimeMillis()<end){scenario.onActivity{a->ready=MainActivity::class.java.getDeclaredField("pageReady").apply{isAccessible=true}.getBoolean(a)&&findCanvas(a.window.decorView)!!.zones.isNotEmpty()};if(ready)break;Thread.sleep(100)}
        assertTrue("Fixture must finish native detection",ready)
        clearAutoProposal(scenario)
    }
    private fun startReadingFirstBlock(scenario:ActivityScenario<MainActivity>){
        scenario.onActivity{a->
            val canvas=findCanvas(a.window.decorView)!!
            canvas.zones.filter{it.kind==Kind.TEXT}.sortedBy{it.box.top}.take(1).forEach{canvas.onZoneTap!!.invoke(it)}
        }
        scenario.onActivity{a->findButton(a.window.decorView,"Lire (")!!.performClick()}
        Thread.sleep(2000);InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario.onActivity{a->findButton(a.window.decorView,"Nettoyé")?.performClick()}
        Thread.sleep(1500);InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
    private fun awaitPage(scenario:ActivityScenario<MainActivity>,label:String){
        var found=false;val end=System.currentTimeMillis()+30000
        while(System.currentTimeMillis()<end){scenario.onActivity{a->found=findButton(a.window.decorView,label)!=null};if(found)break;Thread.sleep(100)}
        assertTrue("Expected page control $label",found)
    }
    @Test fun endCardKeepsBottomPaddingOnNarrowViewport(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        i.runOnMainSync{
            val reader=ContinuousReaderView(c)
            reader.measure(View.MeasureSpec.makeMeasureSpec(820,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));reader.layout(0,0,820,2400)
            reader.onRequest={tile,revision->val b=Bitmap.createBitmap(kotlin.math.ceil(tile.rect.width()).toInt(),kotlin.math.ceil(tile.rect.height()).toInt(),Bitmap.Config.ARGB_8888);b.eraseColor(Color.rgb(40,80,60));reader.accept(tile,revision,b)}
            reader.endTitle="Fin de la page 1";reader.hasNextPage=true
            reader.setDocument(listOf(Zone(1,Box(0f,0f,1f,1f),Kind.TEXT)),600,12000)
            reader.move(Float.MAX_VALUE)
            val out=Bitmap.createBitmap(820,2400,Bitmap.Config.ARGB_8888)
            repeat(2){reader.draw(Canvas(out))}
            File(File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()},"nav-end-card-narrow.png").outputStream().use{out.compress(Bitmap.CompressFormat.PNG,100,it)}
            out.recycle();reader.clear()
        }
    }
    @Test fun pageSliderChangesPage(){
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        writeThreePageFixture(c)
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            awaitDetection(scenario)
            scenario.onActivity{a->
                var seek:SeekBar?=null
                fun walk(v:View){if(v is SeekBar)seek=v;if(v is ViewGroup)for(i in 0 until v.childCount)walk(v.getChildAt(i))}
                walk(a.window.decorView)
                assertNotNull("Page slider must be visible on a multi-page document",seek)
                assertTrue(seek!!.isShown);assertEquals(2,seek!!.max)
            }
            scenario.onActivity{a->a.sliderPage(1)}
            awaitPage(scenario,"Pages 2/3")
            screenshot(scenario,"nav-slider-page-2")
        }
    }
    @Test fun readingEndCardButtonOpensNextPagePreparation(){
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        writeThreePageFixture(c)
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            awaitDetection(scenario);startReadingFirstBlock(scenario)
            scenario.onActivity{a->val r=findReader(a.window.decorView)!!;r.move(Float.MAX_VALUE)}
            Thread.sleep(800);InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity{a->val r=findReader(a.window.decorView)!!;assertTrue("End card must add height",r.footerTop>0);assertTrue(r.hasNextPage)}
            screenshot(scenario,"nav-reading-end-card")
            // Tap the card's « Préparer la page suivante » button, drawn in the reader.
            scenario.onActivity{a->
                val r=findReader(a.window.decorView)!!;val d=r.resources.displayMetrics.density
                val contentY=r.footerTop+8*d+165*d;val screenY=contentY-r.currentPosition()
                val down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,r.width/2f,screenY,0);r.dispatchTouchEvent(down);down.recycle()
                val up=MotionEvent.obtain(0,60,MotionEvent.ACTION_UP,r.width/2f,screenY,0);r.dispatchTouchEvent(up);up.recycle()
            }
            awaitPage(scenario,"Pages 2/3")
            screenshot(scenario,"nav-end-card-next-page")
        }
    }
    @Test fun overscrollBeyondReadingEndOpensNextPagePreparation(){
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        writeThreePageFixture(c)
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            awaitDetection(scenario);startReadingFirstBlock(scenario)
            scenario.onActivity{a->val r=findReader(a.window.decorView)!!;r.move(Float.MAX_VALUE)}
            Thread.sleep(800);InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            // A plain arrival at the bottom changes nothing: only a deliberate sustained pull beyond
            // the end opens the next page's preparation (inverse pull-to-refresh).
            scenario.onActivity{a->
                val r=findReader(a.window.decorView)!!;val x=r.width/2f;val h=r.height
                var t=0L
                val down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,h*.7f,0);r.dispatchTouchEvent(down);down.recycle()
                for(i in 1..6){t+=16;val mv=MotionEvent.obtain(0,t,MotionEvent.ACTION_MOVE,x,h*.7f-i*(h*.09f),0);r.dispatchTouchEvent(mv);mv.recycle()}
                t+=16;val up=MotionEvent.obtain(0,t,MotionEvent.ACTION_UP,x,h*.7f-6*(h*.09f),0);r.dispatchTouchEvent(up);up.recycle()
            }
            awaitPage(scenario,"Pages 2/3")
            screenshot(scenario,"nav-overscroll-next-page")
        }
    }
    @Test fun autoSelectProposesOnceAndRespectsChoices(){
        // Fictional magazine page, invented content: auto mode must preselect title then
        // columns, once, and never recompute after the user changes anything.
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        val pdf=android.graphics.pdf.PdfDocument();val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(840,594,1).create())
        val cv=page.canvas;cv.drawColor(Color.WHITE)
        val titlePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(226,118,27);textSize=30f;typeface=Typeface.create("sans-serif",Typeface.BOLD)}
        cv.drawText("Un grand titre invente",.06f*840,.165f*594,titlePaint)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=9f}
        for((ci,label)in listOf(0 to "Colonne une",1 to "Colonne deux",2 to "Colonne trois")){
            val x=(.06f+ci*.30f)*840
            for(line in 0..39)cv.drawText("$label : phrase fictive numero ${line+1} pour ce test.",x,(.215f+line*.018f)*594,p)
        }
        pdf.finishPage(page);File(c.filesDir,"current.pdf").outputStream().use{pdf.writeTo(it)};pdf.close()
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            fun field(a:MainActivity,n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.get(a)
            fun call(a:MainActivity,n:String,vararg args:Any){MainActivity::class.java.declaredMethods.first{it.name==n}.apply{isAccessible=true}.invoke(a,*args)}
            fun awaitReady(){var ready=false;val end=System.currentTimeMillis()+90000;while(System.currentTimeMillis()<end){scenario.onActivity{a->ready=field(a,"pageReady")as Boolean&&findCanvas(a.window.decorView)!!.zones.isNotEmpty()};if(ready)break;Thread.sleep(100)};assertTrue("Detection must finish",ready)}
            awaitReady()
            scenario.onActivity{a->
                val zones=findCanvas(a.window.decorView)!!.zones
                val order=field(a,"order")as List<*>
                assertEquals("Auto mode must preselect every detected block in proposed order",AutoOrder.propose(zones),order)
                assertTrue("Auto proposal must not be empty",order.isNotEmpty())
                // Reading-order properties on this fixture's three columns: title first,
                // columns contiguous (no interleaving), top-down inside each column.
                val byId=zones.associateBy{it.id}
                val ids=(order as List<*>).map{it as Int}
                fun lane(z:Zone):Int{val cx=(z.box.left+z.box.right)/2;return when{cx<.33f->0;cx<.65f->1;else->2}}
                assertTrue("Title must read first",byId[ids.first()]!!.box.bottom<.19f)
                assertEquals("Columns must read contiguously, left first",listOf(0,0,0,0,0,1,1,1,1,2,2),ids.map{lane(byId[it]!!)})
                for(l in 0..2){val tops=ids.map{byId[it]!!}.filter{lane(it)==l}.map{it.box.top};assertEquals("Column $l must read top-down",tops.sorted(),tops)}
                assertEquals(true,field(a,"autoProposed"))
                assertTrue("Toggle must be visible",findButton(a.window.decorView,"Auto")!!.isShown)
                assertTrue("Clear cross must be visible with a selection",findButton(a.window.decorView,"✕")!!.isShown)
                assertTrue("Status must announce the automatic choice",findText(a.window.decorView,"choisis automatiquement"))
                File(File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()},"auto-select-zones.txt").writeText(zones.joinToString("\n")+"\nORDER\n"+order.joinToString(","))
            }
            screenshot(scenario,"auto-select-page")
            // The proposal is computed once: any manual change ends the automatic label.
            scenario.onActivity{a->val canvas=findCanvas(a.window.decorView)!!;val last=canvas.zones.last{it.id in canvas.selection};canvas.onZoneTap!!.invoke(last);assertTrue("Manual edit must end the automatic label",findText(a.window.decorView,"dans votre ordre"))}
            // The clear cross empties the page and the empty choice persists: no re-proposal on return.
            scenario.onActivity{a->findCanvas(a.window.decorView)!!.onZoneTap!!.invoke(findCanvas(a.window.decorView)!!.zones.first())}
            scenario.onActivity{a->findButton(a.window.decorView,"✕")!!.performClick()}
            i.waitForIdleSync()
            scenario.onActivity{a->
                assertEquals(emptyList<Int>(),field(a,"order"))
                assertFalse("Cross must hide on an empty selection",findButton(a.window.decorView,"✕")!!.isShown)
                assertTrue("Zones stay available for manual choice",findCanvas(a.window.decorView)!!.zones.isNotEmpty())
            }
            screenshot(scenario,"auto-select-cleared")
            scenario.onActivity{a->call(a,"loadPage",0,false)}
            awaitReady()
            scenario.onActivity{a->assertEquals("Cleared page must stay empty on return (no auto re-proposal)",emptyList<Int>(),field(a,"order"));assertEquals(false,field(a,"autoProposed"))}
            // Turning Auto off applies to pages prepared later, without touching saved choices.
            scenario.onActivity{a->findButton(a.window.decorView,"Auto")!!.performClick();assertEquals(false,field(a,"autoSelect"));call(a,"loadPage",0,true)}
            awaitReady()
            scenario.onActivity{a->assertEquals("Auto off must leave new detections unselected",emptyList<Int>(),field(a,"order"));assertTrue(findText(a.window.decorView,"dans votre ordre"))}
            scenario.onActivity{a->findButton(a.window.decorView,"Auto")!!.performClick();assertEquals(true,field(a,"autoSelect"))}
        }
    }
    @Test fun autoSelectProposesReadableOrderOnContinuousText(){
        // Fictional magazine page with continuous prose columns (no repeated labels):
        // the middle column carries a paragraph gap, which may split it into two blocks.
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        val pdf=android.graphics.pdf.PdfDocument();val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(840,594,1).create())
        val cv=page.canvas;cv.drawColor(Color.WHITE)
        val titlePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(226,118,27);textSize=30f;typeface=Typeface.create("sans-serif",Typeface.BOLD)}
        cv.drawText("Un grand titre invente",.06f*840,.165f*594,titlePaint)
        val words=listOf("conseil","municipal","invente","une","histoire","complete","pour","ce","test","de","lecture","continue","sans","etiquette")
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=9f}
        for(ci in 0..2){
            val x=(.06f+ci*.30f)*840;var line=0;var w=ci*3
            while(line<40){
                if(ci==1&&line==22){line+=2} // paragraph gap in the middle column
                val sb=StringBuilder()
                repeat(5+((w+line)%3)){sb.append(words[(w+line+it)%words.size]).append(' ')}
                cv.drawText(sb.toString().trim(),x,(.215f+line*.018f)*594,p);w+=7;line++
            }
        }
        pdf.finishPage(page);File(c.filesDir,"current.pdf").outputStream().use{pdf.writeTo(it)};pdf.close()
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            fun field(a:MainActivity,n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.get(a)
            var ready=false;val end=System.currentTimeMillis()+90000
            while(System.currentTimeMillis()<end){scenario.onActivity{a->ready=field(a,"pageReady")as Boolean&&findCanvas(a.window.decorView)!!.zones.isNotEmpty()};if(ready)break;Thread.sleep(100)}
            assertTrue("Detection must finish",ready)
            screenshot(scenario,"auto-select-continuous")
            scenario.onActivity{a->
                val zones=findCanvas(a.window.decorView)!!.zones
                val order=(field(a,"order")as List<*>).map{it as Int}
                // Dump the detected geometry before asserting, so a failure still ships the evidence.
                File(File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()},"auto-select-continuous-zones.txt").writeText(zones.joinToString("\n")+"\nORDER\n"+order.joinToString(","))
                val byId=zones.associateBy{it.id}
                assertTrue("Proposal must not be empty",order.isNotEmpty())
                assertTrue("Title must read first",byId[order.first()]!!.box.bottom<.19f)
                fun lane(z:Zone):Int{val cx=(z.box.left+z.box.right)/2;return when{cx<.345f->0;cx<.645f->1;else->2}}
                val lanes=order.map{lane(byId[it]!!)}
                assertEquals("Columns must read contiguously, left first",lanes.sorted(),lanes)
                for(l in lanes.toSet()){val tops=order.map{byId[it]!!}.filter{lane(it)==l}.map{it.box.top};assertEquals("Column $l must read top-down",tops.sorted(),tops)}
            }
        }
    }
    @Test fun autoSelectProposesOrderOnMultiPageWithSlider(){
        // Integrated screen on a multi-page document: auto proposal, page slider,
        // Auto toggle and clear cross all visible together.
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        writeThreePageFixture(c)
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            fun field(a:MainActivity,n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.get(a)
            var ready=false;val end=System.currentTimeMillis()+90000
            while(System.currentTimeMillis()<end){scenario.onActivity{a->ready=field(a,"pageReady")as Boolean&&findCanvas(a.window.decorView)!!.zones.isNotEmpty()};if(ready)break;Thread.sleep(100)}
            assertTrue("Detection must finish",ready)
            scenario.onActivity{a->
                var seek:SeekBar?=null
                fun walk(v:View){if(v is SeekBar)seek=v;if(v is ViewGroup)for(j in 0 until v.childCount)walk(v.getChildAt(j))}
                walk(a.window.decorView)
                assertNotNull("Page slider must be visible on a multi-page document",seek);assertTrue(seek!!.isShown)
                assertTrue("Auto toggle must be visible",findButton(a.window.decorView,"Auto")!!.isShown)
                assertTrue("Clear cross must be visible with the proposal",findButton(a.window.decorView,"✕")!!.isShown)
                assertTrue("Proposal must not be empty",(field(a,"order")as List<*>).isNotEmpty())
                assertEquals(true,field(a,"autoProposed"))
            }
            screenshot(scenario,"auto-select-slider")
        }
    }
    @Test fun physicalTwoFingerSweepHitsFingertipsAcrossColumnGutter(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            fun field(a:MainActivity,n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}
            val zones=listOf(Zone(1,Box(.2f,.12f,.42f,.8f),Kind.TEXT),Zone(2,Box(.52f,.12f,.75f,.8f),Kind.TEXT),Zone(3,Box(.8f,.12f,.95f,.8f),Kind.TEXT))
            scenario.onActivity{a->
                val image=Bitmap.createBitmap(700,1000,Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE)
                val cc=Canvas(image);val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=14f}
                for((n,z)in zones.withIndex())for(line in 0..28)cc.drawText("Colonne ${n+1} : ligne $line",z.box.left*700+4,140f+line*22,ink)
                field(a,"preview").set(a,image);field(a,"count").set(a,1);field(a,"pageReady").set(a,true);field(a,"zones").set(a,zones);field(a,"order").set(a,listOf(1,2,3))
                findCanvas(a.window.decorView)!!.image=image;MainActivity::class.java.getDeclaredMethod("sync",Boolean::class.javaPrimitiveType).apply{isAccessible=true}.invoke(a,false)
            };i.waitForIdleSync();screenshot(scenario,"two-finger-columns-before")
            fun point(x:Float,y:Float):PointF{var out=PointF();scenario.onActivity{a->
                val v=findCanvas(a.window.decorView)!!;val matrix=PageCanvas::class.java.getDeclaredField("matrix").apply{isAccessible=true}.get(v)as Matrix
                val xy=floatArrayOf(x*700,y*1000);matrix.mapPoints(xy);val offset=IntArray(2);v.getLocationOnScreen(offset);out=PointF(xy[0]+offset[0],xy[1]+offset[1])
            };return out}
            val left=point(.3f,.04f);val right=point(.65f,.04f);val end=point(.3f,.87f);val down=android.os.SystemClock.uptimeMillis()
            fun inject(action:Int,a:PointF,b:PointF?=null){
                val n=if(b==null)1 else 2
                val properties=Array(n){k->MotionEvent.PointerProperties().apply{id=k;toolType=MotionEvent.TOOL_TYPE_FINGER}}
                val coords=Array(n){k->MotionEvent.PointerCoords().apply{val p=if(k==0)a else b!!;x=p.x;y=p.y;pressure=1f;size=.12f}}
                val e=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,n,properties,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
                assertTrue("Input must enter the Android window",i.uiAutomation.injectInputEvent(e,true));e.recycle()
            }
            inject(MotionEvent.ACTION_DOWN,left);Thread.sleep(80)
            inject(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),left,right)
            repeat(8){k->Thread.sleep(90);val jitter=if(k%2==0)2f else -2f;inject(MotionEvent.ACTION_MOVE,PointF(left.x+jitter,left.y+1),PointF(right.x+jitter,right.y-1))}
            for(k in 1..18){val dy=(end.y-left.y)*k/18;inject(MotionEvent.ACTION_MOVE,PointF(left.x,left.y+dy),PointF(right.x,right.y+dy));Thread.sleep(18)}
            i.waitForIdleSync();screenshot(scenario,"two-finger-columns-dragged")
            inject(MotionEvent.ACTION_POINTER_UP or (1 shl 8),PointF(left.x,end.y),PointF(right.x,end.y));Thread.sleep(60);inject(MotionEvent.ACTION_UP,PointF(left.x,end.y));Thread.sleep(350)
            scenario.onActivity{a->assertEquals("Fingertips remove both columns, midpoint stays in gutter, untouched third column remains",listOf(3),field(a,"order").get(a));assertFalse("Removal must not accidentally zoom",findCanvas(a.window.decorView)!!.isZoomed())}
            screenshot(scenario,"two-finger-columns-after")
        }
    }
    @Test fun twoFingerHoldRemovesCrossedBlocksOnly(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext;File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            fun set(a:MainActivity,n:String,v:Any){MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.set(a,v)}
            fun get(a:MainActivity,n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.get(a)
            fun call(a:MainActivity,n:String,vararg args:Any){MainActivity::class.java.declaredMethods.first{it.name==n}.apply{isAccessible=true}.invoke(a,*args)}
            val zones=listOf(Zone(1,Box(.1f,.1f,.8f,.3f),Kind.TEXT),Zone(2,Box(.1f,.35f,.8f,.55f),Kind.TEXT),Zone(3,Box(.1f,.6f,.8f,.8f),Kind.TEXT))
            scenario.onActivity{a->val image=Bitmap.createBitmap(700,1000,Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);set(a,"preview",image);set(a,"count",1);set(a,"zones",zones);set(a,"order",listOf(1,2,3));val page=findCanvas(a.window.decorView)!!;page.image=image;call(a,"sync",false)}
            i.waitForIdleSync()
            var down=0L
            fun twoFingerEvent(action:Int,x0:Float,y0:Float,x1:Float,y1:Float){
                scenario.onActivity{a->
                    val v=findCanvas(a.window.decorView)!!
                    val pp=arrayOf(MotionEvent.PointerProperties().apply{id=0;toolType=MotionEvent.TOOL_TYPE_FINGER},MotionEvent.PointerProperties().apply{id=1;toolType=MotionEvent.TOOL_TYPE_FINGER})
                    val pc=arrayOf(MotionEvent.PointerCoords().apply{x=x0;y=y0},MotionEvent.PointerCoords().apply{x=x1;y=y1})
                    val e=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,2,pp,pc,0,0,1f,1f,0,0,0,0)
                    v.dispatchTouchEvent(e);e.recycle()
                }
            }
            fun screenPoint(nx:Float,ny:Float):PointF{var out=PointF();scenario.onActivity{a->val v=findCanvas(a.window.decorView)!!;val m=PageCanvas::class.java.getDeclaredField("matrix").apply{isAccessible=true}.get(v)as Matrix;val pts=floatArrayOf(nx*700,ny*1000);m.mapPoints(pts);out=PointF(pts[0],pts[1])};return out}
            // First: a fast two-finger spread (pinch) must NOT remove anything.
            scenario.onActivity{a->val v=findCanvas(a.window.decorView)!!;down=android.os.SystemClock.uptimeMillis();val e=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,300f,300f,0);v.dispatchTouchEvent(e);e.recycle()}
            Thread.sleep(60)
            twoFingerEvent(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),300f,300f,380f,300f)
            Thread.sleep(60)
            twoFingerEvent(MotionEvent.ACTION_MOVE,300f,300f,520f,300f) // span grows: pinch
            Thread.sleep(700)
            twoFingerEvent(MotionEvent.ACTION_MOVE,300f,300f,560f,300f)
            twoFingerEvent(MotionEvent.ACTION_POINTER_UP or (1 shl 8),300f,300f,560f,300f)
            Thread.sleep(200)
            scenario.onActivity{a->assertEquals("Pinch before the hold must not remove blocks",listOf(1,2,3),get(a,"order"))}
            // Then: two fingers held still, then dragged together across block 2 removes it.
            // The pair starts in the blank gap between blocks 1 and 2, so only block 2 is crossed.
            val from=screenPoint(.45f,.32f);val to=screenPoint(.45f,.45f)
            scenario.onActivity{a->val v=findCanvas(a.window.decorView)!!;down=android.os.SystemClock.uptimeMillis();val e=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,from.x-40,from.y,0);v.dispatchTouchEvent(e);e.recycle()}
            Thread.sleep(60)
            twoFingerEvent(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),from.x-40,from.y,from.x+40,from.y)
            Thread.sleep(800) // hold: the removal sweep activates
            val mx=(to.x-from.x)/2;val my=(to.y-from.y)/2
            twoFingerEvent(MotionEvent.ACTION_MOVE,from.x-40+mx,from.y+my,from.x+40+mx,from.y+my)
            twoFingerEvent(MotionEvent.ACTION_MOVE,to.x-40,to.y,to.x+40,to.y)
            twoFingerEvent(MotionEvent.ACTION_POINTER_UP or (1 shl 8),to.x-40,to.y,to.x+40,to.y)
            Thread.sleep(300)
            scenario.onActivity{a->assertEquals("Two-finger sweep must remove the crossed block only",listOf(1,3),get(a,"order"))}
            screenshot(scenario,"two-finger-remove")
            // A full pass from above block 1 to below block 3 removes the rest; unchosen block 2 is never re-added.
            val top=screenPoint(.45f,.05f);val bottom=screenPoint(.45f,.85f)
            scenario.onActivity{a->val v=findCanvas(a.window.decorView)!!;down=android.os.SystemClock.uptimeMillis();val e=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,top.x-40,top.y,0);v.dispatchTouchEvent(e);e.recycle()}
            Thread.sleep(60)
            twoFingerEvent(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),top.x-40,top.y,top.x+40,top.y)
            Thread.sleep(800)
            twoFingerEvent(MotionEvent.ACTION_MOVE,bottom.x-40,bottom.y,bottom.x+40,bottom.y)
            twoFingerEvent(MotionEvent.ACTION_POINTER_UP or (1 shl 8),bottom.x-40,bottom.y,bottom.x+40,bottom.y)
            Thread.sleep(300)
            scenario.onActivity{a->assertEquals("Second pass must remove remaining chosen blocks without re-adding",emptyList<Int>(),get(a,"order"))}
        }
    }
    private fun clearAutoProposal(scenario:ActivityScenario<MainActivity>){
        scenario.onActivity{a->
            fun walk(v:View):Boolean{if(v.contentDescription=="Tout désélectionner"){v.performClick();return true};if(v is ViewGroup)for(j in 0 until v.childCount)if(walk(v.getChildAt(j)))return true;return false}
            walk(a.window.decorView)
        }
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

    @Test fun groupedMenuListsBlockActionsWithDescriptions(){
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            scenario.onActivity{a->findButton(a.window.decorView,"Menu")!!.performClick()}
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity{a->
                val d=MainActivity::class.java.getDeclaredField("menuDialog").apply{isAccessible=true}.get(a)as android.app.Dialog
                val root=d.window!!.decorView
                var icons=0
                fun countIcons(v:View){if(v is TextView&&v.compoundDrawablesRelative[0] is ActionIcon)icons++;if(v is ViewGroup)for(k in 0 until v.childCount)countIcons(v.getChildAt(k))}
                countIcons(root);assertEquals("Every menu action keeps its own icon",11,icons)
                for(label in listOf("Document","Blocs de la page","Aide","Ouvrir un autre PDF","Ajouter un bloc","Voir l'ordre de lecture","Fusionner des blocs","Regrouper automatiquement","Propose des regroupements parmi les blocs choisis. Vous validez ceux à appliquer.","Réessayer la détection","Annuler le dernier regroupement","Confidentialité et aide","Licences des composants"))
                    assertTrue("Menu must show « $label »",findText(root,label))
            }
        }
    }
    @Test fun tutorialPracticesFourGesturesOnIsolatedExample(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        val current=File(c.filesDir,"current.pdf");val session=File(c.filesDir,"session.json")
        val previousPdf=if(current.exists())current.readBytes()else null
        val previousSession=if(session.exists())session.readBytes()else null
        val pdfSentinel="user-pdf-must-never-be-opened-by-tutorial".toByteArray()
        val sessionSentinel="{\"key\":\"user\",\"page\":5,\"selection\":[7,2]}".toByteArray()
        current.writeBytes(pdfSentinel);session.writeBytes(sessionSentinel)
        val prefs=c.getSharedPreferences("MainActivity",0).all.toMap()
        fun isolated(){assertArrayEquals(pdfSentinel,current.readBytes());assertArrayEquals(sessionSentinel,session.readBytes());assertEquals(prefs,c.getSharedPreferences("MainActivity",0).all)}
        fun field(a:TutorialActivity,n:String)=TutorialActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.get(a)
        try{ActivityScenario.launch<TutorialActivity>(Intent(c,TutorialActivity::class.java)).use{scenario->
            fun waitStep(expected:Int){val until=System.currentTimeMillis()+120000;var step=-1;while(System.currentTimeMillis()<until&&step!=expected){Thread.sleep(150);scenario.onActivity{a->step=field(a,"step")as Int}};assertEquals("Exercise step",expected,step)}
            fun shot(name:String){scenario.onActivity{a->val v=a.window.decorView;val bitmap=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(bitmap));File(File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()},name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}}
            fun point(index:Int):Pair<Float,Float>{var p=0f to 0f;scenario.onActivity{a->val v=field(a,"canvas")as PageCanvas;val b=v.zones[index].box;val m=PageCanvas::class.java.getDeclaredField("matrix").apply{isAccessible=true}.get(v)as android.graphics.Matrix;val xy=floatArrayOf((b.left+b.right)/2*v.image!!.width,(b.top+b.bottom)/2*v.image!!.height);m.mapPoints(xy);p=xy[0] to xy[1]};return p}
            var down=0L
            fun send(reader:Boolean,action:Int,vararg points:Pair<Float,Float>){scenario.onActivity{a->val now=android.os.SystemClock.uptimeMillis();if(action==MotionEvent.ACTION_DOWN)down=now;val pp=points.indices.map{MotionEvent.PointerProperties().apply{id=it;toolType=MotionEvent.TOOL_TYPE_FINGER}}.toTypedArray();val pc=points.map{p->MotionEvent.PointerCoords().apply{x=p.first;y=p.second;pressure=1f;size=1f}}.toTypedArray();val e=MotionEvent.obtain(down,now,action,points.size,pp,pc,0,0,1f,1f,0,0,0,0);(field(a,if(reader)"reader" else "canvas")as View).dispatchTouchEvent(e);e.recycle()}}
            waitStep(0);i.waitForIdleSync();shot("tutorial-1-select.png");isolated()
            val p1=point(0);val p2=point(1)
            send(false,MotionEvent.ACTION_DOWN,p1);Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout()+180L)
            for(n in 1..10){send(false,MotionEvent.ACTION_MOVE,(p1.first+(p2.first-p1.first)*n/10) to (p1.second+(p2.second-p1.second)*n/10));Thread.sleep(20)}
            send(false,MotionEvent.ACTION_UP,p2)
            waitStep(1);shot("tutorial-2-remove.png")
            val q=point(0);val d=14*c.resources.displayMetrics.density
            send(false,MotionEvent.ACTION_DOWN,(q.first-d) to q.second)
            send(false,MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),(q.first-d) to q.second,(q.first+d) to q.second)
            Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout()+180L)
            send(false,MotionEvent.ACTION_MOVE,(q.first-d) to (q.second+2),(q.first+d) to (q.second+2))
            send(false,MotionEvent.ACTION_POINTER_UP or (1 shl 8),(q.first-d) to (q.second+2),(q.first+d) to (q.second+2))
            send(false,MotionEvent.ACTION_UP,(q.first-d) to (q.second+2))
            waitStep(2);Thread.sleep(800);shot("tutorial-3-reading.png")
            var cx=0f;var cy=0f;var span=0f
            scenario.onActivity{a->val v=field(a,"reader")as View;assertEquals(View.VISIBLE,v.visibility);cx=v.width/2f;cy=v.height/2f;span=v.width*.44f}
            send(true,MotionEvent.ACTION_DOWN,cx-span/2 to cy)
            send(true,MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),cx-span/2 to cy,cx+span/2 to cy)
            for(n in 1..9){Thread.sleep(30);val half=span/2*(1+n*.07f);send(true,MotionEvent.ACTION_MOVE,cx-half to cy,cx+half to cy)}
            send(true,MotionEvent.ACTION_POINTER_UP or (1 shl 8),cx-span*.8f to cy,cx+span*.8f to cy)
            send(true,MotionEvent.ACTION_UP,cx-span*.8f to cy)
            // Pinch rebuilds tiles asynchronously. Capture after accepted rasters render,
            // but before the success message advances to the page exercise.
            Thread.sleep(650);shot("tutorial-3-reading-zoomed.png");waitStep(3);shot("tutorial-4-pages.png")
            var w=0f;var h=0f;scenario.onActivity{a->val v=field(a,"canvas")as View;w=v.width.toFloat();h=v.height.toFloat()}
            send(false,MotionEvent.ACTION_DOWN,w*.9f to h*.5f)
            for(n in 1..6){Thread.sleep(8);send(false,MotionEvent.ACTION_MOVE,w*(.9f-.8f*n/6) to h*.5f)}
            send(false,MotionEvent.ACTION_UP,w*.1f to h*.5f)
            Thread.sleep(250);shot("tutorial-4-page-changed.png");Thread.sleep(1800);isolated()
        }
        // Early exit and Activity recreation use the same isolated file/session path.
        ActivityScenario.launch<TutorialActivity>(Intent(c,TutorialActivity::class.java)).use{scenario->
            Thread.sleep(300);scenario.recreate();Thread.sleep(300);isolated()
            scenario.onActivity{a->a.onBackPressed()};Thread.sleep(300);isolated()
        }}finally{if(previousPdf==null)current.delete()else current.writeBytes(previousPdf);if(previousSession==null)session.delete()else session.writeBytes(previousSession)}
    }
    @Test fun groupedMenuProductCapture(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            scenario.onActivity{a->findButton(a.window.decorView,"Menu")!!.performClick()}
            i.waitForIdleSync();Thread.sleep(250)
            scenario.onActivity{a->
                val d=MainActivity::class.java.getDeclaredField("menuDialog").apply{isAccessible=true}.get(a)as android.app.Dialog
                val root=d.window!!.decorView
                assertTrue(findText(root,"Document"));assertTrue(findText(root,"Blocs de la page"));assertTrue(findText(root,"Aide"))
                val shot=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(shot))
                File(File(c.getExternalFilesDir(null),"screenshots").apply{mkdirs()},"grouped-menu-product.png").outputStream().use{shot.compress(Bitmap.CompressFormat.PNG,100,it)};shot.recycle();d.dismiss()
            }

        }
    }
    @Test fun autoTogglePillKnobFollowsState(){
        ActivityScenario.launch<MainActivity>(Intent(InstrumentationRegistry.getInstrumentation().targetContext,MainActivity::class.java)).use{scenario->
            scenario.onActivity{a->
                val toggle=findButton(a.window.decorView,"Auto")!!
                assertTrue("Toggle must be the pill view",toggle is AutoToggleButton)
                val pill=toggle as AutoToggleButton
                fun knobSide():Pair<Boolean,Boolean>{
                    val b=Bitmap.createBitmap(toggle.width,toggle.height,Bitmap.Config.ARGB_8888)
                    toggle.draw(Canvas(b))
                    val green=Color.rgb(50,109,93);val grey=Color.rgb(120,118,112)
                    fun close(c:Int,t:Int)=Math.abs(Color.red(c)-Color.red(t))<40&&Math.abs(Color.green(c)-Color.green(t))<40&&Math.abs(Color.blue(c)-Color.blue(t))<40
                    val right=close(b.getPixel(b.width-b.height/2,b.height/2),green)
                    val left=close(b.getPixel(b.height/2,b.height/2),grey)
                    b.recycle();return right to left
                }
                pill.on=true
                var sides=knobSide()
                assertTrue("Green knob must sit on the right when Auto is on",sides.first)
                assertFalse("Left side must not carry the grey knob when Auto is on",sides.second)
                pill.on=false
                sides=knobSide()
                assertTrue("Grey knob must sit on the left when Auto is off",sides.second)
                assertFalse("Right side must not carry the green knob when Auto is off",sides.first)
                assertEquals("Choix automatique désactivé",pill.contentDescription)
                pill.on=true
                assertEquals("Choix automatique activé",pill.contentDescription)
            }
            screenshot(scenario,"auto-pill-on-product")
            scenario.onActivity{a->(findButton(a.window.decorView,"Auto")as AutoToggleButton).on=false}
            screenshot(scenario,"auto-pill-off-product")

        }
    }
    @Test fun topBarFeedbackAndSelectionRevealArePresentationOnly(){
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        // No asynchronous resume from a preceding test may change this fixture.
        File(c.filesDir,"current.pdf").delete();SessionStore(c).clear()
        fun field(a:MainActivity,n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use{scenario->
            scenario.onActivity{a->
                field(a,"count").set(a,1);field(a,"detecting").set(a,true)
                MainActivity::class.java.getDeclaredMethod("updateControls").apply{isAccessible=true}.invoke(a)
                assertEquals(View.VISIBLE,(field(a,"detectionSpinner").get(a)as View).visibility)
                val open=field(a,"changePdfButton").get(a)as Button
                assertTrue(open.isEnabled);assertEquals("Changer de PDF",open.contentDescription)
            }
            screenshot(scenario,"topbar-detecting")
            scenario.onActivity{a->
                field(a,"detecting").set(a,false);MainActivity::class.java.getDeclaredMethod("updateControls").apply{isAccessible=true}.invoke(a)
                assertEquals(View.GONE,(field(a,"detectionSpinner").get(a)as View).visibility)
                val v=findCanvas(a.window.decorView)!!
                val image=Bitmap.createBitmap(700,1000,Bitmap.Config.ARGB_8888);val cv=Canvas(image);cv.drawColor(Color.WHITE)
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=20f}
                for(row in 0..25)cv.drawText("Un texte fictif pour suivre le parcours.",50f,100f+row*30,paint)
                field(a,"preview").set(a,image);v.image=image
                val fixtureZones=listOf(Zone(9,Box(.05f,.06f,.93f,.30f),Kind.TEXT),Zone(3,Box(.05f,.33f,.93f,.58f),Kind.TEXT),Zone(7,Box(.05f,.61f,.93f,.86f),Kind.TEXT))
                field(a,"zones").set(a,fixtureZones);field(a,"order").set(a,listOf(9,3,7));field(a,"pageReady").set(a,true);field(a,"busy").set(a,false)
                MainActivity::class.java.getDeclaredMethod("sync",Boolean::class.javaPrimitiveType).apply{isAccessible=true}.invoke(a,false)
                v.animateSelectionOrder()
                assertEquals(listOf(9,3,7),v.selection)
                assertEquals(0,PageCanvas::class.java.getDeclaredField("revealedSelections").apply{isAccessible=true}.getInt(v))
            }
            Thread.sleep(150)
            screenshot(scenario,"selection-reveal-first")
            scenario.onActivity{a->
                val v=findCanvas(a.window.decorView)!!
                val count=PageCanvas::class.java.getDeclaredField("revealedSelections").apply{isAccessible=true}.getInt(v)
                assertTrue("Reveal follows the existing order progressively",count in 1..3)
                assertEquals(listOf(9,3,7),v.selection)
            }
            Thread.sleep(150);screenshot(scenario,"selection-reveal-next")
            Thread.sleep(180);screenshot(scenario,"selection-reveal-complete")
            scenario.onActivity{a->
                val v=findCanvas(a.window.decorView)!!
                assertEquals(listOf(9,3,7),field(a,"order").get(a))
                // Clearing during a fresh reveal cannot be undone by a delayed frame.
                v.animateSelectionOrder();field(a,"order").set(a,emptyList<Int>())
                MainActivity::class.java.getDeclaredMethod("sync",Boolean::class.javaPrimitiveType).apply{isAccessible=true}.invoke(a,false)
            }
            Thread.sleep(550)
            scenario.onActivity{a->val v=findCanvas(a.window.decorView)!!;assertTrue(v.selection.isEmpty());assertEquals(Int.MAX_VALUE,PageCanvas::class.java.getDeclaredField("revealedSelections").apply{isAccessible=true}.getInt(v))}
            screenshot(scenario,"topbar-idle")
        }
    }
}
