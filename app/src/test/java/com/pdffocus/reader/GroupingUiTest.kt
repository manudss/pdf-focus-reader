package com.pdffocus.reader
import android.graphics.*
import android.view.*
import android.widget.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],qualifiers="fr-rFR-w393dp-h852dp-xxhdpi")
class GroupingUiTest {
 private fun set(a:MainActivity,n:String,v:Any){MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.set(a,v)}
 private fun get(a:MainActivity,n:String)=MainActivity::class.java.getDeclaredField(n).apply{isAccessible=true}.get(a)
 private fun call(a:MainActivity,n:String){MainActivity::class.java.declaredMethods.first{it.name==n}.apply{isAccessible=true}.invoke(a)}
 private fun checkBoxes(v:View):List<CheckBox>{if(v is CheckBox)return listOf(v);if(v is ViewGroup)return (0 until v.childCount).flatMap{checkBoxes(v.getChildAt(it))};return emptyList()}
 private fun canvases(v:View):List<PageCanvas>{if(v is PageCanvas)return listOf(v);if(v is ViewGroup)return (0 until v.childCount).flatMap{canvases(v.getChildAt(it))};return emptyList()}
 private val twoColumns=(0..1).flatMap{x->val left=.05f+x*.48f;listOf(Zone(1+x*2,Box(left,.1f,left+.2f,.14f),Kind.TEXT),Zone(2+x*2,Box(left,.15f,left+.4f,.5f),Kind.TEXT))}
 private fun prepare(order:List<Int>,zones:List<Zone>):MainActivity{
  val a=Robolectric.buildActivity(MainActivity::class.java).setup().visible().get()
  val image=Bitmap.createBitmap(700,1100,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.WHITE)}
  set(a,"count",1);set(a,"preview",image);set(a,"zones",zones);set(a,"order",order);set(a,"pageReady",true)
  val canvas=get(a,"canvas")as PageCanvas;canvas.image=image;canvas.zones=zones;canvas.selection=order
  call(a,"updateControls");return a
 }
 @Test fun regrouperSitsInTheBottomRowWithAjouterOrdreFusionner(){
  val a=prepare(listOf(1,2,3,4),twoColumns)
  val controls=get(a,"controls")as ViewGroup
  val firstRow=(0 until controls.childCount).map{controls.getChildAt(it)}.filterIsInstance<ViewGroup>().first()
  val labels=(0 until firstRow.childCount).map{(firstRow.getChildAt(it)as Button).text.toString()}
  assertEquals(listOf("Ajouter","Ordre","Fusionner","Regrouper"),labels)
 }
 @Test fun proposalNeedsChosenBlocksFirst(){
  val a=prepare(emptyList(),twoColumns)
  call(a,"showAutoGroups");assertNull(get(a,"groupDialog"))
 }
 @Test fun previewColorsEachChosenGroupAndOutlinesTheRest(){
  val a=prepare(listOf(1,2,3),twoColumns) // right column detected but only its title is chosen
  call(a,"showAutoGroups")
  val dialog=get(a,"groupDialog")as android.app.Dialog;assertTrue(dialog.isShowing)
  val root=dialog.window!!.decorView
  val boxes=checkBoxes(root);assertEquals(1,boxes.size);assertTrue(boxes.single().text.toString().startsWith("Groupe 1 · 2 blocs"))
  val canvas=canvases(root).single()
  assertEquals(setOf(1,2,3),canvas.chosenIds)
  assertEquals(setOf(1,2),canvas.mergeIds)
  assertEquals(1,canvas.groupBoxes.size)
  assertEquals(AutoGrouping.color(0),canvas.groupBoxes.single().color);assertEquals(1,canvas.groupBoxes.single().number)
  assertEquals(AutoGrouping.color(0),canvas.mergeColors[1]);assertEquals(AutoGrouping.color(0),canvas.mergeColors[2])
  assertNull(canvas.mergeColors[3])
  boxes.single().performClick();assertTrue(canvas.groupBoxes.isEmpty());assertTrue(canvas.mergeIds.isEmpty())
 }
 @Test fun menuKeepsOnlyTheUndoEntry(){
  val a=prepare(listOf(1,2),twoColumns)
  call(a,"menu")
  val dialog=get(a,"menuDialog")as android.app.Dialog;assertTrue(dialog.isShowing)
  val labels=mutableListOf<String>()
  fun collect(v:android.view.View){if(v is android.widget.TextView)labels+=v.text.toString();if(v is android.view.ViewGroup)for(i in 0 until v.childCount)collect(v.getChildAt(i))}
  collect(dialog.window!!.decorView)
  assertFalse(labels.any{it=="Proposer des regroupements"})
  assertTrue(labels.any{it=="Annuler le dernier regroupement"})
  assertTrue(labels.any{it=="Blocs de la page"})
 }
}
