package com.pdffocus.reader

data class OcrWord(val text:String,val box:Box,val line:Box,val confidence:Float=100f)
/** Selection order wins. A recognized word can belong to only one selected block. */
object OcrFlow {
 fun textByZone(zones:List<Zone>,words:List<OcrWord>):Map<Int,String>{
  val owned=mutableMapOf<Int,MutableList<OcrWord>>()
  for(word in words){val owner=zones.firstOrNull{it.box.contains((word.box.left+word.box.right)/2,(word.box.top+word.box.bottom)/2)||it.box.intersect(word.box)>=word.box.area*.5f}?:continue
   if(owner.kind==Kind.TEXT)owned.getOrPut(owner.id){mutableListOf()}.add(word)
  }
  return zones.filter{it.kind==Kind.TEXT}.associate{zone->
   val lines=owned[zone.id].orEmpty().distinctBy{it.box to it.text}.groupBy{it.line}.entries.sortedWith(compareBy({it.key.top},{it.key.left}))
   val out=StringBuilder();var previous:Box?=null
   for((line,items)in lines){if(out.isNotEmpty())out.append(if(previous!=null&&line.top-previous!!.bottom>previous!!.height*.8f)"\n\n" else " ");out.append(items.sortedBy{it.box.left}.joinToString(" "){it.text});previous=line}
   zone.id to out.toString()
  }
 }
}
