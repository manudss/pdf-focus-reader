package com.pdffocus.reader
import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.OverScroller
import kotlin.math.*

/** Viewport-sized tiles only. No screenshot-sized whole article allocations, no overlapping frames. */
class ContinuousReaderView(context:Context):View(context){
    data class Tile(val key:Int,val source:Box,val rect:RectF,val ordinal:Int,val kind:Kind)
    var onRequest:((Tile,Int)->Unit)?=null
    var onProgress:((Int,Int)->Unit)?=null
    private var bands=listOf<ReadBand>();private var pw=1;private var ph=1
    private var tiles=listOf<Tile>();private var totalHeight=0f;private var offset=0f;private var magnification=1f
    private val bitmaps=object:android.util.LruCache<Int,Bitmap>(32*1024*1024){override fun sizeOf(key:Int,value:Bitmap)=value.byteCount;override fun entryRemoved(evicted:Boolean,key:Int,old:Bitmap,new:Bitmap?){if(old!==new)old.recycle()}}
    private val pending=mutableSetOf<Int>();private val failed=mutableSetOf<Int>();private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scroller=OverScroller(context);private val density=resources.displayMetrics.density
    private var revision=0
    private var horizontal=0f
    private val scaling=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(d:ScaleGestureDetector):Boolean{zoomBy(d.scaleFactor);return true}})
    private val gestures=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
        override fun onDown(e:MotionEvent):Boolean{scroller.forceFinished(true);return true}
        override fun onScroll(a:MotionEvent?,b:MotionEvent,dx:Float,dy:Float):Boolean{horizontal=(horizontal+dx).coerceIn(-max(0f,(width*magnification-width)/2),max(0f,(width*magnification-width)/2));move(offset+dy);return true}
        override fun onFling(a:MotionEvent?,b:MotionEvent,vx:Float,vy:Float):Boolean{scroller.fling(0,offset.roundToInt(),0,(-vy).roundToInt(),0,0,0,max(0f,totalHeight-height).roundToInt());postInvalidateOnAnimation();return true}
    })
    init{setBackgroundColor(Color.rgb(245,243,240));isFocusable=true;contentDescription="Lecture continue. Glissez verticalement pour lire les blocs dans votre ordre."}
    fun setDocument(zones:List<Zone>,pageWidth:Int,pageHeight:Int,position:Int=0){clear();bands=ContinuousLayout.bands(zones);pw=pageWidth;ph=pageHeight;offset=position.toFloat();rebuild()}
    fun zoomBy(f:Float){val fraction=if(totalHeight>0)offset/totalHeight else 0f;magnification=(magnification*f).coerceIn(1f,2f);val limit=max(0f,(width*magnification-width)/2);horizontal=horizontal.coerceIn(-limit,limit);rebuild();move(fraction*totalHeight)}
    fun clear(){revision++;pending.clear();failed.clear();bitmaps.evictAll();tiles=emptyList();bands=emptyList();offset=0f;horizontal=0f;magnification=1f;totalHeight=0f;scroller.forceFinished(true);invalidate()}
    fun accept(tile:Tile,expected:Int,bitmap:Bitmap?){if(expected!=revision||tiles.none{it.key==tile.key}){bitmap?.recycle();return};pending.remove(tile.key);if(bitmap!=null)bitmaps.put(tile.key,bitmap)else failed.add(tile.key);invalidate()}
    fun reprocess(){rebuild()}
    fun currentPosition()=offset.roundToInt()
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);if(w>0&&h>0)rebuild()}
    private fun rebuild(){
        revision++;pending.clear();failed.clear();bitmaps.evictAll();if(width<=0)return
        val out=mutableListOf<Tile>();var y=0f;var last=-1
        for(b in bands){
            if(b.ordinal!=last){if(last>=0)y+=12*density;last=b.ordinal}
            var scale=(width-16*density)/(b.original.width*pw)*if(b.kind==Kind.TEXT)magnification else 1f
            if(b.kind==Kind.IMAGE)scale=min(scale,max(300f,height*.9f)/(b.original.height*ph))
            val bandHeight=(b.bottom-b.top)*ph*scale;val fullWidth=b.original.width*pw*scale;val left=(width-fullWidth)/2
            // Keep each bitmap below about 2 MiB, even with text zoom.
            val tileHeight=min(900f,max(64f,2f*1024*1024/(4*max(1f,fullWidth))))
            val pieces=max(1,ceil(bandHeight/tileHeight).toInt())
            for(i in 0 until pieces){val t=b.top+(b.bottom-b.top)*i/pieces;val bottom=b.top+(b.bottom-b.top)*(i+1)/pieces
                for(p in b.pieces){val rect=RectF(left+(p.left-b.original.left)*pw*scale,y+(t-b.top)*ph*scale,left+(p.right-b.original.left)*pw*scale,y+(bottom-b.top)*ph*scale);out+=Tile(out.size,Box(p.left,t,p.right,bottom),rect,b.ordinal,b.kind)}
            }
            y+=bandHeight
        }
        tiles=out;totalHeight=y;move(offset);invalidate()
    }
    private fun move(value:Float){offset=value.coerceIn(0f,max(0f,totalHeight-height));val ordinal=tiles.firstOrNull{it.rect.bottom>=offset}?.ordinal?:0;onProgress?.invoke(ordinal,offset.roundToInt());invalidate()}
    override fun onDraw(c:Canvas){
        super.onDraw(c);c.save();c.translate(-horizontal,-offset)
        val wanted=tiles.filter{it.rect.bottom>=offset-height*.5f&&it.rect.top<=offset+height*1.5f}
        // Ask nearest visible tiles first; never queue a whole newspaper article.
        for(tile in wanted.sortedBy{abs(it.rect.centerY()-(offset+height*.5f))}){
            if(tile.rect.bottom>=offset&&tile.rect.top<=offset+height){paint.color=Color.WHITE;c.drawRect(tile.rect,paint);val image=bitmaps.get(tile.key);if(image!=null&&!image.isRecycled)c.drawBitmap(image,null,tile.rect,paint)}
            if(tile.key in failed&&tile.rect.bottom>=offset&&tile.rect.top<=offset+height){paint.color=Color.DKGRAY;paint.textSize=14*density;c.drawText("Rendu indisponible · revenez à la page",tile.rect.left+8*density,tile.rect.top+24*density,paint)}
            if(tile.key !in failed&&bitmaps.get(tile.key)==null&&pending.add(tile.key))onRequest?.invoke(tile,revision)
        }
        c.restore()
    }
    override fun computeScroll(){if(scroller.computeScrollOffset()){move(scroller.currY.toFloat());postInvalidateOnAnimation()}}
    override fun onTouchEvent(e:MotionEvent):Boolean{scaling.onTouchEvent(e);if(!scaling.isInProgress)gestures.onTouchEvent(e);if(e.action==MotionEvent.ACTION_UP)performClick();return true}
    override fun performClick():Boolean{super.performClick();return true}
}
