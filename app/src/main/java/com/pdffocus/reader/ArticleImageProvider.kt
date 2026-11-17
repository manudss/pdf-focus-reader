package com.pdffocus.reader

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Read-only, per-URI grants. No PDF, session or directory is exposed by this provider. */
class ArticleImageProvider:ContentProvider(){
    override fun onCreate()=true
    private fun resolve(uri:Uri):File{
        require(uri.authority==context!!.packageName+".article-images"&&uri.pathSegments.size==1)
        val name=uri.lastPathSegment?:error("Image inconnue")
        require(Regex("article-[0-9a-f-]{36}\\.png").matches(name))
        val dir=File(context!!.cacheDir,"article-images").canonicalFile
        val file=File(dir,name).canonicalFile
        require(file.parentFile==dir&&file.isFile){"Image expirée"};return file
    }
    override fun getType(uri:Uri):String{resolve(uri);return "image/png"}
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor{require(mode=="r");return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY)}
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor{
        val file=resolve(uri);val columns=(projection?:arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)).filter{it==OpenableColumns.DISPLAY_NAME||it==OpenableColumns.SIZE}.toTypedArray()
        return MatrixCursor(columns,1).apply{addRow(columns.map{if(it==OpenableColumns.SIZE)file.length()else "article.png"})}
    }
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException()
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
    companion object{
        fun uri(context:Context,file:File):Uri=Uri.Builder().scheme("content").authority(context.packageName+".article-images").appendPath(file.name).build()
    }
}
