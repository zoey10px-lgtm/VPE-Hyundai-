package com.vpe.hyundai

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Pleos/Android-native realtime voice: microphone PCM16 24kHz -> ephemeral
 * OpenAI Realtime WebSocket -> AudioTrack PCM16 24kHz. No static API key in APK.
 * The session is explicitly started/stopped by the home-page VPE orb.
 */
class VpeRealtimeVoice(private val listener: Listener) {
    interface Listener {
        fun onStatus(status: String)
        fun onUserText(text: String)
        fun onVehicleText(text: String)
        fun onEnded(reason: String)
        fun onDiagnostic(event: String)
    }

    private val http = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).build()
    // Realtime sends audio faster than playback. Keep each generated PCM chunk.
    // A completed response is small relative to emulator memory; no silent drops.
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private val interruptEpoch = AtomicInteger(0)
    private val transcript = StringBuilder()
    private var stablePersonaInstructions = ""
    // In WebSocket mode playback lives on Android, so interruptions must
    // truncate audio the user did not hear from the model's conversation.
    private var outputItemId: String? = null
    private var outputContentIndex = 0
    private var outputFirstFrame = 0L
    private var outputReceivedBytes = 0L

    private fun playedFrames(): Long =
        speaker?.playbackHeadPosition?.toLong()?.and(0xffffffffL) ?: 0L

    private fun truncateInterruptedSpeech() {
        val id=outputItemId ?: return
        val heardFrames=(playedFrames()-outputFirstFrame).coerceAtLeast(0L)
        val heardMs=(heardFrames/24L).coerceAtMost(outputReceivedBytes/48L)
        socket?.send(JSONObject().put("type","conversation.item.truncate")
            .put("item_id",id).put("content_index",outputContentIndex)
            .put("audio_end_ms",heardMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            .toString())
        outputItemId=null
        outputReceivedBytes=0L
    }
    @Volatile var active = false
        private set
    @Volatile private var ready = false
    @Volatile private var socket: WebSocket? = null
    @Volatile private var microphone: AudioRecord? = null
    @Volatile private var speaker: AudioTrack? = null
    private var microphoneThread: Thread? = null
    private var speakerThread: Thread? = null

    fun start(ephemeralToken: String, model: String, personaInstructions: String) {
        stablePersonaInstructions = personaInstructions.take(19_000)
        require(ephemeralToken.startsWith("ek_")) { "유효한 Realtime 임시 토큰이 없어." }
        require(model in setOf("gpt-realtime-2.1-mini", "gpt-realtime-2.1")) {
            "허용되지 않은 Realtime 모델이야."
        }
        if (active) return
        active = true
        listener.onStatus("Realtime 음성 서버 연결 중…")
        val request = Request.Builder()
            .url("wss://api.openai.com/v1/realtime?model=" + model)
            .header("Authorization", "Bearer " + ephemeralToken)
            .build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (active) listener.onStatus("Realtime 연결됨 · 음성 세션 준비 중…")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (active) handleEvent(text)
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = Unit
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (active) {
                    val details = response?.code?.toString()
                        ?: t.message?.take(100) ?: "연결 실패"
                    listener.onStatus("Realtime 연결 오류: " + details)
                    stop()
                    listener.onEnded("Realtime 연결 오류: " + details)
                }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (active) {
                    stop()
                    listener.onEnded("음성 서버 연결 종료 (" + code + ")")
                }
            }
        })
    }

    private fun handleEvent(text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            "session.created", "session.updated" -> {
                if (!ready) {
                    ready = true
                    runCatching { initializeAudio() }.onFailure { error ->
                        val reason = "오디오 장치 시작 실패: " +
                            (error.message ?: error.javaClass.simpleName)
                        stop()
                        listener.onEnded(reason)
                    }
                }
            }
            "input_audio_buffer.speech_started" -> {
                interruptEpoch.incrementAndGet()
                if (outputItemId != null || audioQueue.isNotEmpty()) {
                    listener.onDiagnostic("AUDIO_INTERRUPTED: VAD_STARTED; queued=" + audioQueue.size)
                }
                truncateInterruptedSpeech()
                audioQueue.clear()
                runCatching {
                    speaker?.pause()
                    speaker?.flush()
                    speaker?.play()
                }
                listener.onStatus("목소리 듣는 중…")
            }
            "input_audio_buffer.speech_stopped" -> listener.onStatus("대답을 생각 중…")
            "conversation.item.input_audio_transcription.delta" -> {
                val partial=json.optString("delta").trim()
                if (partial.isNotEmpty()) listener.onStatus("듣는 중: " + partial.take(90))
            }
            "conversation.item.input_audio_transcription.completed" -> {
                json.optString("transcript").trim().takeIf { it.isNotEmpty() }
                    ?.let { listener.onUserText(it) }
            }
            "response.output_audio.delta" -> {
                val delta = json.optString("delta")
                if (delta.isNotEmpty()) {
                    runCatching { Base64.decode(delta, Base64.DEFAULT) }
                        .getOrNull()?.let { pcm ->
                            if (pcm.isNotEmpty()) {
                                val item=json.optString("item_id")
                                if (item.isNotBlank() && outputItemId!=item) {
                                    outputItemId=item
                                    outputContentIndex=json.optInt("content_index",0)
                                    outputFirstFrame=playedFrames()
                                    outputReceivedBytes=0L
                                }
                                outputReceivedBytes+=pcm.size
                                audioQueue.offer(pcm)
                            }
                        }
                    listener.onStatus("VPE가 말하는 중…")
                }
            }
            "response.output_audio_transcript.delta" -> {
                transcript.append(json.optString("delta"))
            }
            "response.output_audio_transcript.done" -> {
                val finalText = json.optString("transcript")
                    .ifBlank { transcript.toString() }.trim()
                transcript.setLength(0)
                if (finalText.isNotEmpty()) listener.onVehicleText(finalText)
            }
            "response.done" -> {
                val response=json.optJSONObject("response")
                val status=response?.optString("status").orEmpty()
                val reason=response?.optJSONObject("status_details")?.toString().orEmpty()
                listener.onDiagnostic("REALTIME_RESPONSE_DONE: status=" + status +
                    if (reason.isBlank()) "" else " details=" + reason.take(180))
                if (status == "incomplete" || status == "failed")
                    listener.onStatus("VPE 답변 미완성 · " +
                        if (reason.isBlank()) status else reason.take(95))
                else listener.onStatus("마이크 켜짐 · 편하게 말해 줘")
            }
            "response.output_audio.done" -> {
                listener.onDiagnostic("AUDIO_GENERATION_FINISHED: remaining=" + audioQueue.size)
            }
            "conversation.item.truncated" -> {
                listener.onDiagnostic("AUDIO_TRUNCATED_BY_INTERRUPT")
            }
            "error" -> {
                val reason = json.optJSONObject("error")?.optString("message")
                    ?.take(160).orEmpty().ifBlank { "Realtime 요청 오류" }
                listener.onStatus("음성 API 오류: " + reason)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeAudio() {
        val outputMin = AudioTrack.getMinBufferSize(
            24_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        require(outputMin > 0) { "에뮬레이터 오디오 출력 장치를 찾지 못했어." }
        val output = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(24_000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(max(outputMin * 2, 19_200))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        require(output.state == AudioTrack.STATE_INITIALIZED) { "PCM 출력 초기화 실패" }
        speaker = output
        output.play()
        speakerThread = Thread {
            try {
                while (active && !Thread.currentThread().isInterrupted) {
                    val pcm = audioQueue.poll(250, TimeUnit.MILLISECONDS) ?: continue
                    val epoch = interruptEpoch.get()
                    var offset = 0
                    // Android may accept only part of one PCM chunk. Never
                    // mistake a partial write for a fully played response.
                    while (active && offset < pcm.size && epoch == interruptEpoch.get()) {
                        val size = output.write(pcm, offset, pcm.size - offset,
                            AudioTrack.WRITE_BLOCKING)
                        if (size <= 0) {
                            if (active && epoch == interruptEpoch.get())
                                listener.onDiagnostic("AUDIO_TRACK_WRITE_ERROR: " + size)
                            break
                        }
                        offset += size
                    }
                }
            } catch (_: InterruptedException) {
            } catch (error: Exception) {
                if (active) listener.onDiagnostic("AUDIO_TRACK_ERROR: " +
                    (error.message ?: error.javaClass.simpleName).take(100))
            } finally {
                runCatching { output.stop() }
                output.release()
                if (speaker === output) speaker = null
            }
        }.also { it.name = "vpe-realtime-speaker"; it.start() }

        val inputMin = AudioRecord.getMinBufferSize(
            24_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        require(inputMin > 0) { "에뮬레이터 마이크가 24kHz 입력을 지원하지 않아." }
        fun recorder(source: Int): AudioRecord = AudioRecord(
            source, 24_000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            max(inputMin * 2, 9_600))
        var input=recorder(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        if (input.state != AudioRecord.STATE_INITIALIZED) {
            input.release()
            // Automotive emulator images sometimes expose MIC but not
            // VOICE_COMMUNICATION; try the plain input device as fallback.
            input=recorder(MediaRecorder.AudioSource.MIC)
        }
        require(input.state == AudioRecord.STATE_INITIALIZED) { "Pleos 마이크 초기화 실패" }
        microphone = input
        microphoneThread = Thread {
            val echo = runCatching {
                if (AcousticEchoCanceler.isAvailable())
                    AcousticEchoCanceler.create(input.audioSessionId) else null
            }.getOrNull()
            runCatching { echo?.enabled = true }
            try {
                input.startRecording()
                listener.onStatus("마이크 켜짐 · 편하게 말해 줘")
                val chunk = ByteArray(4_800) // 100 ms of 24kHz mono signed PCM16
                while (active && ready) {
                    val size = input.read(chunk, 0, chunk.size)
                    if (size > 0 && size % 2 == 0 && (socket?.queueSize() ?: 0L) < 512_000L) {
                        val audio = Base64.encodeToString(chunk, 0, size, Base64.NO_WRAP)
                        socket?.send(JSONObject()
                            .put("type", "input_audio_buffer.append")
                            .put("audio", audio).toString())
                    } else if (size < 0) {
                        throw IllegalStateException("마이크 읽기 오류: " + size)
                    }
                }
            } catch (e: Exception) {
                if (active) {
                    val error = "마이크 오류: " + (e.message ?: "장치 읽기 실패")
                    stop()
                    listener.onEnded(error)
                }
            } finally {
                runCatching { echo?.release() }
                runCatching { input.stop() }
                input.release()
                if (microphone === input) microphone = null
            }
        }.also { it.name = "vpe-realtime-microphone"; it.start() }
    }

    /** Update only the dynamic, observed persona; the server supplied initial safety rules. */
    fun updatePersona(context: JSONObject) {
        if (!active || !ready) return
        // Preserve the complete seven-trait T/F/E/I persona contract from
        // Render. Previous versions replaced it with a generic assistant prompt.
        val newGuidance = stablePersonaInstructions +
            "\n\n[최신 HPE 관측 상태: 이 수치가 더 새로운 데이터야]\n" +
            context.toString().take(9_000) +
            "\n처음 지정한 말투·T/F 반응 방식·음성 표현 스타일을 그대로 유지해."
        socket?.send(JSONObject()
            .put("type", "session.update")
            .put("session", JSONObject().put("type", "realtime")
                .put("instructions", newGuidance)).toString())
    }

    /** The car can speak from its own sensor event, without a forged user turn. */
    fun announce(eventText: String) {
        if (!active || !ready) return
        socket?.send(JSONObject()
            .put("type", "response.create")
            .put("response", JSONObject()
                .put("output_modalities", JSONArray().put("audio"))
                .put("instructions", "차량 실시간 이벤트야. VPE 차량 자신이 먼저 한두 문장으로 " +
                    "현재 성격에 맞게 자연스럽게 말해. 이벤트: " + eventText.take(350)))
            .toString())
    }

    fun stop() {
        if (!active && !ready) return
        active = false
        ready = false
        socket?.close(1000, "VPE voice off")
        socket = null
        audioQueue.clear()
        outputItemId=null
        outputReceivedBytes=0L
        interruptEpoch.incrementAndGet()
        runCatching { microphone?.stop() }
        speakerThread?.interrupt()
    }

    fun release() {
        stop()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }
}
