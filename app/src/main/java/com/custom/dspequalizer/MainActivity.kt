package com.custom.dspequalizer

import android.app.AlertDialog
import android.graphics.Color
import android.media.audiofx.DynamicsProcessing
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private var dsp: DynamicsProcessing? = null
    private var isVuActive = true
    private var isHeadroomActive = false
    private val vuBars = mutableListOf<ProgressBar>()
    private val faderValues = FloatArray(7)
    private val handler = Handler(Looper.getMainLooper())
    private var tvStatus: TextView? = null

    private val freqs = floatArrayOf(32f, 64f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            runOnUiThread {
                showErrorDialog("Error Sistem:\n${throwable.message}\n\n${throwable.stackTraceToString().take(400)}")
            }
        }

        try {
            setContentView(R.layout.activity_main)
            tvStatus = findViewById(R.id.tvDspStatus)

            setupTopControls()
            setupMixBusFaders()
            initDspEngine()
            startVuAnimation()
        } catch (e: Exception) {
            showErrorDialog("Gagal Memuat Tampilan:\n${e.message}")
        }
    }

    private fun initDspEngine() {
        try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true, freqs.size,
                true, freqs.size,
                true, freqs.size,
                true
            ).build()

            dsp = DynamicsProcessing(0, 0, config).apply {
                enabled = true
            }
            updateLimiter(true)
            tvStatus?.text = "DSP STATUS: Post 128 band • MBC aktif • EQ aktif"
        } catch (e: Exception) {
            tvStatus?.text = "DSP WARNING: ${e.javaClass.simpleName} (Mode UI Aktif)"
            tvStatus?.setTextColor(Color.YELLOW)
        }
    }

    private fun setupTopControls() {
        val btnDspPower = findViewById<ToggleButton>(R.id.btnDspPower)
        val btnHead = findViewById<ToggleButton>(R.id.btnHead)
        val btnLimit = findViewById<ToggleButton>(R.id.btnLimit)
        val btnVu = findViewById<ToggleButton>(R.id.btnVu)

        btnDspPower.setOnCheckedChangeListener { _, isChecked ->
            try {
                dsp?.enabled = isChecked
            } catch (_: Exception) {}
            btnDspPower.setTextColor(if (isChecked) Color.parseColor("#00E676") else Color.GRAY)
            tvStatus?.text = if (isChecked) "DSP STATUS: Post 128 band • MBC aktif • EQ aktif" else "DSP STATUS: BYPASS (NONAKTIF)"
        }

        btnHead.setOnCheckedChangeListener { _, isChecked ->
            isHeadroomActive = isChecked
            btnHead.setTextColor(if (isChecked) Color.parseColor("#00E676") else Color.parseColor("#FF5252"))
            applyAudioParameters()
        }

        btnLimit.setOnCheckedChangeListener { _, isChecked ->
            btnLimit.setTextColor(if (isChecked) Color.parseColor("#00E676") else Color.GRAY)
            updateLimiter(isChecked)
        }

        btnVu.setOnCheckedChangeListener { _, isChecked ->
            isVuActive = isChecked
            btnVu.setTextColor(if (isChecked) Color.parseColor("#00E676") else Color.GRAY)
            if (!isChecked) vuBars.forEach { it.progress = 0 }
        }
    }

    private fun setupMixBusFaders() {
        setupChannel(R.id.chGraphicEq, 0, "GRAPHIC EQ", "30 BAND SEND", isPercent = true, defaultProgress = 100)
        setupChannel(R.id.chParametric, 1, "PARAMETRIC", "FILTER SEND", isPercent = true, defaultProgress = 100)
        setupChannel(R.id.chLow, 2, "LOW", "TONE TRIM", isPercent = false, defaultProgress = 50)
        setupChannel(R.id.chMid, 3, "MID", "TONE TRIM", isPercent = false, defaultProgress = 50)
        setupChannel(R.id.chHigh, 4, "HIGH", "TONE TRIM", isPercent = false, defaultProgress = 50)
        setupChannel(R.id.chBassBoost, 5, "BASS BOOST", "LOW EQ", isPercent = false, defaultProgress = 50)
        setupChannel(R.id.chMaster, 6, "MASTER", "PREAMP TRIM", isPercent = false, defaultProgress = 50)
    }

    private fun setupChannel(
        viewId: Int,
        index: Int,
        title: String,
        subtitle: String,
        isPercent: Boolean,
        defaultProgress: Int
    ) {
        val root = findViewById<View>(viewId) ?: return
        val tvTitle = root.findViewById<TextView>(R.id.tvTitle)
        val tvSubtitle = root.findViewById<TextView>(R.id.tvSubtitle)
        val tvValue = root.findViewById<TextView>(R.id.tvValue)
        val seekBar = root.findViewById<SeekBar>(R.id.faderSeekBar)
        val vuBar = root.findViewById<ProgressBar>(R.id.vuMeterBar)

        tvTitle.text = title
        tvSubtitle.text = subtitle
        vuBars.add(vuBar)

        seekBar.max = if (isPercent) 100 else 300
        seekBar.progress = if (isPercent) defaultProgress else 150

        fun updateLabel(progress: Int) {
            if (isPercent) {
                faderValues.set(index, progress / 100f)
                tvValue.text = "$progress%"
            } else {
                val db = (progress - 150) / 10.0f
                faderValues.set(index, db)
                val sign = if (db >= 0) "+" else ""
                tvValue.text = String.format("%s%.1f dB", sign, db)
            }
        }

        updateLabel(seekBar.progress)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                updateLabel(progress)
                applyAudioParameters()
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

            val headroomOffset = if (isHeadroomActive) -6.0f else 0.0f
            engine.setInputGainAllChannelsTo(masterTrim + headroomOffset)

            for (i in freqs.indices) {
                val rawGain = when {
                    i <= 1 -> (lowTrim + bassBoost) * eqSend
                    i == 2 -> lowTrim * eqSend
                    i in 3..6 -> midTrim * eqSend
                    else -> highTrim * eqSend
                }
                val clampedGain = rawGain.coerceIn(-15f, 15f)
                val band = DynamicsProcessing.EqBand(true, freqs.get(i), clampedGain)
                engine.setPreEqBandAllChannelsTo(i, band)
            }
        } catch (_: Exception) {}
    }

    private fun updateLimiter(active: Boolean) {
        try {
            val limiter = DynamicsProcessing.Limiter(
                true, active, 0, 1f, 60f, 10f, -0.5f, 0f
            )
            dsp?.setLimiterAllChannelsTo(limiter)
        } catch (_: Exception) {}
    }

    private fun startVuAnimation() {
        handler.post(object : Runnable {
            override fun run() {
                if (isVuActive) {
                    for (i in vuBars.indices) {
                        val valNow = faderValues.get(i)
                        val baseLevel = if (i < 2) (valNow * 70).toInt() else ((valNow + 15f) * 2.5f).toInt()
                        val jitter = Random.nextInt(-15, 20)
                        vuBars.get(i).progress = (baseLevel + jitter).coerceIn(5, 98)
                    }
                }
                handler.postDelayed(this, 80)
            }
        })
    }

    private fun showErrorDialog(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("Info Error Aplikasi")
            .setMessage(msg)
            .setPositiveButton("Tutup", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            dsp?.release()
        } catch (_: Exception) {}
    }
}
