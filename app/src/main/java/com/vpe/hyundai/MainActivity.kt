package com.vpe.hyundai

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import java.util.Locale

/** Voice synthesis intentionally excluded: only a deterministic voice blueprint is produced. */
class MainActivity : Activity() {
    private lateinit var pleos: PleosVehicleClient
    private lateinit var store: IdentityStore
    private lateinit var status: TextView
    private lateinit var output: TextView
    private var identity: VpeIdentity? = null
    private val values = linkedMapOf(
        "차량 연식 (년)" to 5, "누적 주행거리 (천 km)" to 70,
        "연료 (%)" to 65, "관리 점수" to 80,
        "새로운 장소 비율 (%)" to 60, "장거리 비율 (%)" to 40
    )
    private fun snapshot() = VehicleSnapshot(
        values.getValue("차량 연식 (년)").toDouble(),
        values.getValue("누적 주행거리 (천 km)") * 1000.0,
        values.getValue("연료 (%)").toDouble(),
        values.getValue("관리 점수").toDouble(),
        values.getValue("새로운 장소 비율 (%)") / 100.0,
        values.getValue("장거리 비율 (%)") / 100.0
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = IdentityStore(this)
        pleos = PleosVehicleClient(this)
        identity = store.load()
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 40, 36, 36)
            setBackgroundColor(Color.rgb(247,249,252))
        }
        scroll.addView(root)
        root.addView(TextView(this).apply {
            text = "🚗 VPE · Vehicle Persona Engine"
            textSize = 23f
            setTextColor(Color.rgb(22,38,61))
        })
        root.addView(TextView(this).apply {
            text = "최초 연동 → 데이터 분석 → 가중치 생성 → 성격·보이스 설계값 저장"
            textSize = 14f
        })
        root.addView(TextView(this).apply {
            text = "현재 데이터 입력은 테스트용 시뮬레이션입니다. SDK 조회는 별도 연결 테스트입니다."
            textSize = 13f
            setPadding(0,12,0,12)
        })
        for ((name, initial) in values.toMap()) {
            val label = TextView(this).apply { text = "$name: $initial"; textSize = 15f }
            root.addView(label)
            root.addView(SeekBar(this).apply {
                max = when(name) {
                    "차량 연식 (년)" -> 25
                    "누적 주행거리 (천 km)" -> 300
                    else -> 100
                }
                progress = initial
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                        values[name] = progress
                        label.text = "$name: $progress"
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
            })
        }
        status = TextView(this).apply { textSize = 15f; setPadding(0,16,0,12) }
        root.addView(status)
        output = TextView(this).apply { textSize = 16f; setPadding(0,12,0,16) }
        root.addView(output)
        root.addView(Button(this).apply {
            text = "VPE 최초 연동 · Identity 생성"
            setOnClickListener {
                if (identity != null) {
                    status.text = "이미 생성된 Identity가 있어요. 초기화를 원하면 아래 버튼을 사용하세요."
                } else {
                    status.text = "VPE 연동 중… 차량 데이터를 분석하고 보이스 설계값을 생성하고 있습니다."
                    // Prototype vehicle key; replace with a stable authorized vehicle identifier after SDK mapping.
                    val created = IdentityEngine.generate("demo-vehicle", snapshot())
                    store.save(created)
                    identity = created
                    status.text = "VPE Identity 생성 완료 · 기기에 저장됨"
                }
                render()
            }
        })
        root.addView(Button(this).apply {
            text = "운행 경험 반영 · 장기 성격 성장"
            setOnClickListener {
                val current = identity
                if (current == null) {
                    status.text = "먼저 최초 Identity를 생성하세요."
                } else {
                    identity = IdentityEngine.evolve(current, snapshot()).also(store::save)
                    status.text = "장기 성격이 갱신됐어요. 기본 보이스 정체성은 유지됩니다."
                }
                render()
            }
        })
        root.addView(Button(this).apply {
            text = "테스트 Identity 초기화"
            setOnClickListener {
                store.clear()
                identity = null
                status.text = "테스트 Identity 삭제 완료"
                render()
            }
        })
        root.addView(Button(this).apply {
            text = "Pleos SDK 연결 · 운전석 도어 조회 테스트"
            setOnClickListener {
                status.text = "SDK 연결 확인 중…"
                pleos.readDriverDoor { message -> runOnUiThread { status.text = message } }
            }
        })
        setContentView(scroll)
        status.text = if (identity == null) "최초 Identity 생성 대기 중" else "저장된 VPE Identity 복원 완료"
        render()
    }
    private fun render() {
        if (!::output.isInitialized) return
        val id = identity
        if (id == null) {
            output.text = "차량 데이터에 따라 고유 성격과 보이스 정체성을 생성합니다."
            return
        }
        fun pct(d: Double) = (d * 100).toInt()
        val p = id.baseline
        val b = id.voiceBlueprint
        val current = PersonaEngine.calculate(snapshot())
        output.text = """
            [최초 생성된 성격]
            활발함 ${pct(p.energy)}% · 호기심 ${pct(p.curiosity)}%
            친밀감 ${pct(p.warmth)}% · 자신감 ${pct(p.confidence)}%
            표현력 ${pct(p.expressiveness)}%

            [현재 장기 성격 기억]
            ${id.memory.traits.joinToString(" / ") { "%.2f".format(Locale.US,it) }}

            [최초 생성된 기본 보이스 설계값]
            ${b.ageGroup} · ${b.presentation}
            성별 표현 ${pct(b.genderExpression)}%
            음높이 ${"%.2f".format(Locale.US,b.pitch)}
            공명 ${"%.2f".format(Locale.US,b.resonance)}
            부드러움 ${pct(b.softness)}%
            속도 ${"%.2f".format(Locale.US,b.speechRate)}

            [현재 단기 감정]
            배고픔 ${pct(current.hunger)}% · 걱정 ${pct(current.concern)}%
            💬 ${current.line}

            보이스 오디오 생성: 미연동
            데이터 출처: 수동 시뮬레이션
        """.trimIndent()
    }
    override fun onDestroy() {
        pleos.release()
        super.onDestroy()
    }
}
