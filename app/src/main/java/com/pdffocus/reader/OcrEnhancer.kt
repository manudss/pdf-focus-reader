package com.pdffocus.reader

import java.text.Normalizer
import kotlin.math.*

/** Local, bounded repairs. A dictionary hit alone is never a reason to rewrite a word. */
class OcrEnhancer(words:Collection<String>,private val independentlyValid:((String)->Boolean)?=null) {
 private fun norm(s:String)=s.replace('’','\'')
 private val lex=words.flatMap{listOf(norm(it),norm(it).lowercase())}.toHashSet()
 private fun core(s:String)=norm(s.trim{!it.isLetter()&&it!='\''&&it!='’'})
 private fun valid(s:String):Boolean {val c=core(s);return c in lex||independentlyValid?.invoke(c)==true||(Regex("^(l|d|j|n|m|t|s|c|qu)'").containsMatchIn(c)&&c.substringAfter('\'') in lex)}
 private fun unaccent(s:String)=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{M}"),"")
 private fun distance(a:String,b:String):Int {var prev=IntArray(b.length+1){it};for((i,c)in a.withIndex()){val next=IntArray(b.length+1);next[0]=i+1;for((j,d)in b.withIndex())next[j+1]=minOf(next[j]+1,prev[j+1]+1,prev[j]+if(c==d)0 else 1);prev=next};return prev.last()}
 fun improve(raw:List<OcrWord>,better:List<OcrWord>):List<OcrWord> = raw.flatMap {w->
  val old=core(w.text)
  if(old.length<4||!old.first().isLowerCase()||w.text.any{it.isDigit()}||valid(old))return@flatMap listOf(w)
  val candidates=better.filter{w.box.intersect(it.box)>.65f*it.box.area}.sortedBy{it.box.left}
  if(candidates.isEmpty()||candidates.any{it.confidence<65||!valid(it.text)||it.text.any{c->c.isDigit()}})return@flatMap listOf(w)
  val replacement=candidates.joinToString(""){core(it.text)}
  // No truncated suffixes, new hyphens or punctuation edits. Keep numbers and proper names untouched.
  val oldLetters=old.filter{it.isLetter()};val newLetters=replacement.filter{it.isLetter()}
  if(newLetters.length<oldLetters.length||newLetters.length>oldLetters.length+2||distance(old,replacement)>min(2,max(1,old.length/5)))return@flatMap listOf(w)
  if(candidates.size==1&&unaccent(old)==unaccent(replacement)&&old==replacement)return@flatMap listOf(w)
  if(candidates.size>1&&oldLetters!=newLetters)return@flatMap listOf(w)
  val prefix=w.text.takeWhile{!it.isLetter()};val suffix=w.text.takeLastWhile{!it.isLetter()}
  candidates.mapIndexed{i,c->c.copy(text=(if(i==0)prefix else "")+core(c.text)+(if(i==candidates.lastIndex)suffix else ""),line=w.line)}
 }
 fun join(left:String,right:String):String? {
  if(!left.endsWith('-')||right.isBlank())return null
  val a=left.dropLast(1);val b=right.takeWhile{it.isLetter()||it=='\''||it=='’'||it=='-'}
  if(a.isEmpty()||b.isEmpty())return null
  val plain=core(a+b);val compound=core(a+"-"+b)
  // Prefer an attested real compound; avoid inventing or de-hyphenating names.
  if(compound in lex||independentlyValid?.invoke(compound)==true)return a+"-"+right
  if(valid(plain)&&!b.first().isUpperCase())return a+right
  // Unknown compounds and names stay unchanged; source geometry alone is not proof.
  return null
 }
 fun text(words:List<OcrWord>):String = OcrParagraphs.text(words,::join)

}

data class OcrChunk(val key:Int,val zoneId:Int,val ordinal:Int,val box:Box,val kind:Kind,val words:List<OcrWord> = emptyList())
/** Stable chunk identities. A source word belongs to the first selected covering block. */
object OcrChunks {
 fun plan(zones:List<Zone>,words:List<OcrWord>,fallback:(Zone)->List<Box>):List<OcrChunk>{
  val owned=words.groupBy{w->zones.firstOrNull{it.box.contains((w.box.left+w.box.right)/2,(w.box.top+w.box.bottom)/2)||it.box.intersect(w.box)>.5f*w.box.area}?.id}
  val out=mutableListOf<OcrChunk>()
  zones.forEachIndexed{ordinal,z->
   if(z.kind==Kind.IMAGE){out+=OcrChunk(out.size+1,z.id,ordinal,z.box,z.kind);return@forEachIndexed}
   val ws=owned[z.id].orEmpty();val lines=ws.map{it.line}.distinct().sortedWith(compareBy({it.top},{it.left}))
   if(lines.isEmpty()){fallback(z).forEach{out+=OcrChunk(out.size+1,z.id,ordinal,it,Kind.TEXT)};return@forEachIndexed}
   val first=lines.first();val last=lines.last()
   if(first.top-z.box.top>first.height*1.2f)out+=OcrChunk(out.size+1,z.id,ordinal,Box(z.box.left,z.box.top,z.box.right,first.top-.001f).clamp(),Kind.TEXT)
   var start=0
   while(start<lines.size){var end=min(start+6,lines.size)
    // Do not publish half a cut word at a chunk boundary; at most twelve source lines per job.
    while(end<lines.size&&end-start<12&&ws.filter{it.line==lines[end-1]}.maxByOrNull{it.box.left}?.text?.endsWith('-')==true)end++
    val selected=lines.subList(start,end);val top=if(start==0)max(z.box.top,first.top-.001f) else (lines[start-1].bottom+lines[start].top)/2
    val bottom=if(end==lines.size)min(z.box.bottom,last.bottom+.001f) else (lines[end-1].bottom+lines[end].top)/2
    out+=OcrChunk(out.size+1,z.id,ordinal,Box(z.box.left,top,z.box.right,bottom).clamp(),Kind.TEXT,ws.filter{it.line in selected});start=end
   }
   if(z.box.bottom-last.bottom>last.height*1.5f)out+=OcrChunk(out.size+1,z.id,ordinal,Box(z.box.left,last.bottom+.001f,z.box.right,z.box.bottom).clamp(),Kind.TEXT)
  };return out
 }
}
