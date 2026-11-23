package com.pdffocus.reader

import android.app.*
import android.content.*
import android.graphics.*
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
    private val secondChunks=mutableMapOf<Int,List<OcrEvidence>>()
    private var repair:OcrEnhancer?=null
    private var ocrNotice=""
    private var pageSummary:Dialog?=null
    private var menuDialog:Dialog?=null
    private var readingSettings=false
    private var currentOrdinal=0

    private var recognizedWords:List<OcrWord>?=null
    private val readingColors=mutableMapOf<Box,Int>()
    private fun readingColor(box:Box)=readingColors.getOrPut(box){preview?.let{ReadingPalette.background(it,box)}?:Color.WHITE}
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
    private lateinit var header:LinearLayout
    private lateinit var menuButton:Button
    private lateinit var autoButton:Button
    private lateinit var clearSelection:Button
    private lateinit var progress:ProgressBar
    private lateinit var pages:LinearLayout
    private lateinit var pageNav:LinearLayout
    private lateinit var pageSeek:SeekBar
    private lateinit var pageSeekLabel:TextView
    private lateinit var pageSeekTotal:TextView
    private var count=0;private var page=0;private var zones=listOf<Zone>();private var order=listOf<Int>()
    private var preview:Bitmap?=null;private var readingBitmap:Bitmap?=null
    private var frameIndex=0;private var reading=false;private var busy=false
    private var articleIds:List<Int>?=null
    private var articleReason=""
    private var merging=false
    private var moving=0
    private var mergeIds=setOf<Int>()
    private var pendingNew:Zone?=null
    private var adjusting:Int?=null
    private var editDialog:Dialog?=null
    private var adding=false;private var replaceId:Int?=null
    private var documentTitle="Document PDF"
    private var pageMode=PageMode.AUTO
    private lateinit var modeControls:LinearLayout
    private var modePreviewDialog:Dialog?=null
    private var autoSelect=true
    private var autoProposed=false
    private var orderTouched=false
    private val green=Color.rgb(50,109,93);private val ink=Color.rgb(37,31,33)
    // The callback the system invokes for a system back gesture/button on API 33+ (mandatory
    // on Android 16 / target 36). Kept in a field so tests can invoke the exact registered route.
    private var systemBackCallback:android.window.OnBackInvokedCallback?=null
    private fun dp(v:Int)=(v*resources.displayMetrics.density).roundToInt()

    private val spelling by lazy { FrenchSpelling(applicationContext) }
    private fun loadRepair()=OcrEnhancer(emptyList(),spelling::valid)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);store=SessionStore(this)
        // Android 16 (target 36) no longer calls onBackPressed: predictive back is enforced.
        // Register the same mode-leaving handling with the platform dispatcher on API 33+.
        if(Build.VERSION.SDK_INT>=33){val cb=android.window.OnBackInvokedCallback{if(!leaveCurrentMode())finish()};systemBackCallback=cb;onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,cb)}
        cleanReading=getPreferences(0).getBoolean("cleanReading",true);autoSelect=getPreferences(0).getBoolean("autoSelect",true)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(250,249,246))}
        root.setOnApplyWindowInsetsListener{v,insets->v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom);insets}
        header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setBackgroundColor(Color.WHITE);setPadding(dp(16),dp(4),dp(12),dp(2))}
        val names=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        title=text(applicationInfo.loadLabel(packageManager).toString(),18,true).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END};info=text("Lecture privée, sans compte et hors ligne",12)
        names.addView(title);names.addView(info);header.addView(names,LinearLayout.LayoutParams(0,-2,1f))
        changePdfButton=button("",false){pickPdf()}.apply{
            contentDescription="Changer de PDF";minWidth=0;minimumWidth=0;setPadding(0,0,0,0)
            compoundDrawablePadding=0
            val folder=object:android.graphics.drawable.Drawable(){
                private val p=Paint(Paint.ANTI_ALIAS_FLAG)
                override fun draw(c:Canvas){val r=bounds;val w=r.width().toFloat();val h=r.height().toFloat();c.save();c.translate(r.left.toFloat(),r.top.toFloat());p.color=green;p.style=Paint.Style.STROKE;p.strokeWidth=dp(2).toFloat();val path=Path().apply{moveTo(w*.08f,h*.28f);lineTo(w*.42f,h*.28f);lineTo(w*.52f,h*.14f);lineTo(w*.83f,h*.14f);lineTo(w*.92f,h*.34f);lineTo(w*.92f,h*.86f);lineTo(w*.08f,h*.86f);close()};c.drawPath(path,p);c.drawLine(w*.08f,h*.34f,w*.92f,h*.34f,p);c.restore()}
                override fun setAlpha(alpha:Int){p.alpha=alpha};override fun setColorFilter(filter:ColorFilter?){p.colorFilter=filter};@Deprecated("Drawable opacity") override fun getOpacity()=PixelFormat.TRANSLUCENT
            }.apply{setBounds(0,0,dp(24),dp(24))}
            setCompoundDrawables(folder,null,null,null);setPadding(dp(8),0,dp(8),0)
        }
        header.addView(changePdfButton,LinearLayout.LayoutParams(dp(42),dp(48)).apply{marginEnd=dp(4)})
        detectionSpinner=ProgressBar(this).apply{isIndeterminate=true;indeterminateTintList=android.content.res.ColorStateList.valueOf(green);contentDescription="Recherche des blocs en cours";visibility=View.GONE}
        header.addView(detectionSpinner,LinearLayout.LayoutParams(dp(24),dp(24)).apply{marginEnd=dp(8);gravity=Gravity.CENTER_VERTICAL})
        autoButton=AutoToggleButton(this).apply{
            setOnClickListener{if(pageMode==PageMode.MANUAL){toast("Mode Manuel : aucun bloc choisi automatiquement.");return@setOnClickListener};autoSelect=!autoSelect;getPreferences(0).edit().putBoolean("autoSelect",autoSelect).apply();styleAutoButton()}
        }
        styleAutoButton();header.addView(autoButton,LinearLayout.LayoutParams(dp(86),dp(38)).apply{marginEnd=dp(8);gravity=Gravity.CENTER_VERTICAL})
        menuButton=button("Menu",false){menu()};header.addView(menuButton,LinearLayout.LayoutParams(dp(72),dp(48)));root.addView(header)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=1000;progressTintList=android.content.res.ColorStateList.valueOf(green);progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(226,231,226));visibility=View.GONE};root.addView(progress,LinearLayout.LayoutParams(-1,dp(3)))
        status=text("Ouvrez un PDF pour préparer votre parcours.",13).apply{setPadding(dp(16),dp(4),dp(16),dp(4));accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        status.setBackgroundColor(Color.WHITE);root.addView(status,LinearLayout.LayoutParams(-1,-2))
        modeControls=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,dp(12),dp(4))};root.addView(modeControls)
        pages=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),0,dp(10),dp(4))};root.addView(pages)
        val viewport=android.widget.FrameLayout(this);root.addView(viewport,LinearLayout.LayoutParams(-1,0,1f))
        canvas=PageCanvas(this);viewport.addView(canvas,android.widget.FrameLayout.LayoutParams(-1,-1))
        // Clear-the-page cross: just above the preview's top-right corner, outside the PDF content.
        clearSelection=Button(this).apply{
            stateListAnimator=null;elevation=0f;translationZ=0f;backgroundTintList=null
            text="✕";textSize=13f;minHeight=dp(36);minimumHeight=dp(36);minWidth=0;minimumWidth=0;setPadding(0,0,0,0)
            setTextColor(Color.rgb(120,118,112));background=shape(Color.WHITE,18,Color.rgb(226,231,226))
            contentDescription="Tout désélectionner";visibility=View.GONE
            setOnClickListener{if(!busy&&!reading&&articleIds==null&&!merging&&moving==0&&adjusting==null&&!adding&&order.isNotEmpty()){order=emptyList();orderTouched=true;frameIndex=0;scrollPosition=0;sync()}}
        }
        viewport.addView(clearSelection,android.widget.FrameLayout.LayoutParams(dp(36),dp(36),Gravity.TOP or Gravity.END).apply{topMargin=dp(6);marginEnd=dp(6)})
        reader=ContinuousReaderView(this).apply{visibility=View.GONE};viewport.addView(reader,android.widget.FrameLayout.LayoutParams(-1,-1))
        // Page slider (validated mockup B): directly under the page preview, above the actions.
        pageNav=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),0,dp(16),dp(2))}
        pageSeekLabel=text("",12).apply{gravity=Gravity.CENTER}
        pageSeek=SeekBar(this).apply{progressTintList=android.content.res.ColorStateList.valueOf(green);progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(226,231,226));thumbTintList=android.content.res.ColorStateList.valueOf(green)}
        pageSeek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s:SeekBar?,p:Int,fromUser:Boolean){if(count>0)pageSeekLabel.text="Page ${p+1} / $count"}
            override fun onStartTrackingTouch(s:SeekBar?){}
            override fun onStopTrackingTouch(s:SeekBar?){s?.let{sliderPage(it.progress)}}
        })
        val seekRow=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        pageSeekTotal=text("",12)
        seekRow.addView(text("1",12));seekRow.addView(pageSeek,LinearLayout.LayoutParams(0,-2,1f));seekRow.addView(pageSeekTotal)
        pageNav.addView(pageSeekLabel);pageNav.addView(seekRow)
        pageNav.visibility=View.GONE;root.addView(pageNav)
        reader.onRequest={tile,revision->val token=generation;val targetPage=page;val cleanup=cleanReading&&!ocrReading&&tile.kind==Kind.TEXT;worker.execute{var image:Bitmap?=null;try{if(token==generation&&!reader.needsRender(tile,revision)){main.post{reader.skipRequest(tile,revision)};return@execute};if(token==generation)image=document?.readingRegion(targetPage,tile.source,max(1,ceil(tile.rect.width()).toInt()),max(1,ceil(tile.rect.height()).toInt()),cleanup)}catch(_:Exception){};main.post{if(token==generation&&reading)reader.accept(tile,revision,image)else image?.recycle()}}}
        reader.onOcrMark={key,id,_,y->showOcrComparison(key,id,y)}
        reader.onProgress={ordinal,position->currentOrdinal=ordinal;scrollPosition=position;if(reading){info.text="Page ${page+1} sur $count · bloc ${ordinal+1} sur ${order.size}";progress.progress=((ordinal+1)*1000/maxOf(1,order.size))};status.text=if(ocrReading)"OCR ${if(enhancedOcr)"amélioré" else "brut"} · erreurs possibles${if(ocrLoading)" · suite en cours" else ""}${ocrNotice}" else "Lecture continue · bloc ${ordinal+1} / ${order.size} · glissez pour la suite";if(reading&&pageReady){main.removeCallbacks(saveProgress);main.postDelayed(saveProgress,400)}}
        controls=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(10));background=shape(Color.WHITE,22);elevation=dp(5).toFloat()};root.addView(controls)
        setContentView(root)
        detectionWorker.execute{try{val local=loadRepair();main.post{if(!isDestroyed)repair=local}}catch(_:Exception){}}
        canvas.onZoneTap={z->if(!busy&&!reading){if(articleIds!=null){articleIds=ReadingLayout.toggle(articleIds!!,z.id);canvas.selection=articleIds!!;updateControls()}else if(merging){mergeIds=if(z.id in mergeIds)mergeIds-z.id else mergeIds+z.id;canvas.mergeIds=mergeIds;updateControls()}else if(moving!=0){if(z.id in order&&z.id!=moving){order=BlockMove.after(order,moving,z.id);orderTouched=true;frameIndex=0;scrollPosition=0;finishMove(true)}else updateControls()}else{order=ReadingLayout.toggle(order,z.id);orderTouched=true;frameIndex=0;scrollPosition=0;sync()}}}
        canvas.onZoneSweep={z->if(!busy&&!reading&&moving==0&&adjusting==null&&!adding){if(articleIds!=null){if(z.id !in articleIds!!){articleIds=articleIds!!+z.id;canvas.selection=articleIds!!;updateControls()}}else if(merging){mergeIds=mergeIds+z.id;canvas.mergeIds=mergeIds;updateControls()}else if(z.id !in order){order=order+z.id;orderTouched=true;frameIndex=0;scrollPosition=0;sync()}}}
        canvas.onZoneSweepRemove={z->if(!busy&&!reading&&moving==0&&adjusting==null&&!adding&&articleIds==null&&!merging){if(z.id in order){order=order-z.id;orderTouched=true;frameIndex=0;scrollPosition=0;sync()}}}
        canvas.onZoneLongPress={z->if(!busy&&!reading&&!merging&&moving==0&&articleIds==null&&adjusting==null)editZone(z)}
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
        canvas.onPageSwipe={dir->if(!documentLoading&&!reading){val target=page+dir;if(target in 0 until count)loadPage(target)}}
        reader.onReturnToPage={exitReader()}
        reader.onNextPage={if(page<count-1){exitReader();loadPage(page+1)}}
        updateControls();handleIntent(intent)
        val resumed=intent?.action !in listOf(Intent.ACTION_VIEW,Intent.ACTION_SEND)&&File(filesDir,"current.pdf").exists()
        if(resumed)resumeLast()
        // First launch only (no document to resume): the interactive walkthrough, practiced on an
        // isolated copy of the bundled example. Relaunchable from the menu. Skipped under Robolectric.
        if(!resumed&&!Build.FINGERPRINT.startsWith("robolectric",true)&&!getPreferences(0).getBoolean("tutorialSeen",false))main.post{startTutorial()}
    }
    private fun refreshPageNav(){
        pageNav.visibility=if(!reading&&count>1)View.VISIBLE else View.GONE
        if(count>0){pageSeekTotal.text=count.toString();if(pageSeek.max!=count-1)pageSeek.max=count-1;if(pageSeek.progress!=page)pageSeek.progress=page;pageSeekLabel.text="Page ${page+1} / $count"}
        pageSeek.isEnabled=!documentLoading
    }
    internal fun sliderPage(p:Int){if(!documentLoading&&p in 0 until count&&p!=page)loadPage(p)}
    private fun text(value:String,size:Int=14,bold:Boolean=false)=TextView(this).apply{text=value;textSize=size.toFloat();setTextColor(ink);if(bold)setTypeface(null,Typeface.BOLD);setPadding(0,dp(3),0,dp(3))}
    private fun shape(color:Int,radius:Int,stroke:Int=Color.TRANSPARENT)=android.graphics.drawable.GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat();if(stroke!=Color.TRANSPARENT)setStroke(dp(1),stroke)}
    private fun iconName(label:String):String=when{
        label.startsWith("Vue page")||label.startsWith("‹")||label=="Précédente"->"back"
        label.startsWith("Page ›")||label=="Suivante"->"next"
        label.startsWith("Texte −")||label=="Dézoomer"->"minus"
        label.startsWith("Texte +")||label=="Zoomer"||label.startsWith("Ajouter")->"add"
        label.startsWith("Valider")||label.startsWith("Confirmer")->"check"
        label.startsWith("Annuler dernier")->"undo"
        label.startsWith("Annuler")||label.startsWith("Effacer")||label=="Fermer"->"close"
        label.startsWith("Lire")||label.startsWith("Essayer")->"book"
        label.startsWith("Original")||label.startsWith("Ouvrir")->"document"
        label.startsWith("Nettoyé")||label.startsWith("OCR amélioré")->"clean"
        label.startsWith("Texte OCR")||label.startsWith("OCR brut")->"text"
        label.startsWith("Réglages")->"settings"
        label.startsWith("Partager")->"share"
        label.startsWith("Ordre")->"order"
        label.startsWith("Fusion")->"merge"
        label.startsWith("Regrouper")->"group"
        label=="À gauche"||label=="Monter"->"minus"
        label=="À droite"||label=="Descendre"->"add"
        label.startsWith("Recentrer")->"center"
        else->"more"
    }
    private fun button(label:String,primary:Boolean=false,action:()->Unit)=Button(this).apply{
        stateListAnimator=null;elevation=0f;translationZ=0f;backgroundTintList=null
        text=label;textSize=13f;isAllCaps=false;maxLines=1;setAutoSizeTextTypeUniformWithConfiguration(9,13,1,android.util.TypedValue.COMPLEX_UNIT_SP);minHeight=dp(48);minimumHeight=dp(48);minWidth=0;minimumWidth=0;setPadding(dp(9),0,dp(9),0)
        val selected=label.endsWith("✓");val color=if(primary)Color.WHITE else green
        setTextColor(color);typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        background=shape(if(primary)green else if(selected)Color.rgb(234,241,237)else Color.WHITE,12,if(!primary&&!selected)Color.rgb(226,231,226)else Color.TRANSPARENT)
        val icon=ActionIcon(iconName(label),color).apply{setBounds(0,0,dp(18),dp(18))}
        // Page count is a jump action with its own explicit label; every icon action retains its text.
        if(!label.firstOrNull().let{it!=null&&it.isDigit()})setCompoundDrawablesRelative(icon,null,null,null)
        compoundDrawablePadding=dp(6);contentDescription=label.replace("✓","sélectionné")
        setOnClickListener{action()}
    }
    private fun row(vararg buttons:Button){val row=LinearLayout(this).apply{isBaselineAligned=false};buttons.forEachIndexed{i,b->if(resources.configuration.screenWidthDp<=360){b.setCompoundDrawablesRelative(null,null,null,null);b.compoundDrawablePadding=0;b.setPadding(dp(3),0,dp(3),0)};row.addView(b,LinearLayout.LayoutParams(0,dp(48),1f).apply{if(i>0)marginStart=dp(6)})};controls.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(6)})}
    private fun showOrder(){
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(8))}
        val dialog=AlertDialog.Builder(this).setTitle("Votre parcours · ${order.size} blocs").setView(panel).create()
        fun action(label:String,run:()->Unit){panel.addView(button(label){run();dialog.dismiss()},LinearLayout.LayoutParams(-1,dp(52)))}
        panel.addView(text("Les numéros sur la page suivent vos appuis. La fusion utilise une sélection indépendante.",13))
        action("Annuler dernier"){order=order.dropLast(1);orderTouched=true;frameIndex=0;scrollPosition=0;sync()}
        action("Effacer l'ordre"){order=emptyList();orderTouched=true;frameIndex=0;scrollPosition=0;sync()}
        action("Recentrer"){canvas.resetZoom()};action("Fermer"){};dialog.show()
    }
    private fun modeLabel(mode:PageMode)=when(mode){PageMode.AUTO->"Auto";PageMode.COMIC->"BD";PageMode.MANGA->"Manga";PageMode.JOURNAL->"Journal";PageMode.BOOK->"Livre";PageMode.MANUAL->"Manuel"}
    private fun updateModeControls(){
        modeControls.removeAllViews();modeControls.visibility=if(reading||adding||merging||moving!=0||adjusting!=null||articleIds!=null)View.GONE else View.VISIBLE
        val strip=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        for(mode in listOf(PageMode.AUTO,PageMode.COMIC,PageMode.MANGA,PageMode.JOURNAL,PageMode.BOOK,PageMode.MANUAL)){
            val b=button(modeLabel(mode)){choosePageMode(mode)}.apply{
                setCompoundDrawablesRelative(null,null,null,null);setPadding(dp(3),0,dp(3),0);isSelected=pageMode==mode
                background=shape(if(isSelected)Color.rgb(226,239,231)else Color.WHITE,8,if(isSelected)green else Color.rgb(226,231,226));contentDescription="Mode ${modeLabel(mode)}${if(isSelected)", choisi" else ""}"
            }
            strip.addView(b,LinearLayout.LayoutParams(0,dp(44),1f).apply{marginEnd=dp(3)})
        };modeControls.addView(strip)
        modeControls.addView(text(if(pageMode==PageMode.MANUAL)"Manuel : aucun choix auto · T Texte · ▧ Image · ▣ Case" else "T Texte · ▧ Image · ▣ Case · ◇ Complément",10).apply{setPadding(dp(3),dp(2),0,0)})
    }
    private fun choosePageMode(mode:PageMode){
        if(mode==pageMode)return
        if(reading||adding||merging||moving!=0||adjusting!=null)return
        if(mode==PageMode.MANUAL){pageMode=mode;autoProposed=false;orderTouched=true;sync(pageReady||(!busy&&!detecting));return}
        if(count==0){pageMode=mode;updateControls();return}
        if(busy||detecting){pageMode=mode;loadPage(page,true);return}
        val bitmap=preview?:return;val token=generation;val cancelled=java.util.concurrent.atomic.AtomicBoolean(false)
        val dialog=Dialog(this);modePreviewDialog=dialog
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(12));background=shape(Color.rgb(250,249,246),18)}
        panel.addView(text("Préparer en mode ${modeLabel(mode)}",21,true));val hint=text("Aperçu en cours. Vos blocs et votre ordre sont conservés tant que vous n'appliquez pas.",13);panel.addView(hint)
        val sample=PageCanvas(this).apply{image=bitmap};panel.addView(sample,LinearLayout.LayoutParams(-1,0,1f))
        var proposed=listOf<Zone>();var proposedOrder=listOf<Int>();var words:List<OcrWord> = emptyList()
        val actions=LinearLayout(this)
        val apply=button("Appliquer",true){
            if(token!=generation)return@button
            zones=proposed;order=proposedOrder;recognizedWords=words;pageMode=mode;autoProposed=order.isNotEmpty();orderTouched=false;frameIndex=0;scrollPosition=0;chunkPlan=emptyList();rawChunks.clear();improvedChunks.clear();secondChunks.clear();sync();canvas.animateSelectionOrder();dialog.dismiss()
        }.apply{isEnabled=false}
        actions.addView(button("Annuler"){dialog.dismiss()},LinearLayout.LayoutParams(0,dp(48),1f));actions.addView(apply,LinearLayout.LayoutParams(0,dp(48),1f));panel.addView(actions)
        dialog.setContentView(panel);dialog.setOnDismissListener{cancelled.set(true);if(modePreviewDialog===dialog)modePreviewDialog=null;sample.image=null};dialog.show();dialog.window?.apply{setBackgroundDrawableResource(android.R.color.transparent);setLayout(-1,(resources.displayMetrics.heightPixels*.9f).toInt())}
        val copy=bitmap.copy(Bitmap.Config.ARGB_8888,false)
        detectionWorker.execute{try{val engine=ZoneDetector(applicationContext);val found=engine.detect(copy,mode){cancelled.get()||token!=generation};val next=if(mode==PageMode.BOOK)found else TextCoverage.bridgeColumnGaps(found);val ids=if(mode!=PageMode.AUTO||autoSelect)PageReadingOrder.propose(next,mode)else emptyList();words=engine.words
            main.post{if(!cancelled.get()&&token==generation&&!isDestroyed){proposed=next;proposedOrder=ids;sample.zones=next;sample.selection=ids;hint.text=when(mode){PageMode.COMIC,PageMode.MANGA->"Toute la page est couverte. Vérifiez les coupes, surtout sans cadres nets. Appliquer remplacera les blocs et le parcours de cette page.";PageMode.BOOK->"Paragraphes proposés dans l'ordre. Appliquer remplacera les blocs et le parcours de cette page.";else->"${next.size} blocs proposés. Appliquer remplacera les blocs et le parcours de cette page."};apply.isEnabled=next.isNotEmpty()}}
        }catch(_:Exception){main.post{if(!cancelled.get())hint.text="Préparation indisponible. Vos blocs et votre ordre sont inchangés."}}finally{copy.recycle()}}
    }
    private fun updateControls(){
        updateModeControls()
        detectionSpinner.visibility=if(detecting&&!reading)View.VISIBLE else View.GONE
        changePdfButton.isEnabled=!documentLoading
        controls.removeAllViews();controls.isEnabled=true;pages.removeAllViews();pages.visibility=if(reading||adjusting!=null||merging||moving!=0||articleIds!=null||adding)View.GONE else View.VISIBLE
        autoButton.visibility=if(pageMode==PageMode.AUTO&&count>0&&!reading&&articleIds==null&&!merging&&moving==0&&adjusting==null&&!adding)View.VISIBLE else View.GONE
        clearSelection.visibility=if(count>0&&!reading&&order.isNotEmpty()&&articleIds==null&&!merging&&moving==0&&adjusting==null&&!adding)View.VISIBLE else View.GONE
        title.text=when{reading&&ocrReading->"Texte OCR";reading->"Lecture";articleIds!=null->"Article proposé";merging->"Fusionner des blocs";moving!=0->"Déplacer un bloc";adjusting!=null->"Ajuster le cadre";count>0->"Préparer ma lecture";else->applicationInfo.loadLabel(packageManager).toString()}
        info.text=if(count>0)"Page ${page+1} sur $count" else "Lecture privée, hors ligne"
        title.typeface=Typeface.create(if(reading)"sans-serif-medium" else "serif",Typeface.NORMAL)
        title.textSize=if(reading)18f else 21f
        status.visibility=if(reading&&!ocrReading)View.GONE else View.VISIBLE
        status.textSize=if(reading)11f else 13f
        status.setPadding(dp(16),dp(if(reading)1 else 4),dp(16),dp(if(reading)1 else 4))
        if(reading&&ocrReading)status.text="OCR ${if(enhancedOcr)"amélioré" else "brut"} · erreurs possibles"
        progress.visibility=if(reading)View.VISIBLE else View.GONE
        if(reading){info.text="Page ${page+1} sur $count · bloc ${currentOrdinal+1} sur ${order.size}";progress.progress=(currentOrdinal+1)*1000/maxOf(1,order.size)}
        menuButton.background=if(reading)null else shape(Color.WHITE,12,Color.rgb(226,231,226))
        controls.setPadding(dp(if(reading)12 else 12),dp(if(reading)0 else 8),dp(12),dp(if(reading)6 else 10))
        controls.background=shape(Color.WHITE,if(reading)0 else 22)
        controls.elevation=dp(if(reading)1 else 5).toFloat()
        status.maxLines=if(reading)1 else 3;status.ellipsize=if(reading)android.text.TextUtils.TruncateAt.END else null
        if(count==0){
            row(button("Ouvrir un PDF",true){pickPdf()})
            row(button("Essayer un exemple fictif"){demo()})
            controls.addView(text("Aucun fichier n'est envoyé sur Internet. L'analyse et les parcours restent sur cet appareil.",12))
            setEnabledRecursive(controls,!busy);return
        }
        if(articleIds!=null){
            val ids=articleIds!!;status.text="Article proposé · ${ids.size} blocs · à vérifier"
            controls.addView(text(articleReason,12))
            controls.addView(text("Touchez pour ajouter/retirer. Rien n'est appliqué au parcours.",12))
            row(button("Modifier l'ordre"){editArticleOrder()},button("Annuler"){finishArticle(false)})
            row(button("Ajouter au parcours (${ids.count{it !in order}})",true){finishArticle(true)}.apply{isEnabled=ids.isNotEmpty()})
            return
        }
        if(merging){
            status.text="Fusion · ${mergeIds.size} blocs en violet · ordre conservé"
            controls.addView(text("Touchez ou maintenez puis glissez pour choisir. Pointillé : aperçu. L'ordre de lecture est en pause.",12))
            val place=BlockMerge.position(order,mergeIds)
            controls.addView(text(if(place!=null)"Le nouveau bloc prendra la place ${place+1}, celle du premier bloc fusionné." else "Aucun bloc choisi ne figure encore dans le parcours.",12))
            row(button("Annuler"){finishMerge(false)},button("Fusionner",true){confirmMerge()}.apply{isEnabled=mergeIds.size>=2})
            return
        }
        if(moving!=0){
            status.text="Déplacement · bloc ${order.indexOf(moving)+1} en pointillés · touchez le bloc après lequel il passera"
            controls.addView(text("Le parcours passe en violet. Seul l'ordre de lecture change, les cadres restent en place.",12))
            row(button("Annuler"){finishMove(false)})
            return
        }
        if(adjusting!=null){
            controls.addView(text("Déplacez le cadre ou ses poignées. Pincez pour zoomer.",12))
            row(button("Dézoomer"){canvas.zoomBy(.8f)},button("Zoomer"){canvas.zoomBy(1.25f)})
            row(button("Annuler"){finishAdjustment(false)},button("Valider",true){finishAdjustment(true)})
            return
        }
        if(reading){
            fun flat(label:String,action:()->Unit)=button(label,false,action).apply{background=null}
            fun compactRow(vararg buttons:Button){val r=LinearLayout(this).apply{isBaselineAligned=false};buttons.forEach{r.addView(it,LinearLayout.LayoutParams(0,dp(48),1f))};controls.addView(r,LinearLayout.LayoutParams(-1,dp(48)))}
            compactRow(flat("Vue page"){exitReader()},flat("Pages"){showPageSummary()},flat(if(readingSettings)"Fermer" else "Réglages"){readingSettings=!readingSettings;updateControls()})
            fun segment(vararg buttons:Button){
                val r=LinearLayout(this).apply{isBaselineAligned=false;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(3),dp(3),dp(3),dp(3));background=shape(Color.rgb(244,245,241),12);clipToOutline=true;clipChildren=true;clipToPadding=true}
                buttons.forEach{b->val active=b.text.toString().endsWith("✓");b.isSelected=active;b.stateListAnimator=null;b.elevation=0f;b.translationZ=0f
                    val surface=if(active)shape(Color.WHITE,9)else shape(Color.TRANSPARENT,9)
                    b.background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(25,50,109,93)),surface,shape(Color.WHITE,9))
                    b.setPadding(dp(5),0,dp(5),0);b.compoundDrawablePadding=dp(4)
                    r.addView(b,LinearLayout.LayoutParams(0,dp(48),1f))
                }
                controls.addView(r,LinearLayout.LayoutParams(-1,dp(54)).apply{bottomMargin=dp(6)})
            }
            segment(button(if(!ocrReading&&!cleanReading)"Original ✓" else "Original"){selectMode(0)},button(if(!ocrReading&&cleanReading)"Nettoyé ✓" else "Nettoyé"){selectMode(1)},button(if(ocrReading)"Texte OCR ✓" else "Texte OCR"){selectMode(2)})
            if(readingSettings){
                if(ocrReading){
                    segment(button(if(!enhancedOcr)"OCR brut ✓" else "OCR brut"){enhancedOcr=false;prepareOcr(true)},button(if(enhancedOcr)"OCR amélioré ✓" else "OCR amélioré"){enhancedOcr=true;prepareOcr(true)})
                    compactRow(flat("Texte −"){reader.zoomBy(.9f)},flat("Texte +"){reader.zoomBy(1.1f)})
                }else controls.addView(text("Pincez pour zoomer · Glissez pour lire",12))
            }

        }else if(adding){
            controls.addView(text("Tracez un rectangle autour du bloc. Pincez avant d'activer ce mode pour mieux cadrer.",12))
            row(button("Annuler le cadrage"){adding=false;replaceId=null;canvas.drawMode=false;sync()})
        }else{
            val read=button("Lire (${order.size})",true){startReader()}.apply{isEnabled=order.isNotEmpty()&&!busy}
            row(button("Ajouter"){adding=true;canvas.drawMode=true;updateControls()},button("Ordre"){showOrder()},button("Fusionner"){(order.firstOrNull()?.let{id->zones.find{it.id==id}}?:zones.firstOrNull())?.let{beginMerge(it)}}.apply{isEnabled=zones.isNotEmpty()},button("Regrouper"){showAutoGroups()}.apply{isEnabled=zones.isNotEmpty()})
            row(read)
            controls.addView(text("Maintenez-glissez pour choisir · Deux doigts pour retirer · Double tap pour corriger",11))
            val prev=button("Précédente"){loadPage(page-1)}.apply{isEnabled=page>0&&!documentLoading}
            val next=button("Suivante"){loadPage(page+1)}.apply{isEnabled=page<count-1&&!documentLoading}
            pages.addView(prev,LinearLayout.LayoutParams(0,dp(44),1f))
            pages.addView(button("Pages ${page+1}/$count"){showPageSummary()},LinearLayout.LayoutParams(0,dp(44),1f))
            pages.addView(next,LinearLayout.LayoutParams(0,dp(44),1f))
        }
        setEnabledRecursive(controls,!busy)
        refreshPageNav()
    }
    private lateinit var detectionSpinner:ProgressBar
    private lateinit var changePdfButton:Button
    private fun styleAutoButton(){(autoButton as AutoToggleButton).on=autoSelect}
    private fun setEnabledRecursive(v:View,enabled:Boolean){v.isEnabled=v.isEnabled&&enabled;if(v is android.view.ViewGroup)for(i in 0 until v.childCount)setEnabledRecursive(v.getChildAt(i),enabled)}
    private fun sync(save:Boolean=true){
        canvas.zones=zones;canvas.selection=articleIds?:order
        info.text="Page ${page+1} sur $count"
        title.text=documentTitle
        if(!reading&&!busy)status.text=if(adding)"Cadrage manuel" else "${zones.size} blocs proposés · ${order.size} choisis ${if(autoProposed&&!orderTouched)"automatiquement" else "dans votre ordre"}"
        updateControls();if(save&&count>0)saveSession()
    }
    private fun saveSession(){try{store.put(page,PageSession(zones,order,frameIndex,scrollPosition,pageMode))}catch(_:Exception){status.text="Lecture possible, mais sauvegarde indisponible."}}
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
        pageSummary?.dismiss()
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
        pageSummary?.dismiss()
        articleIds=null;articleReason="";readingColors.clear()
        if(index !in 0 until count)return
        if(pageReady)saveSession()
        canvas.finishSelectionReveal()
        pendingNew=null;merging=false;mergeIds=emptySet();canvas.mergeMode=false;canvas.mergeIds=emptySet();moving=0;canvas.moveMode=false;canvas.moveId=0;adjusting=null;canvas.adjustingZone=null;canvas.editBox=null;detector?.cancel();ocrRun++;chunkPlan=emptyList();rawChunks.clear();improvedChunks.clear();secondChunks.clear();ocrNotice="";detecting=false;pageReady=false;recognizedWords=null;ocrLoading=false;autoProposed=false;orderTouched=false
        val savedSession=if(force)null else store.get(index)
        val requestedMode=pageMode
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
                    if(savedSession!=null){if(pageMode==requestedMode)pageMode=savedSession.mode;zones=if(savedSession.mode==PageMode.BOOK)savedSession.zones else TextCoverage.bridgeColumnGaps(savedSession.zones);order=savedSession.order.filter{id->zones.any{it.id==id}};frameIndex=savedSession.frame;scrollPosition=savedSession.scroll;pageReady=true;busy=false;sync()}
                    else{
                        busy=true;detecting=true;status.text="Détection locale en cours. Vous pouvez changer de page.";updateControls()
                        val ocrImage=image.copy(Bitmap.Config.ARGB_8888,false)
                        detectionWorker.execute detection@{
                            try{
                                if(token!=generation)return@detection
                                val engine=ZoneDetector(applicationContext)
                                synchronized(this@MainActivity){if(token!=generation)return@detection;detector=engine}
                                val result=engine.detect(ocrImage,requestedMode){token!=generation}
                                main.post{if(token==generation&&!isDestroyed){detector=null;detecting=false;recognizedWords=engine.words;zones=if(requestedMode==PageMode.BOOK)result else TextCoverage.bridgeColumnGaps(result);order=if((requestedMode!=PageMode.AUTO||autoSelect)&&requestedMode!=PageMode.MANUAL&&pageMode!=PageMode.MANUAL)PageReadingOrder.propose(zones,requestedMode)else emptyList();autoProposed=order.isNotEmpty();frameIndex=0;pageReady=true;busy=false;sync();if(autoProposed)canvas.animateSelectionOrder();if(zones.isEmpty())status.text="Aucun bloc trouvé. Utilisez + Bloc pour tracer une zone."}}
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
        val p=preview?:return;readingSettings=false;currentOrdinal=frameIndex;reading=true;canvas.visibility=View.GONE;reader.visibility=View.VISIBLE
        reader.endTitle="Fin de la page ${page+1}";reader.hasNextPage=page<count-1
        val chosen=BlockSeams.align(p,ReadingLayout.ordered(zones,order).map{z->if(z.kind==Kind.TEXT&&!z.manual)z.copy(box=TextCoverage.safeTextBox(p,z.box))else z})
        reader.setDocument(chosen.map{ReadingWhitespace.trim(p,it)},p.width,p.height,scrollPosition,chosen.associate{it.id to readingColor(it.box)},when{pageMode==PageMode.MANGA->-1;pageMode==PageMode.COMIC||pageMode==PageMode.AUTO&&chosen.any{it.panel}->1;else->0});if(ocrReading)prepareOcr();updateControls()
    }
    private fun selectMode(mode:Int){
        ocrReading=mode==2;cleanReading=mode!=0
        getPreferences(0).edit().putBoolean("cleanReading",cleanReading).apply()
        if(ocrReading)prepareOcr(true)else {preview?.let{reader.setBackgrounds(ReadingLayout.ordered(zones,order).associate{it.id to readingColor(it.box)})};reader.showOcr(null);ocrRun++;detector?.cancel();ocrLoading=false}
        updateControls()
    }
    private fun plainText(words:List<OcrWord>):String = OcrFlow.textByZone(listOf(Zone(1,Box(0f,0f,1f,1f),Kind.TEXT)),words)[1].orEmpty()
    private fun displayOcr(force:Boolean=false,completed:Set<Int>?=null){
        val words=(if(enhancedOcr)rawChunks+improvedChunks else rawChunks).toMutableMap()
        val ready=completed?:(if(enhancedOcr)improvedChunks.keys else rawChunks.keys)
        var prefix=chunkPlan.takeWhile{it.kind==Kind.IMAGE||it.key in ready}
        if(enhancedOcr&&ocrLoading){
            // One selected block is published atomically: future slices must not rewrite its
            // paragraph boundaries, half a word or a saved raw/corrected choice while reading.
            val last=prefix.lastOrNull()
            if(last!=null&&chunkPlan.drop(prefix.size).any{it.zoneId==last.zoneId})prefix=prefix.takeWhile{it.zoneId!=last.zoneId}
        }
        // Adjacent slices of the same selected block share one reflow layout. A slice
        // boundary is an execution detail, never a paragraph or a forced display line.
        val grouped=mutableListOf<Pair<OcrChunk,List<OcrChunk>>>()
        for(c in prefix){val last=grouped.lastOrNull();if(c.kind==Kind.TEXT&&last?.first?.kind==Kind.TEXT&&last.first.zoneId==c.zoneId){val parts=last.second+c;grouped[grouped.lastIndex]=last.first.copy(box=last.first.box.union(c.box)) to parts}else grouped+=c to listOf(c)}
        val plan=grouped.map{it.first}
        val texts=grouped.filter{it.first.kind==Kind.TEXT}.associate{(c,parts)->val ws=parts.flatMap{words[it.key].orEmpty()};c.key to OcrReview.whitespace(if(enhancedOcr&&repair!=null)repair!!.text(ws)else plainText(ws))}
        val reviews=if(enhancedOcr)grouped.filter{it.first.kind==Kind.TEXT}.associate{(c,parts)->c.key to OcrReview.compare(OcrReview.whitespace(plainText(parts.flatMap{rawChunks[it.key].orEmpty()})),texts[c.key].orEmpty(),c.box).let{review->
                val evidence=parts.flatMap{secondChunks[it.key].orEmpty()}
                val edits=review.edits.map{edit->val terms=Regex("[\\p{L}]{4,}").findAll(edit.corrected+" "+edit.raw).map{it.value.lowercase()}.toSet();edit.copy(readings=evidence.filter{e->Regex("[\\p{L}]{4,}").findAll(e.text).any{it.value.lowercase() in terms}}.take(2))}
                review.copy(edits=edits,keptRaw=store.ocrChoices(page,review.fingerprint()))}}else emptyMap()
        val shown=texts.mapValues{(key,value)->reviews[key]?.rendered()?.first?:value}
        if(reading&&ocrReading){preview?.let{reader.setBackgrounds(plan.associate{it.key to readingColor(it.box)})};reader.showOcrStream(plan,shown,ocrLoading,force,reviewTexts=reviews)}
    }

    private fun showOcrComparison(key:Int,id:Int,y:Float){
        val review=reader.review(key)?:return;val edit=review.edits.firstOrNull{it.id==id}?:return
        OcrComparisonPopup(this,edit,id in review.keptRaw,{keep->store.putOcrChoice(page,review.fingerprint(),id,keep);reader.chooseRaw(key,id,keep)},{showOcrSource(edit.source)}).show(reader,y)
    }
    private fun showOcrSource(box:Box){
        val token=generation;val targetPage=page;val image=ImageView(this).apply{adjustViewBounds=true;contentDescription="Extrait du PDF original";setPadding(dp(8),dp(8),dp(8),dp(8))}
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(text("Chargement de l'extrait original…"));addView(image,LinearLayout.LayoutParams(-1,-2))}
        val dialog=AlertDialog.Builder(this).setTitle("Extrait PDF").setView(ScrollView(this).apply{addView(panel)}).setPositiveButton("Fermer",null).create();dialog.show()
        worker.execute{val bitmap=try{if(token==generation)document?.region(targetPage,box,1200,1200)else null}catch(_:Exception){null};main.post{if(token!=generation||!dialog.isShowing){bitmap?.recycle()}else{(panel.getChildAt(0)as TextView).text=if(bitmap==null)"Extrait indisponible. Revenez à Original." else "Source du passage, sans correction OCR";image.setImageBitmap(bitmap);dialog.setOnDismissListener{image.setImageDrawable(null);bitmap?.recycle()}}}}
    }
    private fun prepareOcr(force:Boolean=false){
        val p=preview?:return
        if(force&&ocrLoading){ocrRun++;detector?.cancel();ocrLoading=false}
        val chosen=BlockSeams.align(p,ReadingLayout.ordered(zones,order).map{if(it.kind==Kind.TEXT&&!it.manual)it.copy(box=TextCoverage.safeTextBox(p,it.box))else it})
        val newPlan=OcrChunks.plan(chosen,recognizedWords.orEmpty()){OcrRasterLines.chunks(p,it)}
        if(chunkPlan!=newPlan){ocrRun++;detector?.cancel();ocrLoading=false;chunkPlan=newPlan;rawChunks.clear();improvedChunks.clear();secondChunks.clear();for(c in chunkPlan)if(c.words.isNotEmpty())rawChunks[c.key]=c.words}
        ocrNotice="";displayOcr(force);updateControls()
        if(ocrLoading)return
        val needed=chunkPlan.filter{it.kind==Kind.TEXT&&if(enhancedOcr)it.key !in improvedChunks else it.key !in rawChunks}
        if(needed.isEmpty())return
        val targetPage=page;val image=p.copy(Bitmap.Config.ARGB_8888,false);val token=generation;val run=++ocrRun;val improving=enhancedOcr;val rawSnapshot=rawChunks.toMap()
        val engine=ZoneDetector(applicationContext);detector=engine;ocrLoading=true;displayOcr()
        detectionWorker.execute{
            val cancelled={token!=generation||run!=ocrRun}
            var second:PaddleLineRecognizer?=null
            try{
                val corrector=repair?:loadRepair()
                val spelling=this@MainActivity.spelling
                if(improving&&!cancelled())second=try{PaddleLineRecognizer(applicationContext)}catch(_:Exception){null}
                main.post{if(!cancelled())repair=corrector}
                val localRaw=rawSnapshot.toMutableMap()
                val source:(OcrChunk)->Bitmap?={chunk->if(cancelled())null else worker.submit<Bitmap?>{if(cancelled())null else document?.ocrRegion(targetPage,chunk.box)}.get() }
                fun own(chunk:OcrChunk,result:List<OcrWord>)=result.filter{w->chosen.firstOrNull{z->z.box.contains((w.box.left+w.box.right)/2,(w.box.top+w.box.bottom)/2)||z.box.intersect(w.box)>.5f*w.box.area}?.id==chunk.zoneId}
                fun publishRaw(chunk:OcrChunk,result:List<OcrWord>){
                    val owned=own(chunk,result);localRaw[chunk.key]=owned
                    main.post{if(!cancelled()&&!isDestroyed){repair=corrector;rawChunks[chunk.key]=owned;if(!improving)displayOcr()}}
                }
                fun publishImproved(chunk:OcrChunk,result:List<OcrWord>){
                    val evidence=mutableListOf<OcrEvidence>()
                    val value=OcrParagraphs.lines(own(chunk,result)).flatMap{line->
                        evidence+=OcrEvidence("Tesseract · PDF source",line.text,line.box,line.words.map{it.confidence/100f}.average().toFloat())
                        val box=line.box.pad(.0015f)
                        val raster=if(second!=null&&!cancelled())worker.submit<Bitmap?>{if(cancelled())null else document?.ocrRegion(targetPage,box)}.get()else null
                        val reading=if(raster!=null)try{second?.recognize(raster,box,cancelled)}catch(_:Exception){null}finally{raster.recycle()}else null
                        if(reading!=null){val item=OcrEvidence(reading.engine,reading.text,reading.source,reading.confidence);evidence+=item;val choice=OcrConsensus.choose(line.words,item,spelling);choice.words}else line.words
                    }
                    val protected=OcrBaseline.protectHyphenFragments(localRaw[chunk.key].orEmpty(),value,spelling)
                    main.post{if(!cancelled()&&!isDestroyed){repair=corrector;secondChunks[chunk.key]=evidence;improvedChunks[chunk.key]=protected;displayOcr()}}
                }
                if(improving){
                    if(needed.all{it.key in localRaw})engine.recognizeChunks(needed,image,true,cancelled,::publishImproved,source)
                    else for(chunk in needed){
                        if(cancelled())break
                        if(chunk.key !in localRaw)engine.recognizeChunks(listOf(chunk),image,false,cancelled,::publishRaw)
                        if(!cancelled())engine.recognizeChunks(listOf(chunk),image,true,cancelled,::publishImproved,source)
                    }
                }else engine.recognizeChunks(needed.filter{it.key !in localRaw},image,false,cancelled,::publishRaw)
                main.post{if(!cancelled()&&!isDestroyed){ocrLoading=false;detector=null;ocrNotice=if(improving)" · déjà affiché inchangé" else "";displayOcr();if(ocrReading)status.text="OCR ${if(enhancedOcr)"amélioré" else "brut"} · erreurs possibles$ocrNotice"}}
            }catch(_:Exception){main.post{if(!cancelled()&&!isDestroyed){ocrLoading=false;detector=null;ocrNotice=" · suite indisponible, réessayez";displayOcr();status.text="OCR partiel. Réessayez ou revenez à Original."}}}
            finally{second?.close();image.recycle()}
        }
    }
    private fun exitReader(){ocrRun++;detector?.cancel();ocrLoading=false;saveSession();reader.clear();reader.visibility=View.GONE;canvas.visibility=View.VISIBLE;++generation;reading=false;busy=false;canvas.reading=false;canvas.image=preview;readingBitmap?.recycle();readingBitmap=null;sync()}
    private data class GroupUndo(val page:Int,val zones:List<Zone>,val order:List<Int>,val applied:AutoGrouping.Result)
    private var groupUndo:GroupUndo?=null
    private var groupDialog:Dialog?=null
    private fun showAutoGroups(){
        if(busy||!pageReady||reading){toast("Revenez à la vue page pour regrouper.");return}
        val image=preview?:return
        if(order.isEmpty()){toast("Choisissez d'abord vos blocs à lire.");return}
        val chosen=order.toSet()
        val suggestions=AutoGrouping.propose(zones,chosen)
        if(suggestions.isEmpty()){toast("Aucun regroupement assez sûr parmi vos blocs choisis. La fusion manuelle reste disponible.");return}
        val originalZones=zones;val originalOrder=order;val targetPage=page
        val selected=suggestions.indices.toMutableSet();val dialog=Dialog(this);groupDialog=dialog
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(12));setBackgroundColor(Color.rgb(250,249,246))}
        panel.addView(text("Vérifier les regroupements",23,true));panel.addView(text("Suggestions seulement, limitées aux blocs déjà choisis. Colonnes et cases distinctes restent séparées.",12))
        val view=PageCanvas(this).apply{this.image=image;this.zones=originalZones;mergeMode=true;chosenIds=chosen}
        fun refreshPreview(){view.mergeIds=selected.flatMap{suggestions[it].ids}.toSet();view.mergeColors=selected.flatMap{i->suggestions[i].ids.map{id->id to AutoGrouping.color(i)}}.toMap();view.groupBoxes=selected.sorted().map{i->PageCanvas.GroupBox(suggestions[i].box,AutoGrouping.color(i),i+1)}}
        refreshPreview();panel.addView(view,LinearLayout.LayoutParams(-1,0,1f))
        val choices=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        suggestions.forEachIndexed{index,g->choices.addView(CheckBox(this).apply{isChecked=true;text="Groupe ${index+1} · ${g.ids.size} blocs\n${g.reason}";minHeight=dp(54);setTextColor(ink);buttonTintList=android.content.res.ColorStateList.valueOf(AutoGrouping.color(index));setOnCheckedChangeListener{_,checked->if(checked)selected+=index else selected-=index;refreshPreview()}})}
        panel.addView(ScrollView(this).apply{addView(choices)},LinearLayout.LayoutParams(-1,dp(minOf(180,suggestions.size*58))))
        panel.addView(text("Chaque groupe prend la place de son premier bloc choisi. Une case décochée retire la proposition. Annulation dans Menu.",12))
        val row=LinearLayout(this);row.addView(button("Annuler"){dialog.dismiss()},LinearLayout.LayoutParams(0,dp(54),1f));row.addView(button("Appliquer",true){
            if(page!=targetPage||zones!=originalZones||order!=originalOrder){dialog.dismiss();return@button}
            if(selected.isNotEmpty()){
                val result=AutoGrouping.apply(zones,order,selected.sorted().map{suggestions[it]},suggestions);groupUndo=GroupUndo(page,zones,order,result);zones=result.zones;order=result.order
                chunkPlan=emptyList();rawChunks.clear();improvedChunks.clear();secondChunks.clear();scrollPosition=0;sync()
            };dialog.dismiss()
        },LinearLayout.LayoutParams(0,dp(54),1f));panel.addView(row)
        dialog.setContentView(panel);dialog.setOnDismissListener{view.image=null;if(groupDialog===dialog)groupDialog=null};dialog.show();dialog.window?.setLayout(-1,-1)
    }
    private fun undoAutoGroups(){
        val saved=groupUndo
        if(saved==null||saved.page!=page||saved.applied.zones!=zones||saved.applied.order!=order||reading||busy){toast("Annulation indisponible : aucun regroupement récent, ou la page a été modifiée.");return}
        zones=saved.zones;order=saved.order;groupUndo=null;chunkPlan=emptyList();rawChunks.clear();improvedChunks.clear();secondChunks.clear();scrollPosition=0;sync();toast("Regroupement annulé.")
    }
    private fun beginMerge(z:Zone){merging=true;mergeIds=setOf(z.id);canvas.mergeMode=true;canvas.mergeIds=mergeIds;updateControls()}
    private fun beginMove(z:Zone){moving=z.id;canvas.moveMode=true;canvas.moveId=z.id;updateControls()}
    private fun finishMove(apply:Boolean){val placed=apply;moving=0;canvas.moveMode=false;canvas.moveId=0;if(placed)toast("Bloc déplacé · ordre de lecture mis à jour");sync(apply)}
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
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(14));background=shape(Color.WHITE,24)}
        val dialog=android.app.Dialog(this)
        editDialog=dialog;dialog.setOnDismissListener{if(editDialog===dialog)editDialog=null}
        val heading=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;isBaselineAligned=false}
        heading.addView(text("Corriger le bloc",25).apply{typeface=Typeface.create("serif",Typeface.NORMAL)},LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(button("Fermer"){dialog.dismiss()}.apply{background=null},LinearLayout.LayoutParams(dp(90),dp(48)))
        panel.addView(heading);panel.addView(text("Bloc de ${if(z.kind==Kind.TEXT)"texte" else "type image"} · page ${page+1}",12))
        val scroll=ScrollView(this).apply{isFillViewport=false};val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};scroll.addView(list);panel.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        fun section(label:String){list.addView(text(label,11,true).apply{setTextColor(Color.rgb(88,82,84));setPadding(0,dp(17),0,dp(5))})}
        fun action(label:String,detail:String,icon:String,which:Int,highlight:Boolean=false,danger:Boolean=false){
            val color=if(danger)Color.rgb(170,61,67)else green
            val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;isBaselineAligned=false;minimumHeight=dp(68);setPadding(dp(8),dp(9),dp(8),dp(9));background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(25,50,109,93)),shape(if(highlight)Color.rgb(238,244,240)else Color.WHITE,12),shape(Color.WHITE,12));isClickable=true;isFocusable=true;contentDescription="$label. $detail"}
            row.addView(ImageView(this).apply{setImageDrawable(ActionIcon(icon,color))},LinearLayout.LayoutParams(dp(25),dp(25)).apply{marginEnd=dp(16)})
            val words=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};words.addView(text(label,15,true).apply{if(danger)setTextColor(color)});words.addView(text(detail,12).apply{setTextColor(Color.rgb(88,82,84))});row.addView(words,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(text("›",22).apply{setTextColor(Color.rgb(140,135,137))},LinearLayout.LayoutParams(dp(12),-2))
            row.setOnClickListener{dialog.dismiss();editZoneAction(z,which)};list.addView(row,LinearLayout.LayoutParams(-1,-2))
            if(!highlight&&!danger)list.addView(View(this).apply{setBackgroundColor(Color.rgb(234,233,234))},LinearLayout.LayoutParams(-1,dp(1)))
        }
        section("MODIFIER LE CADRE")
        action("Ajuster le cadre","Déplacez le bloc ou ses poignées.","crop",6,true)
        action("Redessiner le cadre","Tracez un nouveau rectangle à sa place.","draw",0)
        section("ORGANISER LE CONTENU")
        action("Séparer gauche / droite","Placez la ligne, puis confirmez les deux blocs.","splitv",2)
        action("Séparer haut / bas","Placez la ligne, puis confirmez les deux blocs.","splith",3)
        action("Proposer cet article","Suggère les blocs de cette page, à vérifier avant ajout.","book",7)
        action("Fusionner avec d'autres blocs","Choisissez les blocs à réunir dans un seul cadre.","merge",4)
        action("Déplacer à une autre position","Touchez ensuite le bloc après lequel il sera lu.","move",8)
        section("AFFICHAGE DU BLOC")
        action("Traiter comme ${if(z.kind==Kind.TEXT)"image" else "texte"}",if(z.kind==Kind.TEXT)"Conserve le bloc en image, même en mode OCR." else "Permet la reconnaissance du texte en mode OCR.",if(z.kind==Kind.TEXT)"image" else "text",1)
        action("Supprimer le bloc","Retire la zone et sa place dans le parcours.","trash",5,danger=true)
        panel.addView(text("Le PDF original reste inchangé. Fermez ce panneau pour revenir sans modifier le bloc.",11).apply{setTextColor(Color.rgb(88,82,84));setPadding(0,dp(10),0,0)})
        dialog.setContentView(panel);dialog.window?.apply{setBackgroundDrawableResource(android.R.color.transparent);setGravity(Gravity.BOTTOM);addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);setDimAmount(.35f)}
        dialog.show();dialog.window?.setLayout(resources.displayMetrics.widthPixels-dp(16),(resources.displayMetrics.heightPixels*.85f).toInt())
    }
    private fun editZoneAction(z:Zone,which:Int){
        when(which){
                0->{replaceId=z.id;adding=true;canvas.drawMode=true}
                1->{zones=zones.map{if(it.id==z.id)it.copy(kind=if(it.kind==Kind.TEXT)Kind.IMAGE else Kind.TEXT,panel=false,complement=false)else it}}
                2,3->{showSplit(z,which==2);return}
                4->{beginMerge(z)}

                5->{zones=zones.filterNot{it.id==z.id};order=order.filterNot{it==z.id}}
                6->{beginAdjustment(z)}
                7->{beginArticle(z);return}
                8->{if(z.id in order){beginMove(z);return}else toast("Ce bloc ne fait pas partie du parcours de lecture")}
            };sync(adjusting==null&&!merging)
    }

    private fun beginArticle(z:Zone){
        val proposal=ArticleProposal.propose(zones,z.id,recognizedWords.orEmpty())
        articleIds=proposal.ids;articleReason=proposal.reason;canvas.selection=proposal.ids;updateControls()
    }
    private fun finishArticle(apply:Boolean){
        if(apply)order=ArticleProposal.append(order,articleIds.orEmpty())
        articleIds=null;articleReason="";sync(apply)
    }
    private fun editArticleOrder(){
        val dialog=Dialog(this);val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(12));setBackgroundColor(Color.WHITE)}
        panel.addView(text("Ordre proposé",24));panel.addView(text("Les places du parcours existant ne changent pas.",12))
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};val scroll=ScrollView(this).apply{addView(list)};panel.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        fun refresh(){list.removeAllViews();val ids=articleIds.orEmpty();ids.forEachIndexed{i,id->val r=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};r.addView(text("${i+1} · bloc $id${if(id in order)" · déjà choisi" else ""}",14),LinearLayout.LayoutParams(0,-2,1f))
            fun shift(delta:Int){val next=ids.toMutableList();java.util.Collections.swap(next,i,i+delta);articleIds=next;canvas.selection=next;updateControls();refresh()}
            r.addView(button("Monter"){shift(-1)}.apply{isEnabled=i>0},LinearLayout.LayoutParams(dp(84),dp(48)));r.addView(button("Descendre"){shift(1)}.apply{isEnabled=i<ids.lastIndex},LinearLayout.LayoutParams(dp(94),dp(48)));list.addView(r)
        }}
        refresh();panel.addView(button("Fermer"){dialog.dismiss()});dialog.setContentView(panel);dialog.show();dialog.window?.setLayout(resources.displayMetrics.widthPixels-dp(16),(resources.displayMetrics.heightPixels*.8f).toInt())
    }
    private fun showSplit(z:Zone,vertical:Boolean){
        val image=preview?:return
        val dialog=Dialog(this)
        editDialog=dialog;dialog.setOnDismissListener{if(editDialog===dialog)editDialog=null}
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(12));setBackgroundColor(Color.rgb(250,249,246))}
        panel.addView(text("Séparer ${if(vertical)"gauche / droite" else "haut / bas"}",25).apply{typeface=Typeface.create("serif",Typeface.NORMAL)})
        panel.addView(text("Glissez la ligne ${if(vertical)"horizontalement" else "verticalement"}.",13))
        val view=SplitPreview(this,image,z.box,vertical);panel.addView(view,LinearLayout.LayoutParams(-1,0,1f))
        val zoomLabel=text("Pincez pour zoomer · glissez ailleurs pour déplacer la vue",12).apply{gravity=Gravity.CENTER};panel.addView(zoomLabel)
        fun row(vararg items:Button){val r=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};items.forEachIndexed{i,it->r.addView(it,LinearLayout.LayoutParams(0,dp(48),1f).apply{if(i>0)marginStart=dp(6)})};panel.addView(r,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(6)})}
        row(button("Dézoomer"){view.setZoom(view.zoom/1.5f)},button("Zoomer"){view.setZoom(view.zoom*1.5f)},button("Recentrer"){view.setZoom(1f)})
        row(button(if(vertical)"À gauche" else "Monter"){view.nudge(-1)},button(if(vertical)"À droite" else "Descendre"){view.nudge(1)})
        val position=text("",12).apply{gravity=Gravity.CENTER};panel.addView(position)
        view.onChange={position.text="${(view.fraction*100).roundToInt()} % · zoom ${String.format(java.util.Locale.FRANCE,"%.1f",view.zoom)}× · pas de 1 pixel aperçu"};view.onChange?.invoke()
        val actions=LinearLayout(this)
        actions.addView(button("Annuler"){dialog.dismiss()},LinearLayout.LayoutParams(0,dp(54),.85f))
        actions.addView(button("Confirmer la séparation",true){
            val result=BlockSplit.apply(zones,order,z.id,vertical,view.fraction);zones=result.zones;order=result.order
            ocrRun++;detector?.cancel();ocrLoading=false;chunkPlan=emptyList();rawChunks.clear();improvedChunks.clear();secondChunks.clear();scrollPosition=0
            dialog.dismiss();sync()
        },LinearLayout.LayoutParams(0,dp(54),1.6f))
        panel.addView(actions);dialog.setContentView(panel);dialog.show()
        dialog.window?.apply{setBackgroundDrawableResource(android.R.color.transparent);setLayout(-1,-1)}
    }

    private fun showPageSummary(){
        if(count<=0||(busy&&!detecting)||documentLoading||pageSummary?.isShowing==true)return
        val token=generation;val originalPage=page;val dialog=Dialog(this);pageSummary=dialog
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(250,249,246));setPadding(dp(12),dp(12),dp(12),dp(12))}
        val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};val titles=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(text("Sommaire visuel",22,true));addView(text("$count pages · page actuelle : ${page+1}",12))};head.addView(titles,LinearLayout.LayoutParams(0,-2,1f));head.addView(button("Fermer"){dialog.dismiss()});panel.addView(head)
        panel.addView(text("Touchez un aperçu pour ouvrir sa page.",13))
        val grid=GridView(this).apply{numColumns=3;horizontalSpacing=dp(6);verticalSpacing=dp(8);stretchMode=GridView.STRETCH_COLUMN_WIDTH;clipToPadding=false;setPadding(0,dp(8),0,dp(8))}
        val adapter=PageThumbnails(this,count,page,{job->worker.execute{job()}},{index->if(token==generation)document?.thumbnail(index)else null});grid.adapter=adapter
        grid.setOnScrollListener(object:AbsListView.OnScrollListener{override fun onScrollStateChanged(v:AbsListView?,state:Int){};override fun onScroll(v:AbsListView?,first:Int,visible:Int,total:Int){adapter.visible(first,visible)}})
        grid.setOnItemClickListener{_,_,position,_->dialog.dismiss();if(token==generation&&position!=originalPage)loadPage(position)}
        panel.addView(grid,LinearLayout.LayoutParams(-1,0,1f));panel.addView(text("Glissez pour parcourir les $count pages.",12));dialog.setContentView(panel);dialog.setOnDismissListener{adapter.close();grid.adapter=null;pageSummary=null};dialog.show();dialog.window?.apply{setBackgroundDrawableResource(android.R.color.transparent);setLayout(-1,-1)};grid.setSelection((page/3)*3)
    }
    private fun goToPage(){if(documentLoading)return;val input=EditText(this).apply{inputType=android.text.InputType.TYPE_CLASS_NUMBER;hint="1 à $count"};AlertDialog.Builder(this).setTitle("Aller à la page").setView(input).setPositiveButton("Ouvrir"){_,_->val p=input.text.toString().toIntOrNull();if(p!=null&&p in 1..count)loadPage(p-1)else toast("Numéro de page invalide")}.setNegativeButton("Annuler",null).show()}
    private var articleShareDialog:Dialog?=null
    private var articleShareFile:File?=null
    private fun shareArticle(){
        if(!reading||articleShareDialog!=null)return
        if(ocrReading&&(ocrLoading||chunkPlan.any{it.kind==Kind.TEXT&&if(enhancedOcr)it.key !in improvedChunks else it.key !in rawChunks})){
            toast("Attendez la fin de la préparation OCR ou choisissez Original pour partager tous les blocs.");return
        }
        val plan=try{reader.articleImagePlan()}catch(e:Exception){toast(e.message?:"Article indisponible");return}
        val target=document?:return;val targetPage=page;val cleanup=cleanReading&&!ocrReading;val token=generation
        val cancelled=java.util.concurrent.atomic.AtomicBoolean(false)
        val dialog=Dialog(this);articleShareDialog=dialog;articleShareFile=null
        val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(16));background=shape(Color.rgb(250,249,246),18)}
        panel.addView(text("Partager l'article",22,true))
        panel.addView(text("Tous les blocs dans l'ordre · ${if(ocrReading)"Texte OCR" else if(cleanup)"Nettoyé" else "Original"}",13))
        val state=text("Préparation de l'image complète…",13);panel.addView(state)
        val image=ImageView(this).apply{adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="Aperçu de l'article complet, tous les blocs dans l'ordre"}
        val scroll=ScrollView(this).apply{addView(image,ViewGroup.LayoutParams(-1,-2))};panel.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        var shownPreview:Bitmap?=null
        val actions=LinearLayout(this)
        val send=button("Partager l'image",true){
            val file=articleShareFile
            if(file==null||!file.isFile){toast("L'image n'est plus disponible. Réessayez.");return@button}
            val uri=ArticleImageProvider.uri(this,file)
            val intent=Intent(Intent.ACTION_SEND).apply{type="image/png";putExtra(Intent.EXTRA_STREAM,uri);clipData=android.content.ClipData.newUri(contentResolver,"Article",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            try{startActivity(Intent.createChooser(intent,"Partager l'article"));dialog.dismiss()}catch(_:android.content.ActivityNotFoundException){toast("Aucune application de partage disponible.")}
        }.apply{isEnabled=false}
        actions.addView(button("Annuler"){dialog.dismiss()},LinearLayout.LayoutParams(0,dp(52),1f));actions.addView(send,LinearLayout.LayoutParams(0,dp(52),2f).apply{marginStart=dp(8)});panel.addView(actions)
        dialog.setContentView(panel);dialog.setOnDismissListener{cancelled.set(true);articleShareDialog=null;articleShareFile=null;image.setImageDrawable(null);shownPreview?.recycle();shownPreview=null}
        dialog.show();dialog.window?.apply{setBackgroundDrawableResource(android.R.color.transparent);setLayout(-1,(resources.displayMetrics.heightPixels*.92f).toInt())}
        worker.execute{
            var file:File?=null;var thumb:Bitmap?=null
            try{
                val dir=File(cacheDir,"article-images").apply{mkdirs()};dir.listFiles()?.filter{System.currentTimeMillis()-it.lastModified()>24*60*60*1000L}?.forEach{it.delete()}
                file=File(dir,"article-${java.util.UUID.randomUUID()}.png")
                val size=ArticleImageExporter.write(plan,file,{cancelled.get()||token!=generation}){tile->target.readingRegion(targetPage,tile.source,max(1,ceil(tile.rect.width()).toInt()),max(1,ceil(tile.rect.height()).toInt()),cleanup&&tile.kind==Kind.TEXT)}
                val options=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.absolutePath,options)
                options.inSampleSize=1;while(options.outWidth.toLong()*options.outHeight/options.inSampleSize/options.inSampleSize>4_000_000L||options.outHeight/options.inSampleSize>16000)options.inSampleSize*=2
                options.inJustDecodeBounds=false;thumb=BitmapFactory.decodeFile(file.absolutePath,options)?:error("L'aperçu n'a pas pu être créé.")
                val ready=file;val previewImage=thumb
                main.post{if(cancelled.get()||token!=generation||isDestroyed){previewImage.recycle();ready.delete()}else{articleShareFile=ready;shownPreview=previewImage;image.setImageBitmap(previewImage);state.text="${size.width} × ${size.height} px · Faites défiler l'aperçu. Aucune image envoyée sans votre choix.";send.isEnabled=true}}
            }catch(e:Exception){thumb?.recycle();file?.delete();main.post{if(!cancelled.get()&&!isDestroyed)state.text=e.message?:"Impossible de préparer l'image. Rien n'a été partagé."}}catch(_:OutOfMemoryError){thumb?.recycle();file?.delete();main.post{if(!cancelled.get()&&!isDestroyed)state.text="Mémoire insuffisante. Réduisez le nombre de blocs, puis réessayez. Rien n'a été partagé."}}
        }
    }
    private fun menu(){
        val dialog=Dialog(this);menuDialog=dialog
        dialog.setOnDismissListener{menuDialog=null}
        val sheet=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(10),dp(16),dp(16))}
        val handle=View(this).apply{background=shape(Color.rgb(214,211,204),3)};sheet.addView(handle,LinearLayout.LayoutParams(dp(44),dp(5)).apply{gravity=Gravity.CENTER_HORIZONTAL;bottomMargin=dp(8)})
        val titleRow=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        titleRow.addView(text("Menu",22,true),LinearLayout.LayoutParams(0,-2,1f))
        titleRow.addView(button("Fermer"){dialog.dismiss()}.apply{background=null},LinearLayout.LayoutParams(dp(90),dp(48)))
        sheet.addView(titleRow)
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        fun section(name:String){list.addView(text(name,12,true).apply{setTextColor(Color.rgb(120,118,112));setPadding(dp(4),dp(10),0,dp(4))})}
        fun entry(icon:String,label:String,desc:String?,danger:Boolean=false,enabled:Boolean=true,action:()->Unit){
            val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=shape(Color.WHITE,10,Color.rgb(226,231,226));setPadding(dp(14),dp(8),dp(14),dp(8));isEnabled=enabled
                setOnClickListener{if(enabled){dialog.dismiss();action()}}}
            val inkColor=if(danger)Color.rgb(214,74,60)else ink
            row.addView(text(label,16,true).apply{setTextColor(if(enabled)inkColor else Color.rgb(176,173,166));setPadding(0,0,0,0);compoundDrawablePadding=dp(14);setCompoundDrawablesRelative(ActionIcon(icon,if(!enabled)Color.rgb(176,173,166)else if(danger)inkColor else Color.rgb(50,109,93)).apply{setBounds(0,0,dp(24),dp(24))},null,null,null)})
            if(desc!=null)row.addView(text(desc,12).apply{setTextColor(if(enabled)Color.rgb(120,118,112)else Color.rgb(196,193,186));setPadding(dp(38),0,0,0)})
            list.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(6)})
        }
        if(reading){section("Lecture de l'article");entry("share","Partager l'article","Une image avec tous les blocs lus."){shareArticle()}}
        section("Document")
        entry("document","Ouvrir un autre PDF",null){pickPdf()}
        entry("close","Supprimer le document local",null,danger=true){clearDocument()}
        section("Blocs de la page")
        val hasPage=count>0
        entry("add","Ajouter un bloc","Tracez vous-même un bloc sur la page.",enabled=hasPage&&!busy){adding=true;canvas.drawMode=true;updateControls()}
        entry("order","Voir l'ordre de lecture","Vérifiez et corrigez votre parcours.",enabled=hasPage){showOrder()}
        entry("merge","Fusionner des blocs","Réunissez à la main plusieurs blocs en un seul.",enabled=zones.isNotEmpty()){(order.firstOrNull()?.let{id->zones.find{it.id==id}}?:zones.firstOrNull())?.let{beginMerge(it)}}
        entry("group","Regrouper automatiquement","Propose des regroupements parmi les blocs choisis. Vous validez ceux à appliquer.",enabled=zones.isNotEmpty()){showAutoGroups()}
        entry("refresh","Réessayer la détection","Relance la recherche des blocs de la page.",enabled=hasPage&&!busy){AlertDialog.Builder(this).setMessage("Remplacer les blocs et le parcours de cette page ?").setPositiveButton("Remplacer"){_,_->loadPage(page,true)}.setNegativeButton("Annuler",null).show()}
        entry("undo","Annuler le dernier regroupement",null){undoAutoGroups()}
        section("Aide")
        entry("help","Revoir le didacticiel","Les quatre gestes à essayer sur le document d'exemple."){startTutorial()}
        entry("info","Confidentialité et aide",null){AlertDialog.Builder(this).setTitle(applicationInfo.loadLabel(packageManager).toString()).setMessage("Le PDF, les zones et les parcours restent sur cet appareil. Aucune permission Internet, publicité ou compte. Une copie privée du dernier PDF est conservée pour reprendre la lecture.\n\nLa détection peut manquer ou fusionner des zones. + Bloc permet de cadrer à la main ; un double tap ouvre la correction. Maintenez et glissez pour choisir plusieurs blocs. Pincez pour zoomer et faites glisser la page agrandie.\n\nLe zoom ne recrée pas les détails d'un scan flou. Le résultat dépend du document et des capacités de votre téléphone.").setPositiveButton("Fermer",null).show()}
        entry("document","Politique de confidentialité","La politique complète, hébergée en ligne, ouverte dans le navigateur."){openPrivacyPolicy()}
        entry("license","Licences des composants",null){showLicenses()}
        val scroll=ScrollView(this).apply{addView(list)}
        sheet.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        dialog.setContentView(sheet)
        dialog.show()
        dialog.window?.apply{
            setBackgroundDrawable(shape(Color.rgb(250,249,246),18))
            setGravity(Gravity.BOTTOM)
            setLayout(-1,(resources.displayMetrics.heightPixels*.85f).toInt())
        }
    }
    /** Interactive walkthrough, practiced in a separate activity on an isolated copy of the
     * bundled example (cache only): the user's document, sessions and preferences stay untouched. */
    private fun startTutorial(){
        if(busy||documentLoading||reading)return
        getPreferences(0).edit().putBoolean("tutorialSeen",true).apply()
        startActivity(android.content.Intent(this,TutorialActivity::class.java))
    }

    private fun clearDocument(){if(busy){toast("Attendez la fin de l’analyse avant de supprimer.");return};AlertDialog.Builder(this).setTitle("Supprimer la copie locale ?").setMessage("Le fichier original n'est pas modifié. Le parcours enregistré sera effacé.").setPositiveButton("Supprimer"){_,_->++generation;main.removeCallbacks(saveProgress);reader.clear();reader.visibility=View.GONE;canvas.visibility=View.VISIBLE;pageReady=false;worker.execute{document?.close();document=null;File(filesDir,"current.pdf").delete()};count=0;store.clear();preview?.recycle();preview=null;readingBitmap?.recycle();readingBitmap=null;canvas.image=null;canvas.zones=emptyList();canvas.selection=emptyList();reading=false;busy=false;title.text=applicationInfo.loadLabel(packageManager).toString();info.text="Sans compte et hors ligne";status.text="Document local supprimé.";updateControls()}.setNegativeButton("Annuler",null).show()}
    /** Full privacy policy on the project's public page, opened in the browser: the app itself
     *  keeps no Internet permission. */
    private fun openPrivacyPolicy()=startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://manudss.github.io/pdf-focus-reader/confidentialite.html")))
    private fun showLicenses(){val names=assets.list("licenses").orEmpty();AlertDialog.Builder(this).setTitle("Licences").setItems(names){_,which->val view=TextView(this).apply{text=assets.open("licenses/"+names[which]).bufferedReader().use{it.readText()};setPadding(dp(16),dp(12),dp(16),dp(12));textSize=12f};val scroll=ScrollView(this).apply{addView(view)};AlertDialog.Builder(this).setTitle(names[which]).setView(scroll).setPositiveButton("Fermer",null).show()}.setNegativeButton("Fermer",null).show()}
    private fun toast(message:String)=Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
    /** Original tutorial bundled for explicit offline discovery; never replaces a resumed document. */
    private fun demo(){openDocument("PDF Focus - Découverte"){target->assets.open("examples/decouverte.pdf").use{input->target.outputStream().use{output->input.copyTo(output)}}}}
    /** Back pressed: leave the current mode first (article, merge, adjustment, reader, block
     *  adding); only leave the activity when no mode is open. Called by the platform through
     *  OnBackInvokedCallback on API 33+ and through the deprecated override below before that. */
    private fun leaveCurrentMode():Boolean{
        if(articleIds!=null){finishArticle(false);return true}
        if(merging){finishMerge(false);return true}
        if(adjusting!=null){finishAdjustment(false);return true}
        if(reading){exitReader();return true}
        if(adding){adding=false;replaceId=null;canvas.drawMode=false;sync();return true}
        return false
    }
    @Deprecated("Activity back callback")
    // API 26-32 fallback only. With targetSdk 36 the framework dispatches system back
    // (gesture or button) through OnBackInvokedDispatcher on API 33+, where the callback
    // registered in onCreate (and unregistered in onDestroy) runs; this override is
    // bypassed there and cannot intercept the gesture. On API 26-32 legacy dispatch still
    // routes back to this method, so it intentionally applies the same leaveCurrentMode()
    // contract. On Android 16 (target 36) it is never called at all.
    @android.annotation.SuppressLint("GestureBackNavigation")
    override fun onBackPressed(){if(!leaveCurrentMode())super.onBackPressed()}
    override fun onDestroy(){if(Build.VERSION.SDK_INT>=33){systemBackCallback?.let{onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)};systemBackCallback=null};modePreviewDialog?.dismiss();articleShareDialog?.dismiss();canvas.finishSelectionReveal();pageSummary?.dismiss();editDialog?.dismiss();main.removeCallbacks(saveProgress);if(pageReady)saveSession();reader.clear();++generation;detector?.cancel();detectionWorker.shutdown();super.onDestroy();worker.execute{document?.close();document=null};worker.shutdown()}
}
