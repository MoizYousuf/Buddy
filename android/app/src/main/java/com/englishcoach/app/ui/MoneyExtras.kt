package com.englishcoach.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.airbnb.lottie.compose.*
import com.englishcoach.app.CoachViewModel
import com.englishcoach.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private fun pk(v: Double) = "PKR " + String.format("%,.0f", v)

// ---------- Lottie tick after a save ----------
@Composable
fun SuccessLottie(key: Int) {
    if (key == 0) return
    val comp by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.money_success))
    var show by remember(key) { mutableStateOf(true) }
    val prog by animateLottieCompositionAsState(comp, isPlaying = show, restartOnPlay = true)
    LaunchedEffect(prog) { if (prog >= 1f) show = false }
    if (show) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LottieAnimation(comp, { prog }, Modifier.size(140.dp)) }
}

// ---------- SMS: bank / JazzCash / Easypaisa messages that look like payments ----------
data class SmsSpend(val amount: Double, val note: String, val date: String)

private val PAY = Regex("(debit|debited|paid|payment|sent|transfer|purchase|spent|withdraw|bill)", RegexOption.IGNORE_CASE)
private val AMT = Regex("(?:rs\\.?|pkr)\\s*([\\d,]+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)

private suspend fun readSms(ctx: Context): List<SmsSpend> = withContext(Dispatchers.IO) {
    val since = System.currentTimeMillis() - 40L * 86400_000
    val out = mutableListOf<SmsSpend>()
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    ctx.contentResolver.query(android.provider.Telephony.Sms.Inbox.CONTENT_URI, arrayOf("address", "body", "date"), "date > ?", arrayOf("$since"), "date DESC")?.use { c ->
        while (c.moveToNext() && out.size < 40) {
            val body = c.getString(1) ?: continue
            if (!PAY.containsMatchIn(body) || Regex("otp|code|pin", RegexOption.IGNORE_CASE).containsMatchIn(body)) continue
            val a = AMT.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: continue
            out += SmsSpend(a, (c.getString(0) ?: "") + ": " + body.take(70), fmt.format(java.util.Date(c.getLong(2))))
        }
    }
    out
}

/** Mic, SMS and PDF buttons. `prefill` opens the entry form with these values. */
@Composable
fun MoneyQuickRow(vm: CoachViewModel, month: String, prefill: (JSONObject) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sms by remember { mutableStateOf<List<SmsSpend>?>(null) }
    var note by remember { mutableStateOf("") }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@rememberLauncherForActivityResult
        scope.launch { val j = vm.mac("/money/parse", JSONObject().put("text", t)); if (!j.has("error")) prefill(j) else note = j.optString("error") }
    }
    fun loadSms() = scope.launch { sms = runCatching { readSms(ctx) }.getOrElse { note = "SMS nahi parh saka"; emptyList() } }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) loadSms() else note = "SMS ki ijazat nahi mili" }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("🎤 Bol kar" to {
            voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK").putExtra(RecognizerIntent.EXTRA_PROMPT, "Jaise: 500 dawai ghar ka"))
        }, "📩 SMS" to {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) loadSms() else perm.launch(Manifest.permission.READ_SMS)
        }, "📄 PDF" to {
            note = "PDF ban raha hai…"
            scope.launch {
                val b = vm.macBytes("/money/pdf" + (if (month.isNotBlank()) "?month=$month" else ""))
                if (b != null && b.size > 4 && String(b, 0, 4) == "%PDF") { note = ""; vm.shareFile(b, "Money_${month.ifBlank { "month" }}.pdf", "application/pdf") }
                else note = "PDF nahi bana"
            }
        }).forEach { (t, f) ->
            Text(t, Modifier.weight(1f).mcard(14).press { f() }.padding(vertical = 11.dp), color = mInk, fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    if (note.isNotBlank()) Text(note, color = mMuted, fontSize = 12.sp)
    sms?.let { list ->
        Column(Modifier.fillMaxWidth().mcard(18).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row { Text("📩 SMS se mile payments (40 din)", Modifier.weight(1f), color = mInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Text("✕", Modifier.press { sms = null }, color = mMuted) }
            if (list.isEmpty()) Text("Koi payment wala SMS nahi mila.", color = mMuted, fontSize = 12.sp)
            list.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pk(s.amount) + " · " + s.date, color = mInk, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(s.note, color = mMuted, fontSize = 10.sp, maxLines = 2)
                    }
                    Text("➕", Modifier.background(Lime, CircleShape).press {
                        prefill(JSONObject().put("amount", s.amount).put("note", s.note.substringAfter(": ").take(60)).put("date", s.date).put("kind", "spend"))
                        sms = list - s
                    }.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 13.sp)
                }
            }
        }
    }
}

// ---------- people ----------
@Composable
fun PeopleChips(vm: CoachViewModel, names: List<String>, picked: String, onPick: (String) -> Unit, reload: () -> Unit) {
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var nm by remember { mutableStateOf("") }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Kis ka?", color = mMuted, fontSize = 12.sp)
        names.forEach { n ->
            Text(n, Modifier.background(if (picked == n) Violet else mPaper, CircleShape).press { onPick(n) }.padding(horizontal = 12.dp, vertical = 7.dp),
                color = if (picked == n) Color.White else mInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text("➕", Modifier.background(mPaper, CircleShape).press { adding = !adding }.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 12.sp)
    }
    if (adding) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MField(nm, { nm = it }, "Naam (Abu, Bhai…)", modifier = Modifier.weight(1f))
        Text("Save", Modifier.background(Violet, CircleShape).press {
            if (nm.isBlank()) return@press
            scope.launch { vm.mac("/money/people", JSONObject().put("names", JSONArray(names + nm.trim()))); onPick(nm.trim()); nm = ""; adding = false; reload() }
        }.padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PeopleOwe(people: JSONObject) {
    val ks = people.keys().asSequence().toList()
    if (ks.size < 2) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ks.forEach { p ->
            Text("$p: ${pk(people.getJSONObject(p).optDouble("owes"))}", Modifier.background(Color.White.copy(alpha = .15f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 5.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------- recurring bills ----------
@Composable
fun BillsCard(vm: CoachViewModel, data: JSONObject, reload: () -> Unit) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var amt by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var whose by remember { mutableStateOf("home") }
    val rec = data.optJSONArray("recurring")?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: emptyList()
    val due = data.optJSONArray("due_bills")?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: emptyList()
    fun save(list: List<JSONObject>) = scope.launch { vm.mac("/money/recurring", JSONObject().put("items", JSONArray(list))); reload() }
    Column(Modifier.fillMaxWidth().mcard(22).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().press { open = !open }) {
            Text("🧾 Har mahine ke bills" + (if (due.isNotEmpty()) "  · ${due.size} due" else ""), Modifier.weight(1f),
                color = if (due.isNotEmpty()) Color(0xFFE11D48) else mInk, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(if (open) "▲" else "▼", color = mMuted)
        }
        due.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CatIcon(b.optString("category"), 32); Spacer(Modifier.width(8.dp))
                Text(b.optString("name") + " · " + pk(b.optDouble("amount")), Modifier.weight(1f), color = mInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("✅ Add", Modifier.background(Lime, CircleShape).press {
                    scope.launch { vm.mac("/money/bill", JSONObject().put("id", b.optString("id"))); reload() }
                }.padding(horizontal = 12.dp, vertical = 6.dp), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (!open && rec.isEmpty()) Text("Bijli, gas, fees: ek dafa daalo, har mahine yaad dilaunga.", color = mMuted, fontSize = 11.sp)
        if (open) {
            rec.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${r.optString("name")} · ${pk(r.optDouble("amount"))} · har ${r.optInt("day")} tareekh · ${if (r.optString("whose") == "home") "🏠" else "🙋"}",
                        Modifier.weight(1f), color = mInk, fontSize = 12.sp)
                    Text("🗑", Modifier.press { save(rec.filter { it.optString("id") != r.optString("id") }) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("💡 Bijli", "🔥 Gas", "🌐 Internet", "💧 Paani", "📱 Mobile", "🎓 Fees", "🏠 Kiraya").forEach { b ->
                    val n = b.substringAfter(" ")
                    Text(b, Modifier.background(if (name == n) Violet else mPaper, CircleShape).press { name = n }.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = if (name == n) Color.White else mInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            MField(name, { name = it }, "Ya apna naam likho")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MField(amt, { amt = it.filter { c -> c.isDigit() } }, "PKR", number = true, modifier = Modifier.weight(1f))
                val bctx = LocalContext.current
                Text(if (day.isBlank()) "📅 Har mahine kis din?" else "📅 Har $day tareekh", Modifier.weight(1f).background(mPaper, RoundedCornerShape(14.dp)).press {
                    val c = java.util.Calendar.getInstance()
                    android.app.DatePickerDialog(bctx, { _, _, _, d -> day = minOf(d, 28).toString() }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH),
                        day.toIntOrNull() ?: c.get(java.util.Calendar.DAY_OF_MONTH)).show()
                }.padding(vertical = 16.dp, horizontal = 12.dp), color = mInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("home" to "🏠 Ghar ka", "me" to "🙋 Mera").forEach { (k, n) ->
                    Text(n, Modifier.weight(1f).background(if (whose == k) Violet else mPaper, CircleShape).press { whose = k }.padding(vertical = 8.dp),
                        color = if (whose == k) Color.White else mInk, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Text("➕ Add bill", Modifier.weight(1f).background(Lime, CircleShape).press {
                    if (name.isBlank() || amt.isBlank()) return@press
                    save(rec + JSONObject().put("name", name).put("amount", amt).put("day", day.toIntOrNull() ?: 1).put("whose", whose).put("category", "Bills"))
                    name = ""; amt = ""; day = ""
                }.padding(vertical = 8.dp), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
