package com.pdffocus.reader
import kotlin.math.*
/** A cautious geometric candidate, never an assertion that blocks form an article. */
object ArticleProposal{
 data class Proposal(val ids:List<Int>,val reason:String)
 fun propose(zones:List<Zone>,seedId:Int,words:List<OcrWord>):Proposal{
  val seed=zones.first{it.id==seedId};val sizes=words.map{it.box.height}.filter{it>0}.sorted();val median=sizes.getOrNull(sizes.size/2)?:.012f
  fun large(z:Zone):Boolean{val ws=words.filter{z.box.contains((it.box.left+it.box.right)/2,(it.box.top+it.box.bottom)/2)};return ws.isNotEmpty()&&ws.count{it.box.height>median*1.5f}>=maxOf(1,ws.size/2)}
  val headings=zones.filter{it.kind==Kind.TEXT&&large(it)}
  val heading=headings.filter{it.box.top<=seed.box.top+.01f&&it.box.bottom>=seed.box.top-.28f&&it.box.left<=seed.box.left+.04f&&it.box.right>=seed.box.right-.04f}.maxByOrNull{it.box.top}
  val result=if(heading!=null){
   val stop=headings.filter{it.id!=heading.id&&it.box.top>heading.box.bottom&&it.box.left<heading.box.right&&it.box.right>heading.box.left}.minOfOrNull{it.box.top}?:1f
   zones.filter{z->z.id==heading.id||z.id==seedId||(z.kind==Kind.TEXT&&z.box.left>=heading.box.left-.025f&&z.box.right<=heading.box.right+.025f&&z.box.top>=heading.box.bottom-.01f&&z.box.bottom<=stop+.005f)}
  }else{
   val chosen=mutableListOf(seed);var changed=true
   while(changed){changed=false;for(z in zones){if(z in chosen||z.kind!=Kind.TEXT||z in headings)continue
    if(chosen.any{a->abs(a.box.left-z.box.left)<.025f&&abs(a.box.right-z.box.right)<.025f&&max(a.box.top,z.box.top)-min(a.box.bottom,z.box.bottom)<.025f}){chosen+=z;changed=true}
   }};chosen
  }
  // Heading first, then columns left-to-right and blocks top-to-bottom. Always previewable.
  val rest=result.filter{it.id!=heading?.id}.sortedBy{it.box.left};val columns=mutableListOf<MutableList<Zone>>()
  for(z in rest){val column=columns.firstOrNull{abs(it.first().box.left-z.box.left)<.04f};if(column==null)columns+=mutableListOf(z)else column+=z}
  val ids=(listOfNotNull(heading?.takeIf{it in result})+columns.flatMap{it.sortedBy{z->z.box.top}}).map{it.id}.distinct()
  return Proposal(ids,if(heading!=null)"Titre et colonnes proches proposés. Vérifiez les encarts et légendes." else  "Titre non repéré : proposition limitée aux blocs alignés proches. Ajoutez les blocs manquants.")
 }
 fun append(existing:List<Int>,candidate:List<Int>)=existing+candidate.distinct().filter{it !in existing}
}
