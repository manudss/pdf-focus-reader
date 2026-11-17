package com.pdffocus.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.widget.Button

/**
 * Switch-like "Auto" pill shown in the title bar: rounded outline, a small
 * knob on the right when automatic choice is on (left when off) and a bold
 * "Auto" label. Restores the validated toggle look in place of the plain
 * button style. Extends Button so existing lookups (tests, accessibility)
 * keep working; the default label is not painted by the superclass.
 */
class AutoToggleButton(context: Context) : Button(context) {

    var on = true
        set(value) {
            field = value
            contentDescription = if (value) "Choix automatique activé" else "Choix automatique désactivé"
            invalidate()
        }

    private val green = Color.rgb(50, 109, 93)
    private val greenLight = Color.rgb(226, 239, 231)
    private val grey = Color.rgb(120, 118, 112)
    private val line = Color.rgb(226, 231, 226)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val pill = RectF()

    init {
        text = "Auto"
        setBackgroundColor(Color.TRANSPARENT)
        stateListAnimator = null
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        setPadding(0, 0, 0, 0)
        contentDescription = "Choix automatique activé"
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        stroke.strokeWidth = dp(1f)
        pill.set(stroke.strokeWidth / 2, stroke.strokeWidth / 2, w - stroke.strokeWidth / 2, h - stroke.strokeWidth / 2)
        fill.color = if (on) greenLight else Color.WHITE
        stroke.color = if (on) green else line
        canvas.drawRoundRect(pill, h / 2, h / 2, fill)
        canvas.drawRoundRect(pill, h / 2, h / 2, stroke)
        val pad = dp(3.5f)
        val r = h / 2 - pad
        val knobCx = if (on) w - h / 2 else h / 2
        fill.color = if (on) green else grey
        canvas.drawCircle(knobCx, h / 2, r, fill)
        label.color = if (on) green else grey
        val ty = h / 2 - (label.descent() + label.ascent()) / 2
        val tx = if (on) pad + dp(6f) else h / 2 + r + dp(5f)
        canvas.drawText("Auto", tx, ty, label)
    }
}
