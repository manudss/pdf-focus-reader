package com.pdffocus.reader
import android.content.Context
import morfologik.speller.Speller
import morfologik.stemming.Dictionary
/** Independent French inflected-form dictionary, not the OCR training lexicon. No grammar claims. */
interface OcrSpelling { fun valid(word:String):Boolean; fun suggestions(word:String,limit:Int=5):List<String> }
class FrenchSpelling(context:Context):OcrSpelling {
 private val speller=context.assets.open("spelling/fr.dict").use{d->context.assets.open("spelling/fr.info").use{i->Speller(Dictionary.read(d,i),2)}}
 private val checks=object:LinkedHashMap<String,Boolean>(256,.75f,true){override fun removeEldestEntry(e:MutableMap.MutableEntry<String,Boolean>?)=size>4096}
 private fun normalize(s:String)=s.replace('’','\'').trim{!it.isLetter()&&it!='\''&&it!='-'}
 override fun valid(word:String):Boolean{val s=normalize(word);return checks.getOrPut(s){s.isNotEmpty()&&s.length<=80&&(!speller.isMisspelled(s)||Regex("^(?:[ldjnmtsc]|qu)'",RegexOption.IGNORE_CASE).find(s)?.let{!speller.isMisspelled(s.substring(it.value.length))}==true)}}
 override fun suggestions(word:String,limit:Int):List<String> = if(word.length !in 3..40)emptyList()else speller.findReplacements(normalize(word)).take(limit.coerceIn(0,8))
}
