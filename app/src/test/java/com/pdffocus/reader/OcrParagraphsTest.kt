package com.pdffocus.reader
import org.junit.Test
import org.junit.Assert.*
class OcrParagraphsTest {
 private val repair=OcrEnhancer(listOf("brutalités","intimidation","gouvernemental","Saint-Denis","Rémi","Vaucanson","notamment"))
 private fun line(s:String,y:Float,left:Float=.1f,right:Float=.9f):List<OcrWord>{val ts=s.split(' ');val step=(right-left)/ts.size;return ts.mapIndexed{i,t->OcrWord(t,Box(left+i*step,y,left+(i+1)*step-.002f,y+.02f),Box(left,y,right,y+.02f))}}
 @Test fun wrapsAreNotParagraphsAndCutWordsJoin(){val words=line("des bru-",0f)+line("talités et inti-",.029f)+line("midation du gouver-",.058f)+line("nemental",.087f);assertEquals("des brutalités et intimidation du gouvernemental",repair.text(words))}
 @Test fun indentAndExtraLeadingPreserveRealParagraphs(){assertEquals("Première phrase complète.\n\nNouveau paragraphe continu.",repair.text(line("Première phrase complète.",0f)+line("Nouveau paragraphe",.03f,.14f)+line("continu.",.06f)));assertEquals("Une phrase.\n\nUne autre.",repair.text(line("Une phrase.",0f)+line("Une autre.",.06f)))}
 @Test fun shortSourceLinesDoNotCreateDisplayBreaks(){assertEquals("leur quotidien. Pour dénoncer surtout cette situation",repair.text(line("leur quotidien. Pour",0f,.1f,.48f)+line("dénoncer surtout cette situation",.03f)));assertEquals("Lire un passage sans coupure",repair.text(line("Lire un passage",0f)+line("sans coupure",.03f)))}
 @Test fun compoundsNamesNumbersAndListsRemainProtected(){assertEquals("Saint-Denis",repair.text(line("Saint-",0f)+line("Denis",.03f)));assertEquals("Rémi Adam",repair.text(line("Ré-",0f)+line("mi Adam",.03f)));assertEquals("Val- canson",repair.text(line("Val-",0f)+line("canson",.03f)));assertEquals("50 000 habitants",repair.text(line("50 000",0f)+line("habitants",.03f)));assertEquals("Une liste\n\n• premier élément\n\n• second élément",repair.text(line("Une liste",0f)+line("• premier élément",.03f)+line("• second élément",.06f)))}
 @Test fun unknownHyphensAndParagraphBoundariesAreNeverGuessed(){
  assertEquals("porte- parole",repair.text(line("porte-",0f)+line("parole",.03f)))
  assertEquals("des bru-\n\ntalités",repair.text(line("des bru-",0f)+line("talités",.08f)))
 }
 @Test fun slightlyDifferentLineBoxesGroupAsOneLine(){val a=line("Un texte",0f);val b=line("sans doublon",.0004f,.5f,.9f);assertEquals(1,OcrParagraphs.lines(a+b).size)}
}
