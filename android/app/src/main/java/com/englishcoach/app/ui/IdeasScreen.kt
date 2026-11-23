package com.englishcoach.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Screen
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Ideas said while offline wait here on the phone; sent to the Mac (which writes the note) when it is reachable. */
object IdeaOffline {
    private fun p(ctx: Context) = ctx.getSharedPreferences("ideas_offline", 0)
    fun pending(ctx: Context) = JSONArray(p(ctx).getString("queue", "[]"))
    fun add(ctx: Context, text: String) {
        val a = pending(ctx)
        a.put(JSONObject().put("text", text).put("local_id", "ph" + System.currentTimeMillis())
            .put("said_at", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())))
        p(ctx).edit().putString("queue", a.toString()).apply()
    }
    fun removeFirst(ctx: Context) {
        val a = pending(ctx); val b = JSONArray(); for (i in 1 until a.length()) b.put(a.get(i)); p(ctx).edit().putString("queue", b.toString()).apply()
    }
    private val IDEA = Regex("(\\bidea\\b|aidea|aaidiya|aidia|socha hai|soch raha|note kar|note karo|note kr|yaad rakh|likh lo|likh lena|baad mein kaam|remind me)", RegexOption.IGNORE_CASE)
    fun looksLikeIdea(t: String) = IDEA.containsMatchIn(t)
}

@Composable
fun IdeasScreen(vm: CoachViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf(listOf<JSONObject>()) }
    var pend by remember { mutableStateOf(listOf<JSONObject>()) }
    var text by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("open") }
    val mic = rememberMic { t -> text = (text + " " + t).trim() }
    fun load() = scope.launch {
        vm.syncIdeas()
        pend = IdeaOffline.pending(ctx).let { a -> List(a.length()) { a.getJSONObject(it) } }
        vm.mac("/money/ideas").optJSONArray("ideas")?.let { a -> list = List(a.length()) { a.getJSONObject(it) } }
    }
    LaunchedEffect(vm.activeUrl) { load() }
    val doneCount = list.count { it.optBoolean("done") }
    val shown = when (filter) { "open" -> list.filter { !it.optBoolean("done") }; "done" -> list.filter { it.optBoolean("done") }; else -> list }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 150.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("💡 Ideas") { vm.screen = Screen.Home } }
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFDB2777))), RoundedCornerShape(24.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Idea aaya? Bol do ya likh do", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("Offline bhi phone par save. Mac milte hi AI poora note bana dega.", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 84.dp).background(Color.White, RoundedCornerShape(18.dp)).padding(14.dp)) {
                    androidx.compose.foundation.text.BasicTextField(text, { text = it }, Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = InkOnLight, fontSize = 15.sp, lineHeight = 21.sp), minLines = 3, maxLines = 8,
                        decorationBox = { inner -> Box { if (text.isEmpty()) Text("e.g. funeral program ke saath thank you card bundle", color = Color(0xFF8A84A8), fontSize = 14.sp); inner() } })
                }
                if (mic.listening) Text("🔴 Sun raha hoon… aaram se bolo. Khatam ho to ⏹" + (if (mic.partial.isNotBlank()) "\n“${mic.partial}”" else ""),
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).graphicsLayer { val k = if (mic.listening) 1f + mic.level * .2f else 1f; scaleX = k; scaleY = k }
                        .background(if (mic.listening) Color(0xFFE11D48) else Color.White.copy(alpha = .25f), CircleShape).press { mic.toggle() },
                        contentAlignment = Alignment.Center) { Text(if (mic.listening) "⏹" else "🎤", fontSize = 22.sp, color = Color.White) }
                    Text(if (mic.listening) "Bol rahe ho…" else "Tap karke bolo", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("💾 Save", Modifier.background(if (text.isBlank()) Color.White.copy(alpha = .5f) else Color.White, CircleShape).press {
                        mic.stop()
                        if (text.isBlank()) return@press
                        IdeaOffline.add(ctx, text.trim()); text = ""; load()
                    }.padding(horizontal = 22.dp, vertical = 14.dp), color = InkOnLight, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("open", "Baqi", list.size - doneCount), Triple("done", "Ho gaye", doneCount), Triple("all", "Sab", list.size)).forEach { (k, t, n) ->
                    Column(Modifier.weight(1f).background(if (filter == k) Violet else Surface, RoundedCornerShape(16.dp)).press { filter = k }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$n", color = if (filter == k) Color.White else Ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text(t, color = if (filter == k) Color.White.copy(alpha = .85f) else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (pend.isNotEmpty()) item { Text("⏳ Phone par save (Mac ka intezar): ${pend.size}", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        items(pend) { p ->
            Row(Modifier.fillMaxWidth().card(18).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⏳", fontSize = 20.sp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.optString("text"), color = Ink, fontSize = 14.sp)
                    Text("${p.optString("said_at")} · Mac milte hi note banega", color = Muted, fontSize = 11.sp)
                }
            }
        }
        if (shown.isEmpty() && pend.isEmpty()) item {
            Text(if (list.isEmpty()) "Abhi koi idea nahi. Upar bolo ya likho, ya chat mein \"idea: …\" bolo." else "Is list mein kuch nahi.",
                Modifier.fillMaxWidth().padding(vertical = 20.dp), color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        items(shown, key = { it.optString("id") }) { x ->
            val id = x.optString("id")
            val (icon, tint) = when (x.optString("project")) {
                "Etsy" -> "🛍" to Color(0xFFF97316); "YouTube" -> "🎬" to Color(0xFFEF4444); "App" -> "📱" to Color(0xFF6366F1)
                "Money" -> "💰" to Color(0xFF16A34A); else -> "💡" to Color(0xFFF59E0B)
            }
            Column(Modifier.fillMaxWidth().card(20).press { open = if (open == id) null else id }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(tint.copy(alpha = .16f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(icon, fontSize = 20.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(x.optString("title").ifBlank { x.optString("text").take(50) }, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                            textDecoration = if (x.optBoolean("done")) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            val tag = if (x.optString("status") == "thinking") "🤖 note ban raha hai…" else x.optString("project")
                            if (tag.isNotBlank()) Text(tag, Modifier.background(tint.copy(alpha = .14f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                                color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            x.optString("effort").takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, fontSize = 10.sp) }
                            Text(x.optString("said_at"), color = Muted, fontSize = 10.sp)
                        }
                    }
                    Text(if (open == id) "▲" else "▼", color = Muted, fontSize = 11.sp)
                }
                if (open == id) {
                    Text("🗣 \"${x.optString("text")}\"", color = Muted, fontSize = 12.sp)
                    x.optString("note").takeIf { it.isNotBlank() }?.let { Text(it, color = Ink, fontSize = 13.sp, lineHeight = 18.sp) }
                    x.optString("why").takeIf { it.isNotBlank() }?.let { Text("✨ $it", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    x.optJSONArray("steps")?.let { a -> for (i in 0 until a.length()) Text("${i + 1}. ${a.getString(i)}", color = Ink, fontSize = 12.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (x.optBoolean("done")) "↩️ Phir se kholo" else "✅ Ho gaya", Modifier.background(Lime, CircleShape).press {
                            scope.launch { vm.mac("/ideas/change", JSONObject().put("id", id).put("done", !x.optBoolean("done"))); load() }
                        }.padding(horizontal = 14.dp, vertical = 8.dp), color = InkOnLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("🗑 Hatao", Modifier.background(Paper, CircleShape).press {
                            scope.launch { vm.mac("/ideas/change", JSONObject().put("id", id).put("delete", true)); load() }
                        }.padding(horizontal = 14.dp, vertical = 8.dp), color = Ink, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
