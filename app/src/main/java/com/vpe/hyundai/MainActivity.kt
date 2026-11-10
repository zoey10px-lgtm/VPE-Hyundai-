package com.vpe.hyundai

import android.app.Activity
import android.app.AlertDialog
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
import android.text.InputType
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
    private val defaultAiServerUrl = "https://vpe-hyundai.onrender.com"
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
            "report" -> reportScreen()
            "diagnostics" -> diagnosticsScreen()
            else -> homeScreen()
        }

        val navWrap = row().apply {
            setPadding(d(10), d(9), d(10), d(13))
            setBackgroundColor(white)
        }
        listOf(Triple("⌂", "홈", "home"), Triple("⬡", "리포트", "report"),
            Triple("◉", "대화", "chat"), Triple("✧", "진단", "diagnostics")).forEach { (symbol, name, target) ->
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
            affectIntensity = hpe?.expression?.motionIntensity?.toFloat() ?: 0.45f
            valence = hpe?.affect?.valence?.toFloat() ?: 0f
        }
        hero.addView(wave, LinearLayout.LayoutParams(-1, d(166)))
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

        if (identity != null && hpe != null) {
            val reportPreview = card()
            val vehicleTitle = storedVehicleName()
            reportPreview.addView(label("차량 인격 분석 완료", 13f, teal, true))
            reportPreview.addView(gap(7))
            reportPreview.addView(label(vehicleTitle + " 성향 리포트", 22f, ink, true))
            reportPreview.addView(gap(8))
            reportPreview.addView(label("MBTI · 성격 키워드 · 7각형 레이더 차트", 14f, secondary))
            reportPreview.addView(gap(15))
            reportPreview.addView(button("내 차량 분석 리포트 열기") { render("report") })
            page.addView(reportPreview)
            page.addView(gap(23))
        }

        val distance = if (demo) observedSnapshot?.odometerKm else latest.odometerKm
        val energy = if (demo) observedSnapshot?.fuelPercent else latest.energyPercent()
        val hasVehicleData = distance != null || energy != null ||
            (!demo && latest.cabinTemperatureC != null)
        page.addView(row().apply {
            addView(label("차량 상태", 22f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(tag(
                when {
                    demo -> "시연 데이터"
                    hasVehicleData -> "SDK 수신값"
                    else -> "미수신"
                },
                Color.rgb(234, 238, 252), violet
            ))
        })
        page.addView(gap(12))

        val info = row()
        info.addView(metric("누적 주행거리",
            distance?.let { String.format(java.util.Locale.KOREA, "%,.0f km", it) } ?: "미수신",
            Color.rgb(239, 236, 255)),
            LinearLayout.LayoutParams(0, -2, 1f))
        info.addView(metric("에너지 잔량",
            energy?.let { it.toInt().toString() + "%" } ?: "미수신",
            Color.rgb(228, 249, 245)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = d(11) })
        page.addView(info)

        val cabin = if (demo) null else latest.cabinTemperatureC
        val target = if (demo) null else latest.targetTemperatureC
        if (cabin != null || target != null) {
            page.addView(gap(11))
            val climate = row()
            climate.addView(metric("현재 실내 온도",
                cabin?.let { String.format(java.util.Locale.KOREA, "%.1f°C", it) } ?: "미수신",
                Color.rgb(239, 245, 253)),
                LinearLayout.LayoutParams(0, -2, 1f))
            climate.addView(metric("목표 공조 온도",
                target?.let { String.format(java.util.Locale.KOREA, "%.1f°C", it) } ?: "미수신",
                Color.rgb(240, 247, 249)),
                LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = d(11) })
            page.addView(climate)
        }
        page.addView(gap(14))
        page.addView(label(
            if (demo) "현재 화면은 시연용 차량 정보예요. 실제 측정값과 구분해서 표시해요."
            else "Pleos SDK에서 실제 수신한 값만 표시해요. 미수신 항목은 확인된 값이 아니에요.",
            13f, secondary
        ))
    }

    /** Vehicle model isn't exposed by our confirmed Pleos getters. Allow a truthful local name. */
    private fun storedVehicleName(): String {
        val id = identity ?: return "내 차량"
        val saved = getSharedPreferences("vpe_vehicle_names", MODE_PRIVATE)
            .getString(id.vehicleKey, "").orEmpty().trim()
        return saved.ifBlank { if (demo) "시연 차량" else "내 차량" }
    }

    private fun showVehicleNameDialog() {
        val id = identity ?: return
        val existing = getSharedPreferences("vpe_vehicle_names", MODE_PRIVATE)
            .getString(id.vehicleKey, "").orEmpty()
        val entry = EditText(this).apply {
            hint = "예: 내 아이오닉 5, 우리 차"
            setSingleLine(true)
            setText(existing)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("차량 이름 지정")
            .setMessage("현재 연동 SDK에서 차종명이 확인되지 않아, 원하는 차량 이름을 직접 정할 수 있어요.")
            .setView(entry)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장") { _, _ ->
                val updated = entry.text.toString().trim().take(30)
                getSharedPreferences("vpe_vehicle_names", MODE_PRIVATE).edit()
                    .putString(id.vehicleKey, updated).apply()
                render("report")
            }
            .show()
    }

    /** Full-screen report, distinct from Home / live vehicle data cards. */
    private fun reportScreen() {
        val page = scrollContent()
        page.addView(label("MY VEHICLE  /  PERSONALITY REPORT", 12f, teal, true))
        page.addView(gap(9))
        val state = hpe
        val currentIdentity = identity
        if (state == null || currentIdentity == null) {
            page.addView(label("차량 성향 분석 리포트", 26f, ink, true))
            page.addView(gap(12))
            page.addView(label("홈에서 차량 데이터를 연동하면 성격 분석 리포트가 이곳에 생성돼요.",
                15f, secondary))
            page.addView(gap(20))
            page.addView(button("차량 연결하러 가기") { render("home") })
            return
        }

        page.addView(label(storedVehicleName() + " 성향 분석 리포트", 26f, ink, true))
        page.addView(gap(8))
        page.addView(row().apply {
            addView(tag(if (demo) "시연 데이터" else "실제 SDK 관측 기반",
                Color.rgb(231, 243, 255), violet))
            addView(label("  ·  HPE 1.0", 12f, secondary))
        })
        page.addView(gap(11))
        page.addView(button("차량 이름 지정 / 수정", false) { showVehicleNameDialog() })
        page.addView(gap(18))

        val personality = card()
        personality.addView(label("01   차량의 성격 유형", 14f, teal, true))
        personality.addView(gap(13))
        personality.addView(label("MBTI", 13f, secondary))
        personality.addView(label(state.core.mbti, 52f, ink, true))
        personality.addView(gap(10))
        val axes = state.core.axes
        val narrative = listOf(
            if (axes[0] >= .5) "먼저 밝게 다가오고" else "차분하게 곁을 지키고",
            if (axes[1] >= .5) "새로운 가능성에 호기심이 많으며"
                else "익숙한 순간을 소중히 여기며",
            if (axes[2] >= .5) "이성적으로 판단하고"
                else "따뜻한 반응을 전하고",
            if (axes[3] >= .5) "계획적인 리듬을 좋아하는 성격이야."
                else "상황에 맞춰 유연하게 움직이는 성격이야."
        ).joinToString(" ")
        personality.addView(label(narrative, 17f, ink, true))
        personality.addView(gap(12))
        personality.addView(label(
            "보이스 스타일  ·  " + currentIdentity.voiceBlueprint.presentation,
            13f, secondary
        ))
        page.addView(personality)
        page.addView(gap(15))

        val traits = state.core.traits
        val traitNames = listOf("활동성", "호기심", "친밀감", "자신감", "신중함", "장난기", "표현력")
        val keywords = listOf("활기찬", "탐험가", "다정한", "당당한", "사려 깊은", "유쾌한", "표현이 풍부한")
        val strongest = traits.indices.sortedByDescending { traits[it] }.take(3)

        val highlights = card()
        highlights.addView(label("02   성격 키워드", 14f, teal, true))
        highlights.addView(gap(10))
        highlights.addView(label("이 차량을 가장 잘 표현하는 세 가지 특징", 17f, ink, true))
        highlights.addView(gap(14))
        strongest.forEachIndexed { place, index ->
            val feature = row().apply {
                background = shape(Color.rgb(243, 242, 254), 16)
                setPadding(d(15), d(13), d(14), d(13))
            }
            feature.addView(label("0" + (place + 1), 13f, violet, true),
                LinearLayout.LayoutParams(d(37), -2))
            feature.addView(label("#" + keywords[index], 17f, ink, true),
                LinearLayout.LayoutParams(0, -2, 1f))
            feature.addView(label(traitNames[index], 13f, secondary))
            highlights.addView(feature)
            if (place != strongest.lastIndex) highlights.addView(gap(8))
        }
        page.addView(highlights)
        page.addView(gap(15))

        val radarCard = card()
        radarCard.addView(label("03   성격 밸런스", 14f, teal, true))
        radarCard.addView(gap(7))
        radarCard.addView(label("7가지 성향 레이더 차트", 21f, ink, true))
        radarCard.addView(gap(8))
        radarCard.addView(label("각 방향의 바깥쪽에 가까울수록 해당 기질이 강해져요.",
            13f, secondary))
        radarCard.addView(gap(12))
        radarCard.addView(VpeRadarChartView(this, traits),
            LinearLayout.LayoutParams(-1, d(340)))
        radarCard.addView(gap(12))
        radarCard.addView(label(
            "차량별 성격을 나타내는 상대적 지표이며, 실측되지 않은 기질은 초기 설정값을 포함해요.",
            12f, secondary
        ))
        page.addView(radarCard)
        page.addView(gap(15))

        val feeling = card()
        feeling.addView(label("04   지금 이 차의 기분", 14f, teal, true))
        feeling.addView(gap(8))
        feeling.addView(label(state.expression.headline, 26f, ink, true))
        feeling.addView(gap(12))
        val quote = column(16).apply { background = shape(Color.rgb(245, 243, 255), 18) }
        quote.addView(label("“" + state.expression.utterance + "”", 16f, ink, true))
        feeling.addView(quote)
        feeling.addView(gap(13))
        feeling.addView(label("함께 관측한 주행 " +
            String.format(java.util.Locale.KOREA, "%,.0f km", state.memory.sharedKm) +
            "  ·  주요 경험 " + state.memory.meaningfulEvents + "회", 13f, secondary))
        page.addView(feeling)
        page.addView(gap(16))
        page.addView(button("이 차와 대화하기") { render("chat") })
        page.addView(gap(11))
        page.addView(button("홈으로 돌아가기", false) { render("home") })
    }

    private fun metric(title: String, value: String, color: Int): LinearLayout =
        column(17).apply {
            background = shape(color, 22)
            minimumHeight = d(112)
            addView(label(title, 14f, secondary, true))
            addView(gap(13))
            addView(label(value, if (value.length > 9) 22f else 27f, ink, true))
        }

    private fun chatScreen() {
        val title = column().apply { setPadding(d(23), d(8), d(23), d(12)) }
        title.addView(label("VPE와 대화", 25f, ink, true))
        title.addView(gap(4))
        title.addView(label("내 차의 성격과 오늘의 상태를 알아보세요", 13f, secondary))
        title.addView(gap(9))
        val aiPreferences = getSharedPreferences("vpe_ai", MODE_PRIVATE)
        val configured = !aiPreferences.getString("render_url", defaultAiServerUrl).isNullOrBlank() &&
            !aiPreferences.getString("access_token", "").isNullOrBlank()
        title.addView(label(
            if (configured) "GPT 서버 주소 등록됨 · 첫 대화로 연결 확인"
            else "GPT 미설정 · 설정 후 실제 GPT 대화가 시작돼요",
            12f, if (configured) teal else secondary
        ))
        title.addView(gap(8))
        title.addView(button("GPT 연결 설정", false) { showAiSettings() })
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

    private fun showAiSettings() {
        val prefs = getSharedPreferences("vpe_ai", MODE_PRIVATE)
        val form = column(18)
        val serverInput = EditText(this).apply {
            hint = "https://[Render 서비스 주소].onrender.com"
            setSingleLine(true)
            setText(prefs.getString("render_url", defaultAiServerUrl))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val tokenInput = EditText(this).apply {
            hint = "VPE_ACCESS_TOKEN (OpenAI API 키 아님)"
            setSingleLine(true)
            setText(prefs.getString("access_token", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        form.addView(label("Render 서버 HTTPS 주소", 14f, ink, true))
        form.addView(serverInput)
        form.addView(gap(8))
        form.addView(label("VPE 전용 접속 토큰", 14f, ink, true))
        form.addView(tokenInput)
        form.addView(label("OpenAI API 키는 앱에 입력하지 않고 Render 서버에만 보관해요.", 12f, secondary))
        AlertDialog.Builder(this)
            .setTitle("GPT 연결 설정")
            .setView(form)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장") { _, _ ->
                val url = serverInput.text.toString().trim().trimEnd('/')
                val token = tokenInput.text.toString().trim()
                if (url.isNotEmpty() && !url.startsWith("https://")) {
                    Toast.makeText(this, "Render 주소는 https:// 로 시작해야 해.", Toast.LENGTH_LONG).show()
                } else {
                    prefs.edit().putString("render_url", url)
                        .putString("access_token", token).apply()
                    if (currentTab == "chat") render("chat")
                }
            }
            .show()
    }

    /** Send the observed/derived HPE state, never the VIN or raw vehicle key. */
    private fun aiContext(state: HpeState): JSONObject {
        fun value(v: Any?): Any = v ?: JSONObject.NULL
        val traitNames = listOf("activity", "curiosity", "warmth", "confidence",
            "caution", "playfulness", "expressiveness")
        val traits = JSONObject()
        traitNames.forEachIndexed { index, name -> traits.put(name, state.core.traits[index]) }
        val emotion = JSONObject()
            .put("hunger", state.affect.hunger)
            .put("heat", state.affect.heat)
            .put("cold", state.affect.cold)
            .put("fatigue", state.affect.fatigue)
            .put("excitement", state.affect.excitement)
            .put("tension", state.affect.tension)
            .put("relief", state.affect.relief)
            .put("comfort", state.affect.comfort)
            .put("valence", state.affect.valence)
            .put("arousal", state.affect.arousal)
        val snapshot = if (demo) observedSnapshot else null
        val vehicle = JSONObject()
            .put("odometerKm", value(if (demo) snapshot?.odometerKm else latest.odometerKm))
            .put("energyPercent", value(if (demo) snapshot?.fuelPercent else latest.energyPercent()))
            .put("actualCabinC", value(if (demo) null else latest.cabinTemperatureC))
            .put("targetCabinC", value(if (demo) null else latest.targetTemperatureC))
            .put("hvacOn", value(if (demo) null else latest.hvacOn))
            .put("chargingSwitchOn", value(if (demo) null else latest.charging))
            .put("parked", value(if (demo) null else latest.parked))
            .put("safetyWarning", value(if (demo) null else latest.safetyWarning))
        val memory = JSONObject()
            .put("sharedKm", state.memory.sharedKm)
            .put("meaningfulEvents", state.memory.meaningfulEvents)
            .put("highlights", JSONArray(state.memory.highlights.takeLast(3)))
        return JSONObject()
            .put("demo", demo)
            .put("mbti", state.core.mbti)
            .put("evidenceCoverage", state.core.confidence)
            .put("traits", traits)
            .put("affect", emotion)
            .put("currentFeeling", state.expression.headline)
            .put("voiceSpeed", state.expression.voiceSpeed)
            .put("vehicle", vehicle)
            .put("memory", memory)
    }

    private fun sendMessage(message: String) {
        val state = hpe
        val prefs = getSharedPreferences("vpe_ai", MODE_PRIVATE)
        val url = prefs.getString("render_url", defaultAiServerUrl).orEmpty()
        val token = prefs.getString("access_token", "").orEmpty()
        val canUseGpt = identity != null && state != null &&
            url.isNotBlank() && token.isNotBlank()
        val history = JSONArray()
        messages.takeLast(8).forEach { (sender, text) ->
            if (sender == "나" || (sender == "VPE" && !text.startsWith("[GPT"))) {
                history.put(JSONObject()
                    .put("role", if (sender == "나") "user" else "assistant")
                    .put("content", text.take(1000)))
            }
        }
        messages.add("나" to message)
        val replyIndex = messages.size
        val response = when {
            canUseGpt -> "GPT가 답변을 만들고 있어요…"
            state != null && (url.isBlank() || token.isBlank()) ->
                "[GPT 서버 설정 전 · 기존 로컬 대화]\n" + reply(message)
            else -> reply(message)
        }
        messages.add("VPE" to response)
        saveMessages()
        if (currentTab == "chat") render("chat")
        if (!canUseGpt || state == null) return
        val context = aiContext(state)
        Thread {
            val result = runCatching {
                VpeAiClient.ask(url, token, message, context, history)
            }.getOrElse { error ->
                "[GPT 연결 오류: " + (error.message ?: "알 수 없는 오류") +
                    "]\n기존 로컬 응답: " + reply(message)
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed && replyIndex < messages.size) {
                    messages[replyIndex] = "VPE" to result
                    saveMessages()
                    if (currentTab == "chat") render("chat")
                }
            }
        }.start()
    }

    private fun reply(message: String): String {
        val identityNow = identity ?: return "반가워! 먼저 홈에서 Pleos 차량을 연결하거나 데모를 체험해 줘."
        val state = hpe ?: return "내 성격을 분석 중이야. 홈에서 차량 정보를 새로고침해 줘."
        val energy = if (demo) observedSnapshot?.fuelPercent else latest.energyPercent()
        val distance = if (demo) observedSnapshot?.odometerKm else latest.odometerKm
        val mood = state.affect
        return when {
            listOf("MBTI", "mbti", "성격", "누구", "어떤 차").any { it in message } ->
                "난 " + state.core.mbti + " 유형이야. 내 호기심은 " +
                    (state.core.traits[1] * 100).toInt() + "%, 친밀감은 " +
                    (state.core.traits[2] * 100).toInt() + "%야. " + state.expression.utterance
            listOf("연료", "기름", "배터리", "충전").any { it in message } ->
                energy?.let { "현재 에너지 잔량은 " + it.toInt() + "%야. " + state.expression.utterance }
                    ?: "아직 에너지 데이터를 받아오지 못했어."
            listOf("실내", "온도", "더워", "추워", "공조").any { it in message } ->
                latest.cabinTemperatureC?.let { "실내 현재온도는 " + it + "°C야. " + state.expression.utterance }
                    ?: "현재 실내온도는 아직 SDK에서 확인되지 않았어."
            listOf("거리", "주행", "얼마나").any { it in message } ->
                distance?.let { "확인된 누적 주행거리는 " + "%,.0f".format(it) +
                    "km야. 함께 관측한 거리는 " + "%,.0f".format(state.memory.sharedKm) + "km야." }
                    ?: "주행거리 데이터를 아직 확인하지 못했어."
            listOf("기분", "컨디션", "배고", "걱정", "괜찮").any { it in message } ->
                "지금 내 상태는 " + state.expression.headline + "이야. 에너지 부족 " +
                    (mood.hunger * 100).toInt() + "%, 긴장 " +
                    (mood.tension * 100).toInt() + "%. " + state.expression.utterance
            listOf("기억", "추억", "경험").any { it in message } ->
                if (state.memory.highlights.isEmpty()) "이제부터 우리만의 주행 경험을 기억할게."
                else "기억나는 건 " + state.memory.highlights.last() + "야."
            listOf("안녕", "반가워").any { it in message } ->
                "안녕! 반가워. " + state.expression.utterance
            else -> state.expression.utterance
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
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("실내 현재온도", latest.cabinTemperatureC?.let { "$it°C" } ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("공조 목표온도", latest.targetTemperatureC?.let { "$it°C" } ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("공조 전원", latest.hvacOn?.toString() ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("EV 충전 스위치", latest.charging?.toString() ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("주차 여부", latest.parked?.toString() ?: "미수신"))
        page.addView(sourceCard)
        hpe?.let { state ->
            page.addView(gap(14))
            val evidence = card()
            evidence.addView(label("HPE 계산 근거 · SDK / 초기 시드 구분", 15f, ink, true))
            evidence.addView(gap(9))
            evidence.addView(label(state.sources.joinToString("\n"), 12f, secondary))
            page.addView(evidence)
        }
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
                                    render("report")
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
        val generated = buildIdentityFromSdkReadings()
        if (generated) {
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
        render(if (generated) "report" else "home")
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
        if (currentTab == "home" || currentTab == "diagnostics" || currentTab == "report") render(currentTab)
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
        render("report")
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
