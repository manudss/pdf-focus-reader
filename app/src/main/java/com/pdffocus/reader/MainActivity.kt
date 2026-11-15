package com.pdffocus.reader

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.pdf.PdfDocument as AndroidPdf
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import android.view.*
import android.widget.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.*

class MainActivity:Activity() {
    private val worker=Executors.newSingleThreadExecutor()
    private val detectionWorker=Executors.newSingleThreadExecutor()
    @Volatile private var detector:ZoneDetector?=null
    private var cleanReading=true
    private var ocrReading=false
    private var ocrLoading=false
    private var enhancedOcr=false
    @Volatile private var ocrRun=0
    private var chunkPlan=listOf<OcrChunk>()
    private val rawChunks=mutableMapOf<Int,List<OcrWord>>()
    private val improvedChunks=mutableMapOf<Int,List<OcrWord>>()
    private var repair:OcrEnhancer?=null
    private var ocrNotice=""

    private var recognizedWords:List<OcrWord>?=null
    private var scrollPosition=0
    private var documentLoading=false
    private var detecting=false
    private var pageReady=false
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var generation=0
    private var document:PdfDocument?=null // only accessed on worker
    private lateinit var store:SessionStore
    private lateinit var reader:ContinuousReaderView
    private lateinit var canvas:PageCanvas
    private lateinit var title:TextView
    private lateinit var info:TextView
    private lateinit var status:TextView
    private lateinit var controls:LinearLayout
    private lateinit var pages:LinearLayout
    private var count=0;private var page=0;private var zones=listOf<Zone>();private var order=listOf<Int>()
    private var preview:Bitmap?=null;private var readingBitmap:Bitmap?=null
    private var frameIndex=0;private var reading=false;private var busy=false
    private var merging=false
    private var mergeIds=setOf<Int>()
    private var pendingNew:Zone?=null
    private var adjusting:Int?=null
    private var adding=false;private var replaceId:Int?=null
    private var documentTitle="PDF Focus Reader"
    private val green=Color.rgb(57,126,108);private val ink=Color.rgb(37,31,33)
    private fun dp(v:Int)=(v*resources.displayMetrics.density).roundToInt()

    private fun loadRepair()=assets.open("tessdata_best/fra-words.txt").bufferedReader().use{OcrEnhancer(it.readLines())}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);store=SessionStore(this);cleanReading=getPreferences(0).getBoolean("cleanReading",true)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.WHITE)}
        root.setOnApplyWindowInsetsListener{v,insets->v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom);insets}
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),dp(3),dp(6),0)}
        val names=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        title=text("PDF Focus Reader",21,true).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END};info=text("Lecture privée, sans compte et hors ligne",12)
        names.addView(title);names.addView(info);header.addView(names,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("⋮",false){menu()},LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header)
        status=text("Ouvrez un PDF pour préparer votre parcours.",13).apply{setPadding(dp(16),dp(8),dp(16),dp(8));accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        root.addView(status)
        pages=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),0,dp(10),dp(4))};root.addView(pages)
        val viewport=android.widget.FrameLayout(this);root.addView(viewport,LinearLayout.LayoutParams(-1,0,1f))
        canvas=PageCanvas(this);viewport.addView(canvas,android.widget.FrameLayout.LayoutParams(-1,-1))
        reader=ContinuousReaderView(this).apply{visibility=View.GONE};viewport.addView(reader,android.widget.FrameLayout.LayoutParams(-1,-1))
        reader.onRequest={tile,revision->val token=generation;val targetPage=page;val cleanup=cleanReading&&!ocrReading&&tile.kind==Kind.TEXT;worker.execute{var image:Bitmap?=null;try{if(token==generation)image=document?.readingRegion(targetPage,tile.source,max(1,ceil(tile.rect.width()).toInt()),max(1,ceil(tile.rect.height()).toInt()),cleanup)}catch(_:Exception){};main.post{if(token==generation&&reading)reader.accept(tile,revision,image)else image?.recycle()}}}
        reader.onProgress={ordinal,position->scrollPosition=position;status.text=if(ocrReading)"OCR ${if(enhancedOcr)"amélioré" else "brut"} · ${if(ocrLoading)"suite en préparation" else "erreurs possibles"} · bloc ${ordinal+1} / ${order.size}${ocrNotice}" else "Lecture continue · bloc ${ordinal+1} / ${order.size} · glissez pour la suite";if(reading&&pageReady){main.removeCallbacks(saveProgress);main.postDelayed(saveProgress,400)}}
        controls=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),dp(6),dp(10),dp(6))};root.addView(controls)
        setContentView(root)
        detectionWorker.execute{try{val local=loadRepair();main.post{if(!isDestroyed)repair=local}}catch(_:Exception){}}
        canvas.onZoneTap={z->if(!busy&&!reading){if(merging){mergeIds=if(z.id in mergeIds)mergeIds-z.id else mergeIds+z.id;canvas.mergeIds=mergeIds;updateControls()}else{order=ReadingLayout.toggle(order,z.id);frameIndex=0;scrollPosition=0;sync()}}}
        canvas.onZoneLongPress={z->if(!busy&&!reading&&!merging&&adjusting==null)editZone(z)}
        canvas.onRectangle={b->
            if(!busy){
                val id=replaceId?:((zones.maxOfOrNull{it.id}?:0)+1)
                if(replaceId!=null){val zone=zones.first{it.id==id};replaceId=null;adding=false;canvas.drawMode=false;beginAdjustment(zone.copy(box=b))}
                else AlertDialog.Builder(this).setTitle("Type de bloc").setItems(arrayOf("Texte","Image")){_,which->
                    val zone=Zone(id,b,if(which==0)Kind.TEXT else Kind.IMAGE,manual=true);beginNewAdjustment(zone)
                }.setOnCancelListener{adding=false;canvas.drawMode=false;sync()}.show()

            }
        }
        canvas.onViewportReady={}
        updateControls();handleIntent(intent)
        if(intent?.action !in listOf(Intent.ACTION_VIEW,Intent.ACTION_SEND)&&File(filesDir,"current.pdf").exists())resumeLast()
    }
    private fun text(value:String,size:Int=14,bold:Boolean=false)=TextView(this).apply{text=value;textSize=size.toFloat();setTextColor(ink);if(bold)setTypeface(null,Typeface.BOLD);setPadding(0,dp(3),0,dp(3))}
    private fun button(label:String,primary:Boolean=false,action:()->Unit)=Button(this).apply{
        text=label;textSize=13f;isAllCaps=false;minHeight=dp(48);minimumHeight=dp(48);setTextColor(if(primary)Color.WHITE else green)
        if(primary)backgroundTintList=android.content.res.ColorStateList.valueOf(green)
        setOnClickListener{action()}
    }
    private fun row(vararg buttons:Button){val row=LinearLayout(this);buttons.forEach{row.addView(it,LinearLayout.LayoutParams(0,dp(50),1f))};controls.addView(row)}
    private fun updateControls(){
        controls.removeAllViews();controls.isEnabled=true;pages.removeAllViews()
        if(count==0){
            row(button("Ouvrir un PDF",true){pickPdf()})
            row(button("Essayer un exemple fictif"){demo()})
            controls.addView(text("Aucun fichier n'est envoyé sur Internet. L'analyse et les parcours restent sur cet appareil.",12))
            setEnabledRecursive(controls,!busy);return
        }
        if(merging){
            status.text="Fusion · ${mergeIds.size} blocs en violet · ordre conservé"
            controls.addView(text("Touchez les blocs à fusionner. Pointillé : aperçu. L'ordre de lecture est en pause.",12))
            val place=BlockMerge.position(order,mergeIds)
            controls.addView(text(if(place!=null)"Le nouveau bloc prendra la place ${place+1}, celle du premier bloc fusionné." else "Aucun bloc choisi ne figure encore dans le parcours.",12))
            row(button("Annuler"){finishMerge(false)},button("Fusionner",true){confirmMerge()}.apply{isEnabled=mergeIds.size>=2})
            return
        }
        if(adjusting!=null){
            controls.addView(text("Glisser dans le cadre pour le déplacer. Poignées : agrandir / réduire. Pincez pour zoomer.",12))
            row(button("Annuler"){finishAdjustment(false)},button("Valider",true){finishAdjustment(true)})
            return
        }
        if(reading){
            row(button("Vue page"){exitReader()},button("Texte −"){reader.zoomBy(.9f)},button("Texte +"){reader.zoomBy(1.1f)})
            row(button(if(!ocrReading&&!cleanReading)"Original ✓" else "Original"){selectMode(0)},button(if(!ocrReading&&cleanReading)"Nettoyé ✓" else "Nettoyé"){selectMode(1)},button(if(ocrReading)"Texte OCR ✓" else "Texte OCR"){selectMode(2)})
            if(ocrReading){row(button(if(!enhancedOcr)"OCR brut ✓" else "OCR brut"){enhancedOcr=false;prepareOcr(true)},button(if(enhancedOcr)"OCR amélioré ✓" else "OCR amélioré"){enhancedOcr=true;prepareOcr(true)})}

        }else if(adding){
            controls.addView(text("Tracez un rectangle autour du bloc. Pincez avant d'activer ce mode pour mieux cadrer.",12))
            row(button("Annuler le cadrage"){adding=false;replaceId=null;canvas.drawMode=false;sync()})
        }else{
            val undo=button("Annuler dernier"){order=order.dropLast(1);frameIndex=0;scrollPosition=0;sync()}.apply{isEnabled=order.isNotEmpty()&&!busy}
            val read=button("Lire (${order.size})",true){startReader()}.apply{isEnabled=order.isNotEmpty()&&!busy}
            row(undo,read)
            row(button("+ Bloc"){adding=true;canvas.drawMode=true;updateControls()},button("Effacer l'ordre"){order=emptyList();frameIndex=0;scrollPosition=0;sync()},button("Recentrer"){canvas.resetZoom()})
            controls.addView(text("Pincez pour zoomer · Appui long : corriger un bloc",11))
            val prev=button("‹ Page"){loadPage(page-1)}.apply{isEnabled=page>0&&!documentLoading}
            val next=button("Page ›"){loadPage(page+1)}.apply{isEnabled=page<count-1&&!documentLoading}
            pages.addView(prev,LinearLayout.LayoutParams(0,dp(44),1f))
            pages.addView(button("${page+1} / $count"){goToPage()},LinearLayout.LayoutParams(0,dp(44),1f))
            pages.addView(next,LinearLayout.LayoutParams(0,dp(44),1f))
        }
        setEnabledRecursive(controls,!busy);pages.visibility=View.VISIBLE
    }
    private fun setEnabledRecursive(v:View,enabled:Boolean){v.isEnabled=v.isEnabled&&enabled;if(v is android.view.ViewGroup)for(i in 0 until v.childCount)setEnabledRecursive(v.getChildAt(i),enabled)}
    private fun sync(save:Boolean=true){
        canvas.zones=zones;canvas.selection=order
        info.text="Page ${page+1} sur $count"
        title.text=documentTitle
        if(!reading&&!busy)status.text=if(adding)"Cadrage manuel" else "${zones.size} blocs proposés · ${order.size} choisis dans votre ordre"
        updateControls();if(save&&count>0)saveSession()
    }
    private fun saveSession(){try{store.put(page,PageSession(zones,order,frameIndex,scrollPosition))}catch(_:Exception){status.text="Lecture possible, mais sauvegarde indisponible."}}
    private fun pickPdf(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/pdf"},100)}
    private fun handleIntent(intent:Intent?){
        val uri=when(intent?.action){Intent.ACTION_VIEW->intent.data;Intent.ACTION_SEND->intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM);else->null}
        uri?.let{openUri(it)}
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);handleIntent(intent)}
    @Deprecated("Legacy callback is supported by minSdk")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==100&&resultCode==RESULT_OK)data?.data?.let{openUri(it)}}
    private fun openUri(uri:Uri){
        val name=try{contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else "Document PDF"}?:"Document PDF"}catch(_:Exception){"Document PDF"}
        openDocument(name){target->contentResolver.openInputStream(uri)?.use{input->target.outputStream().use{output->val b=ByteArray(64*1024);var bytes=0L;while(true){val n=input.read(b);if(n<0)break;bytes+=n;require(bytes<=250L*1024*1024){"Ce prototype accepte les PDF jusqu'à 250 Mo."};output.write(b,0,n)}}}?:error("Fichier inaccessible")}
    }
    private fun openDocument(name:String,write:(File)->Unit){
        reader.clear();reader.visibility=View.GONE;canvas.visibility=View.VISIBLE;detector?.cancel();detecting=false;pageReady=false;documentLoading=true
        val token=++generation;busy=true;reading=false;canvas.reading=false;canvas.image=preview;status.text="Ouverture du document…";updateControls()
        worker.execute{
            val temp=File(cacheDir,"import-$token.pdf")
            try{
                write(temp)
                require(temp.length()>4){"Fichier vide"}
                if(token!=generation)return@execute
                PdfDocument(temp).use{require(it.count>0){"Aucune page dans ce PDF"}}
                document?.close();document=null
                val current=File(filesDir,"current.pdf")
                java.nio.file.Files.move(temp.toPath(),current.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                document=PdfDocument(current)
                val total=document!!.count
                val digest=MessageDigest.getInstance("SHA-256");current.inputStream().use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;digest.update(b,0,n)}}
                val key=digest.digest().joinToString(""){"%02x".format(it)}
                main.post{if(token==generation&&!isDestroyed){documentLoading=false;store.key=key;page=store.page.coerceIn(0,total-1);count=total;documentTitle=name;getPreferences(0).edit().putString("title",name).apply();busy=false;val old=preview;preview=null;canvas.image=null;old?.recycle();readingBitmap?.recycle();readingBitmap=null;zones=emptyList();order=emptyList();loadPage(page)}}
            }catch(e:Exception){main.post{if(token==generation&&!isDestroyed){documentLoading=false;busy=false;status.text="Ouverture impossible : ${e.message?:"PDF protégé ou endommagé"}";updateControls()}}}finally{temp.delete()}
        }
    }
    private fun resumeLast(){
        documentLoading=true
        val token=++generation;busy=true;status.text="Reprise du dernier PDF…"
        worker.execute{try{document=PdfDocument(File(filesDir,"current.pdf"));val total=document!!.count;main.post{if(token==generation&&!isDestroyed){documentLoading=false;count=total;page=store.page.coerceIn(0,total-1);documentTitle=getPreferences(0).getString("title","Document PDF")!!;busy=false;loadPage(page)}}}catch(_:Exception){main.post{if(token==generation&&!isDestroyed){documentLoading=false;busy=false;status.text="Choisissez un PDF pour commencer.";updateControls()}}}}
    }
    private fun loadPage(index:Int,force:Boolean=false){
        if(index !in 0 until count)return
        if(pageReady)saveSession()
        adjusting=null;canvas.adjustingZone=null;canvas.editBox=null;detector?.cancel();ocrRun++;chunkPlan=emptyList();rawChunks.clear();improvedChunks.clear();ocrNotice="";detecting=false;pageReady=false;recognizedWords=null;ocrLoading=false
        val savedSession=if(force)null else store.get(index)
        reader.clear();reader.visibility=View.GONE;canvas.visibility=View.VISIBLE;scrollPosition=0
        val token=++generation;page=index;reading=false;adding=false;canvas.drawMode=false;canvas.reading=false
        busy=true;order=emptyList();zones=emptyList();canvas.zones=emptyList();canvas.selection=emptyList();status.text="Préparation de la page ${page+1}…";updateControls()
        worker.execute{
            try{
                val image=document!!.preview(index)
                if(token!=generation){image.recycle();return@execute}
                main.post{
                    if(token!=generation||isDestroyed){image.recycle();return@post}
                    val old=preview;preview=image;canvas.image=image;old?.recycle();readingBitmap?.recycle();readingBitmap=null
                    info.text="Page ${index+1} sur $count";title.text=documentTitle
                    if(savedSession!=null){zones=TextCoverage.bridgeColumnGaps(savedSession.zones);order=savedSession.order.filter{id->zones.any{it.id==id}};frameIndex=savedSession.frame;scrollPosition=savedSession.scroll;pageReady=true;busy=false;sync()}
                    else{
                        busy=true;detecting=true;status.text="Détection locale en cours. Vous pouvez changer de page.";updateControls()
                        val ocrImage=image.copy(Bitmap.Config.ARGB_8888,false)
                        detectionWorker.execute detection@{
                            try{
                                if(token!=generation)return@detection
                                val engine=ZoneDetector(applicationContext)
                                synchronized(this@MainActivity){if(token!=generation)return@detection;detector=engine}
                                val result=engine.detect(ocrImage){token!=generation}
                                main.post{if(token==generation&&!isDestroyed){detector=null;detecting=false;recognizedWords=engine.words;zones=TextCoverage.bridgeColumnGaps(result);order=emptyList();frameIndex=0;pageReady=true;busy=false;sync();if(zones.isEmpty())status.text="Aucun bloc trouvé. Utilisez + Bloc pour tracer une zone."}}
                            }catch(_:Exception){main.post{if(token==generation&&!isDestroyed){detector=null;detecting=false;busy=false;pageReady=true;sync(false);status.text="Analyse indisponible. Tracez vos blocs avec + Bloc."}}}
                            finally{ocrImage.recycle()}
                        }
                    }
                }
            }catch(e:Exception){main.post{if(token==generation&&!isDestroyed){busy=false;zones=emptyList();sync(false);status.text="Analyse indisponible. Tracez vos blocs avec + Bloc."}}}
        }
    }
    private val saveProgress=Runnable{if(reading&&pageReady)saveSession()}
    private fun startReader(){
        if(order.isEmpty()||busy)return
        val p=preview?:return;reading=true;canvas.visibility=View.GONE;reader.visibility=View.VISIBLE
        val chosen=ReadingLayout.ordered(zones,order).map{z->if(z.kind==Kind.TEXT&&!z.manual)z.copy(box=TextCoverage.safeTextBox(p,z.box))else z}
        reader.setDocument(chosen,p.width,p.height,scrollPosition);if(ocrReading)prepareOcr();updateControls()
    }
    private fun selectMode(mode:Int){
        ocrReading=mode==2;cleanReading=mode!=0
        getPreferences(0).edit().putBoolean("cleanReading",cleanReading).apply()
        if(ocrReading)prepareOcr(true)else {reader.showOcr(null);ocrRun++;detector?.cancel();ocrLoading=false}
        updateControls()
    }
    private fun plainText(words:List<OcrWord>):String = OcrFlow.textByZone(listOf(Zone(1,Box(0f,0f,1f,1f),Kind.TEXT)),words)[1].orEmpty()
    private fun displayOcr(force:Boolean=false,completed:Set<Int>?=null){
        val words=(if(enhancedOcr)rawChunks+improvedChunks else rawChunks).toMutableMap()
        val ready=completed?:(if(enhancedOcr)improvedChunks.keys else rawChunks.keys)
        var prefix=chunkPlan.takeWhile{it.kind==Kind.IMAGE||it.key in ready}
        if(enhancedOcr&&repair!=null){
            // Reserve an unfinished trailing word until the next source slice has been recognized.
            val tail=prefix.lastOrNull()
            if(ocrLoading&&tail!=null&&tail!=chunkPlan.lastOrNull()&&words[tail.key]?.lastOrNull()?.text?.endsWith('-')==true)prefix=prefix.dropLast(1)
            for((left,right)in prefix.zipWithNext())if(left.zoneId==right.zoneId&&left.kind==Kind.TEXT&&right.kind==Kind.TEXT){
                val a=words[left.key].orEmpty().toMutableList();val b=words[right.key].orEmpty().toMutableList()
                if(a.isNotEmpty()&&b.isNotEmpty()){
                    val last=a.last();val first=b.first();val close=first.line.top-last.line.bottom<last.line.height*.8f
                    val joint=if(close)repair!!.join(last.text,first.text)else null
                    if(joint!=null){a[a.lastIndex]=last.copy(text=joint);b.removeAt(0);words[left.key]=a;words[right.key]=b}
                }
            }
        }
        val texts=prefix.filter{it.kind==Kind.TEXT}.associate{c->c.key to if(enhancedOcr&&repair!=null)repair!!.text(words[c.key].orEmpty())else plainText(words[c.key].orEmpty())}
        if(reading&&ocrReading)reader.showOcrStream(prefix,texts,ocrLoading,force)
    }
    private fun prepareOcr(force:Boolean=false){
        val p=preview?:return
        if(force&&ocrLoading){ocrRun++;detector?.cancel();ocrLoading=false}
        val chosen=ReadingLayout.ordered(zones,order).map{if(it.kind==Kind.TEXT&&!it.manual)it.copy(box=TextCoverage.safeTextBox(p,it.box))else it}
        val newPlan=OcrChunks.plan(chosen,recognizedWords.orEmpty()){OcrRasterLines.chunks(p,it)}
        if(chunkPlan!=newPlan){ocrRun++;detector?.cancel();ocrLoading=false;chunkPlan=newPlan;rawChunks.clear();improvedChunks.clear();for(c in chunkPlan)if(c.words.isNotEmpty())rawChunks[c.key]=c.words}
        ocrNotice="";displayOcr(force);updateControls()
        if(ocrLoading)return
        val needed=chunkPlan.filter{it.kind==Kind.TEXT&&if(enhancedOcr)it.key !in improvedChunks else it.key !in rawChunks}
        if(needed.isEmpty())return
        val image=p.copy(Bitmap.Config.ARGB_8888,false);val token=generation;val run=++ocrRun;val improving=enhancedOcr;val rawSnapshot=rawChunks.toMap()
        val engine=ZoneDetector(applicationContext);detector=engine;ocrLoading=true;displayOcr()
        detectionWorker.execute{
            val cancelled={token!=generation||run!=ocrRun}
            try{
                val corrector=repair?:loadRepair()
                main.post{if(!cancelled())repair=corrector}
                val localRaw=rawSnapshot.toMutableMap()
                fun own(chunk:OcrChunk,result:List<OcrWord>)=result.filter{w->chosen.firstOrNull{z->z.box.contains((w.box.left+w.box.right)/2,(w.box.top+w.box.bottom)/2)||z.box.intersect(w.box)>.5f*w.box.area}?.id==chunk.zoneId}
                fun publishRaw(chunk:OcrChunk,result:List<OcrWord>){
                    val owned=own(chunk,result);localRaw[chunk.key]=owned
                    main.post{if(!cancelled()&&!isDestroyed){repair=corrector;rawChunks[chunk.key]=owned;if(!improving)displayOcr()}}
                }
                fun publishImproved(chunk:OcrChunk,result:List<OcrWord>){
                    val value=corrector.improve(localRaw[chunk.key].orEmpty(),own(chunk,result))
                    main.post{if(!cancelled()&&!isDestroyed){repair=corrector;improvedChunks[chunk.key]=value;displayOcr()}}
                }
                if(improving){
                    if(needed.all{it.key in localRaw})engine.recognizeChunks(needed,image,true,cancelled,::publishImproved)
                    else for(chunk in needed){
                        if(cancelled())break
                        if(chunk.key !in localRaw)engine.recognizeChunks(listOf(chunk),image,false,cancelled,::publishRaw)
                        if(!cancelled())engine.recognizeChunks(listOf(chunk),image,true,cancelled,::publishImproved)
                    }
                }else engine.recognizeChunks(needed.filter{it.key !in localRaw},image,false,cancelled,::publishRaw)
                main.post{if(!cancelled()&&!isDestroyed){ocrLoading=false;detector=null;ocrNotice=if(improving)" · déjà affiché inchangé" else "";displayOcr();if(ocrReading)status.text="OCR ${if(enhancedOcr)"amélioré" else "brut"} · erreurs possibles$ocrNotice"}}
            }catch(_:Exception){main.post{if(!cancelled()&&!isDestroyed){ocrLoading=false;detector=null;ocrNotice=" · suite indisponible, réessayez";displayOcr();status.text="OCR partiel. Réessayez ou revenez à Original."}}}
            finally{image.recycle()}
        }
    }
    private fun exitReader(){ocrRun++;detector?.cancel();ocrLoading=false;saveSession();reader.clear();reader.visibility=View.GONE;canvas.visibility=View.VISIBLE;++generation;reading=false;busy=false;canvas.reading=false;canvas.image=preview;readingBitmap?.recycle();readingBitmap=null;sync()}
    private fun beginMerge(z:Zone){merging=true;mergeIds=setOf(z.id);canvas.mergeMode=true;canvas.mergeIds=mergeIds;updateControls()}
    private fun confirmMerge(){
        val chosen=zones.filter{it.id in mergeIds};if(chosen.size<2)return
        val automatic=BlockMerge.position(order,mergeIds);val position=automatic?:order.size
        fun preview(kind:Kind){val result=BlockMerge.apply(zones,order,mergeIds,position,kind)
            val labels=result.order.mapIndexed{i,id->"${i+1} : "+if(id==result.merged.id)"nouveau bloc fusionné" else "ancien bloc ${order.indexOf(id)+1}"}
            AlertDialog.Builder(this).setTitle("Aperçu du nouvel ordre").setMessage((if(automatic==null)"Aucun bloc choisi ne figure dans le parcours. Ajouter le bloc fusionné à la fin ?\n\n" else "Place du premier bloc fusionné conservée.\n\n")+labels.joinToString("\n")).setPositiveButton("Fusionner"){_,_->zones=result.zones;order=result.order;finishMerge(true)}.setNegativeButton("Revenir",null).show()
        }
        val kinds=chosen.map{it.kind}.distinct()
        if(kinds.size==1)preview(kinds.single())else AlertDialog.Builder(this).setTitle("Contenu du bloc fusionné").setItems(arrayOf("Texte (vue OCR possible)","Image (conserve tous les pixels)")){_,which->preview(if(which==0)Kind.TEXT else Kind.IMAGE)}.setNegativeButton("Annuler",null).show()
    }
    private fun finishMerge(apply:Boolean){merging=false;mergeIds=emptySet();canvas.mergeMode=false;canvas.mergeIds=emptySet();if(apply){chunkPlan=emptyList();rawChunks.clear();improvedChunks.clear()};sync(apply)}
    private fun beginNewAdjustment(z:Zone){pendingNew=z;adding=false;canvas.drawMode=false;beginAdjustment(z)}
    private fun beginAdjustment(z:Zone){adjusting=z.id;canvas.adjustingZone=z.id;canvas.editBox=z.box;status.text="Ajuster le cadre · glisser et poignées";updateControls()}
    private fun finishAdjustment(apply:Boolean){
        val id=adjusting;val box=canvas.editBox;val pending=pendingNew
        if(apply&&id!=null&&box!=null){
            if(pending!=null){zones=zones+pending.copy(box=box,manual=true);order=order+pending.id}
            else zones=zones.map{if(it.id==id)it.copy(box=box,manual=true)else it}
            ocrRun++;detector?.cancel();ocrLoading=false;chunkPlan=emptyList();rawChunks.clear();improvedChunks.clear()
        }
        pendingNew=null;adjusting=null;canvas.adjustingZone=null;canvas.editBox=null;sync(apply)
    }
    private fun editZone(z:Zone){
        frameIndex=0
        AlertDialog.Builder(this).setTitle("Corriger le bloc").setItems(arrayOf("Redessiner le cadre","Traiter comme ${if(z.kind==Kind.TEXT)"image" else "texte"}","Couper en deux verticalement","Couper en deux horizontalement","Choisir les blocs à fusionner","Supprimer ce bloc","Ajuster le cadre")){_,which->
            when(which){
                0->{replaceId=z.id;adding=true;canvas.drawMode=true}
                1->{zones=zones.map{if(it.id==z.id)it.copy(kind=if(it.kind==Kind.TEXT)Kind.IMAGE else Kind.TEXT)else it}}
                2,3->{val b=z.box;val a:Box;val c:Box;if(which==2){val x=(b.left+b.right)/2;a=Box(b.left,b.top,x,b.bottom);c=Box(x,b.top,b.right,b.bottom)}else{val y=(b.top+b.bottom)/2;a=Box(b.left,b.top,b.right,y);c=Box(b.left,y,b.right,b.bottom)};val id=(zones.maxOfOrNull{it.id}?:0)+1;zones=zones.filterNot{it.id==z.id}+z.copy(box=a,manual=true)+Zone(id,c,z.kind,manual=true);val i=order.indexOf(z.id);if(i>=0)order=order.toMutableList().apply{add(i+1,id)}}
                4->{beginMerge(z)}

                5->{zones=zones.filterNot{it.id==z.id};order=order.filterNot{it==z.id}}
                6->{beginAdjustment(z)}
            };sync(adjusting==null&&!merging)
        }.setNegativeButton("Annuler",null).show()
    }
    private fun goToPage(){if(documentLoading)return;val input=EditText(this).apply{inputType=android.text.InputType.TYPE_CLASS_NUMBER;hint="1 à $count"};AlertDialog.Builder(this).setTitle("Aller à la page").setView(input).setPositiveButton("Ouvrir"){_,_->val p=input.text.toString().toIntOrNull();if(p!=null&&p in 1..count)loadPage(p-1)else toast("Numéro de page invalide")}.setNegativeButton("Annuler",null).show()}
    private fun menu(){val items=arrayOf("Ouvrir un autre PDF","Réessayer la détection","Confidentialité et aide","Supprimer le document local","Licences des composants")
        AlertDialog.Builder(this).setItems(items){_,which->when(which){0->pickPdf();1->if(count>0&&!busy)AlertDialog.Builder(this).setMessage("Remplacer les blocs et le parcours de cette page ?").setPositiveButton("Remplacer"){_,_->loadPage(page,true)}.setNegativeButton("Annuler",null).show();2->AlertDialog.Builder(this).setTitle("PDF Focus Reader · prototype").setMessage("Le PDF, les zones et les parcours restent sur cet appareil. Aucune permission Internet, publicité ou compte. Une copie privée du dernier PDF est conservée pour reprendre la lecture.\n\nLa détection peut manquer ou fusionner des zones. + Bloc permet de cadrer à la main ; un appui long modifie, divise ou fusionne les blocs. Pincez pour zoomer et faites glisser la page agrandie.\n\nLe zoom ne recrée pas les détails d'un scan flou. Ce prototype n'a pas encore été testé sur votre téléphone.").setPositiveButton("Fermer",null).show();3->clearDocument();4->showLicenses()}}.show()}
    private fun clearDocument(){if(busy){toast("Attendez la fin de l’analyse avant de supprimer.");return};AlertDialog.Builder(this).setTitle("Supprimer la copie locale ?").setMessage("Le fichier original n'est pas modifié. Le parcours enregistré sera effacé.").setPositiveButton("Supprimer"){_,_->++generation;main.removeCallbacks(saveProgress);reader.clear();reader.visibility=View.GONE;canvas.visibility=View.VISIBLE;pageReady=false;worker.execute{document?.close();document=null;File(filesDir,"current.pdf").delete()};count=0;store.clear();preview?.recycle();preview=null;readingBitmap?.recycle();readingBitmap=null;canvas.image=null;canvas.zones=emptyList();canvas.selection=emptyList();reading=false;busy=false;title.text="PDF Focus Reader";info.text="Sans compte et hors ligne";status.text="Document local supprimé.";updateControls()}.setNegativeButton("Annuler",null).show()}
    private fun showLicenses(){val names=assets.list("licenses").orEmpty();AlertDialog.Builder(this).setTitle("Licences").setItems(names){_,which->val view=TextView(this).apply{text=assets.open("licenses/"+names[which]).bufferedReader().use{it.readText()};setPadding(dp(16),dp(12),dp(16),dp(12));textSize=12f};val scroll=ScrollView(this).apply{addView(view)};AlertDialog.Builder(this).setTitle(names[which]).setView(scroll).setPositiveButton("Fermer",null).show()}.setNegativeButton("Fermer",null).show()}
    private fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
    /** Built-in copyright-safe fixture, also useful on a fresh offline install. */
    private fun demo(){openDocument("Journal de démonstration"){target->val pdf=AndroidPdf();val p=pdf.startPage(AndroidPdf.PageInfo.Builder(700,1000,1).create());val c=p.canvas;val paint=Paint(Paint.ANTI_ALIAS_FLAG);c.drawColor(Color.WHITE);paint.color=ink;paint.textSize=33f;paint.typeface=Typeface.DEFAULT_BOLD;c.drawText("Le journal des idées",40f,70f,paint);paint.textSize=18f;c.drawText("Un document fictif pour découvrir la lecture par blocs",40f,110f,paint);paint.typeface=Typeface.DEFAULT;paint.textSize=17f;val lines=listOf("Le plaisir de lire commence","par un écran confortable.","Choisissez les blocs dans","l'ordre qui vous convient.","Le texte reste celui du PDF.","Il n'est pas réécrit par l'OCR.","Faites glisser pour lire","sans perdre le fil.","Une photo peut se glisser","entre deux paragraphes.","La page d'origine reste","accessible à tout moment.")
        for(col in 0..1)for(i in 0 until 28)c.drawText(lines[i%lines.size],40f+col*330f,170f+i*25f,paint)
        paint.color=Color.rgb(84,153,130);c.drawRect(40f,880f,660f,960f,paint);paint.color=Color.WHITE;paint.textSize=22f;c.drawText("Illustration fictive · aucun document personnel",65f,927f,paint);pdf.finishPage(p);target.outputStream().use{pdf.writeTo(it)};pdf.close()}}
    @Deprecated("Activity back callback")
    override fun onBackPressed(){if(merging)finishMerge(false)else if(adjusting!=null)finishAdjustment(false)else if(reading)exitReader()else if(adding){adding=false;replaceId=null;canvas.drawMode=false;sync()}else super.onBackPressed()}
    override fun onDestroy(){main.removeCallbacks(saveProgress);if(pageReady)saveSession();reader.clear();++generation;detector?.cancel();detectionWorker.shutdown();super.onDestroy();worker.execute{document?.close();document=null};worker.shutdown()}
}
