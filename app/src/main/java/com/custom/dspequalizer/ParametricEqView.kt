package com.custom.dspequalizer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import kotlin.math.*

class ParametricEqView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    enum class FilterType(val label: String) {
        BELL("BELL"),
        LOW_SHELF("LOW SHELF"),
        HIGH_SHELF("HIGH SHELF"),
        HPF("HPF (CUT)"),
        LPF("LPF (CUT)")
    }

    data class EqNode(
        var freq: Float,
        var gain: Float,
        var q: Float,
        var type: FilterType = FilterType.BELL,
        var enabled: Boolean = true
    )

    val nodes = mutableListOf(
        EqNode(45f, 0f, 1.0f, FilterType.BELL),
        EqNode(120f, 0f, 1.0f, FilterType.BELL),
        EqNode(400f, 0f, 1.0f, FilterType.BELL),
        EqNode(1200f, 0f, 1.0f, FilterType.BELL),
        EqNode(3800f, 0f, 1.0f, FilterType.BELL),
        EqNode(10000f, 0f, 1.0f, FilterType.BELL)
    )

    var selectedIndex = 0
    var onEqChangedListener: (() -> Unit)? = null

    private val colBgDark = Color.parseColor("#0D1116")
    private val colPanel = Color.parseColor("#161D24")
    private val colBorder = Color.parseColor("#26323D")
    private val colAccent = Color.parseColor("#38BDF8")
    private val colSecondary = Color.parseColor("#2DD4BF")
    private val colTextMain = Color.parseColor("#E2E8F0")
    private val colTextDim = Color.parseColor("#64748B")

    private lateinit var spinnerPoint: Spinner
    private lateinit var spinnerType: Spinner
    private lateinit var curveCanvas: EqGraphCanvas
    private lateinit var knobFreq: RotaryKnobView
    private lateinit var knobGain: RotaryKnobView
    private lateinit var knobQ: RotaryKnobView
    private lateinit var tvFreqBox: TextView
    private lateinit var tvGainBox: TextView
    private lateinit var tvQBox: TextView

    private var isInternalUpdate = false

    init {
        orientation = VERTICAL
        setBackgroundColor(colBgDark)
        setPadding(10, 6, 10, 6)
        buildTopControlStrip()
        buildGraphArea()
        buildBottomKnobsArea()
        refreshAllControls()
    }
    private fun stylePillView(v: View) {
        val gd = android.graphics.drawable.GradientDrawable().apply {
            setColor(colPanel)
            cornerRadius = 10f
            setStroke(2, colBorder)
        }
        v.background = gd
    }

    private fun buildTopControlStrip() {
        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 6)
            }
        }

        spinnerPoint = Spinner(context).apply {
            layoutParams = LayoutParams(0, 68, 1.15f).apply { setMargins(0, 0, 6, 0) }
            stylePillView(this)
        }

        spinnerType = Spinner(context).apply {
            layoutParams = LayoutParams(0, 68, 1.25f).apply { setMargins(0, 0, 6, 0) }
            stylePillView(this)
            val types = FilterType.values().map { it.label }
            adapter = createDarkAdapter(types)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (isInternalUpdate) return
                    nodes.get(selectedIndex).type = FilterType.values().get(pos)
                    refreshAllControls()
                    notifyChange()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }

        val btnAdd = createTopButton("+ Titik") {
            if (nodes.size < 8) {
                nodes.add(EqNode(1000f, 0f, 1.0f, FilterType.BELL))
                selectedIndex = nodes.size - 1
                updatePointSpinnerAdapter()
                refreshAllControls()
                notifyChange()
            } else {
                Toast.makeText(context, "Maksimal 8 titik", Toast.LENGTH_SHORT).show()
            }
        }

        val btnRemove = createTopButton("- Titik") {
            if (nodes.size > 1) {
                nodes.removeAt(selectedIndex)
                selectedIndex = selectedIndex.coerceIn(0, nodes.size - 1)
                updatePointSpinnerAdapter()
                refreshAllControls()
                notifyChange()
            }
        }

        val btnReset = createTopButton("Atur ulang") {
            nodes.clear()
            nodes.addAll(listOf(
                EqNode(45f, 0f, 1.0f, FilterType.BELL),
                EqNode(120f, 0f, 1.0f, FilterType.BELL),
                EqNode(400f, 0f, 1.0f, FilterType.BELL),
                EqNode(1200f, 0f, 1.0f, FilterType.BELL),
                EqNode(3800f, 0f, 1.0f, FilterType.BELL),
                EqNode(10000f, 0f, 1.0f, FilterType.BELL)
            ))
            selectedIndex = 0
            updatePointSpinnerAdapter()
            refreshAllControls()
            notifyChange()
        }

        bar.addView(spinnerPoint)
        bar.addView(spinnerType)
        bar.addView(btnAdd)
        bar.addView(btnRemove)
        bar.addView(btnReset)
        addView(bar)
        updatePointSpinnerAdapter()
    }

    private fun createDarkAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {
            override fun getView(pos: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val tv = super.getView(pos, convertView, parent) as TextView
                tv.setTextColor(colAccent)
                tv.textSize = 11f
                tv.setPadding(14, 0, 14, 0)
                return tv
            }
            override fun getDropDownView(pos: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val tv = super.getDropDownView(pos, convertView, parent) as TextView
                tv.setBackgroundColor(colPanel)
                tv.setTextColor(colTextMain)
                tv.textSize = 12f
                tv.setPadding(20, 16, 20, 16)
                return tv
            }
        }
    }

    private fun updatePointSpinnerAdapter() {
        val labels = nodes.indices.map { "• Titik ${it + 1}" }
        spinnerPoint.adapter = createDarkAdapter(labels)
        spinnerPoint.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (isInternalUpdate) return
                selectedIndex = pos
                refreshAllControls()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun createTopButton(label: String, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = label
            isAllCaps = false
            textSize = 10.5f
            setTextColor(colTextMain)
            stylePillView(this)
            layoutParams = LayoutParams(0, 68, 0.9f).apply { setMargins(3, 0, 3, 0) }
            setPadding(4, 0, 4, 0)
            setOnClickListener { onClick() }
        }
    }

    private fun buildGraphArea() {
        curveCanvas = EqGraphCanvas(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.35f).apply {
                setMargins(0, 0, 0, 6)
            }
            stylePillView(this)
        }
        addView(curveCanvas)
    }
    private fun buildBottomKnobsArea() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f)
        }

        val pFreq = createKnobCard("FREQUENCY")
        knobFreq = RotaryKnobView(context, colAccent).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            onValueChanged = { norm ->
                val f = (10.0 * 2000.0.pow(norm.toDouble())).toFloat()
                nodes.get(selectedIndex).freq = f
                refreshAllControls(skipKnob = 1)
                notifyChange()
            }
        }
        tvFreqBox = createValueDisplay()
        val freqFooter = createFineTuneRow("Hz", tvFreqBox,
            onMinus = {
                val n = nodes.get(selectedIndex)
                n.freq = (n.freq - if (n.freq > 500f) 10f else 1f).coerceIn(10f, 20000f)
                refreshAllControls(); notifyChange()
            },
            onPlus = {
                val n = nodes.get(selectedIndex)
                n.freq = (n.freq + if (n.freq > 500f) 10f else 1f).coerceIn(10f, 20000f)
                refreshAllControls(); notifyChange()
            }
        )
        pFreq.addView(knobFreq); pFreq.addView(freqFooter)

        val pGain = createKnobCard("GAIN")
        knobGain = RotaryKnobView(context, colSecondary).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            onValueChanged = { norm ->
                val g = (norm - 0.5f) * 40f
                nodes.get(selectedIndex).gain = (g * 10f).roundToInt() / 10f
                refreshAllControls(skipKnob = 2)
                notifyChange()
            }
        }
        tvGainBox = createValueDisplay()
        val gainFooter = createFineTuneRow("dB", tvGainBox,
            onMinus = {
                val n = nodes.get(selectedIndex)
                n.gain = ((n.gain - 0.2f) * 10f).roundToInt() / 10f
                n.gain = n.gain.coerceIn(-20f, 20f)
                refreshAllControls(); notifyChange()
            },
            onPlus = {
                val n = nodes.get(selectedIndex)
                n.gain = ((n.gain + 0.2f) * 10f).roundToInt() / 10f
                n.gain = n.gain.coerceIn(-20f, 20f)
                refreshAllControls(); notifyChange()
            }
        )
        pGain.addView(knobGain); pGain.addView(gainFooter)

        val pQ = createKnobCard("Q / WIDTH")
        knobQ = RotaryKnobView(context, colAccent).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            onValueChanged = { norm ->
                val qVal = 0.2f + (norm * 9.8f)
                nodes.get(selectedIndex).q = (qVal * 100f).roundToInt() / 100f
                refreshAllControls(skipKnob = 3)
                notifyChange()
            }
        }
        tvQBox = createValueDisplay()
        val qFooter = createFineTuneRow("Q", tvQBox,
            onMinus = {
                val n = nodes.get(selectedIndex)
                n.q = ((n.q - 0.05f).coerceIn(0.2f, 10f) * 100f).roundToInt() / 100f
                refreshAllControls(); notifyChange()
            },
            onPlus = {
                val n = nodes.get(selectedIndex)
                n.q = ((n.q + 0.05f).coerceIn(0.2f, 10f) * 100f).roundToInt() / 100f
                refreshAllControls(); notifyChange()
            }
        )
        pQ.addView(knobQ); pQ.addView(qFooter)

        row.addView(pFreq)
        row.addView(pGain)
        row.addView(pQ)
        addView(row)
    }

    private fun createKnobCard(title: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(3, 0, 3, 0) }
            stylePillView(this)
            setPadding(8, 4, 8, 6)
            val tv = TextView(context).apply {
                text = title
                setTextColor(colTextDim)
                textSize = 9.5f
                gravity = Gravity.CENTER
            }
            addView(tv)
        }
    }

    private fun createValueDisplay(): TextView {
        return TextView(context).apply {
            setTextColor(colTextMain)
            textSize = 11f
            gravity = Gravity.CENTER
            stylePillView(this)
            layoutParams = LayoutParams(0, 52, 1f).apply { setMargins(4, 0, 4, 0) }
        }
    }

    private fun createFineTuneRow(unit: String, valueView: TextView, onMinus: () -> Unit, onPlus: () -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            val tvUnit = TextView(context).apply {
                text = unit
                setTextColor(colTextDim)
                textSize = 10f
                setPadding(2, 0, 4, 0)
            }
            val btnM = Button(context).apply {
                text = "-"
                textSize = 12f
                setTextColor(colTextMain)
                stylePillView(this)
                layoutParams = LayoutParams(52, 52).apply { setMargins(2, 0, 2, 0) }
                setPadding(0, 0, 0, 0)
                setOnClickListener { onMinus() }
            }
            val btnP = Button(context).apply {
                text = "+"
                textSize = 12f
                setTextColor(colTextMain)
                stylePillView(this)
                layoutParams = LayoutParams(52, 52).apply { setMargins(2, 0, 0, 0) }
                setPadding(0, 0, 0, 0)
                setOnClickListener { onPlus() }
            }
            addView(tvUnit); addView(valueView); addView(btnM); addView(btnP)
        }
    }

    private fun refreshAllControls(skipKnob: Int = 0) {
        isInternalUpdate = true
        val n = nodes.get(selectedIndex)
        if (spinnerPoint.selectedItemPosition != selectedIndex) {
            spinnerPoint.setSelection(selectedIndex)
        }
        val typeIdx = FilterType.values().indexOf(n.type)
        if (spinnerType.selectedItemPosition != typeIdx) {
            spinnerType.setSelection(typeIdx)
        }

        val freqNorm = (ln((n.freq.coerceIn(10f, 20000f) / 10f).toDouble()) / ln(2000.0)).toFloat().coerceIn(0f, 1f)
        val gainNorm = ((n.gain.coerceIn(-20f, 20f) / 40f) + 0.5f).coerceIn(0f, 1f)
        val qNorm = ((n.q.coerceIn(0.2f, 10f) - 0.2f) / 9.8f).coerceIn(0f, 1f)

        if (skipKnob != 1) knobFreq.setNormalizedValue(freqNorm)
        if (skipKnob != 2) knobGain.setNormalizedValue(gainNorm)
        if (skipKnob != 3) knobQ.setNormalizedValue(qNorm)

        knobFreq.subLabel = if (n.freq >= 1000f) String.format("%.2f kHz", n.freq / 1000f) else String.format("%.0f Hz", n.freq)
        knobGain.subLabel = String.format("%+.1f dB", n.gain)
        knobQ.subLabel = String.format("Q %.2f", n.q)

        tvFreqBox.text = String.format("%.2f", n.freq)
        tvGainBox.text = String.format("%+.1f", n.gain)
        tvQBox.text = String.format("%.2f", n.q)

        curveCanvas.invalidate()
        isInternalUpdate = false
    }

    private fun notifyChange() {
        onEqChangedListener?.invoke()
    }
    fun calculateNodeGainAt(node: EqNode, freqHz: Float): Float {
        if (!node.enabled) return 0f
        val octaves = (ln((freqHz / node.freq).toDouble()) / ln(2.0)).toFloat()
        return when (node.type) {
            FilterType.BELL -> {
                if (abs(node.gain) < 0.05f) return 0f
                val bw = 1.0f / node.q.coerceAtLeast(0.2f)
                node.gain * exp(-0.5 * (abs(octaves) / (bw * 0.55)).pow(2.0)).toFloat()
            }
            FilterType.LOW_SHELF -> {
                val slope = node.q.coerceIn(0.3f, 3.0f) * 2.0f
                node.gain / (1.0f + exp(octaves * slope))
            }
            FilterType.HIGH_SHELF -> {
                val slope = node.q.coerceIn(0.3f, 3.0f) * 2.0f
                node.gain / (1.0f + exp(-octaves * slope))
            }
            FilterType.HPF -> {
                if (freqHz >= node.freq) 0f
                else (-12f * node.q.coerceIn(0.5f, 3f) * abs(octaves)).coerceAtLeast(-24f)
            }
            FilterType.LPF -> {
                if (freqHz <= node.freq) 0f
                else (-12f * node.q.coerceIn(0.5f, 3f) * abs(octaves)).coerceAtLeast(-24f)
            }
        }
    }

    fun calculateTotalGainAt(freqHz: Float): Float {
        var sum = 0f
        for (n in nodes) sum += calculateNodeGainAt(n, freqHz)
        return sum
    }

    inner class EqGraphCanvas(context: Context) : View(context) {
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E293B"); strokeWidth = 2f }
        private val zeroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#334155"); strokeWidth = 2.5f }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colTextDim; textSize = 21f }
        private val mainCurvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colAccent; strokeWidth = 5f; style = Paint.Style.STROKE }
        private val selectedBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colSecondary; strokeWidth = 3f; style = Paint.Style.STROKE }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 3f; style = Paint.Style.STROKE }

        private var isDraggingNode = false

        private fun freqToX(f: Float, w: Float): Float =
            (ln((f.coerceIn(10f, 20000f) / 10f).toDouble()) / ln(2000.0) * w).toFloat()

        private fun xToFreq(x: Float, w: Float): Float =
            (10.0 * 2000.0.pow((x / w).coerceIn(0f, 1f).toDouble())).toFloat()

        private fun gainToY(g: Float, h: Float): Float =
            (h / 2f) - (g.coerceIn(-20f, 20f) / 20f) * (h * 0.42f)

        private fun yToGain(y: Float, h: Float): Float =
            (((h / 2f) - y) / (h * 0.42f) * 20f).coerceIn(-20f, 20f)

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    var closestIdx = -1
                    var minDist = 80f
                    for (i in nodes.indices) {
                        val nx = freqToX(nodes.get(i).freq, w)
                        val ny = gainToY(nodes.get(i).gain, h)
                        val d = hypot(event.x - nx, event.y - ny)
                        if (d < minDist) {
                            minDist = d
                            closestIdx = i
                        }
                    }
                    if (closestIdx != -1) {
                        selectedIndex = closestIdx
                        isDraggingNode = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        refreshAllControls()
                        return true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingNode) {
                        val n = nodes.get(selectedIndex)
                        n.freq = xToFreq(event.x, w)
                        if (n.type != FilterType.HPF && n.type != FilterType.LPF) {
                            n.gain = (yToGain(event.y, h) * 10f).roundToInt() / 10f
                        }
                        refreshAllControls()
                        notifyChange()
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDraggingNode = false
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

            val midY = h / 2f
            val dbLevels = floatArrayOf(20f, 10f, 0f, -10f, -20f)
            for (db in dbLevels) {
                val y = gainToY(db, h)
                canvas.drawLine(0f, y, w, y, if (db == 0f) zeroPaint else gridPaint)
                if (db != 0f) canvas.drawText(if (db > 0) "+${db.toInt()}" else "${db.toInt()}", 8f, y - 4f, labelPaint)
            }

            val freqs = floatArrayOf(20f, 50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f, 20000f)
            val labels = arrayOf("20", "50", "100", "200", "500", "1k", "2k", "5k", "10k", "20k")
            for (i in freqs.indices) {
                val x = freqToX(freqs.get(i), w)
                canvas.drawLine(x, 0f, x, h, gridPaint)
                canvas.drawText(labels.get(i), x + 4f, h - 8f, labelPaint)
            }

            val selPath = Path()
            val totalPath = Path()
            val fillPath = Path()
            fillPath.moveTo(0f, midY)

            val steps = 140
            for (s in 0..steps) {
                val ratio = s.toFloat() / steps
                val f = (10.0 * 2000.0.pow(ratio.toDouble())).toFloat()
                val x = ratio * w

                val selY = gainToY(calculateNodeGainAt(nodes.get(selectedIndex), f), h)
                val totY = gainToY(calculateTotalGainAt(f), h)

                if (s == 0) {
                    selPath.moveTo(x, selY)
                    totalPath.moveTo(x, totY)
                } else {
                    selPath.lineTo(x, selY)
                    totalPath.lineTo(x, totY)
                }
                fillPath.lineTo(x, totY)
            }
            fillPath.lineTo(w, midY)
            fillPath.close()

            fillPaint.shader = LinearGradient(0f, 0f, 0f, h, Color.parseColor("#3338BDF8"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(selPath, selectedBandPaint)
            canvas.drawPath(totalPath, mainCurvePaint)

            for (i in nodes.indices) {
                val n = nodes.get(i)
                val nx = freqToX(n.freq, w)
                val ny = gainToY(if (n.type == FilterType.HPF || n.type == FilterType.LPF) 0f else n.gain, h)
                nodePaint.color = if (i == selectedIndex) colAccent else colSecondary
                val radius = if (i == selectedIndex) 16f else 11f
                canvas.drawCircle(nx, ny, radius, nodePaint)
                if (i == selectedIndex) canvas.drawCircle(nx, ny, radius + 4f, ringPaint)
            }
        }
    }
    inner class RotaryKnobView(context: Context, private val accentColor: Int) : View(context) {
        private var normValue = 0.5f
        var subLabel = ""
        var onValueChanged: ((Float) -> Unit)? = null

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#26323D"); strokeWidth = 8f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val activeArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor; strokeWidth = 8f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E2630"); style = Paint.Style.FILL
        }
        private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colTextDim; textSize = 20f; textAlign = Paint.Align.CENTER
        }

        private var lastTouchY = 0f

        fun setNormalizedValue(v: Float) {
            normValue = v.coerceIn(0f, 1f)
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = lastTouchY - event.y
                    lastTouchY = event.y
                    normValue = (normValue + dy / 260f).coerceIn(0f, 1f)
                    invalidate()
                    onValueChanged?.invoke(normValue)
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
            val cy = (h - 22f) / 2f
            val radius = (min(w, h - 24f) / 2f) - 10f
            if (radius <= 10f) return

            val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(rect, 135f, 270f, false, trackPaint)
            canvas.drawArc(rect, 135f, 270f * normValue, false, activeArcPaint)
            canvas.drawCircle(cx, cy, radius - 10f, bodyPaint)

            val angleRad = Math.toRadians((135f + 270f * normValue).toDouble())
            val ix = cx + cos(angleRad).toFloat() * (radius - 16f)
            val iy = cy + sin(angleRad).toFloat() * (radius - 16f)
            canvas.drawLine(cx, cy, ix, iy, indicatorPaint)

            canvas.drawText(subLabel, cx, h - 4f, textPaint)
        }
    }
}
