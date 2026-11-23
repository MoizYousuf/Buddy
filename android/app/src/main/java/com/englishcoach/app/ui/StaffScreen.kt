package com.englishcoach.app.ui

import androidx.compose.foundation.horizontalScroll

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Household helpers / staff: money handed to each person (advance, sauda, etc.) and the hisaab they gave back
 * (spent with account, or cash returned). Balance 0 = settled. Kept on the phone only.
 */
object StaffStore {
    private fun p(ctx: Context) = ctx.getSharedPreferences("staff", 0)
    fun all(ctx: Context): List<JSONObject> = JSONArray(p(ctx).getString("people", "[]")).let { a -> List(a.length()) { a.getJSONObject(it) } }
    private fun save(ctx: Context, list: List<JSONObject>) = p(ctx).edit().putString("people", JSONArray(list).toString()).apply()

    fun add(ctx: Context, name: String, role: String, phone: String) =
        save(ctx, all(ctx) + JSONObject().put("id", "s" + System.currentTimeMillis()).put("name", name).put("role", role)
            .put("phone", phone).put("entries", JSONArray()))

    fun remove(ctx: Context, id: String) = save(ctx, all(ctx).filterNot { it.optString("id") == id })

    /** kind = "give" (paise diye) or "back" (hisaab mila / wapas). */
    fun entry(ctx: Context, id: String, kind: String, amount: Double, note: String) = save(ctx, all(ctx).map {
        if (it.optString("id") == id) it.optJSONArray("entries")?.put(JSONObject().put("kind", kind).put("amount", amount)
            .put("note", note).put("date", LocalDate.now().toString()))
        it
    })

    fun entries(x: JSONObject) = x.optJSONArray("entries")?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: emptyList()
    fun given(x: JSONObject) = entries(x).filter { it.optString("kind") == "give" }.sumOf { it.optDouble("amount", 0.0) }
    fun back(x: JSONObject) = entries(x).filter { it.optString("kind") == "back" }.sumOf { it.optDouble("amount", 0.0) }
    fun balance(x: JSONObject) = given(x) - back(x)

    /** Date the account last came to zero (null if never, or if money is still out). */
    fun settledOn(x: JSONObject): String? {
        if (entries(x).isEmpty() || balance(x) > 0.5) return null
        var bal = 0.0; var on: String? = null
        entries(x).forEach { e ->
            bal += if (e.optString("kind") == "give") e.optDouble("amount", 0.0) else -e.optDouble("amount", 0.0)
            if (bal <= 0.5) on = e.optString("date")
        }
        return on
    }
}

private fun rs(v: Double) = "PKR " + String.format(java.util.Locale.US, "%,.0f", v)

/** "0300 1234567" -> "923001234567" for wa.me links. */
private fun waNumber(phone: String): String {
    val d = phone.filter { it.isDigit() }
    return when { d.startsWith("00") -> d.drop(2); d.startsWith("0") -> "92" + d.drop(1); else -> d }
}

@Composable
fun StaffScreen(vm: CoachViewModel) {
    val ctx = LocalContext.current
    var people by remember { mutableStateOf(StaffStore.all(ctx)) }
    fun reload() { people = StaffStore.all(ctx) }
    var open by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val out = people.sumOf { StaffStore.balance(it).coerceAtLeast(0.0) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 150.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { BackHeader("🧹 Helpers & staff") { vm.screen = Screen.Home } }
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF0EA5E9), Color(0xFF6366F1))), RoundedCornerShape(24.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Kis ko kitne diye, kis ka hisaab clear", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(if (out > 0.5) "Abhi ${rs(out)} ka hisaab baqi hai" else "Sab ka hisaab clear ✅", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Sirf is phone par save hota hai.", color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
            }
        }
        item {
            if (!adding) Text("➕ Naya banda add karo", Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(14.dp)).press { adding = true }.padding(12.dp),
                color = Violet, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
            else Column(Modifier.fillMaxWidth().card(20).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MField(name, { name = it }, "Naam (e.g. Shabana)")
                Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Maasi", "Driver", "Cook", "Guard", "Mali", "Dhobi", "Bhai/Behan").forEach { o -> Text(o, Modifier.background(if (role == o) Violet else Pastel, androidx.compose.foundation.shape.CircleShape).press { role = o }
                        .padding(horizontal = 12.dp, vertical = 7.dp), color = if (role == o) Color.White else Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
                MField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "Phone (0300…), WhatsApp ke liye", number = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("💾 Save", Modifier.weight(1f).background(Lime, RoundedCornerShape(14.dp)).press {
                        if (name.isBlank()) return@press
                        StaffStore.add(ctx, name.trim(), role.trim(), phone.trim()); name = ""; role = ""; phone = ""; adding = false; reload()
                    }.padding(12.dp), color = InkOnLight, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Text("Cancel", Modifier.weight(1f).background(Paper, RoundedCornerShape(14.dp)).press { adding = false }.padding(12.dp),
                        color = Ink, textAlign = TextAlign.Center)
                }
            }
        }
        if (people.isEmpty()) item { Text("Abhi koi helper nahi. Upar se add karo.", color = Muted) }
        items(people, key = { it.optString("id") }) { x -> StaffCard(x, open == x.optString("id"), { open = if (open == x.optString("id")) null else x.optString("id") }) { reload() } }
    }
}

@Composable
private fun StaffCard(x: JSONObject, expanded: Boolean, toggle: () -> Unit, changed: () -> Unit) {
    val ctx = LocalContext.current
    val id = x.optString("id")
    val bal = StaffStore.balance(x)
    val settled = StaffStore.settledOn(x)
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val nm = x.optString("name"); val ph = x.optString("phone")

    Column(Modifier.fillMaxWidth().card(20).press { toggle() }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(nm, color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text(listOf(x.optString("role"), ph).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 11.sp)
            }
            Text(when {
                    bal > 0.5 -> "⏳ ${rs(bal)} baqi"
                    bal < -0.5 -> "↩️ ${rs(-bal)} un ke wapas"
                    settled != null -> "✅ Clear · $settled"
                    else -> "—"
                }, Modifier.background(if (bal > 0.5) Color(0xFFFEF3C7) else Color(0xFFDCFCE7), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
                color = InkOnLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text("Diye ${rs(StaffStore.given(x))} · Hisaab mila ${rs(StaffStore.back(x))}", color = Muted, fontSize = 12.sp)
        if (expanded) {
            MField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, "Kitne? (PKR)", number = true, big = true)
            QuickAmounts({ q -> amount = ((amount.toDoubleOrNull() ?: 0.0) + q).toLong().toString() }, listOf(500, 1000, 2000, 5000, 10000))
            Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Sabzi", "Ration", "Petrol", "Tankhwah", "Bills", "Dawai", "Advance").forEach { o -> Text(o, Modifier.background(if (note == o) Violet else Pastel, androidx.compose.foundation.shape.CircleShape).press { note = o }
                        .padding(horizontal = 12.dp, vertical = 7.dp), color = if (note == o) Color.White else Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            MField(note, { note = it }, "Ya apna likho")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("give" to "💸 Diye", "back" to "🧾 Hisaab mila").forEach { (k, label) ->
                    Text(label, Modifier.weight(1f).background(if (k == "give") Color(0xFFFFE4E6) else Color(0xFFDCFCE7), RoundedCornerShape(14.dp)).press {
                        val a = amount.toDoubleOrNull() ?: return@press
                        if (a <= 0) return@press
                        StaffStore.entry(ctx, id, k, a, note.trim()); amount = ""; note = ""; changed()
                    }.padding(12.dp), color = InkOnLight, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                }
            }
            // ask / check the person directly
            if (ph.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val msg = if (bal > 0.5) "Assalam o Alaikum $nm! Aap ko total ${rs(StaffStore.given(x))} diye the, abhi ${rs(bal)} ka hisaab baqi hai. Kitne kharch hue aur kitne bache? Bata dein please."
                          else "Assalam o Alaikum $nm! Aap ka hisaab clear hai (${rs(StaffStore.given(x))} diye, sab ka hisaab mil gaya). Shukriya!"
                Text("💬 WhatsApp pe poocho", Modifier.weight(1f).background(Color(0xFF25D366), RoundedCornerShape(14.dp)).press {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${waNumber(ph)}?text=" + Uri.encode(msg)))) }
                }.padding(12.dp), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, textAlign = TextAlign.Center)
                Text("📞 Call", Modifier.background(Paper, RoundedCornerShape(14.dp)).press {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + ph.filter { it.isDigit() || it == '+' }))) }
                }.padding(12.dp), color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            } else Text("Phone number add nahi hai, is liye WhatsApp/call nahi ho sakta.", color = Muted, fontSize = 11.sp)
            StaffStore.entries(x).reversed().take(20).forEach { e ->
                Row {
                    Text("${e.optString("date")}  ${if (e.optString("kind") == "give") "💸" else "🧾"} ${e.optString("note")}", Modifier.weight(1f), color = Ink, fontSize = 12.sp)
                    Text(rs(e.optDouble("amount", 0.0)), color = if (e.optString("kind") == "give") Color(0xFFDC2626) else Color(0xFF16A34A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(if (confirmDelete) "Pakka hatana hai? Dobara dabao" else "🗑 Hatao", Modifier.press {
                if (confirmDelete) { StaffStore.remove(ctx, id); changed() } else confirmDelete = true
            }.padding(vertical = 4.dp), color = if (confirmDelete) Color(0xFFDC2626) else Muted, fontSize = 12.sp)
        }
    }
}
