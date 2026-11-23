package com.englishcoach.app.ui

import kotlinx.coroutines.launch

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Screen

private val Dark = Color(0xFF0E0C1A)
private val Key = Color(0xFF221F35)
private val KeyHi = Color(0xFF2E2A47)

/** Quick actions: label to (key, modifiers). */
private val QUICK = listOf(
    "Spotlight" to ("space" to listOf("cmd")), "App switch" to ("tab" to listOf("cmd")),
    "Mission Ctrl" to ("arrow-up" to listOf("ctrl")), "Desktop" to ("f11" to emptyList()),
    "Copy" to ("c" to listOf("cmd")), "Paste" to ("v" to listOf("cmd")), "Undo" to ("z" to listOf("cmd")),
    "Select all" to ("a" to listOf("cmd")), "Save" to ("s" to listOf("cmd")), "New tab" to ("t" to listOf("cmd")),
    "Close" to ("w" to listOf("cmd")), "Quit app" to ("q" to listOf("cmd")), "Screenshot" to ("3" to listOf("cmd", "shift")),
    "Vol −" to ("volume-down" to emptyList()), "Vol +" to ("volume-up" to emptyList()), "Mute" to ("mute" to emptyList()),
)

/**
 * The Mac, live, inside the app. Two modes:
 *  - Direct: tap where you want to click (double tap = double click, long press = right click, drag = drag).
 *  - Trackpad: slide a finger to move the pointer, tap to click — like a laptop trackpad.
 * Two fingers pinch to zoom the picture. Full screen turns the phone sideways and hides everything else.
 */
@Composable
fun RemoteScreen(vm: CoachViewModel) {
    val s = vm.strings
    var trackpad by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var keyboard by remember { mutableStateOf(false) }
    var ai by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(Unit) { vm.streamScreen() }
    FullscreenEffect(fullscreen)
    val bmp = vm.screenBitmap
    val live = bmp != null || vm.h264Size != null

    Box(Modifier.fillMaxSize().background(Dark)) {
        Column(Modifier.fillMaxSize().then(if (fullscreen) Modifier else Modifier.statusBarsPadding().navigationBarsPadding().imePadding())
            .padding(if (fullscreen) 0.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!fullscreen) {
                // row 1: back · title + status · full screen
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(Icons.BACK) { vm.screen = Screen.Mac }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.screenShare, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(if (live) Color(0xFF2BC46B) else Pink, CircleShape))
                            val where = if (vm.displayCount > 1) "Display ${vm.display + 1} of ${vm.displayCount}" else "Mac mini"
                            Text(if (live) "  Live · $where · ${vm.screenFps} fps · ${if (vm.h264Size != null) "H.264" else "JPEG"} ${"%.1f".format(vm.streamKbps / 1000f)} Mbps" else "  Connecting…", color = Color(0xFF8B86A8), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    Text(when (vm.h264Pref) { "on" -> "H.264"; "off" -> "JPEG"; else -> "Auto" }, Modifier.background(Color(0xCC221F35), CircleShape)
                        .press { vm.cycleH264() }.padding(horizontal = 10.dp, vertical = 9.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    LaunchedEffect(Unit) { vm.watchLock() }
                    val locked = vm.macLocked
                    Text(if (locked) "🔓 Unlock" else "🔒 Lock", Modifier.background(if (locked) Color(0xFF2BC46B) else Color(0xCC221F35), CircleShape)
                        .press { if (locked) vm.unlockMac() else vm.lockMac() }
                        .padding(horizontal = 12.dp, vertical = 9.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    RoundButton(Icons.EXPAND) { fullscreen = true }
                }
                vm.unlockMsg?.let { m ->
                    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .1f), RoundedCornerShape(14.dp)).press { vm.unlockMsg = null }.padding(12.dp)) {
                        Text(m, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (vm.unlockSetup) Text("One-time setup on the Mac: open Terminal and run\nsecurity add-generic-password -a \"\$USER\" -s englishcoach-unlock -w\nthen type your Mac login password when it asks. It stays in the Mac's Keychain; the phone never sees it.",
                            color = Color.White.copy(alpha = .75f), fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
                // row 2: which display · touch or trackpad · assistant
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vm.displayCount > 1) {
                        DisplaySwitch(vm)
                        Spacer(Modifier.width(8.dp))
                    }
                    ModeSwitch(trackpad) { trackpad = it }
                    Spacer(Modifier.width(8.dp))
                    LaunchedEffect(Unit) { vm.loadDisplayMode() }
                    var pickDisplay by remember { mutableStateOf(false) }
                    Text(when (vm.displayModeName) { "extend" -> "➕ Extend"; "main" -> "📱 Phone only"; else -> "🖥 Mirror" } + " ▾",
                        Modifier.background(Key, CircleShape).press { pickDisplay = true }.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(if (vm.soundOn) "🔊" else "🔇", Modifier.background(if (vm.soundOn) Lime else Key, CircleShape).press { vm.toggleSound() }
                        .padding(horizontal = 11.dp, vertical = 8.dp), fontSize = 15.sp)
                    if (pickDisplay) DisplayModeDialog(vm) { pickDisplay = false }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.press { ai = !ai; if (ai) { keyboard = false; controls = false } }
                        .background(if (ai) Lime else Key, CircleShape).padding(start = 4.dp, end = 14.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AiButton(ai) { ai = !ai; if (ai) { keyboard = false; controls = false } }
                        Text(" Ask Mac", color = if (ai) Ink else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // ---- the Mac picture ----
            val ratio = bmp?.let { it.width.toFloat() / it.height } ?: vm.h264Size?.let { it.first.toFloat() / it.second } ?: (16f / 9f)
            Box(Modifier.fillMaxWidth().then(if (fullscreen) Modifier.weight(1f) else Modifier.aspectRatio(ratio))
                .clip(RoundedCornerShape(if (fullscreen) 0.dp else 18.dp)).background(Color.Black)
                .pointerInput(Unit) {
                    // two fingers: pinch to zoom and move the picture (handled before the one-finger gestures below)
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            if (e.changes.count { it.pressed } >= 2) {
                                zoom = (zoom * e.calculateZoom()).coerceIn(1f, 4f)
                                pan = if (zoom == 1f) Offset.Zero else pan + e.calculatePan()
                                e.changes.forEach { it.consume() }
                            }
                        } while (e.changes.any { it.pressed })
                    }
                }, contentAlignment = Alignment.Center) {
                if (live) {
                    val m = Modifier.fillMaxWidth().aspectRatio(ratio)
                        .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }
                        .then(if (trackpad) Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { vm.macInput("click") },
                                    onDoubleTap = { vm.macInput("dclick") },
                                    onLongPress = { vm.macInput("rclick") },
                                )
                            }
                            .pointerInput(Unit) {
                                // relative pointer movement, sent in small batches so the Mac keeps up
                                var acc = Offset.Zero
                                var last = 0L
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    acc += drag * 2.2f
                                    val now = System.currentTimeMillis()
                                    if (now - last > 40) {
                                        vm.macInput("nudge", "dx" to acc.x, "dy" to acc.y)
                                        acc = Offset.Zero
                                        last = now
                                    }
                                }
                            }
                        else Modifier.touchControl(vm))
                    if (vm.h264Size != null || bmp == null) H264View(vm, m) else Image(bmp, null, m, contentScale = ContentScale.Fit)
                } else Text(vm.screenError ?: "…", Modifier.padding(24.dp), color = Color(0xFFB9B3D6), textAlign = TextAlign.Center)

                // scroll strip on the right edge: slide up/down to scroll like two fingers on a trackpad
                if (trackpad) Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight(0.7f).width(34.dp).padding(4.dp)
                    .background(Color(0x33FFFFFF), CircleShape)
                    .pointerInput(Unit) {
                        var acc = 0f
                        detectVerticalDragGestures { change, dy ->
                            change.consume()
                            acc += dy
                            if (kotlin.math.abs(acc) > 12) { vm.macInput("scroll", "dy" to (-acc * 3)); acc = 0f }
                        }
                    }, contentAlignment = Alignment.Center) {
                    Text("⇕", color = Color(0xCCFFFFFF), fontSize = 14.sp)
                }

                if (fullscreen) Row(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiButton(ai) { ai = !ai; if (ai) { keyboard = false; controls = false } }
                    RoundButton(Icons.GRID, controls) { controls = !controls; if (controls) { keyboard = false; ai = false } }
                    RoundButton(Icons.KEYBOARD, keyboard) { keyboard = !keyboard; if (keyboard) { ai = false; controls = false } }
                    RoundButton(if (trackpad) Icons.TRACKPAD else Icons.POINTER, trackpad) { trackpad = !trackpad }
                    RoundButton(Icons.COLLAPSE) { fullscreen = false }
                }
                if (zoom > 1f) Text("${"%.1f".format(zoom)}×  ·  reset", Modifier.align(Alignment.TopCenter).padding(8.dp)
                    .background(Color(0xAA000000), CircleShape).press { zoom = 1f; pan = Offset.Zero }.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            if (!fullscreen) QuickGrid(vm, Modifier.weight(1f)) { controls = true; keyboard = false; ai = false }
            if (!fullscreen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeBar(vm, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    RoundButton(Icons.KEYBOARD, keyboard) { keyboard = !keyboard; if (keyboard) { ai = false; controls = false } }
                }
            }
        }
        AnimatedVisibility(keyboard, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            MacKeyboard(vm, fullscreen) { keyboard = false }
        }
        AnimatedVisibility(ai, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            AssistantPanel(vm, fullscreen) { ai = false }
        }
        AnimatedVisibility(controls, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            MacControlsSheet(vm) { controls = false }
        }
    }
}

@Composable
private fun RoundButton(icon: String, active: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).press(onClick).background(if (active) Lime else Color(0xCC221F35), CircleShape), contentAlignment = Alignment.Center) {
        LineIcon(icon, if (active) Ink else Color.White, size = 20.dp, strokeWidth = 2.2f)
    }
}

@Composable
private fun ModeSwitch(trackpad: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.background(Key, CircleShape).padding(3.dp)) {
        listOf(false to Icons.POINTER, true to Icons.TRACKPAD).forEach { (tp, icon) ->
            Box(Modifier.size(36.dp).background(if (trackpad == tp) Violet else Color.Transparent, CircleShape).press { onChange(tp) },
                contentAlignment = Alignment.Center) { LineIcon(icon, Color.White, size = 18.dp) }
        }
    }
}

@Composable
private fun TypeBar(vm: CoachViewModel, modifier: Modifier) {
    var typing by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    Row(modifier.background(Key, CircleShape).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (typing.isEmpty()) Text(vm.strings.typeOnMac, color = Color(0xFF8B86A8))
            BasicTextField(typing, { typing = it }, Modifier.fillMaxWidth(), textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Lime), singleLine = true)
        }
        Box(Modifier.size(38.dp).background(Lime, CircleShape).press { if (typing.isNotEmpty()) vm.macInput("type", "text" to typing); typing = "" },
            contentAlignment = Alignment.Center) { LineIcon(Icons.ARROW, Ink, size = 18.dp, strokeWidth = 2.5f) }
    }
}

/** A full Mac keyboard. ⌘ ⌥ ⌃ ⇧ fn stick until the next key, so any shortcut can be pressed one key at a time. */
@Composable
private fun MacKeyboard(vm: CoachViewModel, compact: Boolean, onClose: () -> Unit) {
    var mods by remember { mutableStateOf(setOf<String>()) }
    fun press(key: String) {
        vm.macInput("key", "key" to key, "mods" to mods.toList())
        mods = emptySet()
    }
    val h = if (compact) 34.dp else 42.dp
    @Composable
    fun RowScope.K(label: String, key: String = label.lowercase(), w: Float = 1f, mod: String? = null) {
        val on = mod != null && mod in mods
        Box(Modifier.weight(w).height(h).padding(2.dp).clip(RoundedCornerShape(8.dp))
            .background(if (on) Violet else if (mod != null || label.length > 1) KeyHi else Key)
            .press { if (mod != null) mods = if (on) mods - mod else mods + mod else press(key) },
            contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = if (label.length > 2) 11.sp else 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    Column(Modifier.fillMaxWidth().background(Color(0xF2141225), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
        .navigationBarsPadding().padding(6.dp)) {
        // header: grab handle + close button
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Keyboard", color = Color(0xFF8B86A8), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(Modifier.width(40.dp).height(4.dp).background(Color(0x44FFFFFF), CircleShape))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                Row(Modifier.press(onClose).background(Color(0x22FFFFFF), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    LineIcon(Icons.CHEVRON_DOWN, Color.White, size = 16.dp, strokeWidth = 2.4f)
                    Text(" Close", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            (listOf("esc") + (1..12).map { "F$it" }).forEach { k ->
                Box(Modifier.width(52.dp).height(h - 6.dp).padding(2.dp).clip(RoundedCornerShape(8.dp)).background(KeyHi)
                    .press { press(k.lowercase()) }, contentAlignment = Alignment.Center) {
                    Text(k, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Row { "`1234567890-=".forEach { K(it.toString()) }; K("⌫", "delete", 1.6f) }
        Row { K("tab", "tab", 1.5f); "qwertyuiop[]".forEach { K(it.toString()) } }
        Row { "asdfghjkl;'".forEach { K(it.toString()) }; K("return", "return", 1.8f) }
        Row { K("⇧", w = 1.6f, mod = "shift"); "zxcvbnm,./".forEach { K(it.toString()) }; K("⇧", w = 1.6f, mod = "shift") }
        Row {
            K("fn", mod = "fn"); K("⌃", mod = "ctrl"); K("⌥", mod = "alt"); K("⌘", w = 1.4f, mod = "cmd")
            K("space", "space", 4.2f); K("⌘", w = 1.4f, mod = "cmd")
            K("←", "arrow-left"); K("↑", "arrow-up"); K("↓", "arrow-down"); K("→", "arrow-right")
        }
    }
}

/** Landscape + hidden system bars while full screen; restores both when leaving. */
@Composable
private fun FullscreenEffect(on: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    val view = LocalView.current
    DisposableEffect(on) {
        val ctrl = WindowCompat.getInsetsController(activity.window, view)
        if (on) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ctrl.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** Glowing AI orb that opens the assistant on top of the live screen. */
@Composable
private fun AiButton(open: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).press(onClick), contentAlignment = Alignment.Center) {
        AiRing(!open, Modifier.fillMaxSize())
        Box(Modifier.size(32.dp).background(if (open) Lime else Violet, CircleShape), contentAlignment = Alignment.Center) {
            LineIcon(Icons.SPARK, if (open) Ink else Color.White, size = 16.dp, strokeWidth = 2.4f)
        }
    }
}

/** The Mac Assistant, compact, floating over the live screen so you can ask and watch the result at once. */
@Composable
private fun AssistantPanel(vm: CoachViewModel, compact: Boolean, onClose: () -> Unit) {
    val s = vm.strings
    var text by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val mic = rememberMic { t -> text = (text + " " + t).trim() }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(vm.chat.size) { if (vm.chat.isNotEmpty()) list.animateScrollToItem(vm.chat.size - 1) }
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    // keyboard space comes off first, then the panel takes its share of what is left, so the input is always above the keyboard
    Column(Modifier.fillMaxWidth().imePadding().fillMaxHeight(if (compact) 0.75f else 0.5f)
        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xF22A1F5C), Color(0xF7141225))), shape)
        .border(1.dp, Color(0x33B4A2FF), shape)
        .navigationBarsPadding().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Buddy(Violet, when {
                vm.phase == com.englishcoach.app.Phase.Listening -> Mood.Listening
                vm.speaking -> Mood.Talking
                else -> Mood.Idle
            }, Modifier.size(42.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.assistant, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(s.assistantSub, color = Color(0xFFB4A2FF), fontSize = 12.sp)
            }
            Box(Modifier.size(34.dp).background(Color(0x22FFFFFF), CircleShape).press(onClose), contentAlignment = Alignment.Center) {
                LineIcon(Icons.COLLAPSE, Color.White, size = 16.dp)
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.chat.isEmpty()) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.macIdeas.forEach { idea ->
                        Text(idea, Modifier.press { vm.askMac(idea) }.background(Color(0x22FFFFFF), CircleShape)
                            .border(1.dp, Color(0x33FFFFFF), CircleShape).padding(horizontal = 14.dp, vertical = 9.dp),
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            items(vm.chat.size) { i ->
                val m = vm.chat[i]
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromMe) Alignment.End else Alignment.Start) {
                    Text(m.text, Modifier.widthIn(max = 320.dp)
                        .background(if (m.fromMe) Violet else Color(0x1FFFFFFF), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                        color = Color.White, fontSize = 14.sp, lineHeight = 19.sp)
                    if (m.working && m.steps.isNotEmpty()) m.steps.takeLast(2).forEach {
                        Text("▸ " + it.removePrefix("▸ "), Modifier.widthIn(max = 320.dp).padding(top = 3.dp), color = Color(0xFFB4A2FF), fontSize = 11.sp, maxLines = 1)
                    }
                    if (m.denied.isNotEmpty()) Column(Modifier.padding(top = 6.dp).widthIn(max = 320.dp).background(lt(Color(0xFFFFF3C4)), RoundedCornerShape(14.dp)).padding(10.dp)) {
                        Text("Needs your permission: " + m.denied.joinToString { it.removePrefix("Bash(").removeSuffix(")").removeSuffix(":*") },
                            color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Allow & continue", Modifier.padding(top = 6.dp).press { vm.allowAgent(m) }.background(Violet, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 8.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                    if (m.confirmId != null) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(s.yes, Modifier.press { vm.confirmAction(m, true) }.background(Lime, CircleShape).padding(horizontal = 16.dp, vertical = 9.dp),
                            color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        Text(s.no, Modifier.press { vm.confirmAction(m, false) }.background(Color(0x22FFFFFF), CircleShape).padding(horizontal = 16.dp, vertical = 9.dp),
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
        if (mic.listening) {
            VoiceWave(mic.level, Modifier.fillMaxWidth().height(30.dp))
            if (mic.partial.isNotBlank()) Text("“${mic.partial}”", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth().background(Color(0x1FFFFFFF), CircleShape).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (text.isEmpty()) Text(if (mic.listening) "🔴 Sun raha hoon… khatam ho to ⏹" else s.assistantHint, color = Color(0xFF8B86A8), fontSize = 13.sp, maxLines = 1)
                BasicTextField(text, { text = it }, Modifier.fillMaxWidth(), textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Lime), singleLine = true)
            }
            Box(Modifier.size(40.dp).background(if (mic.listening) Color(0xFFFF3D6E) else Violet, CircleShape).press { mic.toggle() },
                contentAlignment = Alignment.Center) {
                if (mic.listening) Text("⏹", fontSize = 16.sp) else LineIcon(Icons.MIC, Color.White, size = 18.dp)
            }
            if (text.isNotBlank()) {
                Spacer(Modifier.width(5.dp))
                Box(Modifier.size(40.dp).background(Lime, CircleShape).press { mic.stop(); vm.askMac(text.trim()); text = "" },
                    contentAlignment = Alignment.Center) { LineIcon(Icons.ARROW, Ink, size = 18.dp, strokeWidth = 2.4f) }
            }
        }
    }
}

/** Shown when the Mac has more than one screen: pick which display to watch and control. */
@Composable
private fun DisplaySwitch(vm: CoachViewModel) {
    Row(Modifier.background(Key, CircleShape).padding(3.dp)) {
        (0 until vm.displayCount).forEach { i ->
            Box(Modifier.size(36.dp).background(if (vm.display == i) Violet else Color.Transparent, CircleShape).press { vm.display = i },
                contentAlignment = Alignment.Center) {
                Text("${i + 1}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
    }
}

/** Big, thumb-friendly buttons for the most used Mac actions, filling the space under the picture. */
@Composable
private fun QuickGrid(vm: CoachViewModel, modifier: Modifier, onAll: () -> Unit) {
    val picks = listOf("Spotlight", "App switcher", "Mission Control", "Copy", "Paste", "Undo",
        "Close window", "Show desktop", "Play / Pause", "Volume −", "Volume +", "Screenshot")
    val actions = MAC_CONTROLS.flatMap { it.second }.associateBy { it.label }
    var done by remember { mutableStateOf<String?>(null) }
    var spot by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done != null) { kotlinx.coroutines.delay(900); done = null } }
    if (spot) SpotDialog(vm) { spot = false }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Adaptive(104.dp), modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(picks.size) { i ->
            val a = actions[picks[i]] ?: return@items
            val hit = done == a.label
            Column(Modifier.height(64.dp).press { if (a.label == "Spotlight") spot = true else a.run(vm); done = a.label }
                .background(if (hit) Violet else Key, RoundedCornerShape(16.dp)).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(a.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, textAlign = TextAlign.Center)
                Text(if (hit) "✓" else a.hint, color = if (hit) Lime else Color(0xFF9C97BC), fontSize = 11.sp, maxLines = 1)
            }
        }
        item {
            Column(Modifier.height(64.dp).press(onAll).background(Violet, RoundedCornerShape(16.dp)).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                LineIcon(Icons.GRID, Color.White, size = 18.dp)
                Text("All controls", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

/** The Mac screen from the H.264 stream, decoded in hardware straight onto a TextureView. */
@Composable
private fun H264View(vm: CoachViewModel, modifier: Modifier) {
    androidx.compose.ui.viewinterop.AndroidView({ ctx ->
        android.view.TextureView(ctx).apply {
            surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) = vm.attachSurface(android.view.Surface(st))
                override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean { vm.attachSurface(null); return true }
                override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
            }
        }
    }, modifier)
}

@Composable
private fun DisplayModeDialog(vm: CoachViewModel, onClose: () -> Unit) {
    androidx.compose.material3.AlertDialog(onDismissRequest = onClose,
        title = { Text("How should the phone show the Mac?", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple("mirror", "🖥 Mirror", "The phone shows the Mac's own screen (both show the same)."),
                    Triple("extend", "➕ Extend", "The phone becomes a second display. Drag windows onto it."),
                    Triple("main", "📱 Phone only", "The phone becomes the main display and the Mac's monitor goes black. It switches back by itself if you stop watching for 10 minutes."),
                ).forEach { (m, t, d) ->
                    val on = vm.displayModeName == m
                    Column(Modifier.fillMaxWidth().background(if (on) lt(Color(0xFFEDE8FF)) else lt(Color(0xFFF6F4FF)), RoundedCornerShape(16.dp))
                        .press { vm.setDisplay(m); onClose() }.padding(12.dp)) {
                        Text(t + if (on) "  ✓" else "", color = Color(0xFF1E1B3A), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        Text(d, color = Color(0xFF6B6785), fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClose) { Text("Close") } })
}


/** "Spotlight" from the phone: type a name, the Mac opens that app / project / file (no ⌘Space needed, quick on slow links). */
@Composable
private fun SpotDialog(vm: CoachViewModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun go(t: String) = scope.launch {
        if (t.isBlank()) return@launch
        msg = "Khol raha hoon…"
        val r = vm.mac("/screen/input", org.json.JSONObject().put("kind", "spot").put("text", t).put("display", vm.display))
        if (r.optBoolean("ok")) onClose() else msg = "⚠️ " + r.optString("error", "Nahi khul saka")
    }
    androidx.compose.ui.window.Dialog(onClose) {
        Column(Modifier.fillMaxWidth().background(Color(0xFF1E1B2E), RoundedCornerShape(24.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🔍 Mac par kya kholna hai?", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            BasicTextField(q, { q = it }, Modifier.fillMaxWidth().focusRequester(focus).background(Key, RoundedCornerShape(14.dp)).padding(14.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp), singleLine = true, cursorBrush = SolidColor(Lime),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Go),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { go(q) }))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Visual Studio Code", "Claude", "Cursor", "Antigravity", "Google Chrome", "Finder", "Safari", "Terminal").forEach { n ->
                    Text(n, Modifier.background(Key, CircleShape).press { go(n) }.padding(horizontal = 12.dp, vertical = 7.dp), color = Color.White, fontSize = 12.sp)
                }
            }
            if (msg.isNotBlank()) Text(msg, color = Color(0xFFFFD86B), fontSize = 12.sp)
            Text("Kholo", Modifier.fillMaxWidth().background(Lime, RoundedCornerShape(14.dp)).press { go(q) }.padding(12.dp), color = Ink,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        }
    }
}
