package com.vpe.hyundai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

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
    private lateinit var patternTracker: VehiclePatternTracker
    private var modeUpdatedAtMs: Long = 0L
    private var cruiseUpdatedAtMs: Long = 0L
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
    private var remoteProfiles = JSONArray()
    private var activeChatScope: String? = null
    private var historyLoadSerial = 0
    private var pendingChats = 0
    private val proactiveEngine = LiveProactiveEngine()
    private var lastProactiveText: String? = null
    private var lastLiveChange: String? = null
    private var proactiveStatus: String = "차량 연결 후 이벤트 감지 시작"
    private val voicePermissionRequestCode = 7301
    private var realtimeVoice: VpeRealtimeVoice? = null
    private var voiceActive = false
    private var voiceStarting = false
    private var voiceDisplay = "가운데 VPE 오브를 눌러 음성 대화 시작"
    private var voiceOrb: VpeWaveView? = null
    private var voiceHint: TextView? = null
    private var voicePersonaSignature: String? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsStatus = "음성 엔진 초기화 중"
    private var liveSdkCallback: ((String, VehicleReadings?) -> Unit)? = null
    private var livePolling = false
    private val pollTask = object : Runnable {
        override fun run() {
            if (!livePolling || isFinishing || isDestroyed) return
            if (!demo && !connecting && environment.automotive) {
                // Use time-separated fresh SDK observations to build real vehicle
                // habits, and re-check deferred speech after its cooldown expires.
                val nowMs = System.currentTimeMillis()
                latest.vehicleKey?.let { key ->
                    patternTracker.sample(
                        key, latest.drivingMode, latest.cruiseControlState,
                        modeUpdatedAtMs > 0L && nowMs - modeUpdatedAtMs < 16_000L,
                        cruiseUpdatedAtMs > 0L && nowMs - cruiseUpdatedAtMs < 16_000L)
                    if (identity?.vehicleKey == key) updateHumanPersonality()
                }
                proactiveOnVehicleChange()
                if (currentTab == "home" || currentTab == "report")
                    render(currentTab)
                liveSdkCallback?.let { callback ->
                    Thread { sdk.readVehicleData(callback) }.start()
                }
            }
            mainHandler.postDelayed(this, 8_000L)
        }
    }
    private var lastProfileSyncAt = 0L
    private var cachedReport = false
    private var authModeRegister = false
    private var authOriginTab = "home"
    private var authUsernameDraft = ""
    private var authBusy = false
    private val accountPrefs get() = getSharedPreferences("vpe_account", MODE_PRIVATE)
    private fun accountName() = accountPrefs.getString("username", "").orEmpty()
    private fun sessionToken() = VpeSecureSession.read(this)
    private fun hasSession() = accountName().isNotBlank() && sessionToken().isNotBlank()
    private fun serverUrl() = getSharedPreferences("vpe_ai", MODE_PRIVATE)
        .getString("render_url", defaultAiServerUrl).orEmpty()
    private var liveMoodStatus: TextView? = null
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
        patternTracker = VehiclePatternTracker(this)
        identity = store.load()?.takeIf { !it.vehicleKey.startsWith("demo") }
        hpe = identity?.let { hpeStore.load(it.vehicleKey) }
        connectionNote = when {
            !environment.automotive -> "이 스마트폰에는 Pleos 차량 서비스가 없어요. 데모 모드에서 VPE를 체험할 수 있어요."
            environment.missingPermissions.isNotEmpty() -> "Pleos 차량 권한이 확인되지 않았어요. 진단 화면에서 상세 원인을 확인해 주세요."
            identity != null -> "저장된 VPE를 불러왔어요. 실시간 차량 정보는 새로 연결해 주세요."
            else -> "Pleos 차량과 연결할 준비가 됐어요."
        }
        activateChatScope()
        if (messages.isEmpty()) messages.add("VPE" to "안녕! 차량을 연결하면 우리만의 성격이 탄생해.")
        render("home")
        initializeTts()
        if (hasSession()) refreshRemoteProfiles()
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
        liveMoodStatus = null
        voiceOrb = null
        voiceHint = null
        root = column().apply { setBackgroundColor(cream) }
        val header = row().apply { setPadding(d(24), d(15), d(24), d(12)) }
        val logo = label("vpe.", 29f, violet, true)
        header.addView(logo, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(tag(if (demo) "●  DEMO" else if (connecting) "●  CONNECTING"
            else if (identity != null && latest.odometerKm != null) "●  SDK LIVE"
            else if (identity != null) "●  SAVED" else "○  OFFLINE",
            if (connecting) Color.rgb(230, 245, 255) else Color.rgb(239, 234, 255)))
        root.addView(header)

        contentHost = column()
        root.addView(contentHost, LinearLayout.LayoutParams(-1, 0, 1f))
        when (tab) {
            "chat" -> chatScreen()
            "auth" -> authScreen()
            "report" -> reportScreen()
            "diagnostics" -> diagnosticsScreen()
            else -> homeScreen()
        }

        if (tab != "auth") {
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
        }
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
                voiceStarting || connecting -> VpeWaveView.Mode.CONNECTING
                voiceActive && voiceDisplay.contains("말하는 중") -> VpeWaveView.Mode.SPEAKING
                voiceActive -> VpeWaveView.Mode.LISTENING
                identity != null -> VpeWaveView.Mode.READY
                else -> VpeWaveView.Mode.IDLE
            }
            affectIntensity = hpe?.expression?.motionIntensity?.toFloat() ?: 0.45f
            valence = hpe?.affect?.valence?.toFloat() ?: 0f
            isClickable = true
            isFocusable = true
            contentDescription = if (voiceActive) "VPE 음성 대화 종료" else "VPE 음성 대화 시작"
            setOnClickListener { toggleRealtimeVoice() }
        }
        voiceOrb = wave
        hero.addView(wave, LinearLayout.LayoutParams(-1, d(166)))
        val voiceCaption = label(voiceDisplay, 14f, if (voiceActive) teal else violet, true)
            .apply { gravity = Gravity.CENTER }
        voiceHint = voiceCaption
        hero.addView(gap(8))
        hero.addView(voiceCaption)
        hero.addView(label(
            if (voiceActive) "다시 누르면 마이크가 꺼져요"
            else "터치하면 한국어 실시간 음성 대화가 시작돼요",
            12f, secondary).apply { gravity = Gravity.CENTER })
        val mainTitle = when {
            voiceActive -> "VPE와 실시간 음성 대화 중"
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
        lastProactiveText?.let { recent ->
            val speaking = card()
            speaking.addView(label("VPE가 먼저 전한 이야기", 13f, teal, true))
            speaking.addView(gap(8))
            speaking.addView(label(recent, 15f, ink))
            page.addView(gap(12))
            page.addView(speaking)
        }
        page.addView(gap(12))
        page.addView(button(if (!environment.automotive) "✦  스마트폰에서 VPE 데모 시작" else "✦  시연용 데모 체험", primary = false) { loadDemo() })
        page.addView(gap(24))

        page.addView(gap(16))
        val accountCard = card()
        accountCard.addView(label("VPE 계정", 13f, teal, true))
        accountCard.addView(gap(6))
        accountCard.addView(label(
            if (hasSession()) "로그인: " + accountName()
            else "로그인해서 차량별 분석과 대화를 저장해 보세요.",
            15f, ink, true))
        accountCard.addView(gap(12))
        if (hasSession()) {
            accountCard.addView(button("내 차량 목록 새로고침", false) { refreshRemoteProfiles() })
            accountCard.addView(gap(8))
            accountCard.addView(button("로그아웃", false) { logoutAccount() })
        } else {
            accountCard.addView(button("로그인 / 회원가입") { openAuth() })
        }
        page.addView(accountCard)
        page.addView(gap(18))

        if (hasSession() && remoteProfiles.length() > 0) {
            val saved = card()
            saved.addView(label("저장된 내 차량", 19f, ink, true))
            saved.addView(gap(10))
            for (i in 0 until remoteProfiles.length()) {
                val item = remoteProfiles.optJSONObject(i) ?: continue
                val vehicleName = item.optString("vehicleName", "내 차량")
                val context = item.optJSONObject("context")
                val mbti = context?.optString("mbti", "") ?: ""
                saved.addView(button(vehicleName + "  ·  " + mbti + "  →", false) {
                    restoreCloudProfile(item)
                })
                saved.addView(gap(9))
            }
            page.addView(saved)
            page.addView(gap(18))
        }


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
        if (identity != null && hpe != null) addLivePersonaEvidence(page)
    }

    private fun percent(v: Double): String =
        String.format(Locale.KOREA, "%.0f%%", v.coerceIn(0.0, 1.0) * 100.0)

    /** Every row says explicitly whether the value changes HPE or only GPT context. */
    private fun addLivePersonaEvidence(page: LinearLayout) {
        val state = hpe ?: return
        page.addView(gap(18))
        val mood = card()
        mood.addView(label("실시간 페르소나 변화", 17f, teal, true))
        mood.addView(gap(9))
        mood.addView(label("현재 감정: " + state.expression.headline +
            "  ·  장기 MBTI " + state.core.mbti, 16f, ink, true))
        mood.addView(gap(10))
        mood.addView(dataRow("에너지 부족 / 긴장",
            percent(state.affect.hunger) + " / " + percent(state.affect.tension)))
        mood.addView(gap(8))
        mood.addView(dataRow("추위 / 더위",
            percent(state.affect.cold) + " / " + percent(state.affect.heat)))
        mood.addView(gap(8))
        mood.addView(dataRow("안도감", percent(state.affect.relief)))
        mood.addView(gap(8))
        mood.addView(label("배터리·온도는 감정만 즉시 변화시켜. MBTI는 장기 운행 패턴이 쌓일 때 학습해.",
            13f, secondary))
        lastLiveChange?.let {
            mood.addView(gap(9))
            mood.addView(label("최근 데이터 변화: " + it, 13f, violet, true))
        }
        mood.addView(gap(9))
        mood.addView(label("능동 발화: " + proactiveStatus, 13f, teal))
        mood.addView(gap(5))
        mood.addView(label("음성 엔진: " + ttsStatus, 12f, secondary))
        page.addView(mood)

        page.addView(gap(16))
        val sources = card()
        sources.addView(label("Pleos 데이터 · 페르소나 영향", 17f, teal, true))
        sources.addView(gap(11))
        fun observed(labelText: String, value: String?, contribution: String) {
            sources.addView(dataRow(labelText, value ?: "미수신"))
            sources.addView(gap(3))
            sources.addView(label("영향: " + contribution, 12f, secondary))
            sources.addView(gap(11))
        }
        observed("누적 주행거리",
            latest.odometerKm?.let { String.format(Locale.KOREA, "%,.0fkm", it) },
            "장기 활동성·자신감 (100만 km까지 로그 정규화)")
        observed("VIN 기반 연식", latest.modelYear?.let { "${it}년 추정" },
            "장기 자신감·신중함 (VIN 모델연도 추정인 경우만)")
        observed("배터리 SOC", latest.evBatteryLevel?.let { "${it.toInt()}%" },
            "단기 배고픔·긴장·배터리 경고")
        observed("실내 / 목표 온도",
            latest.cabinTemperatureC?.let { cabin ->
                latest.targetTemperatureC?.let { target ->
                    String.format(Locale.KOREA, "%.1f / %.1f°C", cabin, target)
                }
            }, "단기 추위·더위·자동 발화")
        observed("충전 스위치", latest.charging?.let { if (it) "ON" else "OFF" },
            "단기 안도감·충전 알림 (실제 충전 전류 미확인)")
        val patterns = identity?.vehicleKey?.let { patternTracker.summary(it) }
        observed("주행 모드", latest.drivingMode,
            "GPT 맥락 + 관측 모드 다양성 누적 → N/S 보정")
        observed("크루즈 상태", latest.cruiseControlState,
            "GPT 맥락 + 크루즈 사용 비율 누적 → J/P 보정")
        observed("모드 다양성 누적", patterns?.modeVarietyRatio?.let { percent(it) },
            "N/S 장기 성격 · 모드 관측 4회 이상부터 반영")
        observed("크루즈 사용률 누적", patterns?.cruiseUseRatio?.let { percent(it) },
            "J/P 장기 성격 · 크루즈 관측 4회 이상부터 반영")
        observed("VPE 대화 상호작용", patterns?.interactions?.toString()?.plus("회"),
            "E/I 장기 성격 · 대화 3회 이상부터 반영")
        patterns?.let {
            sources.addView(label(it.evidenceSummary, 12f, violet, true))
            sources.addView(gap(7))
        }
        observed("크루즈 목표속도", latest.cruiseTargetSpeedRaw,
            "GPT 대화 맥락 · 실제 주행속도 아님")
        observed("공조 팬속도", latest.fanSpeedLevel?.toString(),
            "GPT 대화 맥락 · 사용 패턴 미집계")
        observed("주행가능거리", latest.rangeRemainingRaw,
            "GPT 대화 맥락 · SDK 단위 검증 후 점수 반영")
        observed("배터리 용량", latest.batteryCapacityRaw,
            "GPT 차량 특성 · SDK 단위 검증 후 점수 반영")
        observed("트립 거리 / 시간",
            if (latest.tripDistanceRaw == null && latest.tripTimeRaw == null) null else
                (latest.tripDistanceRaw ?: "미수신") + " / " + (latest.tripTimeRaw ?: "미수신"),
            "운행 이력 후보 · 원값 단위 검증 후 활용")
        sources.addView(label(
            "MBTI는 지속적인 운전 모드 다양성(N), 크루즈 사용(J), VPE 대화(E) " +
                "관측이 쌓여 서서히 바뀔 수 있어. SOC·온도는 단기 감정만 바꿔. " +
                "운행 패턴 가중치는 공개된 엔지니어링 추정치이며 심리검사 결과가 아니야.",
            13f, violet))
        page.addView(sources)
    }

    private fun observedChange(old: VehicleReadings, next: VehicleReadings): String? = when {
        old.energyPercent() != next.energyPercent() ->
            "에너지 " + (next.energyPercent()?.let { percent(it / 100.0) } ?: "미수신") +
                " → 배고픔·긴장 자동 재계산"
        old.cabinTemperatureC != next.cabinTemperatureC ||
            old.targetTemperatureC != next.targetTemperatureC ->
            "공조 온도 변경 → 추위·더위 자동 재계산"
        old.charging != next.charging -> "충전 스위치 변경 → 안도감 자동 재계산"
        old.odometerKm != next.odometerKm -> "주행거리 변경 → 장기 경험 조건 평가"
        old.drivingMode != next.drivingMode -> "주행 모드 수신 → GPT 맥락 변경"
        old.cruiseControlState != next.cruiseControlState -> "크루즈 상태 수신 → GPT 맥락 변경"
        old.fanSpeedLevel != next.fanSpeedLevel -> "공조 팬 변경 → GPT 맥락 변경"
        else -> null
    }

    /** Vehicle model isn't exposed by our confirmed Pleos getters. Allow a truthful local name. */
    private fun vehicleNameKey(key: String) = accountName().ifBlank { "guest" } + "|" + key

    private fun storedVehicleName(): String {
        val id = identity ?: return "내 차량"
        val saved = getSharedPreferences("vpe_vehicle_names", MODE_PRIVATE)
            .getString(vehicleNameKey(id.vehicleKey), "").orEmpty().trim()
        return saved.ifBlank { if (demo) "시연 차량" else "내 차량" }
    }

    private fun showVehicleNameDialog() {
        val id = identity ?: return
        val existing = getSharedPreferences("vpe_vehicle_names", MODE_PRIVATE)
            .getString(vehicleNameKey(id.vehicleKey), "").orEmpty()
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
                    .putString(vehicleNameKey(id.vehicleKey), updated).apply()
                syncCurrentVehicle(force = true)
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
            addView(tag(if (cachedReport) "저장된 분석" else if (demo) "시연 데이터" else "실제 SDK 관측 기반",
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
        personality.addView(label("장기 성격 · SOC·온도 변경은 단기 감정, " +
            "누적 운전·대화 패턴은 MBTI 연속 축에 반영", 13f, secondary))
        val axisNames = listOf("외향 E/I", "새로움 N/S", "분석 T/F", "계획 J/P")
        state.core.axes.forEachIndexed { idx, axis ->
            personality.addView(label(axisNames[idx] + "  " +
                String.format(Locale.KOREA, "%.1f%%", axis * 100.0), 13f, secondary))
        }
        personality.addView(label("관측 장기 입력 커버리지 " + percent(state.core.confidence),
            13f, violet, true))
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
        radarCard.addView(gap(12))
        traitNames.forEachIndexed { index, name ->
            radarCard.addView(dataRow(name + " 점수", percent(traits[index])))
            radarCard.addView(gap(6))
        }
        radarCard.addView(label(
            "실측 장기 영향: 주행거리→활동성·자신감, 연식→자신감·신중함, " +
                "운전 모드 다양성→N/S, 크루즈 사용→J/P, VPE 대화→E/I. " +
                "관측 부족인 축과 T/F 축은 초기 시드 사용.", 13f, violet))
        page.addView(radarCard)
        page.addView(gap(15))

        val feeling = card()
        feeling.addView(label(if (cachedReport) "04   마지막 저장 시점의 기분"
            else "04   지금 이 차의 기분", 14f, teal, true))
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

    private fun openAuth(register: Boolean = false) {
        if (currentTab != "auth") authOriginTab = currentTab
        authModeRegister = register
        authBusy = false
        render("auth")
    }

    /** Full-page account experience. The form stays visible on validation/server errors. */
    private fun authScreen() {
        val page = scrollContent()
        val back = label("‹  돌아가기", 15f, secondary, true).apply {
            setPadding(d(3), d(2), d(3), d(12))
            isClickable = true
            setOnClickListener { if (!authBusy) render(authOriginTab) }
        }
        page.addView(back)
        page.addView(gap(9))

        val banner = column(22).apply { background = gradient() }
        banner.addView(tag("VPE  ·  YOUR OWN VEHICLE AI",
            Color.argb(198, 255, 255, 255), violet))
        banner.addView(gap(18))
        banner.addView(label("차량마다 하나의 인격,\\n나만의 VPE.", 29f, ink, true))
        banner.addView(gap(9))
        banner.addView(label(
            "차량 성향 리포트와 대화의 추억을\\n한 계정에서 안전하게 이어가세요.",
            15f, secondary
        ))
        banner.addView(gap(12))
        banner.addView(row().apply {
            addView(tag("차량별 성격 저장", white, violet))
            addView(gap(8))
            addView(tag("대화 기록 분리", white, teal))
        })
        page.addView(banner)
        page.addView(gap(22))

        val formCard = card()
        formCard.addView(label(if (authModeRegister) "VPE 회원가입" else "VPE 로그인",
            25f, ink, true))
        formCard.addView(gap(7))
        formCard.addView(label(
            if (authModeRegister) "새 계정을 만들어 차량과의 이야기를 시작해요."
            else "저장한 내 차량과 다시 만나보세요.",
            14f, secondary
        ))
        formCard.addView(gap(22))
        var authUserInputText = authUsernameDraft
        val tabs = row()
        tabs.addView(button("로그인", primary = !authModeRegister) {
            if (!authBusy && authModeRegister) {
                authUsernameDraft = authUserInputText
                authModeRegister = false
                render("auth")
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(button("회원가입", primary = authModeRegister) {
            if (!authBusy && !authModeRegister) {
                authUsernameDraft = authUserInputText
                authModeRegister = true
                render("auth")
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = d(9) })
        formCard.addView(tabs)
        formCard.addView(gap(22))

        fun field(hintText: String, password: Boolean = false): EditText =
            EditText(this).apply {
                hint = hintText
                textSize = 16f
                setTextColor(ink)
                setHintTextColor(secondary)
                minHeight = d(58)
                background = shape(cream, 16, line)
                setPadding(d(17), d(13), d(17), d(13))
                setSingleLine(true)
                inputType = if (password)
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }

        formCard.addView(label("아이디", 14f, ink, true))
        formCard.addView(gap(8))
        val usernameInput = field("아이디를 입력하세요").apply {
            setText(authUsernameDraft.ifBlank { accountName() })
            setSelection(text.length)
        }
        formCard.addView(usernameInput)
        usernameInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                authUserInputText = s?.toString().orEmpty()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        formCard.addView(gap(6))
        formCard.addView(label("한글·영문·숫자·밑줄(_) / 2~24자",
            12f, secondary))
        formCard.addView(gap(18))

        formCard.addView(label("비밀번호", 14f, ink, true))
        formCard.addView(gap(8))
        val passwordInput = field("비밀번호 8자 이상", password = true)
        formCard.addView(passwordInput)
        formCard.addView(gap(8))
        var passwordVisible = false
        val visibility = label("비밀번호 표시", 13f, violet, true).apply {
            isClickable = true
            setPadding(d(2), d(4), d(2), d(6))
        }
        visibility.setOnClickListener {
            passwordVisible = !passwordVisible
            val pos = passwordInput.selectionStart.coerceAtLeast(0)
            passwordInput.transformationMethod = if (passwordVisible)
                HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            passwordInput.setSelection(pos.coerceAtMost(passwordInput.text.length))
            visibility.text = if (passwordVisible) "비밀번호 숨기기" else "비밀번호 표시"
        }
        formCard.addView(visibility)

        val confirmInput = if (authModeRegister) {
            formCard.addView(gap(12))
            formCard.addView(label("비밀번호 확인", 14f, ink, true))
            formCard.addView(gap(8))
            field("비밀번호를 다시 입력하세요", password = true).also { formCard.addView(it) }
        } else null

        formCard.addView(gap(16))
        val feedback = label("", 13f, Color.rgb(181, 58, 72), true).apply {
            this.visibility = View.GONE
            setPadding(d(5), d(8), d(5), d(12))
        }
        formCard.addView(feedback)
        val submit = button(
            if (authModeRegister) "계정 만들고 시작하기" else "로그인하기"
        ) {}
        // Assign the actual handler after all inputs exist.
        submit.setOnClickListener {
            if (authBusy) return@setOnClickListener
            val username = usernameInput.text.toString().trim().lowercase(Locale.ROOT)
            val password = passwordInput.text.toString()
            authUsernameDraft = username
            fun error(message: String) {
                feedback.text = message
                feedback.visibility = View.VISIBLE
            }
            if (!Regex("^[a-z0-9_가-힣]{2,24}$").matches(username)) {
                error("아이디는 한글·영문·숫자·_를 사용해 2~24자로 입력해 줘.")
                return@setOnClickListener
            }
            if (password.length !in 8..128) {
                error("비밀번호는 8~128자로 입력해 줘.")
                return@setOnClickListener
            }
            if (authModeRegister && confirmInput?.text.toString() != password) {
                error("비밀번호 확인이 일치하지 않아. 다시 확인해 줘.")
                return@setOnClickListener
            }

            feedback.visibility = View.VISIBLE
            feedback.setTextColor(secondary)
            feedback.text = "서버에 연결하고 있어요…"
            authBusy = true
            submit.isEnabled = false
            submit.alpha = .64f
            submit.text = "처리 중…"

            val registering = authModeRegister
            val returnTo = authOriginTab
            val address = serverUrl()
            Thread {
                val result = runCatching {
                    VpeAiClient.login(address, username, password, registering)
                }
                runOnUiThread {
                    authBusy = false
                    if (currentTab != "auth") return@runOnUiThread
                    submit.isEnabled = true
                    submit.alpha = 1f
                    submit.text = if (registering) "계정 만들고 시작하기" else "로그인하기"
                    result.onSuccess { data ->
                        val applied = runCatching { completeAccountAuth(data) }
                        applied.onSuccess {
                            authUsernameDraft = ""
                            passwordInput.text.clear()
                            confirmInput?.text?.clear()
                            Toast.makeText(this,
                                if (registering) "회원가입 완료! 반가워요." else "로그인 완료!",
                                Toast.LENGTH_SHORT).show()
                            render(returnTo)
                        }.onFailure { err ->
                            feedback.setTextColor(Color.rgb(181, 58, 72))
                            feedback.text = err.message ?: "로그인 세션 저장 오류"
                        }
                    }.onFailure { err ->
                        feedback.setTextColor(Color.rgb(181, 58, 72))
                        feedback.text = err.message ?: "가입·로그인 중 오류가 발생했어."
                    }
                }
            }.start()
        }
        formCard.addView(submit)
        formCard.addView(gap(15))
        formCard.addView(label(
            if (authModeRegister) "이미 계정이 있다면 위의 '로그인' 탭을 눌러 줘."
            else "처음 방문했다면 위의 '회원가입' 탭을 눌러 줘.",
            12f, secondary
        ))
        page.addView(formCard)
        page.addView(gap(16))
        page.addView(label("VPE는 계정마다 차량 성향과 대화 내역을 분리해 저장합니다.\\n" +
            "비밀번호 원문은 서버에 저장하지 않습니다.", 12f, secondary))
        page.addView(gap(15))
    }

    private fun completeAccountAuth(data: JSONObject) {
        val nextAccount = data.getString("username")
        val session = data.getString("token")
        require(nextAccount.isNotBlank() && session.isNotBlank()) {
            "서버에서 로그인 정보를 받지 못했어."
        }
        // Persist session first. If encryption fails, the previous account stays intact.
        VpeSecureSession.save(this, session)
        val previousAccount = accountName()
        if (previousAccount.isNotEmpty() && previousAccount != nextAccount) {
            identity = null
            hpe = null
            observedSnapshot = null
            latest = VehicleReadings()
            cachedReport = false
            store.clear()
            hpeStore.clear()
            remoteProfiles = JSONArray()
        }
        accountPrefs.edit().putString("username", nextAccount).apply()
        activeChatScope = null
        activateChatScope()
        if (identity != null && hpe != null) syncCurrentVehicle(force = true)
        refreshRemoteProfiles()
    }

    private fun logoutAccount() {
        stopLivePolling()
        val oldToken = sessionToken()
        val logoutUrl = serverUrl()
        if (oldToken.isNotBlank()) {
            Thread { runCatching { VpeAiClient.logout(logoutUrl, oldToken) } }.start()
        }
        VpeSecureSession.clear(this)
        accountPrefs.edit().clear().apply()
        identity = null
        hpe = null
        latest = VehicleReadings()
        observedSnapshot = null
        cachedReport = false
        demo = false
        remoteProfiles = JSONArray()
        store.clear()
        hpeStore.clear()
        messages.clear()
        activeChatScope = null
        ++historyLoadSerial
        render("home")
    }

    private fun refreshRemoteProfiles() {
        val username = accountName()
        val token = sessionToken()
        if (token.isBlank()) return
        Thread {
            val result = runCatching { VpeAiClient.myVehicles(serverUrl(), token) }
            runOnUiThread {
                if (accountName() != username) return@runOnUiThread
                result.onSuccess {
                    remoteProfiles = it
                    if (currentTab == "home") render("home")
                }.onFailure {
                    Toast.makeText(this, it.message ?: "차량 목록 조회 실패", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun restoreCloudProfile(profile: JSONObject) {
        val key = profile.optString("vehicleKey")
        val ctx = profile.optJSONObject("context") ?: return
        if (key.isBlank()) return
        val v = ctx.optJSONObject("vehicle") ?: JSONObject()
        val distance = v.optDouble("odometerKm", Double.NaN).takeIf { it.isFinite() }
        val energy = v.optDouble("energyPercent", Double.NaN).takeIf { it.isFinite() }
        val snap = ctx.optJSONObject("snapshot") ?: JSONObject()
        val snapshot = VehicleSnapshot(
            snap.optDouble("ageYears", 10.0),
            snap.optDouble("odometerKm", distance ?: 0.0),
            snap.optDouble("fuelPercent", energy ?: 50.0),
            snap.optDouble("maintenanceScore", 50.0),
            snap.optDouble("explorationRatio", 0.5),
            snap.optDouble("longTripRatio", 0.5)
        )
        val generatedIdentity = IdentityEngine.generate(key, snapshot)
        val savedVoice = ctx.optJSONObject("voice")
        identity = if (savedVoice == null) generatedIdentity else {
            val v = generatedIdentity.voiceBlueprint
            generatedIdentity.copy(voiceBlueprint = VoiceBlueprint(
                savedVoice.optDouble("genderExpression", v.genderExpression),
                savedVoice.optInt("perceivedAge", v.perceivedAge),
                savedVoice.optDouble("pitch", v.pitch),
                savedVoice.optDouble("resonance", v.resonance),
                savedVoice.optDouble("softness", v.softness),
                savedVoice.optDouble("speechRate", v.speechRate)
            ))
        }
        observedSnapshot = if (ctx.optBoolean("demo", false)) snapshot else null
        demo = ctx.optBoolean("demo", false)
        latest = VehicleReadings()
        cachedReport = true
        val seed = HumanPersonalityEngine.makeCore(HpeSignals(key, distance, energy))
        val ax = ctx.optJSONArray("axes")
        val axes = if (ax != null && ax.length() == 4)
            (0 until 4).map { ax.optDouble(it, 0.5).coerceIn(0.0, 1.0) }
            else seed.axes
        val tr = ctx.optJSONObject("traits") ?: JSONObject()
        val keys = listOf("activity", "curiosity", "warmth", "confidence", "caution", "playfulness", "expressiveness")
        val traits = keys.mapIndexed { i, name -> tr.optDouble(name, seed.traits[i]).coerceIn(0.0, 1.0) }
        val core = HpeCore(axes, traits, ctx.optDouble("evidenceCoverage", 0.0))
        val m = ctx.optJSONObject("memory") ?: JSONObject()
        val highlights = m.optJSONArray("highlights") ?: JSONArray()
        val memory = HpeMemory(
            firstKm = m.optDouble("firstKm", Double.NaN).takeIf { it.isFinite() },
            lastKm = m.optDouble("lastKm", Double.NaN).takeIf { it.isFinite() },
            sharedKm = m.optDouble("sharedKm", 0.0),
            meaningfulEvents = m.optInt("meaningfulEvents", 0),
            lastExploration = m.optDouble("lastExploration", Double.NaN).takeIf { it.isFinite() },
            lastRegularity = m.optDouble("lastRegularity", Double.NaN).takeIf { it.isFinite() },
            lastInteraction = m.optDouble("lastInteraction", Double.NaN).takeIf { it.isFinite() },
            highlights = (0 until highlights.length()).map { highlights.optString(it) }
        )
        val signals = HpeSignals(key, distance, energy,
            v.optDouble("actualCabinC", Double.NaN).takeIf { it.isFinite() },
            v.optDouble("targetCabinC", Double.NaN).takeIf { it.isFinite() })
        val appraisal = HumanPersonalityEngine.appraise(signals)
        val mood = HumanPersonalityEngine.affect(appraisal, signals)
        hpe = HpeState(key, core, memory, mood,
            HumanPersonalityEngine.express(core, mood, signals),
            listOf("계정에 저장된 마지막 차량 분석 결과"), signals)
        getSharedPreferences("vpe_vehicle_names", MODE_PRIVATE).edit()
            .putString(vehicleNameKey(key), profile.optString("vehicleName", "내 차량")).apply()
        activateChatScope()
        render("report")
    }

    private fun syncCurrentVehicle(force: Boolean = false) {
        val user = accountName()
        val token = sessionToken()
        val state = hpe ?: return
        if (token.isBlank()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastProfileSyncAt < 20000L) return
        lastProfileSyncAt = now
        val key = state.key
        val context = aiContext(state)
        val name = storedVehicleName()
        Thread {
            val result = runCatching {
                VpeAiClient.saveVehicle(serverUrl(), token, key, name, context)
            }
            runOnUiThread {
                if (accountName() != user) return@runOnUiThread
                result.onSuccess { refreshRemoteProfiles() }
                    .onFailure {
                        Toast.makeText(this, "차량 저장 실패: " + (it.message ?: ""), Toast.LENGTH_LONG).show()
                    }
            }
        }.start()
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
        liveMoodStatus = label(liveMoodSummary(), 13f, teal, true)
        title.addView(gap(5))
        title.addView(liveMoodStatus!!)
        title.addView(gap(9))
        title.addView(label(
            if (hasSession()) "GPT 연결 준비 · " + accountName() + " 계정에 차량별 대화 저장"
            else "GPT 대화는 로그인 후 이용해요. 로그인 전에는 로컬 체험 답변을 제공해요.",
            12f, if (hasSession()) teal else secondary
        ))
        title.addView(gap(8))
        if (!hasSession()) {
            title.addView(button("로그인 / 회원가입") { openAuth() })
            title.addView(gap(7))
        }
        title.addView(button("GPT 서버 주소 설정", false) { showAiSettings() })
        title.addView(gap(8))
        title.addView(button("🔊 VPE 음성 테스트", false) {
            speakVehicleText("안녕! 나는 VPE야. 이제 차량의 상태가 바뀌면 먼저 이야기할게.", showError = true)
        })
        title.addView(gap(8))
        title.addView(button("🧪 GPT 성격 차이 검증", false) { compareCurrentPersona() })
        title.addView(label(
            "같은 질문·같은 차량 데이터로, 실제 VPE 성격과 " +
                "테스트용 반대 성격의 GPT 답변을 비교해요. 로그인 필요 · API 2회 호출.",
            12f, secondary))
        title.addView(gap(5))
        title.addView(label("음성 출력: " + ttsStatus, 12f, secondary))
        title.addView(label("선제 대화: " + proactiveStatus, 12f, secondary))
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
        val input = EditText(this).apply {
            hint = "https://vpe-hyundai.onrender.com"
            setSingleLine(true)
            setText(serverUrl())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        AlertDialog.Builder(this)
            .setTitle("VPE GPT 서버 설정")
            .setMessage("개인 계정으로 로그인하면 차량별 성격과 대화 기록이 분리돼요. OpenAI API 키는 Render에만 보관해요.")
            .setView(input)
            .setNegativeButton("취소", null)
            .setPositiveButton("주소 저장") { _, _ ->
                val url = input.text.toString().trim().trimEnd('/')
                if (!url.startsWith("https://")) {
                    Toast.makeText(this, "HTTPS 서버 주소를 입력해 줘.", Toast.LENGTH_LONG).show()
                } else {
                    getSharedPreferences("vpe_ai", MODE_PRIVATE).edit()
                        .putString("render_url", url).apply()
                    if (currentTab == "chat") render("chat")
                }
            }
            .show()
    }

    private fun liveMoodSummary(): String {
        val state = hpe ?: return "차량 성격과 현재 감정 정보가 아직 없어."
        val energy = if (demo) observedSnapshot?.fuelPercent else latest.energyPercent()
        return "현재 감정: " + state.expression.headline +
            (energy?.let { " · 에너지 ${it.toInt()}%" } ?: "")
    }

    private fun toggleRealtimeVoice() {
        if (voiceActive) {
            stopRealtimeVoice()
            return
        }
        if (identity == null || hpe == null || demo) {
            Toast.makeText(this, "Pleos 차량을 연결하고 내 VPE를 만든 다음 시작해 줘.",
                Toast.LENGTH_LONG).show()
            return
        }
        if (!hasSession()) {
            Toast.makeText(this, "실시간 GPT 음성 대화는 VPE 로그인 후 사용할 수 있어.",
                Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), voicePermissionRequestCode)
            return
        }
        startRealtimeVoice()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == voicePermissionRequestCode) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) startRealtimeVoice()
            else Toast.makeText(this, "마이크 권한이 있어야 음성으로 대화할 수 있어.",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun startRealtimeVoice() {
        val id = identity ?: return
        val state = hpe ?: return
        if (voiceActive) return
        voiceActive = true
        voiceStarting = true
        voicePersonaSignature = null
        updateVoiceDisplay("실시간 음성 세션 연결 중…")
        tts?.stop()
        render("home")
        val context = aiContext(state)
        val token = sessionToken()
        val endpoint = serverUrl()
        val vehicleKey = id.vehicleKey
        Thread {
            val secret = runCatching { VpeAiClient.realtimeSecret(endpoint, token, context) }
            runOnUiThread {
                if (!voiceActive || identity?.vehicleKey != vehicleKey || isDestroyed) return@runOnUiThread
                secret.onSuccess { credential ->
                    val temp = credential.optString("token")
                    val model = credential.optString("model")
                    if (temp.isBlank() || model.isBlank()) {
                        stopRealtimeVoice()
                        Toast.makeText(this, "임시 음성 토큰이 비어 있어.", Toast.LENGTH_LONG).show()
                        return@onSuccess
                    }
                    val session = VpeRealtimeVoice(object : VpeRealtimeVoice.Listener {
                        override fun onStatus(status: String) {
                            runOnUiThread {
                                if (!voiceActive) return@runOnUiThread
                                voiceStarting = false
                                updateVoiceDisplay(status)
                            }
                        }
                        override fun onUserText(text: String) {
                            runOnUiThread {
                                if (!voiceActive) return@runOnUiThread
                                messages.add("나" to text)
                                if (!demo) identity?.vehicleKey?.let {
                                    patternTracker.recordInteraction(it)
                                    updateHumanPersonality()
                                }
                                saveMessages()
                                if (currentTab == "chat") render("chat")
                                else updateVoiceDisplay("인식: " + text.take(76))
                            }
                        }
                        override fun onVehicleText(text: String) {
                            runOnUiThread {
                                if (!voiceActive) return@runOnUiThread
                                messages.add("VPE" to text)
                                saveMessages()
                                if (currentTab == "chat") render("chat")
                                else updateVoiceDisplay("VPE: " + text.take(76))
                            }
                        }
                        override fun onEnded(reason: String) {
                            runOnUiThread {
                                if (!voiceActive) return@runOnUiThread
                                stopRealtimeVoice()
                                Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
                            }
                        }
                    })
                    realtimeVoice = session
                    runCatching { session.start(temp, model) }.onFailure { e ->
                        stopRealtimeVoice()
                        Toast.makeText(this, "음성 연결 실패: " + e.message,
                            Toast.LENGTH_LONG).show()
                    }
                }.onFailure { error ->
                    stopRealtimeVoice()
                    AlertDialog.Builder(this)
                        .setTitle("VPE Realtime 연결 실패")
                        .setMessage(
                            (error.message ?: "실시간 음성 토큰 요청 실패") +
                            "\nRender 최신 배포 및 OpenAI Realtime 사용 가능 여부를 확인해 줘.")
                        .setPositiveButton("확인", null).show()
                }
            }
        }.start()
    }

    private fun updateVoiceDisplay(status: String) {
        voiceDisplay = status
        voiceHint?.text = status
        voiceOrb?.mode = when {
            voiceStarting -> VpeWaveView.Mode.CONNECTING
            !voiceActive -> VpeWaveView.Mode.READY
            status.contains("말하는 중") -> VpeWaveView.Mode.SPEAKING
            else -> VpeWaveView.Mode.LISTENING
        }
    }

    private fun refreshRealtimePersona() {
        if (!voiceActive || voiceStarting) return
        val state = hpe ?: return
        val signature = state.core.axes.toString() + "|" + state.affect.toString()
        if (signature == voicePersonaSignature) return
        voicePersonaSignature = signature
        realtimeVoice?.updatePersona(aiContext(state))
    }

    private fun stopRealtimeVoice() {
        voiceActive = false
        voiceStarting = false
        voicePersonaSignature = null
        realtimeVoice?.release()
        realtimeVoice = null
        voiceDisplay = "가운데 VPE 오브를 눌러 음성 대화 시작"
        if (!isFinishing && !isDestroyed && currentTab == "home") render("home")
    }

    private fun initializeTts() {
        tts = TextToSpeech(this) { status ->
            mainHandler.post {
                val engine = tts
                if (status == TextToSpeech.SUCCESS && engine != null) {
                    val language = engine.setLanguage(Locale.KOREAN)
                    ttsReady = language != TextToSpeech.LANG_MISSING_DATA &&
                        language != TextToSpeech.LANG_NOT_SUPPORTED
                    ttsStatus = if (ttsReady) "한국어 TTS 준비됨" else "한국어 음성 데이터 없음"
                } else {
                    ttsReady = false
                    ttsStatus = "Android TTS 엔진 없음 또는 초기화 실패"
                }
                diagnosticLogs.add("TTS: " + ttsStatus)
                if (currentTab == "chat") render("chat")
            }
        }
    }

    private fun speakVehicleText(text: String, showError: Boolean = false) {
        if (voiceActive) {
            realtimeVoice?.announce(text)
            return
        }
        if (!ttsReady) {
            if (showError) Toast.makeText(this, ttsStatus, Toast.LENGTH_LONG).show()
            return
        }
        val voice = identity?.voiceBlueprint
        val rate = (.65 * (voice?.speechRate ?: 1.0) +
            .35 * (hpe?.expression?.voiceSpeed ?: 1.0)).toFloat().coerceIn(.75f, 1.2f)
        tts?.setSpeechRate(rate)
        tts?.setPitch((voice?.pitch ?: 1.0).toFloat().coerceIn(.8f, 1.25f))
        val status = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null,
            "vpe-" + System.currentTimeMillis())
        if (status != TextToSpeech.SUCCESS && showError)
            Toast.makeText(this, "TTS 재생 요청 실패", Toast.LENGTH_LONG).show()
    }

    private fun startLivePolling() {
        if (livePolling) return
        livePolling = true
        mainHandler.postDelayed(pollTask, 8_000L)
    }

    private fun stopLivePolling() {
        livePolling = false
        mainHandler.removeCallbacks(pollTask)
    }

    private fun proactiveOnVehicleChange(firstConnection: Boolean = false) {
        if (demo) return
        val state = hpe ?: return
        val now = System.currentTimeMillis()
        val event = (proactiveEngine.next(latest, state, now)
            ?: if (firstConnection) proactiveEngine.greeting(state, now) else null)
            ?: return
        lastProactiveText = event.text
        diagnosticLogs.add("PROACTIVE_" + event.kind.uppercase(Locale.ROOT))
        if (diagnosticLogs.size > 80) diagnosticLogs.removeAt(0)
        messages.add("VPE" to event.text)
        saveMessages()
        if (currentTab == "chat") {
            liveMessages?.let { appendBubble(it, "VPE", event.text) }
            liveChatScroll?.post { liveChatScroll?.fullScroll(View.FOCUS_DOWN) }
        }
        val currentVehicle = identity
        if (!hasSession() || currentVehicle == null) {
            proactiveStatus = "차량 이벤트 " + event.kind + " → 기본 대사 출력" +
                if (ttsReady) " · TTS 요청" else " · 음성 엔진 미준비"
            speakVehicleText(event.text)
            return
        }
        val token = sessionToken()
        val scope = activeChatScope
        val key = currentVehicle.vehicleKey
        val context = aiContext(state)
        val server = serverUrl()
        val vehicleName = storedVehicleName()
        val original = event.text
        proactiveStatus = "차량 이벤트 " + event.kind + " → GPT 능동 대화 생성 중"
        // Network request is never executed on the Automotive UI thread.
        Thread {
            val generated = runCatching {
                VpeAiClient.proactiveAccount(
                    server, token, key, vehicleName, event.kind, context)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || activeChatScope != scope ||
                    identity?.vehicleKey != key) return@runOnUiThread
                generated.onSuccess { answer ->
                    val position = messages.indexOfLast { it.first == "VPE" && it.second == original }
                    if (position >= 0) messages[position] = "VPE" to answer
                    else messages.add("VPE" to answer)
                    if (lastProactiveText == original) lastProactiveText = answer
                    proactiveStatus = "차량 이벤트 " + event.kind + " → GPT 응답 성공" +
                        if (ttsReady) " · TTS 요청" else " · 음성 엔진 미준비"
                    diagnosticLogs.add("PROACTIVE_GPT_OK: " + event.kind)
                    saveMessages()
                    if (currentTab == "chat" || currentTab == "home") render(currentTab)
                    speakVehicleText(answer)
                }.onFailure { error ->
                    proactiveStatus = "차량 이벤트 " + event.kind +
                        " → GPT 연결 실패, 기본 대사 유지"
                    diagnosticLogs.add("PROACTIVE_GPT_ERROR: " +
                        (error.message ?: error.javaClass.simpleName).take(100))
                    if (currentTab == "home") render("home")
                    speakVehicleText(original)
                }
                if (diagnosticLogs.size > 80) diagnosticLogs.removeAt(0)
            }
        }.start()
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
            .put("modelYear", value(if (demo) null else latest.modelYear))
            .put("modelYearSource", value(if (demo || latest.modelYear == null) null else "VIN-derived"))
            .put("energyPercent", value(if (demo) snapshot?.fuelPercent else latest.energyPercent()))
            .put("energySource", value(if (demo) "DEMO" else latest.energySource()))
            .put("actualCabinC", value(if (demo) null else latest.cabinTemperatureC))
            .put("targetCabinC", value(if (demo) null else latest.targetTemperatureC))
            .put("hvacOn", value(if (demo) null else latest.hvacOn))
            .put("chargingSwitchOn", value(if (demo) null else latest.charging))
            .put("parked", value(if (demo) null else latest.parked))
            .put("safetyWarning", value(if (demo) null else if (latest.safetyWarning == true || latest.washerFluidWarning == true) true else null))
            .put("tripDistanceSdkRaw", value(if (demo) null else latest.tripDistanceRaw))
            .put("tripTimeSdkRaw", value(if (demo) null else latest.tripTimeRaw))
            .put("drivingMode", value(if (demo) null else latest.drivingMode))
            .put("washerFluidWarning", value(if (demo) null else latest.washerFluidWarning))
            .put("engineOilState", value(if (demo) null else latest.engineOilState))
            .put("brakeFluidState", value(if (demo) null else latest.brakeFluidState))
            .put("rangeRemainingSdkRaw", value(if (demo) null else latest.rangeRemainingRaw))
            .put("batteryCapacitySdkRaw", value(if (demo) null else latest.batteryCapacityRaw))
            .put("cruiseControlState", value(if (demo) null else latest.cruiseControlState))
            .put("cruiseTargetSpeedSdkRaw", value(if (demo) null else latest.cruiseTargetSpeedRaw))
            .put("fanSpeedLevel", value(if (demo) null else latest.fanSpeedLevel))
        val memory = JSONObject()
            .put("firstKm", value(state.memory.firstKm))
            .put("lastKm", value(state.memory.lastKm))
            .put("sharedKm", state.memory.sharedKm)
            .put("meaningfulEvents", state.memory.meaningfulEvents)
            .put("lastExploration", value(state.memory.lastExploration))
            .put("lastRegularity", value(state.memory.lastRegularity))
            .put("lastInteraction", value(state.memory.lastInteraction))
            .put("highlights", JSONArray(state.memory.highlights.takeLast(12)))
        val id = identity
        val base = observedSnapshot ?: id?.lastLearned
        val voice = id?.voiceBlueprint
        val snap = JSONObject()
            .put("ageYears", value(if (demo) base?.ageYears else latest.modelYear?.let { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - it }))
            .put("odometerKm", value(base?.odometerKm))
            .put("fuelPercent", value(if (demo) base?.fuelPercent else latest.energyPercent()))
            .put("maintenanceScore", value(if (demo) base?.maintenanceScore else null))
            .put("explorationRatio", value(if (demo) base?.explorationRatio else null))
            .put("longTripRatio", value(if (demo) base?.longTripRatio else null))
        val voiceJson = JSONObject()
            .put("genderExpression", value(voice?.genderExpression))
            .put("perceivedAge", value(voice?.perceivedAge))
            .put("pitch", value(voice?.pitch))
            .put("resonance", value(voice?.resonance))
            .put("softness", value(voice?.softness))
            .put("speechRate", value(voice?.speechRate))
        return JSONObject()
            .put("demo", demo)
            .put("vehicleName", storedVehicleName())
            .put("voicePresentation", identity?.voiceBlueprint?.presentation ?: "미설정")
            .put("axes", JSONArray(state.core.axes))
            .put("mbti", state.core.mbti)
            .put("evidenceCoverage", state.core.confidence)
            .put("traits", traits)
            .put("affect", emotion)
            .put("currentFeeling", state.expression.headline)
            .put("voiceSpeed", state.expression.voiceSpeed)
            .put("vehicle", vehicle)
            .put("memory", memory)
            .put("snapshot", snap)
            .put("voice", voiceJson)
    }

    private fun compareCurrentPersona() {
        val state = hpe
        if (state == null || !hasSession()) {
            Toast.makeText(this, "차량 연결과 VPE 로그인이 필요해.", Toast.LENGTH_LONG).show()
            return
        }
        val scope = activeChatScope
        val context = aiContext(state)
        val token = sessionToken()
        Toast.makeText(this, "실제·대조 성격으로 GPT 응답을 비교하고 있어.", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching {
                VpeAiClient.comparePersona(serverUrl(), token, context)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed || scope != activeChatScope) return@runOnUiThread
                result.onSuccess { reply ->
                    val actual = reply.optString("actualReply")
                    val contrast = reply.optString("contrastReply")
                    val actualMbti = reply.optString("actualMbti")
                    val contrastMbti = reply.optString("contrastMbti")
                    diagnosticLogs.add("GPT_PERSONA_CONTRAST_OK")
                    AlertDialog.Builder(this)
                        .setTitle("GPT 성격 반영 실험")
                        .setMessage(
                            "질문: " + reply.optString("controlledQuestion") +
                            "\n\n[실제 VPE · " + actualMbti + "]\n" + actual +
                            "\n\n[테스트용 대조 성격 · " + contrastMbti + "]\n" + contrast +
                            "\n\n두 응답의 말투·문장 길이·호응 방식을 비교해 봐." +
                            "\n실제 차량 센서값은 같고 성격 점수만 반전했어.")
                        .setPositiveButton("확인", null)
                        .show()
                }.onFailure { error ->
                    diagnosticLogs.add("GPT_PERSONA_CONTRAST_ERROR: " +
                        (error.message ?: "REQUEST_FAILED").take(120))
                    AlertDialog.Builder(this)
                        .setTitle("GPT 성격 비교 연결 오류")
                        .setMessage(error.message ?: "요청에 실패했어.")
                        .setPositiveButton("확인", null)
                        .show()
                }
                if (diagnosticLogs.size > 80) diagnosticLogs.removeAt(0)
            }
        }.start()
    }

    private fun sendMessage(message: String) {
        if (!demo) identity?.vehicleKey?.let { key ->
            patternTracker.recordInteraction(key)
            updateHumanPersonality()
        }
        val state = hpe
        val token = sessionToken()
        val id = identity
        val canUseGpt = id != null && state != null && token.isNotBlank()
        messages.add("나" to message)
        val replyIndex = messages.size
        val text = when {
            canUseGpt -> "GPT가 답변을 만들고 있어요…"
            id != null && state != null && token.isBlank() ->
                "GPT 대화를 사용하려면 먼저 회원가입 또는 로그인을 해 줘. " +
                "위의 ‘로그인 / 회원가입’ 버튼을 누르면 돼.\n\n" +
                "[데모 로컬 응답] " + reply(message)
            else -> "[로컬 체험 응답] " + reply(message)
        }
        messages.add("VPE" to text)
        saveMessages()
        if (currentTab == "chat") render("chat")
        if (!canUseGpt || state == null || id == null) {
            if (id != null && state != null) speakVehicleText(reply(message))
            return
        }
        pendingChats++
        val scope = activeChatScope
        val context = aiContext(state)
        val name = storedVehicleName()
        Thread {
            val result = runCatching {
                VpeAiClient.askAccount(serverUrl(), token, id.vehicleKey, name, message, context)
            }.getOrElse { error ->
                "[GPT 연결 오류: " + (error.message ?: "알 수 없는 오류") + "]\n" +
                    "로컬 응답: " + reply(message)
            }
            runOnUiThread {
                if (scope != activeChatScope) return@runOnUiThread
                pendingChats = (pendingChats - 1).coerceAtLeast(0)
                if (!isFinishing && !isDestroyed && replyIndex < messages.size) {
                    messages[replyIndex] = "VPE" to result
                    if (!result.startsWith("[GPT 연결 오류")) speakVehicleText(result)
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
        sourceCard.addView(dataRow("HPE 에너지 입력 출처", latest.energySource() ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("EV Battery", latest.evBatteryLevel?.let { "수신 · ${it.toInt()}%" } ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("Vehicle identity", if (latest.vehicleKey != null) "암호화 식별값 확인" else "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("모델 연도(VIN 추정)", latest.modelYear?.let { "${it}년 · VIN 해석" } ?: "미확인"))
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
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("트립 거리(SDK 원값)", latest.tripDistanceRaw ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("트립 시간(SDK 원값)", latest.tripTimeRaw ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("드라이빙 모드", latest.drivingMode ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("워셔액 부족 경고", latest.washerFluidWarning?.toString() ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("엔진오일 상태", latest.engineOilState ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("브레이크액 상태", latest.brakeFluidState ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("남은 주행가능거리(SDK 원값)", latest.rangeRemainingRaw ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("배터리 용량(SDK 원값)", latest.batteryCapacityRaw ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("크루즈 상태", latest.cruiseControlState ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("크루즈 목표속도(SDK 원값)", latest.cruiseTargetSpeedRaw ?: "미수신"))
        sourceCard.addView(gap(13))
        sourceCard.addView(dataRow("공조 팬속도", latest.fanSpeedLevel?.toString() ?: "미수신"))
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
        page.addView(label("음성 출력 상태: " + ttsStatus, 13f, secondary))
        page.addView(gap(10))
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
        stopLivePolling()
        lastProactiveText = null
        lastLiveChange = null
        proactiveStatus = "SDK 연결 진행 중"
        modeUpdatedAtMs = 0L
        cruiseUpdatedAtMs = 0L
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
        val callback: (String, VehicleReadings?) -> Unit = { log, reading ->
                runOnUiThread {
                    if (serial != requestSerial) return@runOnUiThread
                    diagnosticLogs.add(log)
                    if (diagnosticLogs.size > 80) diagnosticLogs.removeAt(0)
                    if (reading != null) {
                        val before = latest
                        latest = latest.mergeObserved(reading)
                        if (reading.drivingMode != null)
                            modeUpdatedAtMs = System.currentTimeMillis()
                        if (reading.cruiseControlState != null)
                            cruiseUpdatedAtMs = System.currentTimeMillis()
                        if (latest != before) observedChange(before, latest)?.let { lastLiveChange = it }
                        if (connecting && latest.odometerKm != null &&
                            latest.energyPercent() != null && latest.vehicleKey != null) {
                            finishConnection(serial, timeout = false)
                        } else if (!connecting && !demo && latest != before) {
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
        liveSdkCallback = callback
        Thread { sdk.readVehicleData(callback) }.start()
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
        val seed = VehicleSnapshot(latest.modelYear?.let { (year - it).toDouble() } ?: 10.0, km, latest.energyPercent() ?: 50.0,
            50.0, 0.5, 0.5)
        val next = if (existing == null) seed else
            VehicleDataMapper.merge(observedSnapshot ?: existing.lastLearned, latest, year)
        observedSnapshot = next
        identity = if (existing == null) IdentityEngine.generate(key, next)
            else IdentityEngine.evolve(existing, next)
        identity?.let(store::save)
        cachedReport = false
        updateHumanPersonality()
        activateChatScope()
        startLivePolling()
        proactiveOnVehicleChange(firstConnection = true)
        syncCurrentVehicle(force = true)
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
        refreshRealtimePersona()
        liveMoodStatus?.text = liveMoodSummary()
        proactiveOnVehicleChange()
        syncCurrentVehicle()
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
        val patterns = if (demo) null else patternTracker.summary(key)
        val signals = HpeSignals(
            vehicleKey = key,
            odometerKm = readings.odometerKm,
            energyPercent = readings.energyPercent(),
            actualCabinC = readings.cabinTemperatureC,
            targetCabinC = readings.targetTemperatureC,
            hvacOn = readings.hvacOn,
            charging = readings.charging,
            parked = readings.parked,
            safetyWarning = if (readings.safetyWarning == true || readings.washerFluidWarning == true) true else null,
            modelAgeYears = if (demo) snapshot.ageYears else readings.modelYear?.let {
                (Calendar.getInstance().get(Calendar.YEAR) - it).toDouble()
            },
            interactionRatio = patterns?.interactionRatio,
            modeVarietyRatio = patterns?.modeVarietyRatio,
            cruiseUseRatio = patterns?.cruiseUseRatio
        )
        val previous = hpe?.takeIf { it.key == key }
            ?: if (demo) null else hpeStore.load(key)
        hpe = HumanPersonalityEngine.observe(signals, previous)
        if (!demo) hpe?.let(hpeStore::save)
    }

    private fun loadDemo() {
        stopLivePolling()
        ++requestSerial
        connecting = false
        demo = true
        latest = VehicleReadings()
        observedSnapshot = VehicleSnapshot(7.0, 87320.0, 71.0, 78.0, 0.64, 0.34)
        identity = IdentityEngine.generate("demo-vehicle", observedSnapshot!!)
        hpe = null
        cachedReport = false
        updateHumanPersonality()
        activateChatScope()
        syncCurrentVehicle(force = true)
        connectionNote = "가상의 차량 데이터로 UI와 성격 알고리즘을 체험하고 있어요."
        diagnosticLogs.clear()
        render("report")
    }

    private fun activateChatScope() {
        val key = identity?.vehicleKey ?: "unassigned"
        val scope = (accountName().ifBlank { "guest" }) + "|" + key
        if (activeChatScope == scope) return
        activeChatScope = scope
        ++historyLoadSerial
        pendingChats = 0
        messages.clear()
        loadMessages()
        if (messages.isEmpty()) {
            messages.add("VPE" to "안녕! 이 차량만의 대화를 시작해 보자.")
        }
        val token = sessionToken()
        if (token.isBlank() || identity == null) return
        val serial = historyLoadSerial
        Thread {
            val remote = runCatching { VpeAiClient.history(serverUrl(), token, key) }
            runOnUiThread {
                if (scope != activeChatScope || serial != historyLoadSerial || pendingChats > 0)
                    return@runOnUiThread
                remote.onSuccess { items ->
                    val updated = mutableListOf<Pair<String, String>>()
                    for (i in 0 until items.length()) {
                        val entry = items.optJSONObject(i) ?: continue
                        updated.add(
                            (if (entry.optString("role") == "user") "나" else "VPE") to
                                entry.optString("content"))
                    }
                    messages.clear()
                    if (updated.isEmpty()) messages.add("VPE" to "안녕! 이 차량만의 대화를 시작해 보자.")
                    else messages.addAll(updated.takeLast(60))
                    saveMessages()
                    if (currentTab == "chat") render("chat")
                }
            }
        }.start()
    }

    private fun loadMessages() {
        val scope = activeChatScope ?: return
        val raw = getSharedPreferences("vpe_chat", MODE_PRIVATE)
            .getString("history_" + scope, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                messages.add(item.getString("who") to item.getString("text"))
            }
        }
    }

    private fun saveMessages() {
        val scope = activeChatScope ?: return
        val array = JSONArray()
        messages.takeLast(60).forEach { (who, message) ->
            array.put(JSONObject().put("who", who).put("text", message))
        }
        getSharedPreferences("vpe_chat", MODE_PRIVATE).edit()
            .putString("history_" + scope, array.toString()).apply()
    }

    override fun onDestroy() {
        ++requestSerial
        stopLivePolling()
        mainHandler.removeCallbacksAndMessages(null)
        realtimeVoice?.release()
        realtimeVoice = null
        tts?.stop()
        tts?.shutdown()
        sdk.release()
        super.onDestroy()
    }
}
