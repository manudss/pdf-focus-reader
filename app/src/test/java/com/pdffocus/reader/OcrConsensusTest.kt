package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class OcrConsensusTest {
 private val b=Box(0f,0f,1f,.02f)
 private val spell=LocalSpelling(listOf("lumière","commence","culte","cuite","cinq"))
 private fun word(s:String,c:Float=75f)=OcrWord(s,b,b,c)
 private fun e(s:String,c:Float=.99f)=OcrEvidence("Second",s,b,c)
 @Test fun onlySecondReadingAndSpellingTogetherCanRepair(){assertEquals("commence",OcrConsensus.reconcile(listOf(word("comence")),e("commence"),spell).single().text);assertEquals("lumière",OcrConsensus.reconcile(listOf(word("lumiére")),e("lumière"),spell).single().text)}
 @Test fun validWordsNamesNumbersHighConfidenceAndTokenCountAreProtected(){for((a,z)in listOf("culte" to "cuite","Rémi" to "Remi","50" to "60","comence" to "commence ici"))assertEquals(a,OcrConsensus.reconcile(listOf(word(a)),e(z),spell).single().text);assertEquals("comence",OcrConsensus.reconcile(listOf(word("comence",98f)),e("commence"),spell).single().text);assertEquals("comence",OcrConsensus.reconcile(listOf(word("comence")),e("commence",.4f),spell).single().text)}
}
