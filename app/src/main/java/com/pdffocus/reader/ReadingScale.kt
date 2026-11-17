package com.pdffocus.reader
import kotlin.math.*

/** Text-proportion scaling for the continuous reading view.
 * Every text band shares one scale, so blocks whose source text has the same size
 * render at the same size and other blocks keep the original proportions. A band
 * never exceeds the screen width, and genuinely small text keeps a legibility floor
 * (45% of the body size) as long as it still fits the width. Sizes are normalized
 * to the page; rendered pixels are scale*pagePixels. */
object ReadingScale {
    /** Median OCR line height of a zone; a lone line's height is estimated from its box. */
    fun lineHeight(lineBottoms:List<Float>,boxHeight:Float):Float?{
        val diffs=lineBottoms.zipWithNext().map{(a,b)->b-a}.filter{it>.002f}.sorted()
        if(diffs.isNotEmpty())return diffs[diffs.size/2]
        if(lineBottoms.size==1&&boxHeight>.002f)return boxHeight*.8f
        return null
    }
    /** Weighted median: line-rich body blocks outvote labels and fragments. */
    fun median(values:List<Pair<Float,Int>>):Float?{
        if(values.isEmpty())return null
        val sorted=values.sortedBy{it.first};val total=sorted.sumOf{it.second};var acc=0
        for((v,w)in sorted){acc+=w;if(acc*2>=total)return v}
        return sorted.last().first
    }
    /** One scale for every text band: the typical fill-to-width scale. */
    fun uniform(fills:List<Pair<Float,Int>>):Float?=median(fills)
    /** Scale for one text band. [fill] keeps the band within the screen width,
     * [uniform] is the shared text scale, [lineHeight] the band's source line height,
     * [bodyLineHeight] the typical source line height. */
    fun textScale(fill:Float,uniform:Float,lineHeight:Float?,bodyLineHeight:Float?):Float{
        var scale=min(uniform,fill)
        if(lineHeight!=null&&bodyLineHeight!=null&&lineHeight>0f){
            val floor=.45f*bodyLineHeight*uniform
            if(lineHeight*scale<floor)scale=min(fill,floor/lineHeight)
        }
        return scale
    }
}
