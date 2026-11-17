package com.pdffocus.reader
/** A precise reread is a candidate, not authority to discard a supported initial reading. */
object OcrBaseline {
 fun protectHyphenFragments(initial:List<OcrWord>,precise:List<OcrWord>,spelling:OcrSpelling):List<OcrWord>{
  val ordered=OcrParagraphs.lines(precise).flatMap{it.words}
  return ordered.mapIndexed{index,word->
   if(!word.text.endsWith('-'))return@mapIndexed word
   val next=ordered.getOrNull(index+1)?:return@mapIndexed word
   if(next.line.top<=word.line.top||next.line.top-word.line.bottom>word.line.height*1.8f)return@mapIndexed word
   val tail=next.text.takeWhile{it.isLetter()}
   val preciseStem=word.text.dropLast(1)
   if(tail.isEmpty()||spelling.valid(preciseStem+tail))return@mapIndexed word
   val alternatives=initial.filter{it.text.endsWith('-')&&it.box.intersect(word.box)>.5f*minOf(it.box.area,word.box.area)}
   val match=alternatives.singleOrNull()?:return@mapIndexed word
   val stem=match.text.dropLast(1)
   val a=stem.lowercase();val b=preciseStem.lowercase()
   // Only a single inserted character in a line-end fragment, validated across the wrap.
   val oneInsertion=b.length==a.length+1&&b.indices.any{b.removeRange(it,it+1)==a}
   if(oneInsertion&&spelling.valid(stem+tail))word.copy(text=match.text)else word
  }
 }
}
