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
        val scale=min(rawScale,2400f/max(box.width*p.width,box.height*p.height))
        val bw=max(1,(box.width*p.width*scale).roundToInt());val bh=max(1,(box.height*p.height*scale).roundToInt())
        val b=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE)
        val m=Matrix().apply{setScale(scale,scale);postTranslate(-box.left*p.width*scale,-box.top*p.height*scale)}
        try{p.render(b,null,m,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);b}catch(t:Throwable){b.recycle();throw t}
    }
    override fun close(){pdf.close();descriptor.close()}
}
