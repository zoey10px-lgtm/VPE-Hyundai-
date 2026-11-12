package com.vpe.hyundai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Persist completed Realtime transcripts locally first, then synchronize to the
 * same account/vehicle's PostgreSQL chat history. Failed uploads survive relaunch.
 * A stable eventId makes retries idempotent on the backend.
 * Session bearer tokens are NEVER written to SharedPreferences.
 */
class VpeVoiceHistorySync(context: Context) {
    private val prefs = context.getSharedPreferences("vpe_voice_pending_v1", Context.MODE_PRIVATE)
    private val io = Executors.newSingleThreadExecutor()
    private fun storageKey(scope: String) = "pending_" + scope

    @Synchronized
    private fun read(scope: String): JSONArray =
        runCatching { JSONArray(prefs.getString(storageKey(scope), "[]")) }
            .getOrDefault(JSONArray())

    @Synchronized
    fun enqueue(
        scope: String, key: String, vehicleName: String,
        context: JSONObject, role: String, content: String
    ) {
        if (scope.isBlank() || key.isBlank() || role !in setOf("user", "assistant")
            || content.isBlank()) return
        val pending=read(scope)
        pending.put(JSONObject()
            .put("eventId", UUID.randomUUID().toString().replace("-", ""))
            .put("vehicleKey", key).put("vehicleName", vehicleName)
            .put("role", role).put("content", content.take(2000))
            .put("context", JSONObject(context.toString())))
        prefs.edit().putString(storageKey(scope), pending.toString()).commit()
    }

    @Synchronized
    private fun first(scope: String): JSONObject? = read(scope).optJSONObject(0)

    @Synchronized
    private fun remove(scope: String, eventId: String) {
        val array=read(scope)
        val result=JSONArray()
        for (i in 0 until array.length()) {
            val item=array.optJSONObject(i) ?: continue
            if (item.optString("eventId") != eventId) result.put(item)
        }
        prefs.edit().putString(storageKey(scope), result.toString()).commit()
    }

    fun pendingCount(scope: String): Int = synchronized(this) { read(scope).length() }

    fun flush(scope: String, baseUrl: String, authToken: String, status: (String) -> Unit) {
        if (scope.isBlank() || authToken.isBlank()) return
        io.execute {
            var count=0
            while (!Thread.currentThread().isInterrupted) {
                val turn=first(scope) ?: break
                val eventId=turn.optString("eventId")
                try {
                    val saved=VpeAiClient.saveRealtimeTurn(
                        baseUrl,authToken,turn.getString("vehicleKey"),
                        turn.optString("vehicleName","내 차량"),
                        turn.getJSONObject("context"),
                        turn.getString("role"),turn.getString("content"),eventId)
                    if (!saved) break
                    remove(scope,eventId)
                    count++
                } catch (e: Exception) {
                    status("음성 대화 로컬 저장됨 · 서버 동기화 대기 (" +
                        (e.message ?: "연결 오류").take(60) + ")")
                    break
                }
            }
            if (count > 0) status("음성 대화 " + count + "개 서버 저장 완료")
        }
    }

    fun release() { io.shutdown() }
}
