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
    private var ocrPlan:List<OcrChunk>?=null
    private val exposed=mutableSetOf<Int>()
    private var streamLoading=false
    private var streamMessage="Préparation de la suite…"
    private var streamTail=0f
    /** Keep every previously exposed chunk immutable. Explicit mode switches can opt in to changes. */
    fun showOcrStream(plan:List<OcrChunk>,texts:Map<Int,String>,loading:Boolean,force:Boolean=false,message:String="Préparation de la suite…"){
        rememberExposed()
        val anchor=textItems.lastOrNull{it.top<=offset}?:textItems.firstOrNull()
        val relative=anchor?.let{offset-it.top}?:offset
        val old=ocrTexts.orEmpty()
        ocrTexts=texts.mapValues{(key,value)->if(!force&&key in exposed&&old.containsKey(key))old.getValue(key)else value}
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
    private var bands=listOf<ReadBand>();private var pw=1;private var ph=1
    @Volatile private var tiles=listOf<Tile>();private var totalHeight=0f;@Volatile private var offset=0f;private var magnification=1f
    private val cacheBytes=(Runtime.getRuntime().maxMemory()/6).coerceIn(32L*1024*1024,96L*1024*1024).toInt()
    private val bitmaps=object:android.util.LruCache<Int,Bitmap>(cacheBytes){override fun sizeOf(key:Int,value:Bitmap)=value.byteCount}
    private val pending=mutableSetOf<Int>();private val failed=mutableSetOf<Int>();private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scroller=OverScroller(context);private val density=resources.displayMetrics.density
    @Volatile private var revision=0
    @Volatile private var horizontal=0f
    private val scaling=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(d:ScaleGestureDetector):Boolean{zoomBy(d.scaleFactor);return true}})
    private val gestures=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
        override fun onDown(e:MotionEvent):Boolean{scroller.forceFinished(true);return true}
        override fun onScroll(a:MotionEvent?,b:MotionEvent,dx:Float,dy:Float):Boolean{horizontal=if(ocrTexts!=null)0f else (horizontal+dx).coerceIn(-max(0f,(width*magnification-width)/2),max(0f,(width*magnification-width)/2));move(offset+dy);return true}
        override fun onFling(a:MotionEvent?,b:MotionEvent,vx:Float,vy:Float):Boolean{scroller.fling(0,offset.roundToInt(),0,(-vy).roundToInt(),0,0,0,max(0f,totalHeight-height).roundToInt());postInvalidateOnAnimation();return true}
    })
    init{setBackgroundColor(Color.WHITE);isFocusable=true;contentDescription="Lecture continue. Glissez verticalement pour lire les blocs dans votre ordre."}
    fun setDocument(zones:List<Zone>,pageWidth:Int,pageHeight:Int,position:Int=0){clear();bands=ContinuousLayout.bands(zones);pw=pageWidth;ph=pageHeight;offset=position.toFloat();rebuild()}
    fun zoomBy(f:Float){if(ocrTexts!=null){val fraction=if(totalHeight>0)offset/totalHeight else 0f;fontSize=(fontSize*f).coerceIn(14f,38f);rebuild();move(fraction*totalHeight);return};val fraction=if(totalHeight>0)offset/totalHeight else 0f;magnification=(magnification*f).coerceIn(1f,2f);val limit=max(0f,(width*magnification-width)/2);horizontal=horizontal.coerceIn(-limit,limit);rebuild();move(fraction*totalHeight)}
    fun clear(){ocrPlan=null;exposed.clear();streamLoading=false;ocrTexts=null;textItems=emptyList();revision++;pending.clear();failed.clear();bitmaps.evictAll();tiles=emptyList();bands=emptyList();offset=0f;horizontal=0f;magnification=1f;totalHeight=0f;scroller.forceFinished(true);invalidate()}
    /** Worker-side guard: discard queued work made obsolete by zoom, mode or a long swipe. */
    fun needsRender(tile:Tile,expected:Int)=expected==revision&&tile.rect.bottom>=offset-height*.5f&&tile.rect.top<=offset+height*1.5f&&tile.rect.right>=horizontal-width*.25f&&tile.rect.left<=horizontal+width*1.25f
    fun skipRequest(tile:Tile,expected:Int){if(expected==revision){pending.remove(tile.key);invalidate()}}
    fun accept(tile:Tile,expected:Int,bitmap:Bitmap?){if(expected!=revision||tiles.none{it.key==tile.key}){bitmap?.recycle();return};pending.remove(tile.key);if(bitmap!=null)bitmaps.put(tile.key,bitmap)else failed.add(tile.key);invalidate()}
    fun showOcr(texts:Map<Int,String>?){ocrPlan=null;exposed.clear();streamLoading=false;val fraction=if(totalHeight>0)offset/totalHeight else 0f;ocrTexts=texts;horizontal=0f;rebuild();move(fraction*totalHeight)}
    fun reprocess(){rebuild()}
    fun currentPosition()=offset.roundToInt()
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){val atEnd=oldh>0&&totalHeight>oldh&&offset>=totalHeight-oldh-2f;super.onSizeChanged(w,h,oldw,oldh);if(w>0&&h>0){rebuild();if(atEnd)move(totalHeight-h)}}
    private fun rebuild(){
        revision++;pending.clear();failed.clear();bitmaps.evictAll();if(width<=0)return
        val out=mutableListOf<Tile>();val textOut=mutableListOf<TextItem>();val done=mutableSetOf<Int>();var y=0f;var last=-1
        val activeBands=ocrPlan?.let{plan->ContinuousLayout.bands(plan.map{Zone(it.key,it.box,it.kind)}).map{it.copy(ordinal=plan[it.ordinal].ordinal)}}?:bands
        for(b in activeBands){
            val recognized=ocrTexts?.get(b.zoneId)
            if(b.kind==Kind.TEXT&&!recognized.isNullOrBlank()){
                if(!done.add(b.zoneId))continue
                if(last>=0&&last!=b.ordinal)y+=6*density;last=b.ordinal
                textPaint.textSize=fontSize*resources.displayMetrics.scaledDensity
                val layout=StaticLayout.Builder.obtain(recognized,0,recognized.length,textPaint,max(1,width-32*density.toInt())).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true).setLineSpacing(4*density,1.12f).build()
                textOut+=TextItem(layout,y,b.ordinal,b.zoneId);y+=layout.height
                continue
            }
            if(b.ordinal!=last){if(last>=0&&last!=b.ordinal)y+=6*density;last=b.ordinal}
            var scale=(width-16*density)/(b.original.width*pw)*if(b.kind==Kind.TEXT)magnification else 1f
            if(b.kind==Kind.IMAGE)scale=min(scale,max(300f,height*.9f)/(b.original.height*ph))
            val bandHeight=(b.bottom-b.top)*ph*scale;val fullWidth=b.original.width*pw*scale;val left=(width-fullWidth)/2
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
        textItems=textOut;tiles=out;totalHeight=y+24*density;move(offset);invalidate()
    }
    private fun move(value:Float){offset=value.coerceIn(0f,max(0f,totalHeight-height));val ordinal=(tiles.filter{it.rect.bottom>=offset}.map{it.rect.top to it.ordinal}+textItems.filter{it.top+it.layout.height>=offset}.map{it.top to it.ordinal}).minByOrNull{it.first}?.second?:0;onProgress?.invoke(ordinal,offset.roundToInt());invalidate()}
    override fun onDraw(c:Canvas){
        super.onDraw(c);c.save();c.translate(-horizontal,-offset)
        for(item in textItems)if(item.top+item.layout.height>=offset&&item.top<=offset+height){c.save();c.translate(16*density,item.top);item.layout.draw(c);c.restore()}
        val visible=tiles.filter{it.rect.bottom>=offset&&it.rect.top<=offset+height&&it.rect.right>=horizontal&&it.rect.left<=horizontal+width}
        // Draw all available visible pixels before touching the cache's prefetch entries.
        for(tile in visible){
            paint.color=Color.WHITE;c.drawRect(tile.rect,paint)
            val image=bitmaps.get(tile.key);if(image!=null&&!image.isRecycled)c.drawBitmap(image,null,tile.rect,paint)
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
        c.restore();rememberExposed()
    }
    override fun computeScroll(){if(scroller.computeScrollOffset()){move(scroller.currY.toFloat());postInvalidateOnAnimation()}}
    override fun onTouchEvent(e:MotionEvent):Boolean{scaling.onTouchEvent(e);if(!scaling.isInProgress)gestures.onTouchEvent(e);if(e.action==MotionEvent.ACTION_UP)performClick();return true}
    override fun performClick():Boolean{super.performClick();return true}
}
