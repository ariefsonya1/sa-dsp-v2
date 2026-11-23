package com.custom.dspequalizer

import android.app.AlertDialog
import android.graphics.Color
import android.media.audiofx.DynamicsProcessing
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
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
    private val handler = Handler(Looper.getMainLooper())
    private var tvStatus: TextView? = null

    private lateinit var pageMixBar: View
    private lateinit var pageMixFaders: View
    private lateinit var pageEq30: GraphicEq30View
    private lateinit var pageParam: ParametricEqView
    private lateinit var pageFx: FxRackView
    private lateinit var pageDyn: LinearLayout
    private lateinit var pagePlaceholder: LinearLayout
    private lateinit var tvPlaceholderTitle: TextView

    private val preFreqs = floatArrayOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    private val eq30Freqs = floatArrayOf(
        20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f,
        200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f,
        2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f
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

        pageEq30 = GraphicEq30View(this).apply {
            layoutParams = fullParams
            visibility = View.GONE
            onEq30ChangedListener = { syncPostEqToDsp() }
        }
        rootLayout.addView(pageEq30)

        pageParam = ParametricEqView(this).apply {
            layoutParams = fullParams
            visibility = View.GONE
            onEqChangedListener = { syncPostEqToDsp() }
        }
        rootLayout.addView(pageParam)

        pageFx = FxRackView(this).apply {
            layoutParams = fullParams
            visibility = View.GONE
            onFxChangedListener = { syncPostEqToDsp() }
        }
        rootLayout.addView(pageFx)

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
    private fun syncPostEqToDsp() {
        val engine = dsp ?: return
        val eqSend = faderValues.get(0)
        val paramSend = faderValues.get(1)
        for (i in eq30Freqs.indices) {
            val freq = eq30Freqs.get(i)
            val graphicDb = pageEq30.getCombinedBandGain(i) * eqSend
            val peqDb = pageParam.calculateTotalGainAt(freq) * paramSend
            val fxDb = pageFx.calculateFxBoostAt(freq)
            val combinedDb = (graphicDb + peqDb + fxDb).coerceIn(-15f, 15f)
            try {
                engine.setPostEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, freq, combinedDb))
            } catch (_: Exception) {}
        }
    }

    private fun setupNavigationTabs() {
        val tabs = listOf(
            findViewById<Button>(R.id.tabMix), findViewById<Button>(R.id.tabEq30), findViewById<Button>(R.id.tabParam),
            findViewById<Button>(R.id.tabFx), findViewById<Button>(R.id.tabDyn), findViewById<Button>(R.id.tabSpace),
            findViewById<Button>(R.id.tabSaved), findViewById<Button>(R.id.tabSignal), findViewById<Button>(R.id.tabCon)
        )

        fun applyTabStyle(btn: Button, isSelected: Boolean) {
            btn.backgroundTintList = null
            btn.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(if (isSelected) Color.parseColor("#1E293B") else Color.parseColor("#141A21"))
                cornerRadius = 10f
                setStroke(2, if (isSelected) Color.parseColor("#38BDF8") else Color.parseColor("#26323D"))
            }
            btn.setTextColor(if (isSelected) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8"))
        }

        tabs.forEachIndexed { index, button -> applyTabStyle(button, index == 0) }

        fun switchPage(btn: Button, mode: String, title: String = "") {
            tabs.forEach { applyTabStyle(it, it == btn) }
            pageMixBar.visibility = if (mode == "MIX") View.VISIBLE else View.GONE
            pageMixFaders.visibility = if (mode == "MIX") View.VISIBLE else View.GONE
            pageEq30.visibility = if (mode == "EQ30") View.VISIBLE else View.GONE
            pageParam.visibility = if (mode == "PARAM") View.VISIBLE else View.GONE
            pageFx.visibility = if (mode == "FX") View.VISIBLE else View.GONE
            pageDyn.visibility = if (mode == "DYN") View.VISIBLE else View.GONE
            pagePlaceholder.visibility = if (mode == "OTHER") View.VISIBLE else View.GONE
            if (title.isNotEmpty()) tvPlaceholderTitle.text = title
        }

        findViewById<Button>(R.id.tabMix).setOnClickListener { switchPage(it as Button, "MIX") }
        findViewById<Button>(R.id.tabEq30).setOnClickListener { switchPage(it as Button, "EQ30") }
        findViewById<Button>(R.id.tabParam).setOnClickListener { switchPage(it as Button, "PARAM") }
        findViewById<Button>(R.id.tabFx).setOnClickListener { switchPage(it as Button, "FX") }
        findViewById<Button>(R.id.tabDyn).setOnClickListener { switchPage(it as Button, "DYN") }
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
            tvStatus?.text = "DSP STATUS: Stereo 30+30 • PEQ 8-Node • FX aktif"
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
            tvStatus?.text = if (isChecked) "DSP STATUS: Stereo 30+30 • PEQ 8-Node • FX aktif" else "DSP STATUS: BYPASS (NONAKTIF)"
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
                if (index == 0 || index == 1) syncPostEqToDsp()
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
