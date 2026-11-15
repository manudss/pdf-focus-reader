package com.pdffocus.reader
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PageSession(val zones:List<Zone>,val order:List<Int>,val frame:Int=0)
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
        val p=root.getJSONObject(index.toString());val a=p.getJSONArray("zones");val zones=(0 until a.length()).map{i->val z=a.getJSONObject(i);Zone(z.getInt("id"),Box(z.getDouble("l").toFloat(),z.getDouble("t").toFloat(),z.getDouble("r").toFloat(),z.getDouble("b").toFloat()),Kind.valueOf(z.getString("kind")))}
        val o=p.getJSONArray("order");PageSession(zones,(0 until o.length()).map{o.getInt(it)},p.optInt("frame"))
    }catch(_:Exception){null}
    fun put(index:Int,session:PageSession){
        val zones=JSONArray();session.zones.forEach{z->zones.put(JSONObject().put("id",z.id).put("l",z.box.left).put("t",z.box.top).put("r",z.box.right).put("b",z.box.bottom).put("kind",z.kind.name))}
        root.put(index.toString(),JSONObject().put("zones",zones).put("order",JSONArray(session.order)).put("frame",session.frame));root.put("page",index);flush()
    }
    fun clear(){root=JSONObject();file.delete()}
    private fun flush(){val temp=File(file.parentFile,"session.tmp");temp.writeText(root.toString());check(temp.renameTo(file)){"Sauvegarde de la lecture impossible"}}
}
