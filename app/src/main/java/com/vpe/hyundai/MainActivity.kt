package com.vpe.hyundai

import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val values = mutableMapOf(
        "차량 연식 (년)" to 5, "누적 주행거리 (천 km)" to 70,
        "연료 (%)" to 65, "관리 점수" to 80,
        "새로운 장소 비율 (%)" to 60, "장거리 비율 (%)" to 40
    )
    private lateinit var result: TextView
    private var persona: Persona? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 38, 36, 32)
            setBackgroundColor(Color.rgb(248, 250, 252))
        }
        scroll.addView(layout)
        val heading = TextView(this).apply {
            text = "🚗 VPE · Vehicle Persona Engine"
            textSize = 23f
            setTextColor(Color.rgb(20, 35, 55))
        }
        layout.addView(heading)
        layout.addView(TextView(this).apply {
            text = "차량 데이터를 바꾸면 성격과 목소리가 함께 변해요."
            textSize = 15f
            setPadding(0, 10, 0, 22)
        })
        for ((name, initial) in values.toMap()) {
            val maxValue = when(name) {
                "차량 연식 (년)" -> 25
                "누적 주행거리 (천 km)" -> 300
                else -> 100
            }
            val label = TextView(this).apply { text = "$name: $initial"; textSize = 16f }
            layout.addView(label)
            val seek = SeekBar(this).apply { max = maxValue; progress = initial }
            seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                    values[name] = progress
                    label.text = "$name: $progress"
                    render()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            layout.addView(seek)
        }
        result = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.rgb(30, 45, 70))
            setPadding(12, 24, 12, 24)
        }
        layout.addView(result)
        layout.addView(Button(this).apply {
            text = "🔊 차량 목소리 듣기"
            setOnClickListener {
                val p = persona ?: return@setOnClickListener
                if (ready) {
                    tts?.setSpeechRate(p.speechRate)
                    tts?.setPitch(p.pitch)
                    tts?.speak(p.line, TextToSpeech.QUEUE_FLUSH, null, "vpe-line")
                } else Toast.makeText(this@MainActivity, "TTS 엔진을 준비 중이에요.", Toast.LENGTH_SHORT).show()
            }
        })
        setContentView(scroll)
        render()
    }
    private fun render() {
        if (!::result.isInitialized) return
        val v = VehicleSnapshot(
            values.getValue("차량 연식 (년)").toDouble(),
            values.getValue("누적 주행거리 (천 km)") * 1000.0,
            values.getValue("연료 (%)").toDouble(),
            values.getValue("관리 점수").toDouble(),
            values.getValue("새로운 장소 비율 (%)") / 100.0,
            values.getValue("장거리 비율 (%)") / 100.0
        )
        val p = PersonaEngine.calculate(v)
        persona = p
        fun percent(x: Double) = (x * 100).toInt()
        result.text = """
            장기 성격
            활발함 ${percent(p.energy)} · 호기심 ${percent(p.curiosity)}
            친밀감 ${percent(p.warmth)} · 자신감 ${percent(p.confidence)}
            표현력 ${percent(p.expressiveness)}

            단기 상태
            배고픔 ${percent(p.hunger)} · 걱정 ${percent(p.concern)}

            음성 속도 ${"%.2f".format(Locale.US,p.speechRate)}×
            음높이 ${"%.2f".format(Locale.US,p.pitch)}×

            💬 ${p.line}
        """.trimIndent()
    }
    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS &&
            (tts?.setLanguage(Locale.KOREAN) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= 0
    }
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
