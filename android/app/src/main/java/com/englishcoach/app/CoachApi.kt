package com.englishcoach.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One explanation in all three languages; the UI picks what the user's setting asks for. */
data class Explanation(val en: String, val roman: String, val urdu: String)

/** Marks a CV interview for the server (it then asks the planned questions in order). */
const val CV_MARK = "CV_INTERVIEW"

data class TurnResult(
    val hasMistake: Boolean,
    val correct: String,
    val natural: String,
    val nextQuestion: String,
    val explainId: String,
    val pending: Boolean,
    val explanations: List<Explanation>,
    val categories: List<String>,
)

data class MacStats(
    val chip: String,
    val diskTotal: Long,
    val diskUsed: Long,
    val memTotal: Long,
    val memUsed: Long,
    val cpu: Double?,
    val models: List<Pair<String, Long>>,
    val loaded: List<String>,
)

/** How clearly one word of the target sentence was said (score 0..100). */
data class WordScore(val word: String, val score: Int, val level: String, val heard: String?, val ipa: String, val tip: Explanation?)
data class Pronunciation(val overall: Int, val said: String, val words: List<WordScore>)

/** Talks to the hybrid coach server on the Mac mini (see server/ in this repo). */
class CoachApi(val baseUrl: () -> String, private val token: () -> String? = { null }) {
    companion object {
        /** The owner's first name from the Mac's config.json (sent in /health). */
        @Volatile var owner: String? = null
    }


    suspend fun health(): Boolean = runCatching { get("/health").optBoolean("ok") }.getOrDefault(false)

    suspend fun stats(): MacStats? = runCatching {
        val o = get("/stats")
        val models = o.optJSONArray("models")
        val loaded = o.optJSONArray("loaded")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        MacStats(
            chip = o.optString("chip"),
            diskTotal = o.optLong("disk_total"), diskUsed = o.optLong("disk_used"),
            memTotal = o.optLong("mem_total"), memUsed = o.optLong("mem_used"),
            cpu = if (o.isNull("cpu")) null else o.optDouble("cpu"),
            models = models?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { it.getString("name") to it.getLong("bytes") } }.orEmpty(),
            loaded = loaded,
        )
    }.getOrNull()

    suspend fun turn(session: String, role: String, text: String): TurnResult {
        val body = JSONObject().put("session", session).put("role", role).put("text", text)
        return parseTurn(post("/turn", body))
    }

    /**
     * Streaming version of [turn]: [onEarly] gets ("mistake", "true"/"false") and ("correct", sentence)
     * as soon as the Mac's model writes them, seconds before the full result returned at the end.
     */
    suspend fun turnStream(session: String, role: String, text: String, useResume: Boolean = false, question: String = "",
                           onEarly: suspend (String, String) -> Unit): TurnResult =
        withContext(Dispatchers.IO) {
            val conn = URL(baseUrl().trimEnd('/') + "/turn/stream").openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 120_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use {
                it.write(JSONObject().put("session", session).put("role", role).put("text", text).put("use_resume", useResume).put("question", question).toString().toByteArray())
            }
            try {
                var result: TurnResult? = null
                conn.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (line.isBlank()) continue
                        val e = JSONObject(line)
                        when (e.optString("event")) {
                            "mistake" -> withContext(Dispatchers.Main) { onEarly("mistake", e.optBoolean("has_mistake").toString()) }
                            "correct" -> withContext(Dispatchers.Main) { onEarly("correct", e.optString("text")) }
                            "done" -> result = parseTurn(e)
                            "error" -> error(e.optString("error"))
                        }
                    }
                }
                result ?: error("stream ended early")
            } finally {
                conn.disconnect()
            }
        }

    private fun parseTurn(r: JSONObject): TurnResult {
        val ex = r.getJSONObject("explanation")
        return TurnResult(
            hasMistake = r.optBoolean("has_mistake"),
            correct = r.optString("correct"),
            natural = r.optString("natural"),
            nextQuestion = r.optString("next_question"),
            explainId = r.optString("explain_id"),
            pending = ex.optString("status") == "pending",
            explanations = parseExplanations(ex),
            categories = r.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
        )
    }

    /** Polls the slow model's explanation; returns empty when it isn't ready or failed. */
    suspend fun explanation(id: String): List<Explanation>? {
        val ex = get("/explain?id=$id")
        return when (ex.optString("status")) {
            "pending" -> null
            else -> parseExplanations(ex)
        }
    }

    private fun parseExplanations(ex: JSONObject): List<Explanation> {
        ex.optJSONArray("items")?.let { items ->
            return (0 until items.length()).map { i ->
                val o = items.getJSONObject(i)
                Explanation(o.optString("en"), o.optString("roman"), o.optString("urdu"))
            }
        }
        return if (ex.has("roman")) listOf(Explanation(ex.optString("en"), ex.optString("roman"), ex.optString("urdu")))
        else emptyList()
    }

    // ---- Mac control (needs the pairing token) ----

    suspend fun paired(): Boolean = runCatching { get("/paired").optBoolean("paired") }.getOrDefault(false)
    suspend fun pairStart(): String? = runCatching { post("/pair/start", JSONObject()).optString("error").ifBlank { null } }.getOrElse { it.message }
    suspend fun pairFinish(code: String): JSONObject = post("/pair/finish", JSONObject().put("code", code))
    suspend fun assistant(text: String, lang: String): JSONObject = post("/assistant", JSONObject().put("text", text).put("lang", lang))
    suspend fun confirm(id: String): JSONObject = post("/assistant/confirm", JSONObject().put("id", id))
    suspend fun jobs(): JSONObject = get("/jobs")
    suspend fun unlockMac(): JSONObject = post("/mac/unlock", JSONObject())
    suspend fun agent(text: String, lang: String, session: String?, allow: List<String> = emptyList(), engine: String = "claude"): JSONObject =
        post("/assistant/agent", JSONObject().put("text", text).put("lang", lang).put("engine", engine).put("allow", org.json.JSONArray(allow)).apply { session?.let { put("session", it) } })
    suspend fun agentJob(id: String): JSONObject = get("/assistant/job?id=$id")
    suspend fun screenInput(o: JSONObject): JSONObject = post("/screen/input", o)
    suspend fun cleanupScan(): JSONObject = post("/cleanup/scan", JSONObject())
    suspend fun cleanupResult(): JSONObject = get("/cleanup/result")
    /** Whisper on the Mac: sends a 16 kHz WAV, returns the text (English, Urdu or mixed). */
    suspend fun transcribe(wav: ByteArray, lang: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(baseUrl().trimEnd('/') + "/transcribe?lang=$lang").openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 60_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "audio/wav")
            conn.outputStream.use { it.write(wav) }
            try { JSONObject(conn.inputStream.bufferedReader().readText()).optString("text").ifBlank { null } } finally { conn.disconnect() }
        }.getOrNull()
    }

    /** Uploads the CV (PDF bytes) to the Mac; returns {"summary": {...}} or {"error": ...}. Paired phone only. */
    suspend fun uploadResume(pdf: ByteArray): JSONObject = withContext(Dispatchers.IO) {
        val conn = URL(baseUrl().trimEnd('/') + "/resume").openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 180_000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/pdf")
        sign(conn, "POST", "/resume", pdf)
        conn.outputStream.use { it.write(pdf) }
        try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            JSONObject(stream.bufferedReader().readText())
        } finally { conn.disconnect() }
    }
    suspend fun resume(): JSONObject = get("/resume")
    suspend fun deleteResume(): JSONObject = post("/resume/delete", JSONObject())

    /** Scores how clearly [target] was said in [wav] (Whisper word confidence + alignment on the Mac). */
    suspend fun pronounce(wav: ByteArray, target: String): Pronunciation? = withContext(Dispatchers.IO) {
        runCatching {
            val q = java.net.URLEncoder.encode(target, "UTF-8")
            val conn = URL(baseUrl().trimEnd('/') + "/pronounce?target=$q").openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 60_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "audio/wav")
            conn.outputStream.use { it.write(wav) }
            val o = try { JSONObject(conn.inputStream.bufferedReader().readText()) } finally { conn.disconnect() }
            val arr = o.getJSONArray("words")
            Pronunciation(o.optInt("overall"), o.optString("said"), (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                val tip = it.optJSONObject("tip")?.let { t -> Explanation(t.optString("en"), t.optString("roman"), "") }
                WordScore(it.optString("word"), it.optInt("score"), it.optString("level"), it.optString("heard").ifBlank { null },
                    it.optString("ipa"), tip)
            })
        }.getOrNull()
    }

    suspend fun cleanupEmptyTrash(): JSONObject = post("/cleanup/empty_trash", JSONObject())
    suspend fun cleanupDecide(path: String, decision: String): JSONObject = post("/cleanup/decide", JSONObject().put("path", path).put("decision", decision))
    suspend fun cleanupTrash(paths: List<String>): JSONObject = post("/cleanup/trash", JSONObject().put("paths", org.json.JSONArray(paths)))

    /** Latest screenshot of the Mac as JPEG bytes, or null when not allowed / no permission. */
    /** Studio (your YouTube dashboard) through the Mac server; [body] null = GET. */
    suspend fun studio(name: String, body: JSONObject? = null): JSONObject = request("/studio/$name", body)

    suspend fun displayCount(): Int = runCatching { get("/displays").optJSONArray("displays")?.length() ?: 1 }.getOrDefault(1)

    /** Live Mac screen: one long request; calls [onFrame] with each JPEG until the stream ends or fails. */
    suspend fun screenStream(display: Int, width: Int, quality: Float, lowLag: Boolean = false, onFrame: suspend (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        val path = "/screen.stream?d=$display&w=$width&q=$quality" + if (lowLag) "&lowlag=1" else ""
        val conn = URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 10_000
        sign(conn, "GET", path, ByteArray(0))
        try {
            if (conn.responseCode != 200) return@withContext
            val inp = java.io.DataInputStream(java.io.BufferedInputStream(conn.inputStream, 1 shl 18))
            while (true) {
                val n = inp.readInt()
                if (n <= 0 || n > 20_000_000) break
                val b = ByteArray(n); inp.readFully(b)
                onFrame(b)
            }
        } catch (_: Exception) {
        } finally { conn.disconnect() }
    }

    suspend fun macLocked(): Boolean = get("/mac/locked").optBoolean("locked")
    suspend fun awakeInfo(): JSONObject = get("/mac/awake")
    suspend fun setAwake(on: Boolean): JSONObject = post("/mac/awake", JSONObject().put("on", on))
    suspend fun getBackup(): JSONObject = get("/practice/backup")
    /** Sends the phone's progress.json to the Mac (raw body, signed). */
    suspend fun backup(json: String): JSONObject = withContext(Dispatchers.IO) {
        val bytes = json.toByteArray()
        val path = "/practice/backup"
        val conn = URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000; conn.readTimeout = 30_000
        sign(conn, "POST", path, bytes)
        conn.requestMethod = "POST"; conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        try { conn.outputStream.use { it.write(bytes) }
            JSONObject((if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText())
        } finally { conn.disconnect() }
    }
    suspend fun cvList(): JSONObject = get("/cv/list")
    suspend fun cvGet(id: String): JSONObject = get("/cv/get?id=$id")
    suspend fun cvCreate(link: String, text: String, note: String): JSONObject =
        post("/cv/create", JSONObject().put("link", link).put("text", text).put("note", note))
    suspend fun cvSave(id: String, text: String): JSONObject = post("/cv/save", JSONObject().put("id", id).put("text", text))
    suspend fun cvAiEdit(id: String, note: String): JSONObject = post("/cv/ai_edit", JSONObject().put("id", id).put("note", note))
    suspend fun cvRestore(id: String, v: String): JSONObject = post("/cv/restore", JSONObject().put("id", id).put("version", v))
    /** Any signed GET as raw bytes (e.g. the CV PDF). */
    suspend fun bytes(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val conn = URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000; conn.readTimeout = 60_000
        sign(conn, "GET", path, ByteArray(0))
        try { if (conn.responseCode == 200) conn.inputStream.readBytes() else null } finally { conn.disconnect() }
    }
    suspend fun activity(kind: String): JSONObject = get("/activity?kind=$kind&limit=400")
    suspend fun interviewPlan(): JSONObject = get("/interview/plan")
    suspend fun interviewFeedbackStart(session: String): JSONObject = post("/interview/feedback", JSONObject().put("session", session))
    suspend fun interviewFeedback(session: String): JSONObject = get("/interview/feedback?session=$session")
    suspend fun analytics(force: Boolean): JSONObject = get("/analytics?force=${if (force) 1 else 0}")
    suspend fun creditGuard(): JSONObject = get("/credits")
    suspend fun creditSettings(limit: Int?, renewDay: Int?): JSONObject =
        post("/credits/settings", JSONObject().apply { limit?.let { put("daily_limit", it) }; renewDay?.let { put("renew_day", it) } })
    suspend fun creditExtra(amount: Int): JSONObject = post("/credits/extra", JSONObject().put("amount", amount))
    suspend fun autopilot(): JSONObject = get("/autopilot")
    suspend fun autopilotSave(on: Boolean, time: String, credits: Int): JSONObject =
        post("/autopilot/settings", JSONObject().put("on", on).put("time", time).put("credits", credits))
    suspend fun autopilotRun(): JSONObject = post("/autopilot/run", JSONObject())
    suspend fun naatList(): JSONObject = get("/naat")
    suspend fun naatGet(id: String): JSONObject = get("/naat/get?id=$id")
    suspend fun naatProgress(id: String): JSONObject = get("/naat/progress?id=$id")
    suspend fun naatCreate(idea: String, style: String, voice: String, kids: Boolean): JSONObject =
        post("/naat/create", JSONObject().put("idea", idea).put("style", style).put("voice", voice).put("kids", kids))
    suspend fun naatRevise(id: String, note: String): JSONObject = post("/naat/revise", JSONObject().put("id", id).put("note", note))
    suspend fun naatMake(id: String, credits: Int, shorts: Boolean, override: Boolean = false): JSONObject =
        post("/naat/make", JSONObject().put("id", id).put("credits", credits).put("shorts", shorts).put("override", override))
    /** Sends the Suno song for a naat project. */
    suspend fun naatUpload(id: String, name: String, bytes: ByteArray): JSONObject = withContext(Dispatchers.IO) {
        val path = "/naat/upload?id=$id&name=" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        val conn = URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000; conn.readTimeout = 300_000
        sign(conn, "POST", path, bytes)
        conn.requestMethod = "POST"; conn.doOutput = true; conn.setFixedLengthStreamingMode(bytes.size)
        try { conn.outputStream.use { it.write(bytes) }
            JSONObject((if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText())
        } finally { conn.disconnect() }
    }
    suspend fun routines(): JSONObject = get("/routines")
    suspend fun saveRoutines(list: org.json.JSONArray): JSONObject = post("/routines/save", JSONObject().put("routines", list))
    suspend fun runRoutine(id: String): JSONObject = post("/routines/run", JSONObject().put("id", id))
    suspend fun macClip(): String = get("/clip").optString("text")
    suspend fun setMacClip(text: String): JSONObject = post("/clip/set", JSONObject().put("text", text))
    suspend fun fileList(place: String, sub: String): JSONObject =
        get("/files/list?place=${java.net.URLEncoder.encode(place, "UTF-8")}&sub=${java.net.URLEncoder.encode(sub, "UTF-8")}")
    suspend fun revealFile(place: String, sub: String, name: String): JSONObject =
        post("/files/reveal", JSONObject().put("place", place).put("sub", sub).put("name", name))

    /** Sends one file to the Mac (lands in Downloads/From Phone). */
    suspend fun upload(name: String, bytes: ByteArray): JSONObject = withContext(Dispatchers.IO) {
        val path = "/files/upload?name=" + java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        val conn = URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000; conn.readTimeout = 300_000
        sign(conn, "POST", path, bytes)
        conn.requestMethod = "POST"; conn.doOutput = true
        conn.setFixedLengthStreamingMode(bytes.size)
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        try {
            conn.outputStream.use { it.write(bytes) }
            JSONObject((if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().readText())
        } finally { conn.disconnect() }
    }

    /** Downloads a Mac file and hands its stream to [sink]. */
    suspend fun download(place: String, sub: String, name: String, sink: (java.io.InputStream, Long) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val path = "/files/get?place=${java.net.URLEncoder.encode(place, "UTF-8")}&sub=${java.net.URLEncoder.encode(sub, "UTF-8")}&name=${java.net.URLEncoder.encode(name, "UTF-8")}"
        val conn = URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000; conn.readTimeout = 300_000
        sign(conn, "GET", path, ByteArray(0))
        try { if (conn.responseCode == 200) { sink(conn.inputStream, conn.contentLengthLong); true } else false } finally { conn.disconnect() }
    }

    suspend fun brief(): JSONObject = get("/brief")
    suspend fun briefSettings(time: String, on: Boolean): JSONObject = post("/brief/settings", JSONObject().put("time", time).put("on", on))
    /** Live Mac screen as H.264: calls [onPacket] with (kind, payload) until the stream ends. 0=config, 1=key, 2=frame. */
    suspend fun screenH264(display: Int, width: Int, kbps: Int, onPacket: suspend (Int, ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        val path = "/screen.h264?d=$display&w=$width&kbps=$kbps"
        val conn = URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000; conn.readTimeout = 10_000
        sign(conn, "GET", path, ByteArray(0))
        try {
            if (conn.responseCode != 200) return@withContext
            val inp = java.io.DataInputStream(java.io.BufferedInputStream(conn.inputStream, 1 shl 16))
            while (true) {
                val n = inp.readInt()
                if (n <= 0 || n > 8_000_000) break
                val kind = inp.readUnsignedByte()
                val b = ByteArray(n - 1); inp.readFully(b)
                onPacket(kind, b)
            }
        } catch (_: Exception) {
        } finally { conn.disconnect() }
    }

    suspend fun displayMode(): JSONObject = get("/mac/display_mode")
    suspend fun setDisplayMode(mode: String, w: Int, h: Int): JSONObject =
        post("/mac/display_mode", JSONObject().put("mode", mode).put("w", w).put("h", h))

    /** The Mac's sound as raw PCM 16-bit: calls [onChunk] until the stream ends. */
    suspend fun macAudio(rate: Int, channels: Int, onChunk: (ByteArray, Int) -> Unit) = withContext(Dispatchers.IO) {
        val path = "/screen.audio?rate=$rate&ch=$channels"
        val conn = URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000; conn.readTimeout = 15_000
        sign(conn, "GET", path, ByteArray(0))
        try {
            if (conn.responseCode != 200) return@withContext
            val inp = conn.inputStream; val buf = ByteArray(8192)
            while (true) { val n = inp.read(buf); if (n < 0) break; onChunk(buf, n) }
        } catch (_: Exception) {
        } finally { conn.disconnect() }
    }

    suspend fun lockMac(): JSONObject = post("/mac/lock", JSONObject())

    suspend fun screen(display: Int = 0): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val path = "/screen.jpg?d=$display"
            val conn = URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 15_000
            sign(conn, "GET", path, ByteArray(0))
            try { if (conn.responseCode == 200) conn.inputStream.readBytes() else null } finally { conn.disconnect() }
        }.getOrNull()
    }

    /**
     * Proves this is the paired phone without sending the secret: the Mac checks
     * HMAC-SHA256(secret, "METHOD\nPATH\nTS\nsha256(body)") and refuses old or repeated signatures.
     */
    /** Signs a request the caller builds itself (e.g. the offline model download, which needs a Range header). */
    fun signPublic(conn: HttpURLConnection, method: String, path: String) = sign(conn, method, path, ByteArray(0))
    suspend fun offlineInfo(): JSONObject = get("/offline/info")

    private fun sign(conn: HttpURLConnection, method: String, path: String, body: ByteArray) {
        val secret = token() ?: return
        val ts = (System.currentTimeMillis() / 1000).toString()
        val bodyHash = java.security.MessageDigest.getInstance("SHA-256").digest(body).joinToString("") { "%02x".format(it) }
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val nonce = java.util.UUID.randomUUID().toString()
        val sig = mac.doFinal("$method\n$path\n$ts\n$nonce\n$bodyHash".toByteArray()).joinToString("") { "%02x".format(it) }
        conn.setRequestProperty("X-Coach-Ts", ts)
        conn.setRequestProperty("X-Coach-Nonce", nonce)
        conn.setRequestProperty("X-Coach-Sig", sig)
    }

    /** /health at [url] within [timeoutMs]; returns the Mac's Wi-Fi address it reports, or null if unreachable. */
    suspend fun probe(url: String, timeoutMs: Int): String? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url.trimEnd('/') + "/health").openConnection() as HttpURLConnection
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs * 2
            try { JSONObject(c.inputStream.bufferedReader().readText()).let { if (it.optBoolean("ok")) { if (it.has("owner")) owner = it.optString("owner"); it.optString("lan", "") } else null } }
            finally { c.disconnect() }
        }.getOrNull()
    }

    /** New alerts after [after]; the Mac holds the request up to 25 s until one arrives. */
    suspend fun events(after: Int, wait: Int = 25): JSONObject = get("/events?after=$after&wait=$wait")
    suspend fun reviewList(): JSONObject = get("/review/list")
    suspend fun reviewUploaded(force: Boolean): JSONObject = get("/review/uploaded?force=${if (force) 1 else 0}")
    suspend fun reviewReschedule(id: String, days: Int?, time: String?): JSONObject =
        post("/review/reschedule", JSONObject().put("id", id).apply { days?.let { put("days", it) }; time?.let { put("time", it) } })
    suspend fun reviewSaveSchedule(perDay: Int, slots: List<String>, auto: Boolean): JSONObject =
        post("/review/schedule", JSONObject().put("per_day", perDay).put("slots", org.json.JSONArray(slots)).put("auto", auto))
    suspend fun reviewBestTime(): JSONObject = post("/review/best_time", JSONObject())
    suspend fun reviewToken(): String = get("/review/token").optString("token")
    suspend fun reviewChoose(id: String, choice: String, days: Int? = null, time: String? = null): JSONObject =
        post("/review/choose", JSONObject().put("id", id).put("choice", choice).apply { days?.let { put("days", it) }; time?.let { put("time", it) } })
    suspend fun reviewFix(id: String, note: String): JSONObject = post("/review/fix", JSONObject().put("id", id).put("note", note))
    suspend fun clearAlerts(): JSONObject = post("/alerts/clear", JSONObject())
    /** Signed POST to an allowed route, used by notification buttons. */
    suspend fun send(path: String, body: JSONObject): JSONObject = post(path, body)

    suspend fun rulesList(): JSONObject = get("/rules/list")
    suspend fun macGet(path: String): JSONObject = get(path)

    private suspend fun get(path: String) = request(path, null)
    private suspend fun post(path: String, body: JSONObject) = request(path, body)

    private suspend fun request(path: String, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        val conn = URL(baseUrl().trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 120_000
        val bytes = body?.toString()?.toByteArray() ?: ByteArray(0)
        sign(conn, if (body != null) "POST" else "GET", path, bytes)
        if (body != null) {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(bytes) }
        }
        try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            JSONObject(stream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }
}
