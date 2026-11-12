package com.vpe.hyundai

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Calls Render over HTTPS. Neither the password nor OpenAI API key is stored in source. */
object VpeAiClient {
    private fun request(
        baseUrl: String,
        route: String,
        token: String? = null,
        body: JSONObject? = null
    ): JSONObject {
        val normalized = baseUrl.trim().trimEnd('/')
        val url = URL(normalized + route)
        require(url.protocol == "https" && url.host.isNotBlank()) {
            "서버 주소는 https:// 로 시작해야 해."
        }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = if (body == null) "GET" else "POST"
            connectTimeout = 10000
            readTimeout = 45000
            doOutput = body != null
            setRequestProperty("Accept", "application/json")
            if (body != null) setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!token.isNullOrEmpty()) setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            if (body != null) connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val parsed = runCatching { JSONObject(text) }.getOrNull()
            if (status !in 200..299) {
                val code = parsed?.optString("error").orEmpty().ifBlank { "HTTP $status" }
                throw IOException(when (code) {
                    "DATABASE_NOT_CONFIGURED" -> "VPE 데이터베이스 설정이 아직 완료되지 않았어."
                    "INVALID_LOGIN" -> "아이디 또는 비밀번호를 확인해 줘."
                    "USERNAME_EXISTS" -> "이미 사용 중인 아이디야."
                    "INVALID_CREDENTIALS_FORMAT" -> "아이디 2~24자(한글·영문·숫자·_), 비밀번호 8~128자로 입력해 줘."
                    "LOGIN_REQUIRED" -> "로그인 세션이 만료됐어. 다시 로그인해 줘."
                    "RATE_LIMITED" -> "요청이 연속으로 많아 잠시 제한됐어. 1분 뒤 다시 시도해 줘."
                    "OPENAI_NOT_CONFIGURED" -> "Render에 OpenAI API 키 등록이 필요해."
                    "OPENAI_RATE_LIMIT" -> "OpenAI API 한도/잔액이 부족하거나 요청이 많아. API 결제 설정을 확인해 줘."
                    "OPENAI_KEY_INVALID" -> "Render의 OpenAI API 키 인증에 실패했어. 등록한 키를 확인해 줘."
                    "OPENAI_ACCESS_DENIED" -> "OpenAI 프로젝트에서 이 모델에 접근할 권한이 없어."
                    "OPENAI_MODEL_UNAVAILABLE" -> "설정된 GPT 모델을 찾지 못했어."
                    "OPENAI_INVALID_REQUEST" -> "GPT API 요청 형식에 문제가 있어. 서버 코드를 확인해야 해."
                    "OPENAI_UPSTREAM_ERROR" -> "OpenAI 서버 응답 오류가 발생했어."
                    "EMPTY_OPENAI_RESPONSE" -> "GPT가 빈 답변을 보내서 다시 요청해야 해."
                    "REQUEST_FAILED" -> "서버 처리 오류가 발생했어."
                    else -> "서버 오류: $code"
                })
            }
            return parsed ?: throw IOException("서버가 올바른 JSON을 보내지 않았어.")
        } finally { connection.disconnect() }
    }

    fun login(baseUrl: String, username: String, password: String, register: Boolean): JSONObject =
        request(baseUrl, if (register) "/api/auth/register" else "/api/auth/login",
            body = JSONObject().put("username", username).put("password", password))

    fun logout(baseUrl: String, token: String) {
        request(baseUrl, "/api/auth/logout", token, JSONObject())
    }

    fun myVehicles(baseUrl: String, token: String): JSONArray =
        request(baseUrl, "/api/vehicles", token).optJSONArray("vehicles") ?: JSONArray()

    fun saveVehicle(
        baseUrl: String,
        token: String,
        vehicleKey: String,
        vehicleName: String,
        context: JSONObject
    ): JSONObject = request(baseUrl, "/api/vehicles", token,
        JSONObject().put("vehicleKey", vehicleKey)
            .put("vehicleName", vehicleName)
            .put("context", context))

    fun history(baseUrl: String, token: String, vehicleKey: String): JSONArray =
        request(baseUrl, "/api/messages?vehicleKey=" +
            URLEncoder.encode(vehicleKey, "UTF-8"), token)
            .optJSONArray("messages") ?: JSONArray()

    /** Two real GPT calls with same question/data but opposite style coefficients. */
    fun comparePersona(baseUrl: String, token: String, context: JSONObject): JSONObject =
        request(baseUrl, "/api/persona-compare", token,
            JSONObject().put("context", context))

    /** Returns an ephemeral Realtime credential; no permanent API key is stored on Android. */
    fun realtimeSecret(baseUrl: String, token: String, context: JSONObject): JSONObject =
        request(baseUrl, "/api/realtime/token", token,
            JSONObject().put("context", context))

    /** An authenticated vehicle-initiated turn (no fake user message). */
    fun proactiveAccount(
        baseUrl: String,
        token: String,
        vehicleKey: String,
        vehicleName: String,
        eventKind: String,
        context: JSONObject
    ): String {
        val result=request(baseUrl,"/api/proactive",token,
            JSONObject().put("vehicleKey",vehicleKey)
                .put("vehicleName",vehicleName)
                .put("eventKind",eventKind)
                .put("context",context))
        return result.optString("reply").takeIf { it.isNotBlank() }
            ?: throw IOException("능동형 GPT 응답이 비어 있어.")
    }

    fun askAccount(
        baseUrl: String,
        token: String,
        vehicleKey: String,
        vehicleName: String,
        message: String,
        context: JSONObject
    ): String {
        val answer = request(baseUrl, "/api/chat", token,
            JSONObject().put("vehicleKey", vehicleKey)
                .put("vehicleName", vehicleName)
                .put("message", message)
                .put("context", context))
        return answer.optString("reply").takeIf { it.isNotBlank() }
            ?: throw IOException("GPT 응답이 비어 있어.")
    }
}
