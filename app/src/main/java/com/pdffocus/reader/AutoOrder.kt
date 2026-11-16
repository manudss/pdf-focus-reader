package com.pdffocus.reader
import kotlin.math.*

/**
 * Geometry-only reading order proposal for pages never prepared before.
 * Wide blocks (titles, banners, full-width images) split the page into
 * horizontal bands; each band reads its columns top-down, left column
 * first. Every detected block is proposed: nothing is silently excluded,
 * so a fragile layout never loses content. The proposal is computed once,
 * when the page's blocks are detected; later edits are the user's own
 * order and are never recomputed.
 */
object AutoOrder {
    private const val SPANNING_WIDTH=.6f
    private const val SHORT_HEIGHT=.075f
    private fun overlap(l0:Float,r0:Float,l1:Float,r1:Float)=minOf(r0,r1)-maxOf(l0,l1)

    /** Overlap-merge the band's blocks into columns, then read columns left to right, top-down. */
    private fun columns(band:List<Zone>):List<Zone>{
        val members=band.map{mutableListOf(it)}.toMutableList()
        val spans=band.map{it.box.left to it.box.right}.toMutableList()
        var merged=true
        while(merged){
            merged=false
            outer@for(i in spans.indices)for(j in i+1 until spans.size){
                val a=spans[i];val b=spans[j]
                if(overlap(a.first,a.second,b.first,b.second)>.3f*minOf(a.second-a.first,b.second-b.first)){
                    spans[i]=minOf(a.first,b.first) to maxOf(a.second,b.second)
                    members[i].addAll(members[j]);members.removeAt(j);spans.removeAt(j)
                    merged=true;break@outer
                }
            }
        }
        val order=spans.indices.sortedBy{spans[it].first}
        return order.flatMap{members[it].sortedBy{z->z.box.top}}
    }

    fun propose(zones:List<Zone>):List<Int>{
        if(zones.isEmpty())return emptyList()
        // A block covering most of the page width (title, banner, wide image) starts a new band.
        val spanning=zones.filter{it.box.right-it.box.left>=SPANNING_WIDTH}.sortedBy{it.box.top}
        // Tiny blocks flush against a page edge (folios) read last; they are proposed, never dropped.
        val edge=zones.filter{it !in spanning&&it.box.height<SHORT_HEIGHT&&(it.box.bottom<.08f||it.box.top>.92f)}
        val body=zones.filter{it !in spanning&&it !in edge}
        val remaining=body.toMutableList()
        val result=mutableListOf<Zone>()
        for(block in spanning){
            val above=remaining.filter{it.box.top<block.box.top}
            result+=columns(above);remaining.removeAll(above)
            result+=block
        }
        result+=columns(remaining)
        result+=edge.sortedBy{it.box.top}
        return result.map{it.id}
    }
}
