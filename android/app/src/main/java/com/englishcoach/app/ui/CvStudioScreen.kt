package com.englishcoach.app.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Phase
import com.englishcoach.app.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Blue = listOf(Color(0xFF1D4ED8), Color(0xFF7C3AED))

@Composable
fun CvStudioScreen(vm: CoachViewModel) {
    LaunchedEffect(Unit) { vm.loadCvs() }
    if (vm.cvOpen != null) { CvDetail(vm); return }
    var link by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("CV studio") { vm.screen = Screen.Home } }
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(Blue), RoundedCornerShape(24.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("📄 New CV for a job", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text("Share the job link, or paste the job description. Only your real experience is used: nothing is invented.",
                    color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                CvField(link, { link = it }, "Job link (LinkedIn, company site…)", single = true)
                CvField(text, { text = it }, "…or paste the job description here", minH = 90)
                CvField(note, { note = it }, "Anything to focus on? e.g. Agentic AI job, remote, highlight my Node.js")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (vm.phase == Phase.Listening) "⏹ Stop" else "🎤 Speak", Modifier.background(Color.White.copy(alpha = .25f), RoundedCornerShape(14.dp))
                        .press { vm.dictate { t -> note = (note + " " + t).trim() } }.padding(horizontal = 16.dp, vertical = 12.dp), color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Text("✨ Make my CV", Modifier.weight(1f).background(Surface, RoundedCornerShape(14.dp))
                        .press { if (link.isNotBlank() || text.isNotBlank() || note.isNotBlank()) { vm.createCv(link.trim(), text.trim(), note.trim()); link = ""; text = ""; note = "" } }
                        .padding(vertical = 12.dp), color = Color(0xFF1D4ED8), fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                }
                vm.cvMsg?.let { Text(it, color = Color.White, fontSize = 12.sp) }
            }
        }
        if (vm.cvs.isNotEmpty()) item { Text("Your CVs", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        items(vm.cvs, key = { it.optString("id") }) { c ->
            Row(Modifier.fillMaxWidth().card(20).press { vm.openCv(c.optString("id")) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                val sc = if (c.isNull("score")) null else c.optInt("score")
                Box(Modifier.size(46.dp).background(scoreColor(sc).copy(alpha = .15f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(sc?.toString() ?: "…", color = scoreColor(sc), fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.optString("title"), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 2)
                    Text(listOf(c.optString("company"), c.optString("stage"), c.optString("updated")).filter { it.isNotBlank() }.joinToString(" · "),
                        color = Muted, fontSize = 11.sp)
                }
                Text("→", color = Violet, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
        }
    }
}

private fun scoreColor(s: Int?) = when { s == null -> Muted; s >= 75 -> Color(0xFF16A34A); s >= 50 -> Color(0xFFF59E0B); else -> Color(0xFFE11D48) }

@Composable
private fun CvField(v: String, on: (String) -> Unit, hint: String, single: Boolean = false, minH: Int = 0) =
    Box(Modifier.fillMaxWidth().heightIn(min = minH.dp).background(Surface, RoundedCornerShape(14.dp)).padding(12.dp)) {
        if (v.isEmpty()) Text(hint, color = Muted, fontSize = 13.sp)
        BasicTextField(v, on, Modifier.fillMaxWidth(), textStyle = TextStyle(color = Ink, fontSize = 14.sp), cursorBrush = SolidColor(Violet), singleLine = single)
    }

@Composable
private fun CvDetail(vm: CoachViewModel) {
    val g = vm.cv
    val r = g?.optJSONObject("report")
    val stage = g?.optJSONObject("status")?.optString("stage").orEmpty()
    var tab by rememberSaveable { mutableStateOf(0) }
    val busy = g?.optBoolean("running") == true || stage == "writing" || stage == "editing"
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader(r?.optString("job_title")?.ifBlank { null } ?: "CV") { vm.cvOpen = null; vm.loadCvs() } }
        vm.cvMsg?.let { m -> item { Text(m, Modifier.fillMaxWidth().background(lt(Color(0xFFE6F7EE)), RoundedCornerShape(14.dp)).press { vm.cvMsg = null }.padding(12.dp), color = Ink, fontSize = 13.sp) } }
        if (busy) item {
            Column(Modifier.fillMaxWidth().card(18).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (stage == "editing") "✍️ Claude is changing your CV…" else "✍️ Claude is reading the job and writing your CV…", color = Ink, fontWeight = FontWeight.ExtraBold)
                g?.optJSONArray("steps")?.let { s -> for (i in maxOf(0, s.length() - 3) until s.length()) Text("▸ " + s.optString(i).removePrefix("▸ "), color = Muted, fontSize = 12.sp, maxLines = 1) }
            }
        }
        if (r != null && r.length() > 0) item {
            Row(Modifier.fillMaxWidth().background(Brush.linearGradient(Blue), RoundedCornerShape(20.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.optString("company").ifBlank { "Job" } + (r.optString("location").takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                    Text("Match ${r.optInt("match_score")}/100", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                }
                if (vm.cvPdf != null) Text("⬇ Share PDF", Modifier.background(Color.White, CircleShape).press { vm.shareCvPdf() }
                    .padding(horizontal = 12.dp, vertical = 8.dp), color = Color(0xFF1D4ED8), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().background(Pastel, CircleShape).padding(4.dp)) {
                listOf("Report", "Edit", "PDF").forEachIndexed { i, t ->
                    Text(t, Modifier.weight(1f).background(if (tab == i) Violet else Color.Transparent, CircleShape).press { tab = i }.padding(vertical = 10.dp),
                        color = if (tab == i) Color.White else Ink, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                }
            }
        }
        if (g == null) item { Text("Loading…", color = Muted) }
        else when (tab) {
            0 -> item { CvReport(vm, r) }
            1 -> item { CvEditor(vm, g.optString("cv"), g.optJSONArray("versions")) }
            else -> item { PdfPages(vm.cvPdf) }
        }
    }
}

@Composable
private fun CvReport(vm: CoachViewModel, r: org.json.JSONObject?) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (r == null || r.length() == 0) { Text("The report appears when the CV is ready.", color = Muted); return }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("✅ You match") { Text(r.optJSONArray("matching")?.let { a -> (0 until a.length()).joinToString(" · ") { a.optString(it) } } ?: "", color = Ink, fontSize = 13.sp) }
        r.optJSONArray("gaps")?.takeIf { it.length() > 0 }?.let { a -> Section("⚠️ Not in your CV (not added)") {
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { x ->
                Text("• " + x.optString("skill"), color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(x.optString("how_to_handle"), color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
            }
        } }
        r.optJSONArray("changes")?.let { a -> Section("✏️ What changed") { for (i in 0 until a.length()) Text("• " + a.optString(i), color = Ink, fontSize = 13.sp, lineHeight = 18.sp) } }
        r.optString("cover_note").takeIf { it.isNotBlank() }?.let { n -> Section("💬 Message to the recruiter") {
            Text(n, color = Ink, fontSize = 13.sp, lineHeight = 18.sp)
            Pill("Copy") {
                ctx.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("note", n))
                vm.cvMsg = "Copied the message"
            }
        } }
    }
}

@Composable
private fun Section(title: String, body: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().card(18).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp); body()
    }

/** The CV split into its sections (by UPPERCASE headings), each editable; Save makes a new version and a new PDF. */
@Composable
private fun CvEditor(vm: CoachViewModel, cv: String, versions: org.json.JSONArray?) {
    val parts = remember(cv) {
        val out = mutableListOf<Pair<String, String>>(); var head = "Name & contact"; val buf = StringBuilder()
        cv.lines().forEach { l ->
            val t = l.trim()
            if (t.length in 3..44 && t == t.uppercase() && t.any { it.isLetter() } && !t.contains("|") && out.isNotEmpty() || (t.length in 3..44 && t == t.uppercase() && t.any { it.isLetter() } && !t.contains("|") && buf.isNotEmpty())) {
                out += head to buf.toString().trim(); head = t; buf.clear()
            } else buf.appendLine(l)
        }
        out += head to buf.toString().trim(); out.toList()
    }
    val texts = remember(cv) { mutableStateListOf(*parts.map { it.second }.toTypedArray()) }
    var ask by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("🤖 Ask AI to change it") {
            CvField(ask, { ask = it }, "e.g. make the summary shorter, put 2sf.gg first, more focus on APIs")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Send to Claude") { if (ask.isNotBlank()) { vm.aiEditCv(ask); ask = "" } }
                Pill(if (vm.phase == Phase.Listening) "⏹ Stop" else "🎤 Speak", outline = true) { vm.dictate { t -> ask = (ask + " " + t).trim() } }
            }
        }
        parts.forEachIndexed { i, (head, _) ->
            Column(Modifier.fillMaxWidth().card(16).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(head, color = Color(0xFF0F5C45), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                BasicTextField(texts[i], { texts[i] = it }, Modifier.fillMaxWidth().border(1.dp, Color(0xFFE4E0F5), RoundedCornerShape(10.dp)).padding(8.dp),
                    textStyle = TextStyle(color = Ink, fontSize = 13.sp, lineHeight = 18.sp), cursorBrush = SolidColor(Violet))
            }
        }
        Text("💾 Save & update PDF", Modifier.fillMaxWidth().background(Brush.horizontalGradient(Blue), RoundedCornerShape(16.dp))
            .press { vm.saveCv(parts.indices.joinToString("\n\n") { i -> if (i == 0) texts[0] else parts[i].first + "\n" + texts[i] }) }
            .padding(vertical = 14.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        versions?.takeIf { it.length() > 0 }?.let { v -> Section("🕘 Earlier versions") {
            for (i in v.length() - 1 downTo 0) Text("Restore ${v.optString(i).removePrefix("cv.").removeSuffix(".txt")}", Modifier.press { vm.restoreCv(v.optString(i)) }.padding(vertical = 4.dp),
                color = Violet, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        } }
    }
}

/** The PDF itself, rendered page by page with Android's own PDF renderer. */
@Composable
private fun PdfPages(file: java.io.File?) {
    var pages by remember(file) { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    LaunchedEffect(file) {
        if (file != null) pages = withContext(Dispatchers.IO) {
            runCatching {
                PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pr ->
                    (0 until pr.pageCount).map { i ->
                        pr.openPage(i).use { p ->
                            val bmp = Bitmap.createBitmap(p.width * 2, p.height * 2, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); bmp.asImageBitmap()
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
    if (file == null) { Text("The PDF appears when the CV is ready.", color = Muted); return }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        pages.forEachIndexed { i, p ->
            Image(p, "Page ${i + 1}", Modifier.fillMaxWidth().border(1.dp, Color(0xFFE4E0F5)), contentScale = ContentScale.FillWidth)
        }
    }
}
