package com.pdffocus.reader
import kotlin.math.*
data class ReadBand(val zoneId:Int,val ordinal:Int,val kind:Kind,val original:Box,val top:Float,val bottom:Float,val pieces:List<Box>)
object ContinuousLayout {
    /** Vertical-only overlap removal. Every visible band keeps its full horizontal extent.
     * A previously selected block can remove rows only when it covers the whole width.
     * Near-twin text blocks of the same column (both edges within .8%, width ratio above
     * .97) also remove leading rows they repeat from the earlier block, but only up to the
     * last text line that an earlier piece already shows ENTIRELY (line top and bottom
     * inside that piece, which must also span this block's width within .2%). A line not
     * proven fully visible earlier stays repeated: partial lines, offset edges and
     * neighbouring columns are never cut. A cut never lands inside a text line either:
     * a line straddling the cut retreats whole to the later block, which may repeat a
     * small sliver of the earlier rendering (keep rather than cut).
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
        data class Placed(val zone:Zone,val box:Box,val pieces:List<Box>)
        val seen=mutableListOf<Placed>();val result=mutableListOf<ReadBand>()
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
            val overlaps=mutableListOf<Box>()
            seen.forEach{s->
                if(s.box.intersect(b)<=0)return@forEach
                if(s.box.left<=b.left+.000001f&&s.box.right>=b.right-.000001f){overlaps+=s.box;return@forEach}
                // Same-column text blocks: drop the leading rows this block repeats from the
                // earlier one. Rows are removed only up to the last line an earlier piece
                // already shows entirely; anything else stays repeated, never cut.
                if(z.kind!=Kind.TEXT||z.manual||s.zone.kind!=Kind.TEXT||s.zone.manual)return@forEach
                if(abs(s.box.left-b.left)>.008f||abs(s.box.right-b.right)>.008f)return@forEach
                if(min(s.box.width,b.width)<=max(s.box.width,b.width)*.97f)return@forEach
                val bandTop=max(s.box.top,b.top);val bandBottom=min(s.box.bottom,b.bottom)
                val seq=(z.lineBottoms+s.zone.lineBottoms).distinct().sorted()
                var cut=b.top;var blocked=false
                for(lb in seq.filter{it>bandTop&&it<=bandBottom+.001f}){
                    val lineTop=seq.lastOrNull{it<lb-.0005f}
                    if(lineTop==null){blocked=true;break}
                    val shownEarlier=seen.flatMap{it.pieces}.any{it.top<=lineTop+.001f&&it.bottom>=lb-.001f&&it.left<=b.left+.002f&&it.right>=b.right-.002f}
                    if(shownEarlier)cut=lb+.001f else{blocked=true;break}
                }
                // Every band line proved fully shown earlier: the whole band is a repeat,
                // trailing blank rows included. A blocked cut stops at the last proven line.
                if(!blocked&&cut>b.top)cut=bandBottom
                // Never sever a line between tiles: when the cut lands strictly inside a
                // text line, that line stays whole with this block and the cut retreats to
                // the line's top. The earlier block then repeats a small sliver of it, which
                // is the documented trade: keep rather than cut. Adjacent lines sharing an
                // exact boundary are not severed by the .001 join epsilon.
                if(cut>b.top){
                    val crossed=seq.zipWithNext().firstOrNull{(lineTop,lineBottom)->lineTop<cut-.002f&&lineBottom>cut}
                    if(crossed!=null)cut=crossed.first
                }
                if(cut>b.top)overlaps+=Box(s.box.left,bandTop,s.box.right,min(bandBottom,cut))
            }
            val cuts=(listOf(b.top,b.bottom)+overlaps.flatMap{listOf(it.top.coerceIn(b.top,b.bottom),it.bottom.coerceIn(b.top,b.bottom))}).distinct().sorted()
            val mine=mutableListOf<Box>()
            for(i in 0 until cuts.lastIndex){
                val top=cuts[i];val bottom=cuts[i+1];if(bottom<=top)continue
                // Never subtract x intervals. Even a small side overlap may contain the
                // first letters of every line in the next column (or the ends in reverse order).
                if(overlaps.none{it.top<bottom&&it.bottom>top}){
                    mine+=Box(b.left,top,b.right,bottom)
                    result+=ReadBand(z.id,ordinal,z.kind,reference,top,bottom,listOf(Box(b.left,top,b.right,bottom)))
                }
            }
            seen+=Placed(z,b,mine)
        }
        return result
    }
}
