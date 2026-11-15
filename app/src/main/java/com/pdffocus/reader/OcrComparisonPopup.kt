package com.pdffocus.reader
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.*
/** On-demand comparison, never a persistent review dashboard. */
class OcrComparisonPopup(context:Context,edit:OcrEdit,keptRaw:Boolean,onChoose:(Boolean)->Unit,onSource:()->Unit){
 private val density=context.resources.displayMetrics.density
 private fun dp(n:Int)=(n*density).toInt()
 val content=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(10),dp(14),dp(10));background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=dp(12).toFloat();setStroke(dp(1),Color.rgb(172,193,181))}}
 val window:PopupWindow
 init {
  fun label(value:String,bold:Boolean=false){content.addView(TextView(context).apply{text=value;textSize=16f;setTextColor(Color.rgb(37,31,33));setPadding(0,dp(3),0,dp(3));if(bold)setTypeface(null,Typeface.BOLD)})}
  label("Comparaison OCR",true);label("Corrigé : "+edit.corrected.trim().ifEmpty{"(retiré)"});label("Brut : "+edit.raw.trim().ifEmpty{"(absent)"});label("Le brut peut aussi contenir une erreur.")
  window=PopupWindow(content,min(dp(370),context.resources.displayMetrics.widthPixels-dp(24)),LinearLayout.LayoutParams.WRAP_CONTENT,true).apply{elevation=dp(7).toFloat();setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));isOutsideTouchable=true}
  fun action(value:String,run:()->Unit){content.addView(Button(context).apply{text=value;isAllCaps=false;minHeight=dp(48);setOnClickListener{window.dismiss();run()}},LinearLayout.LayoutParams(-1,-2))}
  action(if(keptRaw)"Lire le corrigé" else "Garder le brut"){onChoose(!keptRaw)};action("Extrait PDF",onSource);action("Fermer"){}
 }
 fun show(anchor:View,y:Float){content.measure(View.MeasureSpec.makeMeasureSpec(window.width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));val location=IntArray(2);anchor.getLocationOnScreen(location);val visible=android.graphics.Rect();anchor.getWindowVisibleDisplayFrame(visible);val top=(location[1]+y.toInt()+dp(8)).coerceIn(visible.top,max(visible.top,visible.bottom-content.measuredHeight-dp(8)));window.showAtLocation(anchor,Gravity.TOP or Gravity.LEFT,max(visible.left+dp(12),(visible.width()-window.width)/2),top)}
}
