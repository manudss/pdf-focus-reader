package com.pdffocus.reader
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
// Robolectric 4.15.1 supports up to SDK 35; pin these SDK-agnostic tests. API 36 behavior is
// covered by the instrumented device tests on the emulator.
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class ReadingPaletteTest {
 @Test fun paperAndInverseBackgroundsKeepReadableContrast(){
  for(color in listOf(Color.rgb(247,241,223),Color.rgb(31,76,59))){
   val b=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);b.eraseColor(color)
   for(y in 20..80 step 12)for(x in 10..90)b.setPixel(x,y,Color.BLACK)
   assertEquals(color,ReadingPalette.background(b,Box(0f,0f,1f,1f)))
   if(Color.red(color)<100)assertTrue(Color.red(ReadingPalette.foreground(color))>240)else assertTrue(Color.red(ReadingPalette.foreground(color))<60)
   b.recycle()
  }
 }
}
