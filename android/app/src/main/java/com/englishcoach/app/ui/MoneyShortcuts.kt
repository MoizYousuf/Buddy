package com.englishcoach.app.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * ⚡ One-tap entries: the owner's own saved shortcuts first, then the things they add most often (learned from the book).
 * Tap = added at once (with Undo). Long-press a saved one = remove it.
 */
object Shortcuts {
    private fun p(ctx: Context) = ctx.getSharedPreferences("money_shortcuts", 0)
    fun saved(ctx: Context): List<JSONObject> = JSONArray(p(ctx).getString("list", "[]")).let { a -> List(a.length()) { a.getJSONObject(it) } }
    fun save(ctx: Context, e: JSONObject) {
        val l = saved(ctx).filterNot { it.optString("note").equals(e.optString("note"), true) && it.optDouble("amount") == e.optDouble("amount") }
        p(ctx).edit().putString("list", JSONArray((listOf(e) + l).take(12)).toString()).apply()
    }
    fun remove(ctx: Context, e: JSONObject) {
        p(ctx).edit().putString("list", JSONArray(saved(ctx).filterNot { it.toString() == e.toString() }).toString()).apply()
    }
    /** Most repeated (note, amount, whose) from the book. */
    fun learned(entries: List<JSONObject>): List<JSONObject> = entries.filter { it.optString("kind") == "spend" && it.optString("note").length in 2..30 }
        .groupBy { it.optString("note").lowercase().trim() + "|" + it.optDouble("amount") + "|" + it.optString("whose") }
        .values.sortedByDescending { it.size }.take(6).map { g -> g.first().let { e ->
            JSONObject().put("kind", "spend").put("amount", e.optDouble("amount")).put("note", e.optString("note")).put("category", e.optString("category"))
                .put("whose", e.optString("whose")).put("person", e.optString("person", "Ghar")) } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortcutRow(entries: List<JSONObject>, version: Int, onTap: (JSONObject) -> Unit) {
    val ctx = LocalContext.current
    var v by remember { mutableStateOf(0) }
    val saved = remember(v, version) { Shortcuts.saved(ctx) }
    val learned = remember(entries) { Shortcuts.learned(entries) }
    val all = (saved + learned.filter { l -> saved.none { it.optString("note").equals(l.optString("note"), true) && it.optDouble("amount") == l.optDouble("amount") } }).take(12)
    if (all.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("⚡ Ek tap mein add", color = mMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            all.forEach { e ->
                val mine = e in saved
                Row(Modifier.mcard(16).combinedClickable(onClick = { onTap(e) }, onLongClick = { if (mine) { Shortcuts.remove(ctx, e); v++ } })
                    .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CatIcon(e.optString("category"), 26)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(e.optString("note").take(18), color = mInk, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("PKR ${"%,.0f".format(e.optDouble("amount"))} · ${if (e.optString("whose") == "home") "🏠" else "🙋"}${if (mine) " ⭐" else ""}",
                            color = mMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
