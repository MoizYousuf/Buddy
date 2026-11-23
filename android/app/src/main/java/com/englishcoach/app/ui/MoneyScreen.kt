package com.englishcoach.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap

/** Card on the Mac tab. */
@Composable
fun MoneyCard(vm: CoachViewModel) {
    Row(Modifier.fillMaxWidth().press { vm.screen = Screen.Money }.background(lt(Color(0xFFE7F8EE)), RoundedCornerShape(26.dp)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("💰", fontSize = 30.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Money book", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Text("Bill photo → total khud. Ghar se kitne lene hain.", color = Muted, fontSize = 12.sp)
        }
    }
}

private fun rs(v: Double) = "PKR " + String.format("%,.0f", v)

/** Shrinks a photo to 1600 px JPEG so it goes to the Mac fast. */
private suspend fun photoB64(ctx: android.content.Context, u: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val src = ctx.contentResolver.openInputStream(u)!!.use { BitmapFactory.decodeStream(it) }
        val sc = minOf(1f, 1600f / maxOf(src.width, src.height))
        val b = if (sc < 1f) Bitmap.createScaledBitmap(src, (src.width * sc).toInt(), (src.height * sc).toInt(), true) else src
        val out = ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.JPEG, 85, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}

@Composable
fun MoneyScreen(vm: CoachViewModel) = MoneyLockGate { MoneyBody(vm) }

@Composable
private fun MoneyBody(vm: CoachViewModel) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var data by remember { mutableStateOf(JSONObject()) }
    var month by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var form by remember { mutableStateOf<String?>(null) }   // "spend" / "received" / null
    var editId by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Other") }
    var whose by remember { mutableStateOf("home") }
    var date by remember { mutableStateOf("") }
    var person by remember { mutableStateOf("Ghar") }
    var makeShortcut by remember { mutableStateOf(false) }
    var acMode by remember { mutableStateOf("") }      // "" = default for the kind; hbl / faysal / both
    var acHbl by remember { mutableStateOf("") }
    var acPurpose by remember { mutableStateOf("") }
    var acVersion by remember { mutableStateOf(0) }
    var catPicked by remember { mutableStateOf(false) }
    var catOpen by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf("Etsy") }
    var storeName by remember { mutableStateOf("") }
    var stores by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) { vm.mac("/etsy/overview").optJSONArray("stores")?.let { a -> stores = List(a.length()) { a.getJSONObject(it).optString("name") } } }
    var photos by remember { mutableStateOf(listOf<JSONObject>()) }
    var reading by remember { mutableStateOf(0) }
    var burst by remember { mutableStateOf(0) }
    var offline by remember { mutableStateOf(false) }
    fun load() = scope.launch {
        val path = "/money/list" + (if (month.isNotBlank()) "?month=$month" else "")
        var r = vm.mac(path)
        if (r.optBoolean("offline") || (r.has("error") && !r.has("entries"))) { offline = true; data = MoneyOffline.offlineData(ctx); return@launch }
        // Mac is back: send what was saved offline (shared, one at a time), then reload
        val sent = vm.syncMoneyNow() > 0
        if (sent) r = vm.mac(path)
        offline = false; data = r; MoneyOffline.cache(ctx, r)
        if (sent) msg = "✅ Offline entries Mac par sync ho gayi"
    }
    LaunchedEffect(month, vm.activeUrl) { load() }
    fun reset() { form = null; editId = null; amount = ""; note = ""; cat = "Other"; whose = "home"; date = ""; person = "Ghar"; photos = emptyList(); msg = ""; catPicked = false; catOpen = false; makeShortcut = false; acMode = ""; acHbl = ""; acPurpose = "" }
    fun prefill(j: JSONObject) {
        reset(); form = j.optString("kind", "spend").ifBlank { "spend" }
        j.optDouble("amount", 0.0).takeIf { it > 0 }?.let { amount = String.format("%.0f", it) }
        note = j.optString("note"); cat = j.optString("category", "Other").ifBlank { "Other" }
        whose = j.optString("whose", "home").ifBlank { "home" }; date = j.optString("date"); person = j.optString("person", "Ghar").ifBlank { "Ghar" }
    }

    var game by remember { mutableStateOf(0.0) }
    fun scanUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (form == null) form = "spend"
        uris.forEach { u ->
            reading++
            scope.launch {
                val b = photoB64(ctx, u)
                if (b == null) { reading--; msg = "⚠️ Photo khul nahi saki"; return@launch }
                // Mac first (better reading); if it can't be reached or is slow, the phone reads the bill itself
                var r = if (offline) JSONObject().put("error", "offline") else
                    kotlinx.coroutines.withTimeoutOrNull(35_000) { vm.mac("/money/scan", JSONObject().put("image", b)) } ?: JSONObject().put("error", "slow")
                if (r.has("error") && !r.has("photo")) {
                    r = runCatching { BillOcr.read(ctx, u) }.getOrElse { JSONObject().put("error", "read") }
                    // keep the photo on the phone; it goes to the Mac with the entry when online
                    val f = java.io.File(ctx.filesDir, "bill_" + System.currentTimeMillis() + ".jpg")
                    runCatching { f.writeBytes(android.util.Base64.decode(b, android.util.Base64.NO_WRAP)) }
                    r.put("local", f.absolutePath)
                    if (r.optDouble("total", 0.0) <= 0) msg = "📷 Photo save ho gayi, lekin total parh nahi saka. Amount khud likh do."
                    else msg = "📱 Phone ne bill parha (offline): PKR ${"%,.0f".format(r.optDouble("total"))}. Theek hai to Save karo."
                }
                reading--
                photos = photos + r
                // total = all photos together; note/category from the first one
                amount = String.format("%.0f", photos.sumOf { it.optDouble("total", 0.0) })
                if (note.isBlank()) note = r.optString("note")
                r.optString("category").takeIf { it.isNotBlank() }?.let { if (photos.size == 1) cat = it }
                r.optString("whose").takeIf { it == "home" || it == "me" }?.let { if (photos.size == 1) whose = it }
                r.optString("date").takeIf { it.length == 10 && date.isBlank() }?.let { date = it }
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris -> scanUris(uris) }
    if (game > 0) NoteCountGame(game) { game = 0.0 }

    val bal = data.optJSONObject("balance") ?: JSONObject()
    val mt = data.optJSONObject("month_total") ?: JSONObject()
    val cats = data.optJSONArray("categories")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
    val entries = data.optJSONArray("entries")?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: emptyList()
    val shownMonth = data.optString("month")

    var wrapped by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { moneyGold = ctx.getSharedPreferences("money_ui", 0).getBoolean("gold", false) }
    LaunchedEffect(data) { data.optJSONObject("balance")?.let { com.englishcoach.app.MoneyWidget.save(ctx, it.optDouble("home_owes"), it.optDouble("wallet")) } }
    if (wrapped) MonthWrapped(data) { wrapped = false }
    LaunchedEffect(burst) { if (burst > 0) { kotlinx.coroutines.delay(1900); burst = 0 } }
    var tools by remember { mutableStateOf(false) }
    var undoId by remember { mutableStateOf<String?>(null) }
    var undoText by remember { mutableStateOf("") }
    var scVersion by remember { mutableStateOf(0) }
    fun quickAdd(e: JSONObject) = scope.launch {
        val b = JSONObject(e.toString()).put("date", "")
        val before = entries.map { it.optString("id") }.toSet()
        val r = vm.mac("/money/add", b)
        if (r.has("error") && !r.has("entries")) { MoneyOffline.queue(ctx, b); offline = true; data = MoneyOffline.offlineData(ctx); undoId = null }
        else { data = r; undoId = r.optJSONArray("entries")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("id") }.firstOrNull { it !in before } } }
        undoText = "✅ ${b.optString("note")} · PKR ${"%,.0f".format(b.optDouble("amount"))} add ho gaya"
        burst++; cashFeedback(ctx)
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().background(mBg).statusBarsPadding().imePadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 150.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BackHeader("Money book") { vm.screen = Screen.Mac } }
                Text(if (moneyGold) "☀️" else "✨", Modifier.background(mPaper, CircleShape).press {
                    moneyGold = !moneyGold; ctx.getSharedPreferences("money_ui", 0).edit().putBoolean("gold", moneyGold).apply()
                }.padding(10.dp), fontSize = 18.sp)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(heroBrush, RoundedCornerShape(24.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    heroNote(bal.optDouble("wallet", 0.0)).let { hv -> if (hv < 10) SpinningCoin(hv, Modifier.size(140.dp)) else TiltNote(hv, Modifier.size(320.dp, 150.dp)) }
                }
                Text("Note ko ungli se ghumao · phone jhukao", color = Color.White.copy(alpha = .55f), fontSize = 10.sp)
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("🏠 Ghar se lene hain", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                        Text(CountUp(bal.optDouble("home_owes", 0.0)), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("👛 Mere paas (pocket)", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                        val pk = maxOf(bal.optDouble("pocket", 0.0), 1.0)
                        MoneyJar((bal.optDouble("wallet", 0.0) / pk).toFloat(), Modifier.padding(vertical = 4.dp).size(30.dp, 48.dp))
                        Text(CountUp(bal.optDouble("wallet", 0.0)), color = Lime, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                    }
                }
                PeopleOwe(data.optJSONObject("people") ?: JSONObject())
                Text("Ghar ka kharcha (mere paise se) ${rs(bal.optDouble("home_spent", 0.0))}  ·  Mila ${rs(bal.optDouble("received", 0.0))}",
                    color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
            }
        }
        item { AccountsCard(entries, acVersion) { acVersion++ } }
        item { MoneyAssistant(vm, offline, { burst++; cashFeedback(ctx) }) { load() } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTile("📷", "Bill photo", Modifier.weight(1f)) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                QuickTile("➕", "Kharcha", Modifier.weight(1f)) { reset(); form = "spend" }
                QuickTile("👛", "Pocket mili", Modifier.weight(1f)) { reset(); form = "pocket"; note = "Pocket money" }
                QuickTile("💵", "Ghar se wapas", Modifier.weight(1f)) { reset(); form = "received"; note = "Ghar se claim mila"; amount = String.format("%.0f", bal.optDouble("home_owes", 0.0).coerceAtLeast(0.0)) }
            }
        }
        form?.let { kind ->
            item {
                Column(Modifier.fillMaxWidth().mcard(22).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (kind == "income") "Kamai aayi (Etsy, freelance…)" else if (kind == "pocket") "Pocket money mili (mere paas aaye)" else if (kind == "received") "Ghar se claim ke paise mile" else if (editId != null) "Kharcha badlo" else "Naya kharcha", color = mInk,
                        fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    if (reading > 0) Text("📖 $reading photo parh raha hoon… (≈20 sec)", color = Violet, fontSize = 12.sp)
                    if (photos.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.forEach { p ->
                            Column(Modifier.width(96.dp)) {
                                if (p.optString("local").isNotBlank()) LocalPic(p.optString("local"), Modifier.size(96.dp))
                                else NetPic(vm, "/money/photo?name=" + p.optString("photo"), Modifier.size(96.dp))
                                Text(rs(p.optDouble("total", 0.0)), color = mInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    photos.flatMap { p -> p.optJSONArray("items")?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: emptyList() }.takeIf { it.isNotEmpty() }?.let { its ->
                        Column(Modifier.fillMaxWidth().background(mPaper, RoundedCornerShape(12.dp)).padding(10.dp)) {
                            its.forEach { i -> Row { Text(i.optString("name"), Modifier.weight(1f), color = mInk, fontSize = 12.sp); Text(rs(i.optDouble("amount", 0.0)), color = mMuted, fontSize = 12.sp) } }
                        }
                    }
                    if (kind != "pocket" && (kind == "received" || whose == "home")) {
                        val pn = data.optJSONArray("people_names")?.let { a -> List(a.length()) { a.getString(it) } } ?: listOf("Ghar")
                        PeopleChips(vm, pn, person, { person = it }) { load() }
                    }
                    MField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "Kitne? (PKR)", number = true, big = true)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(50, 100, 200, 500, 1000, 5000).forEach { q ->
                            Text("+$q", Modifier.background(mPaper, CircleShape).press { amount = String.format("%.0f", (amount.toDoubleOrNull() ?: 0.0) + q) }
                                .padding(horizontal = 12.dp, vertical = 7.dp), color = mInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (amount.isNotBlank()) Text("✕", Modifier.background(mPaper, CircleShape).press { amount = "" }.padding(horizontal = 12.dp, vertical = 7.dp), color = mMuted, fontSize = 12.sp)
                    }
                    MField(note, { v ->
                        note = v
                        if (!catPicked && kind == "spend" && v.length > 2) MoneyParse.parse("1 " + v).let { g -> cat = g.optString("category"); whose = g.optString("whose") }
                    }, "Kis cheez ka? (doctor fee, Ammi ki dawai…)")
                    if (kind == "income") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Etsy", "Freelance", "YouTube", "Other").forEach { sname ->
                                Text(sname, Modifier.background(if (source == sname) Violet else mPaper, CircleShape).press { source = sname }
                                    .padding(horizontal = 12.dp, vertical = 7.dp), color = if (source == sname) Color.White else mInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (source == "Etsy" && stores.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            stores.forEach { st ->
                                Text("🛍 $st", Modifier.background(if (storeName == st) Lime else mPaper, CircleShape).press { storeName = st }
                                    .padding(horizontal = 12.dp, vertical = 7.dp), color = mInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Kab?", color = mMuted, fontSize = 12.sp)
                        listOf("" to "Aaj", "kal" to "Kal", "parsu" to "Parsu").forEach { (v, t) ->
                            Text(t, Modifier.background(if (date == v) Violet else mPaper, CircleShape).press { date = v }.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = if (date == v) Color.White else mInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        val picked = date.length == 10
                        Text(if (picked) "📅 " + date.substring(5).replace("-", "/") else "📅 Aur din", Modifier.background(if (picked) Violet else mPaper, CircleShape).press {
                            val c = java.util.Calendar.getInstance()
                            android.app.DatePickerDialog(ctx, { _, y, m, d -> date = "%04d-%02d-%02d".format(y, m + 1, d) },
                                c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH))
                                .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
                        }.padding(horizontal = 14.dp, vertical = 8.dp), color = if (picked) Color.White else mInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (kind == "spend") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("home" to "🏠 Ghar ka (wapas lena)", "me" to "🙋 Mera apna").forEach { (k, n) ->
                                Text(n, Modifier.weight(1f).background(if (whose == k) Violet else mPaper, CircleShape).press { whose = k }.padding(vertical = 9.dp),
                                    color = if (whose == k) Color.White else mInk, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CatIcon(cat, 28); Spacer(Modifier.width(8.dp))
                            Text(cat, Modifier.weight(1f), color = mInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(if (catOpen) "Theek hai ▲" else "Badlo ▼", Modifier.press { catOpen = !catOpen }, color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (catOpen) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            cats.forEach { c ->
                                Text(catEmoji(c) + " " + c, Modifier.background(if (cat == c) Violet else mPaper, CircleShape).press { cat = c; catPicked = true; catOpen = false }
                                    .padding(horizontal = 12.dp, vertical = 7.dp), color = if (cat == c) Color.White else mInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(Modifier.press { makeShortcut = !makeShortcut }, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (makeShortcut) "☑️" else "⬜", fontSize = 16.sp); Spacer(Modifier.width(6.dp))
                            Text("⚡ Isay shortcut bana do (agli dafa ek tap)", color = mInk, fontSize = 12.sp)
                        }
                    }
                    val acMode2 = acMode.ifBlank { Accounts.defaultAccount(kind) }
                    val acPurpose2 = acPurpose.ifBlank { Accounts.defaultPurpose(kind, whose) }
                    val potNames = remember(entries, acVersion) { Accounts.summary(ctx, entries).pots.values.map { it.name } }
                    AccountPicker(kind, amount.toDoubleOrNull() ?: 0.0, acMode2, acHbl, acPurpose2, potNames,
                        { m -> if (m == "both" && acHbl.isBlank()) acHbl = amount; acMode = m }, { acHbl = it }, { acPurpose = it })
                    amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { NoteFan(it, Modifier.height(64.dp)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Cancel", Modifier.weight(1f)) { reset() }
                        Btn(if (reading > 0) "Wait…" else "✅ Save", Modifier.weight(2f), primary = true) {
                            if (reading > 0) return@Btn
                            val total = amount.toDoubleOrNull() ?: 0.0
                            val mode = acMode.ifBlank { Accounts.defaultAccount(kind) }
                            val hblPart = when (mode) { Accounts.HBL -> total; Accounts.FAYSAL -> 0.0; else -> acHbl.toDoubleOrNull() ?: 0.0 }
                            if (mode == "both" && hblPart > total) { msg = "HBL wala hissa total se zyada hai"; return@Btn }
                            val purposeNow = acPurpose.ifBlank { Accounts.defaultPurpose(kind, whose) }
                            val acKey = editId ?: ("offa" + System.currentTimeMillis())
                            scope.launch {
                                val b = JSONObject().put("kind", kind).put("amount", amount).put("note", note).put("category", cat)
                                    .put("whose", whose).put("date", date).put("person", person)
                                    .put("source", if (kind == "income") source else null).put("store", if (kind == "income" && source == "Etsy") storeName.ifBlank { null } else null).put("photos", JSONArray(photos.mapNotNull { it.optString("photo").ifBlank { null } }))
                                    .put("local_photos", JSONArray(photos.mapNotNull { it.optString("local").ifBlank { null } }))
                                editId?.let { b.put("id", it) } ?: b.put("_tmp", acKey)   // the Mac keeps it as local_id: the account split is found by it
                                val photosUp = offline || vm.uploadLocalPhotos(b)
                                var r = if (photosUp && !offline) vm.mac("/money/add", b) else JSONObject().put("error", "offline")
                                if (r.has("error") && !r.has("entries") && (offline || vm.mac("/money/list").let { it.has("error") || it.optBoolean("offline") })) {
                                    MoneyOffline.queue(ctx, b); offline = true; r = JSONObject().put("ok", true)
                                }
                                if (r.has("error")) msg = r.optString("error") else {
                                    Accounts.saveSplit(ctx, acKey, hblPart, (total - hblPart).coerceAtLeast(0.0), purposeNow); acVersion++
                                    val pocketAmt = if (kind == "pocket") amount.toDoubleOrNull() ?: 0.0 else 0.0
                                    if (makeShortcut && kind == "spend") { Shortcuts.save(ctx, JSONObject().put("kind", "spend").put("amount", amount.toDoubleOrNull() ?: 0.0)
                                        .put("note", note).put("category", cat).put("whose", whose).put("person", person)); scVersion++ }
                                    reset(); if (offline) data = MoneyOffline.offlineData(ctx) else load(); burst++; if (pocketAmt > 0) game = pocketAmt else cashFeedback(ctx)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { ShortcutRow(entries, scVersion) { quickAdd(it) } }
        if (undoText.isNotBlank()) item {
            Row(Modifier.fillMaxWidth().background(lt(Color(0xFFDCFCE7)), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(undoText, Modifier.weight(1f), color = mInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (undoId != null) Text("↩️ Undo", Modifier.press {
                    val id = undoId ?: return@press
                    scope.launch { vm.mac("/money/delete", JSONObject().put("id", id)); undoId = null; undoText = "↩️ Hata diya"; load() }
                }.padding(6.dp), color = Violet, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                Text("  ✕", Modifier.press { undoText = "" }, color = mMuted)
            }
        }
        item { NewPhotosPrompt { scanUris(it) } }
        item { FamilyPending(vm) { load() } }
        item { ClaimButton(vm, data) { load() } }
        if (offline) item {
            Text("📴 Offline: Mac nahi mil raha. Kharcha, pocket money, wapas mila sab phone par save ho raha hai " +
                "(${MoneyOffline.pending(ctx).length()} baqi), Mac milte hi khud sync. Bill photo aur AI ke liye Mac chahiye.",
                Modifier.fillMaxWidth().background(lt(Color(0xFFFEF3C7)), RoundedCornerShape(14.dp)).press { load() }.padding(12.dp),
                color = Color(0xFF92400E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (msg.isNotBlank()) item { Text(msg, color = Color(0xFFE11D48), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (data.optJSONArray("months")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()).forEach { m ->
                    Text(m, Modifier.background(if (m == shownMonth) Violet else mPaper, CircleShape).press { month = m }.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = if (m == shownMonth) Color.White else mInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().mcard(22).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Is mahine: ${rs(mt.optDouble("spent", 0.0))}", color = mInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text("🏠 Ghar ${rs(mt.optDouble("home", 0.0))}  ·  🙋 Mera ${rs(mt.optDouble("me", 0.0))}", color = mMuted, fontSize = 12.sp)
                Text("💼 Kamaya ${rs(mt.optDouble("income", 0.0))}  ·  🐷 Bachaya ${rs(mt.optDouble("saved", 0.0))}", color = mInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("👛 Pocket mili ${rs(mt.optDouble("pocket", 0.0))}  ·  💵 Ghar se wapas ${rs(mt.optDouble("received", 0.0))}", color = mMuted, fontSize = 12.sp)
                val bc = mt.optJSONObject("by_category") ?: JSONObject()
                val total = mt.optDouble("spent", 0.0).coerceAtLeast(1.0)
                bc.keys().asSequence().map { it to bc.optDouble(it) }.sortedByDescending { it.second }.forEach { (c, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatIcon(c, 28); Spacer(Modifier.width(6.dp))
                        Text(c, Modifier.width(96.dp), color = mInk, fontSize = 12.sp, maxLines = 1)
                        Box(Modifier.weight(1f).height(8.dp).background(mPaper, CircleShape)) {
                            Box(Modifier.fillMaxWidth((v / total).toFloat()).fillMaxHeight().background(Violet, CircleShape))
                        }
                        Text(" " + rs(v), color = mMuted, fontSize = 11.sp)
                    }
                }
                Text("🎬 Mahine ka Wrapped dekho", Modifier.press { wrapped = true }
                    .background(Brush.horizontalGradient(listOf(Color(0xFF7C3AED), Color(0xFFDB2777))), CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                Text("📤 Ghar walon ko hisaab bhejo", Modifier.press {
                    scope.launch {
                        val t = vm.mac("/money/report" + (if (month.isNotBlank()) "?month=$month" else "")).optString("text")
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Hisaab"))
                    }
                }.background(mPaper, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp), color = Violet, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }
        val list = entries.filter { it.optString("date").startsWith(shownMonth) }
        if (list.isEmpty()) item { Text("Is mahine koi entry nahi. Bill ki photo daalo.", color = mMuted) }
        items(list, key = { it.optString("id") }) { e ->
            val rec = e.optString("kind") !in listOf("spend", "goal")
            val pop = remember { Animatable(0f) }
            LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = .7f, stiffness = 260f)) }
            Row(Modifier.animateItem().graphicsLayer { alpha = pop.value; translationX = (1 - pop.value) * 120f }.fillMaxWidth().mcard(16).press {
                reset(); form = e.optString("kind"); editId = e.optString("id"); amount = String.format("%.0f", e.optDouble("amount"))
                note = e.optString("note"); cat = e.optString("category"); whose = e.optString("whose"); date = e.optString("date"); person = e.optString("person", "Ghar").ifBlank { "Ghar" }
                val ph = e.optJSONArray("photos"); photos = ph?.let { a -> List(a.length()) { JSONObject().put("photo", a.getString(it)) } } ?: emptyList()
                Accounts.partOf(ctx, e).let { pt ->
                    acMode = if (pt.hbl > 0 && pt.faysal > 0) "both" else if (pt.faysal > 0) Accounts.FAYSAL else Accounts.HBL
                    acHbl = String.format("%.0f", pt.hbl); acPurpose = pt.purpose
                }
            }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    CatIcon(e.optString("category"))
                    Text(if (e.optString("kind") == "income") "💼" else if (e.optString("kind") == "pocket") "👛" else if (rec) "💵" else if (e.optString("whose") == "home") "🏠" else "🙋",
                        Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp), fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.optString("note").ifBlank { e.optString("category") }, color = mInk, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2)
                    val acTag = if (e.optString("kind") == "goal") "" else Accounts.partOf(ctx, e).let { pt ->
                        " · 🏦 " + (if (pt.hbl > 0 && pt.faysal > 0) "HBL ${"%,.0f".format(pt.hbl)} + Faysal ${"%,.0f".format(pt.faysal)}" else if (pt.faysal > 0) "Faysal" else "HBL") }
                    Text(e.optString("date") + " · " + e.optString("category") + acTag + (if ((e.optJSONArray("photos")?.length() ?: 0) > 0) " · 📷" else ""), color = mMuted, fontSize = 11.sp)
                }
                Text((if (rec) "+" else "") + rs(e.optDouble("amount")), color = if (rec) Color(0xFF15803D) else mInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Text("  🗑", Modifier.press { scope.launch { vm.mac("/money/delete", JSONObject().put("id", e.optString("id"))); load() } }, fontSize = 16.sp)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().mcard(18).press { tools = !tools }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🧰 Aur tools: kamai, goals, bills, budget, SMS, PDF, settings", Modifier.weight(1f), color = mInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(if (tools) "▲" else "▼", color = mMuted)
            }
        }
        if (tools) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTile("💼", "Kamai", Modifier.weight(1f)) { reset(); form = "income"; note = "" }
                QuickTile("🎬", "Wrapped", Modifier.weight(1f)) { wrapped = true }
            } }
            item { MoneyQuickRow(vm, month) { prefill(it) } }
            item { GoalsCard(vm, data, bal.optDouble("wallet", 0.0)) { load() } }
            item { ForecastCard(vm, data, month) }
            item { BillsCard(vm, data) { load() } }
            item { Budgets(vm, data, mt) { load() } }
            item { MoneySettings(vm, data) { load() } }
        }
    }
    if (burst > 0) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SuccessLottie(burst); NoteBurst(burst) }
    }
}

@Composable
private fun Btn(t: String, m: Modifier, primary: Boolean = false, on: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (down) .92f else 1f, spring(dampingRatio = .4f), label = "b")
    Text(t, m.graphicsLayer { scaleX = sc; scaleY = sc }.clickable(src, null) { on() }.then(if (primary) Modifier else Modifier.mcard(14)).background(if (primary) Brush.horizontalGradient(listOf(Color(0xFF0F766E), Color(0xFF16A34A))) else Brush.linearGradient(listOf(if (moneyGold) Color(0xFF2A2418) else Color.White, if (moneyGold) Color(0xFF1E1A10) else Color(0xFFF1FFF7))),
        RoundedCornerShape(14.dp)).padding(vertical = 12.dp), color = if (primary) Color.White else mInk,
        fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center)
}

@Composable
private fun NetPic(vm: CoachViewModel, path: String, m: Modifier) {
    var bmp by remember(path) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(path) { vm.macBytes(path)?.let { b -> bmp = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() } } }
    bmp?.let { androidx.compose.foundation.Image(it, null, m.background(mPaper, RoundedCornerShape(10.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
        ?: Box(m.background(mPaper, RoundedCornerShape(10.dp)))
}

@Composable
private fun Budgets(vm: CoachViewModel, data: JSONObject, mt: JSONObject, reload: () -> Unit) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<String?>(null) }
    var v by remember { mutableStateOf("") }
    val budgets = data.optJSONObject("budgets") ?: JSONObject()
    val bc = mt.optJSONObject("by_category") ?: JSONObject()
    val names = data.optJSONArray("budget_names")?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
    val over = data.optJSONArray("over_budget")?.length() ?: 0
    Column(Modifier.fillMaxWidth().mcard(22).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().press { open = !open }) {
            Text("🎯 Budgets" + (if (over > 0) "  ⚠️ $over had se upar" else ""), Modifier.weight(1f), color = if (over > 0) Color(0xFFE11D48) else mInk,
                fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(if (open) "▲" else "▼", color = mMuted)
        }
        if (!open) Text("Mahine ki had rakho. 80% aur had paar hone par notification aayega.", color = mMuted, fontSize = 11.sp)
        if (open) names.forEach { n ->
            val lim = budgets.optDouble(n, 0.0)
            val used = if (n == "Mera apna (total)") mt.optDouble("me", 0.0) else bc.optDouble(n, 0.0)
            Column(Modifier.fillMaxWidth().press { edit = n; v = if (lim > 0) String.format("%.0f", lim) else "" }) {
                Row { Text(n, Modifier.weight(1f), color = mInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(if (lim > 0) "${rs(used)} / ${rs(lim)}" else "had lagao ✏️", color = if (lim > 0 && used > lim) Color(0xFFE11D48) else mMuted, fontSize = 11.sp) }
                if (lim > 0) Box(Modifier.fillMaxWidth().height(7.dp).background(mPaper, CircleShape)) {
                    Box(Modifier.fillMaxWidth((used / lim).coerceIn(0.0, 1.0).toFloat()).fillMaxHeight()
                        .background(if (used > lim) Color(0xFFE11D48) else if (used > lim * .8) MAmber else Color(0xFF16A34A), CircleShape))
                }
            }
            if (edit == n) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MField(v, { v = it.filter { c -> c.isDigit() } }, "Had PKR (0 = hatao)", number = true, modifier = Modifier.weight(1f))
                Btn("Save", Modifier.width(80.dp), primary = true) {
                    scope.launch { vm.mac("/money/budget", JSONObject().put("category", n).put("amount", v.ifBlank { "0" })); edit = null; reload() }
                }
            }
        }
    }
}


/** Big friendly tile for the 4 main actions. */
@Composable
private fun QuickTile(icon: String, label: String, m: Modifier, on: () -> Unit) =
    Column(m.mcard(18).press(on).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(icon, fontSize = 24.sp)
        Text(label, color = mInk, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }

/** Clean rounded input (no grey Material box). */
@Composable
fun MField(v: String, on: (String) -> Unit, hint: String, number: Boolean = false, big: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    androidx.compose.foundation.text.BasicTextField(v, on, modifier.background(mPaper, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = if (big) 14.dp else 12.dp),
        textStyle = androidx.compose.ui.text.TextStyle(color = mInk, fontSize = if (big) 26.sp else 15.sp, fontWeight = if (big) FontWeight.ExtraBold else FontWeight.Medium),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Violet), singleLine = number,
        decorationBox = { inner -> Box { if (v.isEmpty()) Text(hint, color = mMuted, fontSize = if (big) 20.sp else 14.sp); inner() } })
}

@Composable
fun QuickAmounts(onAdd: (Int) -> Unit, list: List<Int> = listOf(100, 500, 1000, 2000, 5000)) =
    Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEach { q -> Text("+$q", Modifier.background(mPaper, CircleShape).press { onAdd(q) }.padding(horizontal = 12.dp, vertical = 7.dp),
            color = mInk, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }


@Composable
private fun LocalPic(path: String, m: Modifier) {
    val bmp = remember(path) { runCatching { BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }.getOrNull() }
    bmp?.let { androidx.compose.foundation.Image(it, null, m.background(mPaper, RoundedCornerShape(10.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
        ?: Box(m.background(mPaper, RoundedCornerShape(10.dp)))
}
