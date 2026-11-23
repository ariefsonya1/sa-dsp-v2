package com.custom.dspequalizer

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import kotlin.math.*

class SignalRoutingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val colBgDark = Color.parseColor("#0D1116")
    private val colPanel = Color.parseColor("#161D24")
    private val colBoxDark = Color.parseColor("#0B0F14")
    private val colBorder = Color.parseColor("#26323D")
    private val colAccent = Color.parseColor("#38BDF8")
    private val colTeal = Color.parseColor("#2DD4BF")
    private val colGreen = Color.parseColor("#34D399")
    private val colAmber = Color.parseColor("#FBBF24")
    private val colTextMain = Color.parseColor("#E2E8F0")
    private val colTextDim = Color.parseColor("#94A3B8")

    private lateinit var tvAppVal: TextView
    private lateinit var tvBtVal: TextView
    private lateinit var tvAuxVal: TextView
    private lateinit var tvOutVal: TextView
    private lateinit var diagramView: SignalDiagramCanvas

    private var activeRouteIndex = 0
    private var btDeviceName = "Tidak terhubung"

    init {
        orientation = VERTICAL
        setBackgroundColor(colBgDark)
        setPadding(12, 8, 12, 8)
        buildHeader()
        buildTopStatusCards()
        buildDiagramArea()
        buildFooter()
        scanAudioRoutes()
    }

    private fun styleCard(v: View, fill: Int = colPanel, radius: Float = 12f, stroke: Int = colBorder) {
        v.background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            setStroke(2, stroke)
        }
    }

    private fun buildHeader() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(2, 0, 2, 8)
            }
        }
        val tv1 = TextView(context).apply {
            text = "JALUR SINYAL"
            setTextColor(colTextMain)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        }
        val tv2 = TextView(context).apply {
            text = "  /  Status audio"
            setTextColor(colTextDim)
            textSize = 12.5f
        }
        row.addView(tv1); row.addView(tv2)
        addView(row)
    }
    private fun buildTopStatusCards() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 8)
            }
        }
        val c1 = makeStatusBox("Aplikasi yang memutar", colTeal)
        tvAppVal = c1.second
        val c2 = makeStatusBox("BLUETOOTH", colAccent)
        tvBtVal = c2.second
        val c3 = makeStatusBox("AUX / USB", colAmber)
        tvAuxVal = c3.second
        val c4 = makeStatusBox("Keluaran media", colGreen)
        tvOutVal = c4.second

        row.addView(c1.first); row.addView(c2.first); row.addView(c3.first); row.addView(c4.first)
        addView(row)
    }

    private fun makeStatusBox(label: String, borderCol: Int): Pair<LinearLayout, TextView> {
        val box = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 0, 4, 0) }
            styleCard(this, colPanel, 10f, borderCol)
            setPadding(12, 8, 12, 8)
        }
        val tvL = TextView(context).apply { text = label; setTextColor(borderCol); textSize = 11f; setTypeface(null, Typeface.BOLD) }
        val tvV = TextView(context).apply { text = "-"; setTextColor(colTextMain); textSize = 13f; isSingleLine = true; setPadding(0, 3, 0, 0) }
        box.addView(tvL); box.addView(tvV)
        return Pair(box, tvV)
    }

    private fun buildDiagramArea() {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(4, 0, 4, 8) }
            styleCard(this, colBoxDark, 14f, colBorder)
            setPadding(10, 8, 10, 8)
        }
        val tvSub = TextView(context).apply {
            text = "SIGNAL PATH  /  MEDIA ROUTE"
            setTextColor(colTextDim)
            textSize = 11.5f
            gravity = Gravity.CENTER
        }
        diagramView = SignalDiagramCanvas(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        container.addView(tvSub); container.addView(diagramView)
        addView(container)
    }

    private fun buildFooter() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        val tvNote = TextView(context).apply {
            text = "Jalur Android • aplikasi memantau perubahan rute keluaran otomatis"
            setTextColor(colTextDim)
            textSize = 11.5f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnScan = Button(context).apply {
            text = "Scan Ulang"
            isAllCaps = false
            textSize = 12.5f
            setTextColor(colTextMain)
            styleCard(this, colPanel, 10f, colAccent)
            setPadding(22, 0, 22, 0)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, 56)
            setOnClickListener {
                scanAudioRoutes()
                Toast.makeText(context, "Rute audio diperbarui", Toast.LENGTH_SHORT).show()
            }
        }
        row.addView(tvNote); row.addView(btnScan)
        addView(row)
    }
    fun scanAudioRoutes() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isMusic = am?.isMusicActive == true
        tvAppVal.text = if (isMusic) "Media / TaGunner" else "System Standby"

        var foundBt = ""
        var foundWired = false
        var foundUsb = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && am != null) {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (d in devices) {
                when (d.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                        foundBt = d.productName?.toString() ?: "Bluetooth Audio"
                    }
                    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG -> {
                        foundWired = true
                    }
                    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> {
                        foundUsb = true
                    }
                }
            }
        }

        when {
            foundBt.isNotEmpty() -> {
                activeRouteIndex = 1; btDeviceName = foundBt
                tvBtVal.text = foundBt; tvAuxVal.text = "Tidak ada kabel"; tvOutVal.text = "Bluetooth • $foundBt"
            }
            foundUsb -> {
                activeRouteIndex = 3; btDeviceName = "Tidak terhubung"
                tvBtVal.text = "Tidak aktif"; tvAuxVal.text = "USB DAC Audio"; tvOutVal.text = "USB Audio"
            }
            foundWired -> {
                activeRouteIndex = 2; btDeviceName = "Tidak terhubung"
                tvBtVal.text = "Tidak aktif"; tvAuxVal.text = "Kabel AUX / Jack"; tvOutVal.text = "AUX / Kabel"
            }
            else -> {
                activeRouteIndex = 0; btDeviceName = "Tidak terhubung"
                tvBtVal.text = "Tidak aktif"; tvAuxVal.text = "Tidak ada kabel"; tvOutVal.text = "Speaker ponsel"
            }
        }
        diagramView.invalidate()
    }

    inner class SignalDiagramCanvas(context: Context) : View(context) {
        private val boxFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colPanel; style = Paint.Style.FILL }
        private val borderNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colBorder; strokeWidth = 2.5f; style = Paint.Style.STROKE }
        private val borderActive = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colGreen; strokeWidth = 3.5f; style = Paint.Style.STROKE }
        private val lineDim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#334155"); strokeWidth = 3f }
        private val lineActive = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colGreen; strokeWidth = 4f }
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colTextMain; textSize = 26f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colTeal; textSize = 21f; textAlign = Paint.Align.CENTER }
        private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colTextMain; textSize = 21f; textAlign = Paint.Align.CENTER }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val midY = h / 2f
            val b1 = RectF(w * 0.03f, midY - 52f, w * 0.27f, midY + 52f)
            canvas.drawRoundRect(b1, 14f, 14f, boxFill)
            canvas.drawRoundRect(b1, 14f, 14f, borderActive)
            canvas.drawText("MEDIA APP", b1.centerX(), b1.centerY() - 6f, titlePaint)
            canvas.drawText("Audio Stream", b1.centerX(), b1.centerY() + 24f, subPaint)

            val b2 = RectF(w * 0.36f, midY - 56f, w * 0.62f, midY + 56f)
            canvas.drawRoundRect(b2, 14f, 14f, boxFill)
            canvas.drawRoundRect(b2, 14f, 14f, borderActive)
            canvas.drawText("DSP mini Pro", b2.centerX(), b2.centerY() - 6f, titlePaint)
            subPaint.color = colGreen
            canvas.drawText("ENGINE READY", b2.centerX(), b2.centerY() + 24f, subPaint)
            subPaint.color = colTeal

            canvas.drawLine(b1.right, midY, b2.left, midY, lineActive)
            dotPaint.color = colGreen
            canvas.drawCircle(b1.right + 10f, midY, 5f, dotPaint)

            val outputs = arrayOf("Speaker ponsel", "Bluetooth", "AUX / kabel", "USB AUDIO", "Tidak diketahui")
            val busX = w * 0.67f
            val slotLeft = w * 0.72f
            val slotRight = w * 0.97f
            val stepY = h / 5.4f

            canvas.drawLine(b2.right, midY, busX, midY, lineActive)

            for (i in outputs.indices) {
                val cy = stepY * (i + 0.7f)
                val isAct = (i == activeRouteIndex)
                val r = RectF(slotLeft, cy - 24f, slotRight, cy + 24f)

                canvas.drawLine(busX, midY, busX, cy, if (isAct) lineActive else lineDim)
                canvas.drawLine(busX, cy, slotLeft, cy, if (isAct) lineActive else lineDim)

                canvas.drawRoundRect(r, 10f, 10f, boxFill)
                canvas.drawRoundRect(r, 10f, 10f, if (isAct) borderActive else borderNormal)

                dotPaint.color = if (isAct) colGreen else colTextDim
                canvas.drawCircle(r.left + 16f, cy, 5f, dotPaint)

                slotPaint.color = if (isAct) colTextMain else colTextDim
                val label = if (i == 1 && isAct) "Bluetooth ($btDeviceName)" else outputs.get(i)
                canvas.drawText(label, r.centerX() + 6f, cy + 7f, slotPaint)
            }
        }
    }
}
class ConsoleConfigView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onConsoleChannelChanged: ((index: Int, normVal: Float) -> Unit)? = null
    var onFactoryResetAllListener: (() -> Unit)? = null

    private val colBgDark = Color.parseColor("#0D1116")
    private val colPanel = Color.parseColor("#161D24")
    private val colBoxDark = Color.parseColor("#0B0F14")
    private val colBorder = Color.parseColor("#26323D")
    private val colAccent = Color.parseColor("#38BDF8")
    private val colTeal = Color.parseColor("#2DD4BF")
    private val colGreen = Color.parseColor("#34D399")
    private val colRed = Color.parseColor("#F87171")
    private val colAmber = Color.parseColor("#FBBF24")
    private val colTextMain = Color.parseColor("#E2E8F0")
    private val colTextDim = Color.parseColor("#94A3B8")

    private val chNames = arrayOf("GRAPHIC", "PARAM", "LOW", "MID", "HIGH", "BASS", "L VOL", "R VOL", "MAIN")
    private val chIsPercent = booleanArrayOf(true, true, false, false, false, false, false, false, false)
    private val chValues = floatArrayOf(1.0f, 1.0f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
    private val chEnabled = BooleanArray(9) { true }
    private val chAudioLevels = FloatArray(9) { 0f }
    private var selectedCh = 0

    private lateinit var tvSelectedHeader: TextView
    private lateinit var miniCurveView: MiniCurvePreview
    private lateinit var dualVuView: DualAnalogVuView
    private lateinit var selectedKnob: ConsoleKnobView
    private val stripViews = mutableListOf<MiniStripView>()

    init {
        orientation = VERTICAL
        setBackgroundColor(colBgDark)
        setPadding(10, 6, 10, 6)
        buildTopConsoleDeck()
        buildNineChannelStrips()
    }

    fun updateAudioLevels(masterRms: Float, peak: Float, bass: Float, mid: Float, high: Float) {
        if (visibility != View.VISIBLE) return
        dualVuView.rmsLevel = masterRms.coerceIn(0f, 1f)
        dualVuView.peakLevel = peak.coerceIn(0f, 1f)

        chAudioLevels.set(0, if (chEnabled.get(0)) masterRms * chValues.get(0) else 0f)
        chAudioLevels.set(1, if (chEnabled.get(1)) masterRms * chValues.get(1) else 0f)
        chAudioLevels.set(2, if (chEnabled.get(2)) bass else 0f)
        chAudioLevels.set(3, if (chEnabled.get(3)) mid else 0f)
        chAudioLevels.set(4, if (chEnabled.get(4)) high else 0f)
        chAudioLevels.set(5, if (chEnabled.get(5)) (bass * 1.15f).coerceIn(0f, 1f) else 0f)
        chAudioLevels.set(6, if (chEnabled.get(6)) masterRms else 0f)
        chAudioLevels.set(7, if (chEnabled.get(7)) masterRms * 0.96f else 0f)
        chAudioLevels.set(8, if (chEnabled.get(8)) peak else 0f)

        dualVuView.invalidate()
        for (s in stripViews) s.invalidate()
    }

    fun resetVuMeters() {
        dualVuView.rmsLevel = 0f
        dualVuView.peakLevel = 0f
        chAudioLevels.fill(0f)
        dualVuView.invalidate()
        for (s in stripViews) s.invalidate()
    }

    private fun styleBox(v: View, fill: Int = colPanel, radius: Float = 10f, stroke: Int = colBorder) {
        v.background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            setStroke(2, stroke)
        }
    }

    private fun buildTopConsoleDeck() {
        val deck = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 0.85f).apply {
                setMargins(2, 0, 2, 6)
            }
        }

        val leftScreen = LinearLayout(context).apply {
            orientation = VERTICAL
            styleBox(this, colBoxDark, 12f, colBorder)
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.45f).apply { setMargins(0, 0, 6, 0) }
            setPadding(12, 6, 12, 6)
        }
        val topTag = TextView(context).apply {
            text = "DSP mini Pro DIGITAL MIXING CONSOLE"
            setTextColor(colTeal)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
        }
        tvSelectedHeader = TextView(context).apply {
            text = "CH 1 GRAPHIC"
            setTextColor(colTextMain)
            textSize = 15.5f
            setTypeface(null, Typeface.BOLD)
        }
        miniCurveView = MiniCurvePreview(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        leftScreen.addView(topTag); leftScreen.addView(tvSelectedHeader); leftScreen.addView(miniCurveView)

        dualVuView = DualAnalogVuView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.65f).apply { setMargins(0, 0, 6, 0) }
            styleBox(this, colPanel, 12f, colBorder)
        }

        val rightCtrl = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            styleBox(this, colPanel, 12f, colBorder)
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 0.95f)
            setPadding(6, 4, 6, 4)
        }
        val tvCtrlLbl = TextView(context).apply {
            text = "SELECTED CONTROL"
            setTextColor(colTextDim)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        selectedKnob = ConsoleKnobView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            onNormChanged = { newNorm ->
                chValues.set(selectedCh, newNorm)
                refreshHeaderReadout()
                miniCurveView.invalidate()
                if (selectedCh < stripViews.size) stripViews.get(selectedCh).invalidate()
                onConsoleChannelChanged?.invoke(selectedCh, newNorm)
            }
        }
        rightCtrl.addView(tvCtrlLbl); rightCtrl.addView(selectedKnob)

        deck.addView(leftScreen); deck.addView(dualVuView); deck.addView(rightCtrl)
        addView(deck)
    }

    private fun buildNineChannelStrips() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.15f)
        }
        stripViews.clear()
        for (i in 0 until 9) {
            val strip = MiniStripView(context, i).apply {
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(3, 0, 3, 0)
                }
            }
            stripViews.add(strip)
            row.addView(strip)
        }
        addView(row)
        refreshHeaderReadout()
    }

    private fun formatChannelValue(idx: Int): String {
        val v = chValues.get(idx)
        return if (chIsPercent.get(idx)) {
            "${(v * 100f).roundToInt()}%"
        } else {
            val db = (v - 0.5f) * 30f
            String.format("%+.1f dB", db)
        }
    }

    private fun refreshHeaderReadout() {
        tvSelectedHeader.text = "CH ${selectedCh + 1} ${chNames.get(selectedCh)}"
        selectedKnob.setVal(chValues.get(selectedCh), formatChannelValue(selectedCh))
    }
    inner class MiniCurvePreview(context: Context) : View(context) {
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colAccent; strokeWidth = 3.5f; style = Paint.Style.STROKE }
        private val zeroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colBorder; strokeWidth = 2f }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val midY = h / 2f
            canvas.drawLine(0f, midY, w, midY, zeroPaint)
            val p = Path()
            val low = (chValues.get(2) - 0.5f) * 2f
            val mid = (chValues.get(3) - 0.5f) * 2f
            val high = (chValues.get(4) - 0.5f) * 2f
            p.moveTo(0f, midY - low * (h * 0.35f))
            p.cubicTo(w * 0.33f, midY - low * (h * 0.35f), w * 0.38f, midY - mid * (h * 0.35f), w * 0.66f, midY - mid * (h * 0.35f))
            p.lineTo(w, midY - high * (h * 0.35f))
            canvas.drawPath(p, linePaint)
        }
    }

    inner class DualAnalogVuView(context: Context) : View(context) {
        var rmsLevel = 0f
        var peakLevel = 0f
        private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E7D8B1"); style = Paint.Style.FILL }
        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#334155"); strokeWidth = 3f; style = Paint.Style.STROKE }
        private val redArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#DC2626"); strokeWidth = 4f; style = Paint.Style.STROKE }
        private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B91C1C"); strokeWidth = 4f; strokeCap = Paint.Cap.ROUND }
        private val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E293B"); textSize = 19f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val meterW = w * 0.39f
            drawSingleMeter(canvas, RectF(10f, 10f, 10f + meterW, h - 10f), rmsLevel, "RMS")
            drawSingleMeter(canvas, RectF(18f + meterW, 10f, 18f + meterW * 2f, h - 10f), peakLevel, "PEAK")

            val barX = w * 0.86f
            val active = (peakLevel * 10).roundToInt()
            for (b in 0 until 10) {
                val by = (h - 14f) - (b / 9f) * (h - 28f)
                ledPaint.color = when {
                    b >= active -> Color.parseColor("#1E293B")
                    b >= 8 -> colRed
                    b >= 6 -> colAmber
                    else -> colGreen
                }
                canvas.drawRect(barX, by - 5f, barX + 15f, by + 2f, ledPaint)
                canvas.drawRect(barX + 19f, by - 5f, barX + 34f, by + 2f, ledPaint)
            }
        }

        private fun drawSingleMeter(c: Canvas, r: RectF, lvl: Float, tag: String) {
            c.drawRoundRect(r, 8f, 8f, facePaint)
            val cx = r.centerX()
            val cy = r.bottom - 10f
            val rad = min(r.width() * 0.44f, r.height() * 0.72f)
            val arcRect = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
            c.drawArc(arcRect, 215f, 75f, false, arcPaint)
            c.drawArc(arcRect, 290f, 35f, false, redArcPaint)
            c.drawText(tag, cx, r.bottom - 6f, lblPaint)

            val deg = 215f + lvl * 110f
            val a = Math.toRadians(deg.toDouble())
            c.drawLine(cx, cy, cx + cos(a).toFloat() * (rad - 4f), cy + sin(a).toFloat() * (rad - 4f), needlePaint)
        }
    }

    inner class ConsoleKnobView(context: Context) : View(context) {
        private var norm = 1f
        private var label = "100%"
        var onNormChanged: ((Float) -> Unit)? = null
        private val trackP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colBorder; strokeWidth = 6f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val arcP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colAccent; strokeWidth = 6f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        private val bodyP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E293B"); style = Paint.Style.FILL }
        private val indP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND }
        private val txtP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colTextMain; textSize = 23f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private var lastY = 0f

        fun setVal(n: Float, txt: String) { norm = n; label = txt; invalidate() }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastY = event.y; parent?.requestDisallowInterceptTouchEvent(true); return true }
                MotionEvent.ACTION_MOVE -> {
                    val dy = lastY - event.y; lastY = event.y
                    norm = (norm + dy / 200f).coerceIn(0f, 1f)
                    onNormChanged?.invoke(norm); return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = w / 2f; val cy = (h - 24f) / 2f
            val r = (min(w, h - 26f) / 2f) - 6f
            if (r <= 8f) return
            val rect = RectF(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, 135f, 270f, false, trackP)
            canvas.drawArc(rect, 135f, 270f * norm, false, arcP)
            canvas.drawCircle(cx, cy, r - 7f, bodyP)
            val rad = Math.toRadians((135f + 270f * norm).toDouble())
            canvas.drawLine(cx, cy, cx + cos(rad).toFloat() * (r - 12f), cy + sin(rad).toFloat() * (r - 12f), indP)
            canvas.drawText(label, cx, h - 4f, txtP)
        }
    }
    inner class MiniStripView(context: Context, private val idx: Int) : View(context) {
        private val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colPanel; style = Paint.Style.FILL }
        private val bdrNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colBorder; strokeWidth = 2f; style = Paint.Style.STROKE }
        private val bdrSel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colAccent; strokeWidth = 3.5f; style = Paint.Style.STROKE }
        private val selFillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val trackP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B0F14"); strokeWidth = 6f; strokeCap = Paint.Cap.ROUND }
        private val capP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val ledP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val txtSelP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val txtOnP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 17f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val txtNameP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18.5f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val txtValP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colTextMain; textSize = 18.5f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val h = height.toFloat()
            if (h <= 0f) return super.onTouchEvent(event)
            val trackTop = 96f
            val trackBot = h - 34f
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    selectedCh = idx
                    if (event.y < 38f) {
                    } else if (event.y < 72f) {
                        chEnabled.set(idx, !chEnabled.get(idx))
                    } else if (event.y in trackTop..trackBot) {
                        val norm = 1f - ((event.y - trackTop) / (trackBot - trackTop)).coerceIn(0f, 1f)
                        chValues.set(idx, norm)
                        onConsoleChannelChanged?.invoke(idx, norm)
                    }
                    refreshHeaderReadout()
                    miniCurveView.invalidate()
                    for (s in stripViews) s.invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.y >= 72f) {
                        val norm = 1f - ((event.y - trackTop) / (trackBot - trackTop)).coerceIn(0f, 1f)
                        chValues.set(idx, norm)
                        refreshHeaderReadout()
                        miniCurveView.invalidate()
                        invalidate()
                        onConsoleChannelChanged?.invoke(idx, norm)
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val isSel = (idx == selectedCh)
            val isEn = chEnabled.get(idx)

            val cardR = RectF(1f, 1f, w - 1f, h - 1f)
            canvas.drawRoundRect(cardR, 10f, 10f, bgP)
            canvas.drawRoundRect(cardR, 10f, 10f, if (isSel) bdrSel else bdrNormal)

            val selR = RectF(6f, 6f, w - 6f, 34f)
            selFillP.color = if (isSel) colAccent else Color.parseColor("#1E293B")
            canvas.drawRoundRect(selR, 6f, 6f, selFillP)
            txtSelP.color = if (isSel) colBgDark else colTextDim
            canvas.drawText("SEL", w / 2f, 26f, txtSelP)

            txtOnP.color = if (isEn) colGreen else colTextDim
            canvas.drawText(if (isEn) "ON" else "OFF", w / 2f, 54f, txtOnP)

            txtNameP.color = if (idx == 8) colRed else colAccent
            canvas.drawText(chNames.get(idx), w / 2f, 78f, txtNameP)

            val trackTop = 96f
            val trackBot = h - 34f
            val faderX = w * 0.62f
            val ledX = w * 0.28f

            val activeDots = (chAudioLevels.get(idx) * 8).roundToInt()
            for (d in 0 until 8) {
                val dy = trackBot - (d / 7f) * (trackBot - trackTop)
                ledP.color = when {
                    d >= activeDots -> Color.parseColor("#1E293B")
                    d >= 6 -> colRed
                    d >= 4 -> colAmber
                    else -> colGreen
                }
                canvas.drawCircle(ledX, dy, 4f, ledP)
            }

            canvas.drawLine(faderX, trackTop, faderX, trackBot, trackP)
            val v = chValues.get(idx)
            val thumbY = trackBot - v * (trackBot - trackTop)
            capP.color = if (idx == 8) colRed else Color.parseColor("#CBD5E1")
            canvas.drawRoundRect(RectF(faderX - 15f, thumbY - 9f, faderX + 15f, thumbY + 9f), 5f, 5f, capP)

            canvas.drawText(formatChannelValue(idx), w / 2f, h - 8f, txtValP)
        }
    }
}
