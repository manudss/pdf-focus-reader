package com.pdffocus.reader
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PageSession(val zones:List<Zone>,val order:List<Int>,val frame:Int=0,val scroll:Int=0)
/** Last document only. No source text, titles or thumbnails in saved sessions. */
class SessionStore(context:Context) {
    private val file=File(context.filesDir,"session.json")
    private var root=try{JSONObject(file.readText())}catch(_:Exception){JSONObject()}
    var key:String
        get()=root.optString("key")
        set(value){if(value!=key){root=JSONObject().put("key",value);flush()}}
    var page:Int
        get()=root.optInt("page",0)
        set(value){root.put("page",value);flush()}
    fun get(index:Int):PageSession?=try{
        val p=root.getJSONObject(index.toString());val a=p.getJSONArray("zones");val zones=(0 until a.length()).map{i->val z=a.getJSONObject(i);Zone(z.getInt("id"),Box(z.getDouble("l").toFloat(),z.getDouble("t").toFloat(),z.getDouble("r").toFloat(),z.getDouble("b").toFloat()),Kind.valueOf(z.getString("kind")),z.optJSONArray("lines")?.let{ls->(0 until ls.length()).map{ls.getDouble(it).toFloat()}}?:emptyList(),z.optBoolean("manual",false))}
        val o=p.getJSONArray("order");PageSession(zones,(0 until o.length()).map{o.getInt(it)},p.optInt("frame"),p.optInt("scroll"))
    }catch(_:Exception){null}
    fun put(index:Int,session:PageSession){
        val zones=JSONArray();session.zones.forEach{z->zones.put(JSONObject().put("id",z.id).put("l",z.box.left).put("t",z.box.top).put("r",z.box.right).put("b",z.box.bottom).put("kind",z.kind.name).put("lines",JSONArray(z.lineBottoms)).put("manual",z.manual))}
        root.put(index.toString(),JSONObject().put("zones",zones).put("order",JSONArray(session.order)).put("frame",session.frame).put("scroll",session.scroll));root.put("page",index);flush()
    }
    /** Only fingerprints and numeric choices are persisted, never recognized article text. */
    fun ocrChoices(index:Int,fingerprint:String):Set<Int>{val values=root.optJSONObject("ocrChoices")?.optJSONObject(index.toString())?.optJSONArray(fingerprint)?:return emptySet();return (0 until values.length()).map{values.getInt(it)}.toSet()}
    fun putOcrChoice(index:Int,fingerprint:String,id:Int,keep:Boolean){
        require(fingerprint.matches(Regex("[a-f0-9]{64}")))
        val all=root.optJSONObject("ocrChoices")?:JSONObject().also{root.put("ocrChoices",it)}
        val page=all.optJSONObject(index.toString())?:JSONObject().also{all.put(index.toString(),it)}
        val choices=ocrChoices(index,fingerprint);page.put(fingerprint,JSONArray((if(keep)choices+id else choices-id).sorted()));flush()
    }
    fun clear(){root=JSONObject();file.delete()}
    private fun flush(){val temp=File(file.parentFile,"session.tmp");temp.writeText(root.toString());check(temp.renameTo(file)){"Sauvegarde de la lecture impossible"}}
}
