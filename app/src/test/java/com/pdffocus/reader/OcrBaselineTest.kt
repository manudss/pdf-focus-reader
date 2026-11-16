package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class OcrBaselineTest {
 private val spelling=object:OcrSpelling{
  override fun valid(word:String)=word in setOf("marcher","porter","parler","préparer")
  override fun suggestions(word:String,limit:Int)=emptyList<String>()
 }
 private fun w(t:String,y:Float=0f)=OcrWord(t,Box(.1f,y,.4f,y+.02f),Box(.1f,y,.4f,y+.02f),99f)
 @Test fun rejectsInsertedLetterWhenInitialFragmentJoinsToKnownWord(){
  val out=OcrBaseline.protectHyphenFragments(listOf(w("mar-")),listOf(w("marq-"),w("cher",.03f)),spelling)
  assertEquals("mar-",out.first().text)
  assertEquals("marcher",OcrEnhancer(emptyList(),spelling::valid).text(out))
  val accented=OcrBaseline.protectHyphenFragments(listOf(w("pré-")),listOf(w("préè-"),w("parer",.03f)),spelling)
  assertEquals("préparer",OcrEnhancer(emptyList(),spelling::valid).text(accented))
  assertEquals(out.first().box,accented.first().box)
 }
 @Test fun doesNotGuessUnknownWordsValidPreciseOrDistantParagraph(){
  data class Case(val old:String,val now:String,val end:String,val y:Float)
  for(c in listOf(Case("po-","par-","ler",.03f),Case("xy-","xyz-","lier",.03f),Case("mar-","marq-","cher",.2f))){
   val out=OcrBaseline.protectHyphenFragments(listOf(w(c.old)),listOf(w(c.now),w(c.end,c.y)),spelling)
   assertEquals(c.now,out.first().text)
  }
 }
}
