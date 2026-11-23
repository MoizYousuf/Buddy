package com.englishcoach.app.ui

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel

/** 5 cards a day: first the owner's own mistakes, then useful words for their work. Tap to flip. */
private val WORK_WORDS = listOf(
    Triple("Mujhe aap ki madad karke khushi hogi", "I'd be happy to help you with that.", "Etsy buyer ko jawab"),
    Triple("Main kal tak bhej dunga", "I'll send it to you by tomorrow.", "Deadline batana"),
    Triple("Aap ke nuqsan ka bohat afsos hai", "I'm so sorry for your loss.", "Funeral template buyer"),
    Triple("Kya aap thora aur bata sakte hain?", "Could you tell me a bit more about it?", "Client se sawal"),
    Triple("Ye meri qeemat hai", "My rate for this is…", "Freelance price"),
    Triple("Main isay theek kar deta hoon", "Let me fix that for you.", "Problem hal karna"),
    Triple("Kya ye time aap ke liye theek hai?", "Does this time work for you?", "Meeting set karna"),
    Triple("Mujhe samajh nahi aaya", "Sorry, I didn't quite catch that.", "Call par"),
    Triple("Main update deta rahunga", "I'll keep you posted.", "Client ko"),
    Triple("Shukriya intezar karne ka", "Thanks for your patience.", "Late reply"),
)

@Composable
fun WordsCard(vm: CoachViewModel) {
    vm.storeVersion
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("words", 0)
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    val mine = vm.store.dueForReview.take(5)
    val dayIdx = (today.replace("-", "").toLong() % WORK_WORDS.size).toInt()
    val cards = remember(today, mine.size) {
        mine.map { Triple(it.said, it.correct, it.category) } + List(5 - mine.size) { WORK_WORDS[(dayIdx + it) % WORK_WORDS.size] }
    }
    var i by remember { mutableStateOf(prefs.getInt("done_$today", 0)) }
    var flipped by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (flipped) 180f else 0f, spring(dampingRatio = .7f, stiffness = 220f), label = "flip")
    val streak = prefs.getInt("streak", 0)
    Column(Modifier.fillMaxWidth().card(24).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🃏 Aaj ke 5 alfaaz", Modifier.weight(1f), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text("🔥 $streak din", color = Color(0xFFEA580C), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { k -> Box(Modifier.weight(1f).height(5.dp).background(if (k < i) Color(0xFF16A34A) else Paper, CircleShape)) }
        }
        if (i >= 5) {
            Text("✅ Aaj ke 5 ho gaye! Kal naye aayenge.", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            return@Column
        }
        val c = cards[i]
        Box(Modifier.fillMaxWidth().height(130.dp).graphicsLayer { rotationY = rot; cameraDistance = 14 * density }
            .background(Brush.linearGradient(if (rot < 90f) listOf(Color(0xFF7B5CFF), Color(0xFFDB2777)) else listOf(Color(0xFF0F766E), Color(0xFF16A34A))),
                RoundedCornerShape(20.dp)).press { flipped = !flipped }.padding(16.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.graphicsLayer { if (rot >= 90f) rotationY = 180f }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (rot < 90f) (if (i < mine.size) "Aap ne kaha tha:" else "English mein kaise kahein?") else "Sahi English:",
                    color = Color.White.copy(alpha = .75f), fontSize = 11.sp)
                Text(if (rot < 90f) c.first else c.second, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, textAlign = TextAlign.Center)
                Text(if (rot < 90f) "👆 tap karke palto" else c.third, color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
            }
        }
        if (flipped) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            fun next(ok: Boolean) {
                if (i < mine.size) vm.store.reviewed(mine[i], ok)
                if (ok) vm.speakEnglish(c.second)
                val n = i + 1
                prefs.edit().putInt("done_$today", n).apply()
                if (n == 5 && prefs.getString("last", "") != today) {
                    val y = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(System.currentTimeMillis() - 86400000))
                    prefs.edit().putInt("streak", if (prefs.getString("last", "") == y) streak + 1 else 1).putString("last", today).apply()
                }
                i = n; flipped = false
            }
            Text("🔁 Phir se", Modifier.weight(1f).background(Paper, RoundedCornerShape(14.dp)).press { next(false) }.padding(12.dp),
                color = Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("✅ Yaad hai", Modifier.weight(1f).background(Lime, RoundedCornerShape(14.dp)).press { next(true) }.padding(12.dp),
                color = Ink, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        }
    }
}
