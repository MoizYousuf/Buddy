package com.englishcoach.app.ui

import org.json.JSONObject

/** Understands "maine 250 ka cigarette ka packet liya" on the phone itself (no Mac, no internet). */
object MoneyParse {
    private val NUM = Regex("(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k|hazar|hazaar|thousand)?", RegexOption.IGNORE_CASE)
    private val MONEY = Regex("(rupay|rupaye|rupees?|rs\\.?|pkr|liya|li\\b|lia|khareed|kharid|kharch|diye|diya|mila|mile|mili|pocket|kamai|bill|packet|dawai|dawa|paise|paisay|spent|bought|paid)", RegexOption.IGNORE_CASE)

    fun looksLikeMoney(t: String) = NUM.containsMatchIn(t) && MONEY.containsMatchIn(t)

    private val CATS = listOf(
        "Hospital" to "hospital|aspatal|clinic|lab|test",
        "Doctor / Medicine" to "dawai|dawa|medicine|doctor|dr\\b|tablet|injection|pharmacy",
        "Personal / Fun" to "cigarette|cigret|sigret|sigrat|packet|pack|game|movie|cinema|vape|paan|shauq|netflix",
        "Clothes" to "kapr|kapde|kapray|shirt|jeans|shoes|joot|jacket|suit",
        "Grocery" to "sabzi|grocery|ration|atta|aata|doodh|milk|anday|eggs|cheeni|sugar|ghee|chawal",
        "Bills" to "bill|bijli|gas|internet|paani|wifi|recharge|load|balance",
        "Fuel / Transport" to "petrol|fuel|rickshaw|uber|careem|indrive|bus|bykea|parking",
        "Food" to "khana|lunch|dinner|breakfast|nashta|burger|pizza|chai|shawarma|biryani|chips|cold drink|pepsi",
        "School" to "school|fees|fee|kitab|book|copy|tuition|university",
        "Repair" to "repair|mistri|theek|mechanic",
        "Shopping" to "shopping|mall|daraz|online order",
    )
    private val ME = Regex("\\b(mera|meri|mere|apna|apni|apne|personal|khud)\\b", RegexOption.IGNORE_CASE)
    private val HOME = Regex("\\b(ghar|ammi|abu|abbu|ami|amma|bhai ke|behan|family|gharwalon)\\b", RegexOption.IGNORE_CASE)
    private val FILLER = Regex("\\b(maine|mene|main|mein|ne|ka|ki|ke|ko|liya|liye|li|lia|hai|hain|tha|thi|bhai|yaar|yar|rupay|rupaye|rupees?|rs|pkr|aaj|abhi|add|karo|kar|do|ek|wala|wali|diye|diya|kharch|kiye|khareeda|khareedi|kharida)\\b", RegexOption.IGNORE_CASE)

    fun parse(t: String, people: List<String> = listOf("Ghar")): JSONObject {
        val low = t.lowercase()
        val m = NUM.find(t)
        var amount = m?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        if (m?.groupValues?.get(2)?.isNotBlank() == true) amount *= 1000
        val kind = when {
            Regex("pocket").containsMatchIn(low) -> "pocket"
            Regex("kamai|sale|bik(a|i|e)|earned|payment aayi|order aaya").containsMatchIn(low) -> "income"
            Regex("wapas|claim|lota|return").containsMatchIn(low) && Regex("(ghar|ammi|abu|abbu)").containsMatchIn(low) -> "received"
            Regex("(ghar se|ammi se|abu se|abbu se|ghar walon se).*(mil|diye|aaye|aye)|(mil|mile|mili) (ghar|ammi|abu) se").containsMatchIn(low) -> "pocket"
            else -> "spend"
        }
        val cat = CATS.firstOrNull { Regex(it.second, RegexOption.IGNORE_CASE).containsMatchIn(low) }?.first ?: "Other"
        val whose = when {
            ME.containsMatchIn(low) -> "me"
            HOME.containsMatchIn(low) -> "home"
            cat in listOf("Hospital", "Doctor / Medicine", "Grocery", "Bills", "Repair", "School") -> "home"
            else -> "me"
        }
        val note = t.replace(NUM, " ").replace(FILLER, " ").replace(Regex("\\s+"), " ").trim(' ', ',', '.', '-').ifBlank { cat }
        return JSONObject().put("kind", kind).put("amount", amount).put("note", note.replaceFirstChar { it.uppercase() })
            .put("category", cat).put("whose", whose).put("person", people.firstOrNull { low.contains(it.lowercase()) && it != "Ghar" } ?: "Ghar")
            .put("source", if (kind == "income") (if (low.contains("etsy")) "Etsy" else "Other") else null)
    }

    /** "5000 mile, 2000 dawai aur 300 burger" → one piece per amount. */
    fun split(t: String): List<String> {
        val parts = t.split(Regex("\\s*(,|\\baur\\b|\\band\\b|\\bphir\\b|\\bus mein se\\b|\\bisme se\\b|\\bjis mein se\\b|;|\\.|\\n)\\s*", RegexOption.IGNORE_CASE))
            .map { it.trim() }.filter { it.isNotBlank() }
        val out = mutableListOf<String>()
        for (p in parts) if (NUM.containsMatchIn(p) || out.isEmpty()) out += p else out[out.size - 1] = out.last() + " " + p
        return out.filter { NUM.containsMatchIn(it) }
    }

    fun describe(j: JSONObject): String {
        val a = "PKR " + "%,.0f".format(j.optDouble("amount"))
        return when (j.optString("kind")) {
            "pocket" -> "$a pocket money"
            "income" -> "$a kamai · ${j.optString("note")}"
            "received" -> "$a ghar se wapas mile"
            else -> "$a · ${j.optString("note")} · ${if (j.optString("whose") == "home") "🏠 Ghar ka" else "🙋 Mera"} (${j.optString("category")})"
        }
    }
}
