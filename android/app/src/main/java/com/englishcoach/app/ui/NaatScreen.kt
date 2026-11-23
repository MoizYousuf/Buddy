package com.englishcoach.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Phase
import com.englishcoach.app.Screen

private val Green = listOf(Color(0xFF0F766E), Color(0xFF14532D))

/** Naat & song studio: idea → lyrics + Suno prompt → import the Suno song → premium animated video. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NaatScreen(vm: CoachViewModel) {
    LaunchedEffect(Unit) { vm.loadNaats() }
    if (vm.naatOpen != null) { NaatDetail(vm); return }
    var idea by rememberSaveable { mutableStateOf("") }
    var style by rememberSaveable { mutableStateOf("vocals") }
    var voice by rememberSaveable { mutableStateOf("kids") }
    var forKids by rememberSaveable { mutableStateOf(true) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("Naat & song studio") { vm.screen = Screen.Studio } }
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(Green), RoundedCornerShape(24.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🕌 New naat or song", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text("Explain it in Urdu, Roman Urdu or English — type or hold 🎤 and speak.", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                Box(Modifier.fillMaxWidth().heightIn(min = 90.dp).background(Surface, RoundedCornerShape(16.dp)).padding(12.dp)) {
                    if (idea.isEmpty()) Text("e.g. bachon ke liye Bismillah wali nasheed, Allah ka shukr, khushi wala mood…", color = Muted, fontSize = 14.sp)
                    BasicTextField(idea, { idea = it }, Modifier.fillMaxWidth(), textStyle = TextStyle(color = Ink, fontSize = 15.sp), cursorBrush = SolidColor(Violet))
                }
                if (vm.phase == Phase.Listening) VoiceWave(vm.micLevel, Modifier.fillMaxWidth().height(30.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("vocals" to "🎙 Vocals only", "daf" to "🥁 Vocals + daf", "music" to "🎵 Soft music").forEach { (k, t) ->
                        Text(t, Modifier.background(if (style == k) Color.White else Color.White.copy(alpha = .2f), CircleShape).press { style = k }
                            .padding(horizontal = 12.dp, vertical = 7.dp), color = if (style == k) Ink else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Text("Voice", color = Color.White.copy(alpha = .85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("kids" to "👧👦 Kids' choir", "girl" to "👧 Little girl", "boy" to "👦 Little boy", "male" to "👨 Man", "female" to "👩 Woman").forEach { (k, t) ->
                        Text(t, Modifier.background(if (voice == k) Color.White else Color.White.copy(alpha = .2f), CircleShape).press { voice = k }
                            .padding(horizontal = 12.dp, vertical = 7.dp), color = if (voice == k) Ink else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("For kids", color = Color.White, fontWeight = FontWeight.ExtraBold)
                        Text("Simple words, catchy chorus, cartoon video", color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
                    }
                    androidx.compose.material3.Switch(forKids, { forKids = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (vm.phase == Phase.Listening) "⏹ Stop" else "🎤 Speak", Modifier.background(Color.White.copy(alpha = .25f), RoundedCornerShape(14.dp))
                        .press { vm.dictate { t -> idea = (idea + " " + t).trim() } }.padding(horizontal = 16.dp, vertical = 12.dp), color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Text("✨ Write lyrics", Modifier.weight(1f).background(Surface, RoundedCornerShape(14.dp))
                        .press { if (idea.isNotBlank()) { vm.createNaat(idea, style, voice, forKids); idea = "" } }.padding(vertical = 12.dp),
                        color = Color(0xFF0F766E), fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                }
                vm.naatMsg?.let { Text(it, color = Color.White, fontSize = 12.sp) }
            }
        }
        if (vm.naats.isNotEmpty()) item { Text("Your projects", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        items(vm.naats, key = { it.optString("id") }) { p ->
            Row(Modifier.fillMaxWidth().card(20).press { vm.openNaat(p.optString("id")) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (p.optBoolean("song")) "🎵" else "📝", fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.optString("title").ifBlank { "Writing…" }, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 2)
                    Text("${p.optString("stage")} · ${p.optString("created")}", color = Muted, fontSize = 11.sp)
                }
                Text("→", color = Violet, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun NaatDetail(vm: CoachViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val g = vm.naat
    val b = g?.optJSONObject("brief")
    val st = g?.optJSONObject("status")
    val stage = st?.optString("stage").orEmpty()
    var urdu by remember { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var credits by remember { mutableStateOf(200) }
    var shorts by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { u -> vm.importSong(u) } }
    fun copy(label: String, text: String) {
        ctx.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        vm.naatMsg = "Copied $label. Paste it in Suno."
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("Naat project") { vm.naatOpen = null; vm.loadNaats() } }
        vm.naatMsg?.let { m -> item { Text(m, Modifier.fillMaxWidth().background(lt(Color(0xFFE6F7EE)), RoundedCornerShape(14.dp)).press { vm.naatMsg = null }.padding(12.dp), color = Ink, fontSize = 13.sp) } }
        if (b == null || b.length() == 0 || stage == "writing") item {
            Column(Modifier.fillMaxWidth().card(20).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("✍️ Claude is writing the lyrics…", color = Ink, fontWeight = FontWeight.ExtraBold)
                vm.naatSteps.takeLast(3).forEach { Text("▸ " + it.removePrefix("▸ "), color = Muted, fontSize = 12.sp, maxLines = 1) }
            }
        }
        if (b != null && b.length() > 0) {
            item {
                Column(Modifier.fillMaxWidth().background(Brush.linearGradient(Green), RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(b.optString("title"), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, style = TextStyle(textDirection = TextDirection.Content))
                    Text(b.optString("theme"), color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                    Text("Stage: $stage", color = Color(0xFFBBF7D0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Column(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Step 1 · Make the song in Suno", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Text("Style of Music", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(b.optString("suno_style"), color = Ink, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Copy style") { copy("style", b.optString("suno_style")) }
                        Pill("Copy lyrics") { copy("lyrics", b.optString("suno_lyrics")) }
                        Pill("Open Suno", outline = true) {
                            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://suno.com/create")))
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Lyrics", Modifier.weight(1f), color = Ink, fontWeight = FontWeight.ExtraBold)
                        Text(if (urdu) "Roman" else "اردو", Modifier.background(Pastel, CircleShape).press { urdu = !urdu }
                            .padding(horizontal = 12.dp, vertical = 6.dp), color = Violet, fontWeight = FontWeight.Bold)
                    }
                    Text(if (urdu) b.optString("lyrics_urdu") else b.optString("lyrics_roman"), Modifier.fillMaxWidth(), color = Ink, fontSize = 15.sp, lineHeight = 24.sp,
                        textAlign = if (urdu) TextAlign.Right else TextAlign.Start, style = TextStyle(textDirection = if (urdu) TextDirection.Rtl else TextDirection.Ltr))
                    if (b.optString("meaning_en").isNotBlank()) Text("Meaning: " + b.optString("meaning_en"), color = Muted, fontSize = 12.sp)
                }
            }
            item {
                Column(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Want changes?", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Box(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(14.dp)).padding(12.dp)) {
                        if (note.isEmpty()) Text("e.g. chorus chhota karo, ek verse aur add karo…", color = Muted, fontSize = 13.sp)
                        BasicTextField(note, { note = it }, Modifier.fillMaxWidth(), textStyle = TextStyle(color = Ink, fontSize = 14.sp), cursorBrush = SolidColor(Violet))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Send to Claude") { if (note.isNotBlank()) { vm.reviseNaat(note); note = "" } }
                        Pill(if (vm.phase == Phase.Listening) "⏹ Stop" else "🎤 Speak", outline = true) { vm.dictate { t -> note = (note + " " + t).trim() } }
                    }
                }
            }
            item {
                val song = g.optString("song")
                Column(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Step 2 · Import the song", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Text(if (song.isNotBlank()) "✅ $song is on the Mac" else "Download the song (MP3) from Suno, then import it here.", color = Muted, fontSize = 13.sp)
                    Pill(if (song.isNotBlank()) "Replace song" else "🎵 Import song from phone") { pick.launch("audio/*") }
                }
            }
            if (g.optString("song").isNotBlank()) item {
                Column(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Step 3 · Make the video", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Text("Every scene AI-animated in Google Flow. More credits = more and better clips.", color = Muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(100, 200, 300, 400).forEach { c ->
                            Text("$c", Modifier.weight(1f).background(if (credits == c) Violet else Pastel, CircleShape).press { credits = c }
                                .padding(vertical = 9.dp), color = if (credits == c) Color.White else Ink, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Also make a Short (9:16)", Modifier.weight(1f), color = Ink, fontSize = 14.sp)
                        androidx.compose.material3.Switch(shorts, { shorts = it })
                    }
                    val making = stage == "making video" && vm.naatProgress?.optBoolean("running") != false
                    Text(if (making) "⏳ Making the video…" else "🎬 Make video ($credits credits max)", Modifier.fillMaxWidth()
                        .background(Brush.horizontalGradient(if (making) listOf(Muted, Muted) else listOf(Violet, Pink)), RoundedCornerShape(16.dp))
                        .press { if (!making) confirm = true }.padding(vertical = 14.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    vm.naatProgress?.let { VideoProgress(it) { vm.makeNaatVideo(it.optInt("budget", credits).takeIf { b -> b > 0 } ?: credits, shorts) } }
                    g.optJSONArray("videos")?.takeIf { it.length() > 0 }?.let {
                        Text("✅ Video ready — check it in Studio › Videos (waiting for your OK)", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.press { vm.screen = Screen.Videos })
                    }
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Make the video?", fontWeight = FontWeight.ExtraBold) },
        text = { Text("Claude makes every scene in Google Flow on the Mac (Chrome, the profile set in config.json) and spends up to $credits credits. It takes 1–3 hours; you'll get a notification and it waits for your OK before upload.") },
        confirmButton = { TextButton({ vm.makeNaatVideo(credits, shorts); confirm = false }) { Text("Start", fontWeight = FontWeight.ExtraBold) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
}

private fun mins(m: Int) = if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"

/** Everything about the running video: % and bar, every stage, clips, credits, time so far and time left, what Claude
 *  is doing now, and the full log. */
@Composable
private fun VideoProgress(p: org.json.JSONObject, onContinue: (() -> Unit)? = null) {
    var log by remember { mutableStateOf(false) }
    val pct = p.optInt("pct"); val running = p.optBoolean("running")
    val phases = p.optJSONArray("phases"); val cur = p.optInt("phase_no")
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1E1B3A), Color(0xFF0F3D3A))), RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (pct >= 100) "VIDEO READY" else if (running) "MAKING THE VIDEO" else "STOPPED", color = if (running || pct >= 100) Color(0xFFA3E635) else Color(0xFFFB7185),
                    fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Text("Stage $cur of ${phases?.length() ?: 9} · ${p.optString("phase")}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            Text("$pct%", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).background(Color.White.copy(alpha = .15f), CircleShape)) {
            Box(Modifier.fillMaxWidth((pct / 100f).coerceIn(.03f, 1f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFF34D399), Color(0xFFA3E635))), CircleShape))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box2("🎬 ${p.optInt("clips")}/${p.optInt("clips_total")}", "clips", Modifier.weight(1f))
            Box2("💳 ${p.optInt("credits_used")}/${p.optInt("budget")}", "credits", Modifier.weight(1f))
            Box2("⏱ ${mins(p.optInt("elapsed_min"))}", "so far", Modifier.weight(1f))
            Box2(if (pct >= 100) "✓" else "≈ ${mins(p.optInt("left_min"))}", "left", Modifier.weight(1f))
        }
        Text("Now: " + p.optString("now"), color = Color.White.copy(alpha = .9f), fontSize = 13.sp, lineHeight = 18.sp)
        if (phases != null) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until phases.length()) {
                val st = when { i + 1 < cur || pct >= 100 -> "✓"; i + 1 == cur -> "⏳"; else -> "○" }
                Text("$st  ${phases.optString(i)}", color = when (st) { "✓" -> Color(0xFFA3E635); "⏳" -> Color.White; else -> Color.White.copy(alpha = .45f) },
                    fontSize = 13.sp, fontWeight = if (st == "⏳") FontWeight.ExtraBold else FontWeight.Normal)
            }
        }
        if (p.optBoolean("limit")) Text("⏸ Paused: Claude reached its usage limit. Tap Continue when it resets; nothing already made is lost.",
            color = Color(0xFFFDE68A), fontSize = 12.sp, lineHeight = 16.sp)
        else if (!running && p.optString("result").isNotBlank()) Text(p.optString("result"), color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
        if (!running && pct < 100 && onContinue != null) Text("▶ Continue video", Modifier.fillMaxWidth().background(Color(0xFFA3E635), RoundedCornerShape(14.dp))
            .press(onContinue).padding(12.dp), color = Ink, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(if (log) "Hide full log" else "See full log (${p.optJSONArray("log")?.length() ?: 0} steps)", Modifier.press { log = !log }
            .background(Color.White.copy(alpha = .12f), CircleShape).padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (log) p.optJSONArray("log")?.let { a ->
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .3f), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in maxOf(0, a.length() - 80) until a.length()) {
                    val l = a.optString(i); val tool = l.drop(6).startsWith("▸")
                    Text(l, color = if (tool) Color(0xFFB4A2FF) else Color.White, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun Box2(v: String, label: String, modifier: Modifier) =
    Column(modifier.background(Color.White.copy(alpha = .08f), RoundedCornerShape(12.dp)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = .6f), fontSize = 10.sp)
    }
