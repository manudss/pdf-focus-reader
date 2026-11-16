package com.pdffocus.reader
import kotlin.math.*
/** Reconstruct source lines, not display-width line breaks. Geometry stays in page coordinates. */
object OcrParagraphs {
 data class Line(val box:Box,val words:List<OcrWord>){val text get()=words.joinToString(" "){it.text.trim().replace(Regex("[\\s\\p{Zs}\\u200b]+")," ")}}
 fun lines(words:List<OcrWord>):List<Line>{
  val groups=mutableListOf<MutableList<OcrWord>>()
  for(w in words.filter{it.text.isNotBlank()}.distinctBy{it.box to it.text}.sortedWith(compareBy({it.line.top},{it.box.left}))){
   val group=groups.lastOrNull{g->val a=g.first().line;val b=w.line;val overlap=min(a.bottom,b.bottom)-max(a.top,b.top);overlap>min(a.height,b.height)*.55f&&abs((a.top+a.bottom-b.top-b.bottom)/2)<max(a.height,b.height)*.55f}
   if(group==null)groups+=mutableListOf(w)else group+=w
  }
  return groups.map{g->Line(g.map{it.box}.reduce{a,b->a.union(b)},g.sortedBy{it.box.left})}.sortedWith(compareBy({it.box.top},{it.box.left}))
 }
 private fun median(v:List<Float>)=v.sorted().let{if(it.isEmpty())0f else it[it.size/2]}
 fun text(words:List<OcrWord>,join:(String,String)->String?):String{
  val ls=lines(words);if(ls.isEmpty())return ""
  val height=median(ls.map{it.box.height}).coerceAtLeast(.0001f)
  val gaps=ls.zipWithNext().map{(a,b)->b.box.top-a.box.bottom}.filter{it>=0&&it<height*1.4f}
  val gap=min(median(gaps),height*.45f);val left=ls.map{it.box.left}.sorted().let{it[(it.size/4).coerceAtMost(it.lastIndex)]}
  val width=ls.maxOf{it.box.right}-left
  val out=StringBuilder();var previous:Line?=null
  val listStart=Regex("^(?:[•●▪]|[-–]\\s|\\d{1,3}[.)]\\s)")
  for(line in ls){val value=line.text;if(value.isBlank())continue
   if(out.isEmpty()){out.append(value);previous=line;continue}
   val p=previous!!;val separation=line.box.top-p.box.bottom
   val indent=line.box.left-left;val previousIndent=p.box.left-left
   // A short line alone is not a paragraph. Require extra leading, a new first-line indent,
   // or list structure. Equal left/right edges may vary from OCR punctuation and justification.
   val shortSentenceEnd=p.box.right<left+width*.65f&&Regex("[.!?][\"'»]?$").containsMatchIn(p.text)
   val modestIndent=indent>max(height*.55f,width*.035f)&&previousIndent<height*.25f&&separation> -height*.15f
   val paragraph=(shortSentenceEnd&&modestIndent)||separation>max(height*.85f,gap+height*.65f)||
       (indent>max(height*.85f,width*.04f)&&previousIndent<indent-height*.55f&&separation> -height*.15f)||listStart.containsMatchIn(value)
   val last=Regex("\\S+$").find(out)?.value.orEmpty();val first=value.substringBefore(' ')
   // A terminal hyphen followed by a wide blank is also a wrap candidate.
   // The dictionary must still validate the join/compound; whitespace is not proof.
   val trailingBlank=left+width-p.box.right
   val terminalHyphen=last.endsWith('-')&&last.dropLast(1).any{it.isLetter()}
   val wrapEdge=p.box.right>=left+width*.72f||(terminalHyphen&&trailingBlank>height*1.2f)
   val isLineWrap=!paragraph&&wrapEdge&&separation<max(height*1.3f,gap+height*.8f)
   val joint=if(isLineWrap)join(last,first)else null
   if(joint!=null){out.setLength(out.length-last.length);out.append(joint).append(value.drop(first.length))}else out.append(if(paragraph)"\n\n" else " ").append(value)
   previous=line
  }
  return out.toString()
 }
}
