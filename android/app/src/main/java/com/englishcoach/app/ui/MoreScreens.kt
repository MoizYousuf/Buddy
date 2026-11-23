package com.englishcoach.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.ROLES
import java.util.Locale

@Composable
fun Chip(text: String) {
    Text(text, Modifier.card(999).padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Ink)
}

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), Modifier.padding(start = 4.dp, top = 6.dp), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
}

private fun gb(bytes: Long) = String.format(Locale.US, "%.0f GB", bytes / 1e9)

/** Levels are reached by answers given: 0, 30, 90, 180 answers. */
@Composable
fun JourneyCard(vm: CoachViewModel) {
    val s = vm.strings
    vm.storeVersion
    val answers = vm.store.answersByDay.values.sum()
    val thresholds = listOf(0, 30, 90, 180)
    val stage = thresholds.indexOfLast { answers >= it }
    val next = thresholds.getOrNull(stage + 1)
    val pct = if (next == null) 100 else ((answers - thresholds[stage]) * 100 / (next - thresholds[stage]))
    val t = rememberInfiniteTransition(label = "path")
    // the mockup's dashes (6 on, 6 off) march along the path every 1.2 s
    val dash by t.animateFloat(0f, -24f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "dash")
    val halo by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "halo")
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    Column(Modifier.fillMaxWidth().card().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(s.journey, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Text("Level ${stage + 1} · $pct%", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        // Same geometry as the mockup: a 326 x 92 drawing scaled to the card width.
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(326f / 92f)) {
            val k = maxWidth / 326f
            val centers = listOf(24f to 62f, 120f to 46f, 210f to 40f, 302f to 50f)
            Canvas(Modifier.fillMaxSize()) {
                val sx = size.width / 326f
                val p = Path().apply {
                    moveTo(24 * sx, 62 * sx)
                    cubicTo(70 * sx, 10 * sx, 100 * sx, 10 * sx, 120 * sx, 46 * sx)
                    cubicTo(140 * sx, 82 * sx, 190 * sx, 86 * sx, 210 * sx, 40 * sx)
                    cubicTo(230 * sx, -6 * sx, 280 * sx, 14 * sx, 302 * sx, 50 * sx)
                }
                drawPath(p, Color(0xFFCFC4FF), style = Stroke(4 * sx, cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * sx, 6 * sx), dash * sx)))
            }
            centers.forEachIndexed { i, (cx, cy) ->
                val current = i == stage
                val d = if (current) 44f else 32f
                val pop by animateFloatAsState(if (appeared) 1f else 0.4f,
                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "pop$i")
                Box(Modifier.offset(x = k * (cx - d / 2), y = k * (cy - d / 2)).size(k * d).scale(pop), contentAlignment = Alignment.Center) {
                    if (current) Box(Modifier.fillMaxSize().scale(1f + 0.35f * halo).background(Violet.copy(alpha = 0.22f * (1 - halo)), CircleShape))
                    Box(Modifier.fillMaxSize().background(when { i < stage -> Lime; current -> Violet; else -> Color(0xFFEEEAFB) }, CircleShape),
                        contentAlignment = Alignment.Center) {
                        when {
                            i < stage -> LineIcon(Icons.CHECK, Ink, size = k * 16, strokeWidth = 3f)
                            current -> Text("${i + 1}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            else -> LineIcon(Icons.LOCK, Color(0xFF9A93B8), size = k * 14, strokeWidth = 2.5f)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            s.stages.forEachIndexed { i, name ->
                Text(name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (i == stage) Violet else Muted)
            }
        }
    }
}

@Composable
fun UsageBar(label: String, used: Long, total: Long, color: Color, detail: String) {
    val frac = if (total > 0) used.toFloat() / total else 0f
    val anim by animateFloatAsState(frac, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "usage")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { Text(label, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, color = Ink, fontSize = 16.sp)
            Text(detail, color = Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        Box(Modifier.fillMaxWidth().height(12.dp).background(Pastel, CircleShape)) {
            Box(Modifier.fillMaxWidth(anim).fillMaxHeight().background(if (frac > 0.9f) Pink else color, CircleShape))
        }
    }
}

@Composable
fun MacScreen(vm: CoachViewModel) {
    val s = vm.strings
    LaunchedEffect(Unit) { vm.refreshStats() }
    val st = vm.macStats
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(18.dp).padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.mac, Modifier.weight(1f), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            if (vm.paired) {
                LaunchedEffect(Unit) { vm.loadAlerts() }
                Text("🕘", Modifier.padding(end = 8.dp).press { vm.screen = com.englishcoach.app.Screen.Activity }.card(999)
                    .padding(horizontal = 13.dp, vertical = 7.dp), fontSize = 17.sp)
                Box(Modifier.padding(end = 10.dp)) {
                    Text("🔔", Modifier.press { vm.screen = com.englishcoach.app.Screen.Alerts }.card(999).padding(horizontal = 13.dp, vertical = 7.dp), fontSize = 17.sp)
                    if (vm.unreadAlerts > 0) Text("${vm.unreadAlerts}", Modifier.align(Alignment.TopEnd).background(Pink, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 1.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Text(s.refresh, Modifier.press { vm.refreshStats() }.card(999).padding(horizontal = 14.dp, vertical = 8.dp), color = Violet, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().background(InkOnLight, RoundedCornerShape(28.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(if (vm.macOnline == false) Pink else Lime, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                LineIcon(Icons.MAC, Ink)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(if (vm.macOnline == false) s.macOffline else "${s.macOnline} · ${if (vm.onWifi) "Wi-Fi" else "Tailscale"}", color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text(st?.let { "${it.chip} · CPU ${it.cpu ?: "–"}%" } ?: s.macOfflineHint, color = Color(0xFFB9B3D6), fontSize = 12.sp)
            }
        }
        if (st != null) {
            Column(Modifier.fillMaxWidth().card().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                UsageBar(s.storage, st.diskUsed, st.diskTotal, Violet, "${gb(st.diskTotal - st.diskUsed)} ${s.free}")
                UsageBar(s.memory, st.memUsed, st.memTotal, Color(0xFF3BC9FF), "${gb(st.memUsed)} / ${gb(st.memTotal)}")
                UsageBar("CPU", ((st.cpu ?: 0.0) * 10).toLong(), 1000, Lime, "${st.cpu ?: "–"}%")
            }
            Column(Modifier.fillMaxWidth().card().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.aiModels, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                st.models.forEach { (name, bytes) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(if (name in st.loaded) Color(0xFF2BC46B) else lt(Color(0xFFE4E0F5)), CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 14.sp)
                        if (name in st.loaded) Text(s.loadedNow + "  ", color = Color(0xFF1FA85A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(String.format(Locale.US, "%.1f GB", bytes / 1e9), color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }
        PairCard(vm)
        if (vm.paired) {
            Column(Modifier.fillMaxWidth().card(24).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏰", fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Wake Mac", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Text("Wake-on-LAN at home, Tailscale when away", color = Muted, fontSize = 12.sp)
                    }
                    Text("Wake", Modifier.press { vm.wakeMac() }.background(if (vm.macOnline == false) Pink else Violet, CircleShape)
                        .padding(horizontal = 16.dp, vertical = 9.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
                vm.waking?.let { Text(it, color = Ink.copy(alpha = .8f), fontSize = 12.sp) }
            }
            LaunchedEffect(Unit) { vm.loadAwake() }
            Row(Modifier.fillMaxWidth().card(24).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("☀️", fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Keep Mac awake 24/7", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(if (vm.keepAwake == false) "The Mac may sleep, and then the app can't reach it"
                         else "The screen can turn off, but the Mac never sleeps, so the app always reaches it",
                        color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
                androidx.compose.material3.Switch(vm.keepAwake ?: true, { vm.setAwake(it) }, enabled = vm.keepAwake != null)
            }
        }
        if (vm.paired) RoutinesCard(vm)
        if (vm.paired) Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))), RoundedCornerShape(24.dp))
            .press { vm.screen = com.englishcoach.app.Screen.Transfer }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📲", fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Clipboard & files", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text("Copy-paste and send photos/files both ways", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
            }
            Text("→", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (vm.paired) CleanerCard(vm)
        if (vm.paired) StudioCard(vm)
        if (vm.paired) StoreStudioCard(vm)
        if (vm.paired) MoneyCard(vm)
        val sub = if (vm.paired) s.openAssistant else s.comingSoon
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureTile(s.assistant, sub, Icons.MIC, Color(0xFFEAE4FF), Violet,
                Modifier.weight(1f).then(if (vm.paired) Modifier.press { vm.screen = com.englishcoach.app.Screen.Assistant } else Modifier))
            FeatureTile(s.screenShare, sub, Icons.SCREEN, Color(0xFFDDF6FF), Color(0xFF0E8FC2),
                Modifier.weight(1f).then(if (vm.paired) Modifier.press { vm.screen = com.englishcoach.app.Screen.Remote } else Modifier))
        }
    }
}

@Composable
fun FeatureTile(title: String, sub: String, icon: String, bg: Color, accent: Color, modifier: Modifier) {
    Column(modifier.height(124.dp).background(bg, RoundedCornerShape(26.dp)).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        LineIcon(icon, accent, size = 26.dp, strokeWidth = 2.2f)
        Column {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Ink)
            Text(sub, fontSize = 11.sp, color = Muted, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 15.sp)
        content()
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Pastel, CircleShape).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Text(o, Modifier.weight(1f).background(if (on) Violet else Color.Transparent, CircleShape).press { onSelect(i) }.padding(vertical = 10.dp),
                textAlign = TextAlign.Center, color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun AppSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Violet, checkedThumbColor = Color.White))
}

@Composable
fun SettingsScreen(vm: CoachViewModel) {
    val s = vm.strings
    var url by remember { mutableStateOf(vm.serverUrl) }
    var confirmReset by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(18.dp).padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(s.settings, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        val uiPrefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("ui", 0)
        Row(Modifier.fillMaxWidth().card(24).press { appDark = !appDark; uiPrefs.edit().putBoolean("dark", appDark).apply() }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🌙 Dark gold theme", fontWeight = FontWeight.ExtraBold, color = Ink, fontSize = 16.sp)
                Text("Poori app ka premium dark look", color = Muted, fontSize = 12.sp)
            }
            Box(Modifier.size(50.dp, 28.dp).background(if (appDark) Color(0xFFC9A227) else Paper, CircleShape).padding(3.dp),
                contentAlignment = if (appDark) Alignment.CenterEnd else Alignment.CenterStart) { Box(Modifier.size(22.dp).background(Color.White, CircleShape)) }
        }

        if (vm.paired) {
            var askRestore by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().card(24).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("☁️ Progress backup", fontWeight = FontWeight.ExtraBold, color = Ink, fontSize = 16.sp)
                Text(vm.backupInfo.ifBlank { "Your streak, XP and mistakes are copied to the Mac after each practice." },
                    color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Back up now") { vm.backupNow() }
                    Pill("Restore from Mac", outline = true) { askRestore = true }
                }
            }
            if (askRestore) androidx.compose.material3.AlertDialog(onDismissRequest = { askRestore = false },
                title = { Text("Restore from the Mac?", fontWeight = FontWeight.ExtraBold) },
                text = { Text("This replaces the progress on this phone with the last copy saved on the Mac.") },
                confirmButton = { androidx.compose.material3.TextButton({ vm.restoreBackup(); askRestore = false }) { Text("Restore") } },
                dismissButton = { androidx.compose.material3.TextButton({ askRestore = false }) { Text("Cancel") } })
        }

        Column(Modifier.fillMaxWidth().card(24).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📴 Offline mode", fontWeight = FontWeight.ExtraBold, color = Ink, fontSize = 16.sp)
            Text(if (vm.offlineReady) "A small AI model is on this phone: practice keeps working when the Mac is off (simpler answers than the Mac's)."
                 else "Download a small AI model (1.6 GB, once, from the Mac on home Wi-Fi) so practice works without the Mac.",
                color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
            vm.offlineDl?.let { Text(it, color = Violet, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            if (!vm.offlineReady) Pill(if (vm.offlineDl == null) "Download the model" else "Continue download") { vm.downloadOffline() }
            else {
                Text("Use the phone's model:", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 13.sp)
                val opts = listOf("auto" to "When the Mac is off", "on" to "Always", "off" to "Never")
                Segmented(opts.map { it.second }, opts.indexOfFirst { it.first == vm.offlinePref }.coerceAtLeast(0)) { vm.chooseOfflinePref(opts[it].first) }
                Text("Delete the model (frees 1.6 GB)", Modifier.press { vm.deleteOffline() }.padding(vertical = 4.dp), color = Muted, fontSize = 12.sp)
            }
        }

        SectionTitle(s.practiceSection)
        Column(Modifier.fillMaxWidth().card(24).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingRow(s.language) { LanguageToggle(vm) }
            Text(s.level, fontWeight = FontWeight.SemiBold, color = Ink)
            Segmented(s.levels, vm.level) { vm.updateLevel(it) }
            Text(s.questionsPerSession, fontWeight = FontWeight.SemiBold, color = Ink)
            val lengths = listOf(5, 8, 12)
            Segmented(lengths.map { "$it" }, lengths.indexOf(vm.sessionLength).coerceAtLeast(0)) { vm.updateSessionLength(lengths[it]) }
        }

        SectionTitle(s.cvSection)
        ResumeCard(vm)

        SectionTitle(s.voiceSection)
        Column(Modifier.fillMaxWidth().card(24).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingRow(s.speechSpeed) { Text(String.format(Locale.US, "%.1fx", vm.speechRate), color = Violet, fontWeight = FontWeight.ExtraBold) }
            Slider(vm.speechRate, { vm.updateSpeechRate((it * 10).toInt() / 10f) }, valueRange = 0.6f..1.4f,
                colors = SliderDefaults.colors(thumbColor = Violet, activeTrackColor = Violet))
            Text(s.speechEngine, fontWeight = FontWeight.SemiBold, color = Ink)
            Segmented(listOf(s.enginePhone, s.engineMac), if (vm.useWhisper) 1 else 0) { vm.updateWhisper(it == 1) }
            Text(s.engineNote, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
            SettingRow(s.autoSpeak) { AppSwitch(vm.autoSpeak) { vm.updateAutoSpeak(it) } }
            SettingRow(s.hapticsLabel) { AppSwitch(vm.haptics) { vm.updateHaptics(it) } }
        }

        SectionTitle(s.connection)
        Column(Modifier.fillMaxWidth().card(24).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.server, fontWeight = FontWeight.SemiBold, color = Ink)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Pill(s.save + " · " + s.checkConnection, Modifier.fillMaxWidth()) { vm.saveServer(url) }
        }
        MacStatus(vm)

        SectionTitle(s.dataSection)
        Pill(if (confirmReset) s.resetConfirm else s.resetProgress, Modifier.fillMaxWidth(), outline = true) {
            if (confirmReset) { vm.resetProgress(); confirmReset = false } else confirmReset = true
        }
        Text(s.about, Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun OnboardingScreen(vm: CoachViewModel) {
    val s = vm.strings
    var page by remember { mutableStateOf(0) }
    val colors = listOf(ROLES[0].color, ROLES[1].color, ROLES[3].color)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.5f))
        AnimatedContent(page, transitionSpec = {
            (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
        }, label = "onboarding") { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                    AiRing(false, Modifier.fillMaxSize())
                    Buddy(colors[p], if (p == 2) Mood.Happy else Mood.Talking, Modifier.size(190.dp))
                }
                Text(listOf(s.onb1Title, s.onb2Title, s.onb3Title)[p], textAlign = TextAlign.Center, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Text(listOf(s.onb1Text, s.onb2Text, s.onb3Text)[p], textAlign = TextAlign.Center, fontSize = 16.sp, lineHeight = 22.sp, color = Muted)
                if (p == 1) LanguageToggle(vm)
                if (p == 2) MacStatus(vm)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { i ->
                val w by animateFloatAsState(if (i == page) 28f else 8f, spring(Spring.DampingRatioMediumBouncy), label = "dot")
                Box(Modifier.height(8.dp).width(w.dp).background(if (i == page) Violet else Color(0xFFE4E0F5), CircleShape))
            }
        }
        Spacer(Modifier.height(20.dp))
        Pill(if (page < 2) s.next else s.letsGo, Modifier.fillMaxWidth()) { if (page < 2) page++ else vm.finishOnboarding() }
    }
}
