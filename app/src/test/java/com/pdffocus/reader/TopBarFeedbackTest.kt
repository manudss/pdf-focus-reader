package com.pdffocus.reader
import android.content.Intent
import android.widget.Button
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class TopBarFeedbackTest {
 @Test fun folderButtonOpensSystemPdfPicker(){
  val controller=Robolectric.buildActivity(MainActivity::class.java).setup().visible()
  val a=controller.get()
  val b=MainActivity::class.java.getDeclaredField("changePdfButton").apply{isAccessible=true}.get(a)as Button
  assertEquals("Changer de PDF",b.contentDescription)
  b.performClick()
  val request=shadowOf(a).nextStartedActivityForResult
  assertEquals(Intent.ACTION_OPEN_DOCUMENT,request.intent.action)
  assertEquals("application/pdf",request.intent.type)
  assertTrue(request.intent.categories.contains(Intent.CATEGORY_OPENABLE))
  assertEquals(100,request.requestCode)
  controller.pause().stop().destroy()
 }
}
