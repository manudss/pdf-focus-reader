package com.pdffocus.reader
import org.junit.Assert.*
import org.junit.Test
class OcrReviewLongTest {
 private val box=Box(0f,0f,1f,1f)
 @Test fun longBlockRetainsLocalMarkersAndExactRawChoices(){
  val raw=(0..750).joinToString(" "){"mot$it"};val corrected=raw.replace("mot400 ","motcorrigé ")
  val review=OcrReview.compare(raw,corrected,box)
  assertEquals(1,review.edits.size);assertTrue(review.edits.single().corrected.length<30)
  assertEquals(raw,review.chooseRaw(0,true).rendered().first)
 }
 @Test fun longRepeatedBlockStaysBoundedAndReversible(){
  val raw=List(500){"un mot"}.joinToString(" ");val fixed=raw+" final"
  val review=OcrReview.compare(raw,fixed,box);assertTrue(review.edits.isNotEmpty());assertEquals(raw,review.chooseRaw(0,true).rendered().first)
 }
}
