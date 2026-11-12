package com.vpe.hyundai

/** Converts real, changing vehicle readings into brief persona-conditioned speech.
 * It never changes long-term HPE traits; repeated polls do not create repeated speech.
 */
data class VpeProactiveEvent(val kind: String, val text: String)

class LiveProactiveEngine {
    private var lowEnergySpoken = false
    private var criticalEnergySpoken = false
    private var hotSpoken = false
    private var coldSpoken = false
    private var chargeSwitchSpoken = false
    private var washerWarningSpoken = false
    private var lastSpokenAt: Long? = null

    fun next(readings: VehicleReadings, state: HpeState, nowMs: Long): VpeProactiveEvent? {
        val energy = readings.energyPercent()
        if (energy == null || energy >= 26.0) {
            lowEnergySpoken = false
            criticalEnergySpoken = false
        }
        if (state.affect.heat <= .15) hotSpoken = false
        if (state.affect.cold <= .15) coldSpoken = false
        if (readings.charging != true) chargeSwitchSpoken = false
        if (readings.washerFluidWarning != true) washerWarningSpoken = false

        val playful = state.core.traits.getOrElse(5) { .5 } >= .56
        val analytical = state.core.axes.getOrElse(2) { .5 } >= .56
        val kind = when {
            readings.washerFluidWarning == true && !washerWarningSpoken -> "washer"
            energy != null && energy <= 10.0 && !criticalEnergySpoken -> "critical_energy"
            energy != null && energy <= 20.0 && !lowEnergySpoken -> "low_energy"
            state.affect.heat >= .35 && !hotSpoken -> "hot"
            state.affect.cold >= .35 && !coldSpoken -> "cold"
            readings.charging == true && !chargeSwitchSpoken -> "charge_switch"
            else -> return null
        }
        // Prioritize brief, spaced prompts instead of reacting to every sensor poll.
        if (lastSpokenAt != null && nowMs - lastSpokenAt!! < 8_000L) return null
        lastSpokenAt = nowMs
        val message = when (kind) {
            "washer" -> "워셔액 부족 알림이 들어왔어. 다음에 안전한 곳에서 확인해 줘."
            "critical_energy" -> if (playful) "으아, 에너지가 10% 이하야! 충전할 곳부터 찾아보자!"
                else "에너지 잔량이 10% 이하야. 충전을 준비하자."
            "low_energy" -> if (analytical) "에너지가 20% 이하야. 충전 계획을 확인하자."
                else if (playful) "나 슬슬 배고파~! 충전하러 가자!"
                else "에너지가 부족해졌어. 충전할 때가 됐어."
            "hot" -> if (analytical) "실내 온도가 설정 온도보다 높아. 냉방 상태를 확인하자."
                else if (playful) "으아, 너무 더워! 조금 시원해지고 싶어!"
                else "차 안이 좀 더워졌어. 온도를 확인해 줘."
            "cold" -> if (analytical) "실내 온도가 설정 온도보다 낮아. 난방 상태를 확인하자."
                else if (playful) "으으, 추워! 조금 따뜻하게 해 줄래?"
                else "차 안이 추워졌어. 온도를 확인해 줘."
            else -> if (playful) "오, 충전 스위치를 켰구나! 이제 충전할 준비야!"
                else "충전 스위치가 켜졌어. 충전 상태를 확인해 보자."
        }
        when (kind) {
            "washer" -> washerWarningSpoken = true
            "critical_energy" -> { criticalEnergySpoken = true; lowEnergySpoken = true }
            "low_energy" -> lowEnergySpoken = true
            "hot" -> hotSpoken = true
            "cold" -> coldSpoken = true
            "charge_switch" -> chargeSwitchSpoken = true
        }
        return VpeProactiveEvent(kind, message)
    }
}
