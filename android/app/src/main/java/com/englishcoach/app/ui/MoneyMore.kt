package com.englishcoach.app.ui

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private fun pkr(v: Double) = "PKR " + String.format("%,.0f", v)
private fun JSONArray?.objs(): List<JSONObject> = this?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: emptyList()

// ---------- 1. saving goals ----------
@Composable
fun GoalsCard(vm: CoachViewModel, data: JSONObject, wallet: Double, reload: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🎯") }
    var putIn by remember { mutableStateOf<String?>(null) }
    var amt by remember { mutableStateOf("") }
    var party by remember { mutableStateOf(0) }
    var partyName by remember { mutableStateOf("") }
    val goals = data.optJSONArray("goals").objs()
    Column(Modifier.fillMaxWidth().mcard(22).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎯 Bachat ke goals", Modifier.weight(1f), color = mInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(if (adding) "✕" else "➕ Naya", Modifier.background(mPaper, CircleShape).press { adding = !adding }.padding(horizontal = 12.dp, vertical = 6.dp),
                color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        if (goals.isEmpty() && !adding) Text("Kis cheez ke liye paise jama kar rahe ho? Naya phone, bike, Eid… goal banao.", color = mMuted, fontSize = 12.sp)
        if (adding) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("🎯", "📱", "🏍", "💻", "🎮", "👟", "🕋", "🎁", "✈️").forEach { e ->
                    Text(e, Modifier.background(if (emoji == e) Lime else mPaper, CircleShape).press { emoji = e }.padding(8.dp), fontSize = 18.sp)
                }
            }
            MField(name, { name = it }, "Goal (Naya phone)")
            MField(target, { target = it.filter { c -> c.isDigit() } }, "Kitne chahiye (PKR)", number = true)
            QuickAmounts({ q -> target = ((target.toIntOrNull() ?: 0) + q).toString() }, listOf(5000, 10000, 25000, 50000, 100000))
            Text("✅ Goal banao", Modifier.fillMaxWidth().background(Lime, RoundedCornerShape(14.dp)).press {
                scope.launch { vm.mac("/money/goal", JSONObject().put("name", name).put("target", target).put("emoji", emoji)); adding = false; name = ""; target = ""; reload() }
            }.padding(12.dp), color = Ink, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        }
        goals.forEach { g ->
            val pct = (g.optDouble("saved") / g.optDouble("target").coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    MoneyJar(pct, Modifier.size(40.dp, 62.dp))
                    Text(g.optString("emoji"), fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(g.optString("name") + if (g.optBoolean("done")) "  🏆" else "", color = mInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    val a by animateFloatAsState(pct, tween(1000), label = "g")
                    Box(Modifier.fillMaxWidth().height(9.dp).background(mPaper, CircleShape)) {
                        Box(Modifier.fillMaxWidth(a).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFF16A34A), Color(0xFFC9A227))), CircleShape))
                    }
                    Text("${pkr(g.optDouble("saved"))} / ${pkr(g.optDouble("target"))}  ·  ${(pct * 100).toInt()}%", color = mMuted, fontSize = 11.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("➕", Modifier.background(Lime, CircleShape).press { putIn = g.optString("id"); amt = "" }.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp)
                    Text("🗑", Modifier.padding(top = 4.dp).press {
                        scope.launch { vm.mac("/money/goal", JSONObject().put("id", g.optString("id")).put("delete", true)); reload() }
                    }, fontSize = 12.sp)
                }
            }
            if (putIn == g.optString("id")) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MField(amt, { amt = it.filter { c -> c.isDigit() } }, "Kitne daalo? (paas: ${pkr(wallet)})", number = true, modifier = Modifier.weight(1f))
                Text("Daalo", Modifier.background(Violet, CircleShape).press {
                    val v = amt.toDoubleOrNull() ?: return@press
                    scope.launch {
                        vm.mac("/money/add", JSONObject().put("kind", "goal").put("goal", g.optString("id")).put("amount", v).put("note", g.optString("emoji") + " " + g.optString("name")))
                        putIn = null; cashFeedback(ctx)
                        if (g.optDouble("saved") + v >= g.optDouble("target")) { partyName = g.optString("name"); party++ }
                        reload()
                    }
                }.padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (party > 0) GoalParty(partyName, party) { party = 0 }
}

/** Goal reached: big trophy, notes raining. */
@Composable
private fun GoalParty(name: String, key: Int, onClose: () -> Unit) {
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false)) {
        val a = remember(key) { Animatable(0f) }
        LaunchedEffect(key) { a.animateTo(1f, spring(dampingRatio = .45f, stiffness = 150f)) }
        Box(Modifier.fillMaxSize().background(Color(0xE6120F08)).press { onClose() }, contentAlignment = Alignment.Center) {
            NoteRain()
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { scaleX = a.value; scaleY = a.value; rotationZ = (1 - a.value) * -30f }) {
                Text("🏆", fontSize = 96.sp)
                Text("Mubarak ho!", color = Color(0xFFFFD86B), fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
                Text("\"$name\" ka goal poora ho gaya", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                SuccessLottie(key)
                Text("Tap to close", color = Color.White.copy(alpha = .5f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun NoteRain() {
    val inf = rememberInfiniteTransition(label = "rain")
    Box(Modifier.fillMaxSize()) {
        repeat(12) { i ->
            val y by inf.animateFloat(-0.2f, 1.1f, infiniteRepeatable(tween(2600 + i * 170, delayMillis = i * 120, easing = LinearEasing)), label = "y$i")
            val r by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1800 + i * 90, easing = LinearEasing)), label = "r$i")
            BoxWithConstraints(Modifier.fillMaxSize()) {
                RealNote(false, -1f, value = listOf(5000, 1000, 500, 100)[i % 4], modifier = Modifier.offset(x = maxWidth * ((i * 0.083f + 0.02f) % 0.9f), y = maxHeight * y).size(110.dp, 46.dp)
                    .graphicsLayer { rotationZ = r * .3f - 50f; rotationY = r; cameraDistance = 12 * density; alpha = .9f })
            }
        }
    }
}

// ---------- 2. note counting game (when pocket money comes) ----------
@Composable
fun NoteCountGame(amount: Double, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val notes = remember(amount) { noteBreakdown(amount).flatMap { (n, c) -> List(minOf(c, 30)) { n } } }
    var counted by remember { mutableStateOf(0) }
    var sum by remember { mutableStateOf(0.0) }
    val shown by animateFloatAsState(sum.toFloat(), tween(300), label = "sum")
    Dialog(onDone, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF052E2B), Color(0xFF0B1F1C)))), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("👛 Pocket money gino!", color = Color(0xFFFFD86B), fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                Text(pkr(shown.toDouble()), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp)
                Text(if (counted < notes.size) "Note ko upar swipe karo ☝️  (${notes.size - counted} baqi)" else "Poore ${pkr(amount)} gin liye! 🎉",
                    color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
                Box(Modifier.size(300.dp, 200.dp), contentAlignment = Alignment.Center) {
                    for (i in (notes.size - 1) downTo counted) {
                        key(i) { SwipeNote(notes[i], i == counted, (i - counted).coerceAtMost(5)) {
                            counted++; sum += notes[i]; flick(ctx)
                        } }
                    }
                }
                Text(if (counted < notes.size) "Skip" else "✅ Done", Modifier.background(if (counted < notes.size) Color.White.copy(alpha = .15f) else Lime, CircleShape)
                    .press { onDone() }.padding(horizontal = 28.dp, vertical = 12.dp), color = if (counted < notes.size) Color.White else Ink,
                    fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun SwipeNote(value: Int, top: Boolean, depth: Int, onGone: () -> Unit) {
    var dy by remember { mutableStateOf(0f) }
    var dx by remember { mutableStateOf(0f) }
    val fly = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val img = if (value == 5000) null else value
    Box(Modifier.size(280.dp, 120.dp).graphicsLayer {
        translationY = dy - fly.value * 1600f + depth * 10f; translationX = dx; rotationZ = dx / 20f + depth * 1.5f - fly.value * 20f
        scaleX = 1f - depth * .03f; scaleY = 1f - depth * .03f; alpha = 1f - fly.value
    }.then(if (top) Modifier.pointerInput(Unit) {
        detectDragGestures(onDragEnd = {
            if (dy < -120f) scope.launch { fly.animateTo(1f, tween(260)); onGone() } else { dy = 0f; dx = 0f }
        }) { _, d -> dy += d.y; dx += d.x }
    } else Modifier)) {
        RealNote(false, -1f, Modifier.fillMaxSize(), value)
    }
}

/** One short paper-flick sound + tiny buzz. */
private fun flick(ctx: Context) {
    Thread {
        runCatching {
            val rate = 22050; val n = rate / 30; val buf = ShortArray(n); val rnd = java.util.Random()
            for (i in 0 until n) { val e = 1 - i / n.toFloat(); buf[i] = (rnd.nextGaussian() * 10000 * e * e).toInt().coerceIn(-32000, 32000).toShort() }
            val t = android.media.AudioTrack.Builder()
                .setAudioFormat(android.media.AudioFormat.Builder().setSampleRate(rate).setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(n * 2).setTransferMode(android.media.AudioTrack.MODE_STATIC).build()
            t.write(buf, 0, n); t.play(); Thread.sleep(150); t.release()
        }
    }.start()
    runCatching { ctx.getSystemService(android.os.Vibrator::class.java)?.vibrate(android.os.VibrationEffect.createOneShot(15, 120)) }
}

// ---------- 3 + 4. forecast and AI advice ----------
@Composable
fun ForecastCard(vm: CoachViewModel, data: JSONObject, month: String) {
    val scope = rememberCoroutineScope()
    var advice by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val f = data.optJSONObject("forecast") ?: JSONObject()
    Column(Modifier.fillMaxWidth().mcard(22).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("📈 Mahine ka andaza", color = mInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        if (f.length() > 0) {
            Text("Roz ka ausat ${pkr(f.optDouble("per_day"))}. Isi raftaar se mahine ke aakhir tak ≈ ${pkr(f.optDouble("projected"))} (${f.optInt("days_left")} din baqi)",
                color = mMuted, fontSize = 12.sp)
            f.optJSONArray("warn").objs().forEach { w ->
                Text("⚠️ ${w.optString("category")}: aise chala to ${pkr(w.optDouble("projected"))} ho jayega, had ${pkr(w.optDouble("limit"))}",
                    color = Color(0xFFE11D48), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else Text("Andaza sirf is mahine ka banta hai.", color = mMuted, fontSize = 12.sp)
        Text(if (busy) "🤖 Soch raha hoon… (1-2 min)" else "🤖 AI se mashwara lo", Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF0F766E), Color(0xFF7C3AED))), CircleShape)
            .press {
                if (busy) return@press
                busy = true
                scope.launch { advice = vm.mac("/money/advice", JSONObject().put("month", month)).let { it.optString("text", it.optString("error")) }; busy = false }
            }.padding(horizontal = 14.dp, vertical = 9.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        if (advice.isNotBlank()) Text(advice.replace("**", ""), Modifier.background(mPaper, RoundedCornerShape(14.dp)).padding(12.dp), color = mInk, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

// ---------- 5. send claim to home on WhatsApp (text + bill photos) ----------
@Composable
fun ClaimButton(vm: CoachViewModel, data: JSONObject, reload: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val un = data.optDouble("unclaimed", 0.0)
    var busy by remember { mutableStateOf(false) }
    if (un <= 0) return
    Text(if (busy) "Bana raha hoon…" else "📨 Ghar ko claim bhejo · ${pkr(un)} (WhatsApp, bill photos ke saath)", Modifier.fillMaxWidth()
        .background(Brush.horizontalGradient(listOf(Color(0xFF25D366), Color(0xFF128C7E))), RoundedCornerShape(16.dp)).press {
            if (busy) return@press
            busy = true
            scope.launch {
                val r = vm.mac("/money/claim", JSONObject())
                val uris = ArrayList<Uri>()
                r.optJSONArray("photos")?.let { a ->
                    for (i in 0 until a.length()) {
                        val b = vm.macBytes("/money/photo?name=" + a.getString(i)) ?: continue
                        withContext(Dispatchers.IO) {
                            val v = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "bill_${a.getString(i)}")
                                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MoneyBook")
                            }
                            ctx.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)?.let { u ->
                                ctx.contentResolver.openOutputStream(u)?.use { it.write(b) }; uris += u
                            }
                        }
                    }
                }
                val send = if (uris.isEmpty()) Intent(Intent.ACTION_SEND).setType("text/plain")
                else Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                send.putExtra(Intent.EXTRA_TEXT, r.optString("text"))
                runCatching { ctx.startActivity(Intent(send).setPackage("com.whatsapp")) }.onFailure { ctx.startActivity(Intent.createChooser(send, "Claim bhejo")) }
                busy = false; reload()
            }
        }.padding(14.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center)
}

// ---------- 6. new photos in the gallery: bill hai? ----------
@Composable
fun NewPhotosPrompt(onScan: (List<Uri>) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = ctx.getSharedPreferences("money_ui", 0)
    var photos by remember { mutableStateOf<List<Pair<Uri, ImageBitmap?>>>(emptyList()) }
    var picked by remember { mutableStateOf(setOf<Uri>()) }
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    fun load() = scope.launch {
        val since = prefs.getLong("photos_seen", System.currentTimeMillis() / 1000 - 2 * 86400)
        photos = withContext(Dispatchers.IO) {
            val out = mutableListOf<Pair<Uri, ImageBitmap?>>()
            ctx.contentResolver.query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(android.provider.MediaStore.Images.Media._ID),
                "${android.provider.MediaStore.Images.Media.DATE_ADDED} > ? AND ${android.provider.MediaStore.Images.Media.RELATIVE_PATH} NOT LIKE ?",
                arrayOf("$since", "%MoneyBook%"), "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
                while (c.moveToNext() && out.size < 12) {
                    val u = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                    val bm = runCatching { ctx.contentResolver.loadThumbnail(u, android.util.Size(200, 200), null).asImageBitmap() }.getOrNull()
                    out += u to bm
                }
            }
            out
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) load() }
    LaunchedEffect(Unit) { if (ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED) load() }
    fun seen() { prefs.edit().putLong("photos_seen", System.currentTimeMillis() / 1000).apply(); photos = emptyList() }
    if (ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED && !prefs.getBoolean("photos_asked", false)) {
        Text("🧾 Nayi bill photos khud pakrun? (gallery ki ijazat)", Modifier.fillMaxWidth().mcard(16).press {
            prefs.edit().putBoolean("photos_asked", true).apply(); ask.launch(perm)
        }.padding(12.dp), color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        return
    }
    if (photos.isEmpty()) return
    Column(Modifier.fillMaxWidth().mcard(18).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { Text("🧾 Nayi photos aayi hain, koi bill hai?", Modifier.weight(1f), color = mInk, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Text("Nahi", Modifier.press { seen() }, color = mMuted, fontSize = 12.sp) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            photos.forEach { (u, bm) ->
                Box(Modifier.size(70.dp).clip(RoundedCornerShape(10.dp)).background(mPaper).press { picked = if (u in picked) picked - u else picked + u }) {
                    bm?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    if (u in picked) Text("✅", Modifier.align(Alignment.TopEnd).padding(2.dp), fontSize = 14.sp)
                }
            }
        }
        if (picked.isNotEmpty()) Text("📷 ${picked.size} scan karo", Modifier.background(Lime, CircleShape).press { onScan(picked.toList()); picked = emptySet(); seen() }
            .padding(horizontal = 14.dp, vertical = 8.dp), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
    }
}

// ---------- 7 + 8. lock and reminders ----------
@Composable
fun MoneyLockGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("money_ui", 0)
    // unlocked once = open for 10 minutes (no fingerprint every time you switch tabs)
    var open by remember { mutableStateOf(!prefs.getBoolean("lock", false) || System.currentTimeMillis() - prefs.getLong("unlocked_at", 0) < 10 * 60_000) }
    val km = ctx.getSystemService(KeyguardManager::class.java)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (it.resultCode == android.app.Activity.RESULT_OK) { open = true; prefs.edit().putLong("unlocked_at", System.currentTimeMillis()).apply() } }
    fun ask() {
        @Suppress("DEPRECATION")
        val i = km?.createConfirmDeviceCredentialIntent("Money book", "Fingerprint ya PIN se kholo")
        if (i == null) open = true else launcher.launch(i)
    }
    LaunchedEffect(Unit) { if (!open) ask() }
    if (open) content() else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("🔒", fontSize = 64.sp)
            Text("Money book locked", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text("👆 Kholo", Modifier.background(Violet, CircleShape).press { ask() }.padding(horizontal = 26.dp, vertical = 12.dp), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MoneySettings(vm: CoachViewModel, data: JSONObject, reload: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = ctx.getSharedPreferences("money_ui", 0)
    var lock by remember { mutableStateOf(prefs.getBoolean("lock", false)) }
    val remind = data.optBoolean("remind", true)
    Column(Modifier.fillMaxWidth().mcard(22).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("⚙️ Settings", color = mInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        @Composable fun Toggle(t: String, on: Boolean, f: () -> Unit) = Row(Modifier.fillMaxWidth().press { f() }, verticalAlignment = Alignment.CenterVertically) {
            Text(t, Modifier.weight(1f), color = mInk, fontSize = 13.sp)
            Box(Modifier.size(46.dp, 26.dp).background(if (on) Color(0xFF16A34A) else mPaper, CircleShape).padding(3.dp), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
                Box(Modifier.size(20.dp).background(Color.White, CircleShape))
            }
        }
        Toggle("🔒 Fingerprint / PIN lock", lock) { lock = !lock; prefs.edit().putBoolean("lock", lock).apply() }
        Toggle("🌙 Raat 9:30 yaad dihani + Sunday khulasa", remind) { scope.launch { vm.mac("/money/remind", JSONObject().put("on", !remind)); reload() } }
        Text("👨‍👩‍👦 Ghar walon ko hisaab ka link bhejo", Modifier.fillMaxWidth().background(mPaper, RoundedCornerShape(14.dp)).press {
            scope.launch {
                val k = vm.mac("/money/family").optString("key")
                val base = vm.activeUrl.trimEnd('/')
                val t = "Assalam o Alaikum! Ghar ka hisaab yahan dekhein (ghar ke Wi-Fi par khulega). Paise dein to \"De diye\" daba dein:\n$base/family?k=$k"
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Link bhejo"))
            }
        }.padding(12.dp), color = Violet, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Link sirf ghar ke Wi-Fi par chalta hai, aur sirf dekhne ke liye hai.", color = mMuted, fontSize = 11.sp)
    }
}


/** Family tapped "De diye" on their page: confirm to add it as received. */
@Composable
fun FamilyPending(vm: CoachViewModel, reload: () -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(listOf<JSONObject>()) }
    LaunchedEffect(Unit) { items = vm.mac("/money/family").optJSONArray("pending").objs() }
    items.forEach { f ->
        Row(Modifier.fillMaxWidth().background(lt(Color(0xFFDCFCE7)), RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("💵 ${f.optString("who")} ne PKR ${"%,.0f".format(f.optDouble("amount"))} diye?", Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("✅", Modifier.background(Lime, CircleShape).press {
                scope.launch { vm.mac("/family/confirm", JSONObject().put("id", f.optString("id")).put("ok", true)); items = items - f; reload() }
            }.padding(horizontal = 12.dp, vertical = 6.dp))
            Spacer(Modifier.width(6.dp))
            Text("✕", Modifier.background(Color.White, CircleShape).press {
                scope.launch { vm.mac("/family/confirm", JSONObject().put("id", f.optString("id")).put("ok", false)); items = items - f }
            }.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}
