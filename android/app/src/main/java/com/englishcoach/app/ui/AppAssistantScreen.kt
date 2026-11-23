package com.englishcoach.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Screen
import kotlinx.coroutines.launch

/**
 * 📱 App assistant (separate from the Mac assistant): Claude or Gemini runs the APP for you —
 * "ye bill add karo, pocket money 5000 mili, Money kholo", "app mein ye masla theek karo".
 */
@Composable
fun AppAssistantScreen(vm: CoachViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    val listener = remember { LongListen(ctx, { t -> text = (text + " " + t).trim() }, { partial = it }) }
    DisposableEffect(Unit) { onDispose { listener.stop() } }
    val list = rememberLazyListState()
    LaunchedEffect(vm.appChat.size) { if (vm.appChat.isNotEmpty()) list.animateScrollToItem(vm.appChat.size - 1) }
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BackHeader("📱 App assistant") { vm.screen = Screen.Home }
        Row(Modifier.fillMaxWidth().background(Pastel, CircleShape).padding(4.dp)) {
            listOf("claude" to "🤖 Claude", "gemini" to "✨ Gemini · Antigravity (AI Pro)").forEach { (k, t) ->
                Text(t, Modifier.weight(1f).background(if (vm.appEngine == k) Violet else Color.Transparent, CircleShape).press { vm.pickAppEngine(k) }
                    .padding(vertical = 9.dp), color = if (vm.appEngine == k) Color.White else Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
        LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.appChat.isEmpty()) item {
                Column(Modifier.fillMaxWidth().card(20).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Main app chalata hoon. Bolo ya likho:", color = Ink, fontWeight = FontWeight.ExtraBold)
                    listOf("“500 ki dawai Ammi ki, 2000 pocket money mili”", "“idea: Ramzan ki naat series”", "“Money / Stores / Videos kholo”",
                        "“Money mein bill photo ka button nahi chal raha, theek karo” (Claude code theek karega)").forEach {
                        Text("• $it", color = Muted, fontSize = 13.sp)
                    }
                    Text("Mac ka kaam (Code, Claude app, Spotlight) → 🖥 Mac assistant.", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            items(vm.appChat) { m ->
                Text(m.text, Modifier.fillMaxWidth(if (m.fromMe) .85f else 1f).let { if (m.fromMe) it.padding(start = 40.dp) else it }
                    .background(if (m.fromMe) Violet else Surface, RoundedCornerShape(18.dp)).padding(12.dp),
                    color = if (m.fromMe) Color.White else Ink, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        if (listening) Text("🔴 Sun raha hoon… aaram se bolo. Khatam ho to ⏹" + (if (partial.isNotBlank()) "\n“$partial”" else ""),
            color = Color(0xFFE11D48), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().card(26).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                androidx.compose.foundation.text.BasicTextField(text, { text = it }, Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 15.sp), maxLines = 5,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Violet),
                    decorationBox = { inner -> Box { if (text.isEmpty()) Text("App ko batao kya karna hai…", color = Muted, fontSize = 14.sp); inner() } })
            }
            Box(Modifier.size(44.dp).background(if (listening) Color(0xFFE11D48) else Pastel, CircleShape).press {
                if (listening) { listener.stop(); listening = false } else { listener.start(); listening = true }
            }, contentAlignment = Alignment.Center) { Text(if (listening) "⏹" else "🎤", fontSize = 18.sp) }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(44.dp).background(Violet, CircleShape).press {
                if (listening) { listener.stop(); listening = false }
                val t = text.trim(); if (t.isEmpty()) return@press
                text = ""; scope.launch { vm.runAppAssistant(t) }
            }, contentAlignment = Alignment.Center) { Text(if (vm.appBusy) "…" else "➤", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}
