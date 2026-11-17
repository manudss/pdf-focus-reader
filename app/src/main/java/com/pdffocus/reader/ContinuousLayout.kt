package com.pdffocus.reader
import kotlin.math.*
data class ReadBand(val zoneId:Int,val ordinal:Int,val kind:Kind,val original:Box,val top:Float,val bottom:Float,val pieces:List<Box>)
object ContinuousLayout {
    /** Vertical-only overlap removal. Every visible band keeps its full horizontal extent.
     * A previously selected block can remove rows only when it covers the whole width.
     * Partial side overlaps intentionally remain repeated: cutting them would sever words.
     * Nested fragments retain their container's width and the caller's order is unchanged.
     */
    fun bands(zones:List<Zone>):List<ReadBand>{
        val valid=zones.map{it.copy(box=it.box.clamp())}.filter{it.box.width>0&&it.box.height>0}
        val aligned=valid.map{z->
            val b=z.box
            val parent=valid.filter{it.id!=z.id&&it.box.area>b.area*1.02f&&it.box.intersect(b)>=b.area*.999f}.maxByOrNull{it.box.area}
            if(parent==null)z else z.copy(box=Box(parent.box.left,b.top,parent.box.right,b.bottom))
        }
        val coherent=aligned.map{z->
            val b=z.box
            val related=aligned.filter{p->
                val x=p.box;val horizontal=min(x.right,b.right)-max(x.left,b.left);val gap=max(x.top,b.top)-min(x.bottom,b.bottom)
                p.id!=z.id&&p.kind==Kind.TEXT&&z.kind==Kind.TEXT&&(
                    // Only a short heading widens toward a related column BELOW it. A tall column must
                    // never grow toward a wide title above it: it would swallow the neighbouring
                    // columns' strips, which then render twice, clipped, at mismatched scales.
                    b.height<.075f&&x.top>b.top&&abs(x.left-b.left)<.075f&&horizontal>.4f*min(x.width,b.width)&&gap in -.035f.. .035f ||
                    gap in -.01f..0f&&horizontal>.8f*min(x.width,b.width)&&min(x.width,b.width)/max(x.width,b.width)>.72f)
            }
            if(related.isEmpty())z else z.copy(box=Box(min(b.left,related.minOf{it.box.left}),b.top,max(b.right,related.maxOf{it.box.right}),b.bottom))
        }
        val seen=mutableListOf<Box>();val result=mutableListOf<ReadBand>()
        coherent.forEachIndexed{ordinal,z->
            val b=z.box
            // Related short headings use their column's scale, not full-screen magnification.
            // Pieces remain the exact selected area when a neighbour supplies the scale only.
            // The scale reference sits BELOW the heading: a quote or fragment must never
            // inherit the width of a wide title above it, only of the column it tops.
            val reference=if(z.kind==Kind.TEXT&&b.height<.075f)coherent.filter{p->
                p.kind==Kind.TEXT&&p.box.top>b.top&&p.box.width>=b.width&&abs(p.box.left-b.left)<.075f&&(min(p.box.right,b.right)-max(p.box.left,b.left))>.4f*min(p.box.width,b.width)&&
                max(0f,max(p.box.top,b.top)-min(p.box.bottom,b.bottom))<=.035f
            }.maxByOrNull{it.box.width}?.box?.let{Box(min(b.left,it.left),b.top,max(b.right,it.right),b.bottom)}?:b else b
            val overlaps=seen.filter{it.intersect(b)>0&&it.left<=b.left+.000001f&&it.right>=b.right-.000001f}
            val cuts=(listOf(b.top,b.bottom)+overlaps.flatMap{listOf(it.top.coerceIn(b.top,b.bottom),it.bottom.coerceIn(b.top,b.bottom))}).distinct().sorted()
            for(i in 0 until cuts.lastIndex){
                val top=cuts[i];val bottom=cuts[i+1];if(bottom<=top)continue
                // Never subtract x intervals. Even a small side overlap may contain the
                // first letters of every line in the next column (or the ends in reverse order).
                if(overlaps.none{it.top<bottom&&it.bottom>top}){
                    result+=ReadBand(z.id,ordinal,z.kind,reference,top,bottom,listOf(Box(b.left,top,b.right,bottom)))
                }
            }
            seen+=b
        }
        return result
    }
}
