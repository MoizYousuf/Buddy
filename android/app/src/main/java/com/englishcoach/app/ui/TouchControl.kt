package com.englishcoach.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import com.englishcoach.app.CoachViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private const val LONG_PRESS_MS = 420L
private const val DOUBLE_TAP_MS = 260L
private const val SCROLL_GAIN = 2.6f   // phone pixels -> Mac pixels

/**
 * Phone-style touch on the Mac picture:
 *  swipe = scroll (natural direction, with momentum) · tap = click · two quick taps = double click
 *  hold = right click · hold then move = drag (select text, move files).
 * Two-finger pinch is handled by the parent (zoom), so this only reacts to one finger.
 */
@Composable
fun Modifier.touchControl(vm: CoachViewModel): Modifier {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val state = remember { object { var pendingTap: Job? = null; var lastTapAt = 0L; var fling: Job? = null } }

    return pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        fun frac(o: Offset) = arrayOf("x" to (o.x / size.width).coerceIn(0f, 1f), "y" to (o.y / size.height).coerceIn(0f, 1f))

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            state.fling?.cancel()  // a new touch stops momentum, like on a phone
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var mode = "none"      // none -> scroll | hold -> drag
            var pos = down.position
            var pendingScroll = Offset.Zero
            var lastSend = 0L

            // wait for either movement past the slop (scroll) or the long-press time (hold)
            val movedEarly = withTimeoutOrNull(LONG_PRESS_MS) {
                while (true) {
                    val e = awaitPointerEvent()
                    if (e.changes.count { it.pressed } > 1) return@withTimeoutOrNull null  // pinch: let the parent handle it
                    val c = e.changes.first()
                    if (!c.pressed) return@withTimeoutOrNull false  // lifted quickly -> tap
                    tracker.addPosition(c.uptimeMillis, c.position)
                    if ((c.position - down.position).getDistance() > slop) { pos = c.position; return@withTimeoutOrNull true }
                }
                @Suppress("UNREACHABLE_CODE") false
            }
            when (movedEarly) {
                null -> {
                    // still holding after the long-press time: arm drag / right click
                    mode = "hold"
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                true -> mode = "scroll"
                false -> {
                    // a tap: click, or double click if it follows another tap quickly
                    val now = System.currentTimeMillis()
                    if (now - state.lastTapAt < DOUBLE_TAP_MS && state.pendingTap?.isActive == true) {
                        state.pendingTap?.cancel()
                        vm.macInput("double", *frac(down.position))
                        state.lastTapAt = 0L
                    } else {
                        state.lastTapAt = now
                        state.pendingTap = scope.launch { delay(DOUBLE_TAP_MS); vm.macInput("tap", *frac(down.position)) }
                    }
                    return@awaitEachGesture
                }
            }

            while (true) {
                val e = awaitPointerEvent()
                if (e.changes.count { it.pressed } > 1) return@awaitEachGesture
                val c = e.changes.first()
                tracker.addPosition(c.uptimeMillis, c.position)
                if (!c.pressed) break
                val delta = c.position - pos
                pos = c.position
                c.consume()
                when (mode) {
                    "scroll" -> {
                        pendingScroll += delta
                        val now = System.currentTimeMillis()
                        if (now - lastSend > 30) {
                            vm.macInput("scroll", "dy" to -pendingScroll.y * SCROLL_GAIN, "dx" to -pendingScroll.x * SCROLL_GAIN)
                            pendingScroll = Offset.Zero
                            lastSend = now
                        }
                    }
                    "hold" -> if ((pos - down.position).getDistance() > slop) {
                        mode = "drag"
                        vm.macInput("down", *frac(down.position))
                    }
                    "drag" -> {
                        val now = System.currentTimeMillis()
                        if (now - lastSend > 40) { vm.macInput("dragmove", *frac(pos)); lastSend = now }
                    }
                }
            }

            when (mode) {
                "hold" -> vm.macInput("right", *frac(down.position))     // held without moving = right click
                "drag" -> vm.macInput("up", *frac(pos))
                "scroll" -> {
                    if (pendingScroll != Offset.Zero) vm.macInput("scroll", "dy" to -pendingScroll.y * SCROLL_GAIN, "dx" to -pendingScroll.x * SCROLL_GAIN)
                    // momentum: keep scrolling with the release speed, slowing down
                    val v = tracker.calculateVelocity()
                    var vy = -v.y * SCROLL_GAIN / 60f
                    var vx = -v.x * SCROLL_GAIN / 60f
                    if (abs(vy) > 8 || abs(vx) > 8) state.fling = scope.launch {
                        while (abs(vy) > 2 || abs(vx) > 2) {
                            vm.macInput("scroll", "dy" to vy, "dx" to vx)
                            vy *= 0.88f
                            vx *= 0.88f
                            delay(35)
                        }
                    }
                }
            }
        }
    }
}
