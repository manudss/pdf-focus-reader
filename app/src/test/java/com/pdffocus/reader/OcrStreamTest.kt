package com.pdffocus.reader
import android.content.Context
import android.view.View
import org.robolectric.RuntimeEnvironment
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
@RunWith(RobolectricTestRunner::class)
class OcrStreamTest {
 private fun view():ContinuousReaderView {val v=ContinuousReaderView(RuntimeEnvironment.getApplication());v.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY));v.layout(0,0,400,500);return v}
 private fun plan(n:Int)=(1..n).map{OcrChunk(it,1,0,Box(0f,(it-1f)/n,1f,it.toFloat()/n),Kind.TEXT)}
 private fun texts(n:Int)=(1..n).associateWith{"Ligne stable du morceau $it. ".repeat(18)}
 private fun items(v:ContinuousReaderView):List<ContinuousReaderView.TextItem>{val f=ContinuousReaderView::class.java.getDeclaredField("textItems");f.isAccessible=true;@Suppress("UNCHECKED_CAST")return f.get(v)as List<ContinuousReaderView.TextItem>}
 @Test fun appendKeepsFirstChunkAndPixelOffset(){val v=view();val p=plan(3);v.showOcrStream(p.take(1),texts(1),true);val first=items(v).first().layout;v.showOcrStream(p,texts(3),false);assertEquals(0,v.currentPosition());assertEquals(first.text.toString(),items(v).first().layout.text.toString());assertEquals(3,items(v).size)}
 @Test fun exposedTextNeverChangesWithoutExplicitSwitch(){val v=view();val p=plan(2);v.showOcrStream(p,texts(2),true);v.showOcrStream(p,texts(2)+mapOf(1 to "Une correction différente."),false);assertEquals(texts(2)[1],items(v).first().layout.text.toString());v.showOcrStream(p,texts(2)+mapOf(1 to "Une correction différente."),false,true);assertEquals("Une correction différente.",items(v).first().layout.text.toString())}
 @Test fun appendWhileScrolledKeepsPixelAnchor(){val v=view();val p=plan(4);v.showOcrStream(p.take(2),texts(2),true);val move=ContinuousReaderView::class.java.getDeclaredMethod("move",Float::class.javaPrimitiveType).apply{isAccessible=true};move.invoke(v,350f);val before=v.currentPosition();v.showOcrStream(p,texts(4),false);assertEquals(before,v.currentPosition());assertEquals(texts(4)[1],items(v).first().layout.text.toString())}
}
