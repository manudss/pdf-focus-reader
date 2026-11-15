package com.pdffocus.reader
import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import kotlin.math.*

/** Bundled local OCR. Raw words never leave memory or enter logs. */
class ZoneDetector(private val context:Context) {
    private val lifecycle=Any()
    private var active:TessBaseAPI?=null
    @Volatile private var stopped=false
    fun cancel(){stopped=true;synchronized(lifecycle){active?.stop()}}
    fun detect(bitmap:Bitmap,obsolete:()->Boolean):List<Zone> {
        val cancelled={stopped||obsolete()}
        val root=File(context.filesDir,"ocr");val data=File(root,"tessdata").apply{mkdirs()}
        for(lang in listOf("fra","eng")) {
            val target=File(data,"$lang.traineddata")
            if(!target.exists()) {
                val tmp=File(data,"$lang.part")
                context.assets.open("tessdata/$lang.traineddata").use{input->tmp.outputStream().use{input.copyTo(it)}}
                check(tmp.renameTo(target)){"Copie du modèle OCR impossible"}
            }
        }
        if(cancelled())return emptyList()
        val api=TessBaseAPI()
        try {
            check(api.init(root.absolutePath,"fra+eng")){"Le moteur OCR n'a pas pu démarrer"}
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            synchronized(lifecycle){if(cancelled())return emptyList();active=api}
            api.setImage(bitmap);api.getHOCRText(0)
            if(cancelled())return emptyList()
            val w=bitmap.width.toFloat();val h=bitmap.height.toFloat();val text=mutableListOf<Zone>()
            val it=api.resultIterator
            if(it!=null)try {
                it.begin()
                do {
                    val r=it.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_PARA)
                    val words=it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_PARA).orEmpty()
                    if(r.width()>8&&r.height()>8&&words.count{c->c.isLetterOrDigit()}>=3) {
                        val b=Box(r.left/w,r.top/h,r.right/w,r.bottom/h).pad(.003f)
                        if(b.area>.00015f&&b.area<.65f)text+=Zone(text.size+1,b,Kind.TEXT)
                    }
                }while(it.next(TessBaseAPI.PageIteratorLevel.RIL_PARA)&&!cancelled())
            }finally{it.delete()}
            if(cancelled())return emptyList()
            val lines=mutableListOf<Pair<Box,Float>>()
            val lineIterator=api.resultIterator
            if(lineIterator!=null)try{
                lineIterator.begin()
                do{val r=lineIterator.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE);val value=lineIterator.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE).orEmpty();if(r.height()>0&&value.any{it.isLetterOrDigit()})lines+=Box(r.left/w,r.top/h,r.right/w,r.bottom/h) to r.bottom/h}while(lineIterator.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)&&!cancelled())
            }finally{lineIterator.delete()}
            val withLines=TextCoverage.includeLines(text,lines.map{it.first}).map{it.copy(box=TextCoverage.safeTextBox(bitmap,it.box))}
            val pictures=pictureCandidates(bitmap,text.map{it.box},cancelled)
            return (TextGrouping.merge(withLines)+pictures.mapIndexed{i,b->Zone((withLines.maxOfOrNull{it.id}?:0)+i+1,b,Kind.IMAGE)})
                .sortedWith(compareBy<Zone>{(it.box.top*35).toInt()}.thenBy{it.box.left}).mapIndexed{i,z->z.copy(id=i+1)}
        }finally{synchronized(lifecycle){active=null;api.recycle()}}
    }
    /** Candidate heuristic, not semantic article or image recognition. */
    internal fun pictureCandidates(bitmap:Bitmap,text:List<Box>,cancelled:()->Boolean):List<Box> {
        val cols=64;val rows=max(1,(bitmap.height.toFloat()/bitmap.width*cols).toInt())
        val small=Bitmap.createScaledBitmap(bitmap,cols*4,rows*4,true)
        try {
            val grid=BooleanArray(cols*rows)
            for(y in 0 until rows)for(x in 0 until cols) {
                if(cancelled())return emptyList()
                var dark=0;var colored=0;var lo=255;var hi=0
                for(dy in 0..3)for(dx in 0..3){
                    val p=small.getPixel(x*4+dx,y*4+dy);val r=(p shr 16)and 255;val g=(p shr 8)and 255;val b=p and 255
                    val l=(r+g+b)/3;lo=min(lo,l);hi=max(hi,l)
                    if(l<215)dark++
                    if(maxOf(r,g,b)-minOf(r,g,b)>22)colored++
                }
                // Text can connect a photo to a nearby column or resemble a textured image.
                val inText=text.any{it.contains((x+.5f)/cols,(y+.5f)/rows)}
                grid[y*cols+x]=!inText&&((dark>=10&&hi-lo>30)||(colored>=7&&dark>=6))
            }
            val seen=BooleanArray(grid.size);val out=mutableListOf<Box>()
            for(start in grid.indices){
                if(!grid[start]||seen[start])continue
                val q=ArrayDeque<Int>();q.add(start);seen[start]=true
                var x0=cols;var y0=rows;var x1=0;var y1=0;var count=0
                while(q.isNotEmpty()){
                    val a=q.removeFirst();val x=a%cols;val y=a/cols
                    x0=min(x0,x);y0=min(y0,y);x1=max(x1,x);y1=max(y1,y);count++
                    for((nx,ny)in listOf(x-1 to y,x+1 to y,x to y-1,x to y+1))if(nx in 0 until cols&&ny in 0 until rows){val n=ny*cols+nx;if(grid[n]&&!seen[n]){seen[n]=true;q.add(n)}}
                }
                val b=Box(x0.toFloat()/cols,y0.toFloat()/rows,(x1+1f)/cols,(y1+1f)/rows).pad(.004f)
                if(b.width>.09&&b.height>.045&&b.area>.012&&b.area<.65&&count>16)out+=b
            }
            return out.filter{b->text.none{t->t.intersect(b)>.92f*b.area}}
        }finally{if(small!==bitmap)small.recycle()}
    }
}
