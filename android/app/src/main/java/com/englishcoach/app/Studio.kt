package com.englishcoach.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class StudioShot(val n: Int, val location: String, val action: String, val dialogue: List<String>,
                      val keyframe: Boolean = false, val clip: Boolean = false, val qc: Boolean = false)
data class StudioPlan(val mode: String, val label: String, val title: String, val why: String, val story: String,
                      val credits: Int?, val quality: Int?, val warnings: List<String>, val shots: List<StudioShot>)
data class StudioDay(val day: String, val type: String, val title: String)
data class StudioJob(val index: Int, val title: String, val mode: String, val date: String, val status: String, val note: String,
                     val added: String, val started: String, val finished: String, val folder: String, val why: String, val credits: String)
data class FlowProgress(val title: String, val status: String, val pct: Int, val shots: Int, val images: Int, val clips: Int,
                        val qc: Int, val now: String, val minutes: Int, val result: String, val log: List<String>,
                        val denied: List<String> = emptyList(), val folder: String = "", val mode: String = "",
                        val phases: List<String> = emptyList(), val phaseNo: Int = 0, val phase: String = "", val elapsedMin: Int = 0,
                        val leftMin: Int = 0, val creditsUsed: Int = 0, val budget: Int = 0, val running: Boolean = false, val limit: Boolean = false)
data class StudioStatus(val running: Boolean, val task: String, val error: String, val pct: Int?, val current: String?,
                        val eta: Int, val log: List<String>)

private fun JSONArray?.strings(): List<String> = this?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList()
private fun JSONArray?.objects(): List<JSONObject> = this?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: emptyList()

/** State for the Studio screen: your Studio dashboard on the Mac, reached through the signed /studio/ routes. */
class StudioModel(private val api: CoachApi, private val scope: CoroutineScope) {
    var date by mutableStateOf("")
    var series by mutableStateOf("")
    var plans by mutableStateOf(listOf<StudioPlan>())
    var week by mutableStateOf(listOf<StudioDay>())
    var queue by mutableStateOf(listOf<StudioJob>())
    var status by mutableStateOf<StudioStatus?>(null)
    var open by mutableStateOf<String?>(null)  // mode of the plan being viewed
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var scheduleOn by mutableStateOf(false)
    var longTime by mutableStateOf("17:00")
    var shortTime by mutableStateOf("12:00")
    val chat = mutableStateListOf<Pair<String, String>>()  // role to text
    var chatBusy by mutableStateOf(false)
    var profiles by mutableStateOf(listOf<Pair<String, String>>())  // Chrome profile dir to name
    var flow by mutableStateOf<FlowProgress?>(null)
    var showLog by mutableStateOf(false)
    var creditsLeft by mutableStateOf<Int?>(null)
    var creditsChecked by mutableStateOf("")
    var creditsChecking by mutableStateOf(false)
    var stats by mutableStateOf<JSONObject?>(null)
    var statsLoading by mutableStateOf(false)
    fun loadStats(force: Boolean = false) {
        scope.launch { statsLoading = true; runCatching { api.analytics(force) }.getOrNull()?.takeIf { !it.has("error") }?.let { stats = it }; statsLoading = false }
    }
    var guard by mutableStateOf<JSONObject?>(null)
    fun loadGuard() { scope.launch { runCatching { api.creditGuard() }.getOrNull()?.let { guard = it } } }
    fun setDailyLimit(n: Int) { scope.launch { runCatching { api.creditSettings(n, null) }.getOrNull()?.let { guard = it; message = "Daily limit: $n credits" } } }
    fun setRenewDay(d: Int) { scope.launch { runCatching { api.creditSettings(null, d) }.getOrNull()?.let { guard = it } } }
    fun allowExtra(n: Int) { scope.launch { runCatching { api.creditExtra(n) }.getOrNull()?.let { guard = it; message = "+$n credits allowed for today only" } } }
    private var poll: Job? = null
    private var flowPoll: Job? = null

    private suspend fun refreshFlow() {
        runCatching {
            val r = api.studio("flow_progress")
            flow = if (!r.optBoolean("active")) null else FlowProgress(r.optString("title"), r.optString("status"), r.optInt("pct"),
                r.optInt("shots"), r.optInt("images"), r.optInt("clips"), r.optInt("qc"), r.optString("now"), r.optInt("minutes"),
                r.optString("result"), r.optJSONArray("log").strings(),
                r.optJSONArray("denied").strings(), r.optString("folder"), r.optString("mode"),
                r.optJSONArray("phases").strings(), r.optInt("phase_no"), r.optString("phase"), r.optInt("elapsed_min"),
                r.optInt("left_min"), r.optInt("credits_used"), r.optInt("budget"), r.optBoolean("running"), r.optBoolean("limit"))
        }
    }

    /** Refreshes the Create-video progress every 3 s while the Studio screen is open. */
    fun watchFlow(on: Boolean) {
        flowPoll?.cancel()
        if (on) flowPoll = scope.launch { while (true) { refreshFlow(); delay(3000) } }
    }

    val plan get() = plans.firstOrNull { it.mode == open }

    fun load() {
        scope.launch {
            loading = true
            runCatching {
                val t = api.studio("today")
                t.optString("error").takeIf { it.isNotBlank() }?.let { throw Exception(it) }
                date = t.optString("date"); series = t.optString("series")
                week = t.optJSONArray("days").objects().map { StudioDay(it.optString("day"), it.optString("type"), it.optString("title")) }
                plans = t.optJSONArray("plans").objects().map { p ->
                    StudioPlan(p.optString("mode"), p.optString("label"), p.optString("title"), p.optString("why"), p.optString("story"),
                        p.optInt("credits").takeIf { p.has("credits") && !p.isNull("credits") },
                        p.optInt("quality").takeIf { p.has("quality") && !p.isNull("quality") },
                        p.optJSONArray("warnings").strings(),
                        p.optJSONArray("shots").objects().map { s ->
                            StudioShot(s.optInt("n"), s.optString("location"), s.optString("action"), s.optJSONArray("dialogue").strings())
                        })
                }
                val sc = api.studio("schedule")
                scheduleOn = sc.optBoolean("enabled"); longTime = sc.optString("long_time", longTime); shortTime = sc.optString("short_time", shortTime)
                error = null
            }.onFailure { error = it.message ?: "Studio not reachable" }
            loadQueue(); refreshStatus(); loadCredits(); loadGuard()
            runCatching {
                profiles = api.studio("chrome_profiles").optJSONArray("items").objects()
                    .map { it.optString("dir") to it.optString("name") }
                    .sortedBy { if (it.second.contains("arbaz", true)) 0 else 1 }
            }
            loading = false
        }
    }

    private suspend fun loadQueue() {
        runCatching {
            val arr = api.studio("flowqueue")
            queue = arr.optJSONArray("items").objects().mapIndexed { i, o ->
                val f = { k: String -> if (o.isNull(k)) "" else o.optString(k) }
                StudioJob(i, f("title"), f("mode"), f("date"), f("status"), f("note"), f("added"), f("started"), f("finished"),
                    f("folder"), f("why"), f("credits"))
            }.reversed()
        }
    }

    /** Shot progress (keyframe / clip / QC) for the open plan. */
    fun loadPipeline(mode: String) {
        scope.launch {
            runCatching {
                val r = api.studio("pipeline?mode=$mode")
                val done = r.optJSONArray("shots").objects().associate { it.optInt("n") to it }
                plans = plans.map { p ->
                    if (p.mode != mode) p else p.copy(shots = p.shots.map { s ->
                        val d = done[s.n] ?: return@map s
                        s.copy(keyframe = !d.isNull("keyframe"), clip = !d.isNull("clip"), qc = !d.isNull("qc"))
                    })
                }
            }
        }
    }

    private suspend fun refreshStatus() {
        runCatching {
            val r = api.studio("status")
            val pr = r.optJSONObject("progress")
            status = StudioStatus(r.optBoolean("running"), r.optString("task"), r.optString("error"),
                pr?.optInt("pct"), pr?.optString("current")?.takeIf { it.isNotBlank() && it != "null" }, pr?.optInt("eta") ?: 0,
                r.optJSONArray("log").strings().takeLast(6))
        }
    }

    /** Polls the job status every 2 s until Studio's job finishes, then reloads. */
    private fun watch() {
        poll?.cancel()
        poll = scope.launch {
            do { delay(2000); refreshStatus() } while (status?.running == true)
            message = status?.error?.takeIf { it.isNotBlank() } ?: "Done: ${status?.task}"
            load(); open?.let { loadPipeline(it) }
        }
    }

    /** One Studio action (regen, autofix, edit, images, clips, qc, analyze, calendar …). */
    fun run(name: String, body: JSONObject = JSONObject(), longJob: Boolean = true) {
        scope.launch {
            val r = runCatching { api.studio(name, body) }.getOrElse { JSONObject().put("error", it.message) }
            when {
                r.optString("error").isNotBlank() -> message = r.optString("error")
                longJob && !r.optBoolean("ok", true) -> message = "Studio is busy with another job"
                longJob -> { message = "Started"; refreshStatus(); watch() }
                else -> { message = "Done"; loadQueue() }
            }
        }
    }

    fun mode(m: String) = JSONObject().put("mode", m)

    /** Hands the queued video to Claude on the Mac, which makes it in Google Flow following FLOW_RULES.md. */
    fun startFlow(m: String, override: Boolean = false) {
        scope.launch {
            if (!override) runCatching { api.studio("create", mode(m)) }
            val r = runCatching { api.studio("flow_start", mode(m).put("override", override)) }.getOrElse { JSONObject().put("error", it.message) }
            if (r.optBoolean("credit_guard")) { GuardAsk.ask = r.optString("error") to { startFlow(m, true) }; return@launch }
            message = r.optString("error").ifBlank { "Claude started making the video" }
            refreshFlow()
            loadQueue()
        }
    }

    /** Owner allowed the commands Claude was stopped on: save them on the Mac and run the same video again. */
    fun allowAndRetry(rules: List<String>) {
        val f = flow ?: return
        val body = JSONObject().put("allow", JSONArray(rules)).put("mode", f.mode)
        if (f.folder.isNotBlank()) body.put("folder", f.folder).put("title", f.title)
        flowCall(body, "Allowed. Claude is continuing the video")
    }

    /** Finishes a video whose Flow clips already exist (edit, SEO, post), without new Flow credits. */
    fun finish(j: StudioJob) =
        flowCall(JSONObject().put("mode", j.mode).put("folder", j.folder).put("title", j.title), "Claude is finishing \"${j.title}\"")

    private fun flowCall(body: JSONObject, ok: String) {
        scope.launch {
            val r = runCatching { api.studio("flow_start", body) }.getOrElse { JSONObject().put("error", it.message) }
            message = r.optString("error").ifBlank { ok }
            refreshFlow(); loadQueue()
        }
    }

    private suspend fun loadCredits() {
        runCatching {
            val r = api.studio("credits")
            creditsLeft = if (r.isNull("left")) null else r.optInt("left")
            creditsChecked = r.optString("checked")
        }
    }

    /** Reads the real number from Google Flow in Chrome on the Mac (spends no credits), then refreshes. */
    fun checkCredits() {
        if (creditsChecking) return
        scope.launch {
            creditsChecking = true
            val before = creditsChecked
            val r = runCatching { api.studio("credits_check", JSONObject()) }.getOrElse { JSONObject().put("error", it.message) }
            if (r.optString("error").isNotBlank()) message = r.optString("error")
            else repeat(24) { delay(7_500); loadCredits(); if (creditsChecked != before) { creditsChecking = false; return@launch } }
            creditsChecking = false
        }
    }

    /** Starts a stopped video again from where it stopped (keeps the clips it already made). */
    fun continueFlow() {
        val f = flow ?: return
        val body = JSONObject().put("mode", f.mode)
        if (f.folder.isNotBlank()) body.put("folder", f.folder).put("title", f.title)
        flowCall(body, "Continuing \"${f.title}\"")
    }

    /** Talk to the video's Claude job (running or stopped), optionally approving extra Flow credits for this video. */
    fun tellClaude(text: String, extra: Int) {
        val f = flow
        val body = JSONObject().put("text", text).put("extra_credits", extra)
        if (f != null && f.folder.isNotBlank()) body.put("folder", f.folder)
        scope.launch {
            val r = runCatching { api.send("/jobs/reply", body) }.getOrElse { JSONObject().put("error", it.message) }
            message = r.optString("error").ifBlank { if (extra > 0) "Sent to Claude with +$extra credits" else "Sent to Claude" }
        }
    }

    fun openChrome(dir: String, name: String) {
        scope.launch {
            val r = runCatching { api.studio("chrome_open", JSONObject().put("dir", dir)) }.getOrElse { JSONObject().put("error", it.message) }
            message = r.optString("error").ifBlank { "Chrome opened on the Mac: $name › Google Flow" }
        }
    }

    fun removeQueued(j: StudioJob) {
        scope.launch {
            val r = runCatching { api.studio("flowqueue", JSONObject().put("remove", j.index)) }.getOrElse { JSONObject().put("error", it.message) }
            message = r.optString("error").ifBlank { "Removed from the queue" }
            loadQueue()
        }
    }

    fun openFolder(folder: String) {
        scope.launch {
            val r = runCatching { api.studio("open_folder", JSONObject().put("folder", folder)) }.getOrElse { JSONObject().put("error", it.message) }
            message = r.optString("error").ifBlank { "Folder opened in Finder on the Mac" }
        }
    }

    fun saveSchedule(on: Boolean, long: String, short: String) {
        scope.launch {
            val r = runCatching { api.studio("schedule", JSONObject().put("enabled", on).put("long_time", long).put("short_time", short)) }
                .getOrElse { JSONObject().put("error", it.message) }
            message = r.optString("error").ifBlank { scheduleOn = on; longTime = long; shortTime = short; "Schedule saved" }
        }
    }

    fun ask(text: String) {
        if (text.isBlank() || chatBusy) return
        chat.add("user" to text)
        scope.launch {
            chatBusy = true
            val msgs = JSONArray().apply { chat.forEach { put(JSONObject().put("role", it.first).put("content", it.second)) } }
            val r = runCatching { api.studio("chat", JSONObject().put("messages", msgs).apply { open?.let { put("mode", it) } }) }
                .getOrElse { JSONObject().put("error", it.message) }
            chat.add("assistant" to r.optString("reply").ifBlank { r.optString("error", "No reply") })
            chatBusy = false
        }
    }
}


/** Credit guard warning: shows how much is left today; the owner can override and start anyway. */
object GuardAsk { var ask by androidx.compose.runtime.mutableStateOf<Pair<String, () -> Unit>?>(null) }
