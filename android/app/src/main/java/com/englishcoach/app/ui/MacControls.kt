package com.englishcoach.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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

/** One button on the Mac. [hint] is the shortcut shown under the label; [confirm] asks before running. */
class MacAction(val label: String, val hint: String, val confirm: Boolean = false, val run: (CoachViewModel) -> Unit)

private fun key(label: String, key: String, vararg mods: String, hint: String = shortcut(key, mods.toList()), confirm: Boolean = false) =
    MacAction(label, hint, confirm) { it.macInput("key", "key" to key, "mods" to mods.toList()) }

private fun shortcut(key: String, mods: List<String>): String {
    val sym = mapOf("cmd" to "⌘", "shift" to "⇧", "alt" to "⌥", "ctrl" to "⌃", "fn" to "fn")
    val k = mapOf("arrow-up" to "↑", "arrow-down" to "↓", "arrow-left" to "←", "arrow-right" to "→", "space" to "Space",
        "tab" to "Tab", "esc" to "Esc", "return" to "↩", "delete" to "⌫")[key] ?: key.uppercase()
    return mods.joinToString("") { sym[it] ?: it } + k
}

private fun open(label: String, app: String) = MacAction(label, app) { it.macInput("open_app", "name" to app) }

/** Every control, by category. */
val MAC_CONTROLS: List<Pair<String, List<MacAction>>> = listOf(
    "System" to listOf(
        key("Spotlight", "space", "cmd"), key("Mission Control", "arrow-up", "ctrl"), key("App windows", "arrow-down", "ctrl"),
        key("Show desktop", "f11"), open("Launchpad", "Launchpad"), key("Force quit", "esc", "cmd", "alt"),
        key("Screenshot", "3", "cmd", "shift"), key("Screenshot area", "4", "cmd", "shift"), key("Record screen", "5", "cmd", "shift"),
        MacAction("Display off", "pmset") { it.macInput("display_sleep") },
        key("Lock screen", "q", "ctrl", "cmd", confirm = true),
        MacAction("Sleep Mac", "pmset", confirm = true) { it.macInput("mac_sleep") },
    ),
    "Apps" to listOf(
        key("App switcher", "tab", "cmd"), key("Previous app", "tab", "cmd", "shift"), key("Next window", "`", "cmd"),
        key("New window", "n", "cmd"), key("Minimize", "m", "cmd"), key("Hide app", "h", "cmd"),
        key("Full screen", "f", "ctrl", "cmd"), key("Settings", ",", "cmd"), key("Close window", "w", "cmd"),
        key("Quit app", "q", "cmd", confirm = true), key("Space left", "arrow-left", "ctrl"), key("Space right", "arrow-right", "ctrl"),
    ),
    "Edit" to listOf(
        key("Copy", "c", "cmd"), key("Cut", "x", "cmd"), key("Paste", "v", "cmd"),
        key("Undo", "z", "cmd"), key("Redo", "z", "cmd", "shift"), key("Select all", "a", "cmd"),
        key("Find", "f", "cmd"), key("Save", "s", "cmd"), key("Print", "p", "cmd"),
        key("Bold", "b", "cmd"), key("Delete word", "delete", "alt"), key("Delete line", "delete", "cmd"),
    ),
    "Browser" to listOf(
        key("New tab", "t", "cmd"), key("Close tab", "w", "cmd"), key("Reopen tab", "t", "cmd", "shift"),
        key("Next tab", "tab", "ctrl"), key("Previous tab", "tab", "ctrl", "shift"), key("Reload", "r", "cmd"),
        key("Back", "[", "cmd"), key("Forward", "]", "cmd"), key("Address bar", "l", "cmd"),
        key("Zoom in", "=", "cmd"), key("Zoom out", "-", "cmd"), key("Private window", "n", "cmd", "shift"),
    ),
    "Media" to listOf(
        key("Play / Pause", "play-pause", hint = "⏯"), key("Next", "play-next", hint = "⏭"), key("Previous", "play-previous", hint = "⏮"),
        key("Volume −", "volume-down", hint = "🔉"), key("Volume +", "volume-up", hint = "🔊"), key("Mute", "mute", hint = "🔇"),
        key("Brightness −", "brightness-down", hint = "☀︎−"), key("Brightness +", "brightness-up", hint = "☀︎+"),
        open("Music", "Music"),
    ),
    "Mouse" to listOf(
        MacAction("Left click", "at pointer") { it.macInput("click") },
        MacAction("Right click", "at pointer") { it.macInput("rclick") },
        MacAction("Double click", "at pointer") { it.macInput("dclick") },
        MacAction("Scroll up", "page") { it.macInput("scroll", "dy" to -500) },
        MacAction("Scroll down", "page") { it.macInput("scroll", "dy" to 500) },
        key("Page top", "home"), key("Page end", "end"),
    ),
    "Open" to listOf(
        open("Finder", "Finder"), open("Safari", "Safari"), open("Chrome", "Google Chrome"), open("Terminal", "Terminal"),
        open("VS Code", "Visual Studio Code"), open("Android Studio", "Android Studio"), open("Notes", "Notes"),
        open("Calendar", "Calendar"), open("Mail", "Mail"), open("Messages", "Messages"), open("System Settings", "System Settings"),
        open("Activity Monitor", "Activity Monitor"),
    ),
)

/** Bottom sheet with every Mac control, grouped by category. Risky actions need a second tap. */
@Composable
fun MacControlsSheet(vm: CoachViewModel, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var armed by remember { mutableStateOf<MacAction?>(null) }
    var lastRun by remember { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.62f)
        .background(Brush.verticalGradient(listOf(Color(0xF7201B3A), Color(0xFA121022))), shape)
        .border(1.dp, Color(0x22FFFFFF), shape)
        .navigationBarsPadding().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mac controls", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            lastRun?.let { Text("✓ $it  ", color = Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Row(Modifier.press(onClose).background(Color(0x22FFFFFF), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                LineIcon(Icons.CHEVRON_DOWN, Color.White, size = 16.dp, strokeWidth = 2.4f)
                Text(" Close", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MAC_CONTROLS.forEachIndexed { i, (name, _) ->
                Text(name, Modifier.press { tab = i; armed = null }
                    .background(if (tab == i) Violet else Color(0x1FFFFFFF), CircleShape).padding(horizontal = 16.dp, vertical = 9.dp),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MAC_CONTROLS[tab].second) { a ->
                val isArmed = armed === a
                Column(Modifier.fillMaxWidth().height(70.dp).press {
                    if (a.confirm && !isArmed) { armed = a; return@press }
                    a.run(vm); armed = null; lastRun = a.label
                }.background(when { isArmed -> Pink; a.confirm -> Color(0xFF3A2440); else -> Color(0xFF221F35) }, RoundedCornerShape(16.dp))
                    .padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(if (isArmed) "Tap again" else a.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        textAlign = TextAlign.Center, maxLines = 2, lineHeight = 15.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(a.hint, color = Color(0xFF9C97BC), fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}
