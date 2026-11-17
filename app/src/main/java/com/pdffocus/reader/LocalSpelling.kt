package com.pdffocus.reader
import java.text.Normalizer
/** Local word-list suggestions, not grammar checking. Never selects or rewrites a word. */
class LocalSpelling(words:Collection<String>):OcrSpelling {
 override fun valid(word:String)=word in lex
 private val lex=words.filter{it.length in 3..32&&it.first().isLowerCase()}.toHashSet()
 private fun bare(s:String)=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{M}"),"")
 private val accents=lex.groupBy{bare(it)}.filterValues{it.size<16}
 override fun suggestions(word:String,limit:Int):List<String>{
  if(word.length !in 4..28||!word.first().isLowerCase()||word.any{!it.isLetter()}||word in lex)return emptyList()
  val out=linkedSetOf<String>();fun offer(s:String){if(s!=word&&s in lex)out+=s}
  accents[bare(word)].orEmpty().sorted().forEach{offer(it)}
  val alphabet="abcdefghijklmnopqrstuvwxyzàâäéèêëîïôöùûüÿçœ"
  for(i in word.indices){offer(word.removeRange(i,i+1));if(i+1<word.length)offer(word.substring(0,i)+word[i+1]+word[i]+word.substring(i+2));for(c in alphabet)offer(word.substring(0,i)+c+word.substring(i+1))}
  for(i in 0..word.length)for(c in alphabet)offer(word.substring(0,i)+c+word.substring(i))
  return out.take(limit.coerceIn(0,12))
 }
}
