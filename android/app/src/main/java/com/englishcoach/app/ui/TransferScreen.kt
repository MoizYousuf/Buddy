package com.englishcoach.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Screen

/** Clipboard both ways, send photos/files to the Mac, and browse/download the Mac's Downloads, Desktop, Documents. */
@Composable
fun TransferScreen(vm: CoachViewModel) {
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { vm.sendFiles(it) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.sendFiles(it) }
    LaunchedEffect(vm.filePlace, vm.fileSub) { vm.loadFiles() }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("Clipboard & files") { vm.screen = Screen.Mac } }
        vm.transferMsg?.let { m -> item {
            Text(m, Modifier.fillMaxWidth().background(lt(Color(0xFFE6F7EE)), RoundedCornerShape(14.dp)).press { vm.transferMsg = null }.padding(12.dp), color = Ink, fontSize = 13.sp)
        } }
        vm.uploading?.let { m -> item { Text("⏳ $m", color = Violet, fontWeight = FontWeight.Bold, fontSize = 13.sp) } }
        item {
            Column(Modifier.fillMaxWidth().card(24).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📋 Clipboard", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Phone → Mac", Modifier.weight(1f), listOf(Violet, Pink)) { vm.sendClipToMac() }
                    Btn("Mac → Phone", Modifier.weight(1f), listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))) { vm.getClipFromMac() }
                }
                Text("Copy on one, tap, then paste on the other.", color = Muted, fontSize = 12.sp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().card(24).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📤 Send to Mac", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("🖼 Photos & videos", Modifier.weight(1f), listOf(Color(0xFFF59E0B), Color(0xFFF97316))) {
                        pickMedia.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                    Btn("📄 Files", Modifier.weight(1f), listOf(Color(0xFF22C55E), Color(0xFF14B8A6))) { pickFiles.launch(arrayOf("*/*")) }
                }
                Text("They land in Downloads › From Phone. Tip: in any app, Share → Buddy also sends to the Mac.", color = Muted, fontSize = 12.sp)
            }
        }
        item { Text("📥 Files on the Mac", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("From Phone", "Downloads", "Desktop", "Documents").forEach { p ->
                    val on = vm.filePlace == p
                    Text(p, Modifier.background(if (on) Violet else Pastel, CircleShape).press { vm.filePlace = p; vm.fileSub = "" }
                        .padding(horizontal = 12.dp, vertical = 7.dp), color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        if (vm.fileSub.isNotEmpty()) item {
            Text("‹ ${vm.filePlace}/${vm.fileSub}", Modifier.press { vm.fileSub = vm.fileSub.substringBeforeLast('/', "") }, color = Violet, fontWeight = FontWeight.Bold)
        }
        if (vm.macFiles.isEmpty()) item { Text("Nothing here yet.", color = Muted) }
        items(vm.macFiles, key = { it.optString("name") }) { f ->
            val dir = f.optBoolean("dir")
            val name = f.optString("name")
            Row(Modifier.fillMaxWidth().card(18).press {
                    if (dir) vm.fileSub = if (vm.fileSub.isEmpty()) name else "${vm.fileSub}/$name" else vm.downloadFile(name)
                }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (dir) "📁" else icon(name), fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text((if (dir) "Folder" else size(f.optLong("bytes"))) + " · " + ago(f.optDouble("t")), color = Muted, fontSize = 11.sp)
                }
                if (!dir) Text("⬇", Modifier.background(Pastel, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp), color = Violet, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.width(6.dp))
                Text("🔍", Modifier.press { vm.revealOnMac(name) }.padding(4.dp), fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun Btn(t: String, modifier: Modifier, colors: List<Color>, onClick: () -> Unit) =
    Text(t, modifier.background(Brush.horizontalGradient(colors), RoundedCornerShape(16.dp)).press(onClick).padding(vertical = 13.dp),
        color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = TextAlign.Center)

private fun icon(n: String) = when (n.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg", "png", "heic", "gif", "webp" -> "🖼"; "mp4", "mov", "mkv" -> "🎬"; "pdf" -> "📕"; "zip", "dmg" -> "📦"
    "mp3", "wav", "m4a" -> "🎵"; "doc", "docx", "txt", "md" -> "📄"; "xls", "xlsx", "csv" -> "📊"; "apk" -> "🤖"; else -> "📄"
}

private fun size(b: Long) = when { b >= 1L shl 30 -> "%.1f GB".format(b / 1e9); b >= 1L shl 20 -> "%.1f MB".format(b / 1e6); b >= 1024 -> "${b / 1024} KB"; else -> "$b B" }

private fun ago(t: Double): String {
    val s = (System.currentTimeMillis() / 1000.0 - t).toLong()
    return when { s < 3600 -> "${s / 60} min ago"; s < 86400 -> "${s / 3600} h ago"; else -> "${s / 86400} d ago" }
}
