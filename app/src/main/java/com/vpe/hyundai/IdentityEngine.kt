package com.vpe.hyundai

import kotlin.math.roundToInt

/** Deterministic first-time identity generation. Versioned for reproducibility. */
data class VoiceBlueprint(
    val genderExpression: Double, // 0 masculine-presenting, 1 feminine-presenting
    val perceivedAge: Int,
    val pitch: Double,
    val resonance: Double,
    val softness: Double,
    val speechRate: Double
) {
    val presentation: String get() = if (genderExpression >= .5) "여성형" else "남성형"
    val ageGroup: String get() = "${(perceivedAge / 10) * 10}대"
}
data class VpeIdentity(
    val vehicleKey: String,
    val engineVersion: Int,
    val initial: VehicleSnapshot,
    val baseline: Persona,
    val voiceBlueprint: VoiceBlueprint,
    val memory: PersonalityMemory
)
object IdentityEngine {
    const val VERSION = 1
    fun generate(key: String, v: VehicleSnapshot): VpeIdentity {
        require(key.isNotBlank())
        val p = PersonaEngine.calculate(v)
        val age = (v.ageYears / 20.0).coerceIn(0.0, 1.0)
        val experience = (v.odometerKm / 250000.0).coerceIn(0.0, 1.0)
        val gender = (.5 + .25 * p.expressiveness - .20 * p.confidence + .10 * p.warmth).coerceIn(0.0, 1.0)
        val voice = VoiceBlueprint(
            genderExpression = gender,
            perceivedAge = (20 + 15 * age + 12 * experience).roundToInt().coerceIn(20, 50),
            pitch = (.85 + .30 * gender + .10 * p.energy).coerceIn(.7, 1.3),
            resonance = (.4 + .35 * p.confidence + .20 * experience).coerceIn(0.0, 1.0),
            softness = p.warmth,
            speechRate = (.85 + .30 * p.energy).coerceIn(.75, 1.2)
        )
        return VpeIdentity(key, VERSION, v, p, voice,
            PersonalityMemory(listOf(p.energy, p.curiosity, p.warmth, p.confidence, p.expressiveness)))
    }
    /** Only long-term traits grow; the initial voice identity is never regenerated on updates. */
    fun evolve(identity: VpeIdentity, latest: VehicleSnapshot): VpeIdentity {
        val next = PersonaEngine.calculate(latest)
        return identity.copy(memory = identity.memory.update(next))
    }
}
