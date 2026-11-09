package com.vpe.hyundai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Animated VPE identity visualizer. It animates every frame, including idle mode.
 * This is an ambient animation, not microphone level analysis or synthesized audio.
 */
class VpeWaveView(context: Context) : View(context) {
    enum class Mode { IDLE, CONNECTING, READY, ERROR }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var animator: ValueAnimator? = null
    var mode: Mode = Mode.IDLE
        set(value) {
            field = value
            invalidate()
        }
    /** Affect-driven ambient motion, not audio playback. */
    var affectIntensity: Float = 0.45f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    var valence: Float = 0f
        set(value) {
            field = value.coerceIn(-1f, 1f)
            invalidate()
        }
    private val density = resources.displayMetrics.density

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (228 * density).toInt()
        setMeasuredDimension(
            resolveSize((296 * density).toInt(), widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width * 0.32f, height * 0.41f)
        val colorOne = when (mode) {
            Mode.ERROR -> Color.rgb(254, 167, 154)
            Mode.READY -> when {
                valence < -.20f -> Color.rgb(255, 185, 159)
                valence > .20f -> Color.rgb(105, 226, 194)
                else -> Color.rgb(161, 191, 249)
            }
            else -> Color.rgb(131, 172, 255)
        }
        val colorTwo = Color.rgb(186, 142, 247)
        fill.shader = RadialGradient(
            cx - radius * 0.26f, cy - radius * 0.30f, radius * 1.7f,
            intArrayOf(Color.rgb(255, 255, 255), colorOne, colorTwo, Color.rgb(253, 250, 255)),
            floatArrayOf(0f, 0.27f, 0.67f, 1f),
            Shader.TileMode.CLAMP
        )
        fill.alpha = 215
        canvas.drawCircle(cx, cy, radius * (0.91f + 0.025f * sin(phase.toDouble()).toFloat()), fill)
        fill.shader = null
        fill.alpha = 255

        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 1.5f * density
        stroke.color = Color.argb(72, 105, 130, 224)
        canvas.drawCircle(cx, cy, radius * 1.10f, stroke)
        stroke.color = Color.argb(35, 105, 130, 224)
        canvas.drawCircle(cx, cy, radius * 1.29f, stroke)
        stroke.style = Paint.Style.FILL

        val count = 27
        val barWidth = maxOf(3f * density, minOf(width * 0.013f, 6f * density))
        val spread = width * 0.78f
        val pitch = spread / (count - 1)
        stroke.shader = LinearGradient(
            cx - spread / 2, cy, cx + spread / 2, cy,
            intArrayOf(Color.rgb(65, 164, 231), Color.rgb(111, 94, 220), Color.rgb(61, 201, 182)),
            null, Shader.TileMode.CLAMP
        )
        val strength = when (mode) {
            Mode.CONNECTING -> 0.92f
            Mode.READY -> 0.33f + 0.55f * affectIntensity
            Mode.ERROR -> 0.19f
            Mode.IDLE -> 0.48f
        }
        val tempo = 0.75f + 0.90f * affectIntensity
        for (index in 0 until count) {
            val center = (index - (count - 1) / 2f) / (count / 2f)
            val envelope = 0.20f + 0.80f * (1f - abs(center) * 0.82f)
            val a = sin(phase * 1.85 * tempo + index * 0.60).toFloat()
            val b = sin(phase * 0.91 * tempo - index * 0.38 + PI / 2).toFloat()
            val motion = (0.35f + 0.30f * a + 0.35f * b).coerceIn(0.12f, 1f)
            val height = (11f + 77f * strength * envelope * motion) * density
            val x = cx - spread / 2 + index * pitch
            canvas.drawRoundRect(
                x - barWidth / 2, cy - height / 2,
                x + barWidth / 2, cy + height / 2, barWidth / 2, barWidth / 2, stroke
            )
        }
        stroke.shader = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
            duration = 1950L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
