package com.pdffocus.reader
import kotlin.math.*
data class ReadBand(val zoneId:Int,val ordinal:Int,val kind:Kind,val original:Box,val top:Float,val bottom:Float,val pieces:List<Box>)
object ContinuousLayout {
    /** Ordered source union. Nested fragments share their selected container's horizontal
     * extent before subtraction, so a title cannot punch a rectangular white hole in it.
     * No y extension, order change, lost selected pixels or repeated source pixels.
     */
    fun bands(zones:List<Zone>):List<ReadBand>{
        val valid=zones.map{it.copy(box=it.box.clamp())}.filter{it.box.width>0&&it.box.height>0}
        val aligned=valid.map{z->
            val b=z.box
            val parent=valid.filter{it.id!=z.id&&it.box.area>b.area*1.02f&&it.box.intersect(b)>=b.area*.999f}.maxByOrNull{it.box.area}
            if(parent==null)z else z.copy(box=Box(parent.box.left,b.top,parent.box.right,b.bottom))
        }
        val seen=mutableListOf<Box>();val result=mutableListOf<ReadBand>()
        aligned.forEachIndexed{ordinal,z->
            val b=z.box
            // Related short headings use their column's scale, not full-screen magnification.
            // Pieces remain the exact selected area when a neighbour supplies the scale only.
            val reference=if(z.kind==Kind.TEXT)aligned.filter{p->
                p.kind==Kind.TEXT&&p.box.width>=b.width&&abs(p.box.left-b.left)<.075f&&(min(p.box.right,b.right)-max(p.box.left,b.left))>.65f*min(p.box.width,b.width)&&
                max(0f,max(p.box.top,b.top)-min(p.box.bottom,b.bottom))<=.035f
            }.maxByOrNull{it.box.width}?.box?.let{Box(min(b.left,it.left),b.top,max(b.right,it.right),b.bottom)}?:b else b
            val overlaps=seen.filter{it.intersect(b)>0}
            val cuts=(listOf(b.top,b.bottom)+overlaps.flatMap{listOf(it.top.coerceIn(b.top,b.bottom),it.bottom.coerceIn(b.top,b.bottom))}).distinct().sorted()
            for(i in 0 until cuts.lastIndex){
                val top=cuts[i];val bottom=cuts[i+1];if(bottom<=top)continue
                var xs=listOf(b.left to b.right)
                for(p in overlaps.filter{it.top<bottom&&it.bottom>top})xs=xs.flatMap{(l,r)->if(p.right<=l||p.left>=r)listOf(l to r)else listOfNotNull(if(p.left>l)l to min(r,p.left)else null,if(p.right<r)max(l,p.right)to r else null)}
                val pieces=xs.filter{it.second>it.first}.map{Box(it.first,top,it.second,bottom)}
                if(pieces.isNotEmpty())result+=ReadBand(z.id,ordinal,z.kind,reference,top,bottom,pieces)
            }
            seen+=b
        }
        return result
    }
}
