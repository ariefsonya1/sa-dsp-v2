package com.custom.dspequalizer

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.*

class FxRackView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var subsonicHz = 0f       // 0 = OFF, 20..50 Hz
    var subBassDb = 0f        // 0.0 .. +12.0 dB
    var widenerPct = 0        // 0 = OFF, 1..100%
    var tubeDriveDb = 0f      // 0.0 .. +12.0 dB

    var onFxChangedListener: (() -> Unit)? = null

    private val colBgDark = Color.parseColor("#0D1116")
    private val colPanel = Color.parseColor("#161D24")
    private val colBoxDark = Color.parseColor("#0B0F14")
    private val colBorder = Color.parseColor("#26323D")
    private val colAccent = Color.parseColor("#38BDF8")   // Soft Ice Blue
    private val colTeal = Color.parseColor("#2DD4BF")     // Muted Teal
    private val colTextMain = Color.parseColor("#E2E8F0")
    private val colTextDim = Color.parseColor("#64748B")

    private lateinit var tvStatusLine: TextView
    private lateinit var tvValSubsonic: TextView
    private lateinit var tvValSubBass: TextView
    private lateinit var tvValWidener: TextView
    private lateinit var tvValTube: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(colBgDark)
        setPadding(12, 8, 12, 10)
        buildHeaderSection()
        buildFourKnobCards()
        updateStatusText()
    }

    private fun styleRoundedBox(v: View, fillColor: Int, radius: Float = 14f, strokeColor: Int = colBorder) {
        v.background = GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = radius
            setStroke(2, strokeColor)
        }
    }

    private fun buildHeaderSection() {
        val headerBox = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(4, 0, 4, 10)
            }
        }

        val titleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvMain = TextView(context).apply {
            text = "FX RACK"
            setTextColor(colTextMain)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
        }
        val tvSlash = TextView(context).apply {
            text = "  /  efek yang terhubung ke mesin audio"
            setTextColor(colTextDim)
            textSize = 10.5f
        }
        titleRow.addView(tvMain)
        titleRow.addView(tvSlash)

        tvStatusLine = TextView(context).apply {
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 10f
            setPadding(0, 3, 0, 0)
        }

        headerBox.addView(titleRow)
        headerBox.addView(tvStatusLine)
        addView(headerBox)
    }

    private fun buildFourKnobCards() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // 1. SUBSONIC
        val c1 = createKnobCard("SUBSONIC", colAccent,
            onNormChanged = { n ->
                if (n < 0.05f) {
                    subsonicHz = 0f
                    tvValSubsonic.text = "OFF"
                } else {
                    subsonicHz = (20f + ((n - 0.05f) / 0.95f) * 35f).roundToInt().toFloat() // 20 - 55 Hz
                    tvValSubsonic.text = "${subsonicHz.toInt()} Hz"
                }
                updateStatusText()
                onFxChangedListener?.invoke()
            }
        )
        tvValSubsonic = c1.second
        tvValSubsonic.text = "OFF"

        // 2. SUB BASS
        val c2 = createKnobCard("SUB BASS", colTeal,
            onNormChanged = { n ->
                subBassDb = ((n * 12f) * 10f).roundToInt() / 10f
                tvValSubBass.text = String.format("%+.1f dB", subBassDb)
                updateStatusText()
                onFxChangedListener?.invoke()
            }
        )
        tvValSubBass = c2.second
        tvValSubBass.text = "+0.0 dB"

        // 3. WIDENER
        val c3 = createKnobCard("WIDENER", colAccent,
            onNormChanged = { n ->
                widenerPct = (n * 100f).roundToInt()
                tvValWidener.text = if (widenerPct == 0) "OFF" else "$widenerPct%"
                updateStatusText()
                onFxChangedListener?.invoke()
            }
        )
        tvValWidener = c3.second
        tvValWidener.text = "OFF"

        // 4. TUBE DRIVE
        val c4 = createKnobCard("TUBE DRIVE", colTeal,
            onNormChanged = { n ->
                tubeDriveDb = ((n * 12f) * 10f).roundToInt() / 10f
                tvValTube.text = String.format("%+.1f dB", tubeDriveDb)
                updateStatusText()
                onFxChangedListener?.invoke()
            }
        )
        tvValTube = c4.second
        tvValTube.text = "+0.0 dB"

        row.addView(c1.first)
        row.addView(c2.first)
        row.addView(c3.first)
        row.addView(c4.first)
        addView(row)
    }

    private fun createKnobCard(
        title: String,
        needleColor: Int,
        onNormChanged: (Float) -> Unit
    ): Pair<LinearLayout, TextView> {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(6, 0, 6, 0)
            }
            styleRoundedBox(this, colPanel, 16f, colBorder)
            setPadding(12, 12, 12, 12)
        }

        val tvTitle = TextView(context).apply {
            text = title
            setTextColor(colTextMain)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val knob = TaGunnerKnobView(context, needleColor).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                setMargins(0, 6, 0, 6)
            }
            this.onNormChange = onNormChanged
        }

        val tvReadout = TextView(context).apply {
            setTextColor(colTextMain)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            styleRoundedBox(this, colBoxDark, 10f, colBorder)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 58)
        }

        card.addView(tvTitle)
        card.addView(knob)
        card.addView(tvReadout)
        return Pair(card, tvReadout)
    }

    private fun updateStatusText() {
        val subTxt = if (subsonicHz <= 0f) "SUBSONIC OFF" else "SUBSONIC ${subsonicHz.toInt()}Hz"
        val widTxt = if (widenerPct == 0) "WIDENER OFF" else "WIDENER $widenerPct%"
        val drvTxt = if (tubeDriveDb <= 0.05f) "TUBE DRIVE OFF" else String.format("TUBE DRIVE %+.1fdB", tubeDriveDb)
        tvStatusLine.text = "Status efek: $subTxt • $widTxt • $drvTxt"
    }
    fun calculateFxBoostAt(freqHz: Float): Float {
        var boost = 0f

        // 1. Subsonic High-Pass Cut (memotong frekuensi di bawah subsonicHz)
        if (subsonicHz > 0f && freqHz < subsonicHz) {
            val octavesBelow = abs(ln((freqHz / subsonicHz).toDouble()) / ln(2.0)).toFloat()
            boost -= (14f * octavesBelow).coerceAtMost(15f)
        }

        // 2. Sub Bass Boost (fokus di 45 - 65 Hz)
        if (subBassDb > 0f) {
            val octaves = abs(ln((freqHz / 52f).toDouble()) / ln(2.0)).toFloat()
            val curve = exp(-0.5 * (octaves / 0.65).pow(2.0)).toFloat()
            boost += subBassDb * curve
        }

        // 3. Widener Air Presence (sedikit buka ruang 3.5 kHz - 10 kHz)
        if (widenerPct > 0) {
            val octaves = abs(ln((freqHz / 5500f).toDouble()) / ln(2.0)).toFloat()
            val air = exp(-0.5 * (octaves / 0.85).pow(2.0)).toFloat()
            boost += (widenerPct / 100f) * 2.5f * air
        }

        // 4. Tube Drive (kehangatan low-mid 280 Hz + kilau harmonik 7 kHz)
        if (tubeDriveDb > 0f) {
            val warm = exp(-0.5 * (abs(ln((freqHz / 280f).toDouble()) / ln(2.0)) / 0.75).pow(2.0)).toFloat()
            val sparkle = exp(-0.5 * (abs(ln((freqHz / 7000f).toDouble()) / ln(2.0)) / 0.8).pow(2.0)).toFloat()
            boost += (tubeDriveDb / 12f) * (3.5f * warm + 2.5f * sparkle)
        }

        return boost
    }

    // Kenop Putar Metalik dengan Skala Tick Marks seperti TaGunner EQ
    inner class TaGunnerKnobView(
        context: Context,
        private val needleColor: Int
    ) : View(context) {

        private var norm = 0f
        var onNormChange: ((Float) -> Unit)? = null

        private val tickDimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
        }
        private val tickActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = needleColor
            strokeWidth = 3.5f
            strokeCap = Paint.Cap.ROUND
        }
        private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#090D12")
            style = Paint.Style.FILL
        }
        private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = needleColor
            strokeWidth = 7f
            strokeCap = Paint.Cap.ROUND
        }

        private var lastY = 0f

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = lastY - event.y
                    lastY = event.y
                    norm = (norm + dy / 250f).coerceIn(0f, 1f)
                    invalidate()
                    onNormChange?.invoke(norm)
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

            val cx = w / 2f
            val cy = h / 2f
            val maxR = (min(w, h) / 2f) - 6f
            if (maxR <= 20f) return

            // 1. Gambar 13 garis skala (tick marks) melingkar dari 135 deg s/d 405 deg
            val totalTicks = 12
            for (t in 0..totalTicks) {
                val frac = t.toFloat() / totalTicks
                val deg = 135f + frac * 270f
                val rad = Math.toRadians(deg.toDouble())
                val innerTick = maxR - 14f
                val outerTick = if (t % 3 == 0) maxR else maxR - 5f
                val paint = if (frac <= norm + 0.01f && norm > 0f) tickActivePaint else tickDimPaint
                canvas.drawLine(
                    cx + cos(rad).toFloat() * innerTick,
                    cy + sin(rad).toFloat() * innerTick,
                    cx + cos(rad).toFloat() * outerTick,
                    cy + sin(rad).toFloat() * outerTick,
                    paint
                )
            }

            // 2. Cincin dasar kenop
            val knobR = maxR - 20f
            canvas.drawCircle(cx, cy, knobR, outerRingPaint)

            // 3. Badan kenop gradasi 3D
            capPaint.shader = LinearGradient(
                cx - knobR, cy - knobR, cx + knobR, cy + knobR,
                Color.parseColor("#2B3642"), Color.parseColor("#11161D"),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, knobR - 5f, capPaint)
            canvas.drawCircle(cx, cy, knobR - 5f, rimPaint)

            // 4. Jarum indikator berwarna sesuai posisi putaran
            val needleDeg = 135f + norm * 270f
            val needleRad = Math.toRadians(needleDeg.toDouble())
            canvas.drawLine(
                cx + cos(needleRad).toFloat() * (knobR * 0.25f),
                cy + sin(needleRad).toFloat() * (knobR * 0.25f),
                cx + cos(needleRad).toFloat() * (knobR - 11f),
                cy + sin(needleRad).toFloat() * (knobR - 11f),
                needlePaint
            )
        }
    }
}
