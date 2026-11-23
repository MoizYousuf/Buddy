package com.englishcoach.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

// ---------- theme: normal or dark gold ----------
var moneyGoldOwn by mutableStateOf(false)
var moneyGold: Boolean
    get() = moneyGoldOwn || appDark
    set(v) { moneyGoldOwn = v }
val mInk get() = if (moneyGold) Color(0xFFF7E7B4) else Ink
val mMuted get() = if (moneyGold) Color(0xFFB9A77A) else Muted
val mPaper get() = if (moneyGold) Color(0xFF2A2418) else Paper
val mBg get() = if (moneyGold) Color(0xFF14110B) else Color.Transparent
fun Modifier.mcard(r: Int) = if (moneyGold)
    shadow(10.dp, RoundedCornerShape(r.dp), spotColor = Color(0x66C9A227)).background(
        Brush.linearGradient(listOf(Color(0xFF231E14), Color(0xFF1A160E))), RoundedCornerShape(r.dp))
else card(r)
val heroBrush get() = if (moneyGold) Brush.linearGradient(listOf(Color(0xFF3A2C0B), Color(0xFF8A6A1C), Color(0xFF3A2C0B)))
    else Brush.linearGradient(listOf(Color(0xFF052E2B), Color(0xFF065F46), Color(0xFF0B3B36)))

// ---------- 3D note you can tilt (phone sensor) and spin with a finger ----------
@Composable
fun TiltNote(value: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var tx by remember { mutableStateOf(0f) }
    var ty by remember { mutableStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { tx = tx * .85f + e.values[0] * 2.2f * .15f; ty = ty * .85f + (e.values[1] - 6f) * 2.2f * .15f }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(l) }
    }
    val inf = rememberInfiniteTransition(label = "spin")
    val auto by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "a")
    val bob by inf.animateFloat(-6f, 6f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val rot = ((auto + drag.value) % 360f + 360f) % 360f
    val back = rot in 90f..270f
    val rad = Math.toRadians(rot.toDouble())
    val edge = kotlin.math.sin(rad).toFloat()
    val face = kotlin.math.abs(kotlin.math.cos(rad)).toFloat()
    val twinkle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "tw")
    Box(modifier, contentAlignment = Alignment.Center) {
        // soft glow + floor shadow
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Color(0x66FFD86B), Color.Transparent), center, size.width * .6f), size.width * .6f)
            drawOval(Color.Black.copy(alpha = .28f * (.4f + .6f * face)), Offset(size.width * (.5f - .38f * (.35f + .65f * face)), size.height * .92f),
                androidx.compose.ui.geometry.Size(size.width * .76f * (.35f + .65f * face), size.height * .09f))
            // sparkles
            listOf(.08f to .2f, .92f to .15f, .85f to .82f, .12f to .78f, .5f to .02f).forEachIndexed { i, (x, y) ->
                val a = ((twinkle + i * .37f) % 1f)
                val c = Offset(size.width * x, size.height * y); val l = 6f + 8f * a
                drawLine(Color.White.copy(alpha = a), Offset(c.x - l, c.y), Offset(c.x + l, c.y), 2f)
                drawLine(Color.White.copy(alpha = a), Offset(c.x, c.y - l), Offset(c.x, c.y + l), 2f)
            }
        }
        Box(Modifier.fillMaxSize(.86f).pointerInput(Unit) {
            detectHorizontalDragGestures(onDragEnd = {
                scope.launch { drag.animateTo(drag.value + 180f, spring(dampingRatio = .6f, stiffness = 60f)) }
            }) { _, d -> scope.launch { drag.snapTo(drag.value + d * .6f) } }
        }.graphicsLayer {
            rotationY = rot - tx; rotationX = 10f + ty; translationY = bob * density; cameraDistance = 16 * density
        }) {
            // paper thickness (stack of edges)
            for (i in 3 downTo 1) Box(Modifier.fillMaxSize().graphicsLayer { translationX = edge * i * 1.6f * density }
                .background(Color(0xFF8A6A1C).copy(alpha = .9f), RoundedCornerShape(6.dp)))
            RealNote(back, (rot % 180f) / 180f, Modifier.fillMaxSize().graphicsLayer { if (back) rotationY = 180f }, value)
        }
    }
}

// ---------- money jar: fills with what is left of the pocket money ----------
@Composable
fun MoneyJar(fill: Float, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fill.coerceIn(0f, 1f), tween(1200, easing = FastOutSlowInEasing), label = "jar")
    val inf = rememberInfiniteTransition(label = "wave")
    val ph by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "p")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val jar = Path().apply {
            moveTo(w * .3f, 0f); lineTo(w * .7f, 0f); lineTo(w * .7f, h * .1f)
            cubicTo(w, h * .15f, w, h * .2f, w, h * .35f); lineTo(w, h * .9f)
            quadraticTo(w, h, w * .85f, h); lineTo(w * .15f, h); quadraticTo(0f, h, 0f, h * .9f); lineTo(0f, h * .35f)
            cubicTo(0f, h * .2f, 0f, h * .15f, w * .3f, h * .1f); close()
        }
        clipPathSafe(jar) {
            val top = h - (h * .85f) * f
            val wave = Path().apply {
                moveTo(0f, h)
                var x = 0f
                while (x <= w) { lineTo(x, top + sin(x / w * 2 * PI.toFloat() * 1.5f + ph) * 5f); x += 4f }
                lineTo(w, h); close()
            }
            drawPath(wave, Brush.verticalGradient(listOf(Color(0xFFFFD86B), Color(0xFF3E9B5A)), top, h))
            // coins
            for (i in 0 until (f * 8).toInt()) drawCircle(Color(0xFFFFE9A3).copy(alpha = .7f), 5f,
                Offset(w * (.2f + (i * .37f) % .6f), h - 10f - i * (h * .85f * f / 9f)))
        }
        drawPath(jar, Color.White.copy(alpha = .8f), style = Stroke(3f))
        drawLine(Color.White.copy(alpha = .5f), Offset(w * .18f, h * .4f), Offset(w * .18f, h * .8f), 4f)
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.clipPathSafe(p: Path, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) =
    drawContext.canvas.let { c -> c.save(); c.clipPath(p); block(); c.restore() }

// ---------- 3D-looking category icon ----------
fun catEmoji(c: String) = when {
    c.startsWith("Hospital") -> "🏥"; c.startsWith("Doctor") -> "💊"; c.startsWith("Clothes") -> "👕"; c.startsWith("Personal") -> "🎮"
    c.startsWith("Grocery") -> "🛒"; c.startsWith("Bills") -> "💡"; c.startsWith("Fuel") -> "⛽"; c.startsWith("Food") -> "🍔"
    c.startsWith("School") -> "📚"; c.startsWith("Shopping") -> "🛍"; c.startsWith("Repair") -> "🔧"; c.startsWith("Pocket") -> "👛"
    c.startsWith("Ghar se") -> "💵"; else -> "🧾"
}
private fun catColor(c: String) = listOf(0xFFEF4444, 0xFF8B5CF6, 0xFF3B82F6, 0xFFF59E0B, 0xFF10B981, 0xFFEC4899, 0xFF14B8A6)[(c.hashCode() and 0x7fffffff) % 7]

@Composable
fun CatIcon(c: String, size: Int = 44) {
    val col = Color(catColor(c))
    Box(Modifier.size(size.dp).shadow(8.dp, CircleShape, spotColor = col).clip(CircleShape)
        .background(Brush.radialGradient(listOf(Color.White, col.copy(alpha = .55f), col), Offset(size * .9f, size * .8f), size * 2.4f)),
        contentAlignment = Alignment.Center) {
        Text(catEmoji(c), fontSize = (size * .48f).sp)
    }
}

// ---------- sound + vibration (note counting) ----------
fun cashFeedback(ctx: Context) {
    Thread {
        runCatching {
            val rate = 22050
            val buf = ShortArray(rate * 6 / 10)
            val rnd = java.util.Random(7)
            for (k in 0 until 6) {   // 6 quick paper flicks
                val st = k * rate / 12
                for (i in 0 until rate / 40) {
                    val env = (1 - i / (rate / 40f)); val v = (rnd.nextGaussian() * 9000 * env * env).toInt()
                    if (st + i < buf.size) buf[st + i] = v.coerceIn(-32000, 32000).toShort()
                }
            }
            val t = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(buf.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
            t.write(buf, 0, buf.size); t.play(); Thread.sleep(800); t.release()
        }
    }.start()
    runCatching {
        val vib = ctx.getSystemService(android.os.Vibrator::class.java)
        vib?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 18, 60, 18, 60, 18, 60, 30), -1))
    }
}

// ---------- month "Wrapped" story ----------
@Composable
fun MonthWrapped(data: JSONObject, onClose: () -> Unit) {
    val mt = data.optJSONObject("month_total") ?: JSONObject()
    val bal = data.optJSONObject("balance") ?: JSONObject()
    val month = data.optString("month")
    val es = data.optJSONArray("entries")?.let { a -> List(a.length()) { a.getJSONObject(it) } }?.filter { it.optString("date").startsWith(month) } ?: emptyList()
    val spends = es.filter { it.optString("kind") == "spend" }
    val bc = mt.optJSONObject("by_category") ?: JSONObject()
    val top = bc.keys().asSequence().maxByOrNull { bc.optDouble(it) }
    val big = spends.maxByOrNull { it.optDouble("amount") }
    val pocket = mt.optDouble("pocket", 0.0)
    val slides = listOf(
        Triple("🗓", "$month ka hisaab", "Chalo dekhte hain is mahine paisa kahan gaya"),
        Triple("💸", "PKR " + String.format("%,.0f", mt.optDouble("spent", 0.0)), "Kul kharcha · ${spends.size} entries"),
        Triple(top?.let { catEmoji(it) } ?: "🧾", top ?: "—", "Sab se zyada yahan: PKR " + String.format("%,.0f", top?.let { bc.optDouble(it) } ?: 0.0)),
        Triple("🏠🙋", "Ghar ${String.format("%,.0f", mt.optDouble("home", 0.0))} · Mera ${String.format("%,.0f", mt.optDouble("me", 0.0))}", "Ghar ka aur aap ka apna kharcha"),
        Triple("🏆", big?.let { "PKR " + String.format("%,.0f", it.optDouble("amount")) } ?: "—", "Sab se bara kharcha: " + (big?.optString("note")?.ifBlank { big.optString("category") } ?: "")),
        Triple(if (pocket - mt.optDouble("me", 0.0) > 0) "🌟" else "⚠️",
            if (pocket > 0) "PKR " + String.format("%,.0f", pocket - mt.optDouble("me", 0.0) - mt.optDouble("home", 0.0) + mt.optDouble("received", 0.0)) else "—",
            if (pocket > 0) "Pocket money mein se bacha (is mahine)" else "Is mahine pocket money add nahi hui"),
        Triple("🏠", "PKR " + String.format("%,.0f", bal.optDouble("home_owes", 0.0)), "Ghar se abhi itne lene hain"),
    )
    var i by remember { mutableStateOf(0) }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        val s = slides[i]
        val a = remember(i) { Animatable(0f) }
        LaunchedEffect(i) { a.animateTo(1f, spring(dampingRatio = .55f, stiffness = 180f)); kotlinx.coroutines.delay(3200); if (i < slides.size - 1) i++ }
        val inf = rememberInfiniteTransition(label = "bg")
        val sh by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "s")
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3B0764), Color(0xFF0F766E), Color(0xFFC9A227)),
            Offset(0f, sh * 800f), Offset(1000f, 2000f - sh * 800f))).pointerInput(i) {
            detectHorizontalDragGestures { _, _ -> }
        }) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                slides.indices.forEach { k -> Box(Modifier.weight(1f).height(4.dp).background(Color.White.copy(alpha = if (k <= i) .95f else .3f), CircleShape)) }
            }
            Column(Modifier.align(Alignment.Center).padding(28.dp).graphicsLayer { scaleX = .6f + .4f * a.value; scaleY = .6f + .4f * a.value; alpha = a.value; rotationX = (1 - a.value) * 40f },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(s.first, fontSize = 64.sp)
                Text(s.second, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, textAlign = TextAlign.Center)
                Text(s.third, color = Color.White.copy(alpha = .85f), fontSize = 16.sp, textAlign = TextAlign.Center)
            }
            Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("◀", Modifier.background(Color.White.copy(alpha = .2f), CircleShape).press { if (i > 0) i-- }.padding(horizontal = 22.dp, vertical = 12.dp), color = Color.White, fontSize = 18.sp)
                Text(if (i == slides.size - 1) "Band karo" else "Agla ▶", Modifier.background(Color.White, CircleShape)
                    .press { if (i == slides.size - 1) onClose() else i++ }.padding(horizontal = 26.dp, vertical = 12.dp), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

private fun noteRes(v: Int, back: Boolean) = when (v) {
    10 -> if (back) com.englishcoach.app.R.drawable.note10_b else com.englishcoach.app.R.drawable.note10_f
    20 -> if (back) com.englishcoach.app.R.drawable.note20_b else com.englishcoach.app.R.drawable.note20_f
    50 -> if (back) com.englishcoach.app.R.drawable.note50_b else com.englishcoach.app.R.drawable.note50_f
    100 -> if (back) com.englishcoach.app.R.drawable.note100_b else com.englishcoach.app.R.drawable.note100_f
    500 -> if (back) com.englishcoach.app.R.drawable.note500_b else com.englishcoach.app.R.drawable.note500_f
    1000 -> if (back) com.englishcoach.app.R.drawable.note1000_b else com.englishcoach.app.R.drawable.note1000_f
    else -> if (back) com.englishcoach.app.R.drawable.note5000_b else com.englishcoach.app.R.drawable.note5000_f
}

/** A real PKR note photo (front/back) with a moving light reflection and a small "specimen" mark. */
@Composable
fun RealNote(back: Boolean, shine: Float, modifier: Modifier, value: Int = 5000) {
    val img = ImageBitmap.imageResource(noteRes(value, back))
    Box(modifier.clip(RoundedCornerShape(6.dp))) {
        androidx.compose.foundation.Image(img, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
        if (shine in 0f..1f) Canvas(Modifier.fillMaxSize()) {
            val x = -size.width * .4f + shine * size.width * 1.8f
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .4f), Color.Transparent),
                Offset(x - size.width * .15f, 0f), Offset(x + size.width * .15f, size.height)))
        }
        Text("SPECIMEN · نمونہ", Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp), color = Color.Black.copy(alpha = .2f),
            fontSize = 7.sp, fontWeight = FontWeight.Bold)
    }
}


private fun coinRes(v: Int, back: Boolean) = when (v) {
    1 -> if (back) com.englishcoach.app.R.drawable.coin1_b else com.englishcoach.app.R.drawable.coin1_f
    2 -> if (back) com.englishcoach.app.R.drawable.coin2_b else com.englishcoach.app.R.drawable.coin2_f
    5 -> if (back) com.englishcoach.app.R.drawable.coin5_b else com.englishcoach.app.R.drawable.coin5_f
    else -> if (back) com.englishcoach.app.R.drawable.coin10_b else com.englishcoach.app.R.drawable.coin10_f
}

/** A real PKR coin photo, round, with a metal rim; spin it with `rot` (degrees) for a 3D flip. */
@Composable
fun RealCoin(value: Int, modifier: Modifier, rot: Float = 0f) {
    val r = ((rot % 360f) + 360f) % 360f
    val back = r in 90f..270f
    val img = ImageBitmap.imageResource(coinRes(value, back))
    val rim = if (value == 1 || value == 5) Color(0xFF9CA3AF) else Color(0xFFB8860B)
    Box(modifier.graphicsLayer { rotationY = r; cameraDistance = 10 * density }) {
        // edge thickness
        val edge = kotlin.math.sin(Math.toRadians(r.toDouble())).toFloat()
        for (i in 3 downTo 1) Box(Modifier.fillMaxSize().graphicsLayer { translationX = edge * i * 1.4f * density }.background(rim, CircleShape))
        androidx.compose.foundation.Image(img, null, Modifier.fillMaxSize().graphicsLayer { if (back) rotationY = 180f; scaleX = 1.07f; scaleY = 1.07f }
            .clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = .35f), Color.Transparent, Color.Black.copy(alpha = .15f))))
        }
    }
}

/** Coin that keeps spinning (hero when less than 10 rupees, rain, etc.). */
@Composable
fun SpinningCoin(value: Int, modifier: Modifier) {
    val inf = rememberInfiniteTransition(label = "coin")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "c")
    RealCoin(value, modifier, rot)
}
