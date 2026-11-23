package com.englishcoach.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Screen
import com.englishcoach.app.StudioPlan
import com.englishcoach.app.StudioShot
import org.json.JSONObject
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as spin

private val Red = Color(0xFFFF3D57)
private val Green = Color(0xFF1FA85A)
private val MODE_COLORS = listOf(Color(0xFF3BC9FF), Pink, Color(0xFFFFB020), Color(0xFF2BC46B), Violet)
private val MODE_TINTS = listOf(Color(0xFFDDF6FF), Color(0xFFFFE3EF), Color(0xFFFFF1D6), Color(0xFFE2F8E9), Color(0xFFEAE4FF))

/** Slides each block in a moment after the one before, like the home page. */
@Composable
private fun Pop(order: Int, content: @Composable () -> Unit) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(70L * order); show = true }
    AnimatedVisibility(show, enter = fadeIn(tween(300)) + slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) { it / 3 }) { content() }
}

@Composable
private fun StudioHero(vm: CoachViewModel, onAnalyze: () -> Unit, onWeek: () -> Unit) {
    val st = vm.studio
    val t = rememberInfiniteTransition(label = "hero")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "a")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "s")
    Column(Modifier.fillMaxWidth().shadow(22.dp, RoundedCornerShape(30.dp), spotColor = Red)
        .background(Brush.linearGradient(listOf(Color(0xFF2A1240), Color(0xFF5B1A4A).copy(alpha = .9f + shift * .1f), Color(0xFF1A1530)),
            start = androidx.compose.ui.geometry.Offset(0f, 600f * shift), end = androidx.compose.ui.geometry.Offset(900f, 0f)), RoundedCornerShape(30.dp))
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TODAY · ${st.date}", color = Lime, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(4.dp))
                Text(st.series.ifBlank { "Studio" }, color = Color.White, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (st.loading) "Loading…" else "${st.plans.size} stories ready to make", color = Color.White.copy(alpha = .7f), fontSize = 13.sp)
            }
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    spin(angle) { drawCircle(Brush.sweepGradient(RAINBOW), size.minDimension / 2 - 4.dp.toPx(), style = Stroke(4.dp.toPx())) }
                }
                Buddy(Red, Mood.Talking, Modifier.size(78.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("✨ Run analysis", Lime, Ink, onAnalyze)
            Pill("🗓 New week", Color.White, Ink, onWeek)
        }
        Row(Modifier.fillMaxWidth().press { st.openChrome("", "Chrome") }
            .background(Brush.horizontalGradient(listOf(Color(0xFF3BC9FF), Violet, Pink)), RoundedCornerShape(18.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            LineIcon(Icons.SCREEN, Color.White, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text("Open Chrome · Google Flow", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}

/** A colourful story card with its own bouncing buddy, like the role cards on the home page. */
@Composable
private fun StoryCard(p: StudioPlan, i: Int, onCreate: () -> Unit, onClick: () -> Unit) {
    val c = MODE_COLORS[i % MODE_COLORS.size]
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(1f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
    Column(Modifier.width(230.dp).height(300.dp).press(onClick).shadow(12.dp, RoundedCornerShape(28.dp), spotColor = c)
        .background(Brush.verticalGradient(listOf(MODE_TINTS[i % MODE_TINTS.size], Color.White)), RoundedCornerShape(28.dp)).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.Top) {
            Buddy(c, if (i % 2 == 0) Mood.Happy else Mood.Idle, Modifier.size(72.dp))
            Spacer(Modifier.weight(1f))
            p.quality?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text("$it", color = c, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("quality", color = Muted, fontSize = 10.sp)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(p.label, color = c, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text(p.title, color = Ink, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(p.story, color = Muted, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().scale(pulse).press(onCreate).background(Brush.horizontalGradient(listOf(Red, Pink)), CircleShape)
            .padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            LineIcon(Icons.PLAY, Color.White, size = 14.dp, filled = true)
            Spacer(Modifier.width(6.dp))
            Text("Create now" + (p.credits?.let { " · $it cr" } ?: ""), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

/** Card on the Mac tab that opens the YouTube Studio. */
@Composable
fun StudioCard(vm: CoachViewModel) {
    Row(Modifier.fillMaxWidth().press { vm.screen = Screen.Studio; vm.studio.load() }
        .background(lt(Color(0xFFFFE8EC)), RoundedCornerShape(26.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).background(Red, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            LineIcon(Icons.PLAY, Color.White, size = 22.dp, filled = true)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("YouTube Studio", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink)
            Text(if (vm.roman) "Story, Flow video aur upload control" else "Stories, Flow videos and uploads", fontSize = 12.sp, color = Muted)
        }
        LineIcon(Icons.ARROW, Ink, size = 20.dp, strokeWidth = 2.4f)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StudioScreen(vm: CoachViewModel) {
    val st = vm.studio
    val plan = st.plan
    var ask by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }  // confirm dialog
    var edit by remember { mutableStateOf("") }
    var chatText by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<com.englishcoach.app.StudioJob?>(null) }
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    DisposableEffect(Unit) { st.watchFlow(true); onDispose { st.watchFlow(false) } }
    // load every time the screen opens (from More, the assistant or a notification), and again once the Mac is found
    LaunchedEffect(vm.activeUrl) { st.load() }
    if (st.showLog) { FlowLogScreen(vm); return }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (plan != null) item { BackHeader(plan.label) { st.open = null } }
            else {
                item { StudioTop(vm) { vm.screen = Screen.Mac } }
                stickyHeader { StudioTabs(tab) { tab = it } }
            }

            st.flow?.let { f -> item { FlowCard(f, onAllow = { st.allowAndRetry(it) }, onContinue = { st.continueFlow() }, onTell = if (f.running) null else { t, x -> st.tellClaude(t, x) }) { st.showLog = true } } }
            st.status?.takeIf { it.running }?.let { j -> item { JobCard(j.task, j.pct, j.current, j.eta, j.log) } }
            st.message?.let { m -> item {
                Text(m, Modifier.fillMaxWidth().background(lt(Color(0xFFEAE4FF)), RoundedCornerShape(14.dp)).press { st.message = null }.padding(12.dp),
                    color = Ink, fontSize = 13.sp)
            } }
            st.error?.let { e -> item {
                Text("⚠ $e", Modifier.fillMaxWidth().background(lt(Color(0xFFFFE8EC)), RoundedCornerShape(14.dp)).padding(12.dp), color = Red, fontSize = 13.sp)
            } }

            if (plan == null) {
                if (tab == 0) item { Pop(0) { StudioHero(vm, onAnalyze = { ask = "Run today's analysis? It takes a few minutes." to { st.run("analyze") } },
                    onWeek = { ask = "Make a new weekly calendar?" to { st.run("calendar") } }) } }
                if (tab == 0) item {
                    LaunchedEffect(Unit) { vm.loadWaiting() }
                    val n = vm.waiting.count { it.choice.isEmpty() && it.status == "pending" }
                    if (n > 0) Pop(1) {
                        Row(Modifier.fillMaxWidth().press { vm.openVideo = null; vm.screen = com.englishcoach.app.Screen.Videos }
                            .background(Brush.horizontalGradient(listOf(Color(0xFF22C55E), Color(0xFF14B8A6))), RoundedCornerShape(22.dp)).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("✅", fontSize = 26.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("$n video${if (n > 1) "s" else ""} waiting for your OK", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text("Watch, then upload, make Shorts or ask for a fix", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                            }
                            Text("→", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                if (tab == 2) item { Pop(1) { StatsCard(vm) } }
                if (tab == 0) item { Pop(1) { AutopilotCard(vm) } }
                if (tab == 0 || tab == 1) item { Pop(1) {
                    Row(Modifier.fillMaxWidth().press { vm.screen = com.englishcoach.app.Screen.Rules }
                        .background(Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFDB2777))), RoundedCornerShape(22.dp)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("🎯", fontSize = 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Video rules", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Text("Video looks AI or bad? Tell Claude, it writes rules for every next video", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                        }
                        Text("→", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    }
                } }
                if (tab == 3) item { Pop(1) { CreditsCard(st) } }
                if (tab == 1) item { Pop(1) {
                    Row(Modifier.fillMaxWidth().press { vm.naatOpen = null; vm.screen = com.englishcoach.app.Screen.Naat }
                        .background(Brush.horizontalGradient(listOf(Color(0xFF0F766E), Color(0xFF14532D))), RoundedCornerShape(22.dp)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("🕌", fontSize = 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Naat & song studio", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Text("Tell the idea → lyrics + Suno prompt → import song → premium video", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                        }
                        Text("→", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    }
                } }
                if (tab == 0) item { Pop(1) { Text("Today's stories", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink) } }
                if (tab == 0) item {
                    Pop(2) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            st.plans.forEachIndexed { i, p ->
                                StoryCard(p, i, onCreate = { ask = createText(p) to { st.startFlow(p.mode) } }) { st.open = p.mode; st.loadPipeline(p.mode) }
                            }
                        }
                    }
                }
                if (tab == 1 && st.week.isNotEmpty()) item {
                    Pop(3) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("This week · ${st.series}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                st.week.forEachIndexed { i, d ->
                                    val c = MODE_COLORS[i % MODE_COLORS.size]
                                    Column(Modifier.width(140.dp).card(20).padding(12.dp)) {
                                        Text(d.day, Modifier.background(c.copy(alpha = .18f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                                            color = c, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                        Spacer(Modifier.height(6.dp))
                                        Text(d.title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                if (tab == 1) item { Section("Open Chrome on the Mac") }
                if (tab == 1) item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        st.profiles.forEach { (dir, name) ->
                            val arbaz = name.contains("arbaz", true)
                            Text(name, Modifier.press { st.openChrome(dir, name) }
                                .background(if (arbaz) InkOnLight else Surface, CircleShape).padding(horizontal = 14.dp, vertical = 9.dp),
                                color = if (arbaz) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                if (tab == 1) item { Section("Flow queue") }
                if (tab == 1 && st.queue.isEmpty()) item { Text("Nothing queued", color = Muted, fontSize = 13.sp) }
                if (tab == 1) items(st.queue.take(8)) { j ->
                    Row(Modifier.fillMaxWidth().press { pick = j }.card(18).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(j.title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${j.date} · ${j.mode}" + (if (j.note.isNotBlank()) " · ${j.note}" else ""), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val c = when (j.status) { "done" -> Green; "queued" -> Violet; else -> Color(0xFFE08A00) }
                        Text(j.status, Modifier.background(c.copy(alpha = .14f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp), color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        LineIcon(Icons.ARROW, Muted, size = 16.dp)
                    }
                }

                if (tab == 3) item { Section("Upload schedule") }
                if (tab == 3) item { ScheduleCard(st.scheduleOn, st.longTime, st.shortTime) { on, l, s -> st.saveSchedule(on, l, s) } }
            } else {
                item {
                    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(24.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(plan.title, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            plan.quality?.let { Tag("Quality $it", Green) }
                            plan.credits?.let { Tag("$it credits", Violet) }
                            Tag("${plan.shots.size} shots", Muted)
                        }
                        Text(plan.story, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
                        if (plan.why.isNotBlank()) Text(plan.why, color = Muted, fontSize = 12.sp)
                        plan.warnings.forEach { Text("• $it", color = Color(0xFFB86E00), fontSize = 12.sp) }
                    }
                }
                item { Section("Make the video") }
                item {
                    val m = st.mode(plan.mode)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Action("1 · Images", Modifier.weight(1f)) { ask = "Make the keyframe images for this plan?" to { st.run("images", m) } }
                            Action("2 · Veo clips", Modifier.weight(1f)) { ask = "Make Veo clips? This uses credits (~${plan.credits ?: "?"})." to { st.run("clips", m) } }
                            Action("3 · QC", Modifier.weight(1f)) { st.run("qc", m) }
                        }
                        Row(Modifier.fillMaxWidth().press {
                            ask = createText(plan) to { st.startFlow(plan.mode) }
                        }.background(Brush.horizontalGradient(listOf(Red, Pink)), RoundedCornerShape(18.dp)).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            LineIcon(Icons.SPARK, Color.White, size = 18.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Create now", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Action("Add to queue", Modifier.weight(1f)) { st.run("create", m, longJob = false) }
                            Action("Send for review", Modifier.weight(1f)) { ask = "Send this plan to Discord for review?" to { st.run("review", m, longJob = false) } }
                        }
                    }
                }
                item { Section("Change the story") }
                item {
                    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Input(edit, "e.g. make it funnier, add a counting song") { edit = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Action("AI edit", Modifier.weight(1f), dark = true) {
                                if (edit.isNotBlank()) { st.run("edit", st.mode(plan.mode).put("instruction", edit)); edit = "" }
                            }
                            Action("Auto-fix", Modifier.weight(1f)) { st.run("autofix", st.mode(plan.mode)) }
                            Action("New plan", Modifier.weight(1f)) { ask = "Replace this plan with a new one?" to { st.run("regen", st.mode(plan.mode).put("note", edit)) } }
                        }
                    }
                }
                item { Section("Shots") }
                items(plan.shots, key = { it.n }) { ShotCard(it) }
            }

            if (plan != null || tab == 3) item { Section("Ask Studio AI") }
            if (plan != null || tab == 3) items(st.chat.toList()) { (role, text) ->
                val me = role == "user"
                Box(Modifier.fillMaxWidth(), contentAlignment = if (me) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(text, Modifier.widthIn(max = 300.dp).background(if (me) Violet else Surface, RoundedCornerShape(16.dp)).padding(12.dp),
                        color = if (me) Color.White else Ink, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            if (plan != null || tab == 3) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Input(chatText, if (st.chatBusy) "Thinking…" else "Ask about today's videos") { chatText = it } }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(46.dp).background(InkOnLight, CircleShape).press { st.ask(chatText); chatText = "" }, contentAlignment = Alignment.Center) {
                        LineIcon(Icons.ARROW, Color.White, size = 20.dp)
                    }
                }
            }
        }
    }

    pick?.let { j ->
        val inToday = j.date == st.date && st.plans.any { it.mode == j.mode }
        AlertDialog(onDismissRequest = { pick = null },
            title = { Text(j.title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Status" to j.status, "Date" to j.date, "Type" to j.mode, "Added" to j.added, "Started" to j.started,
                        "Finished" to j.finished, "Credits" to j.credits, "Note" to j.note, "Why" to j.why)
                        .filter { it.second.isNotBlank() }
                        .forEach { (k, v) -> Row { Text(k, color = Muted, fontSize = 12.sp, modifier = Modifier.width(70.dp)); Text(v, color = Ink, fontSize = 13.sp) } }
                    Spacer(Modifier.height(4.dp))
                    if (inToday) Action("Open story", Modifier.fillMaxWidth(), dark = true) { st.open = j.mode; st.loadPipeline(j.mode); pick = null }
                    if (j.status == "in_progress" && st.flow != null) Action("See full progress", Modifier.fillMaxWidth(), dark = true) { st.showLog = true; pick = null }
                    if (j.folder.isNotBlank()) Action("Open folder on Mac", Modifier.fillMaxWidth()) { st.openFolder(j.folder); pick = null }
                    if (j.status == "queued" && inToday) Action("Create now (skip queue)", Modifier.fillMaxWidth(), dark = true) {
                        st.plans.firstOrNull { it.mode == j.mode }?.let { p -> ask = createText(p) to { st.startFlow(p.mode) } }; pick = null
                    }
                    if (j.status != "done" && j.status != "queued" && j.folder.isNotBlank() && st.flow?.status != "running")
                        Action("Finish this video (no new credits)", Modifier.fillMaxWidth(), dark = true) {
                            ask = "Finish \"${j.title}\"? Claude edits the clips that are already made and posts it to Discord for your OK." to { st.finish(j) }; pick = null
                        }
                    if (j.status == "queued") Action("Remove from queue", Modifier.fillMaxWidth()) {
                        ask = "Remove \"${j.title}\" from the queue?" to { st.removeQueued(j) }; pick = null
                    }
                }
            },
            confirmButton = { TextButton({ pick = null }) { Text("Close") } })
    }

    ask?.let { (text, go) ->
        AlertDialog(onDismissRequest = { ask = null }, text = { Text(text) },
            confirmButton = { TextButton({ go(); ask = null }) { Text("Yes") } },
            dismissButton = { TextButton({ ask = null }) { Text("Cancel") } })
    }
}

@Composable
private fun JobCard(task: String, pct: Int?, current: String?, eta: Int, log: List<String>) {
    Column(Modifier.fillMaxWidth().background(InkOnLight, RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(task, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            pct?.let { Text("$it%", color = Lime, fontWeight = FontWeight.ExtraBold) }
        }
        Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = .15f), CircleShape)) {
            Box(Modifier.fillMaxWidth(((pct ?: 5) / 100f).coerceIn(.03f, 1f)).fillMaxHeight().background(Lime, CircleShape))
        }
        Text(listOfNotNull(current, if (eta > 0) "~${eta / 60} min left" else null).joinToString(" · "), color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
        log.forEach { Text(it, color = Color.White.copy(alpha = .5f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

private fun createText(p: StudioPlan) =
    "Create \"${p.title}\"? Claude will make it in Google Flow on the Mac (about ${p.credits ?: "?"} credits, Approve only, max 2 characters per clip)."

@Composable
private fun PlanCard(p: StudioPlan, onCreate: () -> Unit, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().press(onClick).background(Surface, RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.label, Modifier.weight(1f), color = Violet, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            p.quality?.let { Tag("$it", Green) }
        }
        Text(p.title, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(p.story, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.press(onCreate).background(Brush.horizontalGradient(listOf(Red, Pink)), CircleShape)
            .padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            LineIcon(Icons.PLAY, Color.White, size = 14.dp, filled = true)
            Spacer(Modifier.width(6.dp))
            Text("Create video" + (p.credits?.let { " · $it cr" } ?: ""), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ShotCard(s: StudioShot) {
    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Shot ${s.n} · ${s.location}", Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Dot("img", s.keyframe); Dot("clip", s.clip); Dot("qc", s.qc)
        }
        Text(s.action, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        s.dialogue.forEach { Text("“$it”", color = Violet, fontSize = 12.sp) }
    }
}

@Composable
private fun ScheduleCard(on: Boolean, long: String, short: String, save: (Boolean, String, String) -> Unit) {
    var l by remember(long) { mutableStateOf(long) }
    var s by remember(short) { mutableStateOf(short) }
    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(20.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto upload", Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold)
            Switch(on, { save(it, l, s) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Long", color = Muted, fontSize = 12.sp); Box(Modifier.width(80.dp)) { Input(l, "17:00") { l = it } }
            Text("Short", color = Muted, fontSize = 12.sp); Box(Modifier.width(80.dp)) { Input(s, "12:00") { s = it } }
            Action("Save", Modifier.weight(1f), dark = true) { save(on, l, s) }
        }
    }
}

@Composable private fun Section(t: String) =
    Text(t, Modifier.padding(top = 8.dp), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)

@Composable private fun Tag(t: String, c: Color) =
    Text(t, Modifier.background(c.copy(alpha = .13f), CircleShape).padding(horizontal = 9.dp, vertical = 3.dp), color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold)

@Composable private fun Dot(t: String, done: Boolean) =
    Text(t, Modifier.padding(start = 4.dp).background(if (done) Green.copy(alpha = .15f) else Paper, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
        color = if (done) Green else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)

@Composable private fun Pill(t: String, bg: Color, fg: Color, onClick: () -> Unit) =
    Text(t, Modifier.press(onClick).background(bg, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp), color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp)

@Composable private fun Action(t: String, modifier: Modifier = Modifier, dark: Boolean = false, onClick: () -> Unit) =
    Box(modifier.press(onClick).background(if (dark) InkOnLight else Surface, RoundedCornerShape(14.dp))
        .then(if (dark) Modifier else Modifier.border(1.5.dp, Color(0xFFE4E0F2), RoundedCornerShape(14.dp))).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center) {
        Text(t, color = if (dark) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
    }

@Composable private fun Input(v: String, hint: String, onChange: (String) -> Unit) =
    Box(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(14.dp)).padding(12.dp)) {
        if (v.isEmpty()) Text(hint, color = Muted, fontSize = 13.sp)
        BasicTextField(v, onChange, Modifier.fillMaxWidth(), textStyle = TextStyle(color = Ink, fontSize = 13.sp))
    }

@Composable
private fun FlowCard(f: com.englishcoach.app.FlowProgress, onAllow: ((List<String>) -> Unit)? = null, onContinue: (() -> Unit)? = null,
                     onTell: ((String, Int) -> Unit)? = null, onOpen: () -> Unit) {
    val done = f.status == "done"; val failed = f.status == "error"
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(InkOnLight, Color(0xFF4A1530))), RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (done) "VIDEO READY" else if (failed) "STOPPED" else "CREATING VIDEO · ${f.minutes} min",
                    color = if (failed) Red else Lime, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Text(f.title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("${f.pct}%", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).background(Color.White.copy(alpha = .15f), CircleShape)) {
            Box(Modifier.fillMaxWidth((f.pct / 100f).coerceIn(.03f, 1f)).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Pink, Lime)), CircleShape))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Stat("Images", f.images, f.shots); Stat("Clips", f.clips, f.shots); Stat("QC", f.qc, f.shots)
        }
        if (f.phases.isNotEmpty()) Text("Stage ${f.phaseNo} of ${f.phases.size} · ${f.phase}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TimeBox("💳 ${f.creditsUsed}/${f.budget}", "credits"); TimeBox("⏱ ${mins(f.elapsedMin)}", "so far")
            TimeBox(if (done) "✓" else "≈ ${mins(f.leftMin)}", "left")
        }
        Text("Now: " + f.now, color = Color.White.copy(alpha = .8f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (f.limit) Text("⏸ Paused: Claude reached its usage limit. Tap Continue when it resets; the clips already made are kept.",
            color = Color(0xFFFDE68A), fontSize = 12.sp, lineHeight = 16.sp)
        if (!f.running && !done && onContinue != null) Row(Modifier.fillMaxWidth().press(onContinue).background(Color(0xFFA3E635), RoundedCornerShape(14.dp)).padding(12.dp),
            horizontalArrangement = Arrangement.Center) { Text("▶ Continue video", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
        if (f.status != "running" && f.denied.isNotEmpty() && onAllow != null) {
            Column(Modifier.fillMaxWidth().background(lt(Color(0xFFFFF3C4)), RoundedCornerShape(16.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Claude stopped: it needs your permission", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                f.denied.forEach { Text("• " + it.removePrefix("Bash(").removeSuffix(")").removeSuffix(":*"), color = Ink, fontSize = 12.sp) }
                Text("Allowed commands are remembered for later videos.", color = Muted, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth().press { onAllow(f.denied) }.background(Brush.horizontalGradient(listOf(Pink, Violet)), RoundedCornerShape(14.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.Center) {
                    Text("Allow & continue", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
        }
        if (onTell != null) TellClaude(onTell)
        Row(Modifier.fillMaxWidth().press(onOpen).background(Color.White.copy(alpha = .12f), RoundedCornerShape(14.dp)).padding(12.dp),
            horizontalArrangement = Arrangement.Center) {
            Text("See full progress", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

/** "Tell Claude": type (or use the keyboard mic) an instruction for the video job, with optional extra credits. */
@Composable
private fun TellClaude(onSend: (String, Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .1f), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("💬 Tell Claude", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        androidx.compose.material3.TextField(text, { text = it }, Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. Finish the video, use the extra credits", fontSize = 13.sp) }, maxLines = 4)
        Text("Extra Flow credits for this video", color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 20, 40, 60).forEach { n ->
                Text(if (n == 0) "None" else "+$n", Modifier.weight(1f).background(if (extra == n) Lime else Color.White.copy(alpha = .15f), CircleShape)
                    .press { extra = n }.padding(vertical = 8.dp), color = if (extra == n) Ink else Color.White, fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().press { if (text.isNotBlank() || extra > 0) { onSend(text.trim(), extra); text = ""; extra = 0 } }
            .background(Brush.horizontalGradient(listOf(Pink, Violet)), RoundedCornerShape(14.dp)).padding(12.dp),
            horizontalArrangement = Arrangement.Center) {
            Text(if (extra > 0) "Send · approve +$extra credits" else "Send", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun RowScope.Stat(name: String, n: Int, total: Int) =
    Column(Modifier.weight(1f).background(Color.White.copy(alpha = .08f), RoundedCornerShape(12.dp)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$n/$total", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Text(name, color = Color.White.copy(alpha = .6f), fontSize = 11.sp)
    }

/** Everything Claude has done so far for the video, newest at the bottom, refreshed every 3 s. */
@Composable
private fun FlowLogScreen(vm: CoachViewModel) {
    val st = vm.studio
    val f = st.flow
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(f?.log?.size) { f?.log?.size?.let { if (it > 0) list.animateScrollToItem(it + 1) } }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp), state = list,
        contentPadding = PaddingValues(top = 16.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { BackHeader("Full progress") { st.showLog = false } }
        if (f == null) { item { Text("No video is being made right now.", color = Muted) }; return@LazyColumn }
        item { FlowCard(f, onAllow = { st.allowAndRetry(it) }, onContinue = { st.continueFlow() }) { } }
        item { TellClaude { t, x -> st.tellClaude(t, x) } }
        if (f.phases.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                f.phases.forEachIndexed { i, name ->
                    val mark = when { i + 1 < f.phaseNo || f.status == "done" -> "✓"; i + 1 == f.phaseNo -> "⏳"; else -> "○" }
                    Text("$mark  $name", color = if (mark == "○") Muted else Ink, fontWeight = if (mark == "⏳") FontWeight.ExtraBold else FontWeight.Normal, fontSize = 13.sp)
                }
            }
        }
        items(f.log) { line ->
            val tool = line.drop(6).startsWith("▸")
            Row(Modifier.fillMaxWidth().background(if (tool) Paper else Surface, RoundedCornerShape(12.dp)).padding(10.dp)) {
                Text(line.take(5), color = Muted, fontSize = 11.sp, modifier = Modifier.width(42.dp))
                Text(line.drop(6), color = if (tool) Violet else Ink, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        if (f.result.isNotBlank()) item {
            Text(f.result, Modifier.fillMaxWidth().background(lt(Color(0xFFE6F7EE)), RoundedCornerShape(14.dp)).padding(12.dp), color = Ink, fontSize = 13.sp)
        }
    }
}

/** Credit guard: Flow balance, today's spending against the daily limit, pace until renewal, recent spends. */
@Composable
private fun CreditsCard(st: com.englishcoach.app.StudioModel) {
    val g = st.guard
    val left = g?.let { if (it.isNull("balance")) null else it.optInt("balance") } ?: st.creditsLeft
    val color = when { left == null -> Muted; left < 120 -> Color(0xFFE11D48); left < 300 -> Color(0xFFF59E0B); else -> Color(0xFF16A34A) }
    var more by remember { mutableStateOf(false) }
    var renewPick by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().card(22).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💳", fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Flow credits · guard", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(if (left == null) "Not checked yet" else "$left left", color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(if (st.creditsChecking) "Checking…" else "↻ Check now", Modifier.press { st.checkCredits() }
                .background(Pastel, CircleShape).padding(horizontal = 12.dp, vertical = 8.dp), color = Violet, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
        if (g != null) {
            val spent = g.optInt("today_spent") + g.optInt("running"); val limit = g.optInt("today_limit").coerceAtLeast(1)
            Text("Today: $spent / $limit credits" + if (g.optInt("running") > 0) " (incl. ${g.optInt("running")} in a running video)" else "",
                color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Box(Modifier.fillMaxWidth().height(8.dp).background(Pastel, CircleShape)) {
                Box(Modifier.fillMaxWidth((spent.toFloat() / limit).coerceIn(.02f, 1f)).fillMaxHeight()
                    .background(if (spent >= limit) Color(0xFFE11D48) else Color(0xFF16A34A), CircleShape))
            }
            Text("≈ ${g.optInt("videos_left")} videos left · renews ${g.optString("renews")} (${g.optInt("days_to_renew")} days) · " +
                "≈ ${g.optInt("per_day")} a day to last", color = Muted, fontSize = 12.sp)
            if (spent >= limit) Text("🛑 Today's limit is reached: new videos wait until tomorrow.", color = Color(0xFFE11D48), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Daily limit", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val cur = g.optJSONObject("settings")?.optInt("daily_limit") ?: 120
                listOf(60, 120, 180, 240).forEach { n ->
                    Text("$n", Modifier.weight(1f).background(if (cur == n) Violet else Pastel, CircleShape).press { st.setDailyLimit(n) }
                        .padding(vertical = 8.dp), color = if (cur == n) Color.White else Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("+60 today only", Modifier.background(lt(Color(0xFFFFF3C4)), CircleShape).press { st.allowExtra(60 + g.optInt("extra_today")) }
                    .padding(horizontal = 12.dp, vertical = 8.dp), color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Renews on day ${g.optJSONObject("settings")?.optInt("renew_day") ?: 1}", Modifier.background(Pastel, CircleShape)
                    .press { renewPick = true }.padding(horizontal = 12.dp, vertical = 8.dp), color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(if (more) "Hide" else "History", Modifier.press { more = !more }.padding(8.dp), color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            if (more) g.optJSONArray("events")?.let { ev ->
                for (k in 0 until minOf(ev.length(), 12)) ev.getJSONObject(k).let { e ->
                    Row { Text(e.optString("date").drop(5), color = Muted, fontSize = 12.sp, modifier = Modifier.width(48.dp))
                        Text(e.optString("what"), Modifier.weight(1f), color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${e.optInt("credits")}", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) }
                }
            }
        }
    }
    if (renewPick) androidx.compose.material3.AlertDialog(onDismissRequest = { renewPick = false },
        title = { Text("Which day of the month do your Flow credits renew?", fontWeight = FontWeight.ExtraBold) },
        text = {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..28).forEach { d -> Text("$d", Modifier.size(38.dp).background(Pastel, CircleShape).press { st.setRenewDay(d); renewPick = false }
                    .padding(top = 9.dp), color = Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold) }
            }
        }, confirmButton = { androidx.compose.material3.TextButton({ renewPick = false }) { Text("Close") } })
}

private fun mins(m: Int) = if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"

@Composable
private fun RowScope.TimeBox(v: String, label: String) =
    Column(Modifier.weight(1f).background(Color.White.copy(alpha = .08f), RoundedCornerShape(12.dp)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = .6f), fontSize = 10.sp)
    }

/** Autopilot day: on/off, daily time, credit budget, today's steps and Run now. */
@Composable
private fun AutopilotCard(vm: CoachViewModel) {
    LaunchedEffect(Unit) { vm.loadAutopilot() }
    val a = vm.autopilot ?: return
    val s = a.optJSONObject("settings")
    val on = s?.optBoolean("on") ?: true
    val time = s?.optString("time") ?: "08:45"
    val credits = s?.optInt("credits") ?: 60
    var askRun by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF7C3AED))), RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🤖", fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Autopilot day", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(if (on) "Every day at ${label(time)} · up to $credits credits · you only tap ✅" else "Off: videos are made only when you start them",
                    color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
            }
            androidx.compose.material3.Switch(on, { vm.saveAutopilot(it, time, credits) })
        }
        Text("Today", color = Color.White.copy(alpha = .7f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        a.optJSONArray("steps")?.let { st ->
            for (i in 0 until st.length()) {
                val x = st.getJSONObject(i)
                val mark = when { x.optBoolean("done") -> "✓"; x.optBoolean("now") -> "⏳"; else -> "○" }
                Text("$mark  ${x.optString("name")}", color = if (mark == "○") Color.White.copy(alpha = .5f) else Color.White,
                    fontWeight = if (mark == "⏳") FontWeight.ExtraBold else FontWeight.Normal, fontSize = 13.sp)
            }
        }
        if (a.optString("title").isNotBlank()) Text("🎬 " + a.optString("title") + (a.optString("note").takeIf { it.isNotBlank() }?.let { "\n" + it } ?: ""),
            color = Color.White.copy(alpha = .9f), fontSize = 12.sp, lineHeight = 16.sp)
        a.optJSONArray("waiting")?.takeIf { it.length() > 0 }?.let { w ->
            Text("✅ ${w.length()} waiting for your OK →", Modifier.press { vm.screen = com.englishcoach.app.Screen.Videos }
                .background(Color.White.copy(alpha = .18f), CircleShape).padding(horizontal = 12.dp, vertical = 7.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("⏰ ${label(time)}", Modifier.background(Color.White.copy(alpha = .18f), CircleShape).press {
                val (h, m) = time.split(":").map { it.toInt() }
                android.app.TimePickerDialog(ctx, { _, hh, mm -> vm.saveAutopilot(on, "%02d:%02d".format(hh, mm), credits) }, h, m, false).show()
            }.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            listOf(60, 80, 120).forEach { c ->
                Text("$c cr", Modifier.background(if (credits == c) Color.White else Color.White.copy(alpha = .18f), CircleShape)
                    .press { vm.saveAutopilot(on, time, c) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (credits == c) Color(0xFF312E81) else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("▶ Run now", Modifier.background(Color(0xFFA3E635), CircleShape).press { askRun = true }
                .padding(horizontal = 12.dp, vertical = 8.dp), color = Color(0xFF1E1B3A), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
        vm.autopilotMsg?.let { Text(it, Modifier.press { vm.autopilotMsg = null }, color = Color(0xFFFDE68A), fontSize = 12.sp) }
    }
    if (askRun) androidx.compose.material3.AlertDialog(onDismissRequest = { askRun = false },
        title = { Text("Make today's video now?", fontWeight = FontWeight.ExtraBold) },
        text = { Text("Claude makes today's planned video in Google Flow (up to $credits credits). If today's video is already made or being made, nothing new starts.") },
        confirmButton = { androidx.compose.material3.TextButton({ vm.runAutopilot(); askRun = false }) { Text("Start", fontWeight = FontWeight.ExtraBold) } },
        dismissButton = { androidx.compose.material3.TextButton({ askRun = false }) { Text("Cancel") } })
}

private fun big(n: Int) = when { n >= 1_000_000 -> "%.1fM".format(n / 1e6); n >= 1000 -> "%.1fK".format(n / 1e3); else -> "$n" }

/** YouTube at a glance: subscribers, views, growth, Shorts vs videos, top and newest videos, and today's AI tip. */
@Composable
private fun StatsCard(vm: CoachViewModel) {
    val st = vm.studio
    LaunchedEffect(Unit) { if (st.stats == null) st.loadStats() }
    val a = st.stats
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var more by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFDC2626), Color(0xFF9F1239))), RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📊", fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a?.optJSONObject("channel")?.optString("title")?.let { "$it on YouTube" } ?: "YouTube", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(if (st.statsLoading) "Loading…" else a?.optString("updated")?.let { "Updated $it" } ?: "", color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
            }
            Text("↻", Modifier.press { st.loadStats(true) }.background(Color.White.copy(alpha = .2f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
        if (a == null) { Text(if (st.statsLoading) "Reading your channel…" else "Couldn't load YouTube numbers.", color = Color.White); return@Column }
        val ch = a.optJSONObject("channel")!!
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SBox("👥 ${big(ch.optInt("subs"))}", "subscribers", Modifier.weight(1f))
            SBox("👁 ${big(ch.optInt("views"))}", "total views", Modifier.weight(1f))
            SBox("🎬 ${ch.optInt("videos")}", "videos", Modifier.weight(1f))
        }
        val g1 = a.optJSONObject("growth_1d"); val g7 = a.optJSONObject("growth_7d")
        if (a.optString("analytics") == "on") Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SBox("${big(a.optInt("views_7d"))}", "views · 7 days", Modifier.weight(1f))
            SBox("${big(a.optInt("minutes_7d") / 60)} h", "watch time · 7 d", Modifier.weight(1f))
            SBox("${a.optInt("avg_view_secs")} s", "avg watched", Modifier.weight(1f))
        } else Text((g1?.let { "Since ${it.optString("since")}: +${it.optInt("views")} views, +${it.optInt("subs")} subscribers. " } ?: "Daily growth starts showing from tomorrow. ") +
            "Watch time needs the YouTube Analytics API turned on.", color = Color.White.copy(alpha = .85f), fontSize = 12.sp, lineHeight = 16.sp)
        a.optJSONArray("daily")?.takeIf { it.length() > 1 }?.let { d ->
            val mx = (0 until d.length()).maxOf { d.getJSONObject(it).optInt("views") }.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                for (i in 0 until d.length()) Box(Modifier.weight(1f).fillMaxHeight((d.getJSONObject(i).optInt("views").toFloat() / mx).coerceAtLeast(.04f))
                    .background(lt(Color.White.copy(alpha = .8f)), RoundedCornerShape(2.dp)))
            }
        }
        Text("Shorts: ${a.optInt("shorts")} · avg ${a.optInt("shorts_avg")} views   |   Videos: ${a.optInt("longs")} · avg ${a.optInt("longs_avg")} views",
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("💡 Today's tip", color = Color(0xFF9F1239), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            Text(a.optString("tip"), color = Ink, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Text(if (more) "Hide videos" else "Top & newest videos", Modifier.press { more = !more }.background(Color.White.copy(alpha = .2f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 7.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        if (more) {
            listOf("🏆 Top" to "top", "🆕 Newest" to "newest").forEach { (head, key) ->
                Text(head, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                a.optJSONArray(key)?.let { arr -> for (i in 0 until arr.length()) arr.getJSONObject(i).let { v ->
                    Row(Modifier.fillMaxWidth().press {
                        ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://youtu.be/" + v.optString("id"))))
                    }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (v.optBoolean("short")) "▮" else "▬", color = Color.White.copy(alpha = .7f), fontSize = 12.sp, modifier = Modifier.width(16.dp))
                        Text(v.optString("title"), Modifier.weight(1f), color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("👁 ${big(v.optInt("views"))}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                } }
            }
        }
        if (a.optString("analytics") == "disabled") Text("Turn on watch time →", Modifier.press {
            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://console.developers.google.com/apis/api/youtubeanalytics.googleapis.com/overview")))
        }, color = Color(0xFFFDE68A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun SBox(v: String, label: String, modifier: Modifier) =
    Column(modifier.background(Color.White.copy(alpha = .15f), RoundedCornerShape(12.dp)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = .75f), fontSize = 10.sp)
    }

/** The top of the Studio page: a slowly moving gradient with floating bubbles, Buddy, the channel and quick numbers. */
@Composable
private fun StudioTop(vm: CoachViewModel, onBack: () -> Unit) {
    val st = vm.studio
    val t = rememberInfiniteTransition(label = "top")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse), label = "shift")
    val bob by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val ch = st.stats?.optJSONObject("channel")
    LaunchedEffect(Unit) { if (st.stats == null) st.loadStats() }
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(Color(0xFFFF3D6E), Color(0xFF7C3AED), Color(0xFF06B6D4)),
            start = androidx.compose.ui.geometry.Offset(0f, 600f * shift), end = androidx.compose.ui.geometry.Offset(900f, 600f * (1 - shift))))) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {          // floating bubbles
            for (i in 0 until 9) {
                val x = size.width * ((i * 0.13f + shift * (0.08f + i * 0.01f)) % 1f)
                val y = size.height * ((0.15f + i * 0.11f + shift * 0.3f) % 1f)
                drawCircle(Color.White.copy(alpha = 0.08f + (i % 3) * 0.04f), radius = 12f + i * 7f, center = androidx.compose.ui.geometry.Offset(x, y))
            }
        }
        Row(Modifier.padding(16.dp).align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Color.White.copy(alpha = .22f), CircleShape).press(onBack), contentAlignment = Alignment.Center) {
                Text("‹", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("YouTube Studio", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text("▶ " + (ch?.optString("title")?.ifBlank { null } ?: "Your channel"), color = Color.White.copy(alpha = .9f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Buddy(Color(0xFFFFB347), Mood.Happy, Modifier.size(86.dp).align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp).offset(y = bob.dp))
        Row(Modifier.align(Alignment.BottomStart).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TopChip("👥", ch?.optInt("subs")?.let { big(it) } ?: "…", "subs")
            TopChip("👁", ch?.optInt("views")?.let { big(it) } ?: "…", "views")
            TopChip("💳", st.creditsLeft?.toString() ?: "…", "credits")
            val waiting = vm.waiting.count { it.choice.isEmpty() && it.status == "pending" }
            if (waiting > 0) TopChip("✅", "$waiting", "waiting")
        }
    }
}

@Composable
private fun TopChip(icon: String, value: String, label: String) =
    Column(Modifier.background(Color.White.copy(alpha = .2f), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon $value", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Text(label, color = Color.White.copy(alpha = .85f), fontSize = 10.sp)
    }

/** Four tabs with a sliding, bouncy highlight; it sticks to the top while you scroll. */
@Composable
private fun StudioTabs(tab: Int, onTab: (Int) -> Unit) {
    val tabs = listOf("🏠" to "Today", "🎬" to "Make", "📈" to "Growth", "⚙️" to "Plan")
    BoxWithConstraints(Modifier.fillMaxWidth().background(Paper).padding(vertical = 8.dp)) {
        val w = maxWidth / tabs.size
        val x by animateDpAsState(w * tab, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "tab")
        Box(Modifier.fillMaxWidth().height(52.dp).card(26)) {
            Box(Modifier.offset(x = x).width(w).fillMaxHeight().padding(4.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFFF3D6E))), RoundedCornerShape(22.dp)))
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { i, (icon, name) ->
                    val on = tab == i
                    val sc by animateFloatAsState(if (on) 1.08f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "sc")
                    Row(Modifier.weight(1f).fillMaxHeight().press { onTab(i) }.scale(sc), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(icon, fontSize = 15.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(name, color = if (on) Color.White else Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
