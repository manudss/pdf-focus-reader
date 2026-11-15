package com.pdffocus.reader
/** Distinct readings retain their identity; neither confidence nor agreement proves correctness. */
data class OcrEvidence(val engine:String,val text:String,val source:Box,val confidence:Float)
object OcrConsensus {
 private fun core(s:String)=s.trim{!it.isLetter()}.replace('’','\'')
 fun reconcile(words:List<OcrWord>,second:OcrEvidence,spelling:LocalSpelling):List<OcrWord>{
  val tokens=second.text.split(Regex("\\s+")).filter{it.isNotEmpty()}
  // Insertions/deletions are kept as disagreements, not forced onto guessed word boxes.
  if(tokens.size!=words.size||second.confidence<.85f)return words
  return words.mapIndexed{i,w->val old=core(w.text);val candidate=core(tokens[i])
   if(old==candidate||old.length<4||old.firstOrNull()?.isLowerCase()!=true||w.text.any{it.isDigit()}||tokens[i].any{it.isDigit()}||candidate.firstOrNull()?.isLowerCase()!=true)w
   else if(candidate in spelling.suggestions(old)&&w.confidence<95f){val prefix=w.text.takeWhile{!it.isLetter()};val suffix=w.text.takeLastWhile{!it.isLetter()};w.copy(text=prefix+candidate+suffix)}else w
  }
 }
}
