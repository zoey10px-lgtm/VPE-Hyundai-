package com.vpe.hyundai

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*

/** Three separate app destinations: Home, Conversation, Developer diagnostics. */
class MainActivity : Activity() {
    private lateinit var sdk: PleosVehicleClient
    private lateinit var store: IdentityStore
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var navigation: LinearLayout
    private var identity: VpeIdentity? = null
    private var readings = VehicleReadings()
    private var snapshot: VehicleSnapshot? = null
    private var status = "연결 대기"
    private var description = "Pleos 차량 환경에서 연결을 시작하세요."
    private var busy = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var requestId = 0
    private val diagnostics = mutableListOf<String>()
    private val conversation = mutableListOf<Pair<String, String>>()
    private var waveAnimator: ValueAnimator? = null
    private val bg = Color.rgb(9, 15, 28)
    private val surface = Color.rgb(20, 31, 50)
    private val surface2 = Color.rgb(30, 45, 69)
    private val accent = Color.rgb(117, 164, 249)
    private val white = Color.rgb(244, 247, 255)
    private val muted = Color.rgb(163, 177, 198)
    private val dp get() = resources.displayMetrics.density
    private fun px(value: Int) = (value * dp).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sdk = PleosVehicleClient(this)
        store = IdentityStore(this)
        identity = store.load()?.takeIf { it.vehicleKey != "demo-vehicle" }
        if (identity != null) {
            status = "페르소나 저장됨"
            description = "저장된 성격을 불러왔어요. 차량 상태는 다시 연결해 확인하세요."
        }
        conversation.add("VPE" to "안녕! 차량 연결 후에는 주행 상태와 내 성격에 관해 이야기할 수 있어.")
        show("home")
    }

    private fun shape(color: Int, radius: Int = 20): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = px(radius).toFloat() }

    private fun text(value: String, size: Float = 15f, color: Int = white, bold: Boolean = false) =
        TextView(this).apply {
            this.text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            includeFontPadding = true
        }

    private fun gap(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, px(h)) }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(px(20), px(20), px(20), px(20))
        background = shape(surface, 22)
    }

    private fun button(label: String, primary: Boolean = true, click: () -> Unit) =
        TextView(this).apply {
            text = label; textSize = 15f; gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (primary) bg else white)
            background = shape(if (primary) accent else surface2, 18)
            setPadding(px(16), px(17), px(16), px(17))
            setOnClickListener { click() }
        }

    private fun show(destination: String) {
        waveAnimator?.cancel()
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(px(22), px(22), px(22), px(14))
        }
        header.addView(text("VPE", 25f, white, true))
        header.addView(text("   VEHICLE PERSONA", 11f, accent, true))
        root.addView(header)
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(14), px(20), px(24))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(px(12), px(9), px(12), px(12))
            background = shape(surface, 0)
        }
        listOf(Triple("⌂  홈", "home", "home"), Triple("◉  대화", "chat", "chat"),
            Triple("⚙  진단", "diagnostics", "diagnostics")).forEach { (label, target, _) ->
            val item = text(label, 14f, if (destination == target) accent else muted, destination == target).apply {
                gravity = Gravity.CENTER; setPadding(0, px(12), 0, px(12))
                setOnClickListener { show(target) }
            }
            navigation.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(navigation)
        setContentView(root)
        when (destination) {
            "chat" -> chatScreen()
            "diagnostics" -> diagnosticScreen()
            else -> homeScreen()
        }
    }

    private fun homeScreen() {
        body.addView(text("MY VEHICLE", 12f, accent, true))
        body.addView(gap(8))
        body.addView(text("차량과 나만의 AI", 29f, white, true))
        body.addView(gap(6))
        body.addView(text("차량의 경험이 성격이 되는 공간", 14f, muted))
        body.addView(gap(26))
        val hero = card()
        hero.addView(text("VPE  ·  INITIALIZATION", 12f, accent, true))
        hero.addView(gap(25))
        val wave = text("▏▎▍▌▋▊▉▊▋▌▍▎▏", 25f, accent, true).apply { gravity = Gravity.CENTER }
        hero.addView(wave)
        if (busy) {
            waveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 950; repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener { wave.alpha = 0.35f + 0.65f * (it.animatedValue as Float) }
                start()
            }
        }
        hero.addView(gap(25))
        hero.addView(text(status, 24f, white, true).apply { gravity = Gravity.CENTER })
        hero.addView(gap(8))
        hero.addView(text(description, 13f, muted).apply { gravity = Gravity.CENTER })
        hero.addView(gap(25))
        val stage = if (identity != null) 4 else if (busy) 1 else 0
        listOf("차량 데이터 연결", "데이터 분석", "성격·보이스 설계", "Identity 저장").forEachIndexed { index, name ->
            hero.addView(text("${if (index < stage) "●" else "○"}   $name", 14f,
                if (index < stage) accent else muted))
            hero.addView(gap(10))
        }
        hero.addView(gap(12))
        hero.addView(button(if (busy) "차량 데이터 조회 중…" else if (identity == null)
            "차량 연결 시작" else "차량 상태 새로고침") { if (!busy) connect() })
        body.addView(hero)
        body.addView(gap(22))
        if (identity != null) {
            val id = identity!!
            val profile = card()
            profile.addView(text("나의 VPE 프로필", 18f, white, true))
            profile.addView(gap(12))
            profile.addView(text("보이스 설계  ·  ${id.voiceBlueprint.ageGroup} / ${id.voiceBlueprint.presentation}", 14f, muted))
            profile.addView(gap(8))
            profile.addView(text("성격 성장 횟수  ·  ${id.learningEvents}회", 14f, muted))
            profile.addView(gap(16))
            profile.addView(button("VPE와 대화하기") { show("chat") })
            body.addView(profile)
        } else {
            body.addView(text("연결 안내", 17f, white, true))
            body.addView(gap(8))
            body.addView(text("차량 데이터는 Pleos 권한이 있는 실행 환경에서 읽습니다. 일반 스마트폰에서는 차량 접근이 제한될 수 있어요.", 13f, muted))
        }
    }

    private fun chatScreen() {
        body.addView(text("VPE와 대화", 27f, white, true))
        body.addView(gap(7))
        body.addView(text("차량의 상태와 성격을 대화로 확인하세요.", 13f, muted))
        body.addView(gap(22))
        val messages = card()
        conversation.forEach { (sender, content) ->
            val bubble = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(14), px(12), px(14), px(12))
                background = shape(if (sender == "나") surface2 else Color.rgb(25, 40, 63), 14)
            }
            bubble.addView(text(sender, 12f, accent, true))
            bubble.addView(gap(4))
            bubble.addView(text(content, 15f))
            messages.addView(bubble)
            messages.addView(gap(10))
        }
        body.addView(messages)
        body.addView(gap(16))
        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val input = EditText(this).apply {
            hint = "메시지를 입력하세요"; setHintTextColor(muted); setTextColor(white)
            textSize = 14f; setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEND
            background = shape(surface, 14)
            setPadding(px(14), px(12), px(14), px(12))
        }
        fun send() {
            val message = input.text.toString().trim()
            if (message.isNotEmpty()) {
                conversation.add("나" to message)
                conversation.add("VPE" to respond(message))
                show("chat")
            }
        }
        input.setOnEditorActionListener { _, _, _ -> send(); true }
        inputRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        val sendButton = button("전송") { send() }
        val lp = LinearLayout.LayoutParams(px(78), -2).apply { leftMargin = px(8) }
        inputRow.addView(sendButton, lp)
        body.addView(inputRow)
        body.addView(gap(15))
        body.addView(text("현재는 차량 상태 기반 규칙 응답입니다. LLM·음성 대화는 아직 미연동입니다.", 12f, muted))
    }

    private fun respond(message: String): String {
        val id = identity ?: return "아직 차량 연결이 완료되지 않았어. 홈에서 차량 연결을 시작해 줘."
        val persona = PersonaEngine.calculate(snapshot ?: id.lastLearned)
        return when {
            listOf("연료", "기름", "배터리").any { it in message } ->
                readings.fuelPercent?.let { "확인된 연료량은 ${it.toInt()}%야." }
                    ?: "실시간 연료 데이터를 아직 확인하지 못했어."
            listOf("거리", "주행").any { it in message } ->
                readings.odometerKm?.let { "누적 주행거리는 ${it.toInt()}km야." }
                    ?: "실시간 주행거리 데이터가 아직 없어."
            listOf("성격", "누구").any { it in message } ->
                "내 호기심은 ${(id.memory.traits[1] * 100).toInt()}%, 친밀감은 ${(id.memory.traits[2] * 100).toInt()}%야."
            else -> persona.line
        }
    }

    private fun diagnosticScreen() {
        body.addView(text("개발자 진단", 27f, white, true))
        body.addView(gap(8))
        body.addView(text("SDK 응답과 권한 상태를 확인하는 개발 전용 화면", 13f, muted))
        body.addView(gap(22))
        body.addView(button("SDK 조회 다시 실행") { connect() })
        body.addView(gap(18))
        val logCard = card()
        if (diagnostics.isEmpty()) logCard.addView(text("아직 조회 기록이 없습니다.", 14f, muted))
        diagnostics.takeLast(25).forEach {
            logCard.addView(text(it, 12f, muted))
            logCard.addView(gap(9))
        }
        body.addView(logCard)
    }

    private fun connect() {
        busy = true
        val currentRequest = ++requestId
        mainHandler.postDelayed({
            if (busy && requestId == currentRequest) {
                busy = false
                status = "연결 응답 시간 초과"
                description = "차량 서비스에서 응답하지 않았어요. 진단 탭에서 확인해 주세요."
                diagnostics.add("SDK 조회 시간 초과: 12초 동안 필수 차량 데이터 미수신")
                show("home")
            }
        }, 12000L)
        status = "차량 데이터 확인 중"
        description = "Pleos SDK 응답을 기다리고 있어요."
        show("home")
        var accepted = false
        sdk.readVehicleData { message, partial ->
            runOnUiThread {
                if (requestId != currentRequest) return@runOnUiThread
                diagnostics.add(message)
                if (partial != null) {
                    readings = readings.copy(
                        odometerKm = partial.odometerKm ?: readings.odometerKm,
                        fuelPercent = partial.fuelPercent ?: readings.fuelPercent,
                        modelYear = partial.modelYear ?: readings.modelYear
                    )
                    accepted = true
                    applyReadings()
                }
                if (message.contains("SecurityException") || message.contains("need Permission")) {
                    busy = false
                    status = "차량 접근 권한 확인 필요"
                    description = "Pleos 차량 서비스 권한이 거부됐어요. 진단 탭에서 확인할 수 있어요."
                    show("home")
                } else if (!accepted && message.contains("없습니다")) {
                    busy = false
                    status = "조회 API 확인 필요"
                    description = "현재 환경에서 지원되는 차량 데이터 API를 확인해 주세요."
                    show("home")
                }
            }
        }
    }

    private fun applyReadings() {
        val km = readings.odometerKm
        val fuel = readings.fuelPercent
        if (km == null || fuel == null) {
            busy = false
            status = "일부 데이터 수신"
            description = "주행거리와 연료값이 모두 확인되어야 페르소나를 생성해요."
            show("home")
            return
        }
        val previous = identity
        val seed = VehicleSnapshot(0.0, km, fuel, 50.0, 0.5, 0.5)
        val next = if (previous == null) seed else VehicleDataMapper.merge(
            previous.lastLearned, readings, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        )
        snapshot = next
        identity = if (previous == null) IdentityEngine.generate("pleos-sdk-observed", next)
            else IdentityEngine.evolve(previous, next)
        identity?.let(store::save)
        busy = false
        status = "VPE Identity 준비 완료"
        description = "차량 데이터로 페르소나를 생성하고 저장했어요."
        show("home")
    }

    override fun onDestroy() {
        waveAnimator?.cancel()
        requestId++
        mainHandler.removeCallbacksAndMessages(null)
        sdk.release()
        super.onDestroy()
    }
}
