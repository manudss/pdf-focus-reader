package com.pdffocus.reader
import kotlin.math.*
data class ReadBand(val zoneId:Int,val ordinal:Int,val kind:Kind,val original:Box,val top:Float,val bottom:Float,val pieces:List<Box>)
object ContinuousLayout {
    /** Ordered geometric union: every source pixel belongs to the first selected block covering it. */
    fun bands(zones:List<Zone>):List<ReadBand>{
        val seen=mutableListOf<Box>();val result=mutableListOf<ReadBand>()
        zones.forEachIndexed{ordinal,z->
            val b=z.box.clamp();if(b.width<=0||b.height<=0)return@forEachIndexed
            val overlaps=seen.filter{it.intersect(b)>0}
            val cuts=(listOf(b.top,b.bottom)+overlaps.flatMap{listOf(it.top.coerceIn(b.top,b.bottom),it.bottom.coerceIn(b.top,b.bottom))}).distinct().sorted()
            for(i in 0 until cuts.lastIndex){
                val top=cuts[i];val bottom=cuts[i+1];if(bottom<=top)continue
                var xs=listOf(b.left to b.right)
                for(p in overlaps.filter{it.top<bottom&&it.bottom>top})xs=xs.flatMap{(l,r)->if(p.right<=l||p.left>=r)listOf(l to r)else listOfNotNull(if(p.left>l)l to min(r,p.left)else null,if(p.right<r)max(l,p.right)to r else null)}
                val pieces=xs.filter{it.second>it.first}.map{Box(it.first,top,it.second,bottom)}
                if(pieces.isNotEmpty())result+=ReadBand(z.id,ordinal,z.kind,b,top,bottom,pieces)
            }
            seen+=b
        }
        return result
    }
}
