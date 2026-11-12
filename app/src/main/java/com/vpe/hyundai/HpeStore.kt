package com.vpe.hyundai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent HPE identity. Avoids regenerating the fictional MBTI seed on
 * every sensor callback. Stores no raw VIN or location trace.
 */
class HpeStore(context: Context) {
    private val prefs = context.getSharedPreferences("vpe_hpe_v1", Context.MODE_PRIVATE)
    private fun arr(values: List<Double>) = JSONArray().also { a -> values.forEach { a.put(it) } }
    private fun values(a: JSONArray) = (0 until a.length()).map { a.getDouble(it) }
    fun save(state: HpeState) {
        val m = state.memory
        val s = state.lastSignals
        val json = JSONObject()
            .put("version", HumanPersonalityEngine.VERSION)
            .put("key", state.key)
            .put("axes", arr(state.core.axes))
            .put("traits", arr(state.core.traits))
            .put("confidence", state.core.confidence)
            .put("firstKm", m.firstKm)
            .put("lastKm", m.lastKm)
            .put("sharedKm", m.sharedKm)
            .put("events", m.meaningfulEvents)
            .put("explore", m.lastExploration)
            .put("regular", m.lastRegularity)
            .put("social", m.lastInteraction)
            .put("modeVariety", m.lastModeVariety)
            .put("cruiseUsage", m.lastCruiseUsage)
            .put("highlights", JSONArray().also { a -> m.highlights.forEach { a.put(it) } })
            .put("energy", s.energyPercent)
            .put("modeVarietySignal", s.modeVarietyRatio)
            .put("cruiseUsageSignal", s.cruiseUseRatio)
            .put("modelAgeYears", s.modelAgeYears)
            .put("cabin", s.actualCabinC)
            .put("target", s.targetCabinC)
            .put("hvac", s.hvacOn)
            .put("charging", s.charging)
            .put("parked", s.parked)
            .put("warning", s.safetyWarning)
        prefs.edit().putString("state_" + state.key, json.toString()).apply()
    }

    fun load(key: String): HpeState? {
        return try {
        val raw = prefs.getString("state_" + key, null)
            ?: prefs.getString("state", null) ?: return null
        val json = JSONObject(raw)
        if (json.optInt("version") !in 2..HumanPersonalityEngine.VERSION ||
            json.optString("key") != key) return null
        fun num(name: String) = if (json.isNull(name)) null else json.optDouble(name).takeIf { it.isFinite() }
        fun boolean(name: String): Boolean? = if (json.isNull(name)) null else json.optBoolean(name)
        val core = HpeCore(values(json.getJSONArray("axes")),
            values(json.getJSONArray("traits")), json.optDouble("confidence"))
        if (core.axes.size != 4 || core.traits.size != 7) return null
        val m = HpeMemory(
            firstKm = num("firstKm"), lastKm = num("lastKm"),
            sharedKm = json.optDouble("sharedKm",0.0),
            meaningfulEvents = json.optInt("events"),
            lastExploration = num("explore"),
            lastRegularity = num("regular"),
            lastInteraction = num("social"),
            lastModeVariety = num("modeVariety"),
            lastCruiseUsage = num("cruiseUsage"),
            highlights = json.optJSONArray("highlights")?.let { a ->
                (0 until a.length()).map { a.getString(it) }
            } ?: emptyList()
        )
        val s = HpeSignals(key, m.lastKm, num("energy"), num("cabin"),
            num("target"), boolean("hvac"), boolean("charging"),
            boolean("parked"), boolean("warning"), m.lastExploration,
            m.lastRegularity, m.lastInteraction, modelAgeYears = num("modelAgeYears"),
            modeVarietyRatio = num("modeVarietySignal") ?: m.lastModeVariety,
            cruiseUseRatio = num("cruiseUsageSignal") ?: m.lastCruiseUsage)
        val appraisal = HumanPersonalityEngine.appraise(s)
        val affect = HumanPersonalityEngine.affect(appraisal,s)
        HpeState(key, core, m, affect,
            HumanPersonalityEngine.express(core,affect,s), emptyList(),s)
        } catch (_: Exception) { null }
    }
    fun clear() { prefs.edit().clear().apply() }

}
