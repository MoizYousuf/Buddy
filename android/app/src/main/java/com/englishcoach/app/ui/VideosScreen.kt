package com.englishcoach.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/** Videos waiting for the owner's OK: watch, then Upload / Make Shorts / Fix / Skip (same as the Discord reactions). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(vm: CoachViewModel) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    val picked = remember { mutableStateListOf<String>() }          // filters; same group = OR, different groups = AND
    var sortViews by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(PAGE) }                  // how many cards are on screen; grows as you scroll
    LaunchedEffect(tab, picked.toList(), sortViews) { shown = PAGE }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(Unit) { vm.loadWaiting(); vm.loadUploaded() }
    val open = vm.waiting.firstOrNull { it.id == vm.openVideo }
    if (open != null) { VideoDetail(vm, open); return }
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(vm.reviewRefreshing, { vm.refreshVideos() }, Modifier.fillMaxSize()) {
    val waitingF = vm.waiting.filter { v ->
        val type = listOf("Shorts", "Videos").filter { it in picked }
        val st = listOf("New", "In progress").filter { it in picked }
        (type.isEmpty() || (v.short && "Shorts" in type) || (!v.short && "Videos" in type)) &&
            (st.isEmpty() || (v.choice.isEmpty() && v.status == "pending" && "New" in st) || ((v.choice.isNotEmpty() || v.status == "uploading") && "In progress" in st))
    }
    val uploadedF = vm.uploadedVideos.filter { u ->
        val type = listOf("Shorts", "Videos").filter { it in picked }
        val st = listOf("Scheduled", "Public", "Private").filter { it in picked }
        val sch = u.privacy == "private" && u.publishAt.isNotBlank()
        (type.isEmpty() || (u.short && "Shorts" in type) || (!u.short && "Videos" in type)) &&
            (st.isEmpty() || (sch && "Scheduled" in st) || (u.privacy == "public" && "Public" in st) || (u.privacy == "private" && !sch && "Private" in st)) &&
            ("TikTok" !in picked || u.tiktok)
    }.let { if (sortViews) it.sortedByDescending { u -> u.views } else it }
    val total = if (tab == 0) waitingF.size else uploadedF.size
    LaunchedEffect(list, total) {
        androidx.compose.runtime.snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= list.layoutInfo.totalItemsCount - 3 && shown < total) shown += PAGE }
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp), state = list,
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BackHeader("Videos") { vm.screen = Screen.Studio } }
                Pill(if (vm.reviewRefreshing) "…" else "↻ Refresh", outline = true) { vm.refreshVideos() }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().background(Pastel, CircleShape).padding(4.dp)) {
                listOf("Waiting (${vm.waiting.size})", "Uploaded (${vm.uploadedVideos.size})").forEachIndexed { i, t ->
                    Text(t, Modifier.weight(1f).background(if (tab == i) Violet else Color.Transparent, CircleShape).press { tab = i }
                        .padding(vertical = 10.dp), color = if (tab == i) Color.White else Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        if (tab == 1) item { YtStatsCard(vm) }
        item { PlanCard(vm) }
        item {
            val opts = if (tab == 0) listOf("Shorts", "Videos", "New", "In progress")
                       else listOf("Scheduled", "Public", "Private", "Shorts", "Videos", "TikTok")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (tab == 1) Text(if (sortViews) "↓ Most views" else "↓ Newest", Modifier.background(lt(Color(0xFFFFF3C4)), CircleShape)
                    .press { sortViews = !sortViews }.padding(horizontal = 12.dp, vertical = 7.dp), color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("All", Modifier.background(if (picked.isEmpty()) Ink else Pastel, CircleShape).press { picked.clear() }
                    .padding(horizontal = 12.dp, vertical = 7.dp), color = if (picked.isEmpty()) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                opts.forEach { o ->
                    val on = o in picked
                    Text((if (on) "✓ " else "") + o, Modifier.background(if (on) Violet else Pastel, CircleShape)
                        .press { if (on) picked.remove(o) else picked.add(o) }.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = if (on) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        item { Text("Showing ${minOf(shown, total)} of $total", color = Muted, fontSize = 12.sp) }
        vm.reviewMsg?.let { m -> item { Note(m) { vm.reviewMsg = null } } }
        if (tab == 0) {
            if (waitingF.isEmpty()) item { Text(if (picked.isEmpty()) "Nothing is waiting. New videos land here when they're ready." else "No videos match these filters.", color = Muted) }
            items(waitingF.take(shown), key = { it.id }) { v ->
                Row(Modifier.fillMaxWidth().card(22).press { vm.openVideo = v.id }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumb(vm, v.id, v.hasThumb, Modifier.width(120.dp).aspectRatio(16f / 9f))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(v.title, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Tag(if (v.short) "Short" else "Video", if (v.short) Color(0xFFFFE3EC) else Color(0xFFEDE8FF))
                            Text(v.posted, color = Muted, fontSize = 11.sp)
                        }
                        when {
                            v.problem.isNotBlank() -> Text("⚠️ " + v.problem, color = Color(0xFFE11D48), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            v.publishAt.isNotBlank() -> Text("🕒 Scheduled", color = Color(0xFF16A34A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            v.status == "uploading" || v.choice == "✅" -> Text("⏳ Uploading…", color = Color(0xFF16A34A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            v.choice == "📱" -> Text("📱 Making Shorts…", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        VideoScoreRow(vm, v.id)
                    }
                }
            }
        } else {
            if (uploadedF.isEmpty()) item { Text(if (vm.uploadedVideos.isEmpty()) "Loading uploaded videos…" else "No videos match these filters.", color = Muted) }
            items(uploadedF.take(shown), key = { "u" + it.id }) { u -> UploadedCard(vm, u) }
        }
        if (shown < total) item {
            Text("Loading more…", Modifier.fillMaxWidth().padding(12.dp), color = Muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    }
}

private const val PAGE = 8

@Composable
private fun UploadedCard(vm: CoachViewModel, u: CoachViewModel.UploadedVideo) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var moving by remember { mutableStateOf(false) }
    if (moving) ScheduleSheet(vm, u.short, "Change when it goes public", { d, t -> vm.reschedule(u, d, t) }) { moving = false }
    val scheduled = u.privacy == "private" && u.publishAt.isNotBlank()
    val (chip, chipBg) = when {
        scheduled -> "🕒 Scheduled ${localTime(u.publishAt)}" to Color(0xFFFFF3C4)
        u.privacy == "public" -> "🌍 Public" to Color(0xFFDCFCE7)
        u.privacy.isBlank() -> "…" to Color(0xFFEDE8FF)
        else -> "🔒 ${u.privacy.replaceFirstChar { it.uppercase() }}" to Color(0xFFFFE3EC)
    }
    Column(Modifier.fillMaxWidth().card(22).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetThumb(u.thumb, Modifier.width(120.dp).aspectRatio(16f / 9f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(u.title, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag(if (u.short) "Short" else "Video", if (u.short) Color(0xFFFFE3EC) else Color(0xFFEDE8FF))
                    Tag(chip, chipBg)
                }
                Text("Uploaded ${u.uploaded}" + if (u.tiktok) " · TikTok inbox ✓" else "", color = Muted, fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat2("👁", u.views, "views", Modifier.weight(1f)); Stat2("👍", u.likes, "likes", Modifier.weight(1f)); Stat2("💬", u.comments, "comments", Modifier.weight(1f))
        }
        if (u.canReschedule) Text("🕒 Change time", Modifier.fillMaxWidth().background(lt(Color(0xFFFFF3C4)), CircleShape).press { moving = true }
            .padding(vertical = 10.dp), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("▶ Open", Modifier.weight(1f).background(Color(0xFFFF0033), CircleShape).press {
                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(u.link)))
            }.padding(vertical = 10.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("Copy link", Modifier.weight(1f).background(Pastel, CircleShape).press {
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("link", u.link))
            }.padding(vertical = 10.dp), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("Share", Modifier.weight(1f).background(Pastel, CircleShape).press {
                ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "${u.title}\n${u.link}"), "Share"))
            }.padding(vertical = 10.dp), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

private fun localTime(iso: String) = runCatching {
    val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    java.text.SimpleDateFormat("d MMM h:mm a", java.util.Locale.getDefault()).format(f.parse(iso.replace(".000Z", "Z"))!!)
}.getOrDefault("")

@Composable
private fun Stat2(icon: String, n: Int, label: String, modifier: Modifier) =
    Column(modifier.background(Paper, RoundedCornerShape(14.dp)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon ${if (n >= 1000) "%.1fK".format(n / 1000f) else "$n"}", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Text(label, color = Muted, fontSize = 11.sp)
    }

@Composable
private fun NetThumb(url: String, modifier: Modifier) {
    var img by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNotBlank()) img = withContext(Dispatchers.IO) {
            runCatching { URL(url).readBytes().let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Violet, Pink))), contentAlignment = Alignment.Center) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Text("▶", color = Color.White, fontSize = 24.sp)
    }
}

@Composable
private fun VideoDetail(vm: CoachViewModel, v: CoachViewModel.WaitingVideo) {
    var ask by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var fixing by remember { mutableStateOf(false) }
    var note by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf(false) }
    if (scheduling) ScheduleSheet(vm, v.short, "When should it go public?", { d, t -> vm.chooseVideo(v, "✅", d, t) }) { scheduling = false }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader(if (v.short) "Short" else "Video") { vm.openVideo = null } }
        item {
            LaunchedEffect(v.id) { vm.refreshMediaToken() }
            val url = vm.mediaUrl(v.id, "video")
            Box(Modifier.fillMaxWidth().aspectRatio(if (v.short) 9f / 12f else 16f / 9f).clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
                var playError by remember(v.id) { mutableStateOf<String?>(null) }
                if (v.problem.isNotBlank() || playError != null) Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️", fontSize = 30.sp)
                    Text(v.problem.ifBlank { playError ?: "" }, color = Color.White, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("Tap to try again", Modifier.padding(top = 8.dp).press { playError = null; vm.refreshMediaToken(); vm.loadWaiting() }.background(Color.White.copy(alpha = .2f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 7.dp), color = Color.White, fontSize = 13.sp)
                } else if (url != null) {
                    FunPlayer(url, v.id, v.short, Modifier.fillMaxSize()) { playError = it }
                }
            }
        }
        item { Text(v.title, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 23.sp) }
        vm.reviewMsg?.let { m -> item { Note(m) { vm.reviewMsg = null } } }
        if (v.error.isNotBlank() && (v.choice.isNotEmpty() || v.status == "uploading")) item {
            if (v.netRetry) Text("🔄 The internet dropped for a moment while uploading. It retries by itself; nothing to do.",
                Modifier.fillMaxWidth().background(lt(Color(0xFFFFF7E0)), RoundedCornerShape(12.dp)).padding(10.dp), color = Ink, fontSize = 12.sp)
            else Text("Last try failed: ${v.error}", color = Color(0xFFE11D48), fontSize = 12.sp)
        }
        item {
            val busy = v.status == "uploading" || v.choice.isNotEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                v.suggest?.takeIf { !busy }?.let { sg ->
                    Column(Modifier.fillMaxWidth().background(lt(Color(0xFFE6F7EE)), RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("⭐ Suggested time (Pakistan)", color = Color(0xFF15803D), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        Text(sg.optString("label"), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        Text(sg.optString("why"), color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                    Big("✅  OK, schedule for ${sg.optString("label")}", Brush.horizontalGradient(listOf(Color(0xFF22C55E), Color(0xFF14B8A6))), true) {
                        vm.chooseVideo(v, "✅", sg.optInt("days"), sg.optString("time"))
                    }
                }
                Big(if (v.suggest != null) "🕒  Pick another time" else "✅  Upload / schedule on YouTube",
                    if (v.suggest != null) Brush.horizontalGradient(listOf(Color(0xFF64748B), Color(0xFF475569))) else Brush.horizontalGradient(listOf(Color(0xFF22C55E), Color(0xFF14B8A6))), !busy) {
                    scheduling = true
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!v.short) Big("📱 Make Shorts", Brush.horizontalGradient(listOf(Pink, Violet)), !busy, Modifier.weight(1f)) { vm.chooseVideo(v, "📱") }
                    Big("✏️ Ask for a fix", Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFF97316))), true, Modifier.weight(1f)) { fixing = true }
                }
                Text("Skip this video", Modifier.align(Alignment.CenterHorizontally).press { ask = "Skip \"${v.title.take(60)}\"? It won't be uploaded." to { vm.chooseVideo(v, "❌") } }
                    .padding(10.dp), color = Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (busy) Text(if (v.choice == "📱") "📱 Making Shorts…" else "⏳ Uploading… you'll get a notification", color = Violet, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        if (fixing) item {
            Column(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What should change?", color = Ink, fontWeight = FontWeight.ExtraBold)
                Box(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(14.dp)).padding(12.dp)) {
                    if (note.isEmpty()) Text("e.g. make the title shorter, cut the blurry end, louder music…", color = Muted, fontSize = 13.sp)
                    BasicTextField(note, { note = it }, Modifier.fillMaxWidth(), textStyle = TextStyle(color = Ink, fontSize = 14.sp), cursorBrush = SolidColor(Violet))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Send to Claude") { vm.fixVideo(v, note); note = ""; fixing = false }
                    Pill("Cancel", outline = true) { fixing = false }
                }
            }
        }
        item { Text(if (more) v.description else v.description.take(220) + if (v.description.length > 220) "…" else "",
            Modifier.press { more = !more }, color = Ink.copy(alpha = .8f), fontSize = 13.sp, lineHeight = 19.sp) }
        if (v.tags.isNotEmpty()) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                v.tags.take(30).forEach { Tag("#$it", Color(0xFFEDE8FF)) }
            }
        }
        if (v.hasThumb) item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Thumbnail", color = Ink, fontWeight = FontWeight.ExtraBold)
                Thumb(vm, v.id, true, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            }
        }
    }
    ask?.let { (q, go) ->
        AlertDialog(onDismissRequest = { ask = null }, text = { Text(q) },
            confirmButton = { TextButton({ go(); ask = null }) { Text("Yes") } },
            dismissButton = { TextButton({ ask = null }) { Text("No") } })
    }
}

@Composable
private fun Thumb(vm: CoachViewModel, id: String, has: Boolean, modifier: Modifier) {
    var img by remember(id) { mutableStateOf<ImageBitmap?>(null) }
    val url = if (has) vm.mediaUrl(id, "thumb") else null
    LaunchedEffect(url) {
        if (url != null) img = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = URL(url).readBytes()
                val o = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Violet, Pink))), contentAlignment = Alignment.Center) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Text("🎬", fontSize = 24.sp)
    }
}

@Composable
private fun Big(label: String, bg: Brush, enabled: Boolean, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) =
    Box(modifier.then(if (enabled) Modifier.press(onClick) else Modifier).clip(RoundedCornerShape(18.dp)).background(bg)
        .background(if (enabled) Color.Transparent else Color.White.copy(alpha = .5f)).padding(vertical = 15.dp), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }

@Composable
private fun Tag(t: String, bg: Color) =
    Text(t, Modifier.background(bg, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp), color = if (bg.luminance() > .5f) InkOnLight else Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)

@Composable
private fun Note(m: String, onClose: () -> Unit) =
    Text(m, Modifier.fillMaxWidth().background(lt(Color(0xFFE6F7EE)), RoundedCornerShape(14.dp)).press(onClose).padding(12.dp), color = Ink, fontSize = 13.sp)

/** Pick when a video goes public: Auto (next free slot), now, or a day at the usual time or one you choose. */
@Composable
private fun ScheduleSheet(vm: CoachViewModel, short: Boolean, title: String, onPick: (Int?, String?) -> Unit, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var days by remember { mutableStateOf(if (vm.autoSchedule) -2 else 1) }  // -2 = auto, -1 = now
    var time by remember { mutableStateOf(vm.slots.lastOrNull() ?: if (short) vm.shortTime else vm.longTime) }
    val next = vm.nextSlot?.let { "${vm.dayName(it.first)} ${label(it.second)}" } ?: "next free slot"
    AlertDialog(onDismissRequest = onClose,
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Day", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-2 to "✨ Auto", -1 to "Now", 0 to "Today", 1 to "Tomorrow", 2 to "Day after", 3 to "In 3 days", 4 to "In 4 days").forEach { (d, t) ->
                        Text(t, Modifier.background(if (days == d) Violet else Pastel, CircleShape).press { days = d }
                            .padding(horizontal = 12.dp, vertical = 8.dp), color = if (days == d) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                if (days >= 0) {
                    Text("Time (Pakistan)", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (vm.slots + vm.bestSlots + listOf(time)).distinct().forEach { t ->
                            Text(label(t) + if (t in vm.bestSlots) " ⭐" else "", Modifier.background(if (time == t) Violet else Pastel, CircleShape).press { time = t }
                                .padding(horizontal = 12.dp, vertical = 8.dp), color = if (time == t) Color.White else Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text("Other…", Modifier.background(lt(Color(0xFFFFF3C4)), CircleShape).press {
                            val (h, m) = time.split(":").map { it.toInt() }
                            android.app.TimePickerDialog(ctx, { _, hh, mm -> time = "%02d:%02d".format(hh, mm) }, h, m, false).show()
                        }.padding(horizontal = 12.dp, vertical = 8.dp), color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Text(when {
                    days == -2 -> "Goes public $next (the next day/time in your plan with no video yet)."
                    days == -1 -> "Goes public right away."
                    else -> "Goes public ${vm.dayName(days)} at ${label(time)}."
                }, color = Ink.copy(alpha = .8f), fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton({ onPick(if (days == -2) null else days, if (days >= 0) time else null); onClose() }) {
            Text(if (days == -1) "Now" else "Schedule", fontWeight = FontWeight.ExtraBold) } },
        dismissButton = { TextButton(onClose) { Text("Cancel") } })
}

fun label(t: String) = runCatching {
    val (h, m) = t.split(":").map { it.toInt() }; "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
}.getOrDefault(t)

/** Your upload plan: how many videos a day, at which times, and whether ✅ picks the next free slot by itself. */
@Composable
private fun PlanCard(vm: CoachViewModel) {
    var edit by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF6D5BFF), Color(0xFFB45CFF))), RoundedCornerShape(20.dp))
        .press { edit = true }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("📅", fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${vm.slots.size} video${if (vm.slots.size > 1) "s" else ""} a day · ${vm.slots.joinToString(", ") { label(it) }}",
                color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text((if (vm.autoSchedule) "Auto ON · next: " + (vm.nextSlot?.let { "${vm.dayName(it.first)} ${label(it.second)}" } ?: "—") else "Auto OFF · you pick each time"),
                color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
        }
        Text("Edit", color = Color.White, fontWeight = FontWeight.ExtraBold)
    }
    if (edit) PlanDialog(vm) { edit = false }
}

@Composable
private fun PlanDialog(vm: CoachViewModel, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val slots = remember { mutableStateListOf<String>().apply { addAll(vm.slots) } }
    var auto by remember { mutableStateOf(vm.autoSchedule) }
    val defaults = listOf("12:00", "17:00", "19:00", "21:00", "09:00", "15:00")
    fun setCount(n: Int) {
        while (slots.size > n) slots.removeAt(slots.lastIndex)
        while (slots.size < n) slots.add((vm.bestSlots + defaults).first { it !in slots })
    }
    AlertDialog(onDismissRequest = onClose,
        title = { Text("Upload plan", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Videos per day", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { n ->
                        Text("$n", Modifier.size(44.dp).background(if (slots.size == n) Violet else Pastel, CircleShape).press { setCount(n) }
                            .padding(top = 11.dp), color = if (slots.size == n) Color.White else Ink, fontWeight = FontWeight.ExtraBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Text("Times (tap to change)", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    slots.forEachIndexed { i, t ->
                        Text("${i + 1}: ${label(t)}", Modifier.background(Pastel, CircleShape).press {
                            val (h, m) = t.split(":").map { it.toInt() }
                            android.app.TimePickerDialog(ctx, { _, hh, mm -> slots[i] = "%02d:%02d".format(hh, mm) }, h, m, false).show()
                        }.padding(horizontal = 12.dp, vertical = 8.dp), color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-schedule", color = Ink, fontWeight = FontWeight.ExtraBold)
                        Text("✅ puts each video in the next free slot", color = Muted, fontSize = 12.sp)
                    }
                    androidx.compose.material3.Switch(auto, { auto = it })
                }
                Column(Modifier.fillMaxWidth().background(lt(Color(0xFFFFF7E0)), RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("⭐ Best time from YouTube Studio", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    if (vm.bestSlots.isNotEmpty()) {
                        Text(vm.bestSlots.joinToString(" · ") { label(it) }, color = Ink, fontWeight = FontWeight.Bold)
                        if (vm.bestNote.isNotBlank()) Text(vm.bestNote, color = Muted, fontSize = 12.sp)
                        Text("Use these times", Modifier.press { slots.clear(); slots.addAll(vm.bestSlots.take(4)) }
                            .background(Violet, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    } else Text("Not checked yet", color = Muted, fontSize = 12.sp)
                    Text(if (vm.bestChecking) "Reading YouTube Studio…" else "↻ Find best time", Modifier.press { vm.findBestTime() }
                        .background(Color.White, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp), color = Violet, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton({ vm.saveSchedule(slots.toList(), auto); onClose() }) { Text("Save", fontWeight = FontWeight.ExtraBold) } },
        dismissButton = { TextButton(onClose) { Text("Cancel") } })
}
