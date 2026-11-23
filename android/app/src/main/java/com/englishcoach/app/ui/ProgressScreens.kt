package com.englishcoach.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CATEGORY_NAMES
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Mistake
import com.englishcoach.app.Phase
import com.englishcoach.app.Screen
import com.englishcoach.app.customRole
import java.time.format.TextStyle as DateStyle
import java.util.Locale

private val BAR_COLORS = listOf(Color(0xFFCBBEFF), Color(0xFFFFC3DC), Color(0xFFB5EBFF), Color(0xFFD8F59A), Color(0xFFFFE29A), Color(0xFFCBBEFF), Violet)

@Composable
fun StatTile(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier.card(22).padding(14.dp)) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Muted)
    }
}

@Composable
fun ProgressScreen(vm: CoachViewModel) {
    val s = vm.strings
    vm.storeVersion  // redraw when the store changes
    val store = vm.store
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(18.dp).padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(s.progress, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        run {
            val w = store.week(0)
            Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF0EA5E9), Color(0xFF6366F1))), RoundedCornerShape(22.dp))
                .press { vm.screen = Screen.Weekly }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📈", fontSize = 26.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Your week", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text("${w.days}/7 days · ${w.answers} answers · +${w.xp} XP", color = Color.White.copy(alpha = .9f), fontSize = 13.sp)
                }
                Text("→", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("🔥 ${store.streak}", s.streak, Color(0xFFFF8A1F), Modifier.weight(1f))
            StatTile("${store.answersByDay.values.sum()}", s.answers, Violet, Modifier.weight(1f))
            StatTile("${store.fixedCount}", s.fixed, Color(0xFF1FA85A), Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().card().padding(18.dp)) {
            Text(s.thisWeek, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            Spacer(Modifier.height(14.dp))
            val week = store.lastWeek
            val max = (week.maxOf { it.second }).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                week.forEachIndexed { i, (day, n) ->
                    val h by animateFloatAsState(n / max.toFloat(), spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "bar")
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (n > 0) "$n" else "", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Bold)
                        Box(Modifier.fillMaxWidth().height((100 * h).dp.coerceAtLeast(6.dp))
                            .background(if (i == 6) Violet else BAR_COLORS[i], RoundedCornerShape(10.dp)))
                        Spacer(Modifier.height(6.dp))
                        Text(day.dayOfWeek.getDisplayName(DateStyle.NARROW, Locale.ENGLISH), fontSize = 11.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        val top = store.topCategories
        if (top.isNotEmpty()) Column(Modifier.fillMaxWidth().card().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(s.topMistakes, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            val max = top.first().second.toFloat()
            top.forEach { (cat, n) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row { Text(CATEGORY_NAMES[cat] ?: cat, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 14.sp)
                        Text("$n", color = Color(0xFFE0367A), fontWeight = FontWeight.ExtraBold) }
                    Box(Modifier.fillMaxWidth().height(8.dp).background(Pastel, CircleShape)) {
                        Box(Modifier.fillMaxWidth(n / max).fillMaxHeight().background(Pink, CircleShape))
                    }
                }
            }
        }
        NotebookCard(vm)
        store.mistakes.take(20).forEach { MistakeRow(it, vm.roman) }
    }
}

@Composable
fun NotebookCard(vm: CoachViewModel) {
    val s = vm.strings
    vm.storeVersion
    val due = vm.store.dueForReview.size
    Row(Modifier.fillMaxWidth().background(InkOnLight, RoundedCornerShape(26.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(s.notebook, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(if (due == 0) s.noMistakes else "$due ${s.toReview}", color = Color(0xFFB9B3D6), fontSize = 13.sp)
        }
        if (due > 0) Text(s.reviewNow, Modifier.press { vm.startReview() }.background(Lime, CircleShape).padding(horizontal = 16.dp, vertical = 10.dp),
            color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

@Composable
fun MistakeRow(m: Mistake, roman: Boolean) {
    Column(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(m.said, color = Color(0xFFC8205F), fontSize = 14.sp, textDecoration = TextDecoration.LineThrough)
        Text(m.correct, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        m.explanation?.let { Text(if (roman) it.roman else it.en, color = Muted, fontSize = 12.sp, lineHeight = 16.sp) }
        if (m.mastered) Text("✓", color = Color(0xFF1FA85A), fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun ReviewScreen(vm: CoachViewModel) {
    val s = vm.strings
    val item = vm.reviewItem
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).card(22).press { vm.screen = Screen.Progress }, contentAlignment = Alignment.Center) { LineIcon(Icons.BACK, Ink, strokeWidth = 2.4f) }
            Spacer(Modifier.width(12.dp))
            Text(s.reviewTitle, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        }
        if (item == null) {
            Spacer(Modifier.weight(1f))
            Buddy(Violet, Mood.Happy, Modifier.size(160.dp).align(Alignment.CenterHorizontally))
            Text(s.allDone, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
            Spacer(Modifier.weight(1f))
            return@Column
        }
        val mood = when (vm.reviewPassed) { true -> Mood.Happy; false -> Mood.Thinking; null -> if (vm.phase == Phase.Listening) Mood.Listening else Mood.Idle }
        Buddy(Violet, mood, Modifier.size(140.dp).align(Alignment.CenterHorizontally))
        Block(s.youSaidBefore, Color(0xFFFFF0F5), Color(0xFFC8205F)) { Text(item.said, fontSize = 17.sp, color = Ink) }
        item.explanation?.let { ex ->
            if (vm.roman) Text(ex.urdu, Modifier.fillMaxWidth(), style = TextStyle(textDirection = TextDirection.Rtl), fontSize = 16.sp, lineHeight = 28.sp, color = Ink)
            else Text(ex.en, fontSize = 14.sp, color = Muted)
        }
        Text(s.sayCorrect, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Violet)
        when (vm.reviewPassed) {
            true -> Text("${s.praise}  ✓ ${item.correct}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Color(0xFF1FA85A), fontWeight = FontWeight.ExtraBold)
            false -> Block(s.correct, Color(0xFFF2FBDD), Color(0xFF3F7A0E)) { Text(item.correct, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Ink) }
            null -> {}
        }
        Spacer(Modifier.weight(1f))
        if (vm.phase == Phase.Listening) VoiceWave(vm.micLevel, Modifier.fillMaxWidth().height(40.dp))
        Text(vm.notice ?: vm.heard, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Muted, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Text(s.hint, Modifier.press { vm.hearReview() }.padding(8.dp), color = Muted, fontWeight = FontWeight.Bold)
            MicButton(vm.phase == Phase.Listening, vm.micLevel) { vm.reviewMic() }
            Text(s.skipOne, Modifier.press { vm.skipReview() }.padding(8.dp), color = Muted, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ReportScreen(vm: CoachViewModel) {
    val s = vm.strings
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Buddy(vm.role.color, Mood.Happy, Modifier.size(150.dp).align(Alignment.CenterHorizontally))
        Text(s.report, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        val correct = vm.sessionAnswers - vm.sessionMistakes.size
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${vm.sessionAnswers}", s.answers, Violet, Modifier.weight(1f))
            StatTile("$correct", s.correct, Color(0xFF1FA85A), Modifier.weight(1f))
            StatTile("${vm.sessionMistakes.size}", s.mistakesThisSession.split(" ").first(), Pink, Modifier.weight(1f))
        }
        if (vm.role.title == "CV Interview") CvReport(vm)
        if (vm.sessionMistakes.isEmpty()) Text(s.perfectSession, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
        else {
            Text(s.mistakesThisSession, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            vm.sessionMistakes.forEach { MistakeRow(it, vm.roman) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(s.home, Modifier.weight(1f), outline = true) { vm.screen = Screen.Home }
            Pill(s.again, Modifier.weight(1f)) { vm.start(vm.role) }
        }
    }
}

@Composable
fun CustomRoleCard(vm: CoachViewModel) {
    val s = vm.strings
    var text by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val ready = text.isNotBlank()
    val sendScale by animateFloatAsState(if (ready) 1f else 0.6f, spring(Spring.DampingRatioMediumBouncy), label = "send")
    Column(Modifier.fillMaxWidth()
        .shadow(18.dp, RoundedCornerShape(30.dp), spotColor = Violet)
        .background(Brush.linearGradient(listOf(lt(Color(0xFFEDE6FF)), lt(Color(0xFFFFE6F1)), lt(Color(0xFFE2F7FF)))), RoundedCornerShape(30.dp))
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("✨ " + s.customRole, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Ink)
                Text(s.customSub, fontSize = 13.sp, color = Muted, lineHeight = 17.sp)
            }
            Buddy(Color(0xFFE040FB), if (ready) Mood.Happy else Mood.Talking, Modifier.size(64.dp))
        }
        // one-tap ideas
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.roleIdeas.forEach { idea ->
                val on = text == idea
                Text(idea, Modifier.press { text = idea }
                    .background(if (on) Violet else lt(Color.White.copy(alpha = 0.85f)), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                    color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(24.dp)).padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (text.isEmpty()) Text(s.customHint, color = Color(0xFFA59FC0), fontSize = 14.sp, lineHeight = 19.sp)
                BasicTextField(text, { text = it.take(300) }, Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
                    cursorBrush = SolidColor(Violet), maxLines = 4)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(48.dp).scale(sendScale)
                .background(if (ready) Violet else Color(0xFFE4E0F5), CircleShape)
                .then(if (ready) Modifier.press { vm.start(customRole(text.trim())) } else Modifier),
                contentAlignment = Alignment.Center) {
                LineIcon(Icons.ARROW, if (ready) Color.White else Color(0xFF9A93B8), size = 22.dp, strokeWidth = 2.5f)
            }
        }
    }
}

/** Weekly report: this week vs last week, repeated mistakes going up or down, and the daily reminder setting. */
@Composable
fun WeeklyScreen(vm: CoachViewModel) {
    vm.storeVersion
    val store = vm.store
    val w = store.week(0); val p = store.week(1)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var remindOn by remember { mutableStateOf(com.englishcoach.app.Reminders.on(ctx)) }
    var time by remember { mutableStateOf(com.englishcoach.app.Reminders.time(ctx)) }
    fun arrow(now: Int, before: Int, lowerIsBetter: Boolean = false) = when {
        before == 0 && now == 0 -> ""
        now == before -> " ="
        now > before -> if (lowerIsBetter) " ▲ (more)" else " ▲"
        else -> if (lowerIsBetter) " ▼ (fewer)" else " ▼"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackHeader("Your English week") { vm.screen = Screen.Progress }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${w.days}/7", "days" + arrow(w.days, p.days), Color(0xFFFF8A1F), Modifier.weight(1f))
            StatTile("${w.answers}", "answers" + arrow(w.answers, p.answers), Violet, Modifier.weight(1f))
            StatTile("+${w.xp}", "XP", Color(0xFF1FA85A), Modifier.weight(1f))
        }
        Text("Last week: ${p.days}/7 days · ${p.answers} answers", color = Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${w.made}", "new mistakes" + arrow(w.made, p.made, lowerIsBetter = true), Pink, Modifier.weight(1f))
            StatTile("${w.mastered}", "already fixed", Color(0xFF1FA85A), Modifier.weight(1f))
            StatTile("🔥 ${store.streak}", "streak", Color(0xFFFF8A1F), Modifier.weight(1f))
        }
        Text("Mistakes you repeat", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        val cats = (w.cats.keys + p.cats.keys).associateWith { (w.cats[it] ?: 0) to (p.cats[it] ?: 0) }
            .toList().sortedByDescending { it.second.first }.take(6)
        if (cats.isEmpty()) Text("No mistakes in the last two weeks. Great job! 🎉", color = Ink)
        cats.forEach { (c, nw) ->
            val (now, before) = nw
            Row(Modifier.fillMaxWidth().card(16).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(CATEGORY_NAMES[c] ?: c, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("This week $now · last week $before", color = Muted, fontSize = 12.sp)
                }
                Text(when { now < before -> "Better ▼"; now > before -> "More ▲"; else -> "Same" },
                    color = when { now < before -> Color(0xFF1FA85A); now > before -> Pink; else -> Muted }, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        }
        val worst = cats.firstOrNull { it.second.first > 0 }?.first
        if (worst != null) Text("💡 Focus this week: ${CATEGORY_NAMES[worst] ?: worst}. Review it in the Mistake notebook, then use it in a practice talk.",
            color = Ink, fontSize = 14.sp, lineHeight = 19.sp)
        if (store.dueForReview.isNotEmpty()) Pill("Review ${store.dueForReview.size} mistakes now") { vm.screen = Screen.Review }
        Column(Modifier.fillMaxWidth().card(20).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily reminder", color = Ink, fontWeight = FontWeight.ExtraBold)
                    Text("Only on days you haven't practised · weekly report on Sundays", color = Muted, fontSize = 12.sp)
                }
                androidx.compose.material3.Switch(remindOn, { remindOn = it; com.englishcoach.app.Reminders.set(ctx, it, time.first, time.second) })
            }
            val (h, m) = time
            Text("⏰ " + "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM"), Modifier.press {
                android.app.TimePickerDialog(ctx, { _, hh, mm -> time = hh to mm; com.englishcoach.app.Reminders.set(ctx, remindOn, hh, mm) }, h, m, false).show()
            }.background(Pastel, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp), color = Violet, fontWeight = FontWeight.Bold)
        }
    }
}

/** The interview coach's report: overall score and verdict, then each answer with score, STAR, what was missing,
 *  a better answer from the CV, and the English fix. */
@Composable
fun CvReport(vm: CoachViewModel) {
    LaunchedEffect(Unit) { if (vm.cvFeedback == null) vm.loadCvFeedback() }
    val f = vm.cvFeedback
    var open by remember { mutableStateOf<Int?>(null) }
    if (f == null || f.optString("status") == "working") {
        Column(Modifier.fillMaxWidth().card(20).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🧑‍💼 Your interview coach is reading your answers…", color = Ink, fontWeight = FontWeight.ExtraBold)
            Text("About a minute. The full report appears here.", color = Muted, fontSize = 13.sp)
        }
        return
    }
    if (f.optString("status") != "ready") { Text("Couldn't make the report: " + f.optString("error"), color = Pink); return }
    val o = f.optJSONObject("overall")
    if (o != null) Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF0E7490))), RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("INTERVIEW RESULT", color = Color(0xFFA7F3D0), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Text(o.optString("verdict"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text("${o.optInt("score")}", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            Text("/100", color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
        }
        Text(o.optString("summary"), color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
        o.optJSONArray("strengths")?.let { a -> for (i in 0 until a.length()) Text("✓ " + a.optString(i), color = Color(0xFFA7F3D0), fontSize = 13.sp) }
        o.optJSONArray("improve")?.let { a -> for (i in 0 until a.length()) Text("↗ " + a.optString(i), color = Color(0xFFFDE68A), fontSize = 13.sp) }
    }
    f.optJSONArray("answers")?.let { arr ->
        for (i in 0 until arr.length()) {
            val a = arr.getJSONObject(i); val sc = a.optInt("score")
            Column(Modifier.fillMaxWidth().card(18).press { open = if (open == i) null else i }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Q${i + 1}", color = Violet, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(34.dp))
                    Text(a.optString("q"), Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = if (open == i) 6 else 2)
                    Text("$sc/10", color = when { sc >= 8 -> Color(0xFF16A34A); sc >= 5 -> Color(0xFFF59E0B); else -> Pink }, fontWeight = FontWeight.ExtraBold)
                }
                if (open == i) {
                    if (a.optString("star").isNotBlank()) Text("⭐ STAR: " + a.optString("star"), color = Ink, fontSize = 12.sp)
                    if (a.optString("missing").isNotBlank()) Text("Missing: " + a.optString("missing"), color = Ink, fontSize = 13.sp)
                    if (a.optString("english").isNotBlank()) Text("🗣 English: " + a.optString("english"), color = Ink, fontSize = 13.sp)
                    Column(Modifier.fillMaxWidth().background(lt(Color(0xFFE6F7EE)), RoundedCornerShape(12.dp)).padding(10.dp)) {
                        Text("A better answer", color = Color(0xFF16A34A), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        Text(a.optString("better"), color = Ink, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    Pill("🔊 Hear it") { vm.voice.speak(a.optString("better"), java.util.Locale.US, flush = true) }
                } else Text("Tap for the better answer", color = Muted, fontSize = 11.sp)
            }
        }
    }
}
