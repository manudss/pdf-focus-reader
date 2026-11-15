package com.pdffocus.reader
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
class GeometryTest {
    @Test fun userOrderIsExactAndDeduplicated(){val a=Zone(1,Box(0f,0f,.2f,.8f),Kind.TEXT);val b=a.copy(id=2);assertEquals(listOf(2,1),ReadingLayout.ordered(listOf(a,b),listOf(2,1,2,99)).map{it.id})}
    @Test fun removingSelectionRenumbersWithoutReordering(){assertEquals(listOf(3,2),ReadingLayout.toggle(listOf(3,1,2),1));assertEquals(listOf(3,2,1),ReadingLayout.toggle(listOf(3,2),1))}
    @Test fun imageStaysOneUndistortedRegion(){val z=Zone(8,Box(.1f,.1f,.8f,.9f),Kind.IMAGE);val f=ReadingLayout.frames(z,1000,1500,400,600);assertEquals(1,f.size);assertEquals(z.box,f[0].box)}
    @Test fun narrowTextGetsWidthFitted(){val z=Zone(1,Box(.1f,.1f,.3f,.2f),Kind.TEXT);assertEquals(1,ReadingLayout.frames(z,1000,1500,400,700).size)}
    @Test fun allTextPixelsAreCoveredWithOverlap(){val r=Random(42);repeat(300){val l=r.nextFloat()*.5f;val top=r.nextFloat()*.2f;val z=Zone(1,Box(l,top,l+.05f+r.nextFloat()*.4f,.9f),Kind.TEXT);val f=ReadingLayout.frames(z,1000,1500,320+r.nextInt(400),320+r.nextInt(1000));assertFalse(f.isEmpty());assertEquals(z.box.top,f.first().box.top);assertEquals(z.box.bottom,f.last().box.bottom);f.zipWithNext().forEach{(a,b)->assertTrue(b.box.top<=a.box.bottom);assertTrue(b.box.top>a.box.top)};f.forEach{assertTrue(it.box.height>0);assertEquals(z.box.left,it.box.left)}}}
    @Test fun lineSnapDoesNotLoseLines(){val z=Zone(1,Box(.1f,.1f,.3f,.9f),Kind.TEXT,(1..80).map{it/100f});val f=ReadingLayout.frames(z,1000,1500,400,500);f.zipWithNext().forEach{(a,b)->assertTrue(b.box.top<a.box.bottom)};assertEquals(.9f,f.last().box.bottom)}
    @Test fun hitTestingPrefersSmallContainedBlock(){val big=Zone(1,Box(0f,0f,1f,1f),Kind.IMAGE);val small=Zone(2,Box(.2f,.2f,.3f,.3f),Kind.TEXT);assertEquals(2,ReadingLayout.smallestHit(listOf(big,small),.25f,.25f)?.id)}
    @Test fun badDimensionsFailSafely(){try{ReadingLayout.frames(Zone(1,Box(0f,0f,1f,1f),Kind.TEXT),0,100,100,100);fail()}catch(_:IllegalArgumentException){}}
    @Test fun rotatedPageAspectStillCoversColumn(){val z=Zone(1,Box(.1f,.1f,.3f,.9f),Kind.TEXT);for((w,h)in listOf(1000 to 1500,1500 to 1000)){val f=ReadingLayout.frames(z,w,h,400,700);assertEquals(z.box.bottom,f.last().box.bottom);assertEquals(z.box.top,f.first().box.top)}}
}
