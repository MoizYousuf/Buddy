package com.englishcoach.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CleanItem
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Screen
import java.util.Locale

private val KIND_ORDER = listOf("cache", "node_modules", "app", "download", "trash")
private val KIND_COLOR = mapOf("cache" to Violet, "node_modules" to Color(0xFF2BC46B), "app" to Pink,
    "download" to Color(0xFFFFB020), "trash" to Muted)

private fun size(b: Long) = if (b >= 1e9) String.format(Locale.US, "%.1f GB", b / 1e9) else String.format(Locale.US, "%.0f MB", b / 1e6)

/** Card on the Mac tab that opens the cleaner. */
@Composable
fun CleanerCard(vm: CoachViewModel) {
    val s = vm.strings
    LaunchedEffect(Unit) { if (vm.cleanItems.isEmpty()) vm.loadCleanup() }
    val pending = vm.cleanItems.count { it.needsDecision }
    Row(Modifier.fillMaxWidth().press { vm.screen = Screen.Cleaner; if (vm.cleanItems.isEmpty() && !vm.cleanScanning) vm.loadCleanup() }
        .background(lt(Color(0xFFFFF1E6)), RoundedCornerShape(26.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).background(Color(0xFFFFB020), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            LineIcon(Icons.BROOM, Ink, size = 24.dp, strokeWidth = 2.2f)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(s.cleaner, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink)
            Text(s.cleanerSub, fontSize = 12.sp, color = Muted)
        }
        if (pending > 0) Text(s.cleanToReview.format(pending), Modifier.background(Pink, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        LineIcon(Icons.ARROW, Ink, size = 20.dp, strokeWidth = 2.4f)
    }
}

@Composable
fun CleanerScreen(vm: CoachViewModel) {
    val s = vm.strings
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf(false) }
    val items = vm.cleanItems
    val total = items.filter { it.kind != "trash" }.sumOf { it.bytes }
    val chosen = items.filter { it.path in selected }
    val now = System.currentTimeMillis() / 1000

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { BackHeader(s.cleaner) { vm.screen = Screen.Mac } }

            // summary: big number + a stacked bar by kind
            item {
                Column(Modifier.fillMaxWidth().background(InkOnLight, RoundedCornerShape(28.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (vm.cleanScanning) {
                        val t = rememberInfiniteTransition(label = "scan")
                        val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "spin")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).rotate(a).border(3.dp, Lime, CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Text(s.scanning, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    } else {
                        Text(size(total), color = Lime, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp)
                        Text(s.canFree, color = Color(0xFFB9B3D6), fontSize = 14.sp)
                        Row(Modifier.fillMaxWidth().height(12.dp).background(Color(0xFF2E2A47), CircleShape)) {
                            KIND_ORDER.filter { it != "trash" }.forEach { k ->
                                val b = items.filter { it.kind == k }.sumOf { it.bytes }
                                if (b > 0 && total > 0) Box(Modifier.weight(b.toFloat() / total).fillMaxHeight().background(KIND_COLOR.getValue(k)))
                            }
                        }
                        Text(s.scanNow, Modifier.press { selected = emptySet(); vm.scanStorage() }
                            .background(Color(0x22FFFFFF), CircleShape).padding(horizontal = 16.dp, vertical = 9.dp),
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            val next = items.firstOrNull { it.needsDecision }
            item {
                if (!vm.cleanScanning) {
                    if (next != null) ReviewCard(vm, next, items.count { it.needsDecision }) else if (items.isNotEmpty())
                        Text("✓ " + s.allReviewed, color = Color(0xFF1FA85A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            item { Text(s.autoScan, color = Muted, fontSize = 12.sp) }
            vm.cleanMessage?.let { msg -> item { Text(msg, color = Color(0xFF1FA85A), fontWeight = FontWeight.SemiBold, fontSize = 13.sp) } }
            item { Text(s.trashNote, color = Muted, fontSize = 12.sp) }
            items.firstOrNull { it.kind == "trash" }?.let { trash ->
                item {
                    var sure by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.emptyTrash, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Ink)
                            Text(size(trash.bytes), color = Muted, fontSize = 12.sp)
                        }
                        Text(if (sure) s.emptyTrashConfirm else s.emptyTrash, Modifier.press { if (sure) { vm.emptyTrash(); sure = false } else sure = true }
                            .background(if (sure) Color(0xFFE5484D) else lt(Color(0xFFFFE8E8)), CircleShape).padding(horizontal = 14.dp, vertical = 9.dp),
                            color = if (sure) Color.White else Color(0xFFC62828), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }

            KIND_ORDER.forEach { kind ->
                val group = items.filter { it.kind == kind }
                if (group.isEmpty()) return@forEach
                item {
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(KIND_COLOR.getValue(kind), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(s.kinds[kind] ?: kind, Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink)
                        Text(size(group.sumOf { it.bytes }), color = Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                group.forEach { it -> item(key = it.path) { CleanRow(it, it.path in selected, s, now) {
                    if (it.kind != "trash") selected = if (it.path in selected) selected - it.path else selected + it.path
                } } }
            }
        }

        // action bar
        if (chosen.isNotEmpty()) {
            Text(if (confirm) s.confirmTrash else s.moveSelected.format(chosen.size, size(chosen.sumOf { it.bytes })),
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(18.dp).fillMaxWidth()
                    .press {
                        if (confirm) { vm.trash(chosen); selected = emptySet(); confirm = false } else confirm = true
                    }
                    .background(if (confirm) Pink else Violet, CircleShape).padding(vertical = 18.dp),
                textAlign = TextAlign.Center, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun CleanRow(it: CleanItem, on: Boolean, s: com.englishcoach.app.Strings, now: Long, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().card(20).press(onToggle).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (it.kind != "trash") {
            Box(Modifier.size(24.dp).background(if (on) Violet else Color.Transparent, RoundedCornerShape(7.dp))
                .border(2.dp, if (on) Violet else Color(0xFFD5CFEA), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                if (on) LineIcon(Icons.CHECK, Color.White, size = 14.dp, strokeWidth = 3f)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            val title = if (it.kind == "node_modules") it.note.substringAfter("Project: ").substringBefore(" —") else it.name.removeSuffix(".app")
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val age = it.lastUsed?.let { t -> s.lastUsedAgo.format(((now - t) / 86400).toInt()) } ?: if (it.kind == "app") s.neverUsed else ""
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerdictBadge(it.verdict, s)
                Spacer(Modifier.width(6.dp))
                Text(listOf(age, it.path.replace(Regex("^/Users/[^/]+"), "~")).filter { x -> x.isNotBlank() }.joinToString(" · "),
                    fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(size(it.bytes), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = KIND_COLOR[it.kind] ?: Ink)
    }
}

private val VERDICT_COLOR = mapOf("safe" to Color(0xFF1FA85A), "suggest" to Color(0xFFE08A00), "ask" to Color(0xFF3B82F6),
    "keep" to Color(0xFF8B86A8), "info" to Color(0xFF8B86A8))

@Composable
private fun VerdictBadge(verdict: String, s: com.englishcoach.app.Strings) {
    val c = VERDICT_COLOR[verdict] ?: Muted
    Text(s.verdicts[verdict] ?: verdict, Modifier.background(c.copy(alpha = 0.14f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
        color = c, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
}

/** One item at a time: what it is, why the Mac thinks so, and three choices. */
@Composable
private fun ReviewCard(vm: CoachViewModel, it: CleanItem, remaining: Int) {
    val s = vm.strings
    val accent = VERDICT_COLOR[it.verdict] ?: Violet
    Column(Modifier.fillMaxWidth().card(26).border(2.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(26.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.reviewTitle2, Modifier.weight(1f), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("1 / $remaining", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val title = if (it.kind == "node_modules") it.note.substringAfter("Project: ").substringBefore(" —") + " · node_modules" else it.name.removeSuffix(".app")
                Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Ink, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                VerdictBadge(it.verdict, s)
            }
            Text(size(it.bytes), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = accent)
        }
        Text(it.reason, fontSize = 14.sp, color = Ink, lineHeight = 19.sp)
        if (it.growth > 100_000_000) Text("↑ " + s.grewBy.format(size(it.growth)), color = Pink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(it.path.replace(Regex("^/Users/[^/]+"), "~"), fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.keepIt, Modifier.weight(1f).press { vm.decide(it, "keep") }.border(2.dp, Color(0xFFE4E0F5), CircleShape).padding(vertical = 12.dp),
                textAlign = TextAlign.Center, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text(s.later, Modifier.weight(0.8f).press { vm.decide(it, "later") }.background(Pastel, CircleShape).padding(vertical = 12.dp),
                textAlign = TextAlign.Center, color = Muted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(s.removeIt, Modifier.weight(1.3f).press { vm.decide(it, "remove") }.background(accent, CircleShape).padding(vertical = 12.dp),
                textAlign = TextAlign.Center, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }
}
