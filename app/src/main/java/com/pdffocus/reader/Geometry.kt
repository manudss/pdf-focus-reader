package com.pdffocus.reader
import kotlin.math.*

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right-left
    val height get() = bottom-top
    val area get() = max(0f,width)*max(0f,height)
    fun contains(x: Float,y: Float) = x in left..right && y in top..bottom
    fun intersect(o: Box): Float = max(0f,min(right,o.right)-max(left,o.left))*max(0f,min(bottom,o.bottom)-max(top,o.top))
    fun clamp() = Box(left.coerceIn(0f,1f),top.coerceIn(0f,1f),right.coerceIn(0f,1f),bottom.coerceIn(0f,1f))
    fun pad(p:Float) = Box(left-p,top-p,right+p,bottom+p).clamp()
    fun union(o: Box) = Box(min(left,o.left),min(top,o.top),max(right,o.right),max(bottom,o.bottom))
}
enum class Kind { TEXT, IMAGE }
data class Zone(val id: Int, val box: Box, val kind: Kind, val lineBottoms: List<Float> = emptyList(),val manual:Boolean=false,val panel:Boolean=false,val complement:Boolean=false)
data class ReadingFrame(val zoneId: Int,val box: Box,val part: Int,val total: Int)

/** Coordinates normalised to displayed page orientation, not bitmap pixels. */
object ReadingLayout {
    fun frames(zone: Zone,pageWidth: Int,pageHeight: Int,viewWidth: Int,viewHeight: Int,size: Float=1f): List<ReadingFrame> {
        require(pageWidth>0 && pageHeight>0 && viewWidth>0 && viewHeight>0)
        val b=zone.box.clamp()
        if(b.width<=0f || b.height<=0f) return emptyList()
        if(zone.kind==Kind.IMAGE) return listOf(ReadingFrame(zone.id,b,1,1))
        val visibleHeight=(viewHeight.toFloat()/viewWidth*b.width*pageWidth/pageHeight/size.coerceIn(1f,2.5f)).coerceAtLeast(.002f)
        if(visibleHeight>=b.height)return listOf(ReadingFrame(zone.id,b,1,1))
        val pieces=mutableListOf<Box>();var y=b.top
        while(y<b.bottom-.00001f && pieces.size<5000) {
            val end=min(b.bottom,y+visibleHeight)
            val lineEnd=zone.lineBottoms.filter{it>y+visibleHeight*.65f && it<=end}.maxOrNull()
            val snapped=if(end>=b.bottom-.00001f)b.bottom else lineEnd?.let{min(end,it+.002f)}?:end
            pieces+=Box(b.left,y,b.right,snapped)
            if(snapped>=b.bottom-.00001f)break
            y=max(y+.001f,snapped-min(visibleHeight*.12f,.02f))
        }
        return pieces.mapIndexed{i,box->ReadingFrame(zone.id,box,i+1,pieces.size)}
    }
    fun ordered(zones:List<Zone>,ids:List<Int>)=ids.distinct().mapNotNull{id->zones.find{it.id==id}}
    fun toggle(ids:List<Int>,id:Int)=if(id in ids)ids.filterNot{it==id} else ids+id
    fun smallestHit(zones:List<Zone>,x:Float,y:Float)=zones.filter{it.box.contains(x,y)}.minByOrNull{it.box.area}
}

/** Join nearby fragments within a column, never across the page's inferred column gutter. */
object TextGrouping {
    fun merge(input:List<Zone>):List<Zone> {
        val widths=input.filter{it.kind==Kind.TEXT&&it.box.height>.035f&&it.box.width>.04f}.map{it.box.width}.sorted()
        if(widths.isEmpty())return input
        val maxWidth=(widths[widths.size/2]*1.3f).coerceIn(.08f,.46f)
        val out=input.toMutableList()
        var changed=true
        while(changed){
            changed=false
            loop@for(i in out.indices)for(j in i+1 until out.size){
                val a=out[i];val b=out[j]
                if(a.kind!=Kind.TEXT||b.kind!=Kind.TEXT)continue
                val x=a.box;val y=b.box;val union=x.union(y)
                val contained=x.intersect(y)>.92f*minOf(x.area,y.area)
                val overlap=(minOf(x.right,y.right)-maxOf(x.left,y.left)).coerceAtLeast(0f)
                val gap=maxOf(x.top,y.top)-minOf(x.bottom,y.bottom)
                val aligned=abs(x.left-y.left)<.018f&&abs(x.right-y.right)<.018f
                if(contained||((union.width<=maxWidth||aligned)&&gap>=-.004f&&gap<=.014f&&overlap>.85f*minOf(x.width,y.width))){
                    out[i]=a.copy(box=union,lineBottoms=(a.lineBottoms+b.lineBottoms).distinct().sorted());out.removeAt(j);changed=true;break@loop
                }
            }
        }
        return out.sortedWith(compareBy<Zone>{(it.box.top*35).toInt()}.thenBy{it.box.left})
    }
}
