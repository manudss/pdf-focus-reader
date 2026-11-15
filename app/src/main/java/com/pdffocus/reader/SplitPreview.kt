package com.pdffocus.reader

import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*

/** Private preview only. Coordinates are in the original normalised zone. */
class SplitPreview(context:Context,val bitmap:Bitmap,val box:Box,val vertical:Boolean):View(context){
 private val density=resources.displayMetrics.density
 private fun dp(x:Float)=x*density
 var fraction=.5f;private set
 var zoom=1f;private set
 private var panX=0f;private var panY=0f
 private var dragging=false;private var wasScaling=false;private var lastX=0f;private var lastY=0f
 private val green=Color.rgb(50,109,93)
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
 private val source=RectF(box.left*bitmap.width,box.top*bitmap.height,box.right*bitmap.width,box.bottom*bitmap.height)
 var onChange:(()->Unit)?=null
 private val scaler=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
  override fun onScaleBegin(d:ScaleGestureDetector):Boolean{dragging=false;wasScaling=true;return true}
  override fun onScale(d:ScaleGestureDetector):Boolean{setZoom(zoom*d.scaleFactor);return true}
 })
 init{setBackgroundColor(Color.rgb(250,249,246));isFocusable=true;contentDescription="Aperçu de séparation. Pincez pour zoomer. Déplacez la ligne ou utilisez les boutons de précision."}
 fun setFraction(value:Float){fraction=value.coerceIn(.001f,.999f);invalidate();onChange?.invoke()}
 fun nudge(direction:Int){setFraction(fraction+direction/(if(vertical)source.width()else source.height()).coerceAtLeast(1f))}
 fun setZoom(value:Float){zoom=value.coerceIn(1f,6f);val a=viewport();val r=imageRect();if(vertical)panX-=(r.left+r.width()*fraction-a.centerX())else panY-=(r.top+r.height()*fraction-a.centerY());if(zoom==1f){panX=0f;panY=0f};constrain();invalidate();onChange?.invoke()}
 private fun viewport()=RectF(dp(22f),dp(34f),max(dp(23f),width-dp(44f)),max(dp(35f),height-dp(48f)))
 private fun imageRect():RectF{val area=viewport();val scale=min(area.width()/source.width(),area.height()/source.height())*zoom;val w=source.width()*scale;val h=source.height()*scale;return RectF(area.centerX()-w/2+panX,area.centerY()-h/2+panY,area.centerX()+w/2+panX,area.centerY()+h/2+panY)}
 private fun constrain(){if(width<=0||height<=0)return;val r=imageRect();val a=viewport();val dx=max(0f,(r.width()-a.width())/2);val dy=max(0f,(r.height()-a.height())/2);panX=panX.coerceIn(-dx,dx);panY=panY.coerceIn(-dy,dy)}
 internal fun linePosition():Float{val r=imageRect();return if(vertical)r.left+r.width()*fraction else r.top+r.height()*fraction}
 internal fun handlePoint():PointF{val a=viewport();val r=imageRect();return if(vertical)PointF(linePosition().coerceIn(a.left,a.right),min(r.bottom,a.bottom)+dp(18f))else PointF(min(r.right,a.right)+dp(18f),linePosition().coerceIn(a.top,a.bottom))}
 override fun onSizeChanged(w:Int,h:Int,ow:Int,oh:Int){constrain()}
 override fun onDraw(c:Canvas){super.onDraw(c);if(width<=0||height<=0)return
  val a=viewport();val r=imageRect();c.save();c.clipRect(a);paint.style=Paint.Style.FILL;paint.color=Color.WHITE;c.drawRect(r,paint);c.drawBitmap(bitmap,Rect(source.left.toInt(),source.top.toInt(),source.right.toInt(),source.bottom.toInt()),r,paint)
  paint.color=green;paint.strokeWidth=dp(1.5f);paint.style=Paint.Style.STROKE;c.drawRect(r,paint);paint.strokeWidth=dp(2f)
  val pos=linePosition();if(vertical)c.drawLine(pos,r.top,pos,r.bottom,paint)else c.drawLine(r.left,pos,r.right,pos,paint);c.restore()
  paint.style=Paint.Style.FILL;paint.textSize=dp(12f);paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);paint.color=green
  if(vertical){c.drawText("1 · Gauche",a.left,dp(21f),paint);paint.textAlign=Paint.Align.RIGHT;c.drawText("2 · Droite",a.right,dp(21f),paint);paint.textAlign=Paint.Align.LEFT}
  else {c.drawText("1 · Haut",a.left,dp(21f),paint);c.drawText("2 · Bas",a.left,height-dp(8f),paint)}
  val visible=if(vertical)pos in a.left..a.right else pos in a.top..a.bottom
  if(visible){val h=handlePoint();paint.color=green;c.drawRoundRect(h.x-dp(17f),h.y-dp(16f),h.x+dp(17f),h.y+dp(16f),dp(14f),dp(14f),paint);paint.color=Color.WHITE;paint.textAlign=Paint.Align.CENTER;paint.textSize=dp(20f);c.drawText(if(vertical)"↔" else "↕",h.x,h.y+dp(7f),paint);paint.textAlign=Paint.Align.LEFT}
 }
 override fun onTouchEvent(e:MotionEvent):Boolean{
  scaler.onTouchEvent(e)
  when(e.actionMasked){
   MotionEvent.ACTION_DOWN->{parent?.requestDisallowInterceptTouchEvent(true);lastX=e.x;lastY=e.y;wasScaling=false;val h=handlePoint();val line=if(vertical)e.x else e.y;dragging=abs(line-linePosition())<dp(22f)||hypot(e.x-h.x,e.y-h.y)<dp(28f);return true}
   MotionEvent.ACTION_MOVE->{if(e.pointerCount==1&&!scaler.isInProgress&&!wasScaling){if(dragging){val r=imageRect();setFraction(if(vertical)(e.x-r.left)/r.width()else(e.y-r.top)/r.height())}else{panX+=e.x-lastX;panY+=e.y-lastY;constrain();invalidate()}};lastX=e.x;lastY=e.y;return true}
   MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{dragging=false;wasScaling=false;parent?.requestDisallowInterceptTouchEvent(false);if(e.actionMasked==MotionEvent.ACTION_UP)performClick();return true}
  };return true
 }
 override fun performClick():Boolean{super.performClick();return true}
}
