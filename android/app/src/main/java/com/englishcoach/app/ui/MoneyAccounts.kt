package com.englishcoach.app.ui

import androidx.compose.animation.core.animateFloat

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Two bank accounts, kept on the phone (the Mac's money book stores the entries; the phone stores which account paid):
 *  - HBL: pocket money + household money the family gives (family money, split in "pots" by purpose, e.g. Shopping)
 *  - Faysal Bank: the owner's own savings
 * Every entry is paid from / goes into one account, or is split between both. Family money can be moved later
 * from one purpose to another (e.g. Shopping → Hospital / Tests); those moves are kept as a history per account.
 * Splits are saved by the entry's local_id (set by the phone, kept by the Mac) or its id (after an edit).
 */
object Accounts {
    const val HBL = "hbl"
    const val FAYSAL = "faysal"
    fun name(a: String) = if (a == FAYSAL) "Faysal Bank" else "HBL"
    fun sub(a: String) = if (a == FAYSAL) "Meri apni bachat" else "Family ke paise (pocket + ghar)"
    val PURPOSES = listOf("Pocket money", "Ghar ka kharcha", "Shopping", "Hospital / Tests", "Bills", "Bachat")

    private fun p(ctx: Context) = ctx.getSharedPreferences("money_accounts", 0)
    private fun splits(ctx: Context) = runCatching { JSONObject(p(ctx).getString("splits", "{}")!!) }.getOrDefault(JSONObject())

    fun saveSplit(ctx: Context, key: String, hbl: Double, faysal: Double, purpose: String) {
        val s = splits(ctx).put(key, JSONObject().put("hbl", hbl).put("faysal", faysal).put("purpose", purpose))
        p(ctx).edit().putString("splits", s.toString()).apply()
    }

    fun opening(ctx: Context, a: String) = p(ctx).getFloat("open_$a", 0f).toDouble()
    fun setOpening(ctx: Context, a: String, v: Double) = p(ctx).edit().putFloat("open_$a", v.toFloat()).apply()

    fun moves(ctx: Context): List<JSONObject> = runCatching { JSONArray(p(ctx).getString("moves", "[]")!!) }.getOrDefault(JSONArray())
        .let { a -> List(a.length()) { a.getJSONObject(it) } }
    fun addMove(ctx: Context, acc: String, amount: Double, from: String, to: String, note: String) {
        val a = JSONArray(moves(ctx)).put(JSONObject().put("id", "mv" + System.currentTimeMillis()).put("account", acc).put("amount", amount)
            .put("from", from).put("to", to).put("note", note)
            .put("date", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())))
        p(ctx).edit().putString("moves", a.toString()).apply()
    }
    fun removeMove(ctx: Context, id: String) =
        p(ctx).edit().putString("moves", JSONArray(moves(ctx).filter { it.optString("id") != id }).toString()).apply()

    data class Part(val hbl: Double, val faysal: Double, val purpose: String, val saved: Boolean)

    fun defaultAccount(kind: String) = if (kind == "income") FAYSAL else HBL
    fun defaultPurpose(kind: String, whose: String) = when (kind) {
        "pocket" -> "Pocket money"; "income" -> "Bachat"; "received" -> "Ghar ka kharcha"
        else -> if (whose == "me") "Pocket money" else "Ghar ka kharcha"
    }

    /** Which account(s) paid for / received this entry: what was saved, else the default (income → Faysal, all else → HBL). */
    fun partOf(s: JSONObject, e: JSONObject): Part {
        val amt = e.optDouble("amount", 0.0)
        val kind = e.optString("kind", "spend")
        val x = s.optJSONObject(e.optString("id")) ?: e.optString("local_id").takeIf { it.isNotBlank() }?.let { s.optJSONObject(it) }
        if (x != null) {
            val h = x.optDouble("hbl", 0.0).coerceIn(0.0, amt)
            return Part(h, (amt - h).coerceAtLeast(0.0), x.optString("purpose").ifBlank { defaultPurpose(kind, e.optString("whose")) }, true)
        }
        val acc = defaultAccount(kind)
        return Part(if (acc == HBL) amt else 0.0, if (acc == FAYSAL) amt else 0.0, defaultPurpose(kind, e.optString("whose")), false)
    }
    fun partOf(ctx: Context, e: JSONObject) = partOf(splits(ctx), e)

    class Pot(val account: String, val name: String) {
        var given = 0.0; var spent = 0.0; var movedIn = 0.0; var movedOut = 0.0
        val left get() = given + movedIn - movedOut - spent
    }
    class Line(val date: String, val text: String, val amount: Double)   // + in, - out

    class Summary {
        val balance = mutableMapOf(HBL to 0.0, FAYSAL to 0.0)
        val pots = linkedMapOf<String, Pot>()                // "acc|name"
        val history = mutableMapOf(HBL to mutableListOf<Line>(), FAYSAL to mutableListOf())
        var familyGiven = 0.0; var familySpent = 0.0
        var ownUsed = 0.0; var ownForHome = 0.0
        val familyLeft get() = familyGiven - familySpent
        fun pot(a: String, n: String) = pots.getOrPut("$a|$n") { Pot(a, n) }
        fun potsOf(a: String) = pots.values.filter { it.account == a }
        /** Family should give: home costs paid from own savings + family purposes that have run out. */
        val shortPots get() = potsOf(HBL).filter { it.left < -0.5 }
        val askFamily get() = ownForHome + shortPots.sumOf { -it.left }
    }

    fun summary(ctx: Context, entries: List<JSONObject>): Summary {
        val s = splits(ctx); val r = Summary()
        listOf(HBL, FAYSAL).forEach { r.balance[it] = opening(ctx, it) }
        for (e in entries) {
            val kind = e.optString("kind", "spend")
            if (kind !in listOf("spend", "pocket", "received", "income")) continue
            val pt = partOf(s, e)
            val label = e.optString("note").ifBlank { e.optString("category") }.removeSuffix("  ⏳")
            for ((acc, v) in listOf(HBL to pt.hbl, FAYSAL to pt.faysal)) {
                if (v <= 0) continue
                if (kind == "spend") {
                    r.balance[acc] = r.balance[acc]!! - v
                    r.pot(acc, pt.purpose).spent += v
                    r.history[acc]!! += Line(e.optString("date"), "$label · ${pt.purpose}", -v)
                    if (acc == HBL) r.familySpent += v else { r.ownUsed += v; if (e.optString("whose") == "home") r.ownForHome += v }
                } else {
                    r.balance[acc] = r.balance[acc]!! + v
                    r.pot(acc, pt.purpose).given += v
                    r.history[acc]!! += Line(e.optString("date"), "$label · ${pt.purpose}", v)
                    if (acc == HBL && kind != "income") r.familyGiven += v
                }
            }
        }
        for (m in moves(ctx)) {
            val a = m.optString("account", HBL); val v = m.optDouble("amount", 0.0)
            r.pot(a, m.optString("from")).movedOut += v
            r.pot(a, m.optString("to")).movedIn += v
        }
        r.history.values.forEach { l -> l.sortByDescending { it.date } }
        return r
    }
}

private fun rs(v: Double) = "PKR " + String.format("%,.0f", v)

@Composable
private fun Chip(t: String, on: Boolean, m: Modifier = Modifier, click: () -> Unit) =
    Text(t, m.background(if (on) Violet else mPaper, CircleShape).press(click).padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (on) Color.White else mInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

/**
 * In the add/edit form: which account paid (spend) or received (pocket / received / income), with an optional split,
 * and what the money is for (the purpose "pot"). [mode] is hbl / faysal / both.
 */
@Composable
fun AccountPicker(kind: String, total: Double, mode: String, hblPart: String, purpose: String, potNames: List<String>,
                  onMode: (String) -> Unit, onHbl: (String) -> Unit, onPurpose: (String) -> Unit) {
    val spend = kind == "spend"
    Column(Modifier.fillMaxWidth().background(lt(Color(0xFFEFF6FF)), RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (spend) "🏦 Kis account se diya?" else "🏦 Kis account mein aaye?", color = mInk, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BankPick(Accounts.HBL, mode == Accounts.HBL, Modifier.weight(1f)) { onMode(Accounts.HBL) }
            BankPick(Accounts.FAYSAL, mode == Accounts.FAYSAL, Modifier.weight(1f)) { onMode(Accounts.FAYSAL) }
            Chip("Dono", mode == "both", Modifier.width(64.dp)) { onMode("both") }
        }
        if (mode == "both") {
            val h = hblPart.toDoubleOrNull() ?: 0.0
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MField(hblPart, { onHbl(it.filter { c -> c.isDigit() || c == '.' }) }, "HBL se", number = true, modifier = Modifier.weight(1f))
                MField(if (total > 0) String.format("%.0f", (total - h).coerceAtLeast(0.0)) else "", { v ->
                    val f = v.filter { c -> c.isDigit() || c == '.' }.toDoubleOrNull() ?: 0.0
                    onHbl(String.format("%.0f", (total - f).coerceAtLeast(0.0)))
                }, "Faysal se", number = true, modifier = Modifier.weight(1f))
            }
            Text(if (h > total) "⚠️ HBL wala hissa total (${rs(total)}) se zyada hai"
                 else "HBL ${rs(h)} + Faysal ${rs((total - h).coerceAtLeast(0.0))} = ${rs(total)}",
                color = if (h > total) Color(0xFFE11D48) else mMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(if (spend) "Kis kaam ke paise se? (family ne kis liye diye the)" else "Kis kaam ke liye diye?", color = mMuted, fontSize = 11.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (Accounts.PURPOSES + potNames).distinct().forEach { n -> Chip(n, purpose == n) { onPurpose(n) } }
        }
        var custom by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MField(custom, { custom = it }, "Ya naya kaam likho (e.g. Shaadi)", modifier = Modifier.weight(1f))
            if (custom.isNotBlank()) Chip("✓", false) { onPurpose(custom.trim()); custom = "" }
        }
    }
}

/** Money screen card: both balances, the family-money tally, purposes (spent / left), moves and per-account history. */
@Composable
fun AccountsCard(entries: List<JSONObject>, version: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val sm = remember(entries, version, tick) { Accounts.summary(ctx, entries) }
    var open by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(Accounts.HBL) }
    var moving by remember { mutableStateOf(false) }
    var editOpen by remember { mutableStateOf(false) }
    fun changed() { tick++; onChanged() }
    Column(Modifier.fillMaxWidth().mcard(22).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏦 Mere accounts", Modifier.weight(1f), color = mInk, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(if (open) "Kam ▲" else "Hisaab ▼", Modifier.press { open = !open }, color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Accounts.HBL, Accounts.FAYSAL).forEach { a -> BankCard(a, sm.balance[a] ?: 0.0, tab == a && open) { tab = a; open = true } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).background(lt(Color(0xFFDCFCE7)), RoundedCornerShape(14.dp)).padding(10.dp)) {
                Text("👪 Family ke paise", color = Color(0xFF166534), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Mile ${rs(sm.familyGiven)}", color = mInk, fontSize = 12.sp)
                Text("Kharch ${rs(sm.familySpent)}", color = mInk, fontSize = 12.sp)
                Text("Baqi ${rs(sm.familyLeft)}", color = if (sm.familyLeft < 0) Color(0xFFE11D48) else Color(0xFF15803D), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            Column(Modifier.weight(1f).background(lt(Color(0xFFE0E7FF)), RoundedCornerShape(14.dp)).padding(10.dp)) {
                Text("💙 Meri bachat use hui", color = Color(0xFF3730A3), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(rs(sm.ownUsed), color = mInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Text("Ghar ke kaam par ${rs(sm.ownForHome)}", color = mMuted, fontSize = 11.sp)
            }
        }
        if (sm.askFamily > 0.5) Text("🙏 Ghar se maangna hai: ${rs(sm.askFamily)}", Modifier.fillMaxWidth().background(lt(Color(0xFFFEF3C7)), RoundedCornerShape(12.dp)).padding(10.dp),
            color = Color(0xFF92400E), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        if (!open) return@Column

        // ---- summary: what family money was given for, spent, left ----
        Text("Family paise: kis kaam ke liye, kitna kharch, kitna baqi", color = mInk, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        val hp = sm.potsOf(Accounts.HBL).filter { it.given + it.movedIn + it.movedOut + it.spent > 0 }
        if (hp.isEmpty()) Text("Abhi HBL mein koi entry nahi.", color = mMuted, fontSize = 12.sp)
        hp.forEach { p ->
            Row(Modifier.fillMaxWidth().background(mPaper, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = mInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Mile ${rs(p.given)}" + (if (p.movedIn > 0) " · +${rs(p.movedIn)} aaye" else "") + (if (p.movedOut > 0) " · −${rs(p.movedOut)} gaye" else "") +
                        " · Kharch ${rs(p.spent)}", color = mMuted, fontSize = 11.sp)
                }
                Text(if (p.left < -0.5) "Kam ${rs(-p.left)}" else "Baqi ${rs(p.left)}", color = if (p.left < -0.5) Color(0xFFE11D48) else Color(0xFF15803D),
                    fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }
        if (sm.askFamily > 0.5) Column(Modifier.fillMaxWidth().background(lt(Color(0xFFFEF3C7)), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("🙏 Ghar walon se kya maangna hai", color = Color(0xFF92400E), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            if (sm.ownForHome > 0.5) Text("• Ghar ke kaam meri bachat (Faysal) se: ${rs(sm.ownForHome)}", color = mInk, fontSize = 12.sp)
            sm.shortPots.forEach { Text("• ${it.name} ke paise kam pad gaye: ${rs(-it.left)}", color = mInk, fontSize = 12.sp) }
            Text("Kul: ${rs(sm.askFamily)}", color = mInk, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }

        // ---- per account: moves (reassignments) and movements ----
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Accounts.HBL, Accounts.FAYSAL).forEach { a -> Chip(Accounts.name(a), tab == a, Modifier.weight(1f)) { tab = a } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("🔁 Paise doosre kaam par lagao", moving, Modifier.weight(1f)) { moving = !moving; editOpen = false }
            Chip("✏️ Shuru ka balance", editOpen) { editOpen = !editOpen; moving = false }
        }
        if (moving) MoveForm(tab, sm) { moving = false; changed() }
        if (editOpen) {
            var v by remember(tab) { mutableStateOf(String.format("%.0f", Accounts.opening(ctx, tab))) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MField(v, { v = it.filter { c -> c.isDigit() || c == '.' || c == '-' } }, "${Accounts.name(tab)} mein pehle se kitne the", number = true, modifier = Modifier.weight(1f))
                Chip("Save", true) { Accounts.setOpening(ctx, tab, v.toDoubleOrNull() ?: 0.0); editOpen = false; changed() }
            }
        }
        val mv = Accounts.moves(ctx).filter { it.optString("account", Accounts.HBL) == tab }.asReversed()
        Text("🔁 ${Accounts.name(tab)}: paise doosre kaam par (history)", color = mInk, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        if (mv.isEmpty()) Text("Koi nahi.", color = mMuted, fontSize = 11.sp)
        mv.forEach { m ->
            Row(Modifier.fillMaxWidth().background(mPaper, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${m.optString("from")} → ${m.optString("to")}", color = mInk, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(m.optString("date") + (m.optString("note").takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), color = mMuted, fontSize = 11.sp)
                }
                Text(rs(m.optDouble("amount")), color = mInk, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                Text("  🗑", Modifier.press { Accounts.removeMove(ctx, m.optString("id")); changed() }, fontSize = 14.sp)
            }
        }
        Text("📒 ${Accounts.name(tab)}: aana jaana", color = mInk, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        val hist = sm.history[tab].orEmpty()
        if (hist.isEmpty()) Text("Koi entry nahi.", color = mMuted, fontSize = 11.sp)
        hist.take(30).forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l.date.takeLast(5).replace("-", "/"), Modifier.width(44.dp), color = mMuted, fontSize = 11.sp)
                Text(l.text, Modifier.weight(1f), color = mInk, fontSize = 12.sp, maxLines = 1)
                Text((if (l.amount > 0) "+" else "−") + rs(kotlin.math.abs(l.amount)), color = if (l.amount > 0) Color(0xFF15803D) else mInk,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Text("Opening balance + aaye − kharch = balance. Jo entry ka account nahi chuna: kamai → Faysal, baqi sab → HBL.", color = mMuted, fontSize = 10.sp)
    }
}

@Composable
private fun MoveForm(acc: String, sm: Accounts.Summary, done: () -> Unit) {
    val ctx = LocalContext.current
    val names = (sm.potsOf(acc).map { it.name } + Accounts.PURPOSES).distinct()
    var from by remember(acc) { mutableStateOf(sm.potsOf(acc).filter { it.left > 0 }.maxByOrNull { it.left }?.name ?: names.first()) }
    var to by remember(acc) { mutableStateOf("Hospital / Tests") }
    var amt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var toCustom by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().background(lt(Color(0xFFF1EEFF)), RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${Accounts.name(acc)}: kis kaam ke paise se", color = mMuted, fontSize = 11.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEach { n -> val left = sm.pots["$acc|$n"]?.left; Chip(n + (left?.let { " (${String.format("%,.0f", it)})" } ?: ""), from == n) { from = n } }
        }
        Text("Kis kaam par lagaye", color = mMuted, fontSize = 11.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.filter { it != from }.forEach { n -> Chip(n, to == n) { to = n; toCustom = "" } }
        }
        MField(toCustom, { toCustom = it; if (it.isNotBlank()) to = it.trim() }, "Ya naya kaam (e.g. Lab test)")
        MField(amt, { amt = it.filter { c -> c.isDigit() || c == '.' } }, "Kitne? (PKR)", number = true)
        MField(note, { note = it }, "Note (optional)")
        val v = amt.toDoubleOrNull() ?: 0.0
        Chip(if (v > 0 && to != from) "✅ ${String.format("%,.0f", v)}: $from → $to" else "Amount aur kaam chuno", v > 0 && to != from, Modifier.fillMaxWidth()) {
            if (v > 0 && to.isNotBlank() && to != from) { Accounts.addMove(ctx, acc, v, from, to, note.trim()); done() }
        }
    }
}


private fun bankLogo(a: String) = if (a == Accounts.FAYSAL) com.englishcoach.app.R.drawable.logo_faysal else com.englishcoach.app.R.drawable.logo_hbl
private fun bankBrush(a: String) = if (a == Accounts.FAYSAL) Brush.linearGradient(listOf(Color(0xFF1B2A6B), Color(0xFF223A8A), Color(0xFF0E7C86)))
    else Brush.linearGradient(listOf(Color(0xFF00594F), Color(0xFF008C7A), Color(0xFF00594F)))

/** Bank card like a real debit card: logo, balance, what the account is for. */
@Composable
private fun BankCard(a: String, balance: Double, picked: Boolean, onTap: () -> Unit) {
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "shine")
    val sh by inf.animateFloat(-1f, 2f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(3200)), label = "s")
    val count by androidx.compose.animation.core.animateFloatAsState(balance.toFloat(), androidx.compose.animation.core.tween(900), label = "c")
    Box(Modifier.width(270.dp).height(160.dp).shadow(if (picked) 18.dp else 10.dp, RoundedCornerShape(22.dp), spotColor = if (a == Accounts.FAYSAL) Color(0xFF223A8A) else Color(0xFF008C7A))
        .background(bankBrush(a), RoundedCornerShape(22.dp)).press(onTap)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = .06f), size.height * .9f, androidx.compose.ui.geometry.Offset(size.width * .95f, size.height * .1f))
            drawCircle(Color.White.copy(alpha = .05f), size.height * .6f, androidx.compose.ui.geometry.Offset(size.width * .1f, size.height * 1.1f))
            val x = size.width * sh
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .14f), Color.Transparent),
                androidx.compose.ui.geometry.Offset(x - 120f, 0f), androidx.compose.ui.geometry.Offset(x + 120f, size.height)))
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.background(Color.White, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(bankLogo(a)), Accounts.name(a), Modifier.height(20.dp))
                }
                Spacer(Modifier.weight(1f))
                // card chip
                Box(Modifier.size(34.dp, 26.dp).background(Brush.linearGradient(listOf(Color(0xFFFFE08A), Color(0xFFC9A227))), RoundedCornerShape(6.dp)))
            }
            Column {
                Text("Balance", color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
                Text(rs(count.toDouble()), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                Text(Accounts.sub(a), color = Color.White.copy(alpha = .8f), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

/** Account choice in the form: the bank's logo button. */
@Composable
private fun BankPick(a: String, on: Boolean, m: Modifier, click: () -> Unit) =
    Box(m.height(46.dp).background(if (on) Color.White else mPaper, RoundedCornerShape(14.dp))
        .then(if (on) Modifier.border(2.dp, if (a == Accounts.FAYSAL) Color(0xFF223A8A) else Color(0xFF008C7A), RoundedCornerShape(14.dp)) else Modifier)
        .press(click).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(bankLogo(a)), Accounts.name(a), Modifier.height(18.dp),
            alpha = if (on) 1f else .55f)
    }
