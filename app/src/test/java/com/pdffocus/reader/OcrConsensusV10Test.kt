package com.pdffocus.reader
import org.junit.Assert.*
import org.junit.Test
class OcrConsensusV10Test {
 private val spelling=object:OcrSpelling{
  private val words=setOf("le","du","jour","étant","arrive","commence","des","logements","lumière","porte-parole","Paris","culte","cuite","50","000")
  override fun valid(word:String)=word in words
  override fun suggestions(word:String,limit:Int)=emptyList<String>()
 }
 private val box=Box(0f,0f,1f,.04f)
 private fun ws(s:String,c:Float=98f)=s.split(' ').mapIndexed{i,t->OcrWord(t,Box(i*.1f,0f,i*.1f+.09f,.04f),box,c)}
 private fun choose(a:String,b:String,c:Float=.99f)=OcrConsensus.choose(ws(a),OcrEvidence("Paddle",b,box,c),spelling)
 private fun output(a:String,b:String)=choose(a,b).words.joinToString(" "){it.text}
 @Test fun dictionaryRepairsBeatHighTesseractConfidence(){assertEquals("commence",output("comence","commence"));assertEquals("du jour étant",output("du Jour etant","du jour étant"))}
 @Test fun namesAcronymsNumbersAndRealWordAmbiguityStayProtected(){for((a,b)in listOf("Paris" to "paris","USA" to "usa","50 000" to "60 000","culte" to "cuite","Jour" to "jour"))assertEquals(a,output(a,b))}
 @Test fun splitAndMergeDoNotShiftLaterWords(){assertEquals("des logements arrive",output("des loge ments arrive","des logements arrive"));assertEquals("des logements arrive",output("deslogements arrive","des logements arrive"))}
 @Test fun unsupportedNewWordsAndWeakSecondReadingStayTesseract(){assertEquals("comence",choose("comence","commence",.4f).words.single().text);assertEquals("texte",output("texte","nouvelle phrase"))}
 @Test fun punctuationAndSourceBoxesAreNotChangedBySpelling(){
  assertEquals("« comence",output("« comence","«comence"))
  assertEquals("commence,",output("comence,","commence."))
  val source=ws("comence,").single();val result=OcrConsensus.choose(listOf(source),OcrEvidence("Paddle","commence.",box,.99f),spelling).words.single()
  assertEquals(source.box,result.box)
 }
}
