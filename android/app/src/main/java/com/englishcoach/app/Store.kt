package com.englishcoach.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** A mistake the learner made, kept for review until they say it correctly twice. */
data class Mistake(
    val said: String,
    val correct: String,
    val category: String,
    val explanation: Explanation?,
    val date: String,
    val reviewsPassed: Int = 0,
) {
    val mastered get() = reviewsPassed >= 2
}

/** Everything the app remembers between sessions, saved as one small JSON file on the phone. */
class Store(context: Context) {
    private val file = File(context.filesDir, "progress.json")

    var mistakes: List<Mistake> = emptyList(); private set
    /** ISO date -> number of answers given that day. */
    var answersByDay: Map<String, Int> = emptyMap(); private set
    var fixedCount = 0; private set

    /** Called after every save (the app uses it to back the progress up to the Mac). */
    var onSaved: (() -> Unit)? = null

    init { load() }

    /** The saved file as text (for the backup) and a way to put a backup back. */
    fun exportJson(): String = if (file.exists()) file.readText() else "{}"
    val isEmpty get() = answersByDay.isEmpty() && mistakes.isEmpty()
    fun importJson(text: String) { file.writeText(text); load() }

    fun recordAnswer() {
        val today = LocalDate.now().toString()
        answersByDay = answersByDay + (today to (answersByDay[today] ?: 0) + 1)
        save()
    }

    fun addMistake(said: String, turn: TurnResult) {
        val m = Mistake(said, turn.correct, turn.categories.firstOrNull() ?: "other",
            turn.explanations.firstOrNull(), LocalDate.now().toString())
        mistakes = listOf(m) + mistakes.filterNot { it.correct.equals(m.correct, ignoreCase = true) }
        save()
    }

    fun reset() { mistakes = emptyList(); answersByDay = emptyMap(); fixedCount = 0; save() }

    fun recordFix() { fixedCount++; save() }

    fun reviewed(m: Mistake, passed: Boolean) {
        mistakes = mistakes.map { if (it == m) it.copy(reviewsPassed = if (passed) it.reviewsPassed + 1 else 0) else it }
        if (passed) fixedCount++
        save()
    }

    /** 10 XP per answer, 20 more per mistake fixed. */
    val xp get() = answersByDay.values.sum() * 10 + fixedCount * 20

    /** Mistakes still to practice, oldest first so nothing is forgotten. */
    val dueForReview get() = mistakes.filterNot { it.mastered }.reversed()

    val streak: Int
        get() {
            var day = LocalDate.now()
            if (answersByDay[day.toString()] == null) day = day.minusDays(1)
            var n = 0
            while (answersByDay[day.toString()] != null) { n++; day = day.minusDays(1) }
            return n
        }

    /** Answers for each of the last 7 days, oldest first. */
    val lastWeek: List<Pair<LocalDate, Int>>
        get() = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }.map { it to (answersByDay[it.toString()] ?: 0) }

    val topCategories: List<Pair<String, Int>>
        get() = mistakes.groupingBy { it.category }.eachCount().toList().sortedByDescending { it.second }.take(5)

    /** One week of practice: [offset] 0 = the last 7 days, 1 = the 7 days before that. */
    data class Week(val days: Int, val answers: Int, val xp: Int, val made: Int, val mastered: Int, val cats: Map<String, Int>)
    fun week(offset: Int): Week {
        val end = LocalDate.now().minusDays(7L * offset)
        val range = (0..6).map { end.minusDays(it.toLong()).toString() }.toSet()
        val answers = answersByDay.filterKeys { it in range }
        val made = mistakes.filter { it.date in range }
        return Week(answers.count { it.value > 0 }, answers.values.sum(), answers.values.sum() * 10, made.size,
            made.count { it.mastered }, made.groupingBy { it.category }.eachCount())
    }
    val practicedToday get() = answersByDay[LocalDate.now().toString()] != null

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val o = JSONObject(file.readText())
            fixedCount = o.optInt("fixed")
            val days = o.optJSONObject("days") ?: JSONObject()
            answersByDay = days.keys().asSequence().associateWith { days.getInt(it) }
            val arr = o.optJSONArray("mistakes") ?: JSONArray()
            mistakes = (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                val ex = m.optJSONObject("ex")?.let { Explanation(it.optString("en"), it.optString("roman"), it.optString("urdu")) }
                Mistake(m.getString("said"), m.getString("correct"), m.optString("cat", "other"), ex, m.optString("date"), m.optInt("passed"))
            }
        }
    }

    private fun save() {
        val arr = JSONArray()
        mistakes.take(300).forEach { m ->
            val o = JSONObject().put("said", m.said).put("correct", m.correct).put("cat", m.category)
                .put("date", m.date).put("passed", m.reviewsPassed)
            m.explanation?.let { o.put("ex", JSONObject().put("en", it.en).put("roman", it.roman).put("urdu", it.urdu)) }
            arr.put(o)
        }
        val days = JSONObject().apply { answersByDay.forEach { (k, v) -> put(k, v) } }
        file.writeText(JSONObject().put("fixed", fixedCount).put("days", days).put("mistakes", arr).toString())
        onSaved?.invoke()
    }
}

/** Short readable names for the server's mistake categories. */
val CATEGORY_NAMES = mapOf(
    "having_state" to "\"am having\" / -ing verbs", "experience_of" to "years of experience", "since_for" to "since vs for",
    "present_perfect_continuous" to "have been + -ing", "past_tense" to "Past tense", "subject_verb_agreement" to "He/she + verb",
    "discuss_about" to "discuss about", "can_able" to "can able", "myself_intro" to "\"Myself …\" intro",
    "belong_from" to "belonging from", "done_graduation" to "done my graduation", "do_the_needful" to "do the needful",
    "article_missing" to "a / an / the", "question_order" to "Question order", "preposition" to "Prepositions",
    "word_choice" to "Word choice", "urdu_input" to "Said in Urdu", "other" to "Other",
)
