package com.vpe.hyundai

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Requests GPT through the user's Render HTTPS backend. Never stores an OpenAI API key. */
object VpeAiClient {
    fun ask(
        baseUrl: String,
        accessToken: String,
        message: String,
        context: JSONObject,
        history: JSONArray
    ): String {
        val normalized = baseUrl.trim().trimEnd('/')
        val endpoint = URL("$normalized/api/chat")
        require(endpoint.protocol == "https" && endpoint.host.isNotBlank()) {
            "Render 주소는 https:// 로 시작해야 해."
        }
        require(accessToken.isNotBlank()) { "VPE 접속 토큰을 등록해 줘." }

        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        try {
            val payload = JSONObject()
                .put("message", message)
                .put("context", context)
                .put("history", history)
                .toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(responseText) }.getOrNull()
            if (status !in 200..299) {
                val code = response?.optString("error").orEmpty().ifBlank { "HTTP $status" }
                throw IOException("GPT 서버 오류: $code")
            }
            return response?.optString("reply")?.takeIf { it.isNotBlank() }
                ?: throw IOException("GPT 서버 응답에 답변이 없어.")
        } finally {
            connection.disconnect()
        }
    }
}
