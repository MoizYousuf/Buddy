package com.englishcoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.CoachViewModel.Routine
import com.englishcoach.app.CoachViewModel.Step
import com.englishcoach.app.Screen

private val COLORS = listOf(listOf(Color(0xFF6D5BFF), Color(0xFFB45CFF)), listOf(Color(0xFFFF6B9D), Color(0xFFFFB347)),
    listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)), listOf(Color(0xFF22C55E), Color(0xFF14B8A6)), listOf(Color(0xFF1E1B3A), Color(0xFF4A1530)))

private val LABEL = mapOf("open_app" to "Open app", "quit_app" to "Close app", "open_url" to "Open website", "chrome" to "Chrome profile",
    "volume" to "Volume", "mute" to "Mute", "say" to "Mac says", "display_off" to "Screen off", "lock" to "Lock Mac", "wait" to "Wait",
    "agent" to "Ask the agent")

private fun stepText(s: Step) = (LABEL[s.type] ?: s.type) + if (s.arg.isNotBlank()) ": ${s.arg}" else ""

/** Mac tab: one tap runs a routine; "Edit" opens the editor. */
@Composable
fun RoutinesCard(vm: CoachViewModel) {
    LaunchedEffect(Unit) { vm.loadRoutines() }
    var confirm by remember { mutableStateOf<Routine?>(null) }
    Column(Modifier.fillMaxWidth().card(24).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚡ Routines", Modifier.weight(1f), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text("Edit", Modifier.press { vm.editing = null; vm.screen = Screen.Routines }.padding(6.dp), color = Violet, fontWeight = FontWeight.ExtraBold)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            vm.routines.forEachIndexed { i, r ->
                Column(Modifier.width(104.dp).background(Brush.linearGradient(COLORS[i % COLORS.size]), RoundedCornerShape(20.dp))
                    .press { if (r.steps.any { it.type in setOf("lock", "quit_app", "agent") }) confirm = r else vm.runRoutine(r) }.padding(12.dp)) {
                    Text(r.emoji, fontSize = 26.sp)
                    Text(r.name, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("${r.steps.size} steps", color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
                }
            }
            Column(Modifier.width(104.dp).background(Pastel, RoundedCornerShape(20.dp))
                .press { vm.editing = Routine("", "", "⚡", emptyList()); vm.screen = Screen.Routines }.padding(12.dp)) {
                Text("＋", fontSize = 26.sp, color = Violet)
                Text("New", color = Violet, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text("routine", color = Muted, fontSize = 11.sp)
            }
        }
        vm.routineMsg?.let { Text(it, Modifier.press { vm.routineMsg = null }, color = Ink.copy(alpha = .8f), fontSize = 12.sp) }
    }
    confirm?.let { r ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Run ${r.emoji} ${r.name}?") },
            text = { Text(r.steps.joinToString("\n") { "• " + stepText(it) }) },
            confirmButton = { TextButton({ vm.runRoutine(r); confirm = null }) { Text("Run", fontWeight = FontWeight.ExtraBold) } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } })
    }
}

@Composable
fun RoutinesScreen(vm: CoachViewModel) {
    LaunchedEffect(Unit) { vm.loadRoutines() }
    val e = vm.editing
    if (e != null) { RoutineEditor(vm, e); return }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("Routines") { vm.screen = Screen.Mac } }
        vm.routineMsg?.let { m -> item { Text(m, color = Ink.copy(alpha = .8f), fontSize = 13.sp) } }
        itemsIndexed(vm.routines) { i, r ->
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(COLORS[i % COLORS.size]), RoundedCornerShape(22.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${r.emoji}  ${r.name}", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("Edit", Modifier.press { vm.editing = r }.background(Color.White.copy(alpha = .25f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("▶ Run", Modifier.press { vm.runRoutine(r) }.background(Color.White, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
                r.steps.forEach { Text("• " + stepText(it), color = Color.White.copy(alpha = .9f), fontSize = 13.sp) }
            }
        }
        item {
            Text("＋ New routine", Modifier.fillMaxWidth().background(Pastel, RoundedCornerShape(18.dp))
                .press { vm.editing = Routine("", "", "⚡", emptyList()) }.padding(16.dp), color = Violet, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun RoutineEditor(vm: CoachViewModel, start: Routine) {
    var name by rememberSaveable { mutableStateOf(start.name) }
    var emoji by rememberSaveable { mutableStateOf(start.emoji) }
    val steps = remember { mutableStateListOf<Step>().apply { addAll(start.steps) } }
    var adding by remember { mutableStateOf<Int?>(null) }  // index to edit, or -1 for a new step
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BackHeader(if (start.id.isEmpty()) "New routine" else "Edit ${start.name}") { vm.editing = null } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(emoji, { emoji = it.take(2) }, "⚡", Modifier.width(64.dp))
                Field(name, { name = it.take(30) }, "Name, e.g. Morning", Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("💼", "🎬", "🌙", "☀️", "🎵", "🎮", "📚", "🏃", "⚡").forEach { em ->
                    Text(em, Modifier.background(if (emoji == em) Violet else Pastel, CircleShape).press { emoji = em }.padding(8.dp), fontSize = 18.sp)
                }
            }
        }
        item { Text("Steps (run top to bottom)", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        itemsIndexed(steps) { i, s ->
            Row(Modifier.fillMaxWidth().card(16).press { adding = i }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", Modifier.size(26.dp).background(Violet, CircleShape).padding(top = 3.dp), color = Color.White,
                    fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.width(10.dp))
                Text(stepText(s), Modifier.weight(1f), color = Ink, fontSize = 14.sp)
                Text("↑", Modifier.press { if (i > 0) { val x = steps.removeAt(i); steps.add(i - 1, x) } }.padding(6.dp), color = Muted, fontWeight = FontWeight.ExtraBold)
                Text("↓", Modifier.press { if (i < steps.lastIndex) { val x = steps.removeAt(i); steps.add(i + 1, x) } }.padding(6.dp), color = Muted, fontWeight = FontWeight.ExtraBold)
                Text("✕", Modifier.press { steps.removeAt(i) }.padding(6.dp), color = Color(0xFFE11D48), fontWeight = FontWeight.ExtraBold)
            }
        }
        item {
            Text("＋ Add step", Modifier.fillMaxWidth().background(Pastel, RoundedCornerShape(16.dp)).press { adding = -1 }.padding(14.dp),
                color = Violet, fontWeight = FontWeight.ExtraBold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Save", Modifier.weight(1f).background(Brush.horizontalGradient(listOf(Violet, Pink)), RoundedCornerShape(16.dp))
                    .press { if (name.isNotBlank()) { vm.saveRoutine(Routine(start.id.ifEmpty { name.lowercase() }, name.trim(), emoji, steps.toList())); vm.editing = null } }
                    .padding(14.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (start.id.isNotEmpty()) Text("Delete", Modifier.background(lt(Color(0xFFFFE3EC)), RoundedCornerShape(16.dp))
                    .press { vm.saveRoutine(start, delete = true); vm.editing = null }.padding(14.dp), color = Color(0xFFE11D48), fontWeight = FontWeight.ExtraBold)
            }
        }
    }
    adding?.let { idx ->
        val old = if (idx >= 0) steps[idx] else null
        var type by remember { mutableStateOf(old?.type ?: "open_app") }
        var arg by remember { mutableStateOf(old?.arg ?: "") }
        val hint = vm.stepTypes.firstOrNull { it.first == type }?.second ?: ""
        AlertDialog(onDismissRequest = { adding = null }, title = { Text(if (old == null) "Add step" else "Edit step", fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (vm.stepTypes.map { it.first }.ifEmpty { LABEL.keys.toList() }).forEach { t ->
                            Text(LABEL[t] ?: t, Modifier.background(if (type == t) Violet else Pastel, CircleShape).press { type = t }
                                .padding(horizontal = 12.dp, vertical = 7.dp), color = if (type == t) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    if (hint != "(nothing)") Field(arg, { arg = it }, hint, Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton({
                val st = Step(type, if (hint == "(nothing)") "" else arg.trim())
                if (old == null) steps.add(st) else steps[idx] = st
                adding = null
            }) { Text(if (old == null) "Add" else "Save", fontWeight = FontWeight.ExtraBold) } },
            dismissButton = { TextButton({ adding = null }) { Text("Cancel") } })
    }
}

@Composable
private fun Field(v: String, on: (String) -> Unit, hint: String, modifier: Modifier) =
    Box(modifier.background(Surface, RoundedCornerShape(14.dp)).padding(12.dp)) {
        if (v.isEmpty()) Text(hint, color = Muted, fontSize = 14.sp)
        BasicTextField(v, on, Modifier.fillMaxWidth(), textStyle = TextStyle(color = Ink, fontSize = 15.sp), cursorBrush = SolidColor(Violet), singleLine = true)
    }
