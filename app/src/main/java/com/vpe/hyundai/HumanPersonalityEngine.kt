package com.vpe.hyundai

import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * HPE 1.0: six distinct computational stages.
 * Inspired by dimensional trait psychology, narrative identity, if-then
 * behavioural signatures, cognitive appraisal and valence/arousal.
 * Weights below are VPE engineering hypotheses, not measured psychometric
 * coefficients. They are deterministic, bounded, and traceable.
 */
data class HpeSignals(
    val vehicleKey: String,
    val odometerKm: Double? = null,
    val energyPercent: Double? = null,
    val actualCabinC: Double? = null,
    val targetCabinC: Double? = null,
    val hvacOn: Boolean? = null,
    val charging: Boolean? = null,
    val parked: Boolean? = null,
    val safetyWarning: Boolean? = null,
    val explorationRatio: Double? = null,
    val regularityRatio: Double? = null,
    val interactionRatio: Double? = null,
    val modelAgeYears: Double? = null
)

data class HpeCore(
    val axes: List<Double>,     // E, N, T, J: >.5 maps to E/N/T/J
    val traits: List<Double>,   // activity, curiosity, warmth, confidence, caution, playfulness, expressiveness
    val confidence: Double
) {
    val mbti: String get() = buildString {
        append(if (axes[0] >= .5) "E" else "I")
        append(if (axes[1] >= .5) "N" else "S")
        append(if (axes[2] >= .5) "T" else "F")
        append(if (axes[3] >= .5) "J" else "P")
    }
}

data class HpeMemory(
    val firstKm: Double? = null,
    val lastKm: Double? = null,
    val sharedKm: Double = 0.0,
    val meaningfulEvents: Int = 0,
    val lastExploration: Double? = null,
    val lastRegularity: Double? = null,
    val lastInteraction: Double? = null,
    val highlights: List<String> = emptyList()
)

data class HpeAppraisal(
    val energyNeed: Double,
    val heat: Double,
    val cold: Double,
    val uncertainty: Double,
    val relief: Double,
    val safetyRisk: Double,
    val measuredClimate: Boolean
)
data class HpeAffect(
    val hunger: Double, val heat: Double, val cold: Double,
    val fatigue: Double, val excitement: Double,
    val tension: Double, val relief: Double, val comfort: Double,
    val valence: Double, val arousal: Double
)
data class HpeExpression(
    val headline: String,
    val utterance: String,
    val motionIntensity: Double,
    val voiceSpeed: Double,
    val warmth: Double,
    val canInitiate: Boolean
)
data class HpeState(
    val key: String,
    val core: HpeCore,
    val memory: HpeMemory,
    val affect: HpeAffect,
    val expression: HpeExpression,
    val sources: List<String>,
    val lastSignals: HpeSignals
)

object HumanPersonalityEngine {
    const val VERSION = 3
    // Engineering priors; not coefficients fitted to psychometric or vehicle data.
    const val BASE_SEED_SPREAD = .20
    const val SOCIAL_TO_E = .28
    const val EXPLORATION_TO_N = .36
    const val REGULARITY_TO_J = .32
    const val EXPERIENCE_SCALE_KM = 250_000.0
    const val MODEL_AGE_SCALE_YEARS = 25.0
    const val FIRST_EVIDENCE_ADAPTATION = .35
    const val MEANINGFUL_PATTERN_ADAPTATION = .12
    const val MILEAGE_EVENT_ADAPTATION = .03
    private fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
    private fun ratio(x: Double?) = x?.takeIf { it.isFinite() && it in 0.0..1.0 }
    private fun normal(x: Double) = clamp(x)
    private fun unitSeed(key: String, label: String): Double {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(("vpe-hpe1|" + key + "|" + label).toByteArray(Charsets.UTF_8))
        return ((bytes[0].toInt() and 0xff) * 256 + (bytes[1].toInt() and 0xff)) / 65535.0
    }

    /** A vehicle identity is a fictional design seed, not a psychometric claim. */
    fun makeCore(s: HpeSignals): HpeCore {
        require(s.vehicleKey.isNotBlank())
        val explore = ratio(s.explorationRatio)
        val regular = ratio(s.regularityRatio)
        val social = ratio(s.interactionRatio)
        val experience = s.odometerKm?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { clamp(it / EXPERIENCE_SCALE_KM) }
        val modelAge = s.modelAgeYears?.takeIf { it.isFinite() && it in 0.0..60.0 }
            ?.let { clamp(it / MODEL_AGE_SCALE_YEARS) }
        val seasoning = when {
            experience != null && modelAge != null -> .65 * experience + .35 * modelAge
            experience != null -> experience
            else -> modelAge
        }
        fun base(name: String) = .5 + BASE_SEED_SPREAD * (unitSeed(s.vehicleKey, name) - .5)
        val axes = listOf(
            clamp(base("E") + (social?.let { SOCIAL_TO_E * (it - .5) } ?: 0.0)),
            clamp(base("N") + (explore?.let { EXPLORATION_TO_N * (it - .5) } ?: 0.0)),
            clamp(base("T")),
            clamp(base("J") + (regular?.let { REGULARITY_TO_J * (it - .5) } ?: 0.0))
        )
        val e = axes[0]; val n = axes[1]; val t = axes[2]; val j = axes[3]
        val traits = listOf(
            clamp(.23 + .54 * e + .10 * (experience ?: .5)),
            clamp(.18 + .66 * n + .10 * (explore ?: .5)),
            clamp(.25 + .52 * (1.0 - t) + .10 * (social ?: .5)),
            clamp(.32 + .32 * (seasoning ?: .5) + .20 * e),
            clamp(.22 + .55 * j + .12 * t + .08 * ((modelAge ?: .5) - .5)),
            clamp(.18 + .55 * (1.0 - j) + .18 * e),
            clamp(.20 + .60 * e + .10 * n)
        )
        // Four MBTI axes: only E/N/J have directly relevant optional
        // behavioural evidence. T/F remains an explicit seeded style axis.
        val evidence = listOfNotNull(s.odometerKm, explore, regular, social, modelAge).size
        return HpeCore(axes, traits, clamp(evidence / 5.0))
    }

    /** Sensor polls are not experience events. Keep first and last readings. */
    fun learn(previous: HpeMemory?, s: HpeSignals): HpeMemory {
        val old = previous ?: HpeMemory()
        val odometer = s.odometerKm?.takeIf { it.isFinite() && it >= 0.0 }
        val first = old.firstKm ?: odometer
        val last = old.lastKm
        val delta = if (odometer != null && last != null && odometer >= last)
            odometer - last else 0.0
        val explore = ratio(s.explorationRatio)
        val regular = ratio(s.regularityRatio)
        val social = ratio(s.interactionRatio)
        val changed = delta >= 100.0 ||
            (previous != null && explore != null && (old.lastExploration == null ||
                abs(explore-old.lastExploration) >= .05)) ||
            (previous != null && regular != null && (old.lastRegularity == null ||
                abs(regular-old.lastRegularity) >= .05)) ||
            (previous != null && social != null && (old.lastInteraction == null ||
                abs(social-old.lastInteraction) >= .05))
        val notes = if (changed && delta >= 100.0)
            (old.highlights + ("함께 달린 거리 +" + delta.toInt() + "km")).takeLast(12)
        else old.highlights
        return old.copy(
            firstKm = first,
            // Keep the learning anchor until a meaningful event; otherwise
            // 1 km periodic callbacks would prevent ever reaching 100 km.
            lastKm = if (last == null || changed) (odometer ?: last) else last,
            sharedKm = if (first != null && odometer != null && odometer >= first)
                max(old.sharedKm, odometer - first) else old.sharedKm,
            meaningfulEvents = old.meaningfulEvents + if (changed) 1 else 0,
            lastExploration = explore ?: old.lastExploration,
            lastRegularity = regular ?: old.lastRegularity,
            lastInteraction = social ?: old.lastInteraction,
            highlights = notes
        )
    }

    /** Scherer/Lazarus-style engineering appraisal: need and coping are separate. */
    fun appraise(s: HpeSignals): HpeAppraisal {
        val soc = s.energyPercent?.takeIf { it.isFinite() && it in 0.0..100.0 }
        val hunger = soc?.let { clamp((35.0 - it) / 35.0) } ?: 0.0
        val actual = s.actualCabinC?.takeIf { it.isFinite() && it in -30.0..60.0 }
        val target = s.targetCabinC?.takeIf { it.isFinite() && it in 14.0..35.0 }
        val difference = if (actual != null && target != null) actual - target else null
        val heat = difference?.let { clamp((it - 1.0) / 7.0) } ?: 0.0
        val cold = difference?.let { clamp((-it - 1.0) / 7.0) } ?: 0.0
        val canCope = if (s.charging == true) 1.0 else 0.0
        val safety = if (s.safetyWarning == true) 1.0 else 0.0
        val uncertainty = clamp(hunger * (1 - .60 * canCope) + .65 * safety)
        val relief = if (s.charging == true && hunger > .05) .65 else 0.0
        return HpeAppraisal(hunger, heat, cold, uncertainty, relief, safety, difference != null)
    }

    /** Emotions remain multidimensional; valence/arousal drive presentation only. */
    fun affect(a: HpeAppraisal, s: HpeSignals): HpeAffect {
        val fatigue = 0.0 // No verified trip-duration source; do not fabricate fatigue.
        val excitement = 0.0 // Requires a verified novelty event.
        val comfort = if (a.measuredClimate) clamp(1.0-max(a.heat,a.cold)) else 0.0
        val tension = clamp(.75 * a.uncertainty + .25 * a.safetyRisk)
        val valence = (comfort * .35 + a.relief * .45 -
            a.energyNeed * .3 - tension * .5 - (a.heat+a.cold) * .22).coerceIn(-1.0,1.0)
        val arousal = clamp(.18 + tension*.48 + max(a.heat,a.cold)*.16 + excitement*.25)
        return HpeAffect(a.energyNeed, a.heat, a.cold, fatigue, excitement,
            tension, a.relief, comfort, valence, arousal)
    }

    /**
     * CAPS-like if/then behavioural signature: objective concern stays the same,
     * style varies with persistent personality. No unsolicited in-motion speech.
     */
    fun express(core: HpeCore, mood: HpeAffect, s: HpeSignals): HpeExpression {
        val t = core.axes[2]
        val play = core.traits[5]
        val expressive = core.traits[6]
        val priority = when {
            s.safetyWarning == true -> "safety"
            mood.hunger >= .25 -> "energy"
            mood.heat >= .35 -> "hot"
            mood.cold >= .35 -> "cold"
            s.charging == true && mood.relief > 0 -> "charging"
            else -> "calm"
        }
        val analytical = t >= .56 && play < .70
        val message = when(priority) {
            "safety" -> "차량 경고가 감지됐어. 상태를 확인해 줘."
            "energy" -> if (analytical) "에너지 잔량이 낮아. 충전 계획을 확인하자."
                else if (play >= .56) "으아, 배고파! 에너지를 좀 채워주면 좋겠어!"
                else "에너지가 부족해. 충전하면서 쉬어가면 좋겠다."
            "hot" -> if (analytical) "실내가 목표 온도보다 높아. 냉방 상태를 확인해 보자."
                else "으, 좀 더워! 실내가 시원해지면 좋겠어."
            "cold" -> if (analytical) "실내가 목표 온도보다 낮아. 난방 상태를 확인해 보자."
                else "으으, 추워! 따뜻해지면 좋겠어."
            "charging" -> "충전하고 있으니 조금 안심이 돼."
            else -> if (play >= .56) "오늘은 어떤 이야기를 해볼까? 궁금해!"
                else "오늘도 편안하게 함께하자."
        }
        val name = when(priority) {
            "safety" -> "차량 상태 확인"
            "energy" -> "배고픔"
            "hot" -> "더움"
            "cold" -> "추움"
            "charging" -> "안도"
            else -> "차분함"
        }
        val speed = clamp(.75 + expressive * .30 - mood.tension*.14)
        return HpeExpression(name, message,
            clamp(.15+.60*mood.arousal+.12*expressive), speed,
            core.traits[2], s.parked == true)
    }

    fun observe(s: HpeSignals, previous: HpeState? = null): HpeState {
        val old = previous?.takeIf { it.key == s.vehicleKey }
        val memory = learn(old?.memory, s)
        val initialCore = old?.core ?: makeCore(s)
        // The first real long-term evidence is worth more than periodic mileage.
        val gainedEvidence = old != null && (
            (old.lastSignals.explorationRatio == null && ratio(s.explorationRatio) != null) ||
            (old.lastSignals.regularityRatio == null && ratio(s.regularityRatio) != null) ||
            (old.lastSignals.interactionRatio == null && ratio(s.interactionRatio) != null) ||
            (old.lastSignals.modelAgeYears == null && s.modelAgeYears != null) ||
            (old.lastSignals.odometerKm == null && s.odometerKm != null))
        val meaningful = old != null && memory.meaningfulEvents > old.memory.meaningfulEvents
        val candidate = makeCore(s)
        val weight = when {
            old == null -> 1.0
            gainedEvidence -> FIRST_EVIDENCE_ADAPTATION
            meaningful && (s.explorationRatio != old.lastSignals.explorationRatio ||
                s.regularityRatio != old.lastSignals.regularityRatio ||
                s.interactionRatio != old.lastSignals.interactionRatio) ->
                MEANINGFUL_PATTERN_ADAPTATION
            meaningful -> MILEAGE_EVENT_ADAPTATION
            else -> 0.0
        }
        val core = if (old == null) candidate else HpeCore(
            axes = initialCore.axes.zip(candidate.axes) { stable, observed ->
                clamp((1.0-weight) * stable + weight * observed)
            },
            traits = initialCore.traits.zip(candidate.traits) { stable, observed ->
                clamp((1.0-weight) * stable + weight * observed)
            },
            confidence = max(initialCore.confidence, candidate.confidence)
        )
        val a = appraise(s)
        val mood = affect(a, s)
        val style = express(core, mood, s)
        val sources = buildList {
            if (s.odometerKm != null) add("주행거리: 실제 관측값")
            if (s.modelAgeYears != null) add("차량 연식: SDK VIN으로부터 해석한 모델연도")
            if (s.energyPercent != null) add("에너지: 실제 관측값")
            if (s.actualCabinC != null) add("실내 현재온도: 수신값")
            if (s.targetCabinC != null) add("목표 온도: 설정값")
            if (s.charging != null) add("충전 상태: 수신값")
            if (s.parked != null) add("정차 상태: 수신값")
            if (s.explorationRatio == null) add("신규 방문 이력: 미연결")
            else add("신규 방문 비율: 장기 패턴 집계 입력")
            if (s.regularityRatio == null) add("운행 규칙성: 미연결")
            else add("운행 규칙성: 장기 패턴 집계 입력")
            if (s.interactionRatio == null) add("대화 빈도: 미연결")
            else add("대화 빈도: 장기 패턴 집계 입력")
            add("장기 입력 커버리지: ${(core.confidence * 100).toInt()}% (5개 항목 기준)")
            add("MBTI T/F: 차량 고유 시드 기반 창작 유형")
        }
        return HpeState(s.vehicleKey,core,memory,mood,style,sources,s)
    }
}
