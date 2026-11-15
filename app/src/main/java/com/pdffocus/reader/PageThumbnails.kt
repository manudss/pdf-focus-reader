package com.pdffocus.reader
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import kotlin.math.*
/** GridView creates only visible cells. Raster jobs and cache are bounded, PDF access stays serialized. */
class PageThumbnails(context:Context,val pageCount:Int,val currentPage:Int,private val execute:((()->Unit)->Unit),private val render:(Int)->Bitmap?):BaseAdapter(){
 private val c=context;private val d=c.resources.displayMetrics.density;private fun dp(n:Int)=(n*d).roundToInt()
 private val main=Handler(Looper.getMainLooper());@Volatile private var active=true
 @Volatile private var wanted=(currentPage-9).coerceAtLeast(0)..(currentPage+11).coerceAtMost(pageCount-1)
 private val pending=mutableSetOf<Int>();private val failed=mutableSetOf<Int>()
 private val cache=object:android.util.LruCache<Int,Bitmap>(3*1024*1024){override fun sizeOf(key:Int,value:Bitmap)=value.byteCount}
 private data class Cell(val root:LinearLayout,val image:ImageView,val number:TextView,var bound:Int=-1,var bitmap:Bitmap?=null)
 private val holders=mutableMapOf<Int,java.lang.ref.WeakReference<Cell>>()
 override fun getCount()=pageCount
 override fun getItem(position:Int)=position
 override fun getItemId(position:Int)=position.toLong()
 override fun hasStableIds()=true
 fun visible(first:Int,count:Int){wanted=(first-3).coerceAtLeast(0)..(first+count+2).coerceAtMost(pageCount-1)}
 fun close(){active=false;cache.evictAll()}
 internal fun cachedBytes()=cache.size()
 internal fun pendingCount()=pending.size
 override fun getView(position:Int,convertView:View?,parent:ViewGroup):View {
  val holder=(convertView?.tag as? Cell)?:run{val root=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(5),dp(5),dp(5),dp(5))};val image=ImageView(c).apply{scaleType=ImageView.ScaleType.FIT_CENTER;setBackgroundColor(Color.WHITE)};val number=TextView(c).apply{gravity=Gravity.CENTER;textSize=13f;setPadding(0,dp(5),0,dp(2));setTypeface(null,android.graphics.Typeface.BOLD)};root.addView(image,LinearLayout.LayoutParams(-1,dp(142)));root.addView(number,LinearLayout.LayoutParams(-1,dp(30)));Cell(root,image,number).also{root.tag=it}}
  holder.number.text="${position+1}"+if(position==currentPage)" · actuelle" else ""
  val selected=position==currentPage;holder.number.setTextColor(if(selected)Color.rgb(50,109,93)else Color.rgb(37,31,33));holder.root.background=GradientDrawable().apply{setColor(if(selected)Color.rgb(234,241,237)else Color.TRANSPARENT);cornerRadius=dp(9).toFloat();setStroke(dp(if(selected)2 else 1),if(selected)Color.rgb(50,109,93)else Color.TRANSPARENT)}
  holder.root.contentDescription="Page ${position+1}"+if(selected)", page actuelle" else ""
  val retained=if(holder.bound==position)holder.bitmap else null;val shown=cache.get(position)?:retained;holder.bound=position;holder.bitmap=shown;holders[position]=java.lang.ref.WeakReference(holder);holder.image.setImageBitmap(shown);holder.image.alpha=if(position in failed).3f else 1f
  if(active&&shown==null&&position !in failed&&pending.size<24&&pending.add(position))execute{
   val needed=active&&position in wanted
   val bitmap=if(needed)try{render(position)}catch(_:Exception){null}else null
   main.post{pending.remove(position);if(!active){bitmap?.recycle()}else if(needed){if(bitmap!=null){cache.put(position,bitmap);holders[position]?.get()?.takeIf{it.bound==position}?.let{it.bitmap=bitmap;it.image.setImageBitmap(bitmap)}}else{failed.add(position);holders[position]?.get()?.takeIf{it.bound==position}?.root?.contentDescription="Page ${position+1}, aperçu indisponible"}};if(active&&holders.any{(n,ref)->val cell=ref.get();n in wanted&&cell?.bound==n&&cell.bitmap==null&&n !in pending&&n !in failed})notifyDataSetChanged()}
  }
  return holder.root
 }
}
