package com.englishcoach.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Phase
import com.englishcoach.app.Screen

/** Shown on the Mac tab until this phone is paired; once paired, a small status row with Unpair. */
@Composable
fun PairCard(vm: CoachViewModel) {
    val s = vm.strings
    if (vm.paired) {
        Row(Modifier.fillMaxWidth().background(lt(Color(0xFFE9F9EF)), RoundedCornerShape(20.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            LineIcon(Icons.LOCK, Color(0xFF1FA85A), size = 18.dp)
            Spacer(Modifier.width(10.dp))
            Text(s.pairedLabel, Modifier.weight(1f), color = Color(0xFF1A6B3E), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(s.unpair, Modifier.press { vm.forgetPairing() }.padding(6.dp), color = Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        return
    }
    var code by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().background(InkOnLight, RoundedCornerShape(28.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(Lime, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { LineIcon(Icons.LOCK, Ink) }
            Spacer(Modifier.width(12.dp))
            Text(s.pairTitle, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 21.sp)
        }
        Text(s.pairText, color = Color(0xFFB9B3D6), fontSize = 13.sp, lineHeight = 18.sp)
        if (vm.pairStep == 0) {
            // The Mac's address: its Tailscale IP (Tailscale app on the Mac › This Mac) or name; port 8787 is added for you.
            var addr by androidx.compose.runtime.saveable.rememberSaveable {
                mutableStateOf(vm.serverUrl.removePrefix("http://").removeSuffix(":8787"))
            }
            Box(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (addr.isEmpty()) Text("Mac address, e.g. 100.101.102.103", color = Color(0xFFA59FC0))
                BasicTextField(addr, { addr = it.trim() }, Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true, cursorBrush = SolidColor(Violet))
            }
            Text(s.pairButton, Modifier.fillMaxWidth().press { vm.startPairing(addr) }.background(Lime, CircleShape).padding(vertical = 14.dp),
                textAlign = TextAlign.Center, color = Ink, fontWeight = FontWeight.ExtraBold)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).background(Surface, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    if (code.isEmpty()) Text(s.pairCodeHint, color = Color(0xFFA59FC0))
                    BasicTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontSize = 22.sp, letterSpacing = 6.sp, fontWeight = FontWeight.ExtraBold, color = Ink),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, cursorBrush = SolidColor(Violet))
                }
                Spacer(Modifier.width(10.dp))
                Text(s.pairConfirm, Modifier.press { if (code.length == 6) vm.finishPairing(code) }
                    .background(if (code.length == 6) Lime else Color(0xFF3A3458), CircleShape).padding(horizontal = 18.dp, vertical = 16.dp),
                    color = Ink, fontWeight = FontWeight.ExtraBold)
            }
        }
        vm.pairError?.let { Text(it, color = Pink, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    }
}

@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).card(22).press(onBack), contentAlignment = Alignment.Center) { LineIcon(Icons.BACK, Ink, strokeWidth = 2.4f) }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
    }
}

@Composable
fun AssistantScreen(vm: CoachViewModel) {
    val s = vm.strings
    var text by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val mic = rememberMic { t -> text = (text + " " + t).trim() }
    val list = rememberLazyListState()
    LaunchedEffect(vm.chat.size) { if (vm.chat.isNotEmpty()) list.animateScrollToItem(vm.chat.size - 1) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { BackHeader("🖥 Mac assistant") { vm.screen = Screen.Mac } }
            if (vm.chat.isNotEmpty()) Pill("New chat", outline = true) { vm.newMacChat() }
        }
        Text("Sirf Mac ka kaam: Code / Cursor kholo, project kholo, Claude app mein chat kholo, Spotlight, files…  App ka kaam → 📱 App assistant",
            color = Muted, fontSize = 11.sp)
        LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Buddy(Violet, if (vm.speaking) Mood.Talking else Mood.Idle, Modifier.size(110.dp).fillMaxWidth()) }
            items(vm.chat) { m ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromMe) Alignment.End else Alignment.Start) {
                    Text(m.text, Modifier.widthIn(max = 300.dp)
                        .background(if (m.fromMe) Violet else Surface, RoundedCornerShape(20.dp)).padding(14.dp),
                        color = if (m.fromMe) Color.White else Ink, fontSize = 15.sp, lineHeight = 20.sp)
                    if (m.steps.isNotEmpty()) {
                        var all by remember(m.id) { mutableStateOf(false) }
                        Column(Modifier.padding(top = 6.dp).widthIn(max = 300.dp).background(lt(Color(0xFFF1EEFF)), RoundedCornerShape(14.dp))
                            .clickable { all = !all }.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(if (m.working) "Working… ${m.steps.size} steps" else "${m.steps.size} steps · tap to " + (if (all) "hide" else "see"),
                                color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            (if (all) m.steps else if (m.working) m.steps.takeLast(3) else emptyList()).forEach {
                                Text(it, color = Ink.copy(alpha = .75f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (m.denied.isNotEmpty()) Column(Modifier.padding(top = 6.dp).widthIn(max = 300.dp)
                        .background(lt(Color(0xFFFFF3C4)), RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Needs your permission", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        m.denied.forEach { Text("• " + it.removePrefix("Bash(").removeSuffix(")").removeSuffix(":*"), color = Ink, fontSize = 12.sp) }
                        Pill("Allow & continue") { vm.allowAgent(m) }
                    }
                    if (m.confirmId != null) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(s.yes) { vm.confirmAction(m, true) }
                        Pill(s.no, outline = true) { vm.confirmAction(m, false) }
                    }
                }
            }
        }
        if (mic.listening) {
            VoiceWave(mic.level, Modifier.fillMaxWidth().height(36.dp))
            Text("🔴 Sun raha hoon… aaram se bolo. Khatam ho to ⏹, phir ➤" + (if (mic.partial.isNotBlank()) "\n“${mic.partial}”" else ""),
                color = Color(0xFFE11D48), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().card(28).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (text.isEmpty()) Text(s.assistantHint, color = Color(0xFFA59FC0), fontSize = 14.sp)
                BasicTextField(text, { text = it }, Modifier.fillMaxWidth(), textStyle = TextStyle(fontSize = 15.sp, color = Ink), cursorBrush = SolidColor(Violet), maxLines = 4)
            }
            Box(Modifier.size(46.dp).background(if (mic.listening) Color(0xFFFF3D6E) else Pastel, CircleShape)
                .press { mic.toggle() }, contentAlignment = Alignment.Center) {
                if (mic.listening) Text("⏹", fontSize = 18.sp) else LineIcon(Icons.MIC, Violet, size = 20.dp)
            }
            if (text.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(46.dp).background(Violet, CircleShape).press { mic.stop(); vm.askMac(text.trim()); text = "" }, contentAlignment = Alignment.Center) {
                    LineIcon(Icons.ARROW, Color.White, size = 20.dp, strokeWidth = 2.5f)
                }
            }
        }
    }
}

