package com.vpe.hyundai

import kotlin.math.max
import kotlin.math.min

/** All inputs normalized and bounded; no random personality generation. */
data class VehicleSnapshot(
    val ageYears: Double,
    val odometerKm: Double,
    val fuelPercent: Double,
    val maintenanceScore: Double,
    val explorationRatio: Double,
    val longTripRatio: Double
)
data class Persona(
    val energy: Double,
    val curiosity: Double,
    val warmth: Double,
    val confidence: Double,
    val expressiveness: Double,
    val hunger: Double,
    val concern: Double,
    val speechRate: Float,
    val pitch: Float,
    val line: String
)
object PersonaEngine {
    private fun bound(x: Double) = x.coerceIn(0.0, 1.0)
    fun calculate(v: VehicleSnapshot): Persona {
        val age = bound(v.ageYears / 20.0)
        val distance = bound(v.odometerKm / 250000.0)
        val care = bound(v.maintenanceScore / 100.0)
        val explore = bound(v.explorationRatio)
        val travel = bound(v.longTripRatio)
        val fuel = bound(v.fuelPercent / 100.0)
        // Explicit, versioned weight matrix. Same snapshot always yields same traits.
        val energy = bound(.45 - .12 * age + .22 * travel + .13 * care + .12 * explore)
        val curiosity = bound(.25 + .48 * explore + .17 * travel - .10 * age)
        val warmth = bound(.30 + .50 * care + .10 * distance)
        val confidence = bound(.30 + .28 * distance + .25 * care + .12 * travel)
        val expression = bound(.20 + .38 * energy + .28 * curiosity + .14 * warmth)
        val hunger = bound((.25 - fuel) / .25)
        val concern = bound(.65 * (1.0 - care) + .35 * hunger)
        val intro = when {
            hunger > .6 -> if (energy > .55) "으아, 배고파! 주유소에 들르자!" else "으으… 배가 고프네. 주유소에 들러줄래?"
            care < .35 -> "요즘 점검이 좀 필요해. 나를 한번 살펴봐 줄래?"
            curiosity > .65 -> "오늘은 새로운 곳으로 달려볼까? 기대돼!"
            energy > .6 -> "좋아! 오늘도 신나게 출발해 보자!"
            else -> "오늘도 편안하게 함께 가자."
        }
        val line = intro + if (warmth > .7 && hunger <= .6) " 늘 챙겨줘서 고마워." else ""
        return Persona(energy, curiosity, warmth, confidence, expression, hunger, concern,
            (.85 + .30 * energy - .07 * hunger).toFloat().coerceIn(.75f, 1.2f),
            (.92 + .16 * expression - .05 * concern).toFloat().coerceIn(.8f, 1.15f), line)
    }
}

/** Persistent long-term traits use an EMA; transient mood is recalculated from live data. */
data class PersonalityMemory(val traits: List<Double>) {
    fun update(next: Persona, learningRate: Double = 0.05): PersonalityMemory {
        val alpha = learningRate.coerceIn(0.0, 1.0)
        val values = listOf(next.energy, next.curiosity, next.warmth, next.confidence, next.expressiveness)
        return PersonalityMemory(if (traits.size == 5) traits.zip(values) { old, current ->
            (old * (1 - alpha) + current * alpha).coerceIn(0.0, 1.0)
        } else values)
    }
}
