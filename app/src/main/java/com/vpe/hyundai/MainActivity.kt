package com.vpe.hyundai

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
    private lateinit var sdk: PleosVehicleClient
    private lateinit var store: IdentityStore
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
        sdk = PleosVehicleClient(this)
        store = IdentityStore(this)
        identity = store.load()?.takeIf { !it.vehicleKey.startsWith("demo") }
        connectionNote = if (identity != null) "저장된 VPE를 불러왔어요. 실시간 차량 정보는 새로 연결해 주세요." else "아직 차량과 연결되지 않았어요."
        loadMessages()
        if (messages.isEmpty()) messages.add("VPE" to "안녕! 내 차의 데이터를 연결하면 우리만의 성격을 만들어 볼 수 있어. 먼저 홈에서 연결해 줘.")
        render("home")
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
        hero.addView(button(if (connecting) "연결 중 · 잠시만요"
            else if (identity != null && !demo) "차량 데이터 새로고침"
            else "Pleos 차량 연결하기") { if (!connecting) connectVehicle() })
        page.addView(hero)
        page.addView(gap(12))
        page.addView(button("✦  시연용 데모 체험", primary = false) { loadDemo() })
        page.addView(gap(24))

        if (identity != null) {
            page.addView(label("나의 페르소나", 20f, ink, true))
            page.addView(gap(12))
            val traits = identity!!.memory.traits
            val profile = card()
            profile.addView(row().apply {
                addView(label(if (demo) "데모 페르소나" else "VPE IDENTITY", 13f, teal, true),
                    LinearLayout.LayoutParams(0, -2, 1f))
                addView(tag(if (demo) "가상 차량" else "차량 데이터 기반"))
            })
            profile.addView(gap(16))
            profile.addView(label("호기심  ${(traits[1] * 100).toInt()}%   ·   친밀감  ${(traits[2] * 100).toInt()}%", 17f, ink, true))
            profile.addView(gap(9))
            profile.addView(label("보이스 설계  ·  ${identity!!.voiceBlueprint.ageGroup} / ${identity!!.voiceBlueprint.presentation}", 13f, secondary))
            profile.addView(gap(14))
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

        messages.forEach { (sender, message) ->
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
            conversationView.addView(alignment)
            conversationView.addView(gap(11))
        }
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

    private fun sendMessage(message: String) {
        messages.add("나" to message)
        messages.add("VPE" to reply(message))
        saveMessages()
        render("chat")
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
            listOf("성격", "누구", "어떤 차").any { it in message } ->
                "난 호기심 ${(id.memory.traits[1] * 100).toInt()}%, 친밀감 ${(id.memory.traits[2] * 100).toInt()}%인 VPE야. 앞으로 함께 달리며 변할 거야!"
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
        summary.addView(gap(14))
        summary.addView(button("다시 연결하기") { connectVehicle() })
        page.addView(summary)
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
        connecting = true
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
                    if (serial != requestSerial || !connecting) return@runOnUiThread
                    diagnosticLogs.add(log)
                    if (reading != null) {
                        latest = latest.copy(
                            odometerKm = reading.odometerKm ?: latest.odometerKm,
                            fuelPercent = reading.fuelPercent ?: latest.fuelPercent,
                            modelYear = reading.modelYear ?: latest.modelYear,
                            evBatteryLevel = reading.evBatteryLevel ?: latest.evBatteryLevel,
                            vehicleKey = reading.vehicleKey ?: latest.vehicleKey
                        )
                        if (latest.odometerKm != null && latest.energyPercent() != null &&
                            latest.vehicleKey != null) {
                            finishConnection(serial, timeout = false)
                        }
                    }
                    if (log.startsWith("SERVICE_ERROR")) finishConnection(serial, timeout = false)
                }
            }
        }.start()
    }

    private fun finishConnection(serial: Int, timeout: Boolean) {
        if (serial != requestSerial || !connecting) return
        connecting = false
        val km = latest.odometerKm
        if (km != null && km.isFinite() && km >= 0) {
            val year = Calendar.getInstance().get(Calendar.YEAR)
            // Missing values are explicitly neutral algorithm inputs, not claimed SDK data.
            val neutral = VehicleSnapshot(10.0, km, latest.energyPercent() ?: 50.0,
                50.0, 0.5, 0.5)
            val existing = identity?.takeIf {
                it.vehicleKey == (latest.vehicleKey ?: "local-device-vpe") && !demo
            }
            val snap = if (existing == null) neutral else VehicleDataMapper.merge(
                existing.lastLearned, latest, year
            )
            identity = if (existing == null) IdentityEngine.generate(
                latest.vehicleKey ?: "local-device-vpe", snap
            ) else IdentityEngine.evolve(existing, snap)
            observedSnapshot = snap
            identity?.let(store::save)
            connectionNote = if (latest.energyPercent() == null)
                "주행거리 수신 완료 · 에너지 데이터는 아직 미확인"
            else "Pleos 차량 데이터를 반영했어요."
        } else {
            connectionNote = when {
                diagnosticLogs.any { it.contains("PERMISSION_DENIED") } ->
                    "차량 접근 권한이 거부됐어요. Pleos 환경과 권한을 확인해 주세요."
                timeout -> "연결 응답이 지연되고 있어요. 진단 탭을 확인해 주세요."
                else -> "차량 데이터를 확인하지 못했어요. 진단 탭에서 상세 정보를 볼 수 있어요."
            }
        }
        render("home")
    }

    private fun loadDemo() {
        ++requestSerial
        connecting = false
        demo = true
        latest = VehicleReadings()
        observedSnapshot = VehicleSnapshot(7.0, 87320.0, 71.0, 78.0, 0.64, 0.34)
        identity = IdentityEngine.generate("demo-vehicle", observedSnapshot!!)
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
