package com.pdffocus.reader

import android.graphics.*
import android.text.StaticLayout
import java.io.*
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.math.*

/** Immutable reading layout, including offscreen content, but never reader controls or the end card. */
data class ArticleImagePlan(val width:Int,val height:Float,val tiles:List<ContinuousReaderView.Tile>,val texts:List<ContinuousReaderView.TextItem>,val surfaces:List<BandSurface>,val density:Float){
    data class BandSurface(val top:Float,val bottom:Float,val color:Int)
    data class Size(val width:Int,val height:Int,val scale:Float,val left:Float)
    fun size():Size{
        require(width>0&&height>0&&height.isFinite()){"Aucun bloc à partager."}
        val left=min(0f,tiles.minOfOrNull{it.rect.left}?:0f)
        val right=max(width.toFloat(),tiles.maxOfOrNull{it.rect.right}?:width.toFloat())
        val full=right-left
        val scale=min(1f,min(1600f/full,65000f/height))
        require(full*scale>=min(360f,full)){"Cet article est trop long pour une seule image lisible. Réduisez le nombre de blocs à partager."}
        return Size(max(1,ceil(full*scale).toInt()),max(1,ceil(height*scale).toInt()),scale,left)
    }
}

/** Bounded strip rendering and PNG encoding: no whole-article bitmap, even for a long article. */
object ArticleImageExporter{
    fun write(plan:ArticleImagePlan,file:File,cancelled:()->Boolean={false},render:(ContinuousReaderView.Tile)->Bitmap?):ArticleImagePlan.Size{
        val size=plan.size();val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        try{file.outputStream().buffered().use{stream->
            PngRows(stream,size.width,size.height).use{png->
                for(y in 0 until size.height step 512){
                    check(!cancelled()){"Partage annulé."}
                    val strip=Bitmap.createBitmap(size.width,min(512,size.height-y),Bitmap.Config.ARGB_8888)
                    try{
                        strip.eraseColor(Color.WHITE);val c=Canvas(strip);c.translate(0f,-y.toFloat());c.scale(size.scale,size.scale);c.translate(-size.left,0f)
                        val top=y/size.scale;val bottom=(y+strip.height)/size.scale
                        for(s in plan.surfaces)if(s.bottom>=top&&s.top<=bottom){paint.color=s.color;c.drawRect(size.left,s.top,size.left+size.width/size.scale,s.bottom,paint)}
                        for(item in plan.texts)if(item.top+item.layout.height>=top&&item.top<=bottom){c.save();c.translate(16*plan.density,item.top);item.layout.draw(c);c.restore()}
                        for(tile in plan.tiles)if(tile.rect.bottom>=top&&tile.rect.top<=bottom){
                            check(!cancelled()){"Partage annulé."}
                            val image=render(tile)?:error("Un bloc n'a pas pu être rendu. Rien n'a été partagé.")
                            try{c.drawBitmap(image,null,tile.rect,paint)}finally{image.recycle()}
                        }
                        png.rows(strip)
                    }finally{strip.recycle()}
                }
            }
        };return size}catch(t:Throwable){file.delete();throw t}
    }
    private class PngRows(private val out:OutputStream,private val width:Int,private val height:Int):Closeable{
        private val deflater=Deflater(6);private val compressed=ByteArray(32768);private val pixels=IntArray(width);private val row=ByteArray(width*3+1);private var count=0;private var bytes=0L
        init{out.write(byteArrayOf(137.toByte(),80,78,71,13,10,26,10));val header=ByteArrayOutputStream();DataOutputStream(header).use{it.writeInt(width);it.writeInt(height);it.write(byteArrayOf(8,2,0,0,0))};chunk("IHDR",header.toByteArray())}
        private fun chunk(type:String,data:ByteArray){bytes+=data.size;check(bytes<80L*1024*1024){"L'image dépasse 80 Mo. Réduisez le nombre de blocs."};val name=type.toByteArray(Charsets.US_ASCII);val d=DataOutputStream(out);d.writeInt(data.size);d.write(name);d.write(data);val crc=CRC32();crc.update(name);crc.update(data);d.writeInt(crc.value.toInt())}
        private fun drain(){while(!deflater.needsInput()){val n=deflater.deflate(compressed);if(n==0)break;chunk("IDAT",compressed.copyOf(n))}}
        fun rows(bitmap:Bitmap){for(y in 0 until bitmap.height){bitmap.getPixels(pixels,0,width,0,y,width,1);row[0]=0;for(x in pixels.indices){val p=pixels[x];row[1+x*3]=(p shr 16).toByte();row[2+x*3]=(p shr 8).toByte();row[3+x*3]=p.toByte()};deflater.setInput(row);drain();count++}}
        override fun close(){try{check(count==height){"Image incomplète."};deflater.finish();while(!deflater.finished()){val n=deflater.deflate(compressed);if(n>0)chunk("IDAT",compressed.copyOf(n))};chunk("IEND",byteArrayOf())}finally{deflater.end()}}
    }
}
