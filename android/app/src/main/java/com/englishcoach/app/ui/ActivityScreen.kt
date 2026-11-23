package com.englishcoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything done on the Mac from the phone, newest first, grouped by day; blocked attempts show in red. */
@Composable
fun ActivityScreen(vm: CoachViewModel) {
    LaunchedEffect(vm.activityKind) { vm.loadActivity() }
    val day = remember { SimpleDateFormat("EEEE, d MMM", Locale.getDefault()) }
    val hm = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val blocked = vm.activity.count { it.optString("kind") == "security" && it.optString("icon") == "🛡" }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BackHeader("Activity") { vm.screen = Screen.Mac } }
                Pill("↻", outline = true) { vm.loadActivity() }
            }
        }
        item { Text("Everything done on your Mac from this phone. Typed text and file contents are never saved.", color = Muted, fontSize = 12.sp) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("" to "All", "security" to "🛡 Security", "mac" to "🖥 Mac", "agent" to "🤖 Agent", "video" to "🎬 Videos",
                    "files" to "📁 Files", "settings" to "⚙️ Settings").forEach { (k, t) ->
                    val on = vm.activityKind == k
                    Text(t, Modifier.background(if (on) Violet else Pastel, CircleShape).press { vm.activityKind = k }
                        .padding(horizontal = 12.dp, vertical = 7.dp), color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        if (blocked > 0 && vm.activityKind.isEmpty()) item {
            Text("🛡 $blocked blocked attempt${if (blocked > 1) "s" else ""} in this list (requests without this phone's key). Nothing was done.",
                Modifier.fillMaxWidth().background(lt(Color(0xFFFFE3EC)), RoundedCornerShape(14.dp)).padding(12.dp), color = Ink, fontSize = 12.sp)
        }
        if (vm.activity.isEmpty()) item { Text("Nothing here yet.", color = Muted) }
        val groups = vm.activity.groupBy { day.format(Date((it.optDouble("t") * 1000).toLong())) }
        groups.forEach { (d, list) ->
            item { Text(d, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp)) }
            items(list) { e ->
                val sec = e.optString("icon") == "🛡"
                Row(Modifier.fillMaxWidth().background(if (sec) lt(Color(0xFFFFF1F2)) else Surface, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalAlignment = Alignment.Top) {
                    Text(e.optString("icon"), fontSize = 18.sp, modifier = Modifier.width(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.optString("text"), color = if (sec) Color(0xFFBE123C) else Ink, fontSize = 13.sp, lineHeight = 18.sp)
                        Text(hm.format(Date((e.optDouble("t") * 1000).toLong())) + (e.optString("via").takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
