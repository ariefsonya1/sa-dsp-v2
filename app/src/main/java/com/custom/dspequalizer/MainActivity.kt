package com.custom.dspequalizer

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private var dsp: DynamicsProcessing? = null
    private var audioVisualizer: Visualizer? = null
    private var isVuActive = true
    private var isHeadroomActive = false
    private var isLimiterActive = true
    private var limiterThresholdDb = -0.5f
    private var limiterReleaseMs = 60.0f

    private val vuBars = mutableListOf<ProgressBar>()
    private val faderValues = FloatArray(7)
    private val smoothVuLevels = FloatArray(7)
    private val handler = Handler(Looper.getMainLooper())
    private var tvStatus: TextView? = null

    private lateinit var pageMixBar: View
    private lateinit var pageMixFaders: View
    private lateinit var pageEq30: GraphicEq30View
    private lateinit var pageParam: ParametricEqView
    private lateinit var pageFx: FxRackView
    private lateinit var pageDyn: DynamicsView
    private lateinit var pageSaved: SavedPresetView
    private lateinit var pageSignal: SignalRoutingView
    private lateinit var pageCon: ConsoleConfigView

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

        pageDyn = DynamicsView(this).apply {
            layoutParams = fullParams
            visibility = View.GONE
            onDynamicsChangedListener = { syncMbcToDsp() }
        }
        rootLayout.addView(pageDyn)

        pageSaved = SavedPresetView(this).apply {
            layoutParams = fullParams
            visibility = View.GONE
            onSavePresetRequest = { exportCurrentStateToJson() }
            onLoadPresetRequest = { json -> importStateFromJson(json) }
        }
        rootLayout.addView(pageSaved)

        pageSignal = SignalRoutingView(this).apply {
            layoutParams = fullParams
            visibility = View.GONE
        }
        rootLayout.addView(pageSignal)

        pageCon = ConsoleConfigView(this).apply {
            layoutParams = fullParams
            visibility = View.GONE
            onConsoleChannelChanged = { idx, norm ->
                when (idx) {
                    0 -> { faderValues.set(0, norm); syncPostEqToDsp() }
                    1 -> { faderValues.set(1, norm); syncPostEqToDsp() }
                    2 -> { faderValues.set(2, (norm - 0.5f) * 30f); applyAudioParameters() }
                    3 -> { faderValues.set(3, (norm - 0.5f) * 30f); applyAudioParameters() }
                    4 -> { faderValues.set(4, (norm - 0.5f) * 30f); applyAudioParameters() }
                    5 -> { faderValues.set(5, (norm - 0.5f) * 30f); applyAudioParameters() }
                    6 -> { pageEq30.leftVolDb = (norm - 0.5f) * 30f; syncPostEqToDsp() }
                    7 -> { pageEq30.rightVolDb = (norm - 0.5f) * 30f; syncPostEqToDsp() }
                    8 -> { faderValues.set(6, (norm - 0.5f) * 30f); applyAudioParameters() }
                }
            }
            onFactoryResetAllListener = { resetAllConsoleModules() }
        }
        rootLayout.addView(pageCon)
    }

    private fun resetAllConsoleModules() {
        pageEq30.leftGains.fill(0f)
        pageEq30.rightGains.fill(0f)
        pageEq30.leftVolDb = 0f
        pageEq30.rightVolDb = 0f
        pageEq30.invalidate()

        pageParam.nodes.clear()
        pageParam.nodes.addAll(listOf(
            ParametricEqView.EqNode(45f, 0f, 1.0f),
            ParametricEqView.EqNode(120f, 0f, 1.0f),
            ParametricEqView.EqNode(400f, 0f, 1.0f),
            ParametricEqView.EqNode(1200f, 0f, 1.0f),
            ParametricEqView.EqNode(3800f, 0f, 1.0f),
            ParametricEqView.EqNode(10000f, 0f, 1.0f)
        ))
        pageParam.selectedIndex = 0

        pageFx.subsonicHz = 0f
        pageFx.subBassDb = 0f
        pageFx.widenerPct = 0
        pageFx.tubeDriveDb = 0f

        syncPostEqToDsp()
        syncMbcToDsp()
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

    private fun syncMbcToDsp() {
        val engine = dsp ?: return
        try {
            for (i in pageDyn.bands.indices) {
                val b = pageDyn.bands.get(i)
                val mbcBand = DynamicsProcessing.MbcBand(
                    pageDyn.isMbcEnabled && b.enabled,
                    b.cutoffHz, b.attackMs, b.releaseMs, b.ratio,
                    b.threshDb, b.kneeDb, b.noiseGateDb, 1.0f, b.preGainDb, b.postGainDb
                )
                engine.setMbcBandAllChannelsTo(i, mbcBand)
            }
            isLimiterActive = pageDyn.isLimiterEnabled
            updateLimiter()
        } catch (_: Exception) {}
    }
    private fun exportCurrentStateToJson(): JSONObject {
        val obj = JSONObject()
        val eqL = JSONArray()
        val eqR = JSONArray()
        for (i in 0 until 30) {
            eqL.put(pageEq30.leftGains.get(i).toDouble())
            eqR.put(pageEq30.rightGains.get(i).toDouble())
        }
        obj.put("eq30_L", eqL)
        obj.put("eq30_R", eqR)

        val peqArr = JSONArray()
        for (n in pageParam.nodes) {
            val item = JSONObject()
            item.put("freq", n.freq.toDouble())
            item.put("gain", n.gain.toDouble())
            item.put("q", n.q.toDouble())
            item.put("type", n.type.name)
            peqArr.put(item)
        }
        obj.put("peq_nodes", peqArr)
        obj.put("fx_subsonic", pageFx.subsonicHz.toDouble())
        obj.put("fx_subBass", pageFx.subBassDb.toDouble())
        obj.put("fx_widener", pageFx.widenerPct)
        obj.put("fx_tubeDrive", pageFx.tubeDriveDb.toDouble())
        return obj
    }

    private fun importStateFromJson(obj: JSONObject) {
        val eqL = obj.optJSONArray("eq30_L")
        val eqR = obj.optJSONArray("eq30_R")
        if (eqL != null && eqR != null) {
            for (i in 0 until minOf(30, eqL.length())) {
                pageEq30.leftGains.set(i, eqL.getDouble(i).toFloat())
                pageEq30.rightGains.set(i, eqR.getDouble(i).toFloat())
            }
            pageEq30.invalidate()
        }
        val peqArr = obj.optJSONArray("peq_nodes")
        if (peqArr != null && peqArr.length() > 0) {
            pageParam.nodes.clear()
            for (i in 0 until peqArr.length()) {
                val it = peqArr.getJSONObject(i)
                val t = try {
                    ParametricEqView.FilterType.valueOf(it.getString("type"))
                } catch (_: Exception) {
                    ParametricEqView.FilterType.BELL
                }
                pageParam.nodes.add(
                    ParametricEqView.EqNode(
                        it.getDouble("freq").toFloat(),
                        it.getDouble("gain").toFloat(),
                        it.getDouble("q").toFloat(),
                        t
                    )
                )
            }
            pageParam.selectedIndex = 0
        }
        pageFx.subsonicHz = obj.optDouble("fx_subsonic", 0.0).toFloat()
        pageFx.subBassDb = obj.optDouble("fx_subBass", 0.0).toFloat()
        pageFx.widenerPct = obj.optInt("fx_widener", 0)
        pageFx.tubeDriveDb = obj.optDouble("fx_tubeDrive", 0.0).toFloat()
        syncPostEqToDsp()
    }

    private fun setupNavigationTabs() {
        findViewById<Button>(R.id.tabSpace).visibility = View.GONE

        val tabs = listOf(
            findViewById<Button>(R.id.tabMix), findViewById<Button>(R.id.tabEq30), findViewById<Button>(R.id.tabParam),
            findViewById<Button>(R.id.tabFx), findViewById<Button>(R.id.tabDyn),
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

        fun switchPage(btn: Button, mode: String) {
            tabs.forEach { applyTabStyle(it, it == btn) }
            pageMixBar.visibility = if (mode == "MIX") View.VISIBLE else View.GONE
            pageMixFaders.visibility = if (mode == "MIX") View.VISIBLE else View.GONE
            pageEq30.visibility = if (mode == "EQ30") View.VISIBLE else View.GONE
            pageParam.visibility = if (mode == "PARAM") View.VISIBLE else View.GONE
            pageFx.visibility = if (mode == "FX") View.VISIBLE else View.GONE
            pageDyn.visibility = if (mode == "DYN") View.VISIBLE else View.GONE
            pageSaved.visibility = if (mode == "SAVED") View.VISIBLE else View.GONE
            pageSignal.visibility = if (mode == "SIGNAL") View.VISIBLE else View.GONE
            pageCon.visibility = if (mode == "CON") View.VISIBLE else View.GONE
            if (mode == "SIGNAL") pageSignal.scanAudioRoutes()
        }

        findViewById<Button>(R.id.tabMix).setOnClickListener { switchPage(it as Button, "MIX") }
        findViewById<Button>(R.id.tabEq30).setOnClickListener { switchPage(it as Button, "EQ30") }
        findViewById<Button>(R.id.tabParam).setOnClickListener { switchPage(it as Button, "PARAM") }
        findViewById<Button>(R.id.tabFx).setOnClickListener { switchPage(it as Button, "FX") }
        findViewById<Button>(R.id.tabDyn).setOnClickListener { switchPage(it as Button, "DYN") }
        findViewById<Button>(R.id.tabSaved).setOnClickListener { switchPage(it as Button, "SAVED") }
        findViewById<Button>(R.id.tabSignal).setOnClickListener { switchPage(it as Button, "SIGNAL") }
        findViewById<Button>(R.id.tabCon).setOnClickListener { switchPage(it as Button, "CON") }
    }

    private fun initDspEngine() {
        try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 1,
                true, preFreqs.size, true, preFreqs.size, true, eq30Freqs.size, true
            ).build()
            dsp = DynamicsProcessing(0, 0, config).apply { enabled = true }
            updateLimiter()
            syncMbcToDsp()
            tvStatus?.text = "DSP STATUS: Stereo 30+30 • PEQ • FX • MBC • Console aktif"
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
            tvStatus?.text = if (isChecked) "DSP STATUS: Stereo 30+30 • PEQ • FX • MBC • Console aktif" else "DSP STATUS: BYPASS (NONAKTIF)"
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
            if (!isChecked) {
                smoothVuLevels.fill(0f)
                vuBars.forEach { it.progress = 0 }
            }
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
    private fun setupChannel(
        containerId: Int,
        index: Int,
        title: String,
        subtitle: String,
        isSend: Boolean,
        defaultProgress: Int
    ) {
        val container = findViewById<View>(containerId) ?: return
        val seekBar = findViewByIdInHierarchy<SeekBar>(container)
        val progressBar = findViewByIdInHierarchy<ProgressBar>(container, excludeSeekBar = true)
        val texts = findAllTextViews(container)

        if (texts.isNotEmpty()) texts.get(0).text = title
        if (texts.size > 1) texts.get(1).text = subtitle

        if (progressBar != null) {
            progressBar.max = 100
            progressBar.progress = 0
            vuBars.add(progressBar)
        }

        faderValues.set(index, if (isSend) defaultProgress / 100f else ((defaultProgress - 50) / 50f) * 15f)
        if (texts.size > 2) {
            texts.get(2).text = if (isSend) "${defaultProgress}%" else String.format("%.1f dB", faderValues.get(index))
        }

        seekBar?.apply {
            max = 100
            progress = defaultProgress
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (isSend) {
                        val norm = progress / 100f
                        faderValues.set(index, norm)
                        if (texts.size > 2) texts.get(2).text = "${progress}%"
                        syncPostEqToDsp()
                        applyAudioParameters()
                    } else {
                        val db = ((progress - 50) / 50f) * 15f
                        faderValues.set(index, db)
                        if (texts.size > 2) texts.get(2).text = String.format("%.1f dB", db)
                        applyAudioParameters()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }

    private inline fun <reified T : View> findViewByIdInHierarchy(
        root: View,
        excludeSeekBar: Boolean = false
    ): T? {
        if (root is T) {
            if (excludeSeekBar && root is SeekBar) return null
            return root
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findViewByIdInHierarchy<T>(root.getChildAt(i), excludeSeekBar)
                if (found != null) return found
            }
        }
        return null
    }

    private fun findAllTextViews(root: View): List<TextView> {
        val result = mutableListOf<TextView>()
        if (root is TextView) result.add(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                result.addAll(findAllTextViews(root.getChildAt(i)))
            }
        }
        return result
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        try {
            audioVisualizer?.release()
            audioVisualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange().get(0)

                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (!isVuActive || waveform == null || vuBars.isEmpty()) return

                        var sum = 0.0
                        for (b in waveform) {
                            val sample = ((b.toInt() and 0xFF) - 128) / 128.0
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / waveform.size)
                        val db = if (rms > 0.0001) 20.0 * log10(rms) else -60.0
                        val masterNorm = ((db + 42.0) / 38.0).toFloat().coerceIn(0f, 1f)

                        updateVuChannel(0, masterNorm * (0.4f + faderValues.get(0) * 0.6f))
                        updateVuChannel(1, masterNorm * (0.4f + faderValues.get(1) * 0.6f))
                        val masterBoost = ((faderValues.get(6) + 15f) / 15f).coerceIn(0.3f, 1.4f)
                        updateVuChannel(6, (masterNorm * masterBoost).coerceIn(0f, 1f))
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (!isVuActive || fft == null || vuBars.size < 6) return

                        val n = fft.size / 2
                        var bassSum = 0.0
                        var midSum = 0.0
                        var highSum = 0.0

                        for (k in 1 until n) {
                            val re = fft.get(2 * k).toDouble()
                            val im = fft.get(2 * k + 1).toDouble()
                            val mag = hypot(re, im) / 128.0

                            when {
                                k <= n / 8 -> bassSum += mag
                                k <= n / 2 -> midSum += mag
                                else -> highSum += mag
                            }
                        }

                        val bassNorm = toDbNorm(bassSum / (n / 8.0), 36.0)
                        val midNorm = toDbNorm(midSum / (n * 3.0 / 8.0), 42.0)
                        val highNorm = toDbNorm(highSum / (n / 2.0), 48.0)

                        updateVuChannel(2, bassNorm)
                        updateVuChannel(3, midNorm)
                        updateVuChannel(4, highNorm)
                        updateVuChannel(5, (bassNorm * 1.15f).coerceIn(0f, 1f))
                    }
                }, Visualizer.getMaxCaptureRate(), true, true)

                enabled = true
            }
        } catch (e: Exception) {
            tvStatus?.text = "VU INFO: Izin audio ditolak / diblokir sistem"
        }
    }

    private fun toDbNorm(linearMag: Double, floorDb: Double): Float {
        val db = if (linearMag > 0.0001) 20.0 * log10(linearMag) else -60.0
        return ((db + floorDb) / (floorDb - 2.0)).toFloat().coerceIn(0f, 1f)
    }

    private fun updateVuChannel(index: Int, targetLevel: Float) {
        if (index !in vuBars.indices) return
        val clamped = targetLevel.coerceIn(0f, 1f)
        smoothVuLevels.set(index, if (clamped > smoothVuLevels.get(index)) {
            clamped
        } else {
            (smoothVuLevels.get(index) * 0.78f).coerceAtLeast(clamped)
        })
        val progressVal = (smoothVuLevels.get(index) * 100).toInt()
        runOnUiThread {
            if (isVuActive) vuBars.get(index).progress = progressVal
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults.get(0) == PackageManager.PERMISSION_GRANTED) {
            startVuAnimation()
        }
    }

    private fun showErrorDialog(msg: String) {
        AlertDialog.Builder(this).setTitle("Info Error").setMessage(msg).setPositiveButton("Tutup", null).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            audioVisualizer?.enabled = false
            audioVisualizer?.release()
        } catch (_: Exception) {}
        try { dsp?.release() } catch (_: Exception) {}
    }
}
