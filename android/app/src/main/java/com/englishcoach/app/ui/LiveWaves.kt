package com.englishcoach.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val AURORA = listOf(Color(0xFF7B5CFF), Color(0xFF3BC9FF), Color(0xFFFF5FA2), Color(0xFFB8F03A))
private val SILK = listOf(Color(0xFF6C4DFF), Color(0xFF3BC9FF), Color(0xFFFF5FA2), Color(0xFFFFC83D), Color(0xFF6C4DFF))

/**
 * Premium animated background for the practice screen: drifting aurora light at the bottom and a bundle
 * of thin silk ribbons behind the mic. [voice] (0..1) is the learner's loudness; [talking] is the coach speaking.
 */
@Composable
fun LiveWaves(voice: Float, talking: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "waves")
    val time by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "time")
    val beat by t.animateFloat(0f, 1f, infiniteRepeatable(tween(460, easing = LinearEasing)), label = "beat")
    val coach = if (talking) 0.3f + 0.2f * sin(beat * 2 * PI).toFloat() else 0f
    val energy by animateFloatAsState(0.15f + maxOf(voice, coach) * 0.85f,
        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "energy")

    Canvas(modifier) {
        val w = size.width
        val h = size.height

        // 1. aurora: big soft colour clouds drifting in the lower half
        AURORA.forEachIndexed { i, c ->
            val a = time + i * 1.6f
            val center = Offset(w * (0.5f + 0.38f * cos(a * (0.6f + i * 0.15f))), h * (0.80f + 0.07f * sin(a * 0.9f)))
            val r = w * (0.55f + 0.12f * (i % 2) + 0.25f * energy)
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.22f + 0.18f * energy), Color.Transparent), center, r), r, center)
        }

        // 2. silk: a bundle of thin ribbons sharing one motion, each slightly offset, tapered at the edges
        val base = h * 0.86f
        val amp = h * (0.018f + 0.075f * energy)
        val brush = Brush.horizontalGradient(SILK.mapIndexed { i, c -> if (i == 0 || i == SILK.lastIndex) c.copy(alpha = 0f) else c })
        for (k in 0 until 5) {
            val off = (k - 2) * 0.35f
            val path = Path()
            var x = 0f
            val step = w / 72f
            while (x <= w + step) {
                val u = x / w
                val taper = sin(u * PI).toFloat()
                val y = base + off * 10 * density - amp * taper * (
                    sin(u * 2.2 * PI + time * 1.3 + off) * 0.7 +
                        sin(u * 5.0 * PI - time * 2.1 + off * 2) * 0.3 * (0.4 + energy)
                    ).toFloat()
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += step
            }
            if (k == 2) drawPath(path, brush, alpha = 0.16f + 0.2f * energy, style = Stroke((14f + 22f * energy) * density, cap = StrokeCap.Round))
            drawPath(path, brush, alpha = (0.55f + 0.35f * energy - abs(off) * 0.25f).coerceIn(0f, 1f),
                style = Stroke((1.2f + 1.4f * energy) * density, cap = StrokeCap.Round))
        }
    }
}
