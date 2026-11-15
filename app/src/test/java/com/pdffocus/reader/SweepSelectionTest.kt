package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class SweepSelectionTest {
 @Test fun fastSweepCrossesTinyBlocksInFirstEncounterOrder(){val zs=(1..5).map{Zone(it,Box(it*.1f,.2f,it*.1f+.02f,.3f),Kind.TEXT)};assertEquals((1..5).toList(),SweepSelection.crossed(zs,.02f,.25f,.7f,.25f).map{it.id});assertEquals((5 downTo 1).toList(),SweepSelection.crossed(zs,.7f,.25f,.02f,.25f).map{it.id})}
 @Test fun outsideDoesNotHit(){assertTrue(SweepSelection.crossed(listOf(Zone(1,Box(.1f,.1f,.2f,.2f),Kind.TEXT)),0f,.4f,1f,.4f).isEmpty())}
}
