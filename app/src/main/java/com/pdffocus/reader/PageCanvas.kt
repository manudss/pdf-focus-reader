package com.pdffocus.reader
import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*

/** Original page and detected rectangles. Touch coordinates invert the exact display matrix. */
class PageCanvas(context:Context):View(context) {
    var image:Bitmap?=null;set(value){field=value;resetZoom();invalidate()}
    var zones:List<Zone> = emptyList();set(value){field=value;invalidate()}
    var selection:List<Int> = emptyList();set(value){field=value;invalidate()}
    var reading=false;set(value){field=value;resetZoom()}
    var drawMode=false;set(value){field=value;draft=null;invalidate()}
    var onZoneTap:((Zone)->Unit)?=null
    var onZoneLongPress:((Zone)->Unit)?=null
    var onRectangle:((Box)->Unit)?=null
    var onViewportReady:(()->Unit)?=null
    private val matrix=Matrix();private val inverse=Matrix();private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var zoom=1f;private var panX=0f;private var panY=0f
    private var start:PointF?=null;private var draft:Box?=null
    private val density=resources.displayMetrics.density
    private val scale=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(d:ScaleGestureDetector):Boolean {zoom=(zoom*d.scaleFactor).coerceIn(1f,6f);invalidate();return true}
    })
    private val gestures=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
        override fun onDown(e:android.view.MotionEvent)=true
        override fun onScroll(a:MotionEvent?,b:MotionEvent,dx:Float,dy:Float):Boolean {if(zoom>1f){panX-=dx;panY-=dy;invalidate()};return true}
        override fun onSingleTapConfirmed(e:MotionEvent):Boolean {if(!reading){point(e)?.let{p->ReadingLayout.smallestHit(zones,p.x,p.y)?.let{onZoneTap?.invoke(it)}}};return true}
        override fun onLongPress(e:MotionEvent){if(!reading)point(e)?.let{p->ReadingLayout.smallestHit(zones,p.x,p.y)?.let{onZoneLongPress?.invoke(it)}}}
        override fun onDoubleTap(e:MotionEvent):Boolean{if(zoom>1f)resetZoom()else{zoom=2f;invalidate()};return true}
    })
    init {setBackgroundColor(Color.rgb(245,243,240));contentDescription="Page PDF. Pincez pour zoomer ; touchez les blocs dans l'ordre. Appui long pour corriger.";isFocusable=true}
    fun resetZoom(){zoom=1f;panX=0f;panY=0f;invalidate()}
    fun zoomBy(f:Float){zoom=(zoom*f).coerceIn(1f,6f);invalidate()}
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);if(w>0&&h>0&&(w!=oldw||h!=oldh))post{onViewportReady?.invoke()}}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        val b=image
        if(b==null){
            paint.style=Paint.Style.FILL;paint.color=Color.rgb(37,31,33);paint.textAlign=Paint.Align.CENTER;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.textSize=21*density
            canvas.drawText("Votre journal,",width/2f,height/2f-55*density,paint)
            canvas.drawText("à votre rythme.",width/2f,height/2f-27*density,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL);paint.textSize=14*density;paint.color=Color.rgb(90,85,85)
            for((i,line)in listOf("Ouvrez une page.","Touchez vos blocs dans l'ordre.","Lisez sans déplacer la page.").withIndex())canvas.drawText(line,width/2f,height/2f+12*density+i*24*density,paint)
            paint.textAlign=Paint.Align.LEFT;return
        }
        if(b.isRecycled)return
        val base=min(width.toFloat()/b.width,height.toFloat()/b.height)
        val s=base*zoom
        val dw=b.width*s;val dh=b.height*s
        // Limit panning so a page cannot be lost outside the viewport.
        panX=panX.coerceIn(-max(0f,(dw-width)/2),max(0f,(dw-width)/2))
        panY=panY.coerceIn(-max(0f,(dh-height)/2),max(0f,(dh-height)/2))
        matrix.reset();matrix.postScale(s,s);matrix.postTranslate((width-dw)/2+panX,(height-dh)/2+panY);matrix.invert(inverse)
        paint.style=Paint.Style.FILL;paint.color=Color.WHITE;canvas.drawBitmap(b,matrix,paint)
        if(!reading)for(zone in zones){
            val number=selection.indexOf(zone.id)+1
            val rect=RectF(zone.box.left*b.width,zone.box.top*b.height,zone.box.right*b.width,zone.box.bottom*b.height);matrix.mapRect(rect)
            paint.color=if(number>0)Color.rgb(37,122,100)else Color.rgb(90,98,98)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=if(number>0)3*density else density
            paint.pathEffect=if(number>0)null else DashPathEffect(floatArrayOf(5*density,3*density),0f)
            canvas.drawRect(rect,paint);paint.pathEffect=null
            if(number>0){
                paint.style=Paint.Style.FILL;canvas.drawRoundRect(RectF(rect.left,rect.top,rect.left+27*density,rect.top+25*density),3*density,3*density,paint)
                paint.color=Color.WHITE;paint.textSize=16*density;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
                canvas.drawText(number.toString(),rect.left+6*density,rect.top+19*density,paint)
            }
        }
        draft?.let{box->val rect=RectF(box.left*b.width,box.top*b.height,box.right*b.width,box.bottom*b.height);matrix.mapRect(rect);paint.color=Color.rgb(37,122,100);paint.style=Paint.Style.STROKE;paint.strokeWidth=3*density;canvas.drawRect(rect,paint)}
        paint.style=Paint.Style.FILL
    }
    private fun point(e:MotionEvent):PointF? {val b=image?:return null;val xy=floatArrayOf(e.x,e.y);inverse.mapPoints(xy);return PointF((xy[0]/b.width).coerceIn(0f,1f),(xy[1]/b.height).coerceIn(0f,1f))}
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(drawMode&&!reading){
            val p=point(e)?:return false
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{start=p;parent.requestDisallowInterceptTouchEvent(true)}
                MotionEvent.ACTION_MOVE->{start?.let{draft=Box(min(it.x,p.x),min(it.y,p.y),max(it.x,p.x),max(it.y,p.y))};invalidate()}
                MotionEvent.ACTION_UP->{start?.let{val b=Box(min(it.x,p.x),min(it.y,p.y),max(it.x,p.x),max(it.y,p.y));if(b.width>.01f&&b.height>.01f)onRectangle?.invoke(b)};start=null;draft=null;invalidate();performClick()}
                MotionEvent.ACTION_CANCEL->{start=null;draft=null;invalidate()}
            };return true
        }
        scale.onTouchEvent(e);if(!scale.isInProgress)gestures.onTouchEvent(e)
        if(e.action==MotionEvent.ACTION_UP)performClick()
        return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}
