package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class OcrFlowTest {
 @Test fun wordsAreAssignedOnceInSelectionOrderAndReflowed(){
  val a=Zone(1,Box(0f,0f,.8f,1f),Kind.TEXT);val b=Zone(2,Box(.4f,0f,1f,1f),Kind.TEXT);val line=Box(0f,.1f,1f,.2f)
  val words=listOf(OcrWord("été",Box(.5f,.1f,.6f,.2f),line),OcrWord("Un",Box(.1f,.1f,.2f,.2f),line),OcrWord("calme.",Box(.85f,.1f,.95f,.2f),line))
  val result=OcrFlow.textByZone(listOf(b,a),words);assertEquals("été calme.",result[2]);assertEquals("Un",result[1]);assertEquals(3,result.values.joinToString(" ").split(" ").size)
 }
 @Test fun anEarlierImageOwnsItsWordsAndEmptyBlocksStayEmpty(){val b=Box(0f,0f,1f,1f);val out=OcrFlow.textByZone(listOf(Zone(1,b,Kind.IMAGE),Zone(2,b,Kind.TEXT)),listOf(OcrWord("Légende",b,b)));assertEquals("",out[2])}
}
