package com.vpe.hyundai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Timestamp only fresh SDK patches. Pending events survive process/network loss. */
class CharacterObservations(context: Context) {
    private val prefs = context.getSharedPreferences("character_events_v1", Context.MODE_PRIVATE)
    @Synchronized fun append(scope: String, patch: VehicleReadings, source: String) {
        val at = System.currentTimeMillis()
        val events = load(scope)
        val values = mapOf("odometerKm" to patch.odometerKm, "energyPercent" to patch.energyPercent(),
            "actualCabinC" to patch.cabinTemperatureC, "targetCabinC" to patch.targetTemperatureC,
            "parked" to patch.parked, "passenger" to patch.passenger, "hvacAuto" to patch.hvacAuto, "drivingMode" to patch.drivingMode,
            "safetyWarning" to patch.safetyWarning, "cruiseControlState" to patch.cruiseControlState,
            "fanSpeedLevel" to patch.fanSpeedLevel)
        values.forEach { (signal, value) -> if (value != null) events.put(JSONObject()
            .put("id", UUID.randomUUID().toString()).put("signal", signal).put("value", value)
            .put("at", at).put("source", source)) }
        val bounded = JSONArray()
        for (i in maxOf(0, events.length()-4000) until events.length()) bounded.put(events.get(i))
        prefs.edit().putString(scope, bounded.toString()).apply()
    }
    @Synchronized fun batch(scope: String): JSONArray {
        val events = load(scope); val out = JSONArray(); val cutoff = System.currentTimeMillis()-6*86400000L
        for (i in 0 until events.length()) if (events.getJSONObject(i).optLong("at") >= cutoff && out.length()<200) out.put(events.get(i))
        return out
    }
    @Synchronized fun acknowledge(scope: String, sent: JSONArray) {
        val ids = (0 until sent.length()).map { sent.getJSONObject(it).getString("id") }.toSet()
        val rest=JSONArray(); val events=load(scope)
        for (i in 0 until events.length()) if (events.getJSONObject(i).optString("id") !in ids) rest.put(events.get(i))
        prefs.edit().putString(scope, rest.toString()).apply()
    }
    private fun load(scope: String) = runCatching { JSONArray(prefs.getString(scope,"[]")) }.getOrDefault(JSONArray())
}
