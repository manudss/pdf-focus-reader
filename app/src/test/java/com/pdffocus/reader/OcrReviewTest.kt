package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class OcrReviewTest {
 private val box=Box(0f,0f,1f,1f)
 @Test fun retainsBothReadingsAndReversibleChoice(){val r=OcrReview.compare("Un mot corrlgé ici.","Un mot corrigé ici.",box);val e=r.edits.single();assertEquals("corrlgé",e.raw.trim());assertEquals("corrigé",e.corrected.trim());assertEquals("Un mot corrlgé ici.",r.chooseRaw(e.id,true).rendered().first);assertEquals(r.corrected,r.chooseRaw(e.id,true).chooseRaw(e.id,false).rendered().first)}
 @Test fun manyToOneAndSeveralChangesKeepOffsets(){val r=OcrReview.compare("notam- ment puis comence.","notamment puis commence.",box);assertEquals(2,r.edits.size);assertEquals("notam- ment",r.edits.first().raw.trim());val s=r.chooseRaw(0,true).rendered();val mark=s.second.last();assertEquals("commence.",s.first.substring(mark.start,mark.end));assertEquals("notam- ment puis commence.",s.first)}
 @Test fun whitespaceKeepsParagraphsAndNumberGroups(){assertEquals("50 000 logements sont ici.\n\nAutre paragraphe.",OcrReview.whitespace(" 50   000 logements\n sont ici.\n \n Autre\tparagraphe. "));assertEquals("3,14 ; l'espoir",OcrReview.whitespace("3,14  ;  l'espoir"))}
 @Test fun unchangedTextHasNoMarks(){assertTrue(OcrReview.compare("50 000 Paris", "50 000 Paris",box).edits.isEmpty())}
 @Test fun deletionAndInsertionAreNotLost(){val d=OcrReview.compare("un faux mot", "un mot",box);assertEquals("faux",d.edits.single().raw.trim());val i=OcrReview.compare("un mot", "un petit mot",box);assertEquals("",i.edits.single().raw.trim());assertEquals("petit",i.edits.single().corrected.trim())}
 @Test fun arbitraryEditsRoundTrip(){for((a,b)in listOf("un faux mot" to "un mot","un mot faux" to "un mot","mot" to "un mot","un mot" to "un petit mot","un mot" to "un mot final","" to "mot","mot" to "")){var r=OcrReview.compare(a,b,box);for(e in r.edits)r=r.chooseRaw(e.id,true);assertEquals(a,r.rendered().first)}}
 @Test fun exceptionallyLongInputUsesOneBoundedReversibleEdit(){val raw="mot ".repeat(400);val r=OcrReview.compare(raw,raw+"fin",box);assertEquals(1,r.edits.size);assertEquals(raw,r.chooseRaw(0,true).rendered().first)}
}
