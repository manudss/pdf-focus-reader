package com.pdffocus.reader
import java.text.Normalizer
import kotlin.math.*
/** Each score remains engine-specific. No calibrated probability or semantic vote is implied. */
data class OcrEvidence(val engine:String,val text:String,val source:Box,val confidence:Float)
object OcrConsensus {
 data class Choice(val words:List<OcrWord>,val reasons:List<String>,val disagreements:Int)
 private fun core(s:String)=s.trim{!it.isLetterOrDigit()}.replace('’','\'')
 private fun bare(s:String)=Normalizer.normalize(s.lowercase(),Normalizer.Form.NFD).replace(Regex("\\p{M}"),"").filter{it.isLetterOrDigit()}
 private fun distance(a:String,b:String):Int{var p=IntArray(b.length+1){it};for((i,c)in a.withIndex()){val n=IntArray(b.length+1);n[0]=i+1;for((j,d)in b.withIndex())n[j+1]=minOf(n[j]+1,p[j+1]+1,p[j]+if(c==d)0 else 1);p=n};return p.last()}
 fun reconcile(words:List<OcrWord>,second:OcrEvidence,spelling:OcrSpelling)=choose(words,second,spelling).words
 fun choose(words:List<OcrWord>,second:OcrEvidence,spelling:OcrSpelling):Choice{
  val tokens=second.text.trim().split(Regex("[\\s\\p{Zs}]+")).filter{it.isNotEmpty()}
  if(words.isEmpty()||tokens.isEmpty()||words.size>120||tokens.size>120)return Choice(words,emptyList(),0)
  // Monotone 1:1, 1:2 and 2:1 alignment. Never align the entire line by token index
  // after a split/merge. Insertions/deletions without matching letters remain Tesseract.
  data class Step(val a:Int,val b:Int,val na:Int,val nb:Int)
  val dp=Array(words.size+1){DoubleArray(tokens.size+1){1e9}};val prev=Array(words.size+1){arrayOfNulls<Step>(tokens.size+1)};dp[0][0]=0.0
  for(i in 0..words.size)for(j in 0..tokens.size){
   val opts=listOf(1 to 1,1 to 2,2 to 1,1 to 0,0 to 1)
   for((na,nb)in opts){if(i+na>words.size||j+nb>tokens.size)continue
    val a=bare(words.subList(i,i+na).joinToString(""){it.text});val b=bare(tokens.subList(j,j+nb).joinToString(""));val cost=if(na==0||nb==0)1.2 else distance(a,b).toDouble()/max(1,max(a.length,b.length))+.09*(na+nb-2)
    if(dp[i][j]+cost<dp[i+na][j+nb]){dp[i+na][j+nb]=dp[i][j]+cost;prev[i+na][j+nb]=Step(i,j,na,nb)}
   }
  }
  val steps=mutableListOf<Step>();var i=words.size;var j=tokens.size;while(i>0||j>0){val s=prev[i][j]?:return Choice(words,emptyList(),0);steps+=s;i=s.a;j=s.b};steps.reverse()
  val out=mutableListOf<OcrWord>();val reasons=mutableListOf<String>();var disagreements=0
  for(s in steps){val old=words.subList(s.a,s.a+s.na);val candidates=tokens.subList(s.b,s.b+s.nb);if(old.isEmpty())continue
   val a=old.joinToString(" "){it.text};val b=candidates.joinToString(" ");if(a==b){out+=old;continue};disagreements++
   val ac=old.map{core(it.text)};val bc=candidates.map{core(it)};val ab=bare(a);val bb=bare(b)
   val numbersA=Regex("\\d+").findAll(a).map{it.value}.toList();val numbersB=Regex("\\d+").findAll(b).map{it.value}.toList()
   val proper=ac.any{w->w.length>1&&(w.all{it.isUpperCase()}||(w.first().isUpperCase()&&spelling.valid(w)))}
   val invalidA=ac.count{!spelling.valid(it)};val invalidB=bc.count{!spelling.valid(it)}
   val sameLetters=ab==bb;val near=distance(ab,bb)<=max(1,max(ab.length,bb.length)/5)
   val previous=words.getOrNull(s.a-1)?.text;val midSentence=previous!=null&&!Regex("[.!?:]$|[.!?][\"'»]$").containsMatchIn(previous)
   val caseOnly=s.na==1&&s.nb==1&&ac[0].equals(bc[0],true)&&ac[0]!=bc[0]
   val lowerCaseRepair=caseOnly&&midSentence&&ac[0].length>2&&ac[0].first().isUpperCase()&&bc[0].first().isLowerCase()&&!spelling.valid(ac[0])&&spelling.valid(bc[0])
   val reason=when{
    s.nb==0||second.confidence<.85f->null
    numbersA!=numbersB||numbersA.isNotEmpty()->null
    proper->null
    ac.any{it.isEmpty()}||bc.any{it.isEmpty()}->null
    lowerCaseRepair->"minuscule attestée dans la phrase"
    caseOnly->null
    sameLetters&&invalidB<invalidA&&invalidB==0->"accents ou découpage validés par le correcteur local"
    near&&invalidB==0&&invalidA>0&&ac.none{it.firstOrNull()?.isUpperCase()==true}&&bc.none{it.firstOrNull()?.isUpperCase()==true}->"lecture Paddle validée par le correcteur local"
    else->null
   }
   if(reason==null){out+=old;continue}
   // Geometry is the aligned source span, not a guessed exact glyph box.
   val box=old.map{it.box}.reduce{a0,b0->a0.union(b0)};val line=old.map{it.line}.reduce{a0,b0->a0.union(b0)}
   val chosen=if(s.na==1&&s.nb==1)listOf(old[0].text.takeWhile{!it.isLetterOrDigit()}+core(candidates[0])+old[0].text.takeLastWhile{!it.isLetterOrDigit()})else candidates
   val total=chosen.sumOf{it.length}.coerceAtLeast(1);var start=0
   for(t in chosen){val span=Box(box.left+box.width*start/total,box.top,box.left+box.width*(start+t.length)/total,box.bottom);out+=OcrWord(t,span,line,old.map{it.confidence}.minOrNull()?:0f);start+=t.length}
   reasons+=reason
  }
  return Choice(out,reasons,disagreements)
 }
}
