package com.englishcoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Screen
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** The owner's own video rules: describe what looked wrong → Claude drafts rules → tick, edit, add. Every video job follows them. */
@Composable
fun RulesScreen(vm: CoachViewModel) {
    val scope = rememberCoroutineScope()
    var rules by remember { mutableStateOf(JSONArray()) }
    var problem by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("all") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<List<String>>(emptyList()) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    var summary by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    fun apply(r: JSONObject) { if (r.has("error")) msg = r.optString("error") else r.optJSONArray("rules")?.let { rules = it } }
    LaunchedEffect(Unit) { apply(vm.rules("/rules/list")) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("Video rules") { vm.screen = Screen.Studio } }
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(InkOnLight, Color(0xFF4A1530))), RoundedCornerShape(22.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What looked wrong?", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text("Write it in any language (or use the keyboard mic). Claude turns it into rules for the next videos.",
                    color = Color.White.copy(alpha = .75f), fontSize = 12.sp)
                TextField(problem, { problem = it }, Modifier.fillMaxWidth(), minLines = 3,
                    placeholder = { Text("e.g. Video AI lag rahi hai, Coco ke kaan badal jate hain", fontSize = 13.sp) })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("all" to "All videos", "kids" to "Kids", "naat" to "Naat").forEach { (k, n) ->
                        Text(n, Modifier.weight(1f).background(if (kind == k) Lime else Color.White.copy(alpha = .15f), CircleShape)
                            .press { kind = k }.padding(vertical = 8.dp), color = if (kind == k) Ink else Color.White,
                            fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
                Row(Modifier.fillMaxWidth().press {
                    if (busy || problem.isBlank()) return@press
                    busy = true; msg = ""; draft = emptyList()
                    scope.launch {
                        val r = vm.rules("/rules/draft", JSONObject().put("problem", problem).put("for", kind))
                        busy = false
                        if (r.has("error")) msg = r.optString("error") else {
                            val a = r.optJSONArray("rules") ?: JSONArray()
                            draft = List(a.length()) { a.getString(it) }; picked = draft.indices.toSet(); summary = r.optString("summary")
                        }
                    }
                }.background(Brush.horizontalGradient(listOf(Pink, Violet)), RoundedCornerShape(14.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.Center) {
                    Text(if (busy) "Claude is writing rules… (up to a minute)" else "✨ Make rules with Claude", color = Color.White,
                        fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
            }
        }
        if (msg.isNotBlank()) item { Text(msg, color = Color(0xFFE11D48), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        if (draft.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().card(22).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Claude's rules: tick the ones to keep", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                if (summary.isNotBlank()) Text(summary, color = Muted, fontSize = 12.sp)
                draft.forEachIndexed { i, t ->
                    Row(Modifier.fillMaxWidth().press { picked = if (i in picked) picked - i else picked + i }
                        .background(if (i in picked) lt(Color(0xFFEDE8FF)) else Paper, RoundedCornerShape(12.dp)).padding(10.dp)) {
                        Text(if (i in picked) "☑" else "☐", color = Violet, fontSize = 16.sp, modifier = Modifier.width(26.dp))
                        Text(t, color = Ink, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add ${picked.size} rules", Modifier.weight(1f).press {
                        val keep = draft.filterIndexed { i, _ -> i in picked }
                        if (keep.isEmpty()) return@press
                        scope.launch { apply(vm.rules("/rules/add", JSONObject().put("rules", JSONArray(keep)).put("for", kind))); draft = emptyList(); problem = ""; msg = "" }
                    }.background(Violet, RoundedCornerShape(14.dp)).padding(12.dp), color = Color.White, fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp, textAlign = TextAlign.Center)
                    Text("Discard", Modifier.press { draft = emptyList() }.background(Paper, RoundedCornerShape(14.dp)).padding(12.dp),
                        color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
        item { Text("Your rules (${rules.length()}) · every new video follows the ticked ones", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        if (rules.length() == 0) item { Text("No rules yet. The base style rules are already in the prompt bible.", color = Muted, fontSize = 13.sp) }
        items(rules.length()) { i ->
            val r = rules.getJSONObject(i); val on = r.optBoolean("on", true); val id = r.optString("id")
            Column(Modifier.fillMaxWidth().card(18).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r.optString("text"), color = if (on) Ink else Muted, fontSize = 13.sp, lineHeight = 18.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${r.optString("for")} · ${r.optString("date")}", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text(if (on) "On" else "Off", Modifier.press { scope.launch { apply(vm.rules("/rules/change", JSONObject().put("id", id).put("on", !on))) } }
                        .background(if (on) lt(Color(0xFFDCFCE7)) else Paper, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (on) Color(0xFF15803D) else Muted, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    Text("Edit", Modifier.press { editing = r }.background(Pastel, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Violet, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    Text("Delete", Modifier.press { scope.launch { apply(vm.rules("/rules/change", JSONObject().put("id", id).put("delete", true))) } }
                        .background(lt(Color(0xFFFFE4E6)), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color(0xFFE11D48), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }
    }
    editing?.let { r ->
        var t by remember(r) { mutableStateOf(r.optString("text")) }
        androidx.compose.material3.AlertDialog(onDismissRequest = { editing = null },
            title = { Text("Edit rule") },
            text = { TextField(t, { t = it }, Modifier.fillMaxWidth(), minLines = 3) },
            confirmButton = { Text("Save", Modifier.press {
                scope.launch { apply(vm.rules("/rules/change", JSONObject().put("id", r.optString("id")).put("text", t))); editing = null }
            }.padding(12.dp), color = Violet, fontWeight = FontWeight.ExtraBold) },
            dismissButton = { Text("Cancel", Modifier.press { editing = null }.padding(12.dp), color = Muted) })
    }
}
