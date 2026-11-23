package com.custom.dspequalizer

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import kotlin.math.roundToInt

class GraphicEq30View @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    val freqs = floatArrayOf(
        20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f,
        200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f,
        2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f
    )
    private val labels = arrayOf(
        "20", "25", "31", "40", "50", "63", "80", "100", "125", "160",
        "200", "250", "315", "400", "500", "630", "800", "1k", "1.2k", "1.6k",
        "2k", "2.5k", "3.1k", "4k", "5k", "6.3k", "8k", "10k", "12.5k", "16k"
    )

    val leftGains = FloatArray(30)
    val rightGains = FloatArray(30)
    var leftVolDb = 0f
    var rightVolDb = 0f
    var isLinked = true

    private var selectedChannel = 0 // 0 = L, 1 = R
    private var selectedBand = 8    // Default 125/160 Hz

    var onEq30ChangedListener: (() -> Unit)? = null

    private val colBgDark = Color.parseColor("#0D1116")
    private val colPanel = Color.parseColor("#161D24")
    private val colBorder = Color.parseColor("#26323D")
    private val colAccentL = Color.parseColor("#38BDF8")   // Soft Ice Blue (Channel L)
    private val colAccentR = Color.parseColor("#2DD4BF")   // Muted Teal (Channel R)
    private val colTextMain = Color.parseColor("#E2E8F0")
    private val colTextDim = Color.parseColor("#64748B")

    private lateinit var bankL: BandBankCanvas
    private lateinit var bankR: BandBankCanvas
    private lateinit var faderVolL: ConsoleFaderView
    private lateinit var faderVolR: ConsoleFaderView
    private lateinit var faderSelectHz: ConsoleFaderView
    private lateinit var faderFineGain: ConsoleFaderView

    init {
        orientation = VERTICAL
        setBackgroundColor(colBgDark)
        setPadding(8, 4, 8, 6)
        buildTopBar()
        buildMainConsoleBody()
        syncSideFadersFromSelection()
    }

    private fun styleBox(v: View, strokeColor: Int = colBorder) {
        v.background = GradientDrawable().apply {
            setColor(colPanel)
            cornerRadius = 10f
            setStroke(2, strokeColor)
        }
    }

    private fun buildTopBar() {
        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(2, 0, 2, 4)
            }
        }

        val tvTitle = TextView(context).apply {
            text = "STEREO GRAPHIC • 30 + 30"
            setTextColor(colTextDim)
            textSize = 10f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnLink = Button(context).apply {
            text = "● LINK L/R"
            textSize = 10f
            setTextColor(Color.parseColor("#34D399"))
            styleBox(this)
            layoutParams = LayoutParams(190, 56).apply { setMargins(0, 0, 8, 0) }
            setPadding(8, 0, 8, 0)
            setOnClickListener {
                isLinked = !isLinked
                if (isLinked) {
                    setTextColor(Color.parseColor("#34D399"))
                    text = "● LINK L/R"
                    for (i in 0 until 30) rightGains.set(i, leftGains.get(i))
                    rightVolDb = leftVolDb
                    faderVolR.setNormalized((rightVolDb + 15f) / 30f)
                    bankR.invalidate()
                    notifyChange()
                } else {
                    setTextColor(colTextDim)
                    text = "○ SPLIT L/R"
                }
            }
        }

        val btnReset = Button(context).apply {
            text = "RESET FLAT"
            textSize = 10f
            setTextColor(colAccentL)
            styleBox(this)
            layoutParams = LayoutParams(170, 56)
            setPadding(8, 0, 8, 0)
            setOnClickListener {
                leftGains.fill(0f)
                rightGains.fill(0f)
                leftVolDb = 0f
                rightVolDb = 0f
                faderVolL.setNormalized(0.5f)
                faderVolR.setNormalized(0.5f)
                bankL.invalidate()
                bankR.invalidate()
                syncSideFadersFromSelection()
                notifyChange()
                Toast.makeText(context, "Stereo 30-Band Reset Flat", Toast.LENGTH_SHORT).show()
            }
        }

        bar.addView(tvTitle)
        bar.addView(btnLink)
        bar.addView(btnReset)
        addView(bar)
    }
    private fun buildMainConsoleBody() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // ZONA 1 (KIRI): L VOL & R VOL
        faderVolL = ConsoleFaderView(context, "L VOL", "LEFT", colAccentL, showLed = true).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 0.72f).apply { setMargins(0, 0, 4, 0) }
            onNormalizedChanged = { norm ->
                leftVolDb = ((norm - 0.5f) * 30f * 10f).roundToInt() / 10f
                valueText = String.format("%+.1f dB", leftVolDb)
                if (isLinked) {
                    rightVolDb = leftVolDb
                    faderVolR.setNormalized(norm)
                    faderVolR.valueText = valueText
                }
                notifyChange()
            }
        }
        faderVolL.valueText = "+0.0 dB"

        faderVolR = ConsoleFaderView(context, "R VOL", "RIGHT", colAccentR, showLed = true).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 0.72f).apply { setMargins(0, 0, 6, 0) }
            onNormalizedChanged = { norm ->
                rightVolDb = ((norm - 0.5f) * 30f * 10f).roundToInt() / 10f
                valueText = String.format("%+.1f dB", rightVolDb)
                if (isLinked) {
                    leftVolDb = rightVolDb
                    faderVolL.setNormalized(norm)
                    faderVolL.valueText = valueText
                }
                notifyChange()
            }
        }
        faderVolR.valueText = "+0.0 dB"

        // ZONA 2 (TENGAH): L 30 BAND (ATAS) & R 30 BAND (BAWAH)
        val centerCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 4.2f)
        }

        bankL = BandBankCanvas(context, "L", leftGains, colAccentL, 0).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(0, 0, 0, 5) }
            styleBox(this)
        }
        bankR = BandBankCanvas(context, "R", rightGains, colAccentR, 1).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            styleBox(this)
        }
        centerCol.addView(bankL)
        centerCol.addView(bankR)

        // ZONA 3 (KANAN): HZ SELECTOR & FINE GAIN FADER
        faderSelectHz = ConsoleFaderView(context, "HZ", "L9", colAccentL, showLed = false).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 0.72f).apply { setMargins(6, 0, 4, 0) }
            onNormalizedChanged = { norm ->
                selectedBand = (norm * 29f).roundToInt().coerceIn(0, 29)
                syncSideFadersFromSelection()
                bankL.invalidate()
                bankR.invalidate()
            }
        }

        faderFineGain = ConsoleFaderView(context, "GAIN", "L9", colAccentR, showLed = false).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 0.72f)
            onNormalizedChanged = { norm ->
                val db = ((norm - 0.5f) * 30f * 10f).roundToInt() / 10f
                applyBandGain(selectedChannel, selectedBand, db)
                valueText = String.format("%+.1f dB", db)
            }
        }

        row.addView(faderVolL)
        row.addView(faderVolR)
        row.addView(centerCol)
        row.addView(faderSelectHz)
        row.addView(faderFineGain)
        addView(row)
    }

    private fun applyBandGain(channel: Int, bandIdx: Int, db: Float) {
        val clamped = db.coerceIn(-15f, 15f)
        if (isLinked) {
            leftGains.set(bandIdx, clamped)
            rightGains.set(bandIdx, clamped)
        } else {
            if (channel == 0) leftGains.set(bandIdx, clamped) else rightGains.set(bandIdx, clamped)
        }
        bankL.invalidate()
        bankR.invalidate()
        notifyChange()
    }

    private fun syncSideFadersFromSelection() {
        val chPrefix = if (selectedChannel == 0) "L" else "R"
        val tag = "$chPrefix${selectedBand + 1}"
        val f = freqs.get(selectedBand)
        val hzLabel = if (f >= 1000f) String.format("%.1f kHz", f / 1000f) else String.format("%.0f Hz", f)
        val currentDb = if (selectedChannel == 0) leftGains.get(selectedBand) else rightGains.get(selectedBand)

        faderSelectHz.subText = tag
        faderSelectHz.valueText = hzLabel
        faderSelectHz.setNormalized(selectedBand / 29f)

        faderFineGain.subText = tag
        faderFineGain.valueText = String.format("%+.1f dB", currentDb)
        faderFineGain.setNormalized((currentDb + 15f) / 30f)
    }

    fun getCombinedBandGain(index: Int): Float {
        val avgEq = (leftGains.get(index) + rightGains.get(index)) * 0.5f
        val avgVol = (leftVolDb + rightVolDb) * 0.5f
        return (avgEq + avgVol).coerceIn(-15f, 15f)
    }

    private fun notifyChange() {
        onEq30ChangedListener?.invoke()
    }
    inner class BandBankCanvas(
        context: Context,
        private val chName: String,
        private val gains: FloatArray,
        private val accentColor: Int,
        private val channelId: Int
    ) : View(context) {

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B0F14"); strokeWidth = 4f }
        private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#334155"); strokeWidth = 2f }
        private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor; strokeWidth = 3f; style = Paint.Style.STROKE }
        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CBD5E1"); style = Paint.Style.FILL }
        private val activeThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor; style = Paint.Style.FILL }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colTextDim; textSize = 16f; textAlign = Paint.Align.CENTER }
        private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor; textSize = 26f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return false

            val leftMargin = 62f
            val chartW = w - leftMargin - 12f
            val topPad = 14f
            val bottomPad = 24f
            val chartH = h - topPad - bottomPad

            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val relX = (event.x - leftMargin).coerceIn(0f, chartW)
                    val bandIdx = ((relX / chartW) * 29f).roundToInt().coerceIn(0, 29)
                    val normY = 1f - ((event.y - topPad) / chartH).coerceIn(0f, 1f)
                    val db = ((normY - 0.5f) * 30f * 10f).roundToInt() / 10f

                    selectedChannel = channelId
                    selectedBand = bandIdx
                    applyBandGain(channelId, bandIdx, db)
                    syncSideFadersFromSelection()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val leftMargin = 62f
            val chartW = w - leftMargin - 12f
            val topPad = 14f
            val bottomPad = 24f
            val chartH = h - topPad - bottomPad
            val midY = topPad + chartH / 2f

            // Badge L / R di kiri
            canvas.drawCircle(30f, midY - 14f, 6f, activeThumbPaint)
            canvas.drawText(chName, 30f, midY + 14f, badgePaint)
            textPaint.textSize = 13f
            canvas.drawText("30 BAND", 30f, midY + 28f, textPaint)
            textPaint.textSize = 15f

            // Garis 0 dB horizontal
            canvas.drawLine(leftMargin, midY, w - 10f, midY, zeroLinePaint)

            val stepX = chartW / 29f
            val curvePath = Path()

            for (i in 0 until 30) {
                val x = leftMargin + i * stepX
                val g = gains.get(i).coerceIn(-15f, 15f)
                val y = midY - (g / 15f) * (chartH * 0.45f)

                // Rel vertikal tiap band
                canvas.drawLine(x, topPad, x, topPad + chartH, trackPaint)

                if (i == 0) curvePath.moveTo(x, y) else curvePath.lineTo(x, y)

                // Label frekuensi di bawah
                val isSelected = (i == selectedBand)
                textPaint.color = if (isSelected) accentColor else colTextDim
                canvas.drawText(labels.get(i), x, h - 6f, textPaint)
            }

            // Garis penghubung antar titik EQ
            canvas.drawPath(curvePath, curvePaint)

            // Kepala slider tiap band
            for (i in 0 until 30) {
                val x = leftMargin + i * stepX
                val g = gains.get(i).coerceIn(-15f, 15f)
                val y = midY - (g / 15f) * (chartH * 0.45f)
                val isSelected = (i == selectedBand && selectedChannel == channelId)
                val rect = RectF(x - 7f, y - 4f, x + 7f, y + 4f)
                canvas.drawRoundRect(rect, 3f, 3f, if (isSelected) activeThumbPaint else thumbPaint)
            }
        }
    }
    inner class ConsoleFaderView(
        context: Context,
        private val titleText: String,
        var subText: String,
        private val accentColor: Int,
        private val showLed: Boolean
    ) : View(context) {

        private var normVal = 0.5f
        var valueText = "+0.0 dB"
        var onNormalizedChanged: ((Float) -> Unit)? = null

        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colTextMain; textSize = 20f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor; textSize = 17f; textAlign = Paint.Align.CENTER }
        private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#090C10"); strokeWidth = 8f; strokeCap = Paint.Cap.ROUND }
        private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#334155"); strokeWidth = 2f }
        private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CBD5E1"); style = Paint.Style.FILL }
        private val capLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor; strokeWidth = 3f }
        private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0D1116"); style = Paint.Style.FILL }
        private val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colTextMain; textSize = 18f; textAlign = Paint.Align.CENTER }
        private val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        init {
            styleBox(this)
        }

        fun setNormalized(v: Float) {
            normVal = v.coerceIn(0f, 1f)
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val h = height.toFloat()
            if (h <= 0f) return false
            val trackTop = 68f
            val trackBottom = h - 56f
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val newNorm = 1f - ((event.y - trackTop) / (trackBottom - trackTop)).coerceIn(0f, 1f)
                    normVal = newNorm
                    invalidate()
                    onNormalizedChanged?.invoke(normVal)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val cx = if (showLed) w * 0.42f else w * 0.5f
            canvas.drawText(titleText, w / 2f, 26f, titlePaint)
            canvas.drawText(subText, w / 2f, 46f, subPaint)

            val trackTop = 68f
            val trackBottom = h - 56f
            val trackH = trackBottom - trackTop

            // Skala garis kecil
            for (t in 0..10) {
                val ty = trackTop + (t / 10f) * trackH
                canvas.drawLine(cx - 18f, ty, cx + 18f, ty, tickPaint)
            }

            // Rel fader
            canvas.drawLine(cx, trackTop, cx, trackBottom, slotPaint)

            // LED meter (khusus L VOL & R VOL)
            if (showLed) {
                val ledX = w * 0.76f
                val activeLeds = (normVal * 14).roundToInt()
                for (l in 0 until 14) {
                    val ly = trackBottom - (l / 13f) * trackH
                    ledPaint.color = when {
                        l > activeLeds -> Color.parseColor("#1E293B")
                        l >= 11 -> Color.parseColor("#F87171")
                        l >= 8 -> Color.parseColor("#FBBF24")
                        else -> Color.parseColor("#34D399")
                    }
                    canvas.drawCircle(ledX, ly, 3.5f, ledPaint)
                }
            }

            // Kepala Fader
            val thumbY = trackBottom - normVal * trackH
            val capRect = RectF(cx - 20f, thumbY - 11f, cx + 20f, thumbY + 11f)
            canvas.drawRoundRect(capRect, 5f, 5f, capPaint)
            canvas.drawLine(cx - 16f, thumbY, cx + 16f, thumbY, capLinePaint)

            // Kotak nilai di bawah
            val boxRect = RectF(8f, h - 42f, w - 8f, h - 8f)
            canvas.drawRoundRect(boxRect, 6f, 6f, boxPaint)
            canvas.drawText(valueText, w / 2f, h - 19f, valPaint)
        }
    }
}
