package com.custom.dspequalizer

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import kotlin.math.*

class DynamicsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    data class MbcBandData(
        val name: String,
        val freqRange: String,
        val cutoffHz: Float,
        var enabled: Boolean = true,
        var isSoftKnee: Boolean = true,
        var threshDb: Float = -18f,
        var ratio: Float = 2.0f,
        var kneeDb: Float = 6.0f,
        var attackMs: Float = 30f,
        var releaseMs: Float = 120f,
        var preGainDb: Float = 0f,
        var postGainDb: Float = 0f,
        var noiseGateDb: Float = -80f
    )

    val bands = arrayOf(
        MbcBandData("SUB", "20 - 60 Hz", 60f),
        MbcBandData("BASS", "60 - 140 Hz", 140f),
        MbcBandData("LO B", "140 - 300 Hz", 300f),
        MbcBandData("LO M", "300 - 650 Hz", 650f),
        MbcBandData("MID", "650 - 1.4k Hz", 1400f),
        MbcBandData("UP M", "1.4k - 2.8k Hz", 2800f),
        MbcBandData("PRES", "2.8k - 4.8k Hz", 4800f),
        MbcBandData("MID H", "4.8k - 8.0k Hz", 8000f),
        MbcBandData("HIGH", "8.0k - 13k Hz", 13000f),
        MbcBandData("AIR", "13k - 20k Hz", 20000f)
    )

    var isMbcEnabled = true
    var isLimiterEnabled = true
    var selectedBandIndex = 0
    var pageMode = 1 // 1 = COMP 1/2, 2 = GAIN 2/2

    var onDynamicsChangedListener: (() -> Unit)? = null

    private val colBgDark = Color.parseColor("#0D1116")
    private val colPanel = Color.parseColor("#161D24")
    private val colBoxDark = Color.parseColor("#0B0F14")
    private val colBorder = Color.parseColor("#26323D")
    private val colAccent = Color.parseColor("#38BDF8")   // Soft Ice Blue
    private val colTeal = Color.parseColor("#2DD4BF")     // Muted Teal
    private val colGreen = Color.parseColor("#34D399")
    private val colTextMain = Color.parseColor("#E2E8F0")
    private val colTextDim = Color.parseColor("#64748B")

    private lateinit var btnCompPage: Button
    private lateinit var btnGainPage: Button
    private lateinit var btnMbcMaster: Button
    private val bandButtons = mutableListOf<Button>()

    private lateinit var tvBandRangeInfo: TextView
    private lateinit var btnBandToggle: Button
    private lateinit var btnKneeType: Button
    private lateinit var tvHeadInfo: TextView

    private val cardTitles = Array(4) { TextView(context) }
    private val cardKnobs = Array(4) { DynKnobView(context, colAccent) }
    private val cardReadouts = Array(4) { TextView(context) }

    init {
        orientation = VERTICAL
        setBackgroundColor(colBgDark)
        setPadding(10, 6, 10, 6)
        buildTopHeader()
        buildBandSelectorStrip()
        buildSubInfoStrip()
        buildFourKnobsGrid()
        buildBottomLimiterBar()
        refreshUi()
    }

    private fun styleBox(v: View, fill: Int = colPanel, radius: Float = 10f, stroke: Int = colBorder) {
        v.background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            setStroke(2, stroke)
        }
    }

    private fun buildTopHeader() {
        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(2, 0, 2, 4)
            }
        }

        val tvTitle = TextView(context).apply {
            text = "DYNAMICS"
            setTextColor(colTextMain)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
        }
        val tvSub = TextView(context).apply {
            text = "  /  kendali pemrosesan audio"
            setTextColor(colTextDim)
            textSize = 10.5f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        btnCompPage = Button(context).apply {
            text = "COMP 1/2"
            textSize = 9.5f
            layoutParams = LayoutParams(150, 50).apply { setMargins(0, 0, 6, 0) }
            setPadding(4, 0, 4, 0)
            setOnClickListener { pageMode = 1; refreshUi() }
        }

        btnGainPage = Button(context).apply {
            text = "GAIN 2/2"
            textSize = 9.5f
            layoutParams = LayoutParams(150, 50)
            setPadding(4, 0, 4, 0)
            setOnClickListener { pageMode = 2; refreshUi() }
        }

        bar.addView(tvTitle); bar.addView(tvSub); bar.addView(btnCompPage); bar.addView(btnGainPage)
        addView(bar)
    }
    private fun buildBandSelectorStrip() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(2, 2, 2, 4)
            }
        }

        btnMbcMaster = Button(context).apply {
            text = "● MBC"
            textSize = 9.5f
            layoutParams = LayoutParams(115, 52).apply { setMargins(0, 0, 6, 0) }
            setPadding(4, 0, 4, 0)
            setOnClickListener {
                isMbcEnabled = !isMbcEnabled
                refreshUi()
                onDynamicsChangedListener?.invoke()
            }
        }
        row.addView(btnMbcMaster)

        for (i in bands.indices) {
            val btn = Button(context).apply {
                textSize = 8.5f
                layoutParams = LayoutParams(0, 52, 1f).apply { setMargins(2, 0, 2, 0) }
                setPadding(2, 0, 2, 0)
                setOnClickListener {
                    selectedBandIndex = i
                    refreshUi()
                }
            }
            bandButtons.add(btn)
            row.addView(btn)
        }
        addView(row)
    }

    private fun buildSubInfoStrip() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(4, 0, 4, 6)
            }
        }

        tvBandRangeInfo = TextView(context).apply {
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 9.5f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        btnBandToggle = Button(context).apply {
            textSize = 9f
            layoutParams = LayoutParams(135, 44).apply { setMargins(0, 0, 6, 0) }
            setPadding(4, 0, 4, 0)
            setOnClickListener {
                val b = bands.get(selectedBandIndex)
                b.enabled = !b.enabled
                refreshUi()
                onDynamicsChangedListener?.invoke()
            }
        }

        btnKneeType = Button(context).apply {
            textSize = 9f
            layoutParams = LayoutParams(125, 44).apply { setMargins(0, 0, 8, 0) }
            setPadding(4, 0, 4, 0)
            setOnClickListener {
                val b = bands.get(selectedBandIndex)
                b.isSoftKnee = !b.isSoftKnee
                refreshUi()
                onDynamicsChangedListener?.invoke()
            }
        }

        tvHeadInfo = TextView(context).apply {
            text = "HEAD 0.0 dB"
            setTextColor(colAccent)
            textSize = 9.5f
        }

        row.addView(tvBandRangeInfo); row.addView(btnBandToggle); row.addView(btnKneeType); row.addView(tvHeadInfo)
        addView(row)
    }

    private fun buildFourKnobsGrid() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        for (slot in 0 until 4) {
            val card = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(5, 0, 5, 0)
                }
                styleBox(this, colPanel, 14f, colBorder)
                setPadding(10, 8, 10, 8)
            }

            val tvT = TextView(context).apply {
                setTextColor(colTextMain)
                textSize = 10.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            cardTitles.set(slot, tvT)

            val needleCol = if (slot % 2 == 0) colAccent else colTeal
            val knob = DynKnobView(context, needleCol).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    setMargins(0, 4, 0, 4)
                }
                onNormChange = { norm -> handleKnobChange(slot, norm) }
            }
            cardKnobs.set(slot, knob)

            val tvR = TextView(context).apply {
                setTextColor(colTextMain)
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                styleBox(this, colBoxDark, 8f, colBorder)
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 48)
            }
            cardReadouts.set(slot, tvR)

            card.addView(tvT); card.addView(knob); card.addView(tvR)
            row.addView(card)
        }
        addView(row)
    }

    private fun buildBottomLimiterBar() {
        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(4, 6, 4, 0)
            }
        }

        val btnLimiter = Button(context).apply {
            text = "● LIMITER"
            textSize = 9.5f
            setTextColor(colGreen)
            styleBox(this, colPanel, 10f, colGreen)
            layoutParams = LayoutParams(170, 46)
            setPadding(6, 0, 6, 0)
            setOnClickListener {
                isLimiterEnabled = !isLimiterEnabled
                text = if (isLimiterEnabled) "● LIMITER" else "○ LIMITER"
                setTextColor(if (isLimiterEnabled) colGreen else colTextDim)
                styleBox(this, colPanel, 10f, if (isLimiterEnabled) colGreen else colBorder)
                onDynamicsChangedListener?.invoke()
            }
        }

        val tvNote = TextView(context).apply {
            text = "  Master Brickwall Limiter melindungi speaker dari distorsi puncak sinyal"
            setTextColor(colTextDim)
            textSize = 9.5f
        }

        bar.addView(btnLimiter); bar.addView(tvNote)
        addView(bar)
    }
    private fun handleKnobChange(slot: Int, norm: Float) {
        val b = bands.get(selectedBandIndex)
        if (pageMode == 1) {
            when (slot) {
                0 -> b.threshDb = (-48f + norm * 48f).roundToInt().toFloat() // -48 .. 0 dBFS
                1 -> b.ratio = ((1f + norm * 19f) * 10f).roundToInt() / 10f  // 1.0 .. 20.0:1
                2 -> b.kneeDb = ((norm * 24f) * 10f).roundToInt() / 10f      // 0.0 .. 24.0 dB
                3 -> b.attackMs = (1f + norm * 199f).roundToInt().toFloat()  // 1 .. 200 ms
            }
        } else {
            when (slot) {
                0 -> b.releaseMs = (10f + norm * 490f).roundToInt().toFloat() // 10 .. 500 ms
                1 -> b.preGainDb = (((norm - 0.5f) * 24f) * 10f).roundToInt() / 10f
                2 -> b.postGainDb = (((norm - 0.5f) * 24f) * 10f).roundToInt() / 10f
                3 -> b.noiseGateDb = (-90f + norm * 60f).roundToInt().toFloat()
            }
        }
        refreshUi(skipSlot = slot)
        onDynamicsChangedListener?.invoke()
    }

    private fun refreshUi(skipSlot: Int = -1) {
        styleBox(btnCompPage, if (pageMode == 1) Color.parseColor("#1E293B") else colPanel, 8f, if (pageMode == 1) colAccent else colBorder)
        btnCompPage.setTextColor(if (pageMode == 1) colAccent else colTextDim)

        styleBox(btnGainPage, if (pageMode == 2) Color.parseColor("#1E293B") else colPanel, 8f, if (pageMode == 2) colAccent else colBorder)
        btnGainPage.setTextColor(if (pageMode == 2) colAccent else colTextDim)

        btnMbcMaster.text = if (isMbcEnabled) "● MBC" else "○ MBC"
        btnMbcMaster.setTextColor(if (isMbcEnabled) colGreen else colTextDim)
        styleBox(btnMbcMaster, colPanel, 8f, if (isMbcEnabled) colGreen else colBorder)

        for (i in bands.indices) {
            val bd = bands.get(i)
            val btn = bandButtons.get(i)
            val isSel = (i == selectedBandIndex)
            val dot = if (bd.enabled) "•" else ""
            btn.text = "${bd.name} $dot"
            btn.setTextColor(
                when {
                    isSel -> colAccent
                    bd.enabled -> colTextMain
                    else -> colTextDim
                }
            )
            styleBox(btn, if (isSel) Color.parseColor("#1E293B") else colPanel, 8f, if (isSel) colAccent else colBorder)
        }

        val cur = bands.get(selectedBandIndex)
        tvBandRangeInfo.text = "${cur.name} • ${cur.freqRange} • ${if (cur.enabled) "ON" else "BYPASS"}"

        btnBandToggle.text = if (cur.enabled) "● BAND" else "○ BAND"
        btnBandToggle.setTextColor(if (cur.enabled) colGreen else colTextDim)
        styleBox(btnBandToggle, colPanel, 8f, if (cur.enabled) colGreen else colBorder)

        btnKneeType.text = if (cur.isSoftKnee) "● SOFT" else "● HARD"
        btnKneeType.setTextColor(if (cur.isSoftKnee) colTeal else colAccent)
        styleBox(btnKneeType, colPanel, 8f, colBorder)

        if (pageMode == 1) {
            cardTitles.get(0).text = "THRESH"
            cardReadouts.get(0).text = String.format("%.0f dBFS", cur.threshDb)
            if (skipSlot != 0) cardKnobs.get(0).setNormalized((cur.threshDb + 48f) / 48f)

            cardTitles.get(1).text = "RATIO"
            cardReadouts.get(1).text = String.format("%.1f:1", cur.ratio)
            if (skipSlot != 1) cardKnobs.get(1).setNormalized((cur.ratio - 1f) / 19f)

            cardTitles.get(2).text = "KNEE"
            cardReadouts.get(2).text = String.format("%.1f dB", cur.kneeDb)
            if (skipSlot != 2) cardKnobs.get(2).setNormalized(cur.kneeDb / 24f)

            cardTitles.get(3).text = "ATTACK"
            cardReadouts.get(3).text = String.format("%.0f ms", cur.attackMs)
            if (skipSlot != 3) cardKnobs.get(3).setNormalized((cur.attackMs - 1f) / 199f)
        } else {
            cardTitles.get(0).text = "RELEASE"
            cardReadouts.get(0).text = String.format("%.0f ms", cur.releaseMs)
            if (skipSlot != 0) cardKnobs.get(0).setNormalized((cur.releaseMs - 10f) / 490f)

            cardTitles.get(1).text = "PRE GAIN"
            cardReadouts.get(1).text = String.format("%+.1f dB", cur.preGainDb)
            if (skipSlot != 1) cardKnobs.get(1).setNormalized((cur.preGainDb / 24f) + 0.5f)

            cardTitles.get(2).text = "POST GAIN"
            cardReadouts.get(2).text = String.format("%+.1f dB", cur.postGainDb)
            if (skipSlot != 2) cardKnobs.get(2).setNormalized((cur.postGainDb / 24f) + 0.5f)

            cardTitles.get(3).text = "NOISE GATE"
            cardReadouts.get(3).text = String.format("%.0f dBFS", cur.noiseGateDb)
            if (skipSlot != 3) cardKnobs.get(3).setNormalized((cur.noiseGateDb + 90f) / 60f)
        }
    }

    inner class DynKnobView(context: Context, private val needleColor: Int) : View(context) {
        private var norm = 0.5f
        var onNormChange: ((Float) -> Unit)? = null

        private val tickDim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#475569"); strokeWidth = 2.8f; strokeCap = Paint.Cap.ROUND }
        private val tickActive = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = needleColor; strokeWidth = 3.2f; strokeCap = Paint.Cap.ROUND }
        private val ringBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#090D12"); style = Paint.Style.FILL }
        private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#334155"); strokeWidth = 2.8f; style = Paint.Style.STROKE }
        private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = needleColor; strokeWidth = 6.5f; strokeCap = Paint.Cap.ROUND }

        private var lastY = 0f

        fun setNormalized(v: Float) {
            norm = v.coerceIn(0f, 1f)
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastY = event.y; parent?.requestDisallowInterceptTouchEvent(true); return true }
                MotionEvent.ACTION_MOVE -> {
                    val dy = lastY - event.y; lastY = event.y
                    norm = (norm + dy / 240f).coerceIn(0f, 1f)
                    invalidate(); onNormChange?.invoke(norm); return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = w / 2f; val cy = h / 2f
            val maxR = (min(w, h) / 2f) - 4f
            if (maxR <= 18f) return

            for (t in 0..12) {
                val frac = t.toFloat() / 12f
                val rad = Math.toRadians((135f + frac * 270f).toDouble())
                val p = if (frac <= norm + 0.01f && norm > 0f) tickActive else tickDim
                canvas.drawLine(
                    cx + cos(rad).toFloat() * (maxR - 12f), cy + sin(rad).toFloat() * (maxR - 12f),
                    cx + cos(rad).toFloat() * maxR, cy + sin(rad).toFloat() * maxR, p
                )
            }

            val knobR = maxR - 17f
            canvas.drawCircle(cx, cy, knobR, ringBg)
            capPaint.shader = LinearGradient(cx - knobR, cy - knobR, cx + knobR, cy + knobR, Color.parseColor("#2B3642"), Color.parseColor("#11161D"), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, knobR - 4f, capPaint)
            canvas.drawCircle(cx, cy, knobR - 4f, rimPaint)

            val nRad = Math.toRadians((135f + norm * 270f).toDouble())
            canvas.drawLine(
                cx + cos(nRad).toFloat() * (knobR * 0.25f), cy + sin(nRad).toFloat() * (knobR * 0.25f),
                cx + cos(nRad).toFloat() * (knobR - 9f), cy + sin(nRad).toFloat() * (knobR - 9f), needlePaint
            )
        }
    }
}
