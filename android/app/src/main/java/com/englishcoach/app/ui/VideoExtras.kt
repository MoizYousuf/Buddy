package com.englishcoach.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Video scores from the Mac (loaded once per screen). */
object VideoScores { var all by mutableStateOf(JSONObject()) }

/** Views chart + top videos (from the stats the Studio already reads). */
@Composable
fun YtStatsCard(vm: CoachViewModel) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) { d = vm.mac("/money/ytstats").also { r -> r.optJSONObject("scores")?.let { VideoScores.all = it } } }
    val s = d ?: return
    if (s.has("error")) return
    val top = s.optJSONArray("top")?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: emptyList()
    val max = (top.maxOfOrNull { it.optInt("views") } ?: 1).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFF0033), Color(0xFF7B1FA2))), RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("📈 Channel stats", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("${s.optInt("total_views")}" to "views", "${s.optInt("videos")}" to "videos", "${s.optInt("long_views")}" to "long", "${s.optInt("shorts_views")}" to "shorts").forEach { (v, l) ->
                Column(Modifier.weight(1f).background(Color.White.copy(alpha = .2f), RoundedCornerShape(14.dp)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(v, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(l, color = Color.White.copy(alpha = .8f), fontSize = 10.sp)
                }
            }
        }
        Text("Top videos", color = Color.White.copy(alpha = .85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        top.take(6).forEachIndexed { i, v ->
            val f by animateFloatAsState(v.optInt("views") / max.toFloat(), tween(900, delayMillis = i * 80), label = "bar")
            Column {
                Text(v.optString("title").substringBefore("|").trim(), color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(10.dp).background(Color.White.copy(alpha = .15f), CircleShape)) {
                        Box(Modifier.fillMaxWidth(f.coerceAtLeast(.02f)).fillMaxHeight().background(Color(0xFFFFD86B), CircleShape))
                    }
                    Text("  ${v.optInt("views")}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

/** "Score karo" on a waiting video: Gemini looks at 4 frames, gives a score, AI-look and fixes. */
@Composable
fun VideoScoreRow(vm: CoachViewModel, id: String) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val sc = VideoScores.all.optJSONObject(id)
    LaunchedEffect(Unit) { if (VideoScores.all.length() == 0) vm.mac("/money/ytstats").optJSONObject("scores")?.let { VideoScores.all = it } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (sc == null) Text(if (busy) "🔍 Dekh raha hoon… (~1 min)" else "🔍 Upload se pehle score karo", Modifier.background(Pastel, CircleShape).press {
            if (busy) return@press
            busy = true; err = ""
            scope.launch {
                val r = vm.mac("/video/score", JSONObject().put("id", id)); busy = false
                if (r.has("error")) err = r.optString("error") else { VideoScores.all = JSONObject(VideoScores.all.toString()).put(id, r); open = true }
            }
        }.padding(horizontal = 12.dp, vertical = 7.dp), color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        else {
            val score = sc.optInt("score"); val ai = sc.optInt("looks_ai")
            Row(Modifier.press { open = !open }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VTag("⭐ $score/10", if (score >= 8) Color(0xFFDCFCE7) else if (score >= 6) Color(0xFFFEF3C7) else Color(0xFFFFE4E6))
                VTag("🤖 AI-look $ai/10", if (ai <= 4) Color(0xFFDCFCE7) else if (ai <= 6) Color(0xFFFEF3C7) else Color(0xFFFFE4E6))
                Text(if (open) "▲" else "▼", color = Muted, fontSize = 12.sp)
            }
            if (open) Column(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                sc.optJSONArray("fix")?.let { a -> for (i in 0 until a.length()) Text("🔧 " + a.getString(i), color = Ink, fontSize = 12.sp) }
                sc.optJSONArray("good")?.let { a -> for (i in 0 until minOf(2, a.length())) Text("👍 " + a.getString(i), color = Muted, fontSize = 11.sp) }
                sc.optString("title_tip").takeIf { it.isNotBlank() }?.let { Text("✏️ Title: $it", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (err.isNotBlank()) Text(err, color = Color(0xFFE11D48), fontSize = 11.sp)
    }
}

@Composable
private fun VTag(t: String, bg: Color) = Text(t, Modifier.background(bg, CircleShape).padding(horizontal = 9.dp, vertical = 4.dp),
    color = InkOnLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
