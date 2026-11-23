package com.englishcoach.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap

enum class Mood { Idle, Talking, Listening, Thinking, Happy }

/** The coach character: a soft 3D-looking blob that blinks, floats, talks, tilts and jumps. */
@Composable
fun Buddy(color: Color, mood: Mood, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "buddy")
    val float by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    val blink by t.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 4200
        1f at 3800; 0.08f at 3900; 1f at 4000
    }), label = "blink")
    val talk by t.animateFloat(0f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 520
        0f at 0; 1f at 110; 0.35f at 230; 0.85f at 360; 0f at 520
    }), label = "talk")
    val jump by t.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "jump")
    val tilt by t.animateFloat(-6f, -2f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "tilt")

    val hi = lerp(color, Color.White, 0.55f)
    val lo = lerp(color, Color.Black, 0.35f)
    val ink = Color(0xFF1A1530)

    Canvas(modifier) {
        val s = size.minDimension
        val bob = when (mood) {
            Mood.Happy -> -s * 0.14f * kotlin.math.sin(jump * Math.PI).toFloat()
            else -> -s * 0.04f * float
        }
        // ground shadow
        drawOval(Color(0x332A1F66), Offset(s * 0.24f, s * 0.92f), Size(s * 0.52f, s * 0.06f))

        translate(top = bob) {
            rotate(if (mood == Mood.Listening) tilt else 0f, Offset(s / 2, s * 0.9f)) {
                // ears
                for (x in listOf(0.14f, 0.64f)) {
                    drawOval(Brush.radialGradient(listOf(hi, color, lo), Offset(s * (x + 0.08f), s * 0.06f), s * 0.2f),
                        Offset(s * x, s * 0.02f), Size(s * 0.22f, s * 0.26f))
                }
                // head
                drawRoundRect(
                    Brush.radialGradient(listOf(hi, color, lo), Offset(s * 0.34f, s * 0.28f), s * 0.75f),
                    Offset(s * 0.04f, s * 0.08f), Size(s * 0.92f, s * 0.8f), CornerRadius(s * 0.42f),
                )
                // face plate
                drawRoundRect(
                    Brush.radialGradient(listOf(Color.White, Color(0xFFF3EEFF), Color(0xFFE4DCFF)), Offset(s * 0.42f, s * 0.38f), s * 0.5f),
                    Offset(s * 0.18f, s * 0.3f), Size(s * 0.64f, s * 0.46f), CornerRadius(s * 0.2f),
                )
                val eyeY = s * 0.44f
                val lookX = if (mood == Mood.Thinking) s * 0.02f else 0f
                val lookY = if (mood == Mood.Thinking) -s * 0.02f else 0f
                for (x in listOf(0.36f, 0.64f)) {
                    val c = Offset(s * x + lookX, eyeY + lookY)
                    if (mood == Mood.Happy) {
                        val p = Path().apply {
                            moveTo(c.x - s * 0.055f, c.y + s * 0.02f)
                            quadraticTo(c.x, c.y - s * 0.06f, c.x + s * 0.055f, c.y + s * 0.02f)
                        }
                        drawPath(p, ink, style = Stroke(s * 0.03f, cap = StrokeCap.Round))
                    } else {
                        val h = s * 0.15f * blink
                        drawOval(ink, Offset(c.x - s * 0.06f, c.y - h / 2), Size(s * 0.12f, h))
                        if (blink > 0.5f) drawCircle(Color.White, s * 0.02f, Offset(c.x - s * 0.02f, c.y - h * 0.22f))
                    }
                }
                // cheeks
                for (x in listOf(0.22f, 0.7f)) drawOval(Color(0x88FF8FB8), Offset(s * x, s * 0.56f), Size(s * 0.08f, s * 0.05f))
                // mouth
                val open = when (mood) {
                    Mood.Talking -> 0.03f + 0.06f * talk
                    Mood.Happy -> 0.08f
                    else -> 0.03f
                }
                val mw = if (mood == Mood.Happy) s * 0.16f else s * 0.12f
                drawRoundRect(ink, Offset(s / 2 - mw / 2, s * 0.62f), Size(mw, s * open), CornerRadius(s * 0.04f))
                if (open > 0.05f) drawOval(Color(0xFFFF6E9C), Offset(s / 2 - mw / 4, s * 0.62f + s * open * 0.5f), Size(mw / 2, s * open * 0.45f))
            }
        }
    }
}
