package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class PageReadingModeTest{
 private fun panel(id:Int,l:Float,t:Float,r:Float,b:Float)=Zone(id,Box(l,t,r,b),Kind.IMAGE,panel=true)
 private val six=listOf(panel(1,.05f,.1f,.31f,.35f),panel(2,.33f,.1f,.95f,.35f),panel(3,.05f,.37f,.66f,.62f),panel(4,.68f,.37f,.95f,.62f),panel(5,.05f,.64f,.48f,.9f),panel(6,.5f,.64f,.95f,.9f))
 @Test fun comicRowsUseLeftToRightBeforeNextRow(){assertEquals(listOf(1,2,3,4,5,6),PageReadingOrder.propose(six.shuffled(java.util.Random(13)),PageMode.COMIC))}
 @Test fun autoRecognizesPanelsButNotAPhotoMajority(){assertEquals(PageProfile.COMIC,PageReadingOrder.profile(six));assertEquals(listOf(1,2,3,4,5,6),PageReadingOrder.propose(six));assertEquals(PageProfile.ILLUSTRATED,PageReadingOrder.profile(six.map{it.copy(panel=false)}))}
 @Test fun mixedTitleIsRetainedBeforeCases(){val z=Zone(7,Box(.04f,.02f,.96f,.07f),Kind.TEXT);assertEquals(listOf(7,1,2,3,4,5,6),PageReadingOrder.propose(six+z))}
 @Test fun tinyComicInsetDoesNotTurnArticleIntoComic(){val inset=six.map{it.copy(box=Box(it.box.left*.3f,it.box.top*.3f,it.box.right*.3f,it.box.bottom*.3f))};val body=Zone(8,Box(.4f,.05f,.95f,.95f),Kind.TEXT);assertNotEquals(PageProfile.COMIC,PageReadingOrder.profile(inset+body));assertEquals(AutoOrder.propose(inset+body),PageReadingOrder.propose(inset+body))}
 @Test fun mangaReversesWithinRowsWithoutReversingTopAndBottom(){assertEquals(listOf(2,1,4,3,6,5),PageReadingOrder.propose(six,PageMode.COMIC,ReadingDirection.RIGHT_TO_LEFT))}
 @Test fun manualProposesNothingAndMangaIsExplicit(){assertTrue(PageReadingOrder.propose(six,PageMode.MANUAL).isEmpty());assertEquals(listOf(2,1,4,3,6,5),PageReadingOrder.propose(six,PageMode.MANGA))}
 @Test fun journalModeKeepsExistingColumnPolicy(){assertEquals(AutoOrder.propose(six),PageReadingOrder.propose(six,PageMode.JOURNAL))}
 @Test fun overlappingOrNestedLayoutsNeverLoseBlocks(){val z=six+panel(8,.12f,.13f,.25f,.25f);val order=PageReadingOrder.propose(z,PageMode.COMIC);assertEquals(z.map{it.id}.toSet(),order.toSet());assertEquals(z.size,order.size)}
 @Test fun slightVerticalSkewStillReadsTheSameRow(){val z=six.map{if(it.id==2)it.copy(box=it.box.copy(top=.105f))else it};assertEquals((1..6).toList(),PageReadingOrder.propose(z,PageMode.COMIC))}
 @Test fun fullHeightPanelBeforeStackToItsRight(){val z=listOf(panel(1,.05f,.1f,.4f,.9f),panel(2,.42f,.1f,.95f,.45f),panel(3,.42f,.47f,.95f,.9f));assertEquals(listOf(1,2,3),PageReadingOrder.propose(z,PageMode.COMIC))}
}
