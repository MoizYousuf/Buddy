package com.englishcoach.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

private val Rainbow = listOf(Color(0xFF7C3AED), Color(0xFFEC4899), Color(0xFFF59E0B), Color(0xFF22C55E), Color(0xFF06B6D4), Color(0xFF7C3AED))

private fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

/** A playful, custom video player on top of ExoPlayer: bouncy play button, rainbow seek bar, double-tap ±10 s, speed,
 *  mute, fullscreen (landscape for wide videos), auto-hiding controls and a glowing border while it plays. */
@Composable
fun FunPlayer(url: String, key: String, portrait: Boolean, modifier: Modifier = Modifier, onError: (String) -> Unit) {
    val ctx = LocalContext.current
    val player = remember(key) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true
        }
    }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }
    var show by remember { mutableStateOf(true) }
    var speed by remember { mutableStateOf(1f) }
    var muted by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    var jump by remember { mutableStateOf<String?>(null) }
    var poke by remember { mutableStateOf(0) }          // any touch: keep controls visible a bit longer
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING; if (state == Player.STATE_ENDED) show = true }
            override fun onPlayerError(error: PlaybackException) {
                onError("Couldn't play: ${error.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')}. Tap to try again.")
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    LaunchedEffect(player) { while (true) { pos = player.currentPosition; dur = player.duration.coerceAtLeast(0); delay(200) } }
    LaunchedEffect(show, playing, poke) { if (show && playing) { delay(3000); show = false } }
    LaunchedEffect(jump) { if (jump != null) { delay(600); jump = null } }

    @Composable
    fun Surface(m: Modifier) {
        val glow = rememberInfiniteTransition(label = "glow")
        val turn by glow.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "turn")
        Box(m.clip(RoundedCornerShape(if (full) 0.dp else 22.dp)).background(Color.Black)
            .then(if (playing && !full) Modifier.border(3.dp, Brush.sweepGradient(Rainbow), RoundedCornerShape(22.dp)) else Modifier)
            .pointerInput(player) {
                detectTapGestures(
                    onTap = { show = !show; poke++ },
                    onDoubleTap = { o ->
                        val back = o.x < size.width / 2
                        player.seekTo((player.currentPosition + if (back) -10_000 else 10_000).coerceIn(0, player.duration.coerceAtLeast(0)))
                        jump = if (back) "⏪ 10s" else "10s ⏩"; show = true; poke++
                    })
            }, contentAlignment = Alignment.Center) {
            AndroidView({ c -> PlayerView(c).apply { useController = false; this.player = player } }, Modifier.fillMaxSize(),
                update = { it.player = player })
            if (buffering) Canvas(Modifier.size(64.dp)) {
                rotate(turn) { drawArc(Brush.sweepGradient(Rainbow), 0f, 280f, false, style = Stroke(9f, cap = androidx.compose.ui.graphics.StrokeCap.Round)) }
            }
            jump?.let { j ->
                Text(j, Modifier.align(if (j.startsWith("⏪")) Alignment.CenterStart else Alignment.CenterEnd).padding(24.dp)
                    .background(Color.Black.copy(alpha = .55f), CircleShape).padding(horizontal = 16.dp, vertical = 10.dp),
                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            AnimatedVisibility(show && !buffering, enter = fadeIn() + scaleIn(initialScale = .6f), exit = fadeOut() + scaleOut(targetScale = .6f)) {
                val press = remember { Animatable(1f) }
                LaunchedEffect(playing) { press.animateTo(1.18f, spring(Spring.DampingRatioHighBouncy)); press.animateTo(1f, spring(Spring.DampingRatioMediumBouncy)) }
                Box(Modifier.size(78.dp).scale(press.value).background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFEC4899))), CircleShape)
                    .border(3.dp, Color.White.copy(alpha = .8f), CircleShape)
                    .pointerInput(Unit) { detectTapGestures { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() }; poke++ } },
                    contentAlignment = Alignment.Center) {
                    Text(if (playing) "❚❚" else "▶", color = Color.White, fontSize = if (playing) 24.sp else 30.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            AnimatedVisibility(show, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f))))
                    .padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SeekBar(if (dur > 0) pos.toFloat() / dur else 0f) { f -> player.seekTo((f * dur).toLong()); pos = (f * dur).toLong(); poke++ }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${fmt(pos)} / ${fmt(dur)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Chip2(if (speed == 1f) "1×" else "${speed}×") {
                            speed = when (speed) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; 2f -> 0.75f; else -> 1f }
                            player.setPlaybackSpeed(speed); poke++
                        }
                        Spacer(Modifier.width(6.dp))
                        Chip2(if (muted) "🔇" else "🔊") { muted = !muted; player.volume = if (muted) 0f else 1f; poke++ }
                        Spacer(Modifier.width(6.dp))
                        Chip2(if (full) "⤡" else "⛶") { full = !full; poke++ }
                    }
                }
            }
        }
    }

    if (full) {
        val act = ctx as? Activity
        DisposableEffect(Unit) {
            if (!portrait) act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose { act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
        Dialog({ full = false }, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize())
        }
    } else Surface(modifier)
}

@Composable
private fun SeekBar(frac: Float, onSeek: (Float) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val f = (drag ?: frac).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(22.dp).pointerInput(Unit) {
        detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) }
    }.pointerInput(Unit) {
        detectHorizontalDragGestures(onDragEnd = { drag?.let(onSeek); drag = null }, onDragCancel = { drag = null }) { c, _ ->
            drag = (c.position.x / size.width).coerceIn(0f, 1f)
        }
    }, contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = .25f), CircleShape))
        Box(Modifier.fillMaxWidth(f.coerceAtLeast(.001f)).height(6.dp).background(Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFEC4899), Color(0xFFF59E0B))), CircleShape))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(Modifier.offset(x = maxWidth * f - 9.dp).size(if (drag != null) 22.dp else 18.dp).background(Color.White, CircleShape)
                .border(3.dp, Color(0xFFEC4899), CircleShape))
        }
    }
}

@Composable
private fun Chip2(t: String, on: () -> Unit) =
    Text(t, Modifier.background(Color.White.copy(alpha = .18f), CircleShape).pointerInput(Unit) { detectTapGestures { on() } }
        .padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
