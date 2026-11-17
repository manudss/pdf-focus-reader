package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class ArticleProposalTest{
 @Test fun cautiousWithoutHeadingAndNoMerging(){val z=listOf(Zone(1,Box(.1f,.1f,.3f,.3f),Kind.TEXT),Zone(2,Box(.1f,.31f,.3f,.5f),Kind.TEXT),Zone(3,Box(.6f,.1f,.9f,.6f),Kind.TEXT),Zone(4,Box(.1f,.51f,.3f,.7f),Kind.IMAGE));assertEquals(listOf(1,2),ArticleProposal.propose(z,2,emptyList()).ids);assertEquals(4,z.size)}
 @Test fun appendNeverReordersOrDuplicatesExisting(){assertEquals(listOf(5,2,9,1,3),ArticleProposal.append(listOf(5,2,9),listOf(1,2,3,1)))}
 @Test fun largeTitleGroupsItsColumnsButStopsAtNextTitle(){val z=listOf(Zone(1,Box(.1f,.1f,.9f,.2f),Kind.TEXT),Zone(2,Box(.1f,.21f,.45f,.5f),Kind.TEXT),Zone(3,Box(.55f,.21f,.9f,.5f),Kind.TEXT),Zone(4,Box(.1f,.6f,.9f,.7f),Kind.TEXT),Zone(5,Box(.1f,.72f,.45f,.9f),Kind.TEXT));val words=(0..10).map{OcrWord("mot",Box(.15f,.23f+it*.015f,.2f,.24f+it*.015f),Box(0f,0f,1f,1f))}+listOf(OcrWord("Titre",Box(.15f,.11f,.7f,.17f),Box(0f,0f,1f,1f)),OcrWord("Autre",Box(.15f,.61f,.7f,.67f),Box(0f,0f,1f,1f)));assertEquals(listOf(1,2,3),ArticleProposal.propose(z,2,words).ids)}
}
