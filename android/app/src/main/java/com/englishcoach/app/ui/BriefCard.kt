package com.englishcoach.app.ui

import androidx.compose.animation.animateContentSize
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
import com.englishcoach.app.Screen

/** "Your day" on Home: today's video, YouTube, Flow credits, Mac and Claude, plus a button to hear it. */
@Composable
fun BriefCard(vm: CoachViewModel) {
    LaunchedEffect(vm.activeUrl) { vm.loadBrief(); vm.syncMoneyOffline() }   // again once the app finds the Mac on Wi-Fi
    val b = vm.brief ?: return
    var open by remember { mutableStateOf(false) }
    var setTime by remember { mutableStateOf(false) }
    val yt = b.optJSONObject("youtube")
    val going = yt?.optJSONArray("going_public")
    val credits = b.optJSONObject("credits")?.let { if (it.isNull("left")) null else it.optInt("left") }
    val mac = b.optJSONObject("mac")
    val cl = b.optJSONObject("claude")
    val video = b.optJSONObject("video")
    val settings = b.optJSONObject("settings")
    Column(Modifier.fillMaxWidth().animateContentSize()
        .background(Brush.linearGradient(listOf(Color(0xFFFFB347), Color(0xFFFF6B9D), Color(0xFF8B5CF6))), RoundedCornerShape(26.dp))
        .press { open = !open }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("☀️", fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Your day", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text(b.optString("date"), color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
            }
            Text("🔊 Read", Modifier.press { vm.readBrief() }.background(Color.White.copy(alpha = .25f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 7.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mini("✅", "${yt?.optInt("waiting") ?: 0}", "waiting", Modifier.weight(1f)) { vm.screen = Screen.Videos }
            Mini("📺", "${going?.length() ?: 0}", "public today", Modifier.weight(1f)) { vm.screen = Screen.Videos }
            Mini("💳", credits?.toString() ?: "?", "credits", Modifier.weight(1f)) { vm.screen = Screen.Studio }
            Mini("💾", "${mac?.optInt("free_gb") ?: 0}G", "free", Modifier.weight(1f)) { vm.screen = Screen.Cleaner }
        }
        val mo = b.optJSONObject("money")
        val ideas = b.optJSONArray("etsy")
        LaunchedEffect(b) { com.englishcoach.app.TodayWidget.save(vm.getApplication(), b) }
        if (mo != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mini("🏠", "%,.0f".format(mo.optDouble("home_owes")), "ghar se lene", Modifier.weight(1f)) { vm.screen = Screen.Money }
            Mini("👛", "%,.0f".format(mo.optDouble("wallet")), "pocket", Modifier.weight(1f)) { vm.screen = Screen.Money }
            Mini("🧾", "${mo.optJSONArray("bills_due")?.length() ?: 0}", "bills due", Modifier.weight(1f)) { vm.screen = Screen.Money }
            Mini("🛍", "${ideas?.length() ?: 0}", "Etsy ideas", Modifier.weight(1f)) { vm.etsyStore = null; vm.etsyRun = null; vm.screen = Screen.Stores }
        }
        if (open) {
            if (ideas != null) for (i in 0 until ideas.length()) ideas.getJSONObject(i).let {
                Line("🛍 Etsy idea · ${it.optString("store")} · ${it.optString("priority")}", it.optString("title"), it.optString("kind"))
            }
            mo?.let { m ->
                Line("💰 Is mahine", "Kharcha PKR ${"%,.0f".format(m.optDouble("spent_month"))} · Kamai PKR ${"%,.0f".format(m.optDouble("earned_month"))}",
                    m.optJSONArray("bills_due")?.let { a -> if (a.length() > 0) "Bills: " + List(a.length()) { a.getString(it) }.joinToString() else "" } ?: "")
            }
            video?.let { Line("🎬 Today's video", it.optString("title"), it.optString("why")) }
            if (going != null) for (i in 0 until going.length()) going.getJSONObject(i).let {
                Line("📺 Goes public at ${label(it.optString("time"))}", it.optString("title"), "")
            }
            yt?.optJSONObject("top")?.let { t ->
                if (t.optInt("views") > 0) Line("🏆 Top video · ${t.optInt("views")} views", t.optString("title"), "Channel total: ${yt.optInt("total_views")} views")
            }
            b.optJSONObject("next_slot")?.let { Line("📅 Next free upload slot", "${vm.dayName(it.optInt("day"))} at ${label(it.optString("time"))}", "") }
            Line("🤖 Claude (last 24 h)", "${cl?.optInt("done")} done · ${cl?.optInt("failed")} failed · ${cl?.optInt("running")} running", "")
            Line("🗣 English practice", "🔥 ${vm.store.streak}-day streak · ⭐ ${vm.store.xp} XP", "")
            Row(verticalAlignment = Alignment.CenterVertically) {
                val on = settings?.optBoolean("on") ?: true
                val t = settings?.optString("time") ?: "09:00"
                Text("Morning notification: ${if (on) label(t) else "off"}", Modifier.weight(1f), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Change", Modifier.press { setTime = true }.background(Color.White.copy(alpha = .25f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(6.dp))
                Text(if (on) "Turn off" else "Turn on", Modifier.press { vm.saveBriefTime(t, !on) }.background(Color.White.copy(alpha = .25f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        } else Text("Tap for details", color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
    }
    if (setTime) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(Unit) {
            val (h, m) = (settings?.optString("time") ?: "09:00").split(":").map { it.toInt() }
            android.app.TimePickerDialog(ctx, { _, hh, mm -> vm.saveBriefTime("%02d:%02d".format(hh, mm), true) }, h, m, false)
                .apply { setOnDismissListener { setTime = false } }.show()
        }
    }
}

@Composable
private fun Mini(icon: String, value: String, label: String, modifier: Modifier, onClick: () -> Unit) =
    Column(modifier.background(Color.White.copy(alpha = .22f), RoundedCornerShape(16.dp)).press(onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon $value", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Text(label, color = Color.White.copy(alpha = .85f), fontSize = 10.sp)
    }

@Composable
private fun Line(head: String, main: String, sub: String) =
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .18f), RoundedCornerShape(14.dp)).padding(10.dp)) {
        Text(head, color = Color.White.copy(alpha = .85f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(main, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (sub.isNotBlank()) Text(sub, color = Color.White.copy(alpha = .8f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
