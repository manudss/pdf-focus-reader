package com.pdffocus.reader

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

/**
 * Interactive walkthrough: the four gestures are practiced for real, on an isolated
 * copy of the bundled example kept in the cache directory. Nothing here touches the
 * user's document, sessions or preferences:
 * a kill or an early exit can lose nothing of theirs.
 */
class TutorialActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val green = Color.rgb(50, 109, 93)
    private val ink = Color.rgb(37, 31, 33)
    private val grey = Color.rgb(120, 118, 112)

    private var document: PdfDocument? = null
    private lateinit var canvas: PageCanvas
    private lateinit var reader: ContinuousReaderView
    private lateinit var dots: LinearLayout
    private lateinit var hintTitle: TextView
    private lateinit var hintBody: TextView
    private lateinit var pass: TextView
    private lateinit var status: TextView

    private var zones = emptyList<Zone>()
    private var order = emptyList<Int>()
    private var step = -1
    private var stepStart = 0
    private var advancing = false
    private var page = 2
    @Volatile private var ended = false
    private var detector: ZoneDetector? = null
    private lateinit var practiceFile: File

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val steps = listOf(
        Triple("Choisir vos blocs", "Maintenez un doigt sur la page, puis glissez sur les blocs à lire. Choisissez-en au moins deux.", "Bien ! Vos blocs sont numérotés dans votre ordre."),
        Triple("Retirer avec deux doigts", "Maintenez deux doigts sur la page, puis glissez sur un bloc choisi pour le retirer.", "C'est ça : le bloc est retiré du parcours."),
        Triple("Zoomer en lisant", "Écartez deux doigts pour agrandir, rapprochez-les pour réduire.", "Parfait, le zoom répond."),
        Triple("Changer de page", "Glissez la page vers la gauche pour voir la suite du document.", "Et voilà : vous savez tout."))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(250, 249, 246)) }
        root.setOnApplyWindowInsetsListener { v, insets -> v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom); insets }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.WHITE); setPadding(dp(16), dp(8), dp(12), dp(6)) }
        dots = LinearLayout(this)
        bar.addView(dots, LinearLayout.LayoutParams(0, -2, 1f))
        pass = TextView(this).apply {
            text = "Passer"; textSize = 15f; setTextColor(grey); setPadding(dp(10), dp(6), dp(2), dp(6))
            setOnClickListener { if (step in steps.indices) advance(true) else finish() }
        }
        bar.addView(pass)
        val hint = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); setPadding(dp(16), dp(2), dp(16), dp(8)) }
        hintTitle = TextView(this).apply { textSize = 17f; setTextColor(ink); setTypeface(null, Typeface.BOLD) }
        hintBody = TextView(this).apply { textSize = 14f; setTextColor(grey); setLineSpacing(0f, 1.2f) }
        hint.addView(hintTitle); hint.addView(hintBody)
        status = TextView(this).apply { textSize = 13f; setTextColor(grey); setPadding(dp(16), dp(4), dp(16), dp(4)) }
        val viewport = FrameLayout(this).apply {
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); setStroke(dp(2), green); cornerRadius = dp(8).toFloat() }
        }
        canvas = PageCanvas(this)
        reader = ContinuousReaderView(this).apply { visibility = View.GONE }
        viewport.addView(canvas, FrameLayout.LayoutParams(-1, -1))
        viewport.addView(reader, FrameLayout.LayoutParams(-1, -1))
        root.addView(bar); root.addView(hint); root.addView(status); root.addView(viewport, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        canvas.onZoneSweep = { z -> if (step == 0 && z.id !in order) { order = order + z.id; sync() } }
        canvas.onZoneSweepRemove = { z -> if (step == 1 && z.id in order) { order = order - z.id; sync() } }
        canvas.onZoneTap = { z ->
            if (step == 0 && z.id !in order) { order = order + z.id; sync() }

        }
        canvas.onPageSwipe = { direction -> if (step == 3 && page == 2 && direction == 1) renderPage(false) }
        reader.onRequest = { tile, revision ->
            worker.execute {
                var image: Bitmap? = null
                try { image = document?.readingRegion(2, tile.source, maxOf(1, Math.ceil(tile.rect.width().toDouble()).toInt()), maxOf(1, Math.ceil(tile.rect.height().toDouble()).toInt()), false) } catch (_: Exception) {}
                main.post { if (!ended) reader.accept(tile, revision, image) else image?.recycle() }
            }
        }

        status.text = "Préparation de la page d'exercice…"
        worker.execute {
            val file = File.createTempFile("tutorial-practice-", ".pdf", cacheDir)
            practiceFile = file
            assets.open("examples/decouverte.pdf").use { input -> file.outputStream().use { input.copyTo(it) } }
            val doc = try { PdfDocument(file) } catch (_: Exception) { null }
            if (doc == null) { main.post { Toast.makeText(this, "Exercice indisponible.", Toast.LENGTH_SHORT).show(); finish() }; return@execute }
            document = doc
            val image = doc.preview(2)
            val detected = try { ZoneDetector(applicationContext).also { detector = it }.detect(image) { ended } } catch (_: Exception) { emptyList() }
            main.post {
                if (ended) { image.recycle(); return@post }
                canvas.image = image
                // Teach the gestures on the newspaper itself, not the example PDF's
                // instructional banner or footer.
                zones = TextCoverage.bridgeColumnGaps(detected).filter { it.box.top > .18f && it.box.bottom < .82f }
                if (zones.size < 2) { status.text = "Les blocs ne sont pas disponibles. Vous pouvez passer cette étape." }
                else status.text = "À vous : faites le geste dans le cadre vert."
                step = 0; stepStart = 0
                sync()
                refreshHint()
                main.postDelayed(watch, 400)
            }
        }
    }

    private fun sync() { canvas.zones = zones; canvas.selection = order }

    private fun renderPage(withZones: Boolean) {
        status.text = "Changement de page…"
        worker.execute {
            val image = document?.preview(3) ?: return@execute
            main.post {
                if (ended) { image.recycle(); return@post }
                val old = canvas.image; canvas.image = image; old?.recycle()
                page = 3; status.text = "Page 4 sur 9"
                if (!withZones) { zones = emptyList(); order = emptyList(); sync() }
            }
        }
    }

    private fun refreshHint() {
        val s = steps[step]
        hintTitle.text = s.first; hintBody.text = s.second
        pass.text = if (step < steps.size - 1) "Passer" else "Terminer"
        dots.removeAllViews()
        for (i in steps.indices) dots.addView(View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(if (i == step) green else Color.rgb(226, 231, 226)) }
        }, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(8) })
    }

    private fun stepDone() = when (step) {
        0 -> order.size >= 2
        1 -> order.size < stepStart
        2 -> reader.zoomUsed
        3 -> page != 2
        else -> false
    }

    private val watch = object : Runnable {
        override fun run() {
            if (ended || step < 0) return
            if (!advancing && stepDone()) advance(false)
            main.postDelayed(this, 400)
        }
    }

    private fun advance(skipped: Boolean) {
        if (advancing) return
        advancing = true
        if (!skipped) { hintTitle.text = steps[step].third; hintBody.text = "" }
        main.postDelayed({
            if (ended) return@postDelayed
            if (step < steps.size - 1) {
                step++; advancing = false
                if (step == 1 && order.size < 2) { order = zones.take(2).map { it.id }; sync() }
                stepStart = order.size
                if (step == 2) enterReading() else if (step == 3) exitReading()
                refreshHint()
            } else finish()
        }, if (skipped) 0 else 1300)
    }

    private fun enterReading() {
        // The zoom exercise is independent of which small headings were selected in
        // the preceding exercises: always show enough article text to read and zoom.
        val body = zones.filter { it.kind == Kind.TEXT && it.box.height > .10f }
            .sortedWith(compareBy<Zone> { it.box.left }.thenBy { it.box.top })
        val image = canvas.image
        if (image != null) reader.setDocument(if (body.isNotEmpty()) body else zones, image.width, image.height)
        canvas.visibility = View.GONE; reader.visibility = View.VISIBLE
        status.text = "Mode lecture : pincez pour zoomer."
    }

    private fun exitReading() {
        reader.clear(); reader.visibility = View.GONE; canvas.visibility = View.VISIBLE
        status.text = "Page ${page + 1} sur 9"
    }

    override fun onDestroy() {
        ended = true; detector?.cancel(); main.removeCallbacksAndMessages(null); reader.clear()
        canvas.image?.recycle(); canvas.image = null
        worker.execute { document?.close(); if (::practiceFile.isInitialized) practiceFile.delete() }
        worker.shutdown()
        super.onDestroy()
    }
}
