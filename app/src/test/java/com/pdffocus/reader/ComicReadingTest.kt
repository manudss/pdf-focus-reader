package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class ComicReadingTest {
 @Test fun wholeCaseFitsBothAxesWithoutStretch(){assertEquals(.4f,ComicReading.fit(1000f,500f,400f,600f),.0001f);assertEquals(.2f,ComicReading.fit(500f,3000f,400f,600f),.0001f)}
 @Test fun panoramaDoesNotShrinkCommonScale(){assertEquals(.8f,ComicReading.commonScale(listOf(1000f,500f,500f),400f),.0001f)}
 @Test fun panIncludesBothEdgesAndMangaStartsOnRight(){assertEquals(600f,ComicReading.pan(Float.NaN,1000f,1f,400f,true),.001f);assertEquals(0f,ComicReading.pan(Float.NaN,1000f,1f,400f,false),.001f);assertEquals(0f,ComicReading.pan(-900f,1000f,1f,400f,true),.001f);assertEquals(600f,ComicReading.pan(900f,1000f,1f,400f,false),.001f)}
 @Test fun touchSlopAndDiagonalsPreferPageScroll(){assertEquals(0,ComicReading.axis(2f,3f,8f));assertEquals(1,ComicReading.axis(40f,5f,8f));assertEquals(2,ComicReading.axis(30f,30f,8f));assertEquals(2,ComicReading.axis(5f,40f,8f))}
}
