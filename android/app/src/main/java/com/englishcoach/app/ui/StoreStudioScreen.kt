package com.englishcoach.app.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

private val Teal = Color(0xFF0EA5A4)
private val Amber = Color(0xFFF59E0B)
private val Grad = listOf(Color(0xFFF97316), Color(0xFFDB2777))

/** Card on the Mac tab that opens Store Studio. */
@Composable
fun StoreStudioCard(vm: CoachViewModel) {
    Row(Modifier.fillMaxWidth().press { vm.etsyStore = null; vm.etsyRun = null; vm.screen = Screen.Stores }
        .background(lt(Color(0xFFFFF1E6)), RoundedCornerShape(26.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).background(Brush.linearGradient(Grad), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Text("🛍", fontSize = 22.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Store Studio", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink)
            Text("Etsy stores: what to make today, search, AI designs", fontSize = 12.sp, color = Muted)
        }
        LineIcon(Icons.ARROW, Ink, size = 20.dp, strokeWidth = 2.4f)
    }
}

@Composable
fun StoreStudioScreen(vm: CoachViewModel) {
    when {
        vm.etsyRun != null -> ResultView(vm, vm.etsyRun!!)
        vm.etsyStore != null -> StoreView(vm, vm.etsyStore!!)
        else -> StoreList(vm)
    }
}

// ---------- images ----------

private val imgCache = mutableMapOf<String, ImageBitmap>()

/** Public Etsy image (http) or a signed Mac path (/etsy/image?...). */
@Composable
private fun NetImage(vm: CoachViewModel, src: String?, modifier: Modifier, crop: Boolean = true) {
    var bmp by remember(src) { mutableStateOf(src?.let { imgCache[it] }) }
    LaunchedEffect(src) {
        if (src == null || bmp != null) return@LaunchedEffect
        val bytes = if (src.startsWith("http")) withContext(Dispatchers.IO) { runCatching { URL(src).readBytes() }.getOrNull() } else vm.macBytes(src)
        bytes?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()?.let { imgCache[src] = it; bmp = it } }
    }
    Box(modifier.background(lt(Color(0xFFF1EEF8))), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = if (crop) ContentScale.Crop else ContentScale.Fit) }
            ?: Text("🖼", fontSize = 20.sp, color = Muted)
    }
}

@Composable
private fun Chip(t: String, bg: Color = Color(0xFFEDE8FF), fg: Color = Violet) =
    Text(t, Modifier.background(bg, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp), color = fg, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)

private fun JSONArray?.strings(): List<String> = this?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList()
private fun JSONArray?.objs(): List<JSONObject> = this?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: emptyList()

// ---------- 1. stores ----------

@Composable
private fun StoreList(vm: CoachViewModel) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) { d = vm.mac("/etsy/overview") }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 150.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("Store Studio") { vm.screen = Screen.Mac } }
        val o = d
        if (o == null) { item { Text("Loading your stores…", color = Muted) }; return@LazyColumn }
        if (o.has("error")) { item { Text(o.optString("error"), color = Color(0xFFE11D48)) }; return@LazyColumn }
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(InkOnLight, Color(0xFF5B1A3A))), RoundedCornerShape(24.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TODAY'S MARKET", color = Lime, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Text(o.optString("summary").ifBlank { "Pick a store to see what to make today." }, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                Text("Data: ${o.optString("generated").replace("T", " ")} · AI: ${if (o.optString("engine") == "gemini") "Gemini" else "Claude"}",
                    color = Color.White.copy(alpha = .6f), fontSize = 11.sp)
            }
        }
        if (o.optBoolean("offline")) item { Text("📴 Offline: Mac nahi mil raha, pichla data dikh raha hai.", Modifier.fillMaxWidth()
            .background(lt(Color(0xFFFEF3C7)), RoundedCornerShape(14.dp)).padding(12.dp), color = Color(0xFF92400E), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        item { TrendsCard(vm) }
        val ev = o.optJSONArray("events").objs()
        if (ev.isNotEmpty()) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ev.forEach { e ->
                    Column(Modifier.width(200.dp).card(18).padding(12.dp)) {
                        Text("📅 ${e.optString("name")}", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        Text("${e.optInt("days_left")} days left", color = if (e.optInt("days_left") <= 7) Color(0xFFE11D48) else Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(e.optString("prepare"), color = Muted, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item { Text("Your stores", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        items(o.optJSONArray("stores").objs()) { s ->
            Row(Modifier.fillMaxWidth().card(22).press { vm.etsyStore = s.optString("name") }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                NetImage(vm, s.optString("img").ifBlank { null }, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(s.optString("name"), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("${s.optInt("sales")} sales · ★ ${s.optString("rating").ifBlank { "–" }} · ${s.optString("top")}", color = Muted, fontSize = 12.sp, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val w = s.optInt("week_sales")
                        Chip("${if (w > 0) "+" else ""}$w this week", if (w > 0) Color(0xFFDCFCE7) else Paper, if (w > 0) Color(0xFF15803D) else Muted)
                        if (s.optInt("todo") > 0) Chip("${s.optInt("todo")} ideas", Color(0xFFFFEDD5), Color(0xFFC2410C))
                    }
                }
                LineIcon(Icons.ARROW, Muted, size = 18.dp)
            }
        }
    }
}

// ---------- 2. one store ----------

@Composable
private fun StoreView(vm: CoachViewModel, name: String) {
    var d by remember(name) { mutableStateOf<JSONObject?>(null) }
    var tab by remember { mutableStateOf(0) }
    var past by remember { mutableStateOf<JSONArray?>(null) }
    var designs by remember { mutableStateOf<JSONArray?>(null) }
    LaunchedEffect(name) {
        d = vm.mac("/etsy/store?name=$name")
        past = vm.mac("/etsy/history?store=$name").optJSONArray("runs")
        designs = vm.mac("/etsy/designs?store=$name").optJSONArray("designs")
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 150.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader(name) { vm.etsyStore = null } }
        val s = d
        if (s == null) { item { Text("Loading…", color = Muted) }; return@LazyColumn }
        if (s.has("error")) { item { Text(s.optString("error"), color = Color(0xFFE11D48)) }; return@LazyColumn }
        item { StoreHeader(s) }
        item { SearchBox(vm, name) }
        item {
            Row(Modifier.fillMaxWidth().background(Pastel, CircleShape).padding(4.dp)) {
                listOf("🔥 Make today", "📊 Insights", "🎨 Designs").forEachIndexed { i, t ->
                    Text(t, Modifier.weight(1f).background(if (tab == i) Violet else Color.Transparent, CircleShape).press { tab = i }.padding(vertical = 9.dp),
                        color = if (tab == i) Color.White else Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }
        when (tab) {
            0 -> {
                val tasks = s.optJSONArray("tasks").objs()
                if (tasks.isEmpty()) item { Text("No ideas from today's analysis. Search above for anything.", color = Muted) }
                items(tasks) { t -> TaskCard(vm, t, name) }
                val runs = past.objs()
                if (runs.isNotEmpty()) {
                    item { Text("Earlier searches", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
                    items(runs) { r ->
                        Column(Modifier.fillMaxWidth().card(16).press { vm.etsyRun = r.optString("id") }.padding(12.dp)) {
                            Text("🔎 " + r.optString("query"), color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(r.optString("verdict"), color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            1 -> { item { RealData(vm, name) }; item { Insights(vm, s) } }
            else -> {
                item { PackResults(vm, name) }
                val ds = designs.objs()
                if (ds.isEmpty()) item { Text("No designs yet. Search, open an idea and tap \"Make design\".", color = Muted) }
                items(ds) { dz -> DesignGallery(vm, dz) }
            }
        }
    }
}

@Composable
private fun StoreHeader(s: JSONObject) {
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(Grad), RoundedCornerShape(24.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (s.optDouble("earned_month", 0.0) > 0) Text("💼 Is mahine ki kamai (Money book): PKR " + "%,.0f".format(s.optDouble("earned_month")),
            Modifier.background(Color.White.copy(alpha = .22f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("${s.optInt("sales")}" to "sales", "${s.optInt("sales_per_month")}" to "a month", "$${s.optDouble("avg_price").let { if (it.isNaN()) 0.0 else it }}" to "avg price",
                (s.optString("rating").ifBlank { "–" }) to "rating").forEach { (v, l) ->
                Column(Modifier.weight(1f).background(Color.White.copy(alpha = .18f), RoundedCornerShape(14.dp)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(v, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1)
                    Text(l, color = Color.White.copy(alpha = .8f), fontSize = 10.sp)
                }
            }
        }
        val h = s.optJSONArray("history")
        if (h != null && h.length() > 1) Sparkline(List(h.length()) { h.getJSONArray(it).optInt(1) })
        s.optString("niche").takeIf { it.isNotBlank() && it != "null" }?.let { Text(it, color = Color.White, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun Sparkline(v: List<Int>) {
    val lo = v.min(); val hi = maxOf(v.max(), lo + 1)
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        v.forEach { x -> Box(Modifier.weight(1f).fillMaxHeight(.15f + .85f * (x - lo) / (hi - lo)).background(lt(Color.White.copy(alpha = .75f)), RoundedCornerShape(3.dp))) }
    }
    Text("Total sales, last ${v.size} checks: +${v.last() - v.first()}", color = Color.White.copy(alpha = .85f), fontSize = 11.sp)
}

@Composable
private fun SearchBox(vm: CoachViewModel, store: String) {
    var q by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().card(22).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("What do you want to make?", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        TextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("e.g. funeral shirt for dad, veteran program…", fontSize = 13.sp) }, maxLines = 3)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Funeral shirt", "Funeral program", "Memorial sign", "Christmas in heaven", "Veteran memorial").forEach { s ->
                Box(Modifier.press { q = s }) { Chip(s) }
            }
        }
        Row(Modifier.fillMaxWidth().press {
            if (busy || q.isBlank()) return@press
            busy = true; err = ""
            scope.launch {
                val r = vm.mac("/etsy/search", JSONObject().put("store", store).put("query", q))
                busy = false
                if (r.has("error")) err = r.optString("error") else vm.etsyRun = r.optString("id")
            }
        }.background(Brush.horizontalGradient(Grad), RoundedCornerShape(14.dp)).padding(12.dp), horizontalArrangement = Arrangement.Center) {
            Text(if (busy) "Starting…" else "🔎 Analyse", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
        if (err.isNotBlank()) Text(err, color = Color(0xFFE11D48), fontSize = 12.sp)
    }
}

@Composable
private fun TaskCard(vm: CoachViewModel, t: JSONObject, store: String) {
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    val pr = t.optString("priority")
    Column(Modifier.fillMaxWidth().card(20).press { open = !open }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(pr.uppercase(), if (pr == "high") Color(0xFFFFE4E6) else Color(0xFFFEF3C7), if (pr == "high") Color(0xFFE11D48) else Color(0xFFB45309))
            Chip(t.optString("kind"))
            if (t.optDouble("price", 0.0) > 0) Chip("$" + t.optDouble("price"), Color(0xFFDCFCE7), Color(0xFF15803D))
        }
        Text(t.optString("title"), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Text(t.optString("why"), color = Muted, fontSize = 12.sp)
        if (open) {
            Text(t.optString("detail"), color = Ink, fontSize = 12.sp, lineHeight = 17.sp)
            t.optString("edge").takeIf { it.isNotBlank() }?.let { Text("💡 $it", color = Violet, fontSize = 12.sp) }
            val tags = t.optJSONArray("tags").strings()
            if (tags.isNotEmpty()) Text("Copy title + tags", Modifier.press { clip.setText(AnnotatedString(t.optString("etsy_title") + "\n\n" + tags.joinToString(", "))) }
                .background(Paper, RoundedCornerShape(12.dp)).padding(10.dp), color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        // opens the full idea (design, Canva steps, image prompt) with the "Make design" button
        Row(Modifier.fillMaxWidth().press {
            if (busy) return@press
            busy = true; err = ""
            scope.launch {
                val q = t.optString("title") + (t.optString("kind").takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
                val r = vm.mac("/etsy/search", JSONObject().put("store", store).put("query", q))
                busy = false
                if (r.has("error")) err = r.optString("error") else vm.etsyRun = r.optString("id")
            }
        }.background(Brush.horizontalGradient(listOf(Violet, Color(0xFFDB2777))), RoundedCornerShape(14.dp)).padding(11.dp), horizontalArrangement = Arrangement.Center) {
            Text(if (busy) "Opening…" else "✨ Details + make design", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
        if (err.isNotBlank()) Text(err, color = Color(0xFFE11D48), fontSize = 12.sp)
    }
}

@Composable
private fun Insights(vm: CoachViewModel, s: JSONObject) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val cats = s.optJSONArray("categories")
        if (cats != null && cats.length() > 0) Column(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What this store sells", color = Ink, fontWeight = FontWeight.ExtraBold)
            val tot = (0 until cats.length()).sumOf { cats.getJSONArray(it).optInt(1) }.coerceAtLeast(1)
            for (i in 0 until cats.length()) {
                val c = cats.getJSONArray(i)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.optString(0), Modifier.width(130.dp), color = Ink, fontSize = 12.sp, maxLines = 1)
                    Box(Modifier.weight(1f).height(10.dp).background(Paper, CircleShape)) {
                        Box(Modifier.fillMaxWidth(c.optInt(1).toFloat() / tot).fillMaxHeight().background(Brush.horizontalGradient(Grad), CircleShape))
                    }
                    Text(" ${c.optInt(1)}", color = Muted, fontSize = 11.sp)
                }
            }
        }
        val miss = s.optJSONArray("missing_themes").strings()
        if (miss.isNotEmpty()) Column(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Themes buyers want that you don't have", color = Ink, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { miss.forEach { Chip(it, Color(0xFFFFEDD5), Color(0xFFC2410C)) } }
        }
        s.optJSONObject("ai")?.let { ai ->
            Column(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("AI notes", color = Ink, fontWeight = FontWeight.ExtraBold)
                ai.keys().forEach { k ->
                    val v = ai.opt(k)
                    val txt = when (v) { is JSONArray -> v.strings().joinToString("\n• ", "• "); else -> v.toString() }
                    if (txt.isNotBlank() && txt != "null") { Text(k.replace("_", " ").replaceFirstChar { it.uppercase() }, color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text(txt, color = Ink, fontSize = 12.sp) }
                }
            }
        }
        val top = s.optJSONArray("top_items").objs().take(8)
        if (top.isNotEmpty()) {
            Text("Your top listings", color = Ink, fontWeight = FontWeight.ExtraBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                top.forEach { i -> ListingTile(vm, i) }
            }
        }
    }
}

@Composable
private fun ListingTile(vm: CoachViewModel, i: JSONObject) {
    Column(Modifier.width(140.dp).card(16).padding(6.dp)) {
        NetImage(vm, i.optString("img").ifBlank { null }, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)))
        Text(i.optString("title"), color = Ink, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$" + i.optString("price"), color = Color(0xFF15803D), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            if (i.optBoolean("bestseller")) Text("★ Best", color = Amber, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
        }
    }
}

// ---------- 3. a search result ----------

@Composable
private fun ResultView(vm: CoachViewModel, id: String) {
    var r by remember(id) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(id) {
        while (true) {
            val x = vm.mac("/etsy/result?id=$id"); r = x
            if (x.optString("status") != "running") break
            delay(2500)
        }
    }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 150.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("Analysis") { vm.etsyRun = null } }
        val d = r
        if (d == null || d.optString("status") == "running") { item { Thinking(d?.optString("query") ?: "") }; return@LazyColumn }
        if (d.has("error")) { item { Text(d.optString("error"), color = Color(0xFFE11D48)) }; return@LazyColumn }
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(InkOnLight, Color(0xFF5B1A3A))), RoundedCornerShape(24.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🔎 " + d.optString("query"), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    fun lv(v: String) = when (v) { "high" -> Color(0xFF16A34A); "low" -> Color(0xFFE11D48); else -> Amber }
                    Chip("Demand: " + d.optString("demand"), lv(d.optString("demand")), Color.White)
                    val c = d.optString("competition")
                    Chip("Competition: $c", lv(if (c == "high") "low" else if (c == "low") "high" else "medium"), Color.White)
                }
                Text(d.optString("verdict"), color = Color.White, fontSize = 13.sp, lineHeight = 19.sp)
                d.optJSONObject("market")?.let { m ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("${m.optInt("count")}" to "listings", "${m.optInt("bestsellers")}" to "bestsellers",
                            (if (m.isNull("price_median")) "–" else "$" + m.optDouble("price_median")) to "median").forEach { (v, l) ->
                            Column(Modifier.weight(1f).background(Color.White.copy(alpha = .12f), RoundedCornerShape(12.dp)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(v, color = Lime, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp); Text(l, color = Color.White.copy(alpha = .7f), fontSize = 10.sp)
                            }
                        }
                    }
                }
                d.optString("avoid").takeIf { it.isNotBlank() }?.let { Text("⚠️ $it", color = Color(0xFFFDE68A), fontSize = 12.sp) }
            }
        }
        val top = d.optJSONObject("market")?.optJSONArray("top").objs()
        if (!top.isNullOrEmpty()) {
            item { Text("What's selling now", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { top.forEach { ListingTile(vm, it) } } }
        }
        item { Text("Make these", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
        val ideas = d.optJSONArray("ideas").objs()
        items(ideas.size) { i -> IdeaCard(vm, id, i, ideas[i]) }
    }
}

@Composable
private fun Thinking(q: String) {
    val t = rememberInfiniteTransition(label = "t")
    val a by t.animateFloat(.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(Grad), RoundedCornerShape(24.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("🧠", fontSize = 40.sp, modifier = Modifier.padding(4.dp))
        Text("Reading today's Etsy data…", color = Color.White.copy(alpha = a), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text("\"$q\" · about a minute", color = Color.White.copy(alpha = .85f), fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun IdeaCard(vm: CoachViewModel, run: String, idx: Int, it: JSONObject) {
    var open by remember { mutableStateOf(idx == 0) }
    var did by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf(it.optString("image_prompt")) }
    var editPrompt by remember { mutableStateOf(false) }
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().card(22).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${idx + 1}", Modifier.size(30.dp).background(Brush.linearGradient(Grad), CircleShape).padding(top = 5.dp), color = Color.White,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).press { open = !open }) {
                Text(it.optString("name"), color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Chip(it.optString("type")); Chip("$" + it.optDouble("price"), Color(0xFFDCFCE7), Color(0xFF15803D)) }
            }
        }
        Text(it.optString("why"), color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        if (open) {
            Section("Design") { Text(it.optString("design"), color = Ink, fontSize = 13.sp, lineHeight = 18.sp) }
            val colors = it.optJSONArray("colors").strings()
            if (colors.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { colors.forEach { c -> Chip("🎨 $c") } }
            it.optString("includes").takeIf { s -> s.isNotBlank() }?.let { s -> Section("Included") { Text(s, color = Ink, fontSize = 12.sp) } }
            it.optString("edge").takeIf { s -> s.isNotBlank() }?.let { s -> Text("💡 $s", color = Violet, fontSize = 12.sp) }
            val steps = it.optJSONArray("canva_steps").strings()
            if (steps.isNotEmpty()) Section("Finish it in Canva") {
                steps.forEachIndexed { i, s -> Text("${i + 1}. $s", color = Ink, fontSize = 12.sp, lineHeight = 17.sp) }
            }
            Section("Etsy title & tags") {
                Text(it.optString("etsy_title"), color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(it.optJSONArray("tags").strings().joinToString(" · "), color = Muted, fontSize = 11.sp)
                Text("📋 Copy", Modifier.press { clip.setText(AnnotatedString(it.optString("etsy_title") + "\n\n" + it.optJSONArray("tags").strings().joinToString(", "))) }
                    .background(Paper, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp), color = Violet, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
            if (editPrompt) TextField(prompt, { p -> prompt = p }, Modifier.fillMaxWidth(), minLines = 3)
            else Text("Image prompt: $prompt", Modifier.press { editPrompt = true }, color = Muted, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        val d = did
        if (d == null) Row(Modifier.fillMaxWidth().press {
            err = ""
            scope.launch {
                val r = vm.mac("/etsy/design", JSONObject().put("run", run).put("idea", idx).put("prompt", prompt))
                if (r.has("error")) err = r.optString("error") else did = r.optString("id")
            }
        }.background(Brush.horizontalGradient(listOf(Violet, Color(0xFFDB2777))), RoundedCornerShape(14.dp)).padding(12.dp), horizontalArrangement = Arrangement.Center) {
            Text("✨ Make design with Gemini", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        } else DesignLive(vm, d)
        if (d == null) Row(Modifier.fillMaxWidth().press {
            err = ""
            scope.launch {
                val r = vm.mac("/etsy/auto", JSONObject().put("run", run).put("idea", idx).put("prompt", prompt))
                if (r.has("error")) err = r.optString("error") else { did = r.optString("id"); err = "⚡ Design → layers → listing pack chal raha hai. Ready hone par notification aayega." }
            }
        }.background(Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFDB2777))), RoundedCornerShape(14.dp)).padding(12.dp), horizontalArrangement = Arrangement.Center) {
            Text("⚡ Ek tap: design + listing pack", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
        if (err.isNotBlank()) Text(err, color = if (err.startsWith("⚡")) Violet else Color(0xFFE11D48), fontSize = 12.sp)
    }
}

@Composable
private fun Section(title: String, body: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Violet, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp); body()
    }

/** Follows the Gemini design job and shows the images when they arrive. */
@Composable
private fun DesignLive(vm: CoachViewModel, did: String) {
    var st by remember(did) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(did) {
        while (true) {
            val x = vm.mac("/etsy/design_status?id=$did"); st = x
            if (x.optString("status") != "running") break
            delay(5000)
        }
    }
    val s = st ?: return Text("Starting Gemini…", color = Muted, fontSize = 12.sp)
    if (s.optString("status") == "running") {
        Column(Modifier.fillMaxWidth().background(lt(Color(0xFFF3E8FF)), RoundedCornerShape(14.dp)).padding(12.dp)) {
            Text("🎨 Gemini is drawing (1–3 min)…", color = Violet, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            s.optJSONArray("steps").strings().lastOrNull()?.let { Text(it, color = Muted, fontSize = 11.sp, maxLines = 2) }
        }
    } else DesignGallery(vm, s)
}

private fun layerLabel(f: String) = when {
    f == "print.png" -> "🖨 Print PNG (4500×5400, 300 dpi)"
    f == "art_no_text.png" -> "🎨 Artwork without text"
    f == "background.png" -> "🌄 Background"
    f == "person.png" -> "🧍 Person / photo"
    f.startsWith("piece_") -> "🧩 " + f.removePrefix("piece_").removeSuffix(".png").substringAfter("_").replace("_", " ").replaceFirstChar { it.uppercase() }
    f.startsWith("element_") -> "🧩 Element " + f.removePrefix("element_").removeSuffix(".png")
    f.startsWith("text_") -> "🔤 Text " + f.removePrefix("text_").removeSuffix(".png")
    else -> f
}

/** Ready-made layers: print file, background, each element, person, each text line — all the same size, so they stack exactly. */
@Composable
private fun LayersPanel(vm: CoachViewModel, id: String, layers0: JSONObject?) {
    var layers by remember(id) { mutableStateOf(layers0) }
    var busy by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    val L = layers
    if (L == null) {
        Text(if (busy.isNotBlank()) busy else "🧩 Make layers + print PNG", Modifier.fillMaxWidth().press {
            if (busy.isNotBlank()) return@press
            busy = "Making layers… (about 10 s)"
            scope.launch {
                val r = vm.mac("/etsy/layers", JSONObject().put("id", id))
                busy = r.optString("error")
                if (!r.has("error")) layers = vm.mac("/etsy/design_status?id=$id").optJSONObject("layers")
            }
        }.background(Brush.horizontalGradient(listOf(Teal, Violet)), RoundedCornerShape(12.dp)).padding(12.dp),
            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center)
        return
    }
    val files = L.optJSONArray("files").strings()
    Column(Modifier.fillMaxWidth().background(lt(Color(0xFFECFEFF)), RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Layers ready for Canva", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        Text("Every layer is the same size: upload them, set each to fill the page, and they line up exactly. Hide the text layers and type your own.",
            color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
        files.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { f ->
                    Column(Modifier.weight(1f).background(Surface, RoundedCornerShape(12.dp)).press {
                        scope.launch { vm.macBytes("/etsy/image?id=$id&name=layers/$f")?.let { b -> vm.saveDesign(b, "${id}_$f", false) } }
                    }.padding(6.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(.83f).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(lt(Color(0xFFE5E7EB)), lt(Color(0xFFF9FAFB)))))) {
                            NetImage(vm, "/etsy/image?id=$id&name=layers/p_$f", Modifier.fillMaxSize(), crop = false)
                        }
                        Text(layerLabel(f), color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        val texts = L.optJSONArray("texts").objs()
        if (texts.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Text in the design (tap to copy)", color = Violet, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            texts.forEach { t ->
                Row(Modifier.fillMaxWidth().press { clip.setText(AnnotatedString(t.optString("text"))) }.background(Surface, RoundedCornerShape(10.dp)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    val c = runCatching { Color(android.graphics.Color.parseColor(t.optString("color"))) }.getOrDefault(Ink)
                    Box(Modifier.size(16.dp).background(c, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(t.optString("text"), Modifier.weight(1f), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(t.optString("color"), color = Muted, fontSize = 11.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (busy.startsWith("Sending")) busy else "All layers → Canva", Modifier.weight(1f).press {
                busy = "Sending…"
                scope.launch {
                    val got = files.mapNotNull { f -> vm.macBytes("/etsy/image?id=$id&name=layers/$f")?.let { "${id}_$f" to it } }
                    busy = ""; vm.saveLayers(got, id, true)
                }
            }.background(Color(0xFF00C4CC), RoundedCornerShape(12.dp)).padding(10.dp), color = Color.White, fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp, textAlign = TextAlign.Center)
            if (L.optBoolean("svg")) Text("SVG (Cricut)", Modifier.weight(1f).press {
                scope.launch { vm.macBytes("/etsy/image?id=$id&name=layers/design.svg")?.let { b -> vm.shareFile(b, "${id}_design.svg", "image/svg+xml") } }
            }.background(Color(0xFF7C3AED), RoundedCornerShape(12.dp)).padding(10.dp), color = Color.White, fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp, textAlign = TextAlign.Center)
            Text("Print PNG", Modifier.weight(1f).press {
                scope.launch { vm.macBytes("/etsy/image?id=$id&name=layers/print.png")?.let { b -> vm.saveDesign(b, "${id}_print.png", false) } }
            }.background(InkOnLight, RoundedCornerShape(12.dp)).padding(10.dp), color = Color.White, fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DesignGallery(vm: CoachViewModel, d: JSONObject) {
    val files = d.optJSONArray("files").strings()
    val id = d.optString("id")
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().card(20).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        d.optString("idea").takeIf { it.isNotBlank() && it != "null" }?.let { Text(it, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
        if (files.isEmpty()) Text(d.optString("note").ifBlank { "Gemini didn't make an image. Try again or change the prompt." }, color = Color(0xFFE11D48), fontSize = 12.sp)
        files.forEach { f ->
            val path = "/etsy/image?id=$id&name=$f"
            NetImage(vm, path, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)), crop = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Open in Canva", Modifier.weight(1f).press { scope.launch { vm.macBytes(path)?.let { b -> vm.saveDesign(b, "${id}_$f", true) } } }
                    .background(Color(0xFF00C4CC), RoundedCornerShape(12.dp)).padding(10.dp), color = Color.White, fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp, textAlign = TextAlign.Center)
                Text("Save / share", Modifier.weight(1f).press { scope.launch { vm.macBytes(path)?.let { b -> vm.saveDesign(b, "${id}_$f", false) } } }
                    .background(Paper, RoundedCornerShape(12.dp)).padding(10.dp), color = Ink, fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        if (files.isNotEmpty()) LayersPanel(vm, id, d.optJSONObject("layers"))
        if (files.isNotEmpty()) PackPanel(vm, id)
    }
}


/** Listing pack: 5 Etsy photos, buyer PDF with the Canva link, title/tags/description, AI-rules warnings, Bulk Create CSV. */
@Composable
private fun PackPanel(vm: CoachViewModel, id: String) {
    var info by remember(id) { mutableStateOf<JSONObject?>(null) }
    var link by remember { mutableStateOf("") }
    var poll by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    LaunchedEffect(id, poll) {
        while (true) {
            val x = vm.mac("/etsy/pack_info?id=$id"); info = x
            if (!x.optBoolean("running")) break
            delay(4000)
        }
        info?.optString("link")?.takeIf { it.isNotBlank() }?.let { link = it }
    }
    val i = info
    Column(Modifier.fillMaxWidth().background(lt(Color(0xFFFFF7ED)), RoundedCornerShape(16.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("📦 Listing pack", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        Text("Canva Pro: make the template from the layers, then Share → Template link, and paste it here. It goes into the buyer's PDF.",
            color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
        TextField(link, { link = it }, Modifier.fillMaxWidth(), placeholder = { Text("Canva template link (optional now)", fontSize = 12.sp) }, singleLine = true)
        val running = i?.optBoolean("running") == true
        Text(if (running) "Making the pack… (about 2 min)" else if (i != null && !i.has("none") && !i.has("error")) "↻ Remake pack" else "📦 Make listing pack",
            Modifier.fillMaxWidth().press {
                if (running) return@press
                scope.launch { vm.mac("/etsy/pack", JSONObject().put("id", id).put("link", link)); delay(1500); poll++ }
            }.background(Brush.horizontalGradient(Grad), RoundedCornerShape(12.dp)).padding(12.dp),
            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (i == null || i.has("none") || running) return@Column
        if (i.has("error")) { Text(i.optString("error"), color = Color(0xFFE11D48), fontSize = 12.sp); return@Column }
        val warns = i.optJSONArray("warnings").strings()
        warns.forEach { w -> Text("⚠️ $w", Modifier.fillMaxWidth().background(lt(Color(0xFFFFE4E6)), RoundedCornerShape(10.dp)).padding(8.dp), color = Color(0xFFBE123C), fontSize = 12.sp) }
        if (warns.isEmpty()) Text("✅ Etsy rules check passed (AI disclosure added, no trademark words, not a near-duplicate)", color = Color(0xFF15803D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val files = i.optJSONArray("files").strings()
        val photos = files.filter { it.endsWith(".jpg") }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.forEach { f -> NetImage(vm, "/etsy/packfile?id=$id&name=p_$f", Modifier.width(240.dp).height(180.dp).clip(RoundedCornerShape(12.dp))) }
        }
        Text("Save ${photos.size} listing photos", Modifier.fillMaxWidth().press {
            scope.launch {
                val got = photos.mapNotNull { f -> vm.macBytes("/etsy/packfile?id=$id&name=$f")?.let { "${id}_$f" to it } }
                vm.saveLayers(got, "$id-listing", false)
            }
        }.background(InkOnLight, RoundedCornerShape(12.dp)).padding(10.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center)
        @Composable fun Copy(label: String, value: String) = Section(label) {
            Text(value, color = Ink, fontSize = 12.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
            Text("📋 Copy", Modifier.press { clip.setText(AnnotatedString(value)) }.background(Color.White, CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp), color = Violet, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
        Copy("Title", i.optString("title"))
        Copy("13 tags", i.optJSONArray("tags").strings().joinToString(", "))
        Copy("Description", i.optString("description"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!i.isNull("price")) Chip("Price $" + i.optDouble("price"), Color(0xFFDCFCE7), Color(0xFF15803D))
            i.optString("brand_kit").takeIf { it.isNotBlank() }?.let { Chip("Brand Kit: $it") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Buyer PDF", Modifier.weight(1f).press {
                scope.launch { vm.macBytes("/etsy/packfile?id=$id&name=Download_Instructions.pdf")?.let { vm.shareFile(it, "${id}_Download_Instructions.pdf", "application/pdf") } }
            }.background(Color(0xFFE11D48), RoundedCornerShape(12.dp)).padding(10.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center)
            Text("Bulk Create CSV", Modifier.weight(1f).press {
                scope.launch { vm.macBytes("/etsy/packfile?id=$id&name=canva_bulk_create.csv")?.let { vm.shareFile(it, "${id}_canva_bulk_create.csv", "text/csv") } }
            }.background(Color(0xFF00C4CC), RoundedCornerShape(12.dp)).padding(10.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        if (i.optString("link").isBlank()) Text("Tip: after you paste the Canva link, tap Remake pack so the PDF gets the link.", color = Muted, fontSize = 11.sp)
        MarkListed(vm, id)
    }
}


@Composable
private fun MarkListed(vm: CoachViewModel, id: String) {
    var url by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Section("Listed it on Etsy?") {
        Text("Paste the listing link: the Mac follows its search visibility, carts and Bestseller badge, and learns what works.", color = Muted, fontSize = 11.sp)
        TextField(url, { url = it }, Modifier.fillMaxWidth(), placeholder = { Text("https://www.etsy.com/listing/…", fontSize = 12.sp) }, singleLine = true)
        Text("✅ Mark as listed", Modifier.press {
            scope.launch { val r = vm.mac("/etsy/listed", JSONObject().put("id", id).put("url", url)); msg = r.optString("error").ifBlank { "Saved. Results show in Designs after 7 days." } }
        }.background(Color(0xFF16A34A), RoundedCornerShape(12.dp)).padding(10.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        if (msg.isNotBlank()) Text(msg, color = Ink, fontSize = 12.sp)
    }
}

/** Buyer search phrases, our rank for each keyword, and which of our listings are gaining favourites. */
@Composable
private fun RealData(vm: CoachViewModel, store: String) {
    var ph by remember(store) { mutableStateOf<JSONObject?>(null) }
    var rk by remember(store) { mutableStateOf<JSONObject?>(null) }
    var sg by remember(store) { mutableStateOf<JSONObject?>(null) }
    val clip = LocalClipboardManager.current
    LaunchedEffect(store) { ph = vm.mac("/etsy/phrases?store=$store"); rk = vm.mac("/etsy/ranks?store=$store"); sg = vm.mac("/etsy/signals?store=$store") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🔎 What buyers type on Etsy", color = Ink, fontWeight = FontWeight.ExtraBold)
            val list = ph?.optJSONArray("phrases").objs()
            if (list.isEmpty()) Text(ph?.optString("note")?.ifBlank { null } ?: "Loading…", color = Muted, fontSize = 12.sp)
            else {
                Text("From Etsy's own search box. ✓ = you already have it, tap to copy.", color = Muted, fontSize = 11.sp)
                list.take(24).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { x ->
                            val have = x.optBoolean("have")
                            Text((if (have) "✓ " else "+ ") + x.optString("q"), Modifier.weight(1f).press { clip.setText(AnnotatedString(x.optString("q"))) }
                                .background(if (have) lt(Color(0xFFDCFCE7)) else lt(Color(0xFFFFEDD5)), RoundedCornerShape(10.dp)).padding(8.dp),
                                color = if (have) Color(0xFF15803D) else Color(0xFFC2410C), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("📈 Your place in Etsy search", color = Ink, fontWeight = FontWeight.ExtraBold)
            val rows = rk?.optJSONArray("ranks").objs()
            if (rows.isEmpty()) Text("Loading…", color = Muted, fontSize = 12.sp)
            rows.take(12).forEach { r ->
                val now = if (r.isNull("now")) null else r.optInt("now")
                val h = r.optJSONArray("history")
                val prev = h?.let { a -> (a.length() - 2).takeIf { it >= 0 }?.let { a.getJSONArray(it) } }?.let { if (it.isNull(1)) null else it.optInt(1) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.optString("keyword"), Modifier.weight(1f), color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val arrow = when { now == null || prev == null -> ""; now < prev -> " ▲"; now > prev -> " ▼"; else -> "" }
                    Text(if (now == null) "not on page 1" else "#$now$arrow", color = when { now == null -> Muted; now <= 10 -> Color(0xFF15803D); else -> Amber },
                        fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }
        val ls = sg?.optJSONArray("listings").objs()
        if (ls.isNotEmpty()) Column(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🛒 Your listings on Etsy", color = Ink, fontWeight = FontWeight.ExtraBold)
            Text("Bestseller badge, people with it in their cart, and in how many of our tracked searches Etsy shows it.", color = Muted, fontSize = 11.sp)
            ls.take(10).forEach { x ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetImage(vm, x.optString("img").ifBlank { null }, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(8.dp))
                    Text(x.optString("title"), Modifier.weight(1f), color = Ink, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("🔎 ${x.optInt("searches")}", color = Violet, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        if (x.optInt("carts") > 0) Text("🛒 ${x.optInt("carts")}", color = Color(0xFFE11D48), fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        val w = x.optInt("searches_week"); if (w != 0) Text("${if (w > 0) "+" else ""}$w wk", color = Muted, fontSize = 10.sp)
                        if (x.optBoolean("best")) Text("★ Best", color = Amber, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

/** Learning loop: packs the owner listed, with their results at 7 and 30 days. */
@Composable
private fun PackResults(vm: CoachViewModel, store: String) {
    var d by remember(store) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(store) { d = vm.mac("/etsy/performance") }
    val packs = d?.optJSONArray("packs").objs().filter { it.optString("store") == store }
    if (packs.isEmpty()) return
    Column(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("📊 How your listed packs are doing", color = Ink, fontWeight = FontWeight.ExtraBold)
        packs.forEach { p ->
            fun cell(o: JSONObject?) = o?.let { "🔎${it.optInt("fav")} 🛒${it.optInt("carts")}" + (if (it.optBoolean("best")) " ★" else "") } ?: "…"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.optString("idea"), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("listed ${p.optString("listed_on")} · ${p.optInt("days")} days", color = Muted, fontSize = 10.sp)
                }
                Text("7d ${cell(p.optJSONObject("day7"))}  ·  30d ${cell(p.optJSONObject("day30"))}", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


/** What changed in the market since yesterday (also sent as up to 3 notifications a day). */
@Composable
private fun TrendsCard(vm: CoachViewModel) {
    var t by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var all by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { t = vm.mac("/etsy/trends").optJSONArray("trends").objs() }
    if (t.isEmpty()) return
    Column(Modifier.fillMaxWidth().card(22).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("📡 Market mein kya badla", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        (if (all) t else t.take(4)).forEach { x ->
            Column(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(12.dp)).padding(10.dp)) {
                Text(x.optString("title"), color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(x.optString("body"), color = Muted, fontSize = 11.sp)
            }
        }
        if (t.size > 4) Text(if (all) "Kam dikhao" else "Sab ${t.size} dekho", Modifier.press { all = !all }, color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
