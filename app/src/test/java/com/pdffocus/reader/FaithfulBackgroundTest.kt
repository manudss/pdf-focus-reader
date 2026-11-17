package com.pdffocus.reader
import org.junit.Assert.*
import org.junit.Test
class FaithfulBackgroundTest {
 @Test fun subtleTintedPaperDoesNotBecomeAWhiteRectangleInCleanedMode(){
  for(color in listOf(0xfff7f1df.toInt(),0xffdce8dc.toInt(),0xffdedcec.toInt(),0xff214c3b.toInt())){
   val pixels=IntArray(144){color};assertArrayEquals(pixels,TextCleaner.filter(pixels,12,12,true))
  }
 }
}
