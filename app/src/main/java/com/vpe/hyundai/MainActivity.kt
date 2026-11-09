package com.vpe.hyundai

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

/** VPE onboarding and conversational dashboard. No fake SDK success states. */
class MainActivity : Activity() {
    private lateinit var sdk: PleosVehicleClient
    private lateinit var store: IdentityStore
    private lateinit var page: LinearLayout
    private lateinit var steps: TextView
    private lateinit var headline: TextView
    private lateinit var subtitle: TextView
    private lateinit var detail: TextView
    private lateinit var chat: LinearLayout
    private var identity: VpeIdentity? = null
    private var readings = VehicleReadings()
    private var lastSnapshot: VehicleSnapshot? = null
    private val navy = Color.rgb(12, 19, 34)
    private val blue = Color.rgb(126, 171, 252)
    private val white = Color.rgb(246, 249, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sdk = PleosVehicleClient(this)
        store = IdentityStore(this)
        identity = store.load()?.takeIf { it.vehicleKey != "demo-vehicle" }
        showDashboard()
    }

    private fun panel(color: Int = Color.rgb(20, 30, 49), radius: Float = 24f): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius * resources.displayMetrics.density }

    private fun label(value: String, size: Float, color: Int = white, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, 7, 0, 7)
        }

    private fun action(title: String, callback: () -> Unit): Button =
        Button(this).apply {
            text = title
            textSize = 15f
            isAllCaps = false
            setTextColor(navy)
            background = panel(white, 28f)
            setOnClickListener { callback() }
        }

    private fun addSpace(height: Int = 22) {
        page.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, (height * resources.displayMetrics.density).toInt())
        })
    }

    private fun showDashboard() {
        val scroll = ScrollView(this).apply { setBackgroundColor(navy); fillViewport = true }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (24 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        scroll.addView(page)
        setContentView(scroll)

        page.addView(label("VPE  /  VEHICLE PERSONA ENGINE", 13f, blue, true))
        addSpace(26)
        page.addView(label("◉  VPE INITIALIZATION", 14f, blue, true))
        addSpace(36)
        page.addView(label("▏▎▍▌▋▊▉▋▌▍▎▏", 28f, blue).apply { gravity = Gravity.CENTER })
        addSpace(36)
        headline = label(if (identity == null) "VPE 연동 준비" else "나의 차량 페르소나", 27f, white, true)
            .apply { gravity = Gravity.CENTER }
        page.addView(headline)
        subtitle = label("차량 데이터를 안전하게 불러옵니다.", 15f, Color.LTGRAY)
            .apply { gravity = Gravity.CENTER }
        page.addView(subtitle)
        addSpace(32)
        steps = label("", 15f)
        page.addView(steps)
        addSpace(20)
        page.addView(action("차량 데이터 연결 · 분석 시작") { connect() })
        addSpace(14)
        detail = label("실제 SDK 응답을 기다리는 중입니다.", 12f, Color.LTGRAY)
        page.addView(detail)
        addSpace(25)
        page.addView(label("VPE  /  CONVERSATION", 13f, blue, true))
        addSpace(8)
        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            background = panel()
        }
        page.addView(chat)
        addMessage("VPE", "안녕! 차량 연결이 완료되면 내 성격과 상태를 여기서 이야기할 수 있어.")
        addSpace(12)
        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val input = EditText(this).apply {
            hint = "VPE에게 메시지 보내기"
            setHintTextColor(Color.GRAY)
            setTextColor(white)
            textSize = 14f
            setSingleLine(true)
            background = panel()
            setPadding(16, 8, 16, 8)
        }
        inputRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        inputRow.addView(Button(this).apply {
            text = "전송"; isAllCaps = false
            setOnClickListener {
                val message = input.text.toString().trim()
                if (message.isNotEmpty()) {
                    addMessage("나", message)
                    input.setText("")
                    respond(message)
                }
            }
        })
        page.addView(inputRow)
        addSpace(24)
        page.addView(label("개발자 진단", 14f, blue, true))
        page.addView(action("SDK 조회 로그 다시 확인") { connect() })
        addSpace(8)
        page.addView(label("보이스 오디오/STT 및 LLM 대화는 아직 연결되지 않았습니다. 현재 채팅은 차량 상태 기반 규칙 응답입니다.", 12f, Color.LTGRAY))
        refresh()
    }

    private fun addMessage(sender: String, message: String) {
        chat.addView(label("$sender  ·  $message", 14f).apply {
            setPadding(14, 10, 14, 10)
            background = panel(if (sender == "나") Color.rgb(34, 54, 84) else Color.rgb(23, 38, 61), 14f)
        })
        chat.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, 9)
        })
    }

    private fun respond(message: String) {
        val id = identity
        if (id == null) {
            addMessage("VPE", "아직 실제 차량 정보가 연결되지 않았어. 먼저 차량 데이터 연결을 진행해 줘.")
            return
        }
        val v = lastSnapshot ?: id.lastLearned
        val p = PersonaEngine.calculate(v)
        val response = when {
            listOf("연료", "기름", "배터리").any { it in message } ->
                if (readings.fuelPercent != null) "현재 연료량은 ${readings.fuelPercent?.toInt()}%야. ${p.line}"
                else "연료 데이터가 아직 확인되지 않았어."
            listOf("거리", "주행").any { it in message } ->
                if (readings.odometerKm != null) "확인된 누적 주행거리는 ${readings.odometerKm?.toInt()}km야."
                else "주행거리 데이터를 아직 확인하지 못했어."
            listOf("성격", "누구").any { it in message } ->
                "내 호기심은 ${(id.memory.traits[1] * 100).toInt()}%, 친밀감은 ${(id.memory.traits[2] * 100).toInt()}%야."
            else -> p.line
        }
        addMessage("VPE", response)
    }

    private fun connect() {
        headline.text = "VPE 연동 중"
        subtitle.text = "Pleos 차량 데이터를 확인하고 있어요."
        steps.text = "◉ 차량 데이터 연결\n○ 데이터 정규화 및 가중치 분석\n○ 기본 성격 및 보이스 생성\n○ VPE Identity 생성 완료"
        detail.text = "SDK 응답 대기 중…"
        val logs = mutableListOf<String>()
        sdk.readVehicleData { message, partial ->
            runOnUiThread {
                logs.add(message)
                detail.text = logs.takeLast(8).joinToString("\n")
                if (partial != null) {
                    readings = readings.copy(
                        odometerKm = partial.odometerKm ?: readings.odometerKm,
                        fuelPercent = partial.fuelPercent ?: readings.fuelPercent,
                        modelYear = partial.modelYear ?: readings.modelYear
                    )
                    applyReadings()
                }
            }
        }
    }

    private fun applyReadings() {
        // Never create a persona from absent required measurements.
        val km = readings.odometerKm
        val fuel = readings.fuelPercent
        if (km == null || fuel == null) {
            subtitle.text = "SDK 연결 중 · 주행거리와 연료값 확인 대기"
            return
        }
        val prior = identity
        // Unavailable history-derived features are neutral placeholders, explicitly not SDK facts.
        val seed = VehicleSnapshot(0.0, km, fuel, 50.0, 0.5, 0.5)
        val snap = if (prior == null) seed else VehicleDataMapper.merge(
            prior.lastLearned, readings, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        )
        lastSnapshot = snap
        if (prior == null) {
            identity = IdentityEngine.generate("pleos-sdk-observed", snap)
        } else {
            identity = IdentityEngine.evolve(prior, snap)
        }
        identity?.let(store::save)
        refresh()
    }

    private fun refresh() {
        val id = identity
        if (id == null) {
            steps.text = "○ 차량 데이터 연결\n○ 데이터 정규화 및 가중치 분석\n○ 기본 성격 및 보이스 생성\n○ VPE Identity 생성 완료"
        } else {
            headline.text = "VPE Identity 생성 완료"
            subtitle.text = "차량의 고유 페르소나가 저장되었어요."
            steps.text = "● 차량 데이터 연결\n● 데이터 정규화 및 가중치 분석\n● 기본 성격 및 보이스 생성\n● VPE Identity 생성 완료"
            addMessage("VPE", "반가워! 나는 ${id.voiceBlueprint.ageGroup} ${id.voiceBlueprint.presentation} 보이스 프로필을 가진 VPE야. ${PersonaEngine.calculate(lastSnapshot ?: id.lastLearned).line}")
        }
    }

    override fun onDestroy() {
        sdk.release()
        super.onDestroy()
    }
}
