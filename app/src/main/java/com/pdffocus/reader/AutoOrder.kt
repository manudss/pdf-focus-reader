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
    private const val ANCHOR_HEIGHT=.4f
    private fun overlap(l0:Float,r0:Float,l1:Float,r1:Float)=minOf(r0,r1)-maxOf(l0,l1)

    /** Fallback for bands without clear columns: overlap-merge blocks, read left to right, top-down. */
    private fun legacyColumns(band:List<Zone>):List<Zone>{
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

    /**
     * Read the band column by column, left column first, top-down inside each column.
     * Columns are anchored by the blocks that run through a large share of the band; touching
     * anchor spans merge (detection often splits one column into stacked tall pieces). Short
     * blocks (headings, line fragments, bridged lines) that chain together by x-overlap away
     * from every anchored column form a column of their own: detection may have split a whole
     * column into short pieces, and attaching those pieces to the nearest anchored column
     * would interleave it with its neighbours. Remaining short blocks attach to the column
     * their centre sits in. Chaining x-overlaps across every block is not used: a wide heading
     * or a narrow full-height strip would glue neighbouring columns into one and interleave
     * their text.
     */
    private fun columns(band:List<Zone>):List<Zone>{
        if(band.size<2)return band.sortedBy{it.box.top}
        val bandHeight=(band.maxOf{it.box.bottom}-band.minOf{it.box.top}).coerceAtLeast(.01f)
        val anchors=band.filter{it.box.bottom-it.box.top>=bandHeight*ANCHOR_HEIGHT}
        val spans=anchors.map{it.box.left to it.box.right}.toMutableList()
        var merged=true
        while(merged){
            merged=false
            outer@for(i in spans.indices)for(j in i+1 until spans.size){
                val a=spans[i];val b=spans[j]
                val distance=maxOf(0f,maxOf(b.first-a.second,a.first-b.second))
                if(distance<=.004f){spans[i]=minOf(a.first,b.first)to maxOf(a.second,b.second);spans.removeAt(j);merged=true;break@outer}
            }
        }
        val rest=band.filter{it !in anchors}
        val groups=rest.map{mutableListOf(it)}.toMutableList()
        val groupSpans=rest.map{it.box.left to it.box.right}.toMutableList()
        merged=true
        while(merged){
            merged=false
            outer@for(i in groupSpans.indices)for(j in i+1 until groupSpans.size){
                val a=groupSpans[i];val b=groupSpans[j]
                if(overlap(a.first,a.second,b.first,b.second)>.3f*minOf(a.second-a.first,b.second-b.first)){
                    groupSpans[i]=minOf(a.first,b.first)to maxOf(a.second,b.second)
                    groups[i].addAll(groups[j]);groups.removeAt(j);groupSpans.removeAt(j)
                    merged=true;break@outer
                }
            }
        }
        for(k in groupSpans.indices){
            val center=(groupSpans[k].first+groupSpans[k].second)/2
            if(spans.none{center>=it.first&&center<=it.second})spans+=groupSpans[k]
        }
        if(spans.size<2)return legacyColumns(band)
        val columns=spans.sortedBy{it.first}.map{it to mutableListOf<Zone>()}
        for(zone in band){
            val center=(zone.box.left+zone.box.right)/2
            val column=columns.firstOrNull{center>=it.first.first&&center<=it.first.second}?:columns.minByOrNull{abs(center-(it.first.first+it.first.second)/2)}!!
            column.second+=zone
        }
        return columns.flatMap{it.second.sortedBy{zone->zone.box.top}}
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
