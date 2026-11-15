package com.pdffocus.reader
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.Closeable
import kotlin.math.*

/** Used only from a single background executor, including close(). */
class PdfDocument(file:File):Closeable {
    private val descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
    private val pdf=try{PdfRenderer(descriptor)}catch(e:Exception){descriptor.close();throw e}
    val count get()=pdf.pageCount
    fun preview(index:Int):Bitmap=pdf.openPage(index).use{p->
        val scale=min(1.5f,2000f/max(p.width,p.height))
        val b=Bitmap.createBitmap(max(1,(p.width*scale).toInt()),max(1,(p.height*scale).toInt()),Bitmap.Config.ARGB_8888)
        b.eraseColor(Color.WHITE)
        try{p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);b}catch(t:Throwable){b.recycle();throw t}
    }
    fun region(index:Int,box:Box,width:Int,height:Int):Bitmap=pdf.openPage(index).use{p->
        // Tile at screen resolution, with a strict allocation cap.
        val rawScale=min(width/(box.width*p.width),height/(box.height*p.height))
        val scale=min(rawScale,4096f/max(box.width*p.width,box.height*p.height))
        val bw=max(1,ceil(box.width*p.width*scale).toInt());val bh=max(1,ceil(box.height*p.height*scale).toInt())
        val b=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE)
        val m=Matrix().apply{setScale(scale,scale);postTranslate(-box.left*p.width*scale,-box.top*p.height*scale)}
        try{p.render(b,null,m,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);b}catch(t:Throwable){b.recycle();throw t}
    }
    /** Two display pixels of halo keep median/stroke filtering identical across tile joins. */
    fun readingRegion(index:Int,box:Box,width:Int,height:Int,clean:Boolean):Bitmap {
        if(!clean)return region(index,box,width,height)
        val dx=box.width/width*2;val dy=box.height/height*2
        val expanded=Box(max(0f,box.left-dx),max(0f,box.top-dy),min(1f,box.right+dx),min(1f,box.bottom+dy))
        val raw=region(index,expanded,ceil(width*expanded.width/box.width).toInt(),ceil(height*expanded.height/box.height).toInt())
        val processed=try{TextCleaner.clean(raw,true)}finally{raw.recycle()}
        try {
            val l=((box.left-expanded.left)/expanded.width*processed.width).roundToInt().coerceIn(0,processed.width-1)
            val t=((box.top-expanded.top)/expanded.height*processed.height).roundToInt().coerceIn(0,processed.height-1)
            val r=((box.right-expanded.left)/expanded.width*processed.width).roundToInt().coerceIn(l+1,processed.width)
            val b=((box.bottom-expanded.top)/expanded.height*processed.height).roundToInt().coerceIn(t+1,processed.height)
            val result=Bitmap.createBitmap(processed,l,t,r-l,b-t)
            return if(result===processed)processed.copy(Bitmap.Config.ARGB_8888,false) else result
        }finally{processed.recycle()}
    }
    override fun close(){pdf.close();descriptor.close()}
}
