package com.englishcoach.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * "Bolo ya likho": one sentence with many things ("ghar se 5000 mile, 2000 dawai Ammi ki, 500 rickshaw, 300 mera burger")
 * → every entry added at once, right kind (pocket / ghar ka / mera). Online the Mac's AI splits it; offline the phone does.
 */
@Composable
fun MoneyAssistant(vm: CoachViewModel, offline: Boolean, onAdded: () -> Unit, reload: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var say by remember { mutableStateOf("") }
    var added by remember { mutableStateOf(listOf<JSONObject>()) }
    var partial by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(0f) }
    var listening by remember { mutableStateOf(false) }
    val listener = remember { LongListen(ctx, { t -> text = (text + " " + t).trim() }, { partial = it }, { level = it }) }
    DisposableEffect(Unit) { onDispose { listener.stop() } }
    val micPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        if (it) { listener.start(); listening = true }
    }
    fun send(t: String) {
        if (t.isBlank() || busy) return
        busy = true; say = ""; added = emptyList()
        scope.launch {
            val (s, list) = vm.addMoneySmart(t)
            say = s; added = list; busy = false; text = ""
            if (list.isNotEmpty()) { onAdded(); reload() }
        }
    }
    val inf = rememberInfiniteTransition(label = "ai")
    val glow by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "g")
    Column(Modifier.fillMaxWidth().animateContentSize()
        .background(Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFFF59E0B))), RoundedCornerShape(24.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🤖", fontSize = 22.sp, modifier = Modifier.graphicsLayer { scaleX = 1f + glow * .12f; scaleY = 1f + glow * .12f })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Mujhe bata do, main add kar dunga", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text(if (offline) "Offline: phone par samajh kar save, Mac milte hi sync" else "Ek saath kitni bhi cheezein bolo", color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).background(Color.White.copy(alpha = .95f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                androidx.compose.foundation.text.BasicTextField(text, { text = it }, Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = InkOnLight, fontSize = 14.sp), maxLines = 4,
                    decorationBox = { inner -> Box { if (text.isEmpty()) Text("e.g. ghar se 5000 mile, 2000 dawai Ammi ki, 300 mera khana", color = Color(0xFF8A84A8), fontSize = 13.sp); inner() } })
            }
            Box(Modifier.size(46.dp).graphicsLayer { val k = if (listening) 1f + level * .25f else 1f; scaleX = k; scaleY = k }
                .background(if (listening) Color(0xFFE11D48) else Color.White.copy(alpha = .25f), CircleShape).press {
                if (listening) { listener.stop(); listening = false }
                else if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    listener.start(); listening = true
                } else micPerm.launch(android.Manifest.permission.RECORD_AUDIO)
            }, contentAlignment = Alignment.Center) { Text(if (listening) "⏹" else "🎤", fontSize = 20.sp, color = Color.White) }
            Box(Modifier.size(46.dp).background(Color.White, CircleShape).press { if (listening) { listener.stop(); listening = false }; send(text) }, contentAlignment = Alignment.Center) {
                Text(if (busy) "…" else "➤", color = Color(0xFF7C3AED), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (listening) Text("🔴 Sun raha hoon… aaram se sochte jao, bolte jao. Khatam ho to ⏹ dabao, phir ➤" +
            (if (partial.isNotBlank()) "\n“$partial”" else ""), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (busy) Text("🤖 Add kar raha hoon…", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (say.isNotBlank()) Text(say, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (added.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .18f), RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                added.forEach { e ->
                    val k = e.optString("kind")
                    Text((when (k) { "pocket" -> "👛 Pocket"; "received" -> "💵 Wapas"; "income" -> "💼 Kamai"; else -> if (e.optString("whose") == "home") "🏠 Ghar" else "🙋 Mera" }) +
                        "  PKR ${"%,.0f".format(e.optDouble("amount"))}  ·  ${e.optString("note")}" + (if (e.optBoolean("offline")) "  ⏳" else ""),
                        color = Color.White, fontSize = 12.sp)
                }
                Text("↩️ Undo (sab hatao)", Modifier.background(Color.White, CircleShape).press {
                    scope.launch {
                        val ids = added.mapNotNull { it.optString("id").takeIf { i -> i.isNotBlank() && !i.startsWith("off") } }
                        if (ids.isNotEmpty()) vm.mac("/money/undo", JSONObject().put("ids", JSONArray(ids)))
                        added.filter { it.optString("id").startsWith("off") }.forEach { _ -> MoneyOffline.removeLast(ctx) }
                        added = emptyList(); say = "↩️ Hata diya"; reload()
                    }
                }.padding(horizontal = 14.dp, vertical = 7.dp), color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
