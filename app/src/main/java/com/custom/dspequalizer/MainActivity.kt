package com.custom.dspequalizer

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.media.audiofx.DynamicsProcessing
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.*
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private var dsp: DynamicsProcessing? = null
    private var isVuActive = true
    private var isHeadroomActive = false
    private var isLimiterActive = true
    private var limiterThresholdDb = -0.5f
    private var limiterReleaseMs = 60.0f

    private val vuBars = mutableListOf<ProgressBar>()
    private val faderValues = FloatArray(7)
    private val eq30SeekBars = mutableListOf<SeekBar>()
    private val eq30DbValues = FloatArray(30)
    private val handler = Handler(Looper.getMainLooper())
    private var tvStatus: TextView? = null

    private lateinit var pageMixBar: View
    private lateinit var pageMixFaders: View
    private lateinit var pageEq30: LinearLayout
    private lateinit var pageParam: LinearLayout
    private lateinit var pageDyn: LinearLayout
    private lateinit var pagePlaceholder: LinearLayout
    private lateinit var tvPlaceholderTitle: TextView

    data class PeqBand(var freq: Float, var gain: Float, var q: Float, var enabled: Boolean = true)
    private val peqBands = arrayOf(
        PeqBand(45f, 0f, 1.4f),
        PeqBand(110f, 0f, 1.4f),
        PeqBand(400f, 0f, 1.4f),
        PeqBand(1200f, 0f, 1.4f),
        PeqBand(3800f, 0f, 1.4f),
        PeqBand(10000f, 0f, 1.2f)
    )
    private var selectedPeqIndex = 0
    private lateinit var peqCurveView: PeqCurveView

    private val preFreqs = floatArrayOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    private val eq30Freqs = floatArrayOf(
        20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f,
        200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f,
        2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f
    )
    private val eq30Labels = arrayOf(
        "20", "25", "31", "40", "50", "63", "80", "100", "125", "160",
        "200", "250", "315", "400", "500", "630", "800", "1k", "1.2k", "1.6k",
        "2k", "2.5k", "3.1k", "4k", "5k", "6.3k", "8k", "10k", "12.5k", "16k"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler { _, t ->
            runOnUiThread { showErrorDialog("Error:\n${t.message}") }
        }
        try {
            setContentView(R.layout.activity_main)
            tvStatus = findViewById(R.id.tvDspStatus)
            val chEq = findViewById<View>(R.id.chGraphicEq)
            pageMixFaders = chEq.parent as View
            val rootLayout = pageMixFaders.parent as ViewGroup
            pageMixBar = rootLayout.getChildAt(2)

            buildDynamicPages(rootLayout)
            setupNavigationTabs()
            setupTopControls()
            setupMixBusFaders()
            initDspEngine()
            startVuAnimation()
        } catch (e: Exception) {
            showErrorDialog("Gagal:\n${e.message}")
        }
    }
    private fun buildDynamicPages(rootLayout: ViewGroup) {
        val fullParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { setMargins(0, 6, 0, 0) }

        pageEq30 = LinearLayout(this).apply {
            layoutParams = fullParams
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12161B"))
            setPadding(12, 8, 12, 8)
            visibility = View.GONE
        }

        val eqTopBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvEqInfo = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = "30-BAND GRAPHIC EQ (20 Hz – 16 kHz)"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 11f
        }
        val btnReset = Button(this).apply {
            text = "RESET FLAT"
            textSize = 10f
            setTextColor(Color.parseColor("#FF5252"))
            setBackgroundResource(R.drawable.bg_pill_btn)
            setOnClickListener {
                eq30SeekBars.forEach { it.progress = 150 }
                eq30DbValues.fill(0f)
                syncPostEqToDsp()
                Toast.makeText(this@MainActivity, "EQ 30-Band Reset Flat", Toast.LENGTH_SHORT).show()
            }
        }
        eqTopBar.addView(tvEqInfo)
        eqTopBar.addView(btnReset)
        pageEq30.addView(eqTopBar)

        val hScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val slidersRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        for (i in eq30Freqs.indices) {
            val col = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(95, LinearLayout.LayoutParams.MATCH_PARENT).apply { setMargins(3, 4, 3, 4) }
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setBackgroundResource(R.drawable.bg_fader_card)
                setPadding(2, 6, 2, 6)
            }
            val tvFreq = TextView(this).apply { text = eq30Labels.get(i); setTextColor(Color.parseColor("#00E5FF")); textSize = 9f; gravity = Gravity.CENTER }
            val tvDb = TextView(this).apply { text = "0.0"; setTextColor(Color.WHITE); textSize = 9f; gravity = Gravity.CENTER }
            val frame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                clipChildren = false
            }
            val sb = SeekBar(this).apply {
                layoutParams = FrameLayout.LayoutParams(380, 48, Gravity.CENTER)
                rotation = 270f; max = 300; progress = 150
                scaleX = 0.85f
                scaleY = 0.65f
                setProgressDrawable(getDrawable(R.drawable.bg_fader_track))
                thumb = getDrawable(R.drawable.bg_fader_thumb)
                splitTrack = false
            }
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    val db = (p - 150) / 10.0f
                    eq30DbValues.set(i, db)
                    tvDb.text = String.format("%+.1f", db)
                    syncPostEqToDsp()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            eq30SeekBars.add(sb)
            frame.addView(sb); col.addView(tvFreq); col.addView(frame); col.addView(tvDb)
            slidersRow.addView(col)
        }
        hScroll.addView(slidersRow)
        pageEq30.addView(hScroll)
        rootLayout.addView(pageEq30)

        buildParametricEqPage(rootLayout, fullParams)

        pageDyn = LinearLayout(this).apply {
            layoutParams = fullParams
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12161B"))
            setPadding(24, 16, 24, 16)
            visibility = View.GONE
        }
        val tvDynTitle = TextView(this).apply { text = "DYNAMICS PROCESSOR & MASTER LIMITER"; setTextColor(Color.parseColor("#00E676")); textSize = 14f }
        val tvThresh = TextView(this).apply { text = "Limiter Threshold: -0.5 dB"; setTextColor(Color.WHITE); textSize = 12f; setPadding(0, 16, 0, 4) }
        val sbThresh = SeekBar(this).apply { max = 120; progress = 115 }
        sbThresh.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                limiterThresholdDb = (p - 120) / 10.0f
                tvThresh.text = String.format("Limiter Threshold: %.1f dB", limiterThresholdDb)
                updateLimiter()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val tvRel = TextView(this).apply { text = "Limiter Release Time: 60 ms"; setTextColor(Color.WHITE); textSize = 12f; setPadding(0, 16, 0, 4) }
        val sbRel = SeekBar(this).apply { max = 290; progress = 50 }
        sbRel.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                limiterReleaseMs = (p + 10).toFloat()
                tvRel.text = String.format("Limiter Release Time: %.0f ms", limiterReleaseMs)
                updateLimiter()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        pageDyn.addView(tvDynTitle); pageDyn.addView(tvThresh); pageDyn.addView(sbThresh); pageDyn.addView(tvRel); pageDyn.addView(sbRel)
        rootLayout.addView(pageDyn)

        pagePlaceholder = LinearLayout(this).apply {
            layoutParams = fullParams
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#12161B"))
            visibility = View.GONE
        }
        tvPlaceholderTitle = TextView(this).apply { setTextColor(Color.parseColor("#00E5FF")); textSize = 16f }
        val tvSub = TextView(this).apply { text = "Modul ini aktif di sistem dan siap dikustomisasi."; setTextColor(Color.parseColor("#90A4AE")); textSize = 12f }
        pagePlaceholder.addView(tvPlaceholderTitle); pagePlaceholder.addView(tvSub)
        rootLayout.addView(pagePlaceholder)
    }
    private fun buildParametricEqPage(rootLayout: ViewGroup, fullParams: LinearLayout.LayoutParams) {
        pageParam = LinearLayout(this).apply {
            layoutParams = fullParams
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#12161B"))
            setPadding(14, 8, 14, 8)
            visibility = View.GONE
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvTitle = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = "6-BAND PARAMETRIC EQ (PEQ)"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 11f
        }
        val btnBandToggle = Button(this).apply {
            text = "BAND: ON"
            textSize = 9f
            setTextColor(Color.parseColor("#00E676"))
            setBackgroundResource(R.drawable.bg_pill_btn)
        }
        val btnResetPeq = Button(this).apply {
            text = "FLAT PEQ"
            textSize = 9f
            setTextColor(Color.parseColor("#FF5252"))
            setBackgroundResource(R.drawable.bg_pill_btn)
        }
        topBar.addView(tvTitle)
        topBar.addView(btnBandToggle)
        topBar.addView(btnResetPeq)
        pageParam.addView(topBar)

        peqCurveView = PeqCurveView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.15f).apply {
                setMargins(0, 6, 0, 6)
            }
        }
        pageParam.addView(peqCurveView)

        val bandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val bandButtons = mutableListOf<Button>()

        val controlsBox = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.35f)
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_fader_card)
            setPadding(14, 8, 14, 8)
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvFreqVal = TextView(this).apply { setTextColor(Color.WHITE); textSize = 11f }
        val sbFreq = SeekBar(this).apply { max = 1000 }
        val tvGainVal = TextView(this).apply { setTextColor(Color.WHITE); textSize = 11f; setPadding(0, 6, 0, 0) }
        val sbGain = SeekBar(this).apply { max = 300; progress = 150 }
        val tvQVal = TextView(this).apply { setTextColor(Color.WHITE); textSize = 11f; setPadding(0, 6, 0, 0) }
        val sbQ = SeekBar(this).apply { max = 95; progress = 9 }

        var isUpdatingUi = false

        fun freqToProgress(freq: Float): Int {
            val ratio = ln((freq.coerceIn(20f, 20000f) / 20f).toDouble()) / ln(1000.0)
            return (ratio * 1000.0).roundToInt().coerceIn(0, 1000)
        }

        fun progressToFreq(progress: Int): Float {
            val ratio = progress / 1000.0
            return (20.0 * 1000.0.pow(ratio)).toFloat()
        }

        fun refreshSelectedBandUi() {
            isUpdatingUi = true
            val b = peqBands.get(selectedPeqIndex)
            bandButtons.forEachIndexed { idx, btn ->
                val bandData = peqBands.get(idx)
                val freqStr = if (bandData.freq >= 1000f) String.format("%.1fk", bandData.freq / 1000f) else String.format("%.0f", bandData.freq)
                btn.text = "B${idx + 1}\n$freqStr"
                btn.setTextColor(
                    when {
                        idx == selectedPeqIndex -> Color.parseColor("#FF5252")
                        !bandData.enabled -> Color.GRAY
                        else -> Color.parseColor("#00E5FF")
                    }
                )
            }
            btnBandToggle.text = if (b.enabled) "B${selectedPeqIndex + 1}: ON" else "B${selectedPeqIndex + 1}: OFF"
            btnBandToggle.setTextColor(if (b.enabled) Color.parseColor("#00E676") else Color.GRAY)

            tvFreqVal.text = String.format("Band %d Frequency: %.0f Hz", selectedPeqIndex + 1, b.freq)
            sbFreq.progress = freqToProgress(b.freq)

            tvGainVal.text = String.format("Band %d Gain: %+.1f dB", selectedPeqIndex + 1, b.gain)
            sbGain.progress = ((b.gain * 10f) + 150f).roundToInt().coerceIn(0, 300)

            tvQVal.text = String.format("Band %d Q-Factor (Lebar): %.2f", selectedPeqIndex + 1, b.q)
            sbQ.progress = ((b.q - 0.5f) * 10f).roundToInt().coerceIn(0, 95)

            peqCurveView.invalidate()
            isUpdatingUi = false
        }

        for (i in peqBands.indices) {
            val btn = Button(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(2, 0, 2, 4)
                }
                textSize = 9f
                setPadding(2, 4, 2, 4)
                setBackgroundResource(R.drawable.bg_pill_btn)
                setOnClickListener {
                    selectedPeqIndex = i
                    refreshSelectedBandUi()
                }
            }
            bandButtons.add(btn)
            bandRow.addView(btn)
        }
        pageParam.addView(bandRow)

        sbFreq.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (isUpdatingUi) return
                peqBands.get(selectedPeqIndex).freq = progressToFreq(p)
                refreshSelectedBandUi()
                syncPostEqToDsp()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        sbGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (isUpdatingUi) return
                peqBands.get(selectedPeqIndex).gain = (p - 150) / 10.0f
                refreshSelectedBandUi()
                syncPostEqToDsp()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        sbQ.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (isUpdatingUi) return
                peqBands.get(selectedPeqIndex).q = 0.5f + (p / 10.0f)
                refreshSelectedBandUi()
                syncPostEqToDsp()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        btnBandToggle.setOnClickListener {
            val b = peqBands.get(selectedPeqIndex)
            b.enabled = !b.enabled
            refreshSelectedBandUi()
            syncPostEqToDsp()
        }

        btnResetPeq.setOnClickListener {
            val defaults = floatArrayOf(45f, 110f, 400f, 1200f, 3800f, 10000f)
            for (i in peqBands.indices) {
                peqBands.get(i).freq = defaults.get(i)
                peqBands.get(i).gain = 0f
                peqBands.get(i).q = 1.4f
                peqBands.get(i).enabled = true
            }
            refreshSelectedBandUi()
            syncPostEqToDsp()
            Toast.makeText(this, "Parametric EQ Reset Flat", Toast.LENGTH_SHORT).show()
        }

        controlsBox.addView(tvFreqVal); controlsBox.addView(sbFreq)
        controlsBox.addView(tvGainVal); controlsBox.addView(sbGain)
        controlsBox.addView(tvQVal); controlsBox.addView(sbQ)
        pageParam.addView(controlsBox)

        refreshSelectedBandUi()
        rootLayout.addView(pageParam)
    }
    private fun calculatePeqGainAt(freqHz: Float): Float {
        var totalGain = 0f
        for (band in peqBands) {
            if (!band.enabled || abs(band.gain) < 0.05f) continue
            val octaves = abs(ln((freqHz / band.freq).toDouble()) / ln(2.0)).toFloat()
            val bw = 1.0f / band.q.coerceAtLeast(0.3f)
            val response = exp(-0.5 * (octaves / (bw * 0.55)).pow(2.0)).toFloat()
            totalGain += band.gain * response
        }
        return totalGain
    }

    private fun syncPostEqToDsp() {
        val engine = dsp ?: return
        val paramSend = faderValues.get(1)
        for (i in eq30Freqs.indices) {
            val freq = eq30Freqs.get(i)
            val graphicDb = eq30DbValues.get(i)
            val peqDb = calculatePeqGainAt(freq) * paramSend
            val combinedDb = (graphicDb + peqDb).coerceIn(-15f, 15f)
            try {
                engine.setPostEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, freq, combinedDb))
            } catch (_: Exception) {}
        }
    }

    inner class PeqCurveView(context: Context) : View(context) {
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#263238"); strokeWidth = 2f; style = Paint.Style.STROKE }
        private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#455A64"); strokeWidth = 2.5f }
        private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00E5FF"); strokeWidth = 5f; style = Paint.Style.STROKE }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2200E5FF"); style = Paint.Style.FILL }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#78909C"); textSize = 22f }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            canvas.drawColor(Color.parseColor("#0D1115"))
            val midY = h / 2f

            canvas.drawLine(0f, midY, w, midY, zeroLinePaint)
            canvas.drawLine(0f, h * 0.15f, w, h * 0.15f, gridPaint)
            canvas.drawLine(0f, h * 0.85f, w, h * 0.85f, gridPaint)

            val markerFreqs = floatArrayOf(50f, 100f, 500f, 1000f, 5000f, 10000f)
            val markerLabels = arrayOf("50", "100", "500", "1k", "5k", "10k")
            for (i in markerFreqs.indices) {
                val x = (ln((markerFreqs.get(i) / 20f).toDouble()) / ln(1000.0) * w).toFloat()
                canvas.drawLine(x, 0f, x, h, gridPaint)
                canvas.drawText(markerLabels.get(i), x + 4f, h - 8f, textPaint)
            }

            val path = Path()
            val fillPath = Path()
            fillPath.moveTo(0f, midY)

            val steps = 120
            for (s in 0..steps) {
                val ratio = s.toFloat() / steps
                val freq = (20.0 * 1000.0.pow(ratio.toDouble())).toFloat()
                val gainDb = calculatePeqGainAt(freq).coerceIn(-15f, 15f)
                val x = ratio * w
                val y = midY - (gainDb / 15f) * (h * 0.42f)
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            fillPath.lineTo(w, midY)
            fillPath.close()

            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(path, curvePaint)

            for (i in peqBands.indices) {
                val b = peqBands.get(i)
                val ratio = (ln((b.freq.coerceIn(20f, 20000f) / 20f).toDouble()) / ln(1000.0)).toFloat()
                val bx = ratio * w
                val by = midY - (b.gain.coerceIn(-15f, 15f) / 15f) * (h * 0.42f)
                dotPaint.color = when {
                    i == selectedPeqIndex -> Color.parseColor("#FF5252")
                    !b.enabled -> Color.GRAY
                    else -> Color.parseColor("#00E676")
                }
                canvas.drawCircle(bx, by, if (i == selectedPeqIndex) 13f else 9f, dotPaint)
            }
        }
    }
    private fun setupNavigationTabs() {
        val tabs = listOf(
            findViewById<Button>(R.id.tabMix), findViewById<Button>(R.id.tabEq30), findViewById<Button>(R.id.tabParam),
            findViewById<Button>(R.id.tabFx), findViewById<Button>(R.id.tabDyn), findViewById<Button>(R.id.tabSpace),
            findViewById<Button>(R.id.tabSaved), findViewById<Button>(R.id.tabSignal), findViewById<Button>(R.id.tabCon)
        )
        fun switchPage(btn: Button, mode: String, title: String = "") {
            tabs.forEach { it.setTextColor(Color.parseColor("#B0BEC5")) }
            btn.setTextColor(Color.parseColor("#FF5252"))
            pageMixBar.visibility = if (mode == "MIX") View.VISIBLE else View.GONE
            pageMixFaders.visibility = if (mode == "MIX") View.VISIBLE else View.GONE
            pageEq30.visibility = if (mode == "EQ30") View.VISIBLE else View.GONE
            pageParam.visibility = if (mode == "PARAM") View.VISIBLE else View.GONE
            pageDyn.visibility = if (mode == "DYN") View.VISIBLE else View.GONE
            pagePlaceholder.visibility = if (mode == "OTHER") View.VISIBLE else View.GONE
            if (title.isNotEmpty()) tvPlaceholderTitle.text = title
        }
        findViewById<Button>(R.id.tabMix).setOnClickListener { switchPage(it as Button, "MIX") }
        findViewById<Button>(R.id.tabEq30).setOnClickListener { switchPage(it as Button, "EQ30") }
        findViewById<Button>(R.id.tabParam).setOnClickListener { switchPage(it as Button, "PARAM") }
        findViewById<Button>(R.id.tabDyn).setOnClickListener { switchPage(it as Button, "DYN") }
        findViewById<Button>(R.id.tabFx).setOnClickListener { switchPage(it as Button, "OTHER", "FX RACK (SUB-BASS & DRIVE)") }
        findViewById<Button>(R.id.tabSpace).setOnClickListener { switchPage(it as Button, "OTHER", "SPACE & STEREO WIDENER") }
        findViewById<Button>(R.id.tabSaved).setOnClickListener { switchPage(it as Button, "OTHER", "SAVED PRESET MANAGER") }
        findViewById<Button>(R.id.tabSignal).setOnClickListener { switchPage(it as Button, "OTHER", "SIGNAL ROUTING & MONITOR") }
        findViewById<Button>(R.id.tabCon).setOnClickListener { switchPage(it as Button, "OTHER", "CONSOLE CONFIGURATION") }
    }

    private fun initDspEngine() {
        try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 1,
                true, preFreqs.size, true, preFreqs.size, true, eq30Freqs.size, true
            ).build()
            dsp = DynamicsProcessing(0, 0, config).apply { enabled = true }
            updateLimiter()
            tvStatus?.text = "DSP STATUS: Post 30-Band • PEQ 6-Band • MBC aktif"
            tvStatus?.setTextColor(Color.parseColor("#00E676"))
        } catch (e: Exception) {
            tvStatus?.text = "DSP INFO: Matikan Dolby/Mi Sound di Setelan HP (${e.javaClass.simpleName})"
            tvStatus?.setTextColor(Color.YELLOW)
        }
    }

    private fun setupTopControls() {
        val btnDspPower = findViewById<ToggleButton>(R.id.btnDspPower)
        val btnHead = findViewById<ToggleButton>(R.id.btnHead)
        val btnLimit = findViewById<ToggleButton>(R.id.btnLimit)
        val btnVu = findViewById<ToggleButton>(R.id.btnVu)

        btnDspPower.setOnCheckedChangeListener { _, isChecked ->
            try { dsp?.enabled = isChecked } catch (_: Exception) {}
            btnDspPower.setTextColor(if (isChecked) Color.parseColor("#00E676") else Color.GRAY)
            tvStatus?.text = if (isChecked) "DSP STATUS: Post 30-Band • PEQ 6-Band • MBC aktif" else "DSP STATUS: BYPASS (NONAKTIF)"
        }
        btnHead.setOnCheckedChangeListener { _, isChecked ->
            isHeadroomActive = isChecked
            btnHead.setTextColor(if (isChecked) Color.parseColor("#00E676") else Color.parseColor("#FF5252"))
            applyAudioParameters()
        }
        btnLimit.setOnCheckedChangeListener { _, isChecked ->
            isLimiterActive = isChecked
            btnLimit.setTextColor(if (isChecked) Color.parseColor("#00E676") else Color.GRAY)
            updateLimiter()
        }
        btnVu.setOnCheckedChangeListener { _, isChecked ->
            isVuActive = isChecked
            btnVu.setTextColor(if (isChecked) Color.parseColor("#00E676") else Color.GRAY)
            if (!isChecked) vuBars.forEach { it.progress = 0 }
        }
    }

    private fun setupMixBusFaders() {
        setupChannel(R.id.chGraphicEq, 0, "GRAPHIC EQ", "30 BAND SEND", true, 100)
        setupChannel(R.id.chParametric, 1, "PARAMETRIC", "FILTER SEND", true, 100)
        setupChannel(R.id.chLow, 2, "LOW", "TONE TRIM", false, 50)
        setupChannel(R.id.chMid, 3, "MID", "TONE TRIM", false, 50)
        setupChannel(R.id.chHigh, 4, "HIGH", "TONE TRIM", false, 50)
        setupChannel(R.id.chBassBoost, 5, "BASS BOOST", "LOW EQ", false, 50)
        setupChannel(R.id.chMaster, 6, "MASTER", "PREAMP TRIM", false, 50)
    }

    private fun setupChannel(viewId: Int, index: Int, title: String, subtitle: String, isPercent: Boolean, defaultProgress: Int) {
        val root = findViewById<View>(viewId) ?: return
        root.findViewById<TextView>(R.id.tvTitle).text = title
        root.findViewById<TextView>(R.id.tvSubtitle).text = subtitle
        val tvValue = root.findViewById<TextView>(R.id.tvValue)
        val seekBar = root.findViewById<SeekBar>(R.id.faderSeekBar)
        vuBars.add(root.findViewById(R.id.vuMeterBar))

        seekBar.max = if (isPercent) 100 else 300
        seekBar.progress = if (isPercent) defaultProgress else 150

        fun updateLabel(progress: Int) {
            if (isPercent) {
                faderValues.set(index, progress / 100f)
                tvValue.text = "$progress%"
            } else {
                val db = (progress - 150) / 10.0f
                faderValues.set(index, db)
                tvValue.text = String.format("%+.1f dB", db)
            }
        }
        updateLabel(seekBar.progress)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                updateLabel(progress)
                applyAudioParameters()
                if (index == 1) syncPostEqToDsp()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun applyAudioParameters() {
        try {
            val engine = dsp ?: return
            val eqSend = faderValues.get(0)
            val lowTrim = faderValues.get(2)
            val midTrim = faderValues.get(3)
            val highTrim = faderValues.get(4)
            val bassBoost = faderValues.get(5)
            val masterTrim = faderValues.get(6)
            engine.setInputGainAllChannelsTo(masterTrim + (if (isHeadroomActive) -6.0f else 0.0f))
            for (i in preFreqs.indices) {
                val rawGain = when {
                    i <= 1 -> (lowTrim + bassBoost) * eqSend
                    i == 2 -> lowTrim * eqSend
                    i in 3..6 -> midTrim * eqSend
                    else -> highTrim * eqSend
                }
                engine.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, preFreqs.get(i), rawGain.coerceIn(-15f, 15f)))
            }
        } catch (_: Exception) {}
    }

    private fun updateLimiter() {
        try {
            dsp?.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, isLimiterActive, 0, 1f, limiterReleaseMs, 10f, limiterThresholdDb, 0f))
        } catch (_: Exception) {}
    }

    private fun startVuAnimation() {
        handler.post(object : Runnable {
            override fun run() {
                if (isVuActive) {
                    for (i in vuBars.indices) {
                        val valNow = faderValues.get(i)
                        val baseLevel = if (i < 2) (valNow * 70).toInt() else ((valNow + 15f) * 2.5f).toInt()
                        vuBars.get(i).progress = (baseLevel + Random.nextInt(-15, 20)).coerceIn(5, 98)
                    }
                }
                handler.postDelayed(this, 80)
            }
        })
    }

    private fun showErrorDialog(msg: String) {
        AlertDialog.Builder(this).setTitle("Info Error").setMessage(msg).setPositiveButton("Tutup", null).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try { dsp?.release() } catch (_: Exception) {}
    }
}
