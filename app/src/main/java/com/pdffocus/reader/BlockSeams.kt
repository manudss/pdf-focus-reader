package com.pdffocus.reader

import android.graphics.Bitmap
import kotlin.math.*

/** Repair an automatic photo/caption seam only when a paper separator and pixel coverage agree. */
object BlockSeams {
    fun align(bitmap:Bitmap,zones:List<Zone>):List<Zone>{
        val out=zones.toMutableList();val w=bitmap.width;val h=bitmap.height
        for(i in out.indices){
            val image=out[i];if(image.kind!=Kind.IMAGE||image.manual)continue
            for(j in out.indices){
                val text=out[j];if(text.kind!=Kind.TEXT||text.manual)continue
                val a=out[i].box;val b=text.box
                val left=max(a.left,b.left);val right=min(a.right,b.right)
                if(right-left<.85f*min(a.width,b.width)||b.top<=a.top+a.height*.55f||b.top>=a.bottom||b.bottom<=a.bottom)continue
                val x0=ceil(left*w).toInt();val x1=floor(right*w).toInt()
                val y0=ceil(b.top*h).toInt();val y1=floor(a.bottom*h).toInt()
                if(x1<=x0||y1-y0<2)continue
                val row=IntArray(x1-x0)
                fun paper(y:Int):Boolean{
                    bitmap.getPixels(row,0,row.size,x0,y,row.size,1)
                    return row.count{p->minOf((p shr 16)and 255,(p shr 8)and 255,p and 255)<185}<=max(1,row.size/200)
                }
                var run=-1;var bestStart=-1;var bestEnd=-1
                for(y in y0..y1){
                    if(paper(y)){if(run<0)run=y}
                    else if(run>=0){if(y-run>bestEnd-bestStart){bestStart=run;bestEnd=y};run=-1}
                }
                if(run>=0&&y1+1-run>bestEnd-bestStart){bestStart=run;bestEnd=y1+1}
                if(bestEnd-bestStart<2)continue
                val seam=(bestStart+bestEnd)/2f/h
                if(abs(a.left-b.left)>.012f||abs(a.right-b.right)>.012f)continue
                // Keep the narrow side slivers with their neighbour instead of stretching them into a gap.
                // Automatic boxes may gain a few pixels of margin; no selected source pixel is removed.
                val commonLeft=min(a.left,b.left);val commonRight=max(a.right,b.right)
                val nextA=a.copy(left=commonLeft,right=commonRight,bottom=seam)
                val nextB=b.copy(left=commonLeft,right=commonRight,top=seam)
                // Different widths can leave side slivers behind. Never discard ink, colour or a faint mark there.
                fun contains(r:Box,x:Float,y:Float)=x>=r.left&&x<r.right&&y>=r.top&&y<r.bottom
                var safe=true
                val l=floor(min(a.left,b.left)*w).toInt().coerceAtLeast(0)
                val r=ceil(max(a.right,b.right)*w).toInt().coerceAtMost(w)
                for(y in floor(b.top*h).toInt().coerceAtLeast(0) until ceil(a.bottom*h).toInt().coerceAtMost(h)){
                    for(x in l until r){val fx=(x+.5f)/w;val fy=(y+.5f)/h
                        if((contains(a,fx,fy)||contains(b,fx,fy))&&!contains(nextA,fx,fy)&&!contains(nextB,fx,fy)){
                            val p=bitmap.getPixel(x,y);val red=(p shr 16)and 255;val green=(p shr 8)and 255;val blue=p and 255
                            if(minOf(red,green,blue)<220||maxOf(red,green,blue)-minOf(red,green,blue)>15){safe=false;break}
                        }
                    };if(!safe)break
                }
                if(safe){out[i]=out[i].copy(box=nextA);out[j]=text.copy(box=nextB)}
            }
        }
        return out
    }
}
