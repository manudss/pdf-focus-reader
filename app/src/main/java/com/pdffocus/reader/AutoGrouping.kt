package com.pdffocus.reader
import kotlin.math.*
/** Suggestions only. Applying a subset is explicit and does not select new reading content. */
object AutoGrouping {
 data class Group(val ids:Set<Int>,val box:Box,val reason:String)
 data class Result(val zones:List<Zone>,val order:List<Int>)
 fun propose(zones:List<Zone>):List<Group>{
  val groups=zones.map{setOf(it.id)}.toMutableList()
  fun members(ids:Set<Int>)=zones.filter{it.id in ids}
  fun box(ids:Set<Int>)=members(ids).map{it.box}.reduce{a,b->a.union(b)}
  var changed=true
  while(changed){
   changed=false
   loop@for(i in groups.indices)for(j in i+1 until groups.size){
    val aa=members(groups[i]);val bb=members(groups[j]);if((aa+bb).any{it.manual})continue
    val a=box(groups[i]);val b=box(groups[j]);val overlap=a.intersect(b)
    val embedded=overlap>=min(a.area,b.area)*.92f
    val mixedOverlap=overlap>=min(a.area,b.area)*.12f&&(aa+bb).any{it.kind==Kind.IMAGE}
    val gap=max(a.top,b.top)-min(a.bottom,b.bottom)
    val short=min(a.height,b.height)<.075f
    val aligned=abs(a.left-b.left)<.075f&&(min(a.right,b.right)-max(a.left,b.left))>.65f*min(a.width,b.width)&&min(a.width,b.width)/max(a.width,b.width)>.07f
    val heading=aa.all{it.kind==Kind.TEXT}&&bb.all{it.kind==Kind.TEXT}&&short&&aligned&&gap in -.035f.. .035f
    if(!embedded&&!mixedOverlap&&!heading)continue
    val merged=a.union(b);val ids=groups[i]+groups[j]
    val texts=(aa+bb).filter{it.kind==Kind.TEXT}
    if(!embedded&&!mixedOverlap&&texts.indices.any{u->(u+1 until texts.size).any{v->val x=texts[u].box;val y=texts[v].box;(x.right<=y.left||y.right<=x.left)&&(min(x.bottom,y.bottom)-max(x.top,y.top))>.2f*min(x.height,y.height)}})continue
    // A candidate must not swallow any separate column, case or other block.
    if(zones.any{it.id !in ids&&merged.intersect(it.box)>.08f*it.box.area&&a.intersect(it.box)<.08f*it.box.area&&b.intersect(it.box)<.08f*it.box.area})continue
    groups[i]=ids;groups.removeAt(j);changed=true;break@loop
   }
  }
  return groups.filter{it.size>1}.map{ids->val bs=members(ids);Group(ids,box(ids),if(bs.any{a->bs.any{b->a.id!=b.id&&a.box.intersect(b.box)>.5f*min(a.box.area,b.box.area)}})"Blocs imbriqués ou superposés" else "Titre et texte de la même colonne")}
 }
 fun apply(zones:List<Zone>,order:List<Int>,groups:List<Group>):Result{
  var output=zones;var ids=order
  for(g in groups){
   val chosen=output.filter{it.id in g.ids};if(chosen.size<2)continue
   val first=ids.indexOfFirst{it in g.ids};val remaining=ids.filterNot{it in g.ids}
   val merged=Zone((output.maxOfOrNull{it.id}?:0)+1,chosen.map{it.box}.reduce{a,b->a.union(b)},if(chosen.any{it.kind==Kind.IMAGE})Kind.IMAGE else Kind.TEXT,chosen.flatMap{it.lineBottoms}.distinct().sorted(),true)
   output=output.filterNot{it.id in g.ids}+merged
   ids=if(first<0)remaining else remaining.toMutableList().apply{add(first.coerceAtMost(size),merged.id)}
  }
  return Result(output,ids)
 }
}
