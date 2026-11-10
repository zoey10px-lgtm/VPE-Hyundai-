package com.vpe.hyundai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Seven-axis radar/spider plot for HPE's seven original long-term traits.
 * Purely renders computed algorithm values; no sample or hard-coded metrics.
 */
class VpeRadarChartView(context: Context, values: List<Double>) : View(context) {
    private val names = listOf("활동성", "호기심", "친밀감", "자신감", "신중함", "장난기", "표현력")
    private val traits = values.take(7).map { it.coerceIn(0.0, 1.0).toFloat() }
    private val density = resources.displayMetrics.density
    private val scaled = resources.displayMetrics.scaledDensity
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(226, 229, 244)
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val spoke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(230, 233, 244)
        strokeWidth = density
    }
    private val area = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(74, 102, 93, 230)
        style = Paint.Style.FILL
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(105, 90, 226)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeJoin = Paint.Join.ROUND
    }
    private val point = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(105, 90, 226)
        style = Paint.Style.FILL
    }
    private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(57, 65, 94)
        textAlign = Paint.Align.CENTER
        textSize = 12.5f * scaled
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    init {
        require(traits.size == names.size) { "All 7 HPE traits must be present" }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "차량 성향 레이더 차트. " +
            names.indices.joinToString(", ") { i ->
                names[i] + " " + (traits[i] * 100).toInt() + "점"
            }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize((340 * density).toInt(), widthMeasureSpec),
            resolveSize((350 * density).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = traits.size
        val cx = width / 2f
        val cy = height * 0.495f
        val r = min(width * 0.30f, height * 0.305f)
        fun angle(index: Int): Double = -PI / 2.0 + index * (2.0 * PI / count)
        fun vertex(index: Int, radius: Float): Pair<Float, Float> {
            val a = angle(index)
            return Pair(cx + cos(a).toFloat() * radius, cy + sin(a).toFloat() * radius)
        }
        fun polygon(scale: Float): Path {
            val result = Path()
            for (i in 0 until count) {
                val (x, y) = vertex(i, r * scale)
                if (i == 0) result.moveTo(x, y) else result.lineTo(x, y)
            }
            result.close()
            return result
        }

        for (level in 1..4) {
            grid.color = if (level == 4) Color.rgb(205, 210, 237)
                else Color.rgb(228, 231, 243)
            canvas.drawPath(polygon(level / 4f), grid)
        }
        for (i in 0 until count) {
            val (x, y) = vertex(i, r)
            canvas.drawLine(cx, cy, x, y, spoke)
        }

        val dataPath = Path()
        traits.forEachIndexed { index, value ->
            val (x, y) = vertex(index, r * value)
            if (index == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
        }
        dataPath.close()
        canvas.drawPath(dataPath, area)
        canvas.drawPath(dataPath, outline)
        traits.forEachIndexed { index, value ->
            val (x, y) = vertex(index, r * value)
            canvas.drawCircle(x, y, 3.8f * density, point)
        }

        for (i in 0 until count) {
            val a = angle(i)
            // Labels sit outside the grid, not on top of the data area.
            val labelRadius = r + 26f * density
            var x = cx + cos(a).toFloat() * labelRadius
            val y = cy + sin(a).toFloat() * labelRadius
            val half = words.measureText(names[i]) / 2f
            x = x.coerceIn(half + 4f * density, width - half - 4f * density)
            canvas.drawText(names[i], x, y + 5f * density, words)
        }
    }
}
