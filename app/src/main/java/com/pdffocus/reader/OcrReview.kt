package com.pdffocus.reader

/** Both readings stay separate. Agreement is evidence, never a correctness certificate. */
data class OcrPass(val engine:String,val variant:String,val words:List<OcrWord>)
data class OcrEdit(val id:Int,val start:Int,val end:Int,val raw:String,val corrected:String,val source:Box,val readings:List<OcrEvidence> = emptyList())
data class OcrMark(val edit:OcrEdit,val start:Int,val end:Int)
data class OcrReviewText(val corrected:String,val edits:List<OcrEdit>,val keptRaw:Set<Int> = emptySet()) {
 fun fingerprint():String=java.security.MessageDigest.getInstance("SHA-256").digest((corrected+"\u0000"+edits.joinToString("\u0000"){"${it.start}:${it.end}:${it.raw}:${it.source}"}).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
 fun chooseRaw(id:Int,keep:Boolean)=copy(keptRaw=if(keep)keptRaw+id else keptRaw-id)
 fun rendered():Pair<String,List<OcrMark>> {
  val out=StringBuilder();val marks=mutableListOf<OcrMark>();var cursor=0
  for(edit in edits){out.append(corrected.substring(cursor,edit.start));val start=out.length;out.append(if(edit.id in keptRaw)edit.raw else edit.corrected);val shown=out.substring(start);val lead=shown.indexOfFirst{!it.isWhitespace()}.coerceAtLeast(0);val tail=shown.indexOfLast{!it.isWhitespace()};marks+=OcrMark(edit,start+lead,if(tail<0)start+lead else start+tail+1);cursor=edit.end}
  out.append(corrected.substring(cursor));return out.toString() to marks
 }
}
object OcrReview {
 private data class Token(val value:String,val start:Int,val end:Int)
 private fun tokens(s:String)=Regex("\\S+").findAll(s).map{Token(it.value,it.range.first,it.range.last+1)}.toList()
 /** Normalize runs, not French number spacing, paragraph boundaries or punctuation. */
 fun whitespace(s:String):String=s.replace("\r\n","\n").replace('\r','\n').split(Regex("\n[ \\t]*\n+")).joinToString("\n\n"){it.replace(Regex("[\\s\\u00a0\\u202f]+")," ").trim()}.trim()
 /** Bounded token alignment retains insertions, deletions and many-to-one hyphen repairs. */
 fun compare(raw:String,corrected:String,source:Box):OcrReviewText {
  val a=tokens(raw);val b=tokens(corrected)
  if(a.size>350||b.size>350){
   if(raw==corrected)return OcrReviewText(corrected,emptyList())
   // Bound each alignment by unchanged, unique words. Long blocks must not turn into
   // one highlighted edit merely because source slices now share one reflow layout.
   val ac=a.groupingBy{it.value}.eachCount();val bc=b.groupingBy{it.value}.eachCount()
   val indexes=b.mapIndexed{i,t->t.value to i}.toMap()
   val pairs=a.mapIndexedNotNull{i,t->if(ac[t.value]==1&&bc[t.value]==1)i to indexes.getValue(t.value)else null}
   val tail=mutableListOf<Int>();val previous=IntArray(pairs.size){-1}
   for(k in pairs.indices){var lo=0;var hi=tail.size;while(lo<hi){val m=(lo+hi)/2;if(pairs[tail[m]].second<pairs[k].second)lo=m+1 else hi=m};if(lo>0)previous[k]=tail[lo-1];if(lo==tail.size)tail+=k else tail[lo]=k}
   val anchors=mutableListOf<Pair<Int,Int>>();var k=tail.lastOrNull()?:-1
   while(k>=0){anchors+=pairs[k];k=previous[k]};anchors.reverse()
   if(anchors.isEmpty())return OcrReviewText(corrected,listOf(OcrEdit(0,0,corrected.length,raw,corrected,source)))
   val edits=mutableListOf<OcrEdit>();var x=0;var y=0
   for((ai,bi)in anchors+listOf(a.size to b.size)){
    val endX=a.getOrNull(ai)?.start?:raw.length;val endY=b.getOrNull(bi)?.start?:corrected.length
    val part=compare(raw.substring(x,endX),corrected.substring(y,endY),source)
    for(edit in part.edits)edits+=edit.copy(id=edits.size,start=edit.start+y,end=edit.end+y)
    x=a.getOrNull(ai)?.end?:raw.length;y=b.getOrNull(bi)?.end?:corrected.length
   }
   return OcrReviewText(corrected,edits)
  }
  val dp=Array(a.size+1){IntArray(b.size+1)}
  for(i in a.indices.reversed())for(j in b.indices.reversed())dp[i][j]=if(a[i].value==b[j].value)1+dp[i+1][j+1]else maxOf(dp[i+1][j],dp[i][j+1])
  val edits=mutableListOf<OcrEdit>();var i=0;var j=0
  while(i<a.size||j<b.size){
   if(i<a.size&&j<b.size&&a[i].value==b[j].value){i++;j++;continue}
   val ai=i;val bj=j
   while(i<a.size||j<b.size){if(i<a.size&&j<b.size&&a[i].value==b[j].value)break;if(i<a.size&&(j==b.size||dp[i+1][j]>=dp[i][j+1]))i++ else j++}
   val terminal=i==a.size&&j==b.size
   val rawStart=if(terminal)if(ai>0)a[ai-1].end else 0 else a.getOrNull(ai)?.start?:raw.length
   val rawEnd=if(terminal)raw.length else a[i].start
   val start=if(terminal)if(bj>0)b[bj-1].end else 0 else b.getOrNull(bj)?.start?:corrected.length
   val end=if(terminal)corrected.length else b[j].start
   edits+=OcrEdit(edits.size,start,end,raw.substring(rawStart,rawEnd),corrected.substring(start,end),source)
  }
  return OcrReviewText(corrected,edits)
 }
}
