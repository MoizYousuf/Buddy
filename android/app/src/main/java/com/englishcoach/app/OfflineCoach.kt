package com.englishcoach.app

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Offline mode (6.2): a small model (Qwen 2.5 1.5B, Apache-2.0) runs on the phone with MediaPipe, so practice works
 * without the Mac. The model is downloaded once from the Mac over home Wi-Fi (it can resume a broken download).
 */
class OfflineCoach(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "models").apply { mkdirs() }
    val file = File(dir, "qwen2.5-1.5b-q8.task")
    private val part = File(dir, "qwen2.5-1.5b-q8.task.part")
    private var llm: LlmInference? = null

    val downloaded get() = file.exists() && file.length() > 1_500_000_000
    val partialBytes get() = if (part.exists()) part.length() else 0L

    /** Downloads (or resumes) the model from the Mac. [progress] gets (bytes so far, total). */
    suspend fun download(api: CoachApi, total: Long, progress: (Long, Long) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (downloaded) return@withContext true
        val path = "/offline/model"
        var have = partialBytes
        val conn = URL(api.baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000; conn.readTimeout = 60_000
        api.signPublic(conn, "GET", path)
        if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
        try {
            val code = conn.responseCode
            if (code != 200 && code != 206) return@withContext false
            if (code == 200) have = 0
            RandomAccessFile(part, "rw").use { out ->
                out.seek(have)
                val buf = ByteArray(1 shl 20)
                conn.inputStream.use { inp ->
                    while (true) {
                        val n = inp.read(buf); if (n < 0) break
                        out.write(buf, 0, n); have += n
                        progress(have, total)
                    }
                }
            }
            if (part.length() >= total && total > 0) { part.renameTo(file); true } else false
        } catch (_: Exception) { false } finally { conn.disconnect() }
    }

    fun delete() { llm?.close(); llm = null; file.delete(); part.delete() }

    private fun engine(): LlmInference {
        llm?.let { return it }
        val o = LlmInference.LlmInferenceOptions.builder().setModelPath(file.path).setMaxTokens(1024).setMaxTopK(40).build()
        return LlmInference.createFromOptions(ctx, o).also { llm = it }
    }

    /** One practice turn on the phone: correction, a more natural way to say it, a short explanation and the next question. */
    suspend fun turn(role: String, question: String, said: String): TurnResult? = withContext(Dispatchers.Default) {
        if (!downloaded) return@withContext null
        val system = "You are $role. You help a Pakistani learner practise spoken English. " +
            "Reply with ONLY one JSON object: {\"has_mistake\": true or false, \"correct\": \"their sentence with only the grammar fixed " +
            "(keep their words)\", \"natural\": \"how a native speaker would say it\", \"why_en\": \"one short reason\", " +
            "\"why_roman\": \"the same reason in Roman Urdu\", \"next_question\": \"your next short question in English\"}"
        val prompt = "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\nQuestion: $question\nLearner said: $said<|im_end|>\n<|im_start|>assistant\n"
        val raw = runCatching { engine().generateResponse(prompt) }.getOrNull() ?: return@withContext null
        val s = raw.indexOf('{'); val e = raw.lastIndexOf('}')
        val o = runCatching { JSONObject(raw.substring(s, e + 1)) }.getOrNull() ?: return@withContext null
        // keep the learner's own words: a small model sometimes writes a new answer instead of fixing theirs
        fun w(t: String) = t.lowercase().split(Regex("[^a-z0-9']+")).filter { it.length > 1 }
        val saidW = w(said); val overlap = { t: String -> if (saidW.isEmpty()) 0.0 else w(t).count { it in saidW }.toDouble() / saidW.size }
        val correct = o.optString("correct").ifBlank { said }.let { if (overlap(it) < 0.6) said else it }
        val mistake = o.optBoolean("has_mistake", !correct.trim().equals(said.trim(), ignoreCase = true))
        TurnResult(mistake && correct != said, correct, o.optString("natural").takeIf { overlap(it) >= 0.3 } ?: "", o.optString("next_question").ifBlank { "Tell me more about that." }, "", false,
            if (mistake) listOf(Explanation(o.optString("why_en"), o.optString("why_roman"), "")) else emptyList(), emptyList())
    }
}
