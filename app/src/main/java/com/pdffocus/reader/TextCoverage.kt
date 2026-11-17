package com.pdffocus.reader
import android.graphics.Bitmap
import kotlin.math.*

/** Keep complete OCR lines, including lines rejected by paragraph-level filtering. */
object TextCoverage {
    /** Cover short unrecognized gaps between aligned fragments. Source-area union removes overlaps. */
    fun bridgeColumnGaps(zones:List<Zone>):List<Zone> = zones.map{z->
        if(z.kind!=Kind.TEXT||z.manual)return@map z
        val b=z.box
        val previous=zones.filter{p->p.id!=z.id&&p.kind==Kind.TEXT&&p.box.bottom<=b.top&&b.top-p.box.bottom<=.045f&&
            min(p.box.width,b.width)/max(p.box.width,b.width)>.72f&&
            (min(p.box.right,b.right)-max(p.box.left,b.left))>.78f*min(p.box.width,b.width)}.maxByOrNull{it.box.bottom}
        if(previous==null||zones.any{it.kind==Kind.IMAGE&&it.box.top<b.top&&it.box.bottom>previous.box.bottom&&it.box.intersect(Box(b.left,previous.box.bottom,b.right,b.top))>0})z
        else z.copy(box=Box(b.left,max(0f,previous.box.bottom-.0015f),b.right,b.bottom))
    }

    fun includeLines(paragraphs:List<Zone>,lines:List<Box>):List<Zone>{
        val out=paragraphs.toMutableList()
        for(line in lines){
            val covered=out.any{it.box.intersect(line)>=line.area*.995f}
            if(covered)continue
            val target=out.indices.filter{out[it].kind==Kind.TEXT}.maxByOrNull{out[it].box.intersect(line)}
            if(target!=null&&out[target].box.intersect(line)>.45f*line.area){out[target]=out[target].copy(box=out[target].box.union(line).pad(.001f))}
            else out+=Zone((out.maxOfOrNull{it.id}?:0)+1,line.pad(.003f),Kind.TEXT)
        }
        return out.map{z->z.copy(lineBottoms=lines.filter{z.box.intersect(it)>.8f*it.area}.map{it.bottom}.sorted())}
    }

    /** A detected/manual edge can cross a descender or miss a final row. Extend to nearby white rows. */
    fun safeTextBox(bitmap:Bitmap,box:Box):Box{
        val w=bitmap.width;val h=bitmap.height
        val x0=floor(box.left*w).toInt().coerceIn(0,w-1);val x1=ceil(box.right*w).toInt().coerceIn(x0+1,w)
        val y0=floor(box.top*h).toInt().coerceIn(0,h-1);val y1=ceil(box.bottom*h).toInt().coerceIn(y0+1,h)
        val limit=max(4,(h*.02f).toInt());val row=IntArray(x1-x0)
        fun blank(y:Int):Boolean{
            bitmap.getPixels(row,0,row.size,x0,y,row.size,1)
            return row.count{p->val r=(p shr 16)and 255;val g=(p shr 8)and 255;val b=p and 255;minOf(r,g,b)<185}<=max(1,row.size/120)
        }
        var top=y0;var bottom=y1
        // Always retain the supplied region. Only grow outward, never shrink to an OCR box.
        if(!blank(y0))for(y in y0-1 downTo max(0,y0-limit)){top=y;if(blank(y))break}
        if(!blank(y1-1))for(y in y1 until min(h,y1+limit)){bottom=y+1;if(blank(y))break}
        return Box(max(0f,box.left-2f/w),top.toFloat()/h,min(1f,box.right+2f/w),bottom.toFloat()/h)
    }
}
