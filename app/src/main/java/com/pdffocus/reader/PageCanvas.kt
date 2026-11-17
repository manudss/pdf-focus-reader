package com.pdffocus.reader
import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*

/** Original page and detected rectangles. Touch coordinates invert the exact display matrix. */
class PageCanvas(context:Context):View(context) {
    var image:Bitmap?=null;set(value){field=value;resetZoom();invalidate()}
    var zones:List<Zone> = emptyList();set(value){field=value;invalidate()}
    var selection:List<Int> = emptyList();set(value){finishSelectionReveal();field=value;invalidate()}
    private var revealedSelections=Int.MAX_VALUE
    private val revealHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private val revealNext=object:Runnable{override fun run(){revealedSelections++;invalidate();if(revealedSelections<selection.size)revealHandler.postDelayed(this,140)}}
    /** Presentation only: the complete selection and its persisted order never change. */
    fun animateSelectionOrder(){revealHandler.removeCallbacks(revealNext);revealedSelections=0;invalidate();if(selection.isNotEmpty())revealHandler.postDelayed(revealNext,100)}
    fun finishSelectionReveal(){revealHandler.removeCallbacks(revealNext);revealedSelections=Int.MAX_VALUE;invalidate()}
    override fun onDetachedFromWindow(){finishSelectionReveal();super.onDetachedFromWindow()}
    var reading=false;set(value){field=value;resetZoom()}
    var drawMode=false;set(value){field=value;draft=null;invalidate()}
    var mergeMode=false;set(value){field=value;invalidate()}
    var mergeIds:Set<Int> = emptySet();set(value){field=value;invalidate()}
    var mergeColors:Map<Int,Int> = emptyMap();set(value){field=value;invalidate()}
    var chosenIds:Set<Int> = emptySet();set(value){field=value;invalidate()}
    var groupBoxes:List<GroupBox> = emptyList();set(value){field=value;invalidate()}
    data class GroupBox(val box:Box,val color:Int,val number:Int)
    var adjustingZone:Int?=null
    var editBox:Box?=null;set(value){field=value;invalidate()}
    private var editStart:PointF?=null
    private var editOriginal:Box?=null
    private var editHandle:Pair<Int,Int>?=null
    private var editMove=false
    private fun screenRect(box:Box):RectF{val b=image?:return RectF();return RectF(box.left*b.width,box.top*b.height,box.right*b.width,box.bottom*b.height).also{matrix.mapRect(it)}}
    private fun handles(rect:RectF)=listOf(-1 to -1,0 to -1,1 to -1,-1 to 0,1 to 0,-1 to 1,0 to 1,1 to 1).map{h->h to PointF(if(h.first<0)rect.left else if(h.first>0)rect.right else rect.centerX(),if(h.second<0)rect.top else if(h.second>0)rect.bottom else rect.centerY())}
    var onZoneTap:((Zone)->Unit)?=null
    var onZoneLongPress:((Zone)->Unit)?=null
    var onZoneSweep:((Zone)->Unit)?=null
    /** Two-finger hold then drag: REMOVE the chosen blocks the pair crosses. Never adds. */
    var onZoneSweepRemove:((Zone)->Unit)?=null
    /** -1 = previous page (swipe right), +1 = next page (swipe left). Only fires unzoomed. */
    var onPageSwipe:((Int)->Unit)?=null
    private var sweeping=false
    private var sweepLast:PointF?=null
    private val swept=mutableSetOf<Int>()
    private var multiTouch=false
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private val touchSlop=ViewConfiguration.get(context).scaledTouchSlop
    private var twoHold=false
    private var twoAnchor:PointF?=null
    private var twoAnchorSpan=0f
    private var twoSweeping=false
    private var twoLast:List<PointF> = emptyList()
    private var twoPoints:List<PointF> = emptyList()
    private var finishTwoSweep=false
    private val twoSwept=mutableSetOf<Int>()
    private val twoHoldRunnable=Runnable{
        if(twoHold&&!reading&&editBox==null&&!drawMode){twoSweeping=true;twoLast=twoPoints;twoSwept.clear();parent?.requestDisallowInterceptTouchEvent(true);performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);removeAt(twoPoints)}
        twoHold=false
    }
    /** Both fingertips count, plus the midpoint for the original wide-block gesture. */
    private fun removalPoints(e:MotionEvent)=listOfNotNull(pagePoint(e.getX(0),e.getY(0)),pagePoint(e.getX(1),e.getY(1)),midpoint(e).let{pagePoint(it.x,it.y)})
    private fun removeAt(points:List<PointF>){
        for((index,p)in points.withIndex()){
            val previous=twoLast.getOrNull(index)?:p
            for(z in SweepSelection.crossed(zones,previous.x,previous.y,p.x,p.y))
                if(z.id in selection&&twoSwept.add(z.id))onZoneSweepRemove?.invoke(z)
        }
        twoLast=points
    }
    private fun midpoint(e:MotionEvent)=if(e.pointerCount>=2)PointF((e.getX(0)+e.getX(1))/2,(e.getY(0)+e.getY(1))/2) else PointF(e.x,e.y)
    private fun span(e:MotionEvent)=if(e.pointerCount>=2)hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1)) else 0f
    private fun cancelTwoHold(){twoHold=false;handler.removeCallbacks(twoHoldRunnable)}
    var onRectangle:((Box)->Unit)?=null
    var onViewportReady:(()->Unit)?=null
    private val matrix=Matrix();private val inverse=Matrix();private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var zoom=1f;private var panX=0f;private var panY=0f
    private var start:PointF?=null;private var draft:Box?=null
    private val density=resources.displayMetrics.density
    private val scale=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScaleBegin(d:ScaleGestureDetector)=!twoHold&&!twoSweeping
        override fun onScale(d:ScaleGestureDetector):Boolean {zoom=(zoom*d.scaleFactor).coerceIn(1f,6f);invalidate();return true}
    })
    private val gestures=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
        override fun onDown(e:android.view.MotionEvent):Boolean{multiTouch=false;return true}
        override fun onScroll(a:MotionEvent?,b:MotionEvent,dx:Float,dy:Float):Boolean {if(zoom>1f){panX-=dx;panY-=dy;invalidate()};return true}
        override fun onSingleTapConfirmed(e:MotionEvent):Boolean {if(!reading&&editBox==null){point(e)?.let{p->ReadingLayout.smallestHit(zones,p.x,p.y)?.let{onZoneTap?.invoke(it)}}};return true}
        override fun onLongPress(e:MotionEvent){if(!reading&&editBox==null&&!drawMode&&!multiTouch)point(e)?.let{p->ReadingLayout.smallestHit(zones,p.x,p.y)?.let{z->sweeping=true;sweepLast=p;swept.clear();swept+=z.id;parent?.requestDisallowInterceptTouchEvent(true);performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);onZoneSweep?.invoke(z)}}}
        override fun onDoubleTap(e:MotionEvent):Boolean{if(!reading&&editBox==null&&!drawMode&&!mergeMode)point(e)?.let{p->ReadingLayout.smallestHit(zones,p.x,p.y)?.let{onZoneLongPress?.invoke(it)}};return true}
        override fun onFling(a:MotionEvent?,b:MotionEvent,vx:Float,vy:Float):Boolean{
            // Page change only when unzoomed: while zoomed, the same gesture pans the page.
            if(!reading&&editBox==null&&zoom<=1f&&abs(vx)>abs(vy)*1.5f&&abs(vx)>1200f*density){onPageSwipe?.invoke(if(vx<0)1 else -1);return true}
            return false
        }

    })
    init {setBackgroundColor(Color.rgb(245,243,240));contentDescription="Page PDF. Pincez pour zoomer ; touchez les blocs dans l'ordre. Maintenez puis glissez pour sélectionner. Maintenez deux doigts puis glissez pour retirer. Double tap pour corriger.";isFocusable=true}
    fun isZoomed()=zoom>1f
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
            if(zone.id==adjustingZone)continue
            val number=if(mergeMode)0 else (selection.indexOf(zone.id)+1).let{if(it>revealedSelections)0 else it}
            val merged=mergeMode&&zone.id in mergeIds
            val chosenSolo=mergeMode&&!merged&&zone.id in chosenIds
            val rect=RectF(zone.box.left*b.width,zone.box.top*b.height,zone.box.right*b.width,zone.box.bottom*b.height);matrix.mapRect(rect)
            paint.color=if(merged)mergeColors[zone.id]?:Color.rgb(128,91,167)else if(number>0||chosenSolo)Color.rgb(37,122,100)else Color.rgb(90,98,98)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=if(number>0||merged)3*density else if(chosenSolo)1.5f*density else density
            paint.pathEffect=if(number>0||merged||chosenSolo)null else DashPathEffect(floatArrayOf(5*density,3*density),0f)
            canvas.drawRect(rect,paint);paint.pathEffect=null
            // A visible sign plus tint identifies content without relying on colour alone.
            val mark=when{zone.complement->"◇";zone.panel->"▣";zone.kind==Kind.TEXT->"T";else->"▧"}
            val tint=when{zone.panel->Color.rgb(50,109,93);zone.kind==Kind.TEXT->Color.rgb(75,101,144);else->Color.rgb(155,106,36)}
            val bx=(rect.right-23*density).coerceAtLeast(rect.left);val by=rect.top
            paint.style=Paint.Style.FILL;paint.color=Color.WHITE;canvas.drawRoundRect(RectF(bx,by,rect.right,by+22*density),3*density,3*density,paint)
            paint.color=tint;paint.textSize=13*density;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);canvas.drawText(mark,bx+5*density,by+16*density,paint)
            paint.color=if(merged)mergeColors[zone.id]?:Color.rgb(128,91,167)else Color.rgb(37,122,100)
            if(number>0||merged){
                paint.style=Paint.Style.FILL;canvas.drawRoundRect(RectF(rect.left,rect.top,rect.left+27*density,rect.top+25*density),3*density,3*density,paint)
                paint.color=Color.WHITE;paint.textSize=16*density;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD)
                canvas.drawText(if(merged)"✓" else number.toString(),rect.left+6*density,rect.top+19*density,paint)
            }
        }
        if(mergeMode&&groupBoxes.isNotEmpty()){for(gb in groupBoxes){val rect=screenRect(gb.box);rect.inset(-3*density,-3*density);paint.color=gb.color;paint.style=Paint.Style.STROKE;paint.strokeWidth=3*density;canvas.drawRect(rect,paint)
            val label=gb.number.toString();paint.textSize=12*density;paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);val tw=paint.measureText(label)
            val cw=max(21*density,tw+10*density);val ch=21*density;val cx=rect.right-cw;val cy=max(2*density,rect.top-ch-2*density)
            paint.style=Paint.Style.FILL;canvas.drawRoundRect(RectF(cx,cy,cx+cw,cy+ch),10*density,10*density,paint)
            paint.color=Color.WHITE;canvas.drawText(label,cx+(cw-tw)/2,cy+ch-6*density,paint)}}
        if(mergeMode&&groupBoxes.isEmpty()&&mergeIds.size>=2){zones.filter{it.id in mergeIds}.map{it.box}.reduceOrNull{a,b->a.union(b)}?.let{box->val rect=screenRect(box);rect.inset(-4*density,-4*density);paint.color=Color.rgb(128,91,167);paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density;paint.pathEffect=DashPathEffect(floatArrayOf(8*density,4*density),0f);canvas.drawRect(rect,paint);paint.pathEffect=null}}
        draft?.let{box->val rect=RectF(box.left*b.width,box.top*b.height,box.right*b.width,box.bottom*b.height);matrix.mapRect(rect);paint.color=Color.rgb(37,122,100);paint.style=Paint.Style.STROKE;paint.strokeWidth=3*density;canvas.drawRect(rect,paint)}
        editBox?.let{box->
            val rect=screenRect(box);paint.color=Color.rgb(57,126,108);paint.style=Paint.Style.STROKE;paint.strokeWidth=2*density;canvas.drawRect(rect,paint)
            for((_,p)in handles(rect)){paint.style=Paint.Style.FILL;paint.color=Color.WHITE;canvas.drawRect(p.x-5*density,p.y-5*density,p.x+5*density,p.y+5*density,paint);paint.color=Color.rgb(57,126,108);paint.style=Paint.Style.STROKE;canvas.drawRect(p.x-5*density,p.y-5*density,p.x+5*density,p.y+5*density,paint)}
        }
        paint.style=Paint.Style.FILL
    }
    private fun point(e:MotionEvent):PointF?=pagePoint(e.x,e.y)
    private fun pagePoint(x:Float,y:Float):PointF?{val b=image?:return null;val xy=floatArrayOf(x,y);inverse.mapPoints(xy);return PointF((xy[0]/b.width).coerceIn(0f,1f),(xy[1]/b.height).coerceIn(0f,1f))}
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(e.actionMasked==MotionEvent.ACTION_DOWN){finishSelectionReveal();finishTwoSweep=false;cancelTwoHold()}
        if(finishTwoSweep){
            scale.onTouchEvent(e)
            if(e.actionMasked==MotionEvent.ACTION_UP||e.actionMasked==MotionEvent.ACTION_CANCEL){finishTwoSweep=false;multiTouch=false;parent?.requestDisallowInterceptTouchEvent(false)}
            return true
        }
        if(editBox!=null&&!reading){
            scale.onTouchEvent(e)
            if(e.pointerCount>1||scale.isInProgress){editStart=null;editHandle=null;editMove=false;return true}
            val p=point(e)?:return false
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{
                    val rect=screenRect(editBox!!);val nearest=handles(rect).minByOrNull{(_,q)->hypot((q.x-e.x).toDouble(),(q.y-e.y).toDouble())}
                    editHandle=nearest?.takeIf{(_,q)->hypot((q.x-e.x).toDouble(),(q.y-e.y).toDouble())<24*density}?.first
                    editMove=editHandle==null&&rect.contains(e.x,e.y)
                    if(editHandle!=null||editMove){editStart=p;editOriginal=editBox;parent?.requestDisallowInterceptTouchEvent(true)}else gestures.onTouchEvent(e)
                }
                MotionEvent.ACTION_MOVE->{val a=editStart;val original=editOriginal;if(a!=null&&original!=null){val h=editHandle?: (0 to 0);editBox=FrameAdjustment.change(original,h.first,h.second,p.x-a.x,p.y-a.y,editMove)}else gestures.onTouchEvent(e)}
                MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{if(editStart==null)gestures.onTouchEvent(e);editStart=null;editOriginal=null;editHandle=null;editMove=false;performClick()}
            };return true
        }
        if(drawMode&&!reading){
            val p=point(e)?:return false
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{start=p;parent.requestDisallowInterceptTouchEvent(true)}
                MotionEvent.ACTION_MOVE->{start?.let{draft=Box(min(it.x,p.x),min(it.y,p.y),max(it.x,p.x),max(it.y,p.y))};invalidate()}
                MotionEvent.ACTION_UP->{start?.let{val b=Box(min(it.x,p.x),min(it.y,p.y),max(it.x,p.x),max(it.y,p.y));if(b.width>.01f&&b.height>.01f)onRectangle?.invoke(b)};start=null;draft=null;invalidate();performClick()}
                MotionEvent.ACTION_CANCEL->{start=null;draft=null;invalidate()}
            };return true
        }
        // Two-finger hold candidate: both fingers planted together, nearly still, no span change.
        if(e.actionMasked==MotionEvent.ACTION_POINTER_DOWN&&e.pointerCount==2){
            cancelTwoHold()
            twoHold=!reading&&editBox==null&&!drawMode;twoAnchor=midpoint(e);twoAnchorSpan=span(e);twoPoints=removalPoints(e)
            // End any one-finger tap/long-press before the two-finger recognizer takes over.
            val cancel=MotionEvent.obtain(e).apply{action=MotionEvent.ACTION_CANCEL};gestures.onTouchEvent(cancel);cancel.recycle()
            if(twoHold)handler.postDelayed(twoHoldRunnable,ViewConfiguration.getLongPressTimeout().toLong())
        }
        if(twoHold&&!twoSweeping){
            if(e.pointerCount==2)twoPoints=removalPoints(e)
            val m=midpoint(e);val a=twoAnchor
            val moved=a!=null&&e.actionMasked==MotionEvent.ACTION_MOVE&&hypot(m.x-a.x,m.y-a.y)>touchSlop*2f
            val pinched=e.pointerCount>=2&&abs(span(e)-twoAnchorSpan)>touchSlop
            if(moved||pinched||e.actionMasked==MotionEvent.ACTION_POINTER_UP||e.actionMasked==MotionEvent.ACTION_UP||e.actionMasked==MotionEvent.ACTION_CANCEL||e.pointerCount!=2)cancelTwoHold()
        }
        // Active two-finger removal sweep: zoom, pan and page fling stay suspended until a finger lifts.
        if(twoSweeping){
            if(e.actionMasked==MotionEvent.ACTION_MOVE&&e.pointerCount==2)removeAt(removalPoints(e))
            if(e.actionMasked==MotionEvent.ACTION_POINTER_UP||e.actionMasked==MotionEvent.ACTION_UP||e.actionMasked==MotionEvent.ACTION_CANCEL){twoSweeping=false;twoLast=emptyList();twoSwept.clear();finishTwoSweep=e.actionMasked==MotionEvent.ACTION_POINTER_UP;parent?.requestDisallowInterceptTouchEvent(false);performClick()}
            return true
        }
        if(e.pointerCount>1){multiTouch=true;sweeping=false;sweepLast=null;swept.clear()}
        scale.onTouchEvent(e)
        if(sweeping){
            if(e.actionMasked==MotionEvent.ACTION_MOVE){point(e)?.let{p->val previous=sweepLast?:p;for(z in SweepSelection.crossed(zones,previous.x,previous.y,p.x,p.y))if(swept.add(z.id))onZoneSweep?.invoke(z);sweepLast=p}}
            if(e.actionMasked==MotionEvent.ACTION_UP||e.actionMasked==MotionEvent.ACTION_CANCEL){sweeping=false;sweepLast=null;swept.clear();parent?.requestDisallowInterceptTouchEvent(false);performClick()}
            return true
        }
        if(!scale.isInProgress&&!multiTouch)gestures.onTouchEvent(e)
        if(e.actionMasked==MotionEvent.ACTION_UP||e.actionMasked==MotionEvent.ACTION_CANCEL){multiTouch=false;performClick()}
        return true
    }
    override fun performClick():Boolean{super.performClick();return true}
}
