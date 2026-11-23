package com.englishcoach.app.ui

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/** Reads a bill photo ON THE PHONE (Google ML Kit, offline, free): finds the total and a short note. */
object BillOcr {
    private val NUM = Regex("(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?")
    private val TOTAL = Regex("(grand\\s*total|net\\s*total|total|net\\s*amount|amount\\s*due|payable|balance\\s*due|bill\\s*amount|rs\\.?\\s*total|kul)", RegexOption.IGNORE_CASE)

    suspend fun read(ctx: Context, uri: Uri): JSONObject {
        val img = InputImage.fromFilePath(ctx, uri)
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text = suspendCancellableCoroutine<String> { c ->
            rec.process(img).addOnSuccessListener { c.resume(it.text) }.addOnFailureListener { c.resume("") }
        }
        rec.close()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        fun amounts(l: String) = NUM.findAll(l).mapNotNull { it.value.replace(",", "").toDoubleOrNull() }.filter { it in 1.0..5_000_000.0 && it != 2024.0 && it != 2025.0 && it != 2026.0 }.toList()
        // 1) a "total" line (or the line after it), 2) otherwise the biggest money-looking number
        var total = 0.0
        for ((i, l) in lines.withIndex()) if (TOTAL.containsMatchIn(l) && !Regex("sub\\s*total", RegexOption.IGNORE_CASE).containsMatchIn(l)) {
            val a = amounts(l).ifEmpty { lines.getOrNull(i + 1)?.let { amounts(it) } ?: emptyList() }
            if (a.isNotEmpty()) total = maxOf(total, a.max())
        }
        if (total == 0.0) total = lines.flatMap { l -> if (Regex("(phone|tel|ntn|cnic|invoice|no\\.|#|date|time)", RegexOption.IGNORE_CASE).containsMatchIn(l)) emptyList() else amounts(l) }
            .filter { it < 1_000_000 }.maxOrNull() ?: 0.0
        val skip = Regex("(only|rupees?|thousand|hundred|lakh|total|amount|cash|change|date|time|bill|invoice|receipt|thank|signature|phone|tel|address|road|street|ntn|strn|gst|tax)", RegexOption.IGNORE_CASE)
        val shop = lines.take(8).firstOrNull { it.length in 4..40 && !NUM.containsMatchIn(it) && !skip.containsMatchIn(it) && it.count { c -> c.isLetter() } >= 3 }
            ?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Bill"
        val guess = MoneyParse.parse("1 " + text.take(400))
        return JSONObject().put("total", total).put("note", shop).put("category", guess.optString("category"))
            .put("whose", guess.optString("whose")).put("by", "phone").put("text", text.take(800))
    }
}
