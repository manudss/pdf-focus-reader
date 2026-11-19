package com.pdffocus.reader
import android.content.Context
import android.graphics.*
import android.view.*
import android.text.StaticLayout
import android.text.TextPaint
import android.text.Layout
import android.widget.OverScroller
import kotlin.math.*

/** Viewport-sized tiles only. No screenshot-sized whole article allocations, no overlapping frames. */
class ContinuousReaderView(context:Context):View(context){
    data class Tile(val key:Int,val source:Box,val rect:RectF,val ordinal:Int,val kind:Kind,val chunkKey:Int=-1)
    data class TextItem(val layout:StaticLayout,val top:Float,val ordinal:Int,val key:Int=ordinal)
    private var ocrTexts:Map<Int,String>?=null
    private var textItems=listOf<TextItem>()
    private var reviews=mapOf<Int,OcrReviewText>()
    var onOcrMark:((Int,Int,Float,Float)->Unit)?=null
    fun setBackgrounds(values:Map<Int,Int>){if(palettes!=values){palettes=values;rebuild()}}
    fun review(key:Int)=reviews[key]
    fun chooseRaw(key:Int,id:Int,keep:Boolean){val r=reviews[key]?:return;val anchor=textItems.lastOrNull{it.top<=offset};val relative=anchor?.let{offset-it.top}?:offset;reviews=reviews+(key to r.chooseRaw(id,keep));ocrTexts=ocrTexts.orEmpty()+(key to reviews.getValue(key).rendered().first);rebuild();val next=anchor?.let{a->textItems.firstOrNull{it.key==a.key}};if(next!=null)move(next.top+relative)}
    private var ocrPlan:List<OcrChunk>?=null
    private val exposed=mutableSetOf<Int>()
    private var streamLoading=false
    private var streamMessage="Préparation de la suite…"
    private var streamTail=0f
    /** Keep every previously exposed chunk immutable. Explicit mode switches can opt in to changes. */
    fun showOcrStream(plan:List<OcrChunk>,texts:Map<Int,String>,loading:Boolean,force:Boolean=false,message:String="Préparation de la suite…",reviewTexts:Map<Int,OcrReviewText> = emptyMap()){
        rememberExposed()
        val anchor=textItems.lastOrNull{it.top<=offset}?:textItems.firstOrNull()
        val relative=anchor?.let{offset-it.top}?:offset
        val old=ocrTexts.orEmpty()
        reviews=reviewTexts.mapValues{(key,value)->if(!force&&key in exposed&&reviews.containsKey(key)&&!texts[key].orEmpty().startsWith(old[key].orEmpty()))reviews.getValue(key)else value}
        ocrTexts=texts.mapValues{(key,value)->if(!force&&key in exposed&&old.containsKey(key)&&!value.startsWith(old.getValue(key)))old.getValue(key)else value}
        if(force)exposed.clear()
        ocrPlan=plan;streamLoading=loading;streamMessage=message;horizontal=0f;rebuild()
        val newAnchor=anchor?.let{a->textItems.firstOrNull{it.key==a.key}}
        if(newAnchor!=null)move(newAnchor.top+relative)else move(offset)
        rememberExposed()
    }
    private fun rememberExposed(){if(ocrPlan!=null){textItems.filter{it.top<=offset+height}.forEach{exposed+=it.key};tiles.filter{it.rect.top<=offset+height}.forEach{exposed+=it.chunkKey}}}

    private var fontSize=20f
    private val textPaint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(37,31,33);typeface=Typeface.create("serif",Typeface.NORMAL)}
    var onRequest:((Tile,Int)->Unit)?=null
    var onProgress:((Int,Int)->Unit)?=null
    /** End-of-page card: title ("Fin de la page N"); empty disables the card and the overscroll gesture. */
    var endTitle=""
    var hasNextPage=false
    var onReturnToPage:(()->Unit)?=null
    var onNextPage:(()->Unit)?=null
    internal var footerTop=0f
    private var overpull=0f
    private val overpullThreshold get()=110*density
    private fun footerHeight()=if(endTitle.isEmpty())0f else 244*density
    private var palettes=mapOf<Int,Int>()
    private data class Surface(val top:Float,val bottom:Float,val color:Int)
    private var surfaces=listOf<Surface>()
    private var bands=listOf<ReadBand>();private var pw=1;private var ph=1
    private var lineHeights=mapOf<Int,Float>();private var lineCounts=mapOf<Int,Int>()
    @Volatile private var tiles=listOf<Tile>();private var totalHeight=0f;@Volatile private var offset=0f;private var magnification=1f
    private val cacheBytes=(Runtime.getRuntime().maxMemory()/6).coerceIn(32L*1024*1024,96L*1024*1024).toInt()
    private val bitmaps=object:android.util.LruCache<Int,Bitmap>(cacheBytes){override fun sizeOf(key:Int,value:Bitmap)=value.byteCount}
    private val pending=mutableSetOf<Int>();private val failed=mutableSetOf<Int>();private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scroller=OverScroller(context);private val density=resources.displayMetrics.density
    @Volatile private var revision=0
    @Volatile private var horizontal=0f
    private var multiTouch=false
    private var comicDirection=0
    private var comicStates=mutableMapOf<Int,ComicReading.State>()
    private data class ComicFrame(val key:Int,val ordinal:Int,val source:Box,val top:Float,val bottom:Float,val scale:Float,val fit:Float,val left:Float,val fullWidth:Float)
    @Volatile private var comicFrames=listOf<ComicFrame>()
    @Volatile private var comicMaps=mapOf<Int,Tile>()
    private var comicCommon=1f
    private var comicZoom=1.5f
    private var gestureAxis=0;private var gestureKey:Int?=null
    private var gestureStartX=0f;private var gestureStartY=0f
    private var pinchKey:Int?=null
    private val comic get()=comicDirection!=0&&(ocrTexts==null||bands.all{it.kind==Kind.IMAGE})
    private val comicBar get()=if(comic)80*density else 0f
    private val readingHeight get()=height-comicBar
    private fun frameAt(y:Float)=comicFrames.firstOrNull{y>=it.top&&y<=it.bottom}
    private fun activeComic()=frameAt(offset+min(32*density,readingHeight/2))?:comicFrames.firstOrNull{it.bottom>=offset}?:comicFrames.lastOrNull()
    private fun state(f:ComicFrame)=comicStates[f.key]?:ComicReading.State()
    private fun comicAnchor(f:ComicFrame,screenY:Float)=((offset+screenY-comicBar-f.top)/(f.bottom-f.top)).coerceIn(0f,1f)
    private fun restoreComic(key:Int,sourceY:Float,screenY:Float){comicFrames.firstOrNull{it.key==key}?.let{move(it.top+sourceY*(it.bottom-it.top)-(screenY-comicBar))}}
    private fun toggleComic(f:ComicFrame,screenY:Float){
        val old=state(f);val at=comicAnchor(f,screenY)
        // Remember the viewport, not the tap location: Resume may be pressed elsewhere.
        val center=comicBar+readingHeight/2
        val returning=!old.detail&&old.scale>0
        comicStates[f.key]=if(old.detail)old.copy(detail=false,sourceY=comicAnchor(f,center))
            else old.copy(detail=true,scale=if(old.scale>0)old.scale else max(f.fit,comicCommon*comicZoom))
        rebuild();restoreComic(f.key,if(returning)old.sourceY else at,if(returning)center else screenY)
    }
    private fun panComic(key:Int,dx:Float){
        val f=comicFrames.firstOrNull{it.key==key}?:return;if(!state(f).detail)return
        val old=state(f);val before=ComicReading.pan(old.pan,f.source.width*pw,f.scale,width-16*density,comicDirection<0)
        val next=ComicReading.pan(before+dx,f.source.width*pw,f.scale,width-16*density,comicDirection<0)
        comicStates[key]=old.copy(pan=next)
        val delta=next-before;if(delta==0f)return
        tiles=tiles.map{if(it.chunkKey==key)it.copy(rect=RectF(it.rect).apply{offset(-delta,0f)})else it}
        comicFrames=comicFrames.map{if(it.key==key)it.copy(left=it.left-delta)else it};invalidate()
    }
    private fun zoomComic(key:Int,factor:Float,fx:Float,fy:Float){
        val f=comicFrames.firstOrNull{it.key==key}?:return
        val sy=comicAnchor(f,fy);val sx=((fx-f.left)/f.fullWidth).coerceIn(0f,1f)
        val next=(f.scale*factor).coerceIn(f.fit,max(f.fit,comicCommon*4f))
        val full=f.source.width*pw*next
        val pan=ComicReading.pan(8*density+sx*full-fx,f.source.width*pw,next,width-16*density,comicDirection<0)
        comicStates[key]=state(f).copy(detail=true,scale=next,pan=pan,sourceY=sy)
        comicZoom=(next/comicCommon).coerceIn(.5f,4f);zoomUsed=true
        rebuild();restoreComic(key,sy,fy)
    }
    // Pinch zoom: preview live by scaling the canvas around the gesture's focal point, then
    // rebuild the tile grid, layouts and bitmap cache exactly once when the gesture ends.
    @Volatile private var pinchLive=false
    private var pinchScale=1f;private var pinchFx=0f;private var pinchFy=0f
    private val scaling=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScaleBegin(d:ScaleGestureDetector):Boolean{pinchKey=if(comic)frameAt(offset+d.focusY-comicBar)?.key else null;if(comic&&pinchKey==null)return false;pinchLive=true;pinchScale=1f;pinchFx=d.focusX;pinchFy=d.focusY;return true}
        override fun onScale(d:ScaleGestureDetector):Boolean{
            val cumulative=pinchScale*d.scaleFactor
            pinchScale=if(comic){val f=comicFrames.firstOrNull{it.key==pinchKey};if(f==null)1f else cumulative.coerceIn(f.fit/f.scale,max(f.fit,comicCommon*4f)/f.scale)} else if(ocrTexts!=null)cumulative.coerceIn(14f/fontSize,38f/fontSize) else cumulative.coerceIn(1f/magnification,2f/magnification)
            invalidate();return true
        }
        override fun onScaleEnd(d:ScaleGestureDetector){commitPinch()}
    })
    private fun commitPinch(){
        if(!pinchLive)return
        val s=pinchScale;pinchLive=false;pinchScale=1f
        if(comic){if(abs(s-1f)>.001f)pinchKey?.let{zoomComic(it,s,pinchFx,pinchFy)};pinchKey=null;invalidate();return}
        if(s<0.999f||s>1.001f){
            // The live preview holds the content point under the focus fixed; keep that same
            // point under the focus after the rebuild instead of jumping to the scroll fraction.
            val contentY=offset+pinchFy
            val before=if(ocrTexts!=null)fontSize else magnification
            zoomBy(s)
            val after=if(ocrTexts!=null)fontSize else magnification
            if(before>0)move(contentY*(after/before)-pinchFy)
        }else invalidate()
    }
    private val gestures=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
        override fun onDown(e:MotionEvent):Boolean{scroller.forceFinished(true);overpull=0f;return true}
        override fun onScroll(a:MotionEvent?,b:MotionEvent,dx:Float,dy:Float):Boolean{
            if(comic){
                if(gestureAxis==0)gestureAxis=ComicReading.axis(b.x-gestureStartX,b.y-gestureStartY,ViewConfiguration.get(context).scaledTouchSlop.toFloat())
                if(gestureAxis==1){gestureKey?.let{panComic(it,dx)};return true}
                if(gestureAxis==0)return true
            }
            horizontal=if(ocrTexts!=null||comic)0f else (horizontal+dx).coerceIn(-max(0f,(width*magnification-width)/2),max(0f,(width*magnification-width)/2))
            val maxOff=max(0f,totalHeight-readingHeight)
            // A plain scroll stops at the end; only a sustained pull beyond it arms the next-page gesture.
            if(dy>0&&offset>=maxOff-2f&&hasNextPage&&onNextPage!=null)overpull+=dy else if(dy<0)overpull=0f
            move(offset+dy);invalidate();return true
        }
        override fun onSingleTapUp(e:MotionEvent):Boolean {
            if(comic&&e.y<comicBar){if(e.x>=width-120*density&&e.y<=44*density)activeComic()?.let{toggleComic(it,comicBar+min(readingHeight/2,180*density))};return true}
            if(footerHeight()>0){
                val fy=e.y+offset-comicBar;val top=footerTop+8*density
                if(fy>=top+72*density&&fy<=top+126*density){onReturnToPage?.invoke();return true}
                if(hasNextPage&&fy>=top+138*density&&fy<=top+192*density){onNextPage?.invoke();return true}
            }
            if(comic){
                val f=if(e.y<comicBar)activeComic()else frameAt(e.y+offset-comicBar)
                if(f!=null){toggleComic(f,if(e.y<comicBar)comicBar+min(readingHeight/2,180*density)else e.y);return true}
                return false
            }
            val x=e.x-16*density;val y=e.y+offset
            for(item in textItems){if(y<item.top||y>item.top+item.layout.height)continue
                val layout=item.layout;val line=layout.getLineForVertical((y-item.top).toInt());val start=layout.getLineStart(line);val end=layout.getLineEnd(line)
                for(mark in reviews[item.key]?.rendered()?.second.orEmpty())if(mark.end>=start&&mark.start<=end){val left=layout.getPrimaryHorizontal(max(start,mark.start));val right=layout.getPrimaryHorizontal(min(end,mark.end));if(x>=min(left,right)-10*density&&x<=max(left,right)+10*density){onOcrMark?.invoke(item.key,mark.edit.id,e.x,e.y);return true}}
            };return false
        }
        override fun onFling(a:MotionEvent?,b:MotionEvent,vx:Float,vy:Float):Boolean{if(comic&&gestureAxis!=2)return true;scroller.fling(0,offset.roundToInt(),0,(-vy).roundToInt(),0,0,0,max(0f,totalHeight-readingHeight).roundToInt());postInvalidateOnAnimation();return true}
    })
    init{gestures.setOnDoubleTapListener(null);setBackgroundColor(Color.WHITE);isFocusable=true;contentDescription="Lecture continue. Glissez verticalement pour lire les blocs dans votre ordre."}
    fun setDocument(zones:List<Zone>,pageWidth:Int,pageHeight:Int,position:Int=0,backgrounds:Map<Int,Int> = emptyMap(),comicDirection:Int=0){clear();this.comicDirection=comicDirection;palettes=backgrounds;bands=ContinuousLayout.bands(zones);pw=pageWidth;ph=pageHeight;offset=position.toFloat();lineHeights=zones.mapNotNull{z->ReadingScale.lineHeight(z.lineBottoms,z.box.height)?.let{z.id to it}}.toMap();lineCounts=zones.mapNotNull{z->if(z.lineBottoms.isEmpty())null else z.id to z.lineBottoms.size}.toMap();rebuild()}
    /** True after a reading-mode pinch has committed. */
    var zoomUsed=false;private set
    fun zoomBy(f:Float){if(comic){activeComic()?.let{zoomComic(it.key,f,width/2f,comicBar+readingHeight/2)};return};zoomUsed=true;if(ocrTexts!=null){val fraction=if(totalHeight>0)offset/totalHeight else 0f;fontSize=(fontSize*f).coerceIn(14f,38f);rebuild();move(fraction*totalHeight);return};val fraction=if(totalHeight>0)offset/totalHeight else 0f;magnification=(magnification*f).coerceIn(1f,2f);val limit=max(0f,(width*magnification-width)/2);horizontal=horizontal.coerceIn(-limit,limit);rebuild();move(fraction*totalHeight)}
    fun clear(){comicDirection=0;comicStates.clear();comicFrames=emptyList();comicMaps=emptyMap();comicZoom=1.5f;pinchKey=null;ocrPlan=null;reviews=emptyMap();exposed.clear();streamLoading=false;ocrTexts=null;textItems=emptyList();revision++;pending.clear();failed.clear();bitmaps.evictAll();tiles=emptyList();bands=emptyList();surfaces=emptyList();palettes=emptyMap();offset=0f;horizontal=0f;magnification=1f;totalHeight=0f;overpull=0f;scroller.forceFinished(true);invalidate()}
    /** Worker-side guard: discard queued work made obsolete by zoom, mode or a long swipe. */
    fun needsRender(tile:Tile,expected:Int)=expected==revision&&(comicMaps[tile.chunkKey]?.key==tile.key||tile.rect.bottom>=offset-height*.5f&&tile.rect.top<=offset+height*1.5f&&tile.rect.right>=horizontal-width*.25f&&tile.rect.left<=horizontal+width*1.25f)
    fun skipRequest(tile:Tile,expected:Int){if(expected==revision){pending.remove(tile.key);invalidate()}}
    fun accept(tile:Tile,expected:Int,bitmap:Bitmap?){if(expected!=revision||tiles.none{it.key==tile.key}&&comicMaps.values.none{it.key==tile.key}){bitmap?.recycle();return};pending.remove(tile.key);if(bitmap!=null)bitmaps.put(tile.key,bitmap)else failed.add(tile.key);invalidate()}
    fun showOcr(texts:Map<Int,String>?){reviews=emptyMap();ocrPlan=null;exposed.clear();streamLoading=false;val fraction=if(totalHeight>0)offset/totalHeight else 0f;ocrTexts=texts;horizontal=0f;rebuild();move(fraction*totalHeight)}
    fun reprocess(){rebuild()}
    fun currentPosition()=offset.roundToInt()
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){
        val atEnd=oldh>0&&totalHeight>oldh&&offset>=totalHeight-oldh-2f
        val anchor=if(comic&&oldh>0)frameAt(offset+(oldh-comicBar)/2)else null
        val sy=anchor?.let{((offset+(oldh-comicBar)/2-it.top)/(it.bottom-it.top)).coerceIn(0f,1f)}
        super.onSizeChanged(w,h,oldw,oldh)
        if(w>0&&h>0){rebuild();if(anchor!=null&&sy!=null)restoreComic(anchor.key,sy,comicBar+readingHeight/2)else if(atEnd)move(totalHeight-readingHeight)}
    }
    /** Called on the UI thread. The snapshot is detached from scrolling and later mode changes. */
    fun articleImagePlan():ArticleImagePlan{
        check(!streamLoading){"Attendez la fin de la préparation OCR pour partager tous les blocs."}
        check(tiles.isNotEmpty()||textItems.isNotEmpty()){"Aucun bloc à partager."}
        if(comic){
            var y=0f;val output=mutableListOf<Tile>();val backgrounds=mutableListOf<ArticleImagePlan.BandSurface>()
            // Include every selected block, including manually added text, at its complete width.
            // Pan and the fixed minimap bar are presentation state, never export cropping.
            for((_,group)in tiles.groupBy{it.ordinal}.toSortedMap()){
                val top=group.minOf{it.rect.top};val bottom=group.maxOf{it.rect.bottom}
                val left=group.minOf{it.rect.left};val right=group.maxOf{it.rect.right}
                val frame=comicFrames.firstOrNull{it.key==group.first().chunkKey}
                val factor=if(frame!=null)frame.fit/frame.scale else min(1f,(width-16*density)/(right-left))
                val x=(width-(right-left)*factor)/2
                for(t in group)output+=t.copy(rect=RectF(x+(t.rect.left-left)*factor,y+(t.rect.top-top)*factor,x+(t.rect.right-left)*factor,y+(t.rect.bottom-top)*factor))
                val h=(bottom-top)*factor;backgrounds+=ArticleImagePlan.BandSurface(y,y+h,palettes[group.first().chunkKey]?:Color.WHITE);y+=h+6*density
            }
            return ArticleImagePlan(width,y,output,emptyList(),backgrounds,density)
        }
        val right=surfaces.maxOfOrNull{it.bottom}?:0f
        return ArticleImagePlan(width,max(right,streamTail),tiles.map{it.copy(rect=RectF(it.rect))},textItems.toList(),surfaces.map{ArticleImagePlan.BandSurface(it.top,it.bottom,it.color)},density)
    }
    private fun rebuild(){
        revision++;pending.clear();failed.clear();bitmaps.evictAll();if(width<=0)return
        val surfaceOut=mutableListOf<Surface>();val out=mutableListOf<Tile>();val textOut=mutableListOf<TextItem>();val done=mutableSetOf<Int>();var y=0f;var last=-1
        var previousRaster:ReadBand?=null;var previousScale=0f
        val frameOut=mutableListOf<ComicFrame>();val mapOut=mutableMapOf<Int,Tile>()
        val activeBands=ocrPlan?.let{plan->ContinuousLayout.bands(plan.map{Zone(it.key,it.box,it.kind)}).map{it.copy(ordinal=plan[it.ordinal].ordinal)}}?:bands
        // Text-proportion scaling: one shared scale for every text band, so blocks with the
        // same source text size render the same size and others keep the original proportions.
        val textFills=activeBands.filter{it.kind==Kind.TEXT}.map{it.zoneId to (width-16*density)/(it.original.width*pw)}
        val uniform=ReadingScale.uniform(textFills.mapNotNull{(id,f)->lineHeights[id]?.let{f to (lineCounts[id]?:1)}})
        val bodyLineHeight=ReadingScale.median(textFills.mapNotNull{(id,_)->lineHeights[id]?.let{it to (lineCounts[id]?:1)}})
        comicCommon=ComicReading.commonScale(activeBands.filter{it.kind==Kind.IMAGE}.distinctBy{it.zoneId}.map{it.original.width*pw},width-16*density)
        for(b in activeBands){
            val recognized=ocrTexts?.get(b.zoneId)
            if(b.kind==Kind.TEXT&&!recognized.isNullOrBlank()){
                if(!done.add(b.zoneId))continue
                if(last>=0&&last!=b.ordinal)y+=6*density;last=b.ordinal
                textPaint.color=ReadingPalette.foreground(palettes[b.zoneId]?:Color.WHITE)
                textPaint.textSize=fontSize*resources.displayMetrics.scaledDensity
                val layout=StaticLayout.Builder.obtain(recognized,0,recognized.length,TextPaint(textPaint),max(1,width-32*density.toInt())).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true).setLineSpacing(4*density,1.12f).build()
                previousRaster=null
                textOut+=TextItem(layout,y,b.ordinal,b.zoneId);surfaceOut+=Surface(y-6*density,y+layout.height,palettes[b.zoneId]?:Color.WHITE);y+=layout.height
                continue
            }
            val fill=(width-16*density)/(b.original.width*pw)
            var scale=if(b.kind==Kind.TEXT)ReadingScale.textScale(fill,uniform?:fill,lineHeights[b.zoneId],bodyLineHeight)*magnification else fill
            if(b.kind==Kind.IMAGE)scale=min(scale,min((width-16*density)/(.5f*pw),max(300f,height*.9f)/(b.original.height*ph)))
            val cs=comicStates[b.zoneId]?:ComicReading.State()
            val cf=ComicReading.fit(b.original.width*pw,(b.bottom-b.top)*ph,width-16*density,max(1f,readingHeight*.85f))
            if(comic&&b.kind==Kind.IMAGE)scale=if(cs.detail)max(cf,if(cs.scale>0)cs.scale else comicCommon*comicZoom)else cf
            // Adjacent pieces of the same source column must remain pixel-contiguous,
            // even when overlap subtraction assigned them to different selected blocks.
            // A block spacer here used to split glyphs crossing the overlap boundary.
            val previous=previousRaster
            val continuousSource=previous!=null&&previous.kind==Kind.TEXT&&b.kind==Kind.TEXT&&
                abs(previous.bottom-b.top)<.00001f&&abs(previous.original.left-b.original.left)<.00001f&&
                abs(previous.original.right-b.original.right)<.00001f&&abs(previousScale-scale)<.0001f
            if(b.ordinal!=last){if(last>=0&&!continuousSource)y+=6*density;last=b.ordinal}
            previousRaster=b;previousScale=scale
            val bandHeight=(b.bottom-b.top)*ph*scale;surfaceOut+=Surface(y-6*density,y+bandHeight,palettes[b.zoneId]?:Color.WHITE);val fullWidth=b.original.width*pw*scale;val left=if(comic&&b.kind==Kind.IMAGE&&fullWidth>width-16*density)8*density-ComicReading.pan(cs.pan,b.original.width*pw,scale,width-16*density,comicDirection<0)else (width-fullWidth)/2
            if(comic&&b.kind==Kind.IMAGE){
                frameOut+=ComicFrame(b.zoneId,b.ordinal,b.original,y,y+bandHeight,scale,cf,left,fullWidth)
                val mw=76*density;val mh=48*density;val ms=min(mw/(b.original.width*pw),mh/(b.original.height*ph))
                mapOut[b.zoneId]=Tile(1000000+mapOut.size,b.original,RectF(0f,0f,b.original.width*pw*ms,b.original.height*ph*ms),b.ordinal,Kind.IMAGE,b.zoneId)
            }
            // Two-dimensional tiles: a zoomed column no longer decodes an entire off-screen width.
            val tileHeight=512f
            val rows=max(1,ceil(bandHeight/tileHeight).toInt())
            for(i in 0 until rows){val t=b.top+(b.bottom-b.top)*i/rows;val bottom=b.top+(b.bottom-b.top)*(i+1)/rows
                for(p in b.pieces){
                    val columns=max(1,ceil((p.right-p.left)*pw*scale/512f).toInt())
                    for(j in 0 until columns){val l=p.left+(p.right-p.left)*j/columns;val r=p.left+(p.right-p.left)*(j+1)/columns
                        val rect=RectF(left+(l-b.original.left)*pw*scale,y+(t-b.top)*ph*scale,left+(r-b.original.left)*pw*scale,y+(bottom-b.top)*ph*scale)
                        out+=Tile(out.size,Box(l,t,r,bottom),rect,b.ordinal,b.kind,b.zoneId)
                    }
                }
            }
            y+=bandHeight
        }
        streamTail=y;if(streamLoading)y+=72*density
        footerTop=y+24*density
        comicFrames=frameOut;comicMaps=mapOut;surfaces=surfaceOut;textItems=textOut;tiles=out;totalHeight=footerTop+footerHeight();move(offset);invalidate()
    }
    internal fun move(value:Float){offset=value.coerceIn(0f,max(0f,totalHeight-readingHeight));val ordinal=(tiles.filter{it.rect.bottom>=offset}.map{it.rect.top to it.ordinal}+textItems.filter{it.top+it.layout.height>=offset}.map{it.top to it.ordinal}).minByOrNull{it.first}?.second?:0;val atEnd=offset>=max(0f,totalHeight-readingHeight)-1f&&offset>0f;val shown=if(atEnd)max(ordinal,max(tiles.maxOfOrNull{it.ordinal}?:0,textItems.maxOfOrNull{it.ordinal}?:0))else ordinal;onProgress?.invoke(shown,offset.roundToInt());invalidate()}
    override fun onDraw(c:Canvas){
        super.onDraw(c);c.save();c.clipRect(0,0,width,height)
        c.translate(0f,comicBar);if(comic)c.clipRect(0f,0f,width.toFloat(),readingHeight)
        if(!comic&&pinchLive&&pinchScale!=1f)c.scale(pinchScale,pinchScale,pinchFx,pinchFy)
        c.translate(-horizontal,-offset)
        paint.color=(surfaces.firstOrNull{it.bottom>=offset}?:surfaces.lastOrNull())?.color?:Color.WHITE;c.drawRect(horizontal,offset,horizontal+width,offset+height,paint)
        surfaces.lastOrNull()?.let{paint.color=it.color;c.drawRect(horizontal,max(offset,it.bottom),horizontal+width,offset+height,paint)}
        for(surface in surfaces)if(surface.bottom>=offset&&surface.top<=offset+height){paint.color=surface.color;c.drawRect(horizontal,surface.top,horizontal+width,surface.bottom,paint)}
        for(item in textItems)if(item.top+item.layout.height>=offset&&item.top<=offset+height){c.save();c.translate(16*density,item.top);item.layout.draw(c)
            val underline=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(87,117,107);strokeWidth=density;style=Paint.Style.STROKE;pathEffect=DashPathEffect(floatArrayOf(2*density,3*density),0f)}
            for(mark in reviews[item.key]?.rendered()?.second.orEmpty())for(line in 0 until item.layout.lineCount){val start=max(mark.start,item.layout.getLineStart(line));val end=min(mark.end,item.layout.getLineEnd(line));if(end<start||end==start&&mark.end!=mark.start)continue;val left=item.layout.getPrimaryHorizontal(start);val right=item.layout.getPrimaryHorizontal(end);val y=item.layout.getLineBaseline(line)+3*density;c.drawLine(left,y,max(left+5*density,right),y,underline)}
            c.restore()}
        val visible=tiles.filter{it.rect.bottom>=offset&&it.rect.top<=offset+height&&it.rect.right>=horizontal&&it.rect.left<=horizontal+width}
        // Draw all available visible pixels before touching the cache's prefetch entries.
        for(tile in visible){
            val image=bitmaps.get(tile.key)
            if(image==null||image.isRecycled){paint.color=palettes[tile.chunkKey]?:Color.WHITE;paint.isAntiAlias=false;c.drawRect(tile.rect,paint);paint.isAntiAlias=true}
            if(image!=null&&!image.isRecycled){
                c.save()
                if(comic&&pinchLive&&tile.chunkKey==pinchKey){comicFrames.firstOrNull{it.key==pinchKey}?.let{c.clipRect(0f,it.top,width.toFloat(),it.bottom)};c.scale(pinchScale,pinchScale,pinchFx,pinchFy-comicBar+offset)}
                val dest=if(comic)RectF((tile.rect.left-horizontal).roundToInt()+horizontal,(tile.rect.top-offset).roundToInt()+offset,(tile.rect.right-horizontal).roundToInt()+horizontal,(tile.rect.bottom-offset).roundToInt()+offset)else tile.rect
                paint.isAntiAlias=false;c.drawBitmap(image,null,dest,paint);paint.isAntiAlias=true;c.restore()
            }
            if(tile.key in failed){paint.color=Color.DKGRAY;paint.textSize=14*density;c.drawText("Rendu indisponible · revenez à la page",tile.rect.left+8*density,tile.rect.top+24*density,paint)}
        }
        val near=tiles.filter{it.rect.bottom>=offset-height*.5f&&it.rect.top<=offset+height*1.5f&&it.rect.right>=horizontal-width*.25f&&it.rect.left<=horizontal+width*1.25f}
        val ordered=near.sortedWith(compareBy<Tile>{if(it in visible)0 else 1}.thenBy{abs(it.rect.centerY()-(offset+height*.5f))+abs(it.rect.centerX()-(horizontal+width*.5f))})
        var bytes=0L;val budget=cacheBytes*.8f
        for(tile in ordered){
            val cost=ceil(tile.rect.width()).toLong().coerceAtLeast(1)*ceil(tile.rect.height()).toLong().coerceAtLeast(1)*4
            if(tile !in visible&&bytes+cost>budget)continue
            bytes+=cost
            if(tile.key !in failed&&bitmaps.get(tile.key)==null&&pending.add(tile.key))onRequest?.invoke(tile,revision)
        }
        if(streamLoading&&streamTail<=offset+height){paint.color=Color.DKGRAY;paint.textSize=14*density;c.drawText(streamMessage,16*density,streamTail+30*density,paint)}
        if(footerHeight()>0&&footerTop<=offset+height)drawEndCard(c)
        c.restore()
        if(comic)drawComicBar(c)
        if(overpull>8*density){// Deliberate pull past the end: releasing opens the next page's preparation.
            val ready=overpull>=overpullThreshold
            val label="Relâchez pour préparer la page suivante ›"
            paint.textSize=15*density;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
            val tw=paint.measureText(label);val bw=tw+36*density;val bh=44*density;val bx=(width-bw)/2;val by=height-bh-28*density
            paint.style=Paint.Style.FILL;paint.color=Color.rgb(50,109,93);paint.alpha=(120+135*min(1f,overpull/overpullThreshold)).roundToInt()
            c.drawRoundRect(RectF(bx,by,bx+bw,by+bh),bh/2,bh/2,paint);paint.alpha=255
            paint.color=if(ready)Color.WHITE else Color.argb(230,255,255,255);c.drawText(label,bx+18*density,by+bh/2+5*density,paint)
            paint.style=Paint.Style.FILL
        }
        rememberExposed()
    }
    private fun drawComicBar(c:Canvas){
        val f=activeComic()?:return;val detail=state(f).detail
        paint.style=Paint.Style.FILL;paint.color=Color.WHITE;c.drawRect(0f,0f,width.toFloat(),comicBar,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textAlign=Paint.Align.LEFT;paint.textSize=14*density;paint.color=Color.rgb(37,31,33)
        c.drawText("Bloc ${f.ordinal+1} · "+if(detail)"Détail" else "Vue entière",12*density,24*density,paint)
        val label=if(detail)"Vue entière" else if(state(f).scale>0)"Reprendre" else "Agrandir"
        paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.color=Color.rgb(50,109,93)
        val tw=paint.measureText(label);c.drawText(label,width-12*density-tw,24*density,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=11*density;paint.color=Color.DKGRAY
        c.drawText(if(detail)"↔ Explorer · ↕ Continuer" else "Appui bref : lire plus grand",12*density,57*density,paint)
        if(detail){comicMaps[f.key]?.let{t->
            val left=width-12*density-t.rect.width();val top=32*density
            val dest=RectF(left,top,left+t.rect.width(),top+t.rect.height())
            val image=bitmaps.get(t.key)
            if(image!=null&&!image.isRecycled)c.drawBitmap(image,null,dest,paint)else if(pending.add(t.key))onRequest?.invoke(t,revision)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=density;paint.color=Color.GRAY;c.drawRect(dest,paint)
            val l=((0f-f.left)/f.fullWidth).coerceIn(0f,1f);val r=((width-f.left)/f.fullWidth).coerceIn(0f,1f)
            val a=((offset-f.top)/(f.bottom-f.top)).coerceIn(0f,1f);val b=((offset+readingHeight-f.top)/(f.bottom-f.top)).coerceIn(0f,1f)
            paint.color=Color.rgb(50,109,93);paint.strokeWidth=2*density
            c.drawRect(left+l*dest.width(),top+a*dest.height(),left+r*dest.width(),top+b*dest.height(),paint);paint.style=Paint.Style.FILL
        }}
        paint.color=Color.LTGRAY;c.drawLine(0f,comicBar-1,width.toFloat(),comicBar-1,paint)
    }
    private fun drawEndCard(c:Canvas){
        val l=horizontal+8*density;val r=horizontal+width-8*density;val top=footerTop+8*density;val bottom=footerTop+236*density
        val green=Color.rgb(50,109,93);val grey=Color.rgb(120,118,112)
        paint.style=Paint.Style.FILL;paint.color=Color.WHITE;c.drawRoundRect(RectF(l,top,r,bottom),18*density,18*density,paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=density;paint.color=Color.rgb(205,203,198);c.drawRoundRect(RectF(l,top,r,bottom),18*density,18*density,paint);paint.style=Paint.Style.FILL
        paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.color=Color.rgb(37,31,33);paint.textSize=20*density
        c.drawText(endTitle,(l+r)/2,top+34*density,paint)
        paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=13*density;paint.color=grey
        c.drawText(if(hasNextPage)"Revenez à la page ou préparez la suite." else "Dernière page du document.",(l+r)/2,top+56*density,paint)
        val bl=l+16*density;val br=r-16*density
        val b1=RectF(bl,top+72*density,br,top+126*density)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density;paint.color=green;c.drawRoundRect(b1,16*density,16*density,paint);paint.style=Paint.Style.FILL
        paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.textSize=16*density;paint.color=green
        c.drawText("Revenir à la page",(l+r)/2,b1.centerY()+6*density,paint)
        if(hasNextPage){
            val b2=RectF(bl,top+138*density,br,top+192*density)
            paint.color=green;c.drawRoundRect(b2,16*density,16*density,paint)
            paint.color=Color.WHITE;c.drawText("Préparer la page suivante  ›",(l+r)/2,b2.centerY()+6*density,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.color=grey
            val caption="Elle s'ouvre pour choisir ses blocs, sans lancer sa lecture."
            var captionSize=12*density;paint.textSize=captionSize
            val available=r-l-32*density
            while(paint.measureText(caption)>available&&captionSize>9*density){captionSize-=density;paint.textSize=captionSize}
            c.drawText(caption,(l+r)/2,top+212*density,paint)
        }
        paint.textAlign=Paint.Align.LEFT
    }
    override fun computeScroll(){if(scroller.computeScrollOffset()){move(scroller.currY.toFloat());postInvalidateOnAnimation()}}
    override fun onTouchEvent(e:MotionEvent):Boolean{
        if(e.actionMasked==MotionEvent.ACTION_DOWN){multiTouch=false;gestureAxis=0;gestureStartX=e.x;gestureStartY=e.y;gestureKey=if(comic)frameAt(e.y+offset-comicBar)?.key else null}
        if(e.pointerCount>1&&!multiTouch){multiTouch=true;overpull=0f;val cancel=MotionEvent.obtain(e);cancel.action=MotionEvent.ACTION_CANCEL;gestures.onTouchEvent(cancel);cancel.recycle()}
        if(e.actionMasked==MotionEvent.ACTION_CANCEL&&comic){pinchLive=false;pinchScale=1f;pinchKey=null}
        scaling.onTouchEvent(e);if(!multiTouch&&!scaling.isInProgress)gestures.onTouchEvent(e)
        if(e.actionMasked==MotionEvent.ACTION_UP){commitPinch();if(overpull>=overpullThreshold){overpull=0f;onNextPage?.invoke()}else if(overpull>0f){overpull=0f;invalidate()};performClick()}
        if(e.actionMasked==MotionEvent.ACTION_CANCEL){if(comic){pinchLive=false;pinchScale=1f;pinchKey=null;invalidate()}else commitPinch();if(overpull>0f){overpull=0f;invalidate()}}
        return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}
