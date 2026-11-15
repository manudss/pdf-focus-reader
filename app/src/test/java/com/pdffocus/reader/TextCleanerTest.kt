package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class TextCleanerTest {
 @Test fun removesLightNoiseButKeepsADotAndAnOpenLetter(){
  val pixels=IntArray(25*25){-1};pixels[0]=0xffdddddd.toInt()
  for(y in 3..7)for(x in 3..7)pixels[y*25+x]=0xff000000.toInt()
  for(y in 10..21)for(x in 10..21)if(x<13||x>18||y<13||y>18)pixels[y*25+x]=0xff000000.toInt()
  val copy=pixels.copyOf();val normal=TextCleaner.filter(pixels,25,25,false);val thick=TextCleaner.filter(pixels,25,25,true)
  assertArrayEquals(copy,pixels);assertEquals(-1,normal[0]);assertEquals(0xff000000.toInt(),normal[5*25+5]);assertEquals(-1,thick[15*25+15])
  assertTrue(thick.indices.all{(thick[it] and 255)<=(normal[it] and 255)})
 }
 @Test fun leavesColoredTextUnchanged(){val p=IntArray(9){0xffc92318.toInt()};assertArrayEquals(p,TextCleaner.filter(p,3,3,true))}
}
