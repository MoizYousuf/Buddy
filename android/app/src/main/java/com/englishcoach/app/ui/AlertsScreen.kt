package com.englishcoach.app.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Every alert the Mac has sent (newest first, last 200): tap one to open its screen. */
@Composable
fun AlertsScreen(vm: CoachViewModel) {
    LaunchedEffect(Unit) { vm.loadAlerts() }
    val seenBefore = remember { vm.alertsSeen }
    DisposableEffect(Unit) { onDispose { vm.markAlertsSeen() } }
    LaunchedEffect(vm.alerts) { if (vm.alerts.isNotEmpty()) vm.markAlertsSeen() }
    val fmt = remember { SimpleDateFormat("d MMM · h:mm a", Locale.getDefault()) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BackHeader("Notifications") { vm.screen = Screen.Mac } }
                if (vm.alerts.isNotEmpty()) Pill("Clear all", outline = true) { vm.clearAlerts() }
            }
        }
        vm.alertMsg?.let { m -> item { Text(m, Modifier.fillMaxWidth().background(lt(Color(0xFFE6F7EE)), RoundedCornerShape(14.dp)).press { vm.alertMsg = null }.padding(12.dp), color = Ink, fontSize = 13.sp) } }
        if (vm.alerts.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔕", fontSize = 48.sp)
                Text("No notifications yet", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text("Video, agent and storage alerts from your Mac show up here.", color = Muted, fontSize = 13.sp)
            }
        }
        items(vm.alerts, key = { it.id }) { a ->
            val (emoji, tint) = when (a.kind) {
                "video" -> "🎬" to Color(0xFFFFE3EC)
                "agent" -> "🤖" to Color(0xFFEDE8FF)
                "permission" -> "⚠️" to Color(0xFFFFF3C4)
                "disk" -> "💾" to Color(0xFFFFEFE0)
                else -> "🔔" to Color(0xFFE6F7EE)
            }
            val new = a.id > seenBefore
            Row(Modifier.fillMaxWidth().card(22).press {
                    when (a.screen) { "Studio" -> vm.screen = Screen.Studio; "Assistant" -> vm.screen = Screen.Assistant; "Cleaner" -> vm.screen = Screen.Cleaner; "Videos" -> vm.screen = Screen.Videos; "Stores" -> vm.screen = Screen.Stores; "Money" -> vm.screen = Screen.Money; "Ideas" -> vm.screen = Screen.Ideas }
                }.padding(14.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(44.dp).background(tint, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.title, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        if (new) Box(Modifier.size(9.dp).background(Pink, CircleShape))
                    }
                    if (a.body.isNotBlank()) Text(a.body, color = Ink.copy(alpha = .75f), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 4)
                    Text(fmt.format(Date(a.time)), color = Muted, fontSize = 11.sp)
                    a.action?.let { act ->
                        Text(act.optString("label"), Modifier.padding(top = 4.dp).press { vm.alertAction(a) }.background(Violet, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
