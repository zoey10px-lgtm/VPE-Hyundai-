package com.vpe.hyundai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small local prototype store; keeps baseline, voice blueprint and evolving memory. */
class IdentityStore(context: Context) {
    private val prefs = context.getSharedPreferences("vpe_identity_v1", Context.MODE_PRIVATE)
    fun save(identity: VpeIdentity) {
        val v = identity.initial
        val b = identity.voiceBlueprint
        val obj = JSONObject()
            .put("key", identity.vehicleKey).put("version", identity.engineVersion)
            .put("vehicle", JSONObject().put("age",v.ageYears).put("km",v.odometerKm)
                .put("fuel",v.fuelPercent).put("care",v.maintenanceScore)
                .put("explore",v.explorationRatio).put("travel",v.longTripRatio))
            .put("voice", JSONObject().put("gender",b.genderExpression).put("age",b.perceivedAge)
                .put("pitch",b.pitch).put("resonance",b.resonance)
                .put("softness",b.softness).put("rate",b.speechRate))
            .put("memory", JSONArray(identity.memory.traits))
        prefs.edit().putString("identity",obj.toString()).apply()
    }
    fun load(): VpeIdentity? = try {
        val raw = prefs.getString("identity",null) ?: return null
        val o = JSONObject(raw)
        if (o.getInt("version") != IdentityEngine.VERSION) return null
        val v = o.getJSONObject("vehicle")
        val snap = VehicleSnapshot(v.getDouble("age"),v.getDouble("km"),
            v.getDouble("fuel"),v.getDouble("care"),v.getDouble("explore"),v.getDouble("travel"))
        val b = o.getJSONObject("voice")
        val voice = VoiceBlueprint(b.getDouble("gender"),b.getInt("age"),b.getDouble("pitch"),
            b.getDouble("resonance"),b.getDouble("softness"),b.getDouble("rate"))
        val arr = o.getJSONArray("memory")
        val memory = PersonalityMemory((0 until arr.length()).map { arr.getDouble(it) })
        VpeIdentity(o.getString("key"),o.getInt("version"),snap,PersonaEngine.calculate(snap),voice,memory)
    } catch (_: Exception) { null }
    fun clear() { prefs.edit().remove("identity").apply() }
}
