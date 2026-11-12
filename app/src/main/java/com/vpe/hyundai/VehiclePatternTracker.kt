package com.vpe.hyundai

import android.content.Context
import org.json.JSONObject
import kotlin.math.max

/**
 * Observed, per-vehicle aggregates, not guessed driving habits.
 * Sampling is invoked at most once per live polling tick. Only SDK values
 * received recently are counted. Aggregate counts are kept, never raw VIN.
 */
data class VehiclePatternSummary(
    val modeSamples: Int,
    val modeVarietyRatio: Double?,
    val cruiseSamples: Int,
    val cruiseUseRatio: Double?,
    val interactions: Int,
    val interactionRatio: Double?
) {
    val evidenceSummary: String get() =
        "운전 모드 관측 ${modeSamples}회 · 크루즈 관측 ${cruiseSamples}회 · 대화 ${interactions}회"
}

class VehiclePatternTracker(context: Context) {
    private val prefs = context.getSharedPreferences("vpe_observed_patterns_v1", Context.MODE_PRIVATE)
    private val minSamples = 4

    private fun read(key: String): JSONObject =
        runCatching { JSONObject(prefs.getString("stats_" + key, "{}") ?: "{}") }
            .getOrDefault(JSONObject())

    private fun write(key: String, value: JSONObject) {
        prefs.edit().putString("stats_" + key, value.toString()).apply()
    }

    /** Counts time-separated readings, never callback bursts or duplicate polling code paths. */
    fun sample(key: String, mode: String?, cruiseState: String?,
               modeFresh: Boolean, cruiseFresh: Boolean) {
        if (key.isBlank()) return
        val json = read(key)
        val modeName = mode?.trim()?.uppercase()?.takeIf {
            it.isNotBlank() && it != "UNKNOWN" && it != "UNAVAILABLE" && it.length <= 60
        }
        if (modeFresh && modeName != null) {
            val counts = json.optJSONObject("modeCounts") ?: JSONObject()
            val total = json.optInt("modeSamples", 0)
            if (total >= 120) {
                for (name in counts.keys().asSequence().toList()) {
                    counts.put(name, max(0, counts.optInt(name, 0) / 2))
                }
                json.put("modeSamples", counts.keys().asSequence().sumOf { counts.optInt(it,0) })
            }
            counts.put(modeName, counts.optInt(modeName, 0) + 1)
            json.put("modeCounts", counts)
            json.put("modeSamples", json.optInt("modeSamples", 0) + 1)
        }
        val active = if (cruiseFresh) cruiseActive(cruiseState) else null
        if (active != null) {
            val samples = json.optInt("cruiseSamples",0)
            if (samples >= 120) {
                json.put("cruiseSamples",samples/2)
                json.put("cruiseOn",json.optInt("cruiseOn",0)/2)
            }
            json.put("cruiseSamples",json.optInt("cruiseSamples",0)+1)
            if (active) json.put("cruiseOn",json.optInt("cruiseOn",0)+1)
        }
        if ((modeFresh && modeName != null) || active != null) write(key,json)
    }

    fun recordInteraction(key: String) {
        if (key.isBlank()) return
        val json = read(key)
        json.put("interactions", (json.optInt("interactions",0) + 1).coerceAtMost(10000))
        write(key,json)
    }

    fun summary(key: String): VehiclePatternSummary {
        val json = read(key)
        val modeCount = json.optInt("modeSamples",0)
        val modeCounts = json.optJSONObject("modeCounts") ?: JSONObject()
        val modeLargest = modeCounts.keys().asSequence()
            .map { modeCounts.optInt(it, 0) }.maxOrNull() ?: 0
        // Variety of observed driving-mode sessions, not GPS exploration.
        val modeRatio = if (modeCount >= minSamples)
            ((1.0 - modeLargest.toDouble() / modeCount) * 1.5).coerceIn(0.0,1.0)
        else null
        val cruiseCount = json.optInt("cruiseSamples",0)
        val cruiseRatio = if (cruiseCount >= minSamples)
            (json.optInt("cruiseOn",0).toDouble()/cruiseCount).coerceIn(0.0,1.0)
        else null
        val interactions = json.optInt("interactions",0)
        val interactionRatio = if (interactions >= 3)
            (interactions.toDouble()/12.0).coerceIn(0.0,1.0)
        else null
        return VehiclePatternSummary(modeCount,modeRatio,cruiseCount,
            cruiseRatio,interactions,interactionRatio)
    }

    companion object {
        /** Interpret only explicit states. Unknown SDK enums are not inventively mapped. */
        fun cruiseActive(raw: String?): Boolean? {
            val value=raw?.trim()?.uppercase() ?: return null
            return when {
                value.contains("INACTIVE") || value.contains("DISABLED") ||
                    value.contains("OFF") || value.contains("STANDBY") ||
                    value.contains("CANCEL") -> false
                value.contains("ACTIVE") || value.contains("ENGAGED") ||
                    value.contains("ENABLED") || value.contains("ON") ||
                    value.contains("RUNNING") -> true
                else -> null
            }
        }
    }
}
