package com.englishcoach.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Offline Money book: the last list from the Mac is kept on the phone, and entries saved while the Mac
 * can't be reached wait in a queue (shown right away, sent when the Mac is back).
 */
object MoneyOffline {
    private fun p(ctx: Context) = ctx.getSharedPreferences("money_offline", 0)

    fun cache(ctx: Context, data: JSONObject) = p(ctx).edit().putString("list", data.toString()).apply()
    fun pending(ctx: Context): JSONArray = JSONArray(p(ctx).getString("queue", "[]"))
    private fun setPending(ctx: Context, a: JSONArray) = p(ctx).edit().putString("queue", a.toString()).apply()

    fun queue(ctx: Context, body: JSONObject) {
        val a = pending(ctx); a.put(JSONObject(body.toString()).put("_tmp", body.optString("_tmp").ifBlank { "off" + System.currentTimeMillis() })); setPending(ctx, a)
    }

    fun removeLast(ctx: Context) {
        val a = pending(ctx); val b = JSONArray(); for (i in 0 until a.length() - 1) b.put(a.get(i)); setPending(ctx, b)
    }

    fun removeFirst(ctx: Context) {
        val a = pending(ctx); val b = JSONArray(); for (i in 1 until a.length()) b.put(a.get(i)); setPending(ctx, b)
    }

    /** Cached list with the queued entries added on top (totals updated here on the phone). */
    fun offlineData(ctx: Context): JSONObject {
        val d = runCatching { JSONObject(p(ctx).getString("list", "{}")!!) }.getOrDefault(JSONObject())
        val q = pending(ctx)
        val bal = d.optJSONObject("balance") ?: JSONObject().also { d.put("balance", it) }
        val mt = d.optJSONObject("month_total") ?: JSONObject().also { d.put("month_total", it) }
        val bc = mt.optJSONObject("by_category") ?: JSONObject().also { mt.put("by_category", it) }
        val es = d.optJSONArray("entries") ?: JSONArray()
        val month = d.optString("month", java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date()))
        if (!d.has("month")) d.put("month", month)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        fun add(o: JSONObject, k: String, v: Double) = o.put(k, o.optDouble(k, 0.0) + v)
        val newEs = JSONArray()
        for (i in 0 until q.length()) {
            val e = q.getJSONObject(i)
            if (e.has("id")) continue   // offline edits are only shown after sync
            val a = e.optString("amount").toDoubleOrNull() ?: e.optDouble("amount", 0.0)
            val kind = e.optString("kind", "spend"); val whose = e.optString("whose", "home")
            val date = e.optString("date").ifBlank { today }
            val inMonth = date.startsWith(month)
            when (kind) {
                "spend" -> {
                    if (whose == "home") add(bal, "home_spent", a)
                    add(bal, "wallet", -a)
                    if (inMonth) { add(mt, "spent", a); add(mt, if (whose == "home") "home" else "me", a); add(bc, e.optString("category", "Other"), a) }
                }
                "received" -> { add(bal, "received", a); add(bal, "wallet", a); if (inMonth) add(mt, "received", a) }
                "pocket" -> { add(bal, "pocket", a); add(bal, "wallet", a); if (inMonth) add(mt, "pocket", a) }
                "income" -> { add(bal, "pocket", a); add(bal, "wallet", a); if (inMonth) add(mt, "income", a) }
                "goal" -> add(bal, "wallet", -a)
            }
            newEs.put(JSONObject().put("id", e.optString("_tmp")).put("kind", kind).put("amount", a).put("note", e.optString("note") + "  ⏳")
                .put("category", e.optString("category", if (kind == "pocket") "Pocket money" else "Other")).put("whose", whose)
                .put("date", date).put("person", e.optString("person", "Ghar")).put("photos", JSONArray()).put("offline", true))
        }
        bal.put("home_owes", bal.optDouble("home_spent", 0.0) - bal.optDouble("received", 0.0))
        for (i in 0 until es.length()) newEs.put(es.get(i))
        d.put("entries", newEs)
        if (!d.has("categories")) d.put("categories", JSONArray(listOf("Hospital", "Doctor / Medicine", "Clothes", "Personal / Fun", "Grocery", "Bills",
            "Fuel / Transport", "Food", "School", "Shopping", "Repair", "Other")))
        if (!d.has("months")) d.put("months", JSONArray(listOf(month)))
        return d.put("offline", true)
    }
}
