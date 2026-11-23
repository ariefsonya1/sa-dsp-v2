package com.custom.dspequalizer

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import kotlin.math.*
import kotlin.random.Random

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
    private val colTextDim = Color.parseColor("#64748B")

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
        setPadding(12, 6, 12, 6)
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
                setMargins(2, 0, 2, 6)
            }
        }
        val tv1 = TextView(context).apply {
            text = "JALUR SINYAL"
            setTextColor(colTextMain)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
        }
        val tv2 = TextView(context).apply {
            text = "  /  Status audio"
            setTextColor(colTextDim)
            textSize = 10f
        }
        row.addView(tv1); row.addView(tv2)
        addView(row)
    }

    private fun buildTopStatusCards() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 6)
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
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(3, 0, 3, 0) }
            styleCard(this, colPanel, 10f, borderCol)
            setPadding(10, 7, 10, 7)
        }
        val tvL = TextView(context).apply { text = label; setTextColor(borderCol); textSize = 8.5f; setTypeface(null, Typeface.BOLD) }
        val tvV = TextView(context).apply { text = "-"; setTextColor(colTextMain); textSize = 10f; isSingleLine = true; setPadding(0, 2, 0, 0) }
        box.addView(tvL); box.addView(tvV)
        return Pair(box, tvV)
    }
    private fun buildDiagramArea() {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(3, 0, 3, 6) }
            styleCard(this, colBoxDark, 14f, colBorder)
            setPadding(8, 6, 8, 6)
        }
        val tvSub = TextView(context).apply {
            text = "SIGNAL PATH  /  MEDIA ROUTE"
            setTextColor(colTextDim)
            textSize = 9.5f
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
            text = "Jalur Android • aplikasi dapat memantau perubahan rute keluaran otomatis"
            setTextColor(colTextDim)
            textSize = 9.5f
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnScan = Button(context).apply {
            text = "Scan Ulang"
            isAllCaps = false
            textSize = 10f
            setTextColor(colTextMain)
            styleCard(this, colPanel, 10f, colAccent)
            setPadding(18, 0, 18, 0)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, 50)
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
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colTextMain; textSize = 22f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colTeal; textSize = 18f; textAlign = Paint.Align.CENTER }
        private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colTextMain; textSize = 18f; textAlign = Paint.Align.CENTER }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val midY = h / 2f
            val b1 = RectF(w * 0.03f, midY - 48f, w * 0.27f, midY + 48f)
            canvas.drawRoundRect(b1, 14f, 14f, boxFill)
            canvas.drawRoundRect(b1, 14f, 14f, borderActive)
            canvas.drawText("MEDIA APP", b1.centerX(), b1.centerY() - 4f, titlePaint)
            canvas.drawText("Audio Stream", b1.centerX(), b1.centerY() + 22f, subPaint)

            val b2 = RectF(w * 0.36f, midY - 52f, w * 0.62f, midY + 52f)
            canvas.drawRoundRect(b2, 14f, 14f, boxFill)
            canvas.drawRoundRect(b2, 14f, 14f, borderActive)
            canvas.drawText("DSP mini Pro", b2.centerX(), b2.centerY() - 4f, titlePaint)
            subPaint.color = colGreen
            canvas.drawText("ENGINE READY", b2.centerX(), b2.centerY() + 22f, subPaint)
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
                val r = RectF(slotLeft, cy - 22f, slotRight, cy + 22f)

                canvas.drawLine(busX, midY, busX, cy, if (isAct) lineActive else lineDim)
                canvas.drawLine(busX, cy, slotLeft, cy, if (isAct) lineActive else lineDim)

                canvas.drawRoundRect(r, 10f, 10f, boxFill)
                canvas.drawRoundRect(r, 10f, 10f, if (isAct) borderActive else borderNormal)

                dotPaint.color = if (isAct) colGreen else colTextDim
                canvas.drawCircle(r.left + 16f, cy, 5f, dotPaint)

                slotPaint.color = if (isAct) colTextMain else colTextDim
                val label = if (i == 1 && isAct) "Bluetooth ($btDeviceName)" else outputs.get(i)
                canvas.drawText(label, r.centerX() + 6f, cy + 6f, slotPaint)
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
    private val colTextDim = Color.parseColor("#64748B")

    private val chNames = arrayOf("GRAPHIC", "PARAM", "LOW", "MID", "HIGH", "BASS", "L VOL", "R VOL", "MAIN")
    private val chIsPercent = booleanArrayOf(true, true, false, false, false, false, false, false, false)
    private val chValues = floatArrayOf(1.0f, 1.0f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
    private val chEnabled = BooleanArray(9) { true }
    private var selectedCh = 0

    private lateinit var tvSelectedHeader: TextView
    private lateinit var miniCurveView: MiniCurvePreview
    private lateinit var dualVuView: DualAnalogVuView
    private lateinit var selectedKnob: ConsoleKnobView
    private val stripViews = mutableListOf<MiniStripView>()

    private val animHandler = Handler(Looper.getMainLooper())

    init {
        orientation = VERTICAL
        setBackgroundColor(colBgDark)
        setPadding(10, 6, 10, 6)
        buildTopConsoleDeck()
        buildNineChannelStrips()
        startNeedleAnimation()
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
            setPadding(10, 6, 10, 6)
        }
        val topTag = TextView(context).apply {
            text = "DSP mini Pro DIGITAL MIXING CONSOLE"
            setTextColor(colTeal)
            textSize = 8.5f
        }
        tvSelectedHeader = TextView(context).apply {
            text = "CH 1 GRAPHIC"
            setTextColor(colTextMain)
            textSize = 12f
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
            textSize = 8.5f
            gravity = Gravity.CENTER
        }
        selectedKnob = ConsoleKnobView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            onNormChanged = { norm ->
                chValues.set(selectedCh, norm)
                stripViews.get(selectedCh).invalidate()
                miniCurveView.invalidate()
                refreshHeaderReadout()
                onConsoleChannelChanged?.invoke(selectedCh, norm)
            }
        }
        rightCtrl.addView(tvCtrlLbl); rightCtrl.addView(selectedKnob)

        deck.addView(leftScreen); deck.addView(dualVuView); deck.addView(rightCtrl)
        addView(deck)
    }

    private fun buildNineChannelStrips() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.35f)
        }
        for (i in chNames.indices) {
            val strip = MiniStripView(context, i).apply {
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(2, 0, 2, 0) }
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

    private fun startNeedleAnimation() {
        animHandler.post(object : Runnable {
            override fun run() {
                if (visibility == View.VISIBLE) {
                    val mainLvl = chValues.get(8)
                    dualVuView.rmsLevel = (mainLvl * 0.65f + Random.nextFloat() * 0.25f).coerceIn(0.05f, 0.95f)
                    dualVuView.peakLevel = (mainLvl * 0.75f + Random.nextFloat() * 0.22f).coerceIn(0.08f, 0.98f)
                    dualVuView.invalidate()
                    stripViews.forEach { it.invalidate() }
                }
                animHandler.postDelayed(this, 90)
            }
        })
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animHandler.removeCallbacksAndMessages(null)
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
        var rmsLevel = 0.4f
        var peakLevel = 0.55f
        private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E7D8B1"); style = Paint.Style.FILL }
        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#334155"); strokeWidth = 2.5f; style = Paint.Style.STROKE }
        private val redArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#DC2626"); strokeWidth = 3.5f; style = Paint.Style.STROKE }
        private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B91C1C"); strokeWidth = 3.5f; strokeCap = Paint.Cap.ROUND }
        private val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E293B"); textSize = 15f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
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
                    b > active -> Color.parseColor("#1E293B")
                    b >= 8 -> colRed
                    b >= 6 -> colAmber
                    else -> colGreen
                }
                canvas.drawRect(barX, by - 4f, barX + 14f, by + 2f, ledPaint)
                canvas.drawRect(barX + 18f, by - 4f, barX + 32f, by + 2f, ledPaint)
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
        private val txtP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colTextMain; textSize = 18f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
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
            val cx = w / 2f; val cy = (h - 20f) / 2f
            val r = (min(w, h - 22f) / 2f) - 6f
            if (r <= 8f) return
            val rect = RectF(cx - r, cy - r, cx + r, cy + r)
            canvas.drawArc(rect, 135f, 270f, false, trackP)
            canvas.drawArc(rect, 135f, 270f * norm, false, arcP)
            canvas.drawCircle(cx, cy, r - 7f, bodyP)
            val rad = Math.toRadians((135f + 270f * norm).toDouble())
            canvas.drawLine(cx, cy, cx + cos(rad).toFloat() * (r - 12f), cy + sin(rad).toFloat() * (r - 12f), indP)
            canvas.drawText(label, cx, h - 3f, txtP)
        }
    }
    inner class MiniStripView(context: Context, private val idx: Int) : View(context) {
        private val bgP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colPanel; style = Paint.Style.FILL }
        private val bdrNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colBorder; strokeWidth = 2f; style = Paint.Style.STROKE }
        private val bdrSel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colAccent; strokeWidth = 3f; style = Paint.Style.STROKE }
        private val btnFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val txtP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colTextMain; textSize = 15f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        private val slotP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colBoxDark; strokeWidth = 7f; strokeCap = Paint.Cap.ROUND }
        private val capP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CBD5E1"); style = Paint.Style.FILL }
        private val capLineP = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f }
        private val ledP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val h = height.toFloat()
            if (h <= 0f) return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (event.y < 42f) {
                        selectedCh = idx
                        refreshHeaderReadout()
                        stripViews.forEach { it.invalidate() }
                        return true
                    } else if (event.y < 82f) {
                        chEnabled.set(idx, !chEnabled.get(idx))
                        invalidate()
                        return true
                    }
                    selectedCh = idx
                    refreshHeaderReadout()
                    stripViews.forEach { it.invalidate() }
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val topT = 112f; val botT = h - 32f
                    val n = 1f - ((event.y - topT) / (botT - topT)).coerceIn(0f, 1f)
                    chValues.set(idx, n)
                    refreshHeaderReadout()
                    miniCurveView.invalidate()
                    invalidate()
                    onConsoleChannelChanged?.invoke(idx, n)
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
            val rect = RectF(1f, 1f, w - 1f, h - 1f)
            canvas.drawRoundRect(rect, 10f, 10f, bgP)
            canvas.drawRoundRect(rect, 10f, 10f, if (isSel) bdrSel else bdrNormal)

            btnFill.color = if (isSel) colAccent else colBoxDark
            canvas.drawRoundRect(RectF(8f, 8f, w - 8f, 38f), 6f, 6f, btnFill)
            txtP.color = if (isSel) colBgDark else colTextDim
            canvas.drawText("SEL", w / 2f, 28f, txtP)

            val isOn = chEnabled.get(idx)
            btnFill.color = if (isOn) Color.parseColor("#132A24") else colBoxDark
            canvas.drawRoundRect(RectF(8f, 44f, w - 8f, 74f), 6f, 6f, btnFill)
            txtP.color = if (isOn) colGreen else colTextDim
            canvas.drawText(if (isOn) "ON" else "MUTE", w / 2f, 64f, txtP)

            txtP.color = if (idx == 8) colRed else colAccent
            canvas.drawText(chNames.get(idx), w / 2f, 96f, txtP)

            val topT = 112f; val botT = h - 32f
            val faderX = w * 0.62f
            val ledX = w * 0.24f
            canvas.drawLine(faderX, topT, faderX, botT, slotP)

            val v = chValues.get(idx)
            val activeLeds = if (isOn) (v * 11 + Random.nextInt(-1, 2)).coerceIn(1, 11) else 0
            for (l in 0 until 11) {
                val ly = botT - (l / 10f) * (botT - topT)
                ledP.color = when {
                    l > activeLeds -> colBoxDark
                    l >= 9 -> colRed
                    l >= 7 -> colAmber
                    else -> colGreen
                }
                canvas.drawCircle(ledX, ly, 3f, ledP)
            }

            val thumbY = botT - v * (botT - topT)
            capP.color = if (idx == 8) colRed else Color.parseColor("#CBD5E1")
            canvas.drawRoundRect(RectF(faderX - 18f, thumbY - 9f, faderX + 18f, thumbY + 9f), 4f, 4f, capP)
            capLineP.color = if (idx == 8) Color.WHITE else colAccent
            canvas.drawLine(faderX - 14f, thumbY, faderX + 14f, thumbY, capLineP)

            txtP.color = colTextMain
            txtP.textSize = 13f
            canvas.drawText(formatChannelValue(idx), w / 2f, h - 10f, txtP)
            txtP.textSize = 15f
        }
    }
}
