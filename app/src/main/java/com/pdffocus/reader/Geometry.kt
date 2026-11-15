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
data class Zone(val id: Int, val box: Box, val kind: Kind, val lineBottoms: List<Float> = emptyList())
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
