package com.pdffocus.reader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28])
class FrenchSpellingTest {
 @Test fun independentDictionaryChecksInflectionsAccentsAndProducesSuggestions(){
  val spell=FrenchSpelling(RuntimeEnvironment.getApplication())
  for(w in listOf("musiciens","mangeaient","étant","l'intimidation","porte-parole"))assertTrue(w,spell.valid(w))
  assertFalse(spell.valid("comence"));assertFalse(spell.valid("etant"));assertFalse(spell.valid("Jour"))
  assertTrue(spell.suggestions("comence",8).contains("commence"));assertTrue(spell.suggestions("etant",8).contains("étant"))
 }
}
