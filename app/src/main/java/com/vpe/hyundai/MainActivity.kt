package com.vpe.hyundai

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * VPE consumer-facing app.
 * Light, expressive UI inspired by contemporary AI/automotive experiences.
 * Real Pleos data, explicitly labeled demo, and diagnostic data never mix.
 */
class MainActivity : Activity() {
    private lateinit var environment: VehicleEnvironment
    private lateinit var sdk: PleosVehicleClient
    private lateinit var store: IdentityStore
    private lateinit var hpeStore: HpeStore
    private var hpe: HpeState? = null
    private lateinit var root: LinearLayout
    private lateinit var contentHost: LinearLayout
    private var currentTab = "home"
    private var identity: VpeIdentity? = null
    private var latest: VehicleReadings = VehicleReadings()
    private var observedSnapshot: VehicleSnapshot? = null
    private var demo = false
    private var connecting = false
    private var connectionNote = "아직 차량과 연결되지 않았어요."
    private val messages = mutableListOf<Pair<String, String>>()
    private var liveMessages: LinearLayout? = null
    private var liveChatScroll: ScrollView? = null
    private val diagnosticLogs = mutableListOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var requestSerial = 0
    private val dp get() = resources.displayMetrics.density
    private fun d(v: Int) = (v * dp + 0.5f).toInt()

    private val ink = Color.rgb(30, 37, 61)
    private val secondary = Color.rgb(108, 119, 145)
    private val violet = Color.rgb(105, 90, 226)
    private val teal = Color.rgb(21, 162, 144)
    private val cream = Color.rgb(249, 250, 255)
    private val white = Color.WHITE
    private val line = Color.rgb(234, 237, 247)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = cream
        window.navigationBarColor = white
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        environment = VehicleEnvironmentInspector.inspect(this)
        sdk = PleosVehicleClient(this)
        store = IdentityStore(this)
        hpeStore = HpeStore(this)
        identity = store.load()?.takeIf { !it.vehicleKey.startsWith("demo") }
        hpe = identity?.let { hpeStore.load(it.vehicleKey) }
        connectionNote = when {
            !environment.automotive -> "이 스마트폰에는 Pleos 차량 서비스가 없어요. 데모 모드에서 VPE를 체험할 수 있어요."
            environment.missingPermissions.isNotEmpty() -> "Pleos 차량 권한이 확인되지 않았어요. 진단 화면에서 상세 원인을 확인해 주세요."
            identity != null -> "저장된 VPE를 불러왔어요. 실시간 차량 정보는 새로 연결해 주세요."
            else -> "Pleos 차량과 연결할 준비가 됐어요."
        }
        loadMessages()
        if (messages.isEmpty()) messages.add("VPE" to "안녕! 내 차의 데이터를 연결하면 우리만의 성격을 만들어 볼 수 있어. 먼저 홈에서 연결해 줘.")
        render("home")
        // Debug-only integration test hook: launch the real SDK request without UI
        // automation, which is unavailable in some Automotive headless environments.
        // No vehicle values are injected or simulated by this hook.
        val debugging = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debugging && intent?.getBooleanExtra("vpe_test_sdk_connect", false) == true) {
            mainHandler.post { connectVehicle() }
        }
    }

    private fun type(size: Float, bold: Boolean = false): Typeface =
        Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)

    private fun label(value: String, size: Float, color: Int = ink, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color); typeface = type(size, bold)
            includeFontPadding = true
        }

    private fun shape(color: Int, radius: Int = 24, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = d(radius).toFloat()
            if (stroke != null) setStroke(d(1), stroke)
        }

    private fun gradient(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(239, 234, 255), Color.rgb(229, 245, 255), Color.rgb(219, 251, 241))
        ).apply { cornerRadius = d(32).toFloat() }

    private fun gap(h: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, d(h))
    }

    private fun column(padding: Int = 0): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (padding != 0) setPadding(d(padding), d(padding), d(padding), d(padding))
    }

    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun card(): LinearLayout = column(20).apply {
        background = shape(white, 26, line)
        elevation = d(2).toFloat()
    }

    private fun button(value: String, primary: Boolean = true, onClick: () -> Unit): TextView =
        label(value, 15f, if (primary) white else violet, true).apply {
            gravity = Gravity.CENTER
            minHeight = d(54)
            setPadding(d(16), d(12), d(16), d(12))
            background = shape(if (primary) violet else white, 19, if (primary) null else line)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun tag(value: String, backgroundColor: Int = Color.rgb(236, 232, 255),
                    foregroundColor: Int = violet): TextView =
        label(value, 12f, foregroundColor, true).apply {
            background = shape(backgroundColor, 50)
            setPadding(d(12), d(8), d(12), d(8))
        }

    private fun render(tab: String = currentTab) {
        currentTab = tab
        liveMessages = null
        liveChatScroll = null
        root = column().apply { setBackgroundColor(cream) }
        val header = row().apply { setPadding(d(24), d(15), d(24), d(12)) }
        val logo = label("vpe.", 29f, violet, true)
        header.addView(logo, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(tag(if (demo) "●  DEMO" else if (connecting) "●  CONNECTING"
            else if (identity != null) "●  SAVED" else "○  OFFLINE",
            if (connecting) Color.rgb(230, 245, 255) else Color.rgb(239, 234, 255)))
        root.addView(header)

        contentHost = column()
        root.addView(contentHost, LinearLayout.LayoutParams(-1, 0, 1f))
        when (tab) {
            "chat" -> chatScreen()
            "diagnostics" -> diagnosticsScreen()
            else -> homeScreen()
        }

        val navWrap = row().apply {
            setPadding(d(10), d(9), d(10), d(13))
            setBackgroundColor(white)
        }
        listOf(Triple("⌂", "홈", "home"), Triple("◉", "대화", "chat"),
            Triple("✧", "진단", "diagnostics")).forEach { (symbol, name, target) ->
            val active = target == tab
            val item = column().apply {
                gravity = Gravity.CENTER
                minimumHeight = d(56)
                setPadding(d(8), d(5), d(8), d(5))
                background = if (active) shape(Color.rgb(242, 239, 255), 20) else null
                setOnClickListener { if (target != currentTab) render(target) }
                isClickable = true
            }
            item.addView(label(symbol, 21f, if (active) violet else secondary, active))
            item.addView(label(name, 11f, if (active) violet else secondary, active))
            navWrap.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        }
        root.addView(navWrap)
        setContentView(root)
    }

    private fun scrollContent(): LinearLayout {
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            isVerticalScrollBarEnabled = false
        }
        val inner = column().apply { setPadding(d(22), d(12), d(22), d(26)) }
        scroll.addView(inner)
        contentHost.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        return inner
    }

    private fun homeScreen() {
        val page = scrollContent()
        page.addView(label("HELLO, YOUR VEHICLE", 11f, teal, true))
        page.addView(gap(8))
        page.addView(label("차가, 나를 닮아가다.", 27f, ink, true))
        page.addView(gap(5))
        page.addView(label("함께 달린 시간이 하나의 성격이 되는 경험", 14f, secondary))
        page.addView(gap(23))

        val hero = column(20).apply { background = gradient() }
        val header = row()
        header.addView(tag("VPE  /  PERSONALITY ENGINE", Color.argb(175, 255, 255, 255), ink))
        hero.addView(header)
        val wave = VpeWaveView(this).apply {
            mode = when {
                connecting -> VpeWaveView.Mode.CONNECTING
                identity != null -> VpeWaveView.Mode.READY
                else -> VpeWaveView.Mode.IDLE
            }
        }
        hero.addView(wave, LinearLayout.LayoutParams(-1, d(218)))
        val mainTitle = when {
            connecting -> "당신의 차를 알아가는 중"
            demo -> "데모 페르소나가 준비됐어요"
            !environment.automotive -> "스마트폰에서 VPE 체험하기"
            environment.missingPermissions.isNotEmpty() -> "차량 권한 점검이 필요해요"
            identity != null -> "나만의 VPE가 탄생했어요"
            else -> "차량과 연결할 준비 완료"
        }
        hero.addView(label(mainTitle, 23f, ink, true).apply { gravity = Gravity.CENTER })
        hero.addView(gap(9))
        hero.addView(label(connectionNote, 13f, secondary).apply { gravity = Gravity.CENTER })
        hero.addView(gap(24))
        val stepsDone = if (identity != null) 4 else if (connecting) 1 else 0
        val steps = row().apply { gravity = Gravity.CENTER }
        repeat(4) { index ->
            steps.addView(View(this@MainActivity).apply {
                background = shape(if (index < stepsDone) violet else Color.argb(110, 255, 255, 255), 6)
            }, LinearLayout.LayoutParams(0, d(6), 1f).apply {
                if (index > 0) leftMargin = d(7)
            })
        }
        hero.addView(steps)
        hero.addView(gap(17))
        hero.addView(button(
            when {
                connecting -> "차량 데이터 확인 중…"
                !environment.automotive -> "Pleos 차량 연동 안내"
                environment.missingPermissions.isNotEmpty() -> "차량 접근 권한 진단"
                identity != null && !demo -> "차량 데이터 새로고침"
                else -> "Pleos 차량 연결하기"
            }
        ) {
            if (environment.automotive && !connecting) connectVehicle()
            else render("diagnostics")
        })
        page.addView(hero)
        page.addView(gap(12))
        page.addView(button(if (!environment.automotive) "✦  스마트폰에서 VPE 데모 시작" else "✦  시연용 데모 체험", primary = false) { loadDemo() })
        page.addView(gap(24))

        if (identity != null) {
            val state = hpe
            page.addView(label("나의 VPE 인격", 20f, ink, true))
            page.addView(gap(12))
            val profile = card()
            profile.addView(row().apply {
                addView(label(if (demo) "DEMO / HUMAN PERSONALITY ENGINE" else "HPE / VEHICLE IDENTITY", 13f, teal, true),
                    LinearLayout.LayoutParams(0, -2, 1f))
                addView(tag(if (state == null) "분석 준비 중" else "HPE 1.0"))
            })
            profile.addView(gap(12))
            if (state != null) {
                fun percent(v: Double) = String.format(java.util.Locale.KOREA, "%.0f%%", v * 100)
                val core = state.core
                val affect = state.affect
                profile.addView(label("MBTI  " + core.mbti + "   ·   " +
                    identity!!.voiceBlueprint.presentation + " 보이스", 24f, ink, true))
                profile.addView(gap(7))
                profile.addView(label("고유 인격 · 4개 성격 축에서 계산된 초기 유형", 12f, secondary))
                profile.addView(gap(11))
                val axisNames = listOf("외향성 E", "직관형 N", "분석형 T", "계획형 J")
                profile.addView(label(axisNames.mapIndexed { i, name ->
                    name + " " + percent(core.axes[i])
                }.joinToString("   ·   "), 12f, secondary))
                profile.addView(gap(14))
                profile.addView(label("장기 기질", 15f, ink, true))
                profile.addView(gap(6))
                val labels = listOf("활동성", "호기심", "친밀감", "자신감", "신중함", "장난기", "표현력")
                profile.addView(label(labels.mapIndexed { i, name ->
                    name + " " + percent(core.traits[i])
                }.chunked(4).joinToString("\n") { it.joinToString("   ·   ") }, 13f, secondary))
                profile.addView(gap(14))
                profile.addView(label("지금의 감정 · " + state.expression.headline, 16f, ink, true))
                profile.addView(gap(6))
                val moodData = listOf(
                    "배고픔" to affect.hunger,
                    "더움" to affect.heat,
                    "추움" to affect.cold,
                    "긴장" to affect.tension,
                    "안도" to affect.relief,
                    "편안함" to affect.comfort
                )
                profile.addView(label(moodData.chunked(3).joinToString("\n") { row ->
                    row.joinToString("   ·   ") { (name, value) ->
                        name + " " + if ((name == "더움" || name == "추움" || name == "편안함") &&
                            latest.cabinTemperatureC == null && !demo) "미측정" else percent(value)
                    }
                }, 13f, secondary))
                profile.addView(gap(11))
                profile.addView(label("“" + state.expression.utterance + "”", 15f, ink, true))
                profile.addView(gap(9))
                profile.addView(label("함께 주행한 관측 거리 " +
                    String.format(java.util.Locale.KOREA, "%,.0f km",state.memory.sharedKm) +
                    "   ·   중요 경험 " + state.memory.meaningfulEvents + "회", 12f, secondary))
                profile.addView(gap(6))
                profile.addView(label("성격 신뢰도 " + percent(core.confidence) +
                    " · 데이터 부족 영역은 차량별 고유 시드 사용", 12f, secondary))
            } else {
                profile.addView(label("차량 인격 분석을 준비 중이에요.", 14f, secondary))
            }
            profile.addView(gap(15))
            profile.addView(button("VPE와 대화하기") { render("chat") })
            page.addView(profile)
            page.addView(gap(18))
        }
        page.addView(label("차량 데이터", 20f, ink, true))
        page.addView(gap(12))
        val info = row()
        val distance = if (demo) observedSnapshot?.odometerKm else latest.odometerKm
        val energy = if (demo) observedSnapshot?.fuelPercent else latest.energyPercent()
        info.addView(metric("누적 주행거리", distance?.let { "%,.0f km".format(it) } ?: "조회 전",
            Color.rgb(238, 236, 255)), LinearLayout.LayoutParams(0, -2, 1f))
        info.addView(metric("에너지 잔량", energy?.let { "${it.toInt()}%" } ?: "조회 전",
            Color.rgb(226, 250, 244)), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = d(10) })
        page.addView(info)
        page.addView(gap(14))
        page.addView(label("SDK에서 확인되지 않은 운행 이력·관리 점수는 중립 기준값으로 처리되며 실제 측정값으로 표시하지 않습니다.", 12f, secondary))
    }

    private fun metric(title: String, value: String, color: Int): LinearLayout =
        column(17).apply {
            background = shape(color, 22)
            addView(label(title, 12f, secondary))
            addView(gap(10))
            addView(label(value, 19f, ink, true))
        }

    private fun chatScreen() {
        val title = column().apply { setPadding(d(23), d(8), d(23), d(12)) }
        title.addView(label("VPE와 대화", 25f, ink, true))
        title.addView(gap(4))
        title.addView(label("내 차의 성격과 오늘의 상태를 알아보세요", 13f, secondary))
        contentHost.addView(title)

        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val conversationView = column().apply { setPadding(d(17), d(12), d(17), d(18)) }
        scroll.addView(conversationView)
        contentHost.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        liveMessages = conversationView
        liveChatScroll = scroll
        messages.forEach { (sender, message) -> appendBubble(conversationView, sender, message) }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }

        val actions = row().apply {
            setPadding(d(17), d(7), d(17), d(7))
        }
        listOf("오늘 기분은?", "내 성격은?").forEach { prompt ->
            val chip = tag(prompt, Color.rgb(238, 236, 255), violet)
            chip.setOnClickListener { sendMessage(prompt) }
            actions.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = d(8) })
        }
        contentHost.addView(actions)

        val composer = row().apply {
            setPadding(d(14), d(9), d(14), d(13))
            setBackgroundColor(white)
        }
        val input = EditText(this).apply {
            hint = "VPE에게 이야기하기"
            textSize = 14f; setTextColor(ink); setHintTextColor(secondary)
            typeface = type(14f)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEND
            background = shape(cream, 22, line)
            setPadding(d(16), d(13), d(16), d(13))
        }
        fun submit() {
            val typed = input.text.toString().trim()
            if (typed.isNotEmpty()) {
                input.setText("")
                sendMessage(typed)
            }
        }
        input.setOnEditorActionListener { _, _, _ -> submit(); true }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        composer.addView(button("↑") { submit() },
            LinearLayout.LayoutParams(d(54), d(52)).apply { leftMargin = d(9) })
        contentHost.addView(composer)
    }

    private fun appendBubble(container: LinearLayout, sender: String, message: String) {
        val mine = sender == "나"
        val alignment = row().apply { gravity = if (mine) Gravity.RIGHT else Gravity.LEFT }
        val bubble = column(14).apply {
            background = shape(if (mine) violet else white, 21, if (mine) null else line)
        }
        bubble.addView(label(message, 15f, if (mine) white else ink))
        alignment.addView(bubble, LinearLayout.LayoutParams(-2, -2).apply {
            width = if (resources.displayMetrics.widthPixels > d(540)) d(400)
                else (resources.displayMetrics.widthPixels * 0.76).toInt()
        })
        container.addView(alignment)
        container.addView(gap(11))
    }

    private fun sendMessage(message: String) {
        val response = reply(message)
        messages.add("나" to message)
        messages.add("VPE" to response)
        saveMessages()
        val visible = liveMessages
        if (currentTab == "chat" && visible != null) {
            appendBubble(visible, "나", message)
            appendBubble(visible, "VPE", response)
            liveChatScroll?.post { liveChatScroll?.fullScroll(View.FOCUS_DOWN) }
        } else {
            render("chat")
        }
    }

    private fun reply(message: String): String {
        val id = identity ?: return "반가워! 먼저 홈에서 Pleos 차량을 연결하거나 데모를 체험해 줘."
        val current = observedSnapshot ?: id.lastLearned
        val persona = PersonaEngine.calculate(current)
        val energy = if (demo) current.fuelPercent else latest.energyPercent()
        val distance = if (demo) current.odometerKm else latest.odometerKm
        return when {
            listOf("연료", "기름", "배터리", "충전").any { it in message } ->
                energy?.let { "현재 에너지 잔량은 ${it.toInt()}%야. ${persona.line}" }
                    ?: "아직 에너지 데이터를 받아오지 못했어."
            listOf("거리", "주행", "얼마나").any { it in message } ->
                distance?.let { "확인된 누적 주행거리는 ${"%,.0f".format(it)}km야." }
                    ?: "주행거리 데이터를 아직 확인하지 못했어."
            listOf("성격", "누구", "어떤 차").any { it in message } -> {
                val traits = PersonaEngine.expressedTraits(id.memory, current)
                "지금의 나는 호기심 ${"%.1f".format(traits[1] * 100)}%, 친밀감 ${"%.1f".format(traits[2] * 100)}%야. ${PersonaEngine.moodLabel(persona)}. ${persona.line}"
            }
            listOf("기분", "컨디션", "배고", "걱정", "괜찮").any { it in message } ->
                "지금 기분은 ${PersonaEngine.moodLabel(persona)}. 에너지 부족 ${(persona.hunger * 100).toInt()}%, 걱정 ${(persona.concern * 100).toInt()}%야. ${persona.line}"
            listOf("안녕", "반가워").any { it in message } ->
                "안녕! 반가워. ${persona.line}"
            else -> persona.line
        }
    }

    private fun diagnosticsScreen() {
        val page = scrollContent()
        page.addView(label("연결 진단", 26f, ink, true))
        page.addView(gap(7))
        page.addView(label("앱과 Pleos Vehicle Service의 통신 상태", 14f, secondary))
        page.addView(gap(20))
        val summary = card()
        summary.addView(label("SDK  ·  Vehicle 2.3.2", 13f, teal, true))
        summary.addView(gap(10))
        summary.addView(label(connectionNote, 16f, ink, true))
        summary.addView(gap(12))
        summary.addView(label(environment.summary, 13f, secondary))
        summary.addView(gap(14))
        if (environment.automotive) {
            summary.addView(button("Pleos 차량 연결 재시도") { connectVehicle() })
        } else {
            summary.addView(button("시연용 VPE 바로 체험하기") { loadDemo() })
        }
        page.addView(summary)
        page.addView(gap(18))
        page.addView(label("실행 환경 및 권한", 20f, ink, true))
        page.addView(gap(11))
        val permissionsCard = card()
        permissionsCard.addView(dataRow("실행 환경",
            if (environment.automotive) "Android Automotive" else "일반 Android 스마트폰"))
        permissionsCard.addView(gap(13))
        environment.permissions.forEach { check ->
            permissionsCard.addView(dataRow(check.shortName, check.description))
            permissionsCard.addView(gap(13))
        }
        permissionsCard.addView(label(
            "Pleos의 CAR_* 권한은 일반 Android 팝업으로 승인받는 권한이 아닙니다. 차량용 Pleos Connect 시스템 이미지와 Vehicle Service가 필요합니다.",
            12f, secondary
        ))
        page.addView(permissionsCard)
        page.addView(gap(10))
        page.addView(button("Pleos Connect 공식 에뮬레이터 설치 안내", primary = false) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator"
            )))
        })
        page.addView(gap(18))
        page.addView(label("수신 상태", 20f, ink, true))
        page.addView(gap(11))
        val sourceCard = card()
        sourceCard.addView(dataRow("Odometer", latest.odometerKm?.let { "수신 · %,.1f km".format(it) } ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("Fuel", latest.fuelPercent?.let { "수신 · ${it.toInt()}%" } ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("EV Battery", latest.evBatteryLevel?.let { "수신 · ${it.toInt()}%" } ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("Vehicle identity", if (latest.vehicleKey != null) "암호화 식별값 확인" else "미수신"))
        page.addView(sourceCard)
        page.addView(gap(18))
        page.addView(label("개발자 로그", 20f, ink, true))
        page.addView(gap(10))
        val logCard = card()
        logCard.addView(label(
            if (diagnosticLogs.isEmpty()) "아직 조회한 기록이 없습니다."
            else diagnosticLogs.takeLast(18).joinToString("\n\n"),
            12f, secondary
        ))
        page.addView(logCard)
        page.addView(gap(11))
        page.addView(button("진단 로그 복사", false) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("VPE diagnostics", diagnosticLogs.joinToString("\n")))
            Toast.makeText(this, "진단 로그를 복사했어요.", Toast.LENGTH_SHORT).show()
        })
        page.addView(gap(15))
        page.addView(label("실제 차량 데이터는 Pleos Connect 호환 환경과 차량 접근 권한이 필요합니다. 일반 스마트폰에서는 SDK 조회가 거부될 수 있습니다.", 12f, secondary))
    }

    private fun dataRow(name: String, value: String): LinearLayout =
        row().apply {
            addView(label(name, 14f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(label(value, 12f, if (value.startsWith("수신") || value.startsWith("암호화")) teal else secondary))
        }

    private fun connectVehicle() {
        if (connecting) return
        environment = VehicleEnvironmentInspector.inspect(this)
        if (!environment.automotive) {
            ++requestSerial
            connecting = false
            diagnosticLogs.clear()
            diagnosticLogs.add("ENVIRONMENT: " + environment.code)
            environment.permissions.forEach { check ->
                diagnosticLogs.add(check.shortName + ": " + check.description)
            }
            connectionNote = environment.summary
            render("diagnostics")
            return
        }
        connecting = true
        if (demo) { identity = null; observedSnapshot = null }
        demo = false
        latest = VehicleReadings()
        diagnosticLogs.clear()
        connectionNote = "실제 차량 데이터 연결을 확인하고 있어요."
        val serial = ++requestSerial
        render("home")
        mainHandler.postDelayed({
            if (connecting && serial == requestSerial) finishConnection(serial, timeout = true)
        }, 12000L)
        // SDK IPC is performed off main thread. Callbacks are always marshalled to UI thread.
        Thread {
            sdk.readVehicleData { log, reading ->
                runOnUiThread {
                    if (serial != requestSerial) return@runOnUiThread
                    diagnosticLogs.add(log)
                    if (diagnosticLogs.size > 80) diagnosticLogs.removeAt(0)
                    if (reading != null) {
                        latest = latest.copy(
                            odometerKm = reading.odometerKm ?: latest.odometerKm,
                            fuelPercent = reading.fuelPercent ?: latest.fuelPercent,
                            modelYear = reading.modelYear ?: latest.modelYear,
                            evBatteryLevel = reading.evBatteryLevel ?: latest.evBatteryLevel,
                            vehicleKey = reading.vehicleKey ?: latest.vehicleKey,
                            cabinTemperatureC = reading.cabinTemperatureC ?: latest.cabinTemperatureC,
                            targetTemperatureC = reading.targetTemperatureC ?: latest.targetTemperatureC,
                            hvacOn = reading.hvacOn ?: latest.hvacOn,
                            charging = reading.charging ?: latest.charging,
                            parked = reading.parked ?: latest.parked,
                            safetyWarning = reading.safetyWarning ?: latest.safetyWarning
                        )
                        if (connecting && latest.odometerKm != null &&
                            latest.energyPercent() != null && latest.vehicleKey != null) {
                            finishConnection(serial, timeout = false)
                        } else if (!connecting && !demo) {
                            if (latest.odometerKm != null && latest.vehicleKey != null &&
                                identity?.vehicleKey != latest.vehicleKey) {
                                if (buildIdentityFromSdkReadings()) {
                                    connectionNote = "차량 식별과 주행거리 수신 완료 · VPE Identity 생성"
                                    render("home")
                                }
                            } else if (identity != null) {
                                applyLiveSdkReadings()
                            }
                        }
                    }
                    if (connecting && log.startsWith("SERVICE_ERROR")) {
                        finishConnection(serial, timeout = false)
                    }
                }
            }
        }.start()
    }

    private fun finishConnection(serial: Int, timeout: Boolean) {
        if (serial != requestSerial || !connecting) return
        connecting = false
        if (buildIdentityFromSdkReadings()) {
            connectionNote = if (latest.energyPercent() == null)
                "차량 주행거리와 식별자 확인 완료 · 에너지 데이터는 미수신"
            else "Pleos Vehicle Service 데이터 수신 및 VPE Identity 생성 완료"
        } else {
            connectionNote = when {
                latest.odometerKm != null && latest.vehicleKey == null ->
                    "주행거리는 수신했어요. CAR_INFO 차량 식별자가 미수신이라 고유 페르소나 생성을 보류했어요."
                diagnosticLogs.any { it.contains("PERMISSION_DENIED") } ->
                    "Pleos 차량 데이터 권한 거부. 차량 시스템의 앱 권한을 확인하세요."
                timeout -> "차량 데이터 응답 시간 초과 · 진단에서 도메인별 결과를 확인하세요."
                else -> "차량 SDK 응답 미수신 · 진단 화면에서 원인을 확인하세요."
            }
        }
        render("home")
    }

    /** Create a persistent vehicle persona only from observed Odometer + real vehicle ID. */
    private fun buildIdentityFromSdkReadings(): Boolean {
        val km = latest.odometerKm ?: return false
        val key = latest.vehicleKey ?: return false
        if (!km.isFinite() || km < 0.0) return false
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val existing = identity?.takeIf { it.vehicleKey == key && !demo }
        // Neutral attributes are explicitly unobserved, not spoofed SDK measurements.
        val seed = VehicleSnapshot(10.0, km, latest.energyPercent() ?: 50.0,
            50.0, 0.5, 0.5)
        val next = if (existing == null) seed else
            VehicleDataMapper.merge(observedSnapshot ?: existing.lastLearned, latest, year)
        observedSnapshot = next
        identity = if (existing == null) IdentityEngine.generate(key, next)
            else IdentityEngine.evolve(existing, next)
        identity?.let(store::save)
        updateHumanPersonality()
        return true
    }

    /**
     * Receives subsequent real Pleos listener notifications even after the first
     * connection has finished. Transient mood tracks current readings immediately;
     * long-term identity learns only if the engine's event gate is satisfied.
     */
    private fun applyLiveSdkReadings() {
        val previous = identity ?: return
        if (latest.odometerKm == null || demo ||
            latest.vehicleKey != previous.vehicleKey) return
        val base = observedSnapshot ?: previous.lastLearned
        val next = VehicleDataMapper.merge(
            base, latest, Calendar.getInstance().get(Calendar.YEAR)
        )
        observedSnapshot = next
        val evolved = IdentityEngine.evolve(previous, next)
        if (evolved != previous) {
            identity = evolved
            store.save(evolved)
        }
        updateHumanPersonality()
        if (currentTab == "home" || currentTab == "diagnostics") render(currentTab)
    }

    private fun updateHumanPersonality() {
        val id = identity ?: return
        val snapshot = observedSnapshot ?: id.lastLearned
        val key = id.vehicleKey
        val readings = if (demo) VehicleReadings(
            odometerKm = snapshot.odometerKm, evBatteryLevel = snapshot.fuelPercent,
            vehicleKey = key
        ) else latest
        val signals = HpeSignals(
            vehicleKey = key,
            odometerKm = readings.odometerKm,
            energyPercent = readings.energyPercent(),
            actualCabinC = readings.cabinTemperatureC,
            targetCabinC = readings.targetTemperatureC,
            hvacOn = readings.hvacOn,
            charging = readings.charging,
            parked = readings.parked,
            safetyWarning = readings.safetyWarning
        )
        val previous = hpe?.takeIf { it.key == key }
            ?: if (demo) null else hpeStore.load(key)
        hpe = HumanPersonalityEngine.observe(signals, previous)
        if (!demo) hpe?.let(hpeStore::save)
    }

    private fun loadDemo() {
        ++requestSerial
        connecting = false
        demo = true
        latest = VehicleReadings()
        observedSnapshot = VehicleSnapshot(7.0, 87320.0, 71.0, 78.0, 0.64, 0.34)
        identity = IdentityEngine.generate("demo-vehicle", observedSnapshot!!)
        hpe = null
        updateHumanPersonality()
        connectionNote = "가상의 차량 데이터로 UI와 성격 알고리즘을 체험하고 있어요."
        diagnosticLogs.clear()
        render("home")
    }

    private fun loadMessages() {
        val raw = getSharedPreferences("vpe_chat", MODE_PRIVATE).getString("history", null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                messages.add(item.getString("who") to item.getString("text"))
            }
        }
    }

    private fun saveMessages() {
        val array = JSONArray()
        messages.takeLast(60).forEach { (who, message) ->
            array.put(JSONObject().put("who", who).put("text", message))
        }
        getSharedPreferences("vpe_chat", MODE_PRIVATE).edit().putString("history", array.toString()).apply()
    }

    override fun onDestroy() {
        ++requestSerial
        mainHandler.removeCallbacksAndMessages(null)
        sdk.release()
        super.onDestroy()
    }
}
