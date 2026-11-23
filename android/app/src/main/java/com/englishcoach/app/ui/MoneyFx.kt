package com.englishcoach.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

internal val MAmber = Color(0xFFF59E0B)

val NOTES = listOf(5000, 1000, 500, 100, 50, 20, 10, 5, 2, 1)

/** Amount → which notes make it (e.g. 2630 = 1000×2, 500, 100, 10×3). */
fun noteBreakdown(amount: Double): List<Pair<Int, Int>> {
    var left = amount.toLong().coerceAtLeast(0)
    return NOTES.mapNotNull { n -> val c = (left / n).toInt(); left -= c.toLong() * n; if (c > 0) n to c else null }
}

/** A 3D note that keeps turning around (shows both sides). */
@Composable
fun SpinningNote(value: Int, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "spin")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "r")
    val bob by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val back = rot in 90f..270f
    Box(modifier.graphicsLayer {
        rotationY = rot; rotationX = 8f; translationY = bob * density; cameraDistance = 14 * density
    }) {
        RealNote(back, -1f, Modifier.fillMaxSize().graphicsLayer { if (back) rotationY = 180f }, value)
    }
}

/** A fanned stack of the notes that make up an amount; pops in note by note. */
@Composable
fun NoteFan(amount: Double, modifier: Modifier = Modifier) {
    val parts = noteBreakdown(amount).flatMap { (n, c) -> List(minOf(c, 4)) { n } }.take(10)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        parts.forEachIndexed { i, n ->
            val a = remember(amount) { Animatable(0f) }
            LaunchedEffect(amount) { kotlinx.coroutines.delay(i * 70L); a.animateTo(1f, spring(dampingRatio = .55f, stiffness = 300f)) }
            val anim = Modifier.graphicsLayer {
                rotationZ = (i - parts.size / 2f) * 4f * a.value; scaleX = a.value; scaleY = a.value; alpha = a.value
                translationY = (1 - a.value) * 60f
            }
            if (n < 10) RealCoin(n, Modifier.offset(x = (i * 22).dp + 30.dp).size(42.dp).then(anim), rot = (1 - a.value) * 360f)
            else RealNote(false, -1f, Modifier.offset(x = (i * 22).dp).size(104.dp, 42.dp).then(anim).graphicsLayer { shadowElevation = 6f }, n)
        }
    }
    if (parts.isNotEmpty()) Text(noteBreakdown(amount).joinToString("  +  ") { (n, c) -> "$n×$c" }, color = Muted, fontSize = 11.sp)
}

/** Number that counts up to its value. */
@Composable
fun CountUp(value: Double, prefix: String = "PKR "): String {
    val a by animateFloatAsState(value.toFloat(), tween(900, easing = FastOutSlowInEasing), label = "count")
    return prefix + String.format("%,.0f", a)
}

/** Notes flying up after a save. Change `key` to play it. */
@Composable
fun NoteBurst(key: Int, modifier: Modifier = Modifier) {
    if (key == 0) return
    Box(modifier.fillMaxWidth().height(220.dp)) {
        List(9) { it }.forEach { i ->
            val a = remember(key) { Animatable(0f) }
            LaunchedEffect(key) { kotlinx.coroutines.delay(i * 45L); a.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }
            if (a.value < 1f && i % 3 == 2) RealCoin(listOf(10, 5, 2, 1)[i % 4], Modifier.align(Alignment.BottomCenter).size(40.dp).graphicsLayer {
                translationX = (i - 4) * 38f * density * a.value / 2; translationY = -a.value * 210f * density; alpha = 1f - a.value }, rot = a.value * 720f)
            else if (a.value < 1f) RealNote(false, -1f, value = NOTES[i % 4], modifier = Modifier.align(Alignment.BottomCenter).size(90.dp, 44.dp).graphicsLayer {
                translationX = (i - 4) * 38f * density * a.value / 2
                translationY = -a.value * 190f * density
                rotationZ = (i - 4) * 25f * a.value; rotationY = a.value * 540f
                alpha = 1f - a.value; cameraDistance = 12 * density
            })
        }
    }
}

/** Biggest note that fits the amount (for the spinning hero note). */
fun heroNote(v: Double) = NOTES.firstOrNull { v >= it } ?: 5000
