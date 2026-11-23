package com.custom.dspequalizer

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class SavedPresetView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onSavePresetRequest: ((String) -> JSONObject)? = null
    var onLoadPresetRequest: ((JSONObject) -> Unit)? = null

    private val prefs = context.getSharedPreferences("dsp_mini_pro_presets", Context.MODE_PRIVATE)
    private val colBgDark = Color.parseColor("#0D1116")
    private val colPanel = Color.parseColor("#161D24")
    private val colBoxDark = Color.parseColor("#0B0F14")
    private val colBorder = Color.parseColor("#26323D")
    private val colAccent = Color.parseColor("#38BDF8")
    private val colTeal = Color.parseColor("#2DD4BF")
    private val colGreen = Color.parseColor("#34D399")
    private val colRed = Color.parseColor("#F87171")
    private val colTextMain = Color.parseColor("#E2E8F0")
    private val colTextDim = Color.parseColor("#64748B")

    private lateinit var etPresetName: EditText
    private lateinit var listContainer: LinearLayout
    private lateinit var tvActivePreset: TextView

    init {
        orientation = VERTICAL
        setBackgroundColor(colBgDark)
        setPadding(12, 8, 12, 8)
        buildHeaderAndSaveBar()
        buildFactoryPresetsStrip()
        buildSavedListArea()
        refreshPresetList()
    }

    private fun styleBox(v: View, fill: Int = colPanel, radius: Float = 10f, stroke: Int = colBorder) {
        v.background = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            setStroke(2, stroke)
        }
    }

    private fun buildHeaderAndSaveBar() {
        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(2, 0, 2, 8)
            }
        }

        val titleCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.1f)
        }
        val tvTitle = TextView(context).apply {
            text = "PRESET MANAGER"
            setTextColor(colTextMain)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
        }
        tvActivePreset = TextView(context).apply {
            text = "Preset aktif: Default / Custom"
            setTextColor(colTeal)
            textSize = 9.5f
        }
        titleCol.addView(tvTitle); titleCol.addView(tvActivePreset)

        etPresetName = EditText(context).apply {
            hint = "Nama preset baru (misal: Horeg 12in)..."
            setHintTextColor(colTextDim)
            setTextColor(colTextMain)
            textSize = 11f
            isSingleLine = true
            styleBox(this, colBoxDark, 10f, colBorder)
            setPadding(16, 8, 16, 8)
            layoutParams = LayoutParams(0, 62, 1.4f).apply { setMargins(8, 0, 8, 0) }
        }

        val btnSave = Button(context).apply {
            text = "+ SIMPAN"
            textSize = 10.5f
            setTextColor(colBgDark)
            setTypeface(null, Typeface.BOLD)
            setPadding(16, 0, 16, 0)
            background = GradientDrawable().apply {
                setColor(colAccent)
                cornerRadius = 10f
            }
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, 62)
            setOnClickListener {
                val name = etPresetName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(context, "Ketik nama preset dulu", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val snapshot = onSavePresetRequest?.invoke(name) ?: return@setOnClickListener
                savePresetToStorage(name, snapshot)
                etPresetName.setText("")
                tvActivePreset.text = "Preset aktif: $name"
                refreshPresetList()
                Toast.makeText(context, "Preset '$name' tersimpan!", Toast.LENGTH_SHORT).show()
            }
        }


        topRow.addView(titleCol); topRow.addView(etPresetName); topRow.addView(btnSave)
        addView(topRow)
    }

    private fun buildFactoryPresetsStrip() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(2, 0, 2, 8)
            }
        }

        val lbl = TextView(context).apply {
            text = "QUICK FACTORY:"
            setTextColor(colTextDim)
            textSize = 9.5f
            setPadding(0, 0, 8, 0)
        }
        row.addView(lbl)

        val factories = listOf("FLAT REFERENCE", "SUB HOREG", "DANGDUT PUNCH", "VOCAL CLARITY")
        for (fName in factories) {
            val btn = Button(context).apply {
                text = fName
                textSize = 9f
                setTextColor(colAccent)
                styleBox(this, colPanel, 8f, colBorder)
                layoutParams = LayoutParams(0, 50, 1f).apply { setMargins(3, 0, 3, 0) }
                setPadding(4, 0, 4, 0)
                setOnClickListener {
                    val json = createFactoryPresetJson(fName)
                    onLoadPresetRequest?.invoke(json)
                    tvActivePreset.text = "Preset aktif: $fName (Factory)"
                    Toast.makeText(context, "Memuat preset $fName", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(btn)
        }
        addView(row)
    }

    private fun buildSavedListArea() {
        val scroll = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            styleBox(this, colBoxDark, 12f, colBorder)
            setPadding(10, 10, 10, 10)
        }
        listContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(listContainer)
        addView(scroll)
    }
    private fun savePresetToStorage(name: String, data: JSONObject) {
        val allStr = prefs.getString("saved_list", "{}") ?: "{}"
        val root = JSONObject(allStr)
        root.put(name, data)
        prefs.edit().putString("saved_list", root.toString()).apply()
    }

    private fun deletePresetFromStorage(name: String) {
        val allStr = prefs.getString("saved_list", "{}") ?: "{}"
        val root = JSONObject(allStr)
        root.remove(name)
        prefs.edit().putString("saved_list", root.toString()).apply()
        refreshPresetList()
    }

    private fun refreshPresetList() {
        listContainer.removeAllViews()
        val allStr = prefs.getString("saved_list", "{}") ?: "{}"
        val root = JSONObject(allStr)
        val keys = root.keys()

        if (!keys.hasNext()) {
            val emptyTv = TextView(context).apply {
                text = "Belum ada preset tersimpan.\nAtur EQ30, PARAM, FX, atau DYN lalu ketik nama di atas dan tekan '+ SIMPAN PRESET'."
                setTextColor(colTextDim)
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(20, 50, 20, 50)
            }
            listContainer.addView(emptyTv)
            return
        }

        while (keys.hasNext()) {
            val name = keys.next()
            val presetObj = root.getJSONObject(name)

            val itemCard = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                styleBox(this, colPanel, 10f, colBorder)
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 6)
                }
                setPadding(14, 10, 10, 10)
            }

            val infoCol = LinearLayout(context).apply {
                orientation = VERTICAL
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvName = TextView(context).apply {
                text = "●  $name"
                setTextColor(colTextMain)
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
            }
            val tvMeta = TextView(context).apply {
                val subDb = presetObj.optDouble("fx_subBass", 0.0)
                val drvDb = presetObj.optDouble("fx_tubeDrive", 0.0)
                text = String.format("Konfigurasi Lengkap • Sub Bass %+.1f dB • Drive %+.1f dB", subDb, drvDb)
                setTextColor(colTextDim)
                textSize = 9.5f
            }
            infoCol.addView(tvName); infoCol.addView(tvMeta)

            val btnLoad = Button(context).apply {
                text = "MUAT"
                textSize = 9.5f
                setTextColor(colGreen)
                styleBox(this, colBoxDark, 8f, colGreen)
                layoutParams = LayoutParams(130, 50).apply { setMargins(0, 0, 8, 0) }
                setOnClickListener {
                    onLoadPresetRequest?.invoke(presetObj)
                    tvActivePreset.text = "Preset aktif: $name"
                    Toast.makeText(context, "Preset '$name' diterapkan", Toast.LENGTH_SHORT).show()
                }
            }

            val btnDel = Button(context).apply {
                text = "HAPUS"
                textSize = 9.5f
                setTextColor(colRed)
                styleBox(this, colBoxDark, 8f, colBorder)
                layoutParams = LayoutParams(130, 50)
                setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle("Hapus Preset")
                        .setMessage("Yakin ingin menghapus preset '$name'?")
                        .setPositiveButton("Hapus") { _, _ -> deletePresetFromStorage(name) }
                        .setNegativeButton("Batal", null)
                        .show()
                }
            }

            itemCard.addView(infoCol); itemCard.addView(btnLoad); itemCard.addView(btnDel)
            listContainer.addView(itemCard)
        }
    }

    private fun createFactoryPresetJson(type: String): JSONObject {
        val obj = JSONObject()
        val eqL = JSONArray()
        val eqR = JSONArray()
        for (i in 0 until 30) {
            val g = when (type) {
                "SUB HOREG" -> when {
                    i in 2..6 -> 5.5
                    i in 7..10 -> 2.0
                    i in 24..28 -> 3.0
                    else -> 0.0
                }
                "DANGDUT PUNCH" -> when {
                    i in 3..7 -> 4.0
                    i in 8..11 -> 3.5
                    i in 18..22 -> 2.5
                    i in 25..28 -> 3.5
                    else -> 0.0
                }
                "VOCAL CLARITY" -> when {
                    i in 0..3 -> -2.0
                    i in 15..22 -> 3.0
                    i in 24..27 -> 2.5
                    else -> 0.0
                }
                else -> 0.0
            }
            eqL.put(g); eqR.put(g)
        }
        obj.put("eq30_L", eqL)
        obj.put("eq30_R", eqR)
        obj.put("fx_subsonic", if (type == "SUB HOREG") 32.0 else 0.0)
        obj.put("fx_subBass", if (type == "SUB HOREG") 6.0 else if (type == "DANGDUT PUNCH") 3.5 else 0.0)
        obj.put("fx_widener", if (type == "VOCAL CLARITY") 35 else 15)
        obj.put("fx_tubeDrive", if (type == "DANGDUT PUNCH") 3.0 else 0.0)
        return obj
    }
}
