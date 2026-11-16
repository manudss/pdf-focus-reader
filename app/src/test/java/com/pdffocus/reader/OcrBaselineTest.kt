package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class OcrBaselineTest {
 private val spelling=object:OcrSpelling{
  override fun valid(word:String)=word in setOf("marcher","porter","parler")
  override fun suggestions(word:String,limit:Int)=emptyList<String>()
 }
 private fun w(t:String,y:Float=0f)=OcrWord(t,Box(.1f,y,.4f,y+.02f),Box(.1f,y,.4f,y+.02f),99f)
 @Test fun rejectsInsertedLetterWhenInitialFragmentJoinsToKnownWord(){
  val out=OcrBaseline.protectHyphenFragments(listOf(w("mar-")),listOf(w("marq-"),w("cher",.03f)),spelling)
  assertEquals("mar-",out.first().text)
  assertEquals("marcher",OcrEnhancer(emptyList(),spelling::valid).text(out))
 }
 @Test fun doesNotGuessUnknownWordsValidPreciseOrDistantParagraph(){
  for((old,now,end,y)in listOf(listOf("po-","par-","ler",.03f),listOf("xy-","xyz-","lier",.03f),listOf("mar-","marq-","cher",.2f))){
   val out=OcrBaseline.protectHyphenFragments(listOf(w(old as String)),listOf(w(now as String),w(end as String,y as Float)),spelling)
   assertEquals(now,out.first().text)
  }
 }
}
