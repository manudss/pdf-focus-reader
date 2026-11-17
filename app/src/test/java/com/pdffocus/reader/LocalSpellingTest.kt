package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class LocalSpellingTest {
 private val spell=LocalSpelling(listOf("lumière","commence","cinq","lecture","culte","cuite","été","Grenoble","Paris"))
 @Test fun accentMissingLetterConfusionAndSwapAreCandidatesOnly(){assertTrue("lumière" in spell.suggestions("lumiére"));assertTrue("commence" in spell.suggestions("comence"));assertTrue("cinq" in spell.suggestions("cing"));assertTrue("lecture" in spell.suggestions("lectrue"))}
 @Test fun protectsKnownWordsNamesNumbersAndShortWords(){for(s in listOf("culte","Paris","Gren0ble","50 000","été","l'été"))assertTrue(spell.suggestions(s).isEmpty())}
 @Test fun boundedSuggestionsAndNoForcedChoice(){assertTrue(spell.suggestions("inconnaissable").isEmpty());assertEquals(0,spell.suggestions("cing",0).size);assertEquals(listOf("cinq"),spell.suggestions("cing"))}
}
