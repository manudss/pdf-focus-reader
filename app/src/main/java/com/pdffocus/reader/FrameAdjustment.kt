package com.pdffocus.reader
import kotlin.math.*
/** Normalized geometry, shared by move and all eight resize handles. */
object FrameAdjustment {
 fun change(box:Box,hx:Int,hy:Int,dx:Float,dy:Float,move:Boolean=false):Box {
  if(move){val x=dx.coerceIn(-box.left,1f-box.right);val y=dy.coerceIn(-box.top,1f-box.bottom);return Box(box.left+x,box.top+y,box.right+x,box.bottom+y)}
  val minWidth=min(.008f,box.width);val minHeight=min(.008f,box.height)
  val l=if(hx<0)(box.left+dx).coerceIn(0f,box.right-minWidth)else box.left
  val r=if(hx>0)(box.right+dx).coerceIn(box.left+minWidth,1f)else box.right
  val t=if(hy<0)(box.top+dy).coerceIn(0f,box.bottom-minHeight)else box.top
  val b=if(hy>0)(box.bottom+dy).coerceIn(box.top+minHeight,1f)else box.bottom
  return Box(l,t,r,b)
 }
}
