package com.pdffocus.reader
import android.graphics.*
import android.graphics.drawable.Drawable

/** Small local vector icons, paired with visible action labels. No font or network dependency. */
class ActionIcon(private val name:String,private val tint:Int):Drawable(){
 private val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=1.7f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND;color=tint}
 override fun draw(c:Canvas){c.save();c.translate(bounds.left.toFloat(),bounds.top.toFloat());c.scale(bounds.width()/24f,bounds.height()/24f)
  fun line(vararg values:Float){val path=Path();path.moveTo(values[0],values[1]);for(i in 2 until values.size step 2)path.lineTo(values[i],values[i+1]);c.drawPath(path,p)}
  when(name){
   "share"->{line(6f,12f,18f,5f);line(6f,12f,18f,19f);for((x,y)in listOf(5f to 12f,19f to 4f,19f to 20f))c.drawCircle(x,y,2.5f,p)}
   "refresh"->{c.drawArc(RectF(4f,4f,20f,20f),40f,290f,false,p);line(20f,3f,20f,9f,14f,9f)}
   "info"->{c.drawCircle(12f,12f,9f,p);line(12f,11f,12f,17f);c.drawCircle(12f,7f,.5f,p)}
   "help"->{c.drawCircle(12f,12f,9f,p);c.drawArc(RectF(8f,6f,16f,12f),180f,270f,false,p);line(12f,12f,12f,14f);c.drawCircle(12f,18f,.5f,p)}
   "license"->{line(6f,3f,18f,3f,18f,21f,6f,21f,6f,3f);line(9f,8f,15f,8f);line(9f,12f,15f,12f);line(9f,16f,13f,16f)}
   "back"->line(15f,5f,8f,12f,15f,19f)
   "next"->line(9f,5f,16f,12f,9f,19f)
   "add"->{line(12f,5f,12f,19f);line(5f,12f,19f,12f)}
   "minus"->line(5f,12f,19f,12f)
   "close"->{line(5f,5f,19f,19f);line(19f,5f,5f,19f)}
   "check"->line(4f,12f,9f,17f,20f,6f)
   "settings"->{line(4f,7f,20f,7f);line(4f,17f,20f,17f);line(9f,4f,9f,10f);line(16f,14f,16f,20f)}
   "text"->{line(4f,5f,20f,5f);line(12f,5f,12f,20f);line(8f,20f,16f,20f)}
   "clean"->line(12f,3f,14f,9f,21f,12f,14f,14f,12f,21f,10f,14f,3f,12f,10f,9f,12f,3f)
   "book"->{line(3f,4f,9f,4f,12f,6f,15f,4f,21f,4f,21f,20f,15f,20f,12f,22f,9f,20f,3f,20f,3f,4f);line(12f,6f,12f,22f)}
   "document"->line(5f,3f,14f,3f,19f,8f,19f,21f,5f,21f,5f,3f,14f,3f,14f,8f,19f,8f)
   "merge"->{line(5f,3f,5f,8f,12f,14f,19f,8f,19f,3f);line(12f,14f,12f,21f);line(8f,17f,12f,21f,16f,17f)}
   "group"->{line(4f,6f,11f,6f,11f,11f,4f,11f,4f,6f);line(13f,13f,20f,13f,20f,18f,13f,18f,13f,13f);p.pathEffect=DashPathEffect(floatArrayOf(2.5f,2f),0f);line(6f,3f,21f,3f,21f,21f,6f,21f,6f,3f);p.pathEffect=null}
   "order"->{line(6f,3f,6f,21f);line(3f,18f,6f,21f,9f,18f);line(13f,5f,21f,5f);line(13f,11f,19f,11f);line(13f,17f,17f,17f)}
   "splitv"->{line(3f,3f,21f,3f,21f,21f,3f,21f,3f,3f);line(12f,3f,12f,21f)}
   "splith"->{line(3f,3f,21f,3f,21f,21f,3f,21f,3f,3f);line(3f,12f,21f,12f)}
   "image"->{line(3f,4f,21f,4f,21f,20f,3f,20f,3f,4f);line(3f,16f,9f,10f,13f,14f,16f,11f,21f,16f)}
   "trash"->{line(3f,6f,21f,6f);line(9f,6f,9f,3f,15f,3f,15f,6f);line(5f,6f,6f,21f,18f,21f,19f,6f);line(10f,10f,10f,17f);line(14f,10f,14f,17f)}
   "draw"->{line(3f,17f,3f,21f,7f,21f,21f,7f,17f,3f,3f,17f);line(14f,6f,18f,10f)}
   "crop"->{line(7f,3f,7f,17f,21f,17f);line(3f,7f,17f,7f,17f,21f)}
   "undo"->{line(8f,4f,3f,9f,8f,14f);val path=Path();path.moveTo(3f,9f);path.lineTo(14f,9f);path.cubicTo(23f,9f,23f,21f,14f,21f);c.drawPath(path,p)}
   "center"->{c.drawCircle(12f,12f,6f,p);line(12f,2f,12f,6f);line(12f,18f,12f,22f);line(2f,12f,6f,12f);line(18f,12f,22f,12f)}
   else->{for(x in listOf(5f,12f,19f))c.drawCircle(x,12f,1f,p)}
  };c.restore()
 }
 override fun setAlpha(alpha:Int){p.alpha=alpha};override fun setColorFilter(cf:ColorFilter?){p.colorFilter=cf}
 @Deprecated("Deprecated in Java")override fun getOpacity()=PixelFormat.TRANSLUCENT
}
