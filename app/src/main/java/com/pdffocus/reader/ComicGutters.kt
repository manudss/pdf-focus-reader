package com.pdffocus.reader
import kotlin.math.*
/** Frameless comic panels found from the paper colour and the empty gutters between drawings.
 * Used only after the closed black frames of [ComicPanels] are absent, and only in Comic and Manga modes:
 * a page of text has no panel layout and must never be cut by this. No source pixels are retained.
 * Known limits: strips with no gutter, diagonal or polygonal panels and panels that touch stay merged. */
object ComicGutters {
 private const val W=420
 /** Closed frames stay authoritative locally, but must not suppress frameless panels elsewhere on the page. */
 fun panels(pixels:IntArray,width:Int,height:Int,gutters:Boolean,cancelled:()->Boolean={false}):List<Box>{
  val framed=ComicPanels.find(pixels,width,height,cancelled)
  if(!gutters||cancelled())return framed
  val emptyGutters=find(pixels,width,height,cancelled)
  if(cancelled())return emptyList()
  return reconcile(framed,emptyGutters)
 }
 /** Keep a closed frame unless separated gutter candidates account for almost all of it.
  * Non-overlapping gutter panels elsewhere are added. Never combine intersecting alternative proposals. */
 internal fun reconcile(framed:List<Box>,gutters:List<Box>):List<Box>{
  if(framed.isEmpty())return gutters
  if(gutters.isEmpty())return framed
  val out=ArrayList<Box>()
  for(frame in framed){
   val inner=gutters.filter{it.intersect(frame)>=it.area*.95f}
   val cover=inner.sumOf{it.intersect(frame).toDouble()}/frame.area
   if(inner.size>=2&&cover>=.8&&inner.indices.none{i->(i+1 until inner.size).any{j->inner[i].intersect(inner[j])>.000001f}})out.addAll(inner)
   else out+=frame
  }
  for(g in gutters)if(out.none{it.intersect(g)>.03f*min(it.area,g.area)})out+=g
  if(out.indices.any{i->(i+1 until out.size).any{j->out[i].intersect(out[j])>.03f*min(out[i].area,out[j].area)}})return framed
  return out.sortedWith(compareBy<Box>{it.top}.thenBy{it.left})
 }
 fun find(pixels:IntArray,width:Int,height:Int,cancelled:()->Boolean={false}):List<Box>{
  require(pixels.size==width*height)
  if(width<80||height<80)return emptyList()
  val w=min(W,width);val h=max(1,(height.toLong()*w/width).toInt())
  val r=IntArray(w*h);val g=IntArray(w*h);val b=IntArray(w*h)
  for(y in 0 until h){val sy=min(height-1,(y.toLong()*height/h).toInt());for(x in 0 until w){val p=pixels[sy*width+min(width-1,(x.toLong()*width/w).toInt())];r[y*w+x]=(p ushr 16)and 255;g[y*w+x]=(p ushr 8)and 255;b[y*w+x]=p and 255}}
  if(cancelled())return emptyList()
  val band=max(1,(min(h,w)*.02f).toInt())
  val edge=ArrayList<Int>()
  for(y in 0 until h)for(x in 0 until w)if(y<band||y>=h-band||x<band||x>=w-band)edge+=y*w+x
  val buckets=HashMap<Int,MutableList<Int>>()
  for(i in edge)buckets.getOrPut((r[i]/16)*256+(g[i]/16)*16+b[i]/16){mutableListOf()}+=i
  val mode=buckets.values.maxByOrNull{it.size}?:return emptyList()
  fun median(c:IntArray)=mode.map{c[it]}.sorted()[mode.size/2]
  val pr=median(r);val pg=median(g);val pb=median(b)
  val ink=BooleanArray(w*h){abs(r[it]-pr)+abs(g[it]-pg)+abs(b[it]-pb)>60}
  var grown=ink
  repeat(2){grown=morph(grown,w,h,true)};repeat(2){grown=morph(grown,w,h,false)}
  grown=fillHoles(grown,w,h)
  repeat(2){grown=morph(grown,w,h,false)};repeat(2){grown=morph(grown,w,h,true)}
  if(cancelled())return emptyList()
  val label=IntArray(w*h);var n=0;val boxes=ArrayList<IntArray>()
  val queue=IntArray(w*h)
  for(s in 0 until w*h){
   if(!grown[s]||label[s]!=0)continue
   n++;var head=0;var tail=0;queue[tail++]=s;label[s]=n
   var t=h;var l=w;var bt=0;var rt=0
   while(head<tail){
    val p=queue[head++];val x=p%w;val y=p/w
    if(y<t)t=y;if(y>bt)bt=y;if(x<l)l=x;if(x>rt)rt=x
    if(x>0&&grown[p-1]&&label[p-1]==0){label[p-1]=n;queue[tail++]=p-1}
    if(x<w-1&&grown[p+1]&&label[p+1]==0){label[p+1]=n;queue[tail++]=p+1}
    if(y>0&&grown[p-w]&&label[p-w]==0){label[p-w]=n;queue[tail++]=p-w}
    if(y<h-1&&grown[p+w]&&label[p+w]==0){label[p+w]=n;queue[tail++]=p+w}
   }
   boxes+=intArrayOf(n,t,l,bt+1,rt+1)
  }
  val out=ArrayList<Box>()
  for(c in boxes){
   if(cancelled())return emptyList()
   val (id,t,l,bt,rt)=c
   if((bt-t).toLong()*(rt-l)<h.toLong()*w*.02)continue
   val sub=BooleanArray(w*h){ink[it]&&label[it]==id}
   val pieces=ArrayList<IntArray>();cut(sub,w,h,intArrayOf(t,l,bt,rt),pieces,.06f)
   for(q in pieces)if((q[2]-q[0]).toLong()*(q[3]-q[1])>=h.toLong()*w*.015)out+=Box(q[1].toFloat()/w,q[0].toFloat()/h,q[3].toFloat()/w,q[2].toFloat()/h)
  }
  val area=out.sumOf{it.area.toDouble()}
  // A page without at least two separate panels is left to the normal split, never cut by guesswork.
  if(out.size<2||area<.2)return emptyList()
  if(out.indices.any{i->(i+1 until out.size).any{j->out[i].intersect(out[j])>.03f*min(out[i].area,out[j].area)}})return emptyList()
  return out
 }
 private operator fun IntArray.component1()=this[0]
 private operator fun IntArray.component2()=this[1]
 private operator fun IntArray.component3()=this[2]
 private operator fun IntArray.component4()=this[3]
 private operator fun IntArray.component5()=this[4]
 /** 3x3 square dilation or erosion; outside the image counts as empty. */
 private fun morph(a:BooleanArray,w:Int,h:Int,dilate:Boolean):BooleanArray{
  val o=BooleanArray(w*h)
  for(y in 0 until h)for(x in 0 until w){
   var any=false;var all=true
   for(dy in -1..1)for(dx in -1..1){val xx=x+dx;val yy=y+dy;val v=xx in 0 until w&&yy in 0 until h&&a[yy*w+xx];if(v)any=true else all=false}
   o[y*w+x]=if(dilate)any else all
  }
  return o
 }
 private fun fillHoles(a:BooleanArray,w:Int,h:Int):BooleanArray{
  val outside=BooleanArray(w*h);val q=IntArray(w*h);var head=0;var tail=0
  fun push(p:Int){if(!a[p]&&!outside[p]){outside[p]=true;q[tail++]=p}}
  for(x in 0 until w){push(x);push((h-1)*w+x)};for(y in 0 until h){push(y*w);push(y*w+w-1)}
  while(head<tail){val p=q[head++];val x=p%w;val y=p/w;if(x>0)push(p-1);if(x<w-1)push(p+1);if(y>0)push(p-w);if(y<h-1)push(p+w)}
  return BooleanArray(w*h){a[it]||!outside[it]}
 }
 /** Recursive cut along the widest empty gutter, never closer than [minFraction] to a side. */
 private fun cut(mask:BooleanArray,w:Int,h:Int,reg:IntArray,out:MutableList<IntArray>,minFraction:Float){
  val t=reg[0];val l=reg[1];val b=reg[2];val r=reg[3]
  if((b-t)<h*minFraction*1.8f&&(r-l)<w*minFraction*1.8f){out+=reg;return}
  var best:IntArray?=null // size, axis, start, end
  for(axis in 0..1){
   val n=if(axis==0)b-t else r-l;val full=if(axis==0)h else w
   val prof=FloatArray(n){i->var s=0;if(axis==0){for(x in l until r)if(mask[(t+i)*w+x])s++;s.toFloat()/(r-l)}else{for(y in t until b)if(mask[y*w+l+i])s++;s.toFloat()/(b-t)}}
   val minLen=max(3,(full*.006f).toInt());val lo=(full*minFraction).toInt()
   var i=0
   while(i<n){
    if(prof[i]<.012f){var j=i;while(j<n&&prof[j]<.012f)j++
     if(j-i>=minLen&&i>=lo&&n-j>=lo&&(best==null||j-i>best[0]))best=intArrayOf(j-i,axis,i,j)
     i=j}else i++
   }
  }
  val c=best
  if(c==null){out+=reg;return}
  if(c[1]==0){cut(mask,w,h,intArrayOf(t,l,t+c[2],r),out,minFraction);cut(mask,w,h,intArrayOf(t+c[3],l,b,r),out,minFraction)}
  else{cut(mask,w,h,intArrayOf(t,l,b,l+c[2]),out,minFraction);cut(mask,w,h,intArrayOf(t,l+c[3],b,r),out,minFraction)}
 }
}
