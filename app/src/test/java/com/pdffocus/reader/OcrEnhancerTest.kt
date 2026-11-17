package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class OcrEnhancerTest {
 private val repair=OcrEnhancer(listOf("notamment","télévisés","Saint-Denis","lumière","cinq","fois","en","parti","culte","cuite","été","organisée","voiture","extérieur","commence"))
 private fun w(t:String,y:Float=0f,x:Float=0f)=OcrWord(t,Box(x,y,x+.15f,y+.02f),Box(0f,y,.9f,y+.02f),96f)
 @Test fun joinsOnlyAttestedLineEndWords(){assertEquals("notamment",repair.join("notam-","ment"));assertEquals("Saint-Denis",repair.join("Saint-","Denis"));assertNull(repair.join("Papa-","galli"));assertNull(repair.join("non","mot"));assertNull(repair.join("17-","18"))}
 @Test fun preservesParagraphBoundariesAndRealHyphens(){assertEquals("notamment",repair.text(listOf(w("notam-"),w("ment",.025f))));assertEquals("notam-\n\nment",repair.text(listOf(w("notam-"),w("ment",.1f))));assertEquals("Saint-Denis",repair.text(listOf(w("Saint-"),w("Denis",.025f))))}
 @Test fun neverReplacesValidWordsOrNumbersOrProperNames(){for((a,b)in listOf("culte" to "cuite","17" to "18","Papagalli" to "parti","partic" to "parti")){assertEquals(a,repair.improve(listOf(w(a)),listOf(w(b))).single().text)}}
 @Test fun acceptsSupportedLettersAndAccents(){assertEquals("lumière.",repair.improve(listOf(w("lumiére.")),listOf(w("lumière."))).single().text);assertEquals("cinq",repair.improve(listOf(w("cing")),listOf(w("cinq"))).single().text);assertEquals("ét",repair.improve(listOf(w("ét")),listOf(w("été"))).single().text)}
 @Test fun supportsMissingLetterWhenSecondReadingAndLexiconAgree(){assertEquals("commence",repair.improve(listOf(w("comence")),listOf(w("commence"))).single().text)}
 @Test fun lowConfidenceCandidateDoesNotRewrite(){assertEquals("cing",repair.improve(listOf(w("cing")),listOf(w("cinq").copy(confidence=40f))).single().text)}
 @Test fun wordsRemainOwnedOnceAndCutsDoNotRepeat(){val box=Box(0f,0f,1f,1f);val words=(0..19).map{w("mot",it*.03f)};val plan=OcrChunks.plan(listOf(Zone(1,box,Kind.TEXT),Zone(2,box,Kind.TEXT)),words){listOf(it.box)};assertEquals(words,plan.flatMap{it.words});assertEquals(20,plan.sumOf{it.words.size});assertTrue(plan.filter{it.zoneId==1}.size>1)}
 @Test fun chunkBoundaryKeepsHyphenatedWordTogether(){val words=(0..12).map{w(if(it==5)"notam-" else  if(it==6)"ment" else "mot",it*.03f)};val plan=OcrChunks.plan(listOf(Zone(1,Box(0f,0f,1f,1f),Kind.TEXT)),words){listOf(it.box)};assertEquals(7,plan.first().words.size);assertEquals(words,plan.flatMap{it.words})}
}
