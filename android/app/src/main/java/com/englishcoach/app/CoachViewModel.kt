package com.englishcoach.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlinx.coroutines.sync.withLock
import androidx.compose.ui.graphics.asImageBitmap
import java.util.UUID

/** One line in the Mac Assistant chat. */
/** Something on the Mac that could be removed to free space. */
data class CleanItem(val kind: String, val path: String, val name: String, val bytes: Long, val lastUsed: Long?, val note: String,
                     val verdict: String = "ask", val reason: String = "", val growth: Long = 0) {
    val needsDecision get() = verdict in setOf("safe", "suggest", "ask")
}

/** What the Mac understood from the CV. */
data class ResumeSummary(val title: String, val years: String, val skills: List<String>, val projects: List<Pair<String, String>>, val summary: String)

data class ChatItem(val fromMe: Boolean, val text: String, val confirmId: String? = null, val jobId: String? = null,
                    val working: Boolean = false, val steps: List<String> = emptyList(), val denied: List<String> = emptyList(),
                    val ask: String = "", val id: Long = System.nanoTime())

data class Role(
    val title: String,
    val persona: String,
    val prompt: String,
    val opening: String,
    val color: Color,
    val tint: Color,
)

val ROLES = listOf(
    Role("Interview", "Sarah · Android Lead", "Sarah, a senior Android lead interviewing a developer for a Kotlin/Jetpack Compose job",
        "Hi, I'm Sarah. Thanks for coming in. Could you tell me a little about yourself?", Color(0xFF7B5CFF), Color(0xFFE6DEFF)),
    Role("HR", "Ayesha · HR Manager", "Ayesha, an HR manager discussing salary, notice period and company culture with a candidate",
        "Hello, I'm Ayesha from HR. Before we begin, what are you looking for in your next job?", Color(0xFFFF5FA2), Color(0xFFFFE1EC)),
    Role("Client", "Mark · US Client", "Mark, a busy American business owner who might buy a mobile app from the learner's company",
        "Hey, Mark here. I've only got a few minutes. Why should I pick your team to build my app?", Color(0xFF2DB8F0), Color(0xFFD9F4FF)),
    Role("Teacher", "Ms. Khan · Teacher", "Ms. Khan, a kind teacher having an everyday conversation to build the learner's confidence",
        "Good morning! Let's just chat today. What did you do last weekend?", Color(0xFF8FD11F), Color(0xFFF0FBD3)),
    Role("Visa", "Officer Brown · Embassy", "Officer Brown, a strict but fair visa officer at the US embassy interviewing an applicant",
        "Good morning. Passport, please. What is the purpose of your trip?", Color(0xFF3D5AFE), Color(0xFFE0E6FF)),
    Role("Doctor", "Dr. Lee · Clinic", "Dr. Lee, a friendly doctor asking a patient about their symptoms",
        "Hello, please have a seat. What seems to be the problem today?", Color(0xFF00BFA5), Color(0xFFD7F7F1)),
    Role("Shop", "Emma · Store clerk", "Emma, a helpful clerk in a clothing store abroad",
        "Hi there! Are you looking for anything in particular today?", Color(0xFFFF9100), Color(0xFFFFEBD6)),
    Role("Etsy buyer", "Linda · Etsy buyer", "Linda, an American Etsy customer who bought an editable Canva funeral program template from the learner's store and asks for help editing it, a small change and a fast reply; polite but emotional because she lost her mother",
        "Hi, I bought your funeral program template yesterday. I'm having trouble changing the photo in Canva. Can you help me?", Color(0xFFF1641E), Color(0xFFFFE7DA)),
    Role("Freelance", "Tom · Upwork client", "Tom, a client on a freelance video call who wants a mobile app; asks about price, timeline, past work and how updates will be shared",
        "Hi, thanks for jumping on the call. I read your proposal. Can you walk me through how you'd build this and how long it'd take?", Color(0xFF14A800), Color(0xFFDDF7D6)),
    Role("YouTube", "Priya · Brand manager", "Priya, a brand manager from a kids' toy company who wants a sponsored video on the learner's kids YouTube channel; negotiates price and rules",
        "Hello! We love your Pip & Friends videos. We'd like to talk about a sponsored video. Do you have a moment?", Color(0xFFFF0033), Color(0xFFFFE0E6)),
    Role("Friend", "Alex · New friend", "Alex, a relaxed new friend making small talk over coffee",
        "Hey! So how's your week been going?", Color(0xFFE040FB), Color(0xFFF8E1FF)),
)

/** A role described by the learner in their own words; the AI plays whatever they asked for. */
fun customRole(description: String) = Role(
    "Custom", description.take(32), description,
    "Hi! I'm ready when you are. Let's begin — tell me a little about yourself.", Color(0xFF546E7A), Color(0xFFE3EAEE),
)

enum class Screen { Home, Practice, Progress, Review, Report, Mac, Settings, Assistant, Remote, Cleaner, Studio, Alerts, Videos, Transfer, Routines, Naat, Weekly, Activity, Cv, Rules, Stores, Money, Ideas, AppAssistant, Staff }
val TAB_SCREENS = setOf(Screen.Home, Screen.Progress, Screen.Money, Screen.Stores, Screen.Mac, Screen.Settings, Screen.AppAssistant, Screen.Ideas)
val LEVELS = listOf("Beginner", "Intermediate", "Advanced")
enum class Phase { Ask, Listening, Thinking, Sheet, Praise }

class CoachViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("coach", 0)
    val voice = Voice(app)
    private val api = CoachApi({ activeUrl }, { macToken })

    // ---- Store Studio (Etsy) ----
    var etsyStore by mutableStateOf<String?>(null)
    var etsyRun by mutableStateOf<String?>(null)
    /** Mac call. GET answers are kept on the phone, so screens still show the last data when the Mac is away. */
    suspend fun mac(path: String, body: org.json.JSONObject? = null): org.json.JSONObject {
        val cache = getApplication<Application>().getSharedPreferences("mac_cache", 0)
        return runCatching { if (body == null) api.macGet(path) else api.send(path, body) }.map { r ->
            if (body == null && !r.has("error")) r.toString().takeIf { it.length < 300_000 }?.let { cache.edit().putString(path, it).apply() }
            r
        }.getOrElse { e ->
            if (body == null) cache.getString(path, null)?.let { return org.json.JSONObject(it).put("offline", true) }
            org.json.JSONObject().put("error", e.message ?: "Can't reach the Mac")
        }
    }
    suspend fun macBytes(path: String): ByteArray? = runCatching { api.bytes(path) }.getOrNull()

    /** Saves a design to Pictures/StoreStudio, then opens Canva (or the share sheet) with it. */
    fun saveDesign(bytes: ByteArray, name: String, canva: Boolean) {
        val app = getApplication<Application>()
        val v = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StoreStudio")
        }
        val uri = app.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return
        app.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("image/png")
            .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val toCanva = android.content.Intent(send).setPackage("com.canva.editor").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (canva && runCatching { app.startActivity(toCanva) }.isSuccess) return
        app.startActivity(android.content.Intent.createChooser(send, "Open design").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Saves several design layers to Pictures/StoreStudio/<folder> and sends them all to Canva (or the share sheet). */
    fun saveLayers(files: List<Pair<String, ByteArray>>, folder: String, canva: Boolean) {
        val app = getApplication<Application>()
        val uris = ArrayList<android.net.Uri>()
        for ((name, bytes) in files) {
            val v = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StoreStudio/$folder")
            }
            app.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)?.let { u ->
                app.contentResolver.openOutputStream(u)?.use { it.write(bytes) }; uris.add(u)
            }
        }
        if (uris.isEmpty()) return
        val send = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).setType("image/png")
            .putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val toCanva = android.content.Intent(send).setPackage("com.canva.editor").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (canva && runCatching { app.startActivity(toCanva) }.isSuccess) return
        app.startActivity(android.content.Intent.createChooser(send, "Send layers").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Saves any file (PDF, CSV, JPEG) to Downloads/StoreStudio and opens the share sheet. */
    fun shareFile(bytes: ByteArray, name: String, mime: String) {
        val app = getApplication<Application>()
        val v = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/StoreStudio")
        }
        val uri = app.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return
        app.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        app.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType(mime)
            .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share $name")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Owner's video rules: list (body null) or a signed POST to /rules/... */
    suspend fun rules(path: String, body: org.json.JSONObject? = null): org.json.JSONObject =
        runCatching { if (body == null) api.rulesList() else api.send(path, body) }.getOrElse { org.json.JSONObject().put("error", it.message ?: "Can't reach the Mac") }
    val studio = StudioModel(api, viewModelScope)

    var roman by mutableStateOf(prefs.getBoolean("roman", false)); private set
    var serverUrl by mutableStateOf(prefs.getString("server", DEFAULT_SERVER)!!); private set
    /** The Mac's home Wi-Fi address, learned from the server; used directly when the phone is at home. */
    private var lanUrl: String? = prefs.getString("lanUrl", null)
    init { prefs.edit().remove("lanOnly").apply() }
    /** Where requests go right now: Wi-Fi when reachable, otherwise Tailscale ([serverUrl]). */
    var activeUrl by mutableStateOf(serverUrl); private set
    var onWifi by mutableStateOf(false); private set

    /**
     * Finds the Mac on the home network by its Bonjour announcement ("_englishcoach._tcp"), so the app
     * learns the Mac's current address even when Tailscale is off or the Mac changed Wi-Fi/cable.
     */
    private fun discoverMac(app: Application) {
        val nsd = app.getSystemService(android.content.Context.NSD_SERVICE) as android.net.nsd.NsdManager
        nsd.discoverServices("_englishcoach._tcp", android.net.nsd.NsdManager.PROTOCOL_DNS_SD, object : android.net.nsd.NsdManager.DiscoveryListener {
            override fun onServiceFound(info: android.net.nsd.NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : android.net.nsd.NsdManager.ResolveListener {
                    override fun onServiceResolved(r: android.net.nsd.NsdServiceInfo) {
                        val ip = r.host?.hostAddress ?: return
                        if (!isPrivateLan(ip)) return
                        val url = "http://$ip:${r.port}"
                        if (url != lanUrl) {
                            lanUrl = url
                            prefs.edit().putString("lanUrl", url).apply()
                        }
                        checkServer()
                    }
                    override fun onResolveFailed(info: android.net.nsd.NsdServiceInfo, code: Int) {}
                })
            }
            override fun onServiceLost(info: android.net.nsd.NsdServiceInfo) {}
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) {}
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
        })
    }

    /** Picks the fastest way to the Mac: home Wi-Fi first (no VPN needed), then Tailscale. */
    suspend fun pickRoute(): Boolean {
        for (lan in listOfNotNull(lanUrl, HOME_URL).distinct()) {
            if (api.probe(lan, 2_000) != null) {
                activeUrl = lan; onWifi = true; noteOwner()
                if (lan != lanUrl) { lanUrl = lan; prefs.edit().putString("lanUrl", lan).apply() }
                return true
            }
        }
        val lan = api.probe(serverUrl, 4000) ?: run { onWifi = false; activeUrl = serverUrl; return false }
        activeUrl = serverUrl
        onWifi = false
        noteOwner()
        if (lan.isNotBlank()) {
            val port = runCatching { java.net.URL(serverUrl).port }.getOrDefault(-1).takeIf { it > 0 } ?: 8787
            val url = "http://$lan:$port"
            if (url != lanUrl) { lanUrl = url; prefs.edit().putString("lanUrl", url).apply() }
        }
        return true
    }
    /** The owner's first name, from the Mac's config.json (empty until the Mac is reached once). */
    var ownerName by mutableStateOf(prefs.getString("ownerName", "")!!); private set
    private fun noteOwner() {
        val o = CoachApi.owner ?: return
        if (o != ownerName) { ownerName = o; prefs.edit().putString("ownerName", o).apply() }
    }
    var macOnline by mutableStateOf<Boolean?>(null); private set
    var macStats by mutableStateOf<MacStats?>(null); private set

    // ---- Mac control ----
    /** Secret issued by the Mac at pairing; kept only in this app's private storage. */
    private var macToken: String? = prefs.getString("macToken", null)
    var paired by mutableStateOf(macToken != null); private set
    var pairStep by mutableStateOf(0); private set   // 0 idle, 1 waiting for code
    var pairError by mutableStateOf<String?>(null); private set
    var chat by mutableStateOf<List<ChatItem>>(emptyList()); private set
    var screenImage by mutableStateOf<ByteArray?>(null); private set
    var screenBitmap by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null); private set
    var screenFps by mutableStateOf(0); private set
    var screenError by mutableStateOf<String?>(null); private set
    var displayCount by mutableStateOf(1); private set
    var display by mutableStateOf(0)

    fun startPairing(address: String? = null) {
        pairError = null
        address?.let { normalizeServer(it) }?.let { if (it != serverUrl) { serverUrl = it; prefs.edit().putString("server", it).apply() } }
        if (serverUrl.isBlank() && lanUrl == null) { pairError = "Type your Mac's address first (its Tailscale IP, like 100.x.y.z)."; return }
        viewModelScope.launch {
            activeUrl = serverUrl.ifBlank { lanUrl!! }  // the Mac only shows the code; pairing works over Tailscale or home Wi-Fi
            val err = api.pairStart()
            if (err == null) pairStep = 1 else pairError = err
        }
    }

    fun finishPairing(code: String) {
        viewModelScope.launch {
            val r = runCatching { api.pairFinish(code.trim()) }.getOrNull()
            val t = r?.optString("token").orEmpty()
            if (t.isNotBlank()) {
                macToken = t
                prefs.edit().putString("macToken", t).apply()
                AlertService.start(getApplication())
                paired = true
                pairStep = 0
            } else pairError = r?.optString("error") ?: strings.serverError
        }
    }

    fun forgetPairing() {
        macToken = null
        prefs.edit().remove("macToken").apply()
        AlertService.stop(getApplication())
        paired = false
    }

    /** Claude session on the Mac, so the agent remembers the conversation. */
    private var agentSession: String? = null

    var unlockMsg by mutableStateOf<String?>(null)
    var unlockSetup by mutableStateOf(false)
    fun unlockMac() {
        unlockMsg = "Waking the Mac…"
        viewModelScope.launch {
            val r = runCatching { api.unlockMac() }.getOrNull()
            unlockSetup = r?.optBoolean("setup") == true
            if (r?.optBoolean("ok") == true) macLocked = false
            unlockMsg = r?.optString("result")?.ifBlank { null } ?: r?.optString("error") ?: strings.serverError
        }
    }

    data class Alert(val id: Int, val time: Long, val kind: String, val title: String, val body: String, val screen: String,
                     val action: org.json.JSONObject?)
    var alerts by mutableStateOf(listOf<Alert>()); private set
    var alertsSeen by mutableStateOf(prefs.getInt("alertsSeen", 0)); private set
    val unreadAlerts get() = alerts.count { it.id > alertsSeen }

    fun loadAlerts() {
        viewModelScope.launch {
            val r = runCatching { api.events(0, 0) }.getOrNull() ?: return@launch
            val a = r.optJSONArray("events") ?: return@launch
            alerts = List(a.length()) { a.getJSONObject(it) }.filter { it.optString("kind") != "cleared" }.map {
                Alert(it.optInt("id"), (it.optDouble("t") * 1000).toLong(), it.optString("kind"), it.optString("title"),
                    it.optString("body"), it.optString("screen"), it.optJSONObject("action"))
            }.reversed()
        }
    }

    fun markAlertsSeen() {
        alertsSeen = alerts.maxOfOrNull { it.id } ?: alertsSeen
        prefs.edit().putInt("alertsSeen", alertsSeen).apply()
    }

    fun clearAlerts() {
        viewModelScope.launch { runCatching { api.clearAlerts() }; markAlertsSeen(); alerts = emptyList() }
    }

    var alertMsg by mutableStateOf<String?>(null)
    fun alertAction(a: Alert) {
        val act = a.action ?: return
        viewModelScope.launch {
            val r = runCatching { api.send(act.optString("path"), act.optJSONObject("body") ?: org.json.JSONObject()) }.getOrNull()
            alertMsg = r?.optString("error")?.ifBlank { null } ?: if (r != null) "Done: Claude is continuing" else strings.serverError
        }
    }

    // ---- videos waiting for the owner's OK ----
    data class WaitingVideo(val id: String, val title: String, val description: String, val tags: List<String>, val posted: String,
                            val status: String, val short: Boolean, val hasThumb: Boolean, val choice: String, val error: String,
                            val publishAt: String = "", val problem: String = "", val suggest: org.json.JSONObject? = null,
                            val netRetry: Boolean = false)
    var longTime by mutableStateOf("19:00"); private set
    var shortTime by mutableStateOf("17:00"); private set
    var waiting by mutableStateOf(listOf<WaitingVideo>()); private set
    var openVideo by mutableStateOf<String?>(null)
    var reviewMsg by mutableStateOf<String?>(null)
    private var mediaToken by mutableStateOf<String?>(null)
    /** A fresh play key each time a video is opened (and on "try again"). */
    fun refreshMediaToken() { viewModelScope.launch { runCatching { api.reviewToken() }.getOrNull()?.let { mediaToken = it } } }

    data class UploadedVideo(val id: String, val title: String, val link: String, val short: Boolean, val uploaded: String,
                             val views: Int, val likes: Int, val comments: Int, val privacy: String, val publishAt: String,
                             val thumb: String, val tiktok: Boolean, val canReschedule: Boolean = false)
    var slots by mutableStateOf(listOf("19:00")); private set
    var autoSchedule by mutableStateOf(true); private set
    var nextSlot by mutableStateOf<Pair<Int, String>?>(null); private set
    var bestSlots by mutableStateOf(listOf<String>()); private set
    var bestNote by mutableStateOf(""); private set
    var bestChecking by mutableStateOf(false); private set

    fun saveSchedule(newSlots: List<String>, auto: Boolean) {
        viewModelScope.launch {
            val r = runCatching { api.reviewSaveSchedule(newSlots.size, newSlots, auto) }.getOrNull()
            reviewMsg = r?.optString("error")?.ifBlank { null } ?: if (r == null) strings.serverError
                else "Saved: ${newSlots.size} video${if (newSlots.size > 1) "s" else ""} a day"
            loadWaiting()
        }
    }

    fun findBestTime() {
        if (bestChecking) return
        viewModelScope.launch {
            bestChecking = true
            val before = bestNote
            runCatching { api.reviewBestTime() }
            reviewMsg = "Claude is reading YouTube Studio's audience chart on the Mac. This takes 1–3 minutes."
            repeat(30) { delay(8_000); loadWaiting(); delay(500); if (bestNote != before && bestNote.isNotBlank()) { bestChecking = false; return@launch } }
            bestChecking = false
        }
    }

    fun reschedule(u: UploadedVideo, days: Int?, time: String?) {
        viewModelScope.launch {
            val r = runCatching { api.reviewReschedule(u.id, days, time) }.getOrNull()
            reviewMsg = r?.optString("error")?.ifBlank { null } ?: if (r == null) strings.serverError else when {
                days == null -> "Moved to the next free slot"
                days < 0 -> "It's public now"
                else -> "Moved: goes public ${dayName(days)} at $time"
            }
            loadUploaded(true)
        }
    }
    var uploadedVideos by mutableStateOf(listOf<UploadedVideo>()); private set
    var reviewRefreshing by mutableStateOf(false); private set

    fun loadUploaded(force: Boolean = false) {
        viewModelScope.launch {
            val a = runCatching { api.reviewUploaded(force).optJSONArray("items") }.getOrNull() ?: return@launch
            uploadedVideos = List(a.length()) { a.getJSONObject(it) }.map { o ->
                UploadedVideo(o.optString("id"), o.optString("title"), o.optString("link"), o.optBoolean("short"), o.optString("uploaded"),
                    o.optInt("views"), o.optInt("likes"), o.optInt("comments"), o.optString("privacy"), o.optString("publish_at"),
                    o.optString("thumb"), o.optBoolean("tiktok"), o.optBoolean("can_reschedule"))
            }
        }
    }

    /** Pull-to-refresh / Refresh button: waiting list and fresh YouTube numbers. */
    fun refreshVideos() {
        viewModelScope.launch {
            reviewRefreshing = true
            loadWaiting(); runCatching { api.reviewUploaded(true) }; loadUploaded(false)
            delay(600); reviewRefreshing = false
        }
    }

    fun loadWaiting() {
        viewModelScope.launch {
            val r = runCatching { api.reviewList() }.getOrNull() ?: return@launch
            val a = r.optJSONArray("items") ?: return@launch
            waiting = List(a.length()) { a.getJSONObject(it) }.map { o ->
                val t = o.optJSONArray("tags")
                WaitingVideo(o.optString("id"), o.optString("title"), o.optString("description"),
                    t?.let { x -> List(x.length()) { x.optString(it) } } ?: emptyList(), o.optString("posted"), o.optString("status"),
                    o.optBoolean("short"), o.optBoolean("has_thumb"), o.optString("choice"), o.optString("error"), o.optString("publish_at"), o.optString("problem"), o.optJSONObject("suggest"), o.optBoolean("net_retry"))
            }
            r.optJSONObject("times")?.let { t ->
                longTime = t.optString("long", longTime); shortTime = t.optString("short", shortTime)
                t.optJSONArray("slots")?.let { a -> slots = List(a.length()) { a.optString(it) } }
                autoSchedule = t.optBoolean("auto", true)
            }
            nextSlot = r.optJSONObject("next_slot")?.let { it.optInt("day") to it.optString("time") }
            r.optJSONObject("best")?.let { b ->
                bestSlots = b.optJSONArray("slots")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList()
                bestNote = b.optString("note")
            }
            mediaToken = runCatching { api.reviewToken() }.getOrNull()
        }
    }

    /** Short-lived link the video player and thumbnails use (the Mac checks the token). */
    fun mediaUrl(id: String, what: String) = mediaToken?.let { "${activeUrl.trimEnd('/')}/review/file?t=$it&id=$id&what=$what" }

    fun chooseVideo(v: WaitingVideo, choice: String, days: Int? = null, time: String? = null) {
        viewModelScope.launch {
            val r = runCatching { api.reviewChoose(v.id, choice, days, time) }.getOrNull()
            reviewMsg = r?.optString("error")?.ifBlank { null } ?: if (r == null) strings.serverError else when (choice) {
                "✅" -> if (days == null && autoSchedule) "Auto-scheduled: goes public ${nextSlot?.let { "${dayName(it.first)} at ${it.second}" } ?: "at the next free slot"}."
                       else if (days != null && days >= 0) "Scheduled: it uploads now and goes public ${dayName(days)} at $time. You'll get a notification."
                       else "Uploading \"${v.title.take(40)}\" to YouTube. You'll get a notification with the link."
                "📱" -> "Making Shorts. You'll get a notification when they're ready to review."
                else -> "Skipped"
            }
            loadWaiting()
        }
    }

    fun dayName(d: Int) = when (d) { 0 -> "today"; 1 -> "tomorrow"; 2 -> "the day after tomorrow"; else -> "in $d days" }

    fun fixVideo(v: WaitingVideo, note: String) {
        if (note.isBlank()) return
        viewModelScope.launch {
            val r = runCatching { api.reviewFix(v.id, note) }.getOrNull()
            reviewMsg = r?.optString("error")?.ifBlank { null } ?: if (r == null) strings.serverError
                else "Claude is fixing it. The new version will come here for your OK."
        }
    }

    // ---- talk to the Mac (hold the centre mic, or the "Talk to Mac" app shortcut) ----
    var voiceOpen by mutableStateOf(false)
    var voiceHeard by mutableStateOf(""); private set
    private var voiceSent = false

    fun voiceStart() {
        voiceOpen = true; voiceHeard = ""; voiceSent = false
        voice.stopSpeaking()
        if (phase == Phase.Listening) return
        phase = Phase.Listening
        hear(onPartial = { voiceHeard = it }) { text ->
            micLevel = 0f
            phase = Phase.Ask
            if (!text.isNullOrBlank()) { voiceHeard = text; voiceSent = true; viewModelScope.launch { pickRoute(); voiceOpen = false; screen = Screen.AppAssistant; runAppAssistant(text) } }
            else if (!voiceSent) voiceHeard = "…"
        }
    }

    /** Releasing the mic ends listening; the words go to the agent. */
    fun voiceRelease() { if (phase == Phase.Listening) stopHearing() }

    fun voiceClose() { voiceRelease(); voiceOpen = false }

    fun speakEnglish(t: String) = voice.speak(t, Locale.US, flush = true)


    fun newMacChat() { agentSession = null; chat = emptyList() }

    /**
     * "250 ka cigarette ka packet liya" → straight into the Money book. Works offline: understood on the phone,
     * saved in the offline queue when the Mac can't be reached (sent when it is back). Returns what was added.
     */
    suspend fun addMoneyFromText(t: String): String? {
        val j = com.englishcoach.app.ui.MoneyParse.parse(t)
        if (j.optDouble("amount", 0.0) <= 0) return null
        val r = mac("/money/add", j)
        val desc = com.englishcoach.app.ui.MoneyParse.describe(j)
        return if (r.has("error") && !r.has("entries")) {
            com.englishcoach.app.ui.MoneyOffline.queue(getApplication(), j)
            "✅ $desc · Money book mein save (offline, Mac milte hi sync)"
        } else "✅ $desc · Money book mein add ho gaya"
    }

    /** Sends ideas saved offline; the Mac writes the note and notifies. */
    suspend fun syncIdeas(): Int {
        val app = getApplication<Application>(); var n = 0
        while (com.englishcoach.app.ui.IdeaOffline.pending(app).length() > 0) {
            val q = com.englishcoach.app.ui.IdeaOffline.pending(app).getJSONObject(0)
            val r = runCatching { api.send("/ideas/add", q) }.getOrNull() ?: break
            if (r.has("error") && !r.has("ideas")) break
            com.englishcoach.app.ui.IdeaOffline.removeFirst(app); n++
        }
        return n
    }

    private fun reply(t: String, speak: String? = null) {
        chat = chat + ChatItem(false, t)
        voiceHeard = t.lineSequence().first()
        speak?.let { voice.speak(it, Locale.US, flush = true) }
    }

    private val SCREENS = listOf(
        "mac ki (window|screen)|mac screen|screen mirror|remote|live screen" to Screen.Remote,
        "money|paise|kharch|hisaab|hisab|wallet" to Screen.Money, "idea" to Screen.Ideas, "helpers?|staff|naukar|maasi|masi|mulazim|driver" to Screen.Staff,
        "etsy|store studio|stores?\\b|dukan" to Screen.Stores, "naat|nasheed|song studio" to Screen.Naat,
        "videos?\\b|uploads?|review" to Screen.Videos, "youtube|studio|flow" to Screen.Studio, "cv|resume" to Screen.Cv,
        "settings?|setting" to Screen.Settings, "progress|report" to Screen.Progress, "alerts?|notification" to Screen.Alerts,
        "routines?" to Screen.Routines, "clipboard|files?\\b|transfer" to Screen.Transfer, "video rules|rules" to Screen.Rules,
        "cleaner|storage|safai" to Screen.Cleaner, "assistant|chat" to Screen.Assistant, "home|ghar ki screen|main screen" to Screen.Home, "\\bmac\\b" to Screen.Mac,
    )
    private val OPEN = Regex("(kholo|khol do|khol dein|kholna|open|dikhao|dikha do|le chalo|par jao|pe jao|chalo)", RegexOption.IGNORE_CASE)

    /**
     * The assistant drives the app itself: "Bismillah ki English naat banani hai" → brief + lyrics made, Naat page opened;
     * "Mac ki window kholo, phir Claude kholo, phir usme X likho" → app opens the live Mac screen, the Mac steps go to the agent;
     * ideas and money entries are kept (offline too). Returns false when it's a plain Mac request.
     */
    suspend fun appCommand(t: String): Boolean {
        val low = t.lowercase()
        // ideas first ("idea aaya: …")
        if (com.englishcoach.app.ui.IdeaOffline.looksLikeIdea(t)) {
            com.englishcoach.app.ui.IdeaOffline.add(getApplication(), t)
            val sent = syncIdeas() > 0
            reply(if (sent) "💡 Idea note kar liya! AI poora note bana raha hai, ready hone par notification aayega.\n(Sab ideas: More › Ideas)"
                  else "💡 Idea phone par save kar liya. Mac/internet milte hi AI note bana dega.\n(Sab ideas: More › Ideas)", "Idea note kar liya.")
            return true
        }
        // naat / song: write the brief + lyrics and open its page
        if (Regex("(naat|nasheed|hamd|manqabat)").containsMatchIn(low) && Regex("(bana|banani|banao|create|make|likh|chahiye)").containsMatchIn(low)) {
            val style = when { Regex("daf|duff").containsMatchIn(low) -> "daf"; Regex("music|instrument").containsMatchIn(low) -> "music"; else -> "vocals" }
            val kids = !Regex("(bare|baron|adults?|grown)").containsMatchIn(low)
            val r = runCatching { api.naatCreate(t, style, "kids", kids) }.getOrNull()
            if (r == null || r.has("error")) { reply("⚠️ Naat shuru nahi ho saki: ${r?.optString("error") ?: "Mac nahi mil raha"}"); return true }
            loadNaats(); openNaat(r.optString("id")); screen = Screen.Naat; voiceOpen = false
            reply("🎵 Naat shuru! Lyrics, title aur Suno style ban rahe hain (1-2 min). Naat page khol diya.\n" +
                "Agla step: lyrics + style copy karke Suno mein gana banao, phir yahan \"Upload song\" dabao. Video khud ban jayegi.", "Naat ki lyrics ban rahi hain. Naat page khol diya.")
            return true
        }
        // chains: "X kholo, phir Y karo, phir Z"
        val parts = t.split(Regex("\\s*(,?\\s*\\b(phir|fir|then|uske baad|us ke baad|and then|aur phir)\\b)\\s*", RegexOption.IGNORE_CASE)).map { it.trim(' ', ',', '.') }.filter { it.isNotBlank() }
        var target: Screen? = null
        val macSteps = mutableListOf<String>()
        for (p in parts) {
            val pl = p.lowercase()
            val sc = if (OPEN.containsMatchIn(pl)) SCREENS.firstOrNull { Regex(it.first, RegexOption.IGNORE_CASE).containsMatchIn(pl) }?.second else null
            // "claude kholo", "chrome kholo" etc. are Mac apps, not app screens
            if (sc != null && !Regex("(claude|chrome|safari|finder|terminal|xcode|canva|whatsapp web|notes|music app)").containsMatchIn(pl)) target = sc
            else macSteps += p
        }
        if (target != null) {
            if (target == Screen.Stores) { etsyStore = null; etsyRun = null }
            if (target == Screen.Videos) refreshVideos()
            screen = target; voiceOpen = false
            if (macSteps.isEmpty()) { reply("✅ ${target.name} khol diya."); return true }
            reply("✅ ${target.name} khol diya. Baqi kaam Mac par shuru: ${macSteps.joinToString(" → ")}")
            askMacAgent(macSteps.joinToString(", phir "), alreadyShown = true)
            return true
        }
        // money ("250 ka cigarette packet liya")
        if (com.englishcoach.app.ui.MoneyParse.looksLikeMoney(t)) {
            reply("🤖 Money book mein add kar raha hoon…")
            val (say, list) = addMoneySmart(t)
            if (list.isEmpty()) return false
            reply("$say\n" + list.joinToString("\n") { com.englishcoach.app.ui.MoneyParse.describe(it) } + "\n(Ghalat ho to Money book mein Undo ya tap karke badlo.)",
                say.replace(Regex("[^\\w\\s.,·:-]"), ""))
            return true
        }
        // Etsy design search ("<store> ke liye funeral shirt banao")
        val design = Regex("(design|shirt|t-shirt|program|template|poster|card|banner|sticker)").containsMatchIn(low) &&
            Regex("(bana|banao|make|create|ideas?|search)").containsMatchIn(low)
        if (design) {
            val stores = mac("/etsy/overview").optJSONArray("stores")?.let { a -> List(a.length()) { a.getJSONObject(it).optString("name") } } ?: emptyList()
            val st = stores.firstOrNull { low.contains(it.lowercase()) } ?: stores.firstOrNull() ?: return false
            val q = t.replace(Regex(st, RegexOption.IGNORE_CASE), "").replace(Regex("(ke liye|for|bana do|banao|bana|make|create)", RegexOption.IGNORE_CASE), " ").trim()
            val r = mac("/etsy/search", org.json.JSONObject().put("store", st).put("query", q))
            if (r.has("error")) return false
            etsyStore = st; etsyRun = r.optString("id"); screen = Screen.Stores; voiceOpen = false
            reply("🛍 $st ke liye \"$q\" ki analysis shuru, ideas aa rahe hain. Store Studio khol diya.", "$st ke liye analysis shuru.")
            return true
        }
        return false
    }

    private val moneySync = kotlinx.coroutines.sync.Mutex()

    /** Bill photos kept on the phone (read offline) go to the Mac first; the entry then points at them. False = Mac not reachable. */
    suspend fun uploadLocalPhotos(e: org.json.JSONObject): Boolean {
        val loc = e.optJSONArray("local_photos") ?: return true
        val names = e.optJSONArray("photos") ?: org.json.JSONArray().also { e.put("photos", it) }
        for (i in 0 until loc.length()) {
            val f = java.io.File(loc.getString(i)); if (!f.exists()) continue
            val r = runCatching { api.send("/money/attach", org.json.JSONObject().put("image", android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))) }.getOrNull()
            val n = r?.optString("photo")?.ifBlank { null } ?: return false
            names.put(n); f.delete()
        }
        e.remove("local_photos")
        return true
    }

    /** Sends Money entries saved offline (one sync at a time; the Mac also ignores an entry it already has). */
    suspend fun syncMoneyNow(): Int = moneySync.withLock {
        val app = getApplication<Application>()
        var sent = 0
        while (com.englishcoach.app.ui.MoneyOffline.pending(app).length() > 0) {
            val q = com.englishcoach.app.ui.MoneyOffline.pending(app).getJSONObject(0)   // keeps _tmp: the Mac de-duplicates by it
            if (!uploadLocalPhotos(q)) break
            val r = runCatching { api.send("/money/add", q) }.getOrNull() ?: break
            if (r.has("error") && !r.has("entries")) break
            com.englishcoach.app.ui.MoneyOffline.removeFirst(app); sent++
        }
        sent
    }

    fun syncMoneyOffline() = viewModelScope.launch {
        val sent = syncMoneyNow()
        val ideasSent = syncIdeas()
        if (sent > 0 || ideasSent > 0) notice = listOfNotNull(if (sent > 0) "$sent kharche" else null, if (ideasSent > 0) "$ideasSent ideas" else null)
            .joinToString(" aur ") + " Mac par sync ho gaye ✅"
    }

    /**
     * One sentence, many entries. Online: the Mac's AI splits and classifies it (/money/smart). Offline (or AI failed):
     * the phone splits it and saves each piece in the offline queue. Returns (what to say, the entries added).
     */
    suspend fun addMoneySmart(t: String): Pair<String, List<org.json.JSONObject>> {
        // understood on the phone right away (the Mac's AI took ~1 minute); the Mac only stores the entries
        val app = getApplication<Application>()
        val r = org.json.JSONObject()
        val online = !mac("/money/list").let { it.has("error") || it.optBoolean("offline") }
        val list = com.englishcoach.app.ui.MoneyParse.split(t).map { com.englishcoach.app.ui.MoneyParse.parse(it) }.filter { it.optDouble("amount", 0.0) > 0 }
        if (list.isEmpty()) return (if (online) r.optString("error", "Samajh nahi aaya") else "Amount samajh nahi aaya, jaise: 500 dawai ghar ki") to emptyList()
        fun ids(l: org.json.JSONObject) = l.optJSONArray("entries")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("id") }.toSet() } ?: emptySet()
        val before = if (online) ids(mac("/money/list")) else emptySet()
        val out = mutableListOf<org.json.JSONObject>()
        var last: org.json.JSONObject? = null
        for (j in list) {
            val x = if (online) mac("/money/add", j) else org.json.JSONObject().put("error", "offline")
            if (x.has("error") && !x.has("entries")) { com.englishcoach.app.ui.MoneyOffline.queue(app, j); out += org.json.JSONObject(j.toString()).put("offline", true).put("id", "off") }
            else last = x
        }
        last?.optJSONArray("entries")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("id") !in before }.forEach { out += it } }
        return "✅ ${out.size} cheezein add ho gayi" + (if (out.any { it.optBoolean("offline") }) " (offline, Mac milte hi sync)" else "") to out
    }

    /** Sends the request to the Claude agent on the Mac and shows its steps live until it answers. */
    fun askMac(text: String, allow: List<String> = emptyList()) {
        if (text.isBlank()) return
        askMacAgent(text, allow)
    }

    /** Which AI the Mac assistant uses: "claude" (controls the Mac) or "gemini" (free; answers and writes, no clicking). */
    var macEngine by mutableStateOf(prefs.getString("macEngine", "claude")!!); private set
    fun pickMacEngine(e: String) { macEngine = e; prefs.edit().putString("macEngine", e).apply(); agentSession = null }

    // ---- 📱 App assistant (its own chat; Claude or Gemini) ----
    var appChat by mutableStateOf(listOf<ChatItem>()); private set
    var appBusy by mutableStateOf(false); private set
    var appEngine by mutableStateOf(prefs.getString("appEngine", "gemini")!!); private set
    fun pickAppEngine(e: String) { appEngine = e; prefs.edit().putString("appEngine", e).apply() }
    private fun appSay(t: String) { appChat = appChat + ChatItem(false, t) }

    /** Runs what the owner told the app. Online: Claude/Gemini plans the actions; offline: the phone does what it can. */
    suspend fun runAppAssistant(t: String) {
        appChat = appChat + ChatItem(fromMe = true, text = t)
        appBusy = true
        val r = mac("/app/assist", org.json.JSONObject().put("text", t).put("engine", appEngine))
        appBusy = false
        if (r.has("error") || !r.has("actions")) {
            // offline: phone-side understanding (money, ideas, screens)
            val before = chat.size
            if (appCommand(t)) { chat.drop(before).filter { !it.fromMe }.forEach { appSay(it.text) }; appSay("📴 (offline, phone ne khud kiya)") }
            else appSay("📴 Mac/internet nahi mil raha. Paise, ideas aur screens offline bhi chalte hain; baqi ke liye online aao.")
            return
        }
        val app = getApplication<Application>()
        val done = mutableListOf<String>()
        var go: Screen? = null
        val acts = r.optJSONArray("actions") ?: org.json.JSONArray()
        for (i in 0 until acts.length()) {
            val a = acts.getJSONObject(i)
            when (a.optString("type")) {
                "money" -> {
                    a.remove("type")
                    val x = mac("/money/add", a)
                    if (x.has("error") && !x.has("entries")) com.englishcoach.app.ui.MoneyOffline.queue(app, a)
                    done += "💰 " + com.englishcoach.app.ui.MoneyParse.describe(a)
                }
                "idea" -> { com.englishcoach.app.ui.IdeaOffline.add(app, a.optString("text")); done += "💡 Idea note: " + a.optString("text").take(60) }
                "open" -> runCatching { Screen.valueOf(a.optString("screen")) }.getOrNull()?.let { go = it; done += "📲 ${it.name} khola" }
                "fix" -> done += "🛠 Claude app theek kar raha hai: " + a.optString("request").take(90) +
                    (if (a.optString("note").isNotBlank()) " (" + a.optString("note") + ")" else "") + "\nNaya APK ban kar Downloads mein aayega, notification milega."
            }
        }
        syncIdeas()
        appSay((r.optString("say").ifBlank { "Ho gaya" }) + (if (done.isNotEmpty()) "\n" + done.joinToString("\n") else "") +
            "\n(" + (if (r.optString("engine") == "gemini") "✨ Gemini" else "🤖 Claude") + ")")
        voice.speak(r.optString("say"), Locale.US, flush = true)
        go?.let { if (it == Screen.Stores) { etsyStore = null; etsyRun = null }; screen = it }
    }

    /** App assistant (the big mic): app things first (money, ideas, naat, screens); anything else goes to the Mac assistant. */
    fun appAssistant(text: String) {
        if (text.isBlank()) return
        chat = chat + ChatItem(fromMe = true, text = text)
        viewModelScope.launch { if (!appCommand(text)) askMacAgent(text, alreadyShown = true) }
    }

    private fun askMacAgent(text: String, allow: List<String> = emptyList(), alreadyShown: Boolean = false) {
        if (allow.isEmpty() && !alreadyShown) chat = chat + ChatItem(fromMe = true, text = text)
        val item = ChatItem(false, strings.claudeWorking, working = true, ask = text)
        chat = chat + item
        fun update(f: (ChatItem) -> ChatItem) { chat = chat.map { if (it.id == item.id) f(it) else it } }
        viewModelScope.launch {
            val r = runCatching { api.agent(text, if (roman) "roman" else "en", agentSession, allow, "claude") }.getOrNull()
            if (r?.has("answer") == true) { update { it.copy(text = "✨ Gemini: " + r.optString("answer"), working = false) }; return@launch }
            val jid = r?.optString("job_id")?.ifBlank { null }
            if (jid == null) {
                if (r?.optString("error") == "Not paired") forgetPairing()
                update { it.copy(text = r?.optString("error")?.ifBlank { null } ?: strings.serverError, working = false) }; return@launch
            }
            while (true) {
                delay(1_500)
                val j = runCatching { api.agentJob(jid) }.getOrNull() ?: continue
                j.optString("session").ifBlank { null }?.let { agentSession = it }
                val steps = j.optJSONArray("steps")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList()
                if (j.optString("status") == "running") { update { it.copy(steps = steps) }; continue }
                val denied = j.optJSONArray("denied")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList()
                val answer = j.optString("result").ifBlank { strings.done }
                update { it.copy(text = answer, working = false, steps = steps, denied = denied) }
                voice.speak(answer.take(300), if (roman) Voice.URDU else Locale.US, flush = true)
                return@launch
            }
        }
    }

    /** The owner allowed the commands the agent was stopped on: remember them and run the same request again. */
    fun allowAgent(item: ChatItem) {
        chat = chat.map { if (it.id == item.id) it.copy(denied = emptyList()) else it }
        askMac(item.ask, item.denied)
    }

    private val recorder = Recorder { micLevel = it }
    private var recording: Job? = null
    var transcribing by mutableStateOf(false); private set
    var urduMode by mutableStateOf(false); private set
    /** Word-by-word result of the last "Say it again". */
    var pron by mutableStateOf<Pronunciation?>(null); private set
    var pronChecking by mutableStateOf(false); private set

    /**
     * One spoken answer -> text. Uses Whisper on the Mac when [useWhisper] is on (or [whisperLang] is given,
     * e.g. "ur" for the Help button); otherwise the phone's recognizer with live words in [onPartial].
     */
    private fun hear(onPartial: (String) -> Unit, whisperLang: String? = null, onResult: (String?) -> Unit) {
        if (whisperLang == null && (!useWhisper || macOnline == false)) { voice.listen(onPartial, onResult); return }
        voice.stopSpeaking()
        voice.stopListening()  // free the mic from the phone recognizer before recording
        recording = viewModelScope.launch {
            kotlinx.coroutines.delay(150)
            val wav = recorder.record()
            if (wav == null) {
                if (recorder.lastProblem == "mic") notice = strings.micBusy
                onResult(null)
                return@launch
            }
            transcribing = true
            val text = api.transcribe(wav, whisperLang ?: "auto")
            transcribing = false
            if (text == null) { notice = strings.serverError; macOnline = false; checkServer() }
            onResult(text?.takeUnless { voice.isEcho(it) })   // the app's own voice picked up by the mic is not an answer
        }
    }

    private fun stopHearing() { if (recording?.isActive == true) recorder.stop() else voice.stopListening() }

    /** "Help" on the practice screen: say it in Urdu, the coach shows how to say it in English. */
    fun helpInUrdu() {
        if (phase == Phase.Listening) { stopHearing(); return }
        notice = null
        heard = ""
        retrying = false
        phase = Phase.Listening
        urduMode = true
        hear(onPartial = {}, whisperLang = "ur") { text ->
            urduMode = false
            micLevel = 0f
            if (text.isNullOrBlank()) { if (notice == null) notice = strings.didntHear; phase = Phase.Ask } else { heard = text; send(text) }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val r = runCatching { api.cleanupEmptyTrash() }.getOrNull()
            val freed = r?.optLong("freed") ?: 0
            cleanItems = cleanItems.filterNot { it.kind == "trash" }
            cleanMessage = if (r?.optBoolean("ok") == true) strings.trashEmptied.format(String.format(java.util.Locale.US, "%.1f GB", freed / 1e9))
                else r?.optString("error") ?: strings.serverError
            refreshStats()
        }
    }

    /** Listens once and hands the text to [onText]; used by the Mac Assistant's mic. */
    fun dictate(onText: (String) -> Unit) {
        if (phase == Phase.Listening) { stopHearing(); return }
        phase = Phase.Listening
        hear(onPartial = {}) { text ->
            micLevel = 0f
            phase = Phase.Ask
            if (!text.isNullOrBlank()) onText(text)
        }
    }

    fun confirmAction(item: ChatItem, yes: Boolean) {
        chat = chat.map { if (it === item) it.copy(confirmId = null) else it }
        if (!yes) { chat = chat + ChatItem(false, strings.cancelled); return }
        viewModelScope.launch {
            val r = runCatching { api.confirm(item.confirmId!!) }.getOrNull() ?: return@launch
            val job = r.optString("job_id").ifBlank { null }
            chat = chat + ChatItem(false, r.optString("result").ifBlank { if (job != null) strings.claudeWorking else strings.done }, jobId = job)
            job?.let { watchJob(it) }
        }
    }

    private fun watchJob(id: String) {
        viewModelScope.launch {
            repeat(180) {
                delay(5_000)
                val jobs = runCatching { api.jobs().optJSONArray("jobs") }.getOrNull() ?: return@repeat
                for (i in 0 until jobs.length()) {
                    val j = jobs.getJSONObject(i)
                    if (j.optString("id") == id && j.optString("status") != "running") {
                        chat = chat + ChatItem(false, "Claude: " + j.optString("result").take(1500))
                        voice.speak(strings.claudeDone, flush = true)
                        return@launch
                    }
                }
            }
        }
    }

    /** Pulls a fresh Mac screenshot about once a second while the Remote screen is open. */
    /** H.264 uses ~15x less data than JPEG frames: on by default over Tailscale, or when the owner picks it. */
    var h264Pref by mutableStateOf(prefs.getString("h264", "auto")!!); private set   // auto | on | off
    fun cycleH264() {
        h264Pref = when (h264Pref) { "auto" -> "on"; "on" -> "off"; else -> "auto" }
        prefs.edit().putString("h264", h264Pref).apply()
    }
    // On Tailscale / mobile data the low-lag JPEG stream (Mac drops old frames) beats H.264, which piles up behind a slow link
    private fun wantH264() = h264Pref == "on"
    var h264Size by mutableStateOf<Pair<Int, Int>?>(null); private set
    var streamKbps by mutableStateOf(0); private set
    private var surface: android.view.Surface? = null
    private var decoder: H264Decoder? = null
    private var lastConfig: ByteArray? = null
    private val decLock = Any()

    fun attachSurface(s: android.view.Surface?) {
        synchronized(decLock) { decoder?.release(); decoder = null; surface = s }
    }

    private fun onH264(kind: Int, data: ByteArray) {
        synchronized(decLock) {
            when (kind) {
                0 -> {
                    val w = ((data[0].toInt() and 0xff) shl 8) or (data[1].toInt() and 0xff)
                    val h = ((data[2].toInt() and 0xff) shl 8) or (data[3].toInt() and 0xff)
                    val cfg = data.copyOfRange(4, data.size)
                    if (h264Size != (w to h) || !cfg.contentEquals(lastConfig)) { decoder?.release(); decoder = null }
                    lastConfig = cfg
                    if (h264Size != (w to h)) h264Size = w to h
                }
                else -> {
                    val s = surface ?: return
                    val cfg = lastConfig ?: return
                    if (decoder == null) {
                        if (kind != 1) return          // a new decoder must start on a key frame
                        val sz = h264Size ?: return
                        decoder = runCatching { H264Decoder(s, sz.first, sz.second, cfg) }.getOrNull() ?: return
                    }
                    runCatching { decoder?.feed(data) }.onFailure { decoder?.release(); decoder = null }
                }
            }
        }
    }

    suspend fun streamScreen() {
        displayCount = api.displayCount()
        while (true) {
            if (display >= displayCount) display = 0   // e.g. the phone display was removed
            val shownDisplay = display
            if (wantH264()) {
                screenBitmap = null
                var bytes = 0L; var frames = 0; var t0 = System.currentTimeMillis(); var got = false
                api.screenH264(display, if (onTailscale()) 1280 else 1600, if (onTailscale()) 1200 else 3000) { kind, data ->
                    if (display != shownDisplay || !wantH264()) throw kotlinx.coroutines.CancellationException("changed")
                    onH264(kind, data); got = true
                    bytes += data.size; if (kind != 0) frames++
                    val now = System.currentTimeMillis()
                    if (now - t0 >= 1000) {
                        val f = frames; val k = (bytes * 8 / (now - t0)).toInt()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { screenFps = f; streamKbps = k; screenError = null }
                        frames = 0; bytes = 0; t0 = now
                    }
                }
                if (got) continue
                h264Size = null    // H.264 didn't start: fall back to JPEG frames below
            }
            h264Size = null
            var frames = 0; var t0 = System.currentTimeMillis(); var shown = 0
            api.screenStream(display, if (onTailscale()) 900 else 1440, if (onTailscale()) 0.38f else 0.55f, lowLag = onTailscale()) { jpg ->
                if (display != shownDisplay || wantH264()) throw kotlinx.coroutines.CancellationException("changed")
                val bmp = android.graphics.BitmapFactory.decodeByteArray(jpg, 0, jpg.size)?.asImageBitmapSafe() ?: return@screenStream
                frames++; shown++
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    screenBitmap = bmp; screenError = null; streamKbps = jpg.size * 8 * maxOf(frames, 1) / 1000
                    val now = System.currentTimeMillis()
                    if (now - t0 >= 1000) { screenFps = frames; frames = 0; t0 = now }
                }
            }
            if (shown > 0 || display != shownDisplay || wantH264()) continue
            // no picture: maybe that display no longer exists (extend mode ended) rather than a permission problem
            displayCount = api.displayCount()
            if (display >= displayCount) { display = 0; displayModeName = "mirror"; continue }
            val img = api.screen(display)
            if (img != null) {
                screenBitmap = android.graphics.BitmapFactory.decodeByteArray(img, 0, img.size)?.asImageBitmapSafe(); screenError = null; screenFps = 1
            } else { screenError = strings.screenPermission; pickRoute() }
            delay(if (img != null) 300 else 3_000)
        }
    }

    // ---- phone as a display + Mac sound ----
    var displayModeName by mutableStateOf("mirror"); private set   // mirror | extend | main
    var displayBusy by mutableStateOf(false); private set
    fun loadDisplayMode() {
        viewModelScope.launch {
            runCatching { api.displayMode() }.getOrNull()?.let { r ->
                displayModeName = r.optString("mode", "mirror")
                displayCount = r.optInt("displays", displayCount)
                display = if (r.isNull("phone_index")) (if (display >= displayCount) 0 else display) else r.optInt("phone_index")
            }
        }
    }
    fun setDisplay(mode: String) {
        if (displayBusy) return
        viewModelScope.launch {
            displayBusy = true
            val r = runCatching { api.setDisplayMode(mode, 1920, 886) }.getOrNull()
            if (r == null || r.has("error")) screenError = r?.optString("error") ?: strings.serverError
            else {
                displayModeName = r.optString("mode")
                displayCount = r.optInt("displays", 1)
                display = if (r.isNull("phone_index")) 0 else r.optInt("phone_index")
            }
            displayBusy = false
        }
    }

    var soundOn by mutableStateOf(false); private set
    private var soundJob: kotlinx.coroutines.Job? = null
    /** Plays the Mac's sound on the phone (CD quality at home, lighter on mobile data). */
    fun toggleSound() {
        if (soundOn) { soundOn = false; soundJob?.cancel(); return }
        soundOn = true
        soundJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (soundOn) {
                val mobile = onTailscale()
                val rate = if (mobile) 24000 else 48000; val ch = if (mobile) 1 else 2
                val mask = if (ch == 2) android.media.AudioFormat.CHANNEL_OUT_STEREO else android.media.AudioFormat.CHANNEL_OUT_MONO
                val min = android.media.AudioTrack.getMinBufferSize(rate, mask, android.media.AudioFormat.ENCODING_PCM_16BIT)
                val track = android.media.AudioTrack.Builder()
                    .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(android.media.AudioFormat.Builder().setSampleRate(rate).setChannelMask(mask)
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(min * 3).setTransferMode(android.media.AudioTrack.MODE_STREAM)
                    .setPerformanceMode(android.media.AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build()
                track.play()
                try { api.macAudio(rate, ch) { b, n -> if (soundOn) track.write(b, 0, n) else throw kotlinx.coroutines.CancellationException() } }
                catch (_: Exception) {}
                runCatching { track.stop(); track.release() }
                if (soundOn) delay(1500)
            }
        }
    }

    private fun onTailscale() = api.baseUrl().contains("100.")
    /** True while the Mac shows its lock screen (checked every few seconds on the Screen view). */
    var macLocked by mutableStateOf(false); private set
    suspend fun watchLock() {
        while (true) { runCatching { macLocked = api.macLocked() }; delay(3_000) }
    }

    private fun android.graphics.Bitmap.asImageBitmapSafe() = this.asImageBitmap()

    // ---- CV studio: a CV tailored to each job, edited by hand or by AI, as a PDF ----
    var cvs by mutableStateOf(listOf<org.json.JSONObject>()); private set
    var cvOpen by mutableStateOf<String?>(null)
    var cv by mutableStateOf<org.json.JSONObject?>(null); private set
    var cvMsg by mutableStateOf<String?>(null)
    var cvPdf by mutableStateOf<java.io.File?>(null); private set
    private var cvPoll: Job? = null

    fun loadCvs() { viewModelScope.launch { cvs = runCatching { api.cvList().optJSONArray("items") }.getOrNull()?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: cvs } }

    fun openCv(id: String) {
        cvOpen = id; cv = null; cvPdf = null
        cvPoll?.cancel()
        cvPoll = viewModelScope.launch {
            var lastStage = ""
            while (cvOpen == id) {
                val g = runCatching { api.cvGet(id) }.getOrNull()
                if (g != null) {
                    cv = g
                    val stage = g.optJSONObject("status")?.optString("stage").orEmpty()
                    if (stage == "ready" && (lastStage != "ready" || cvPdf == null)) loadCvPdf(id)
                    lastStage = stage
                }
                delay(if (g?.optBoolean("running") == true) 4_000 else 20_000)
            }
        }
    }

    private suspend fun loadCvPdf(id: String) {
        val b = runCatching { api.bytes("/cv/pdf?id=$id") }.getOrNull() ?: return
        val f = java.io.File(getApplication<Application>().cacheDir, "cv-$id.pdf")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { f.writeBytes(b) }
        cvPdf = f
    }

    fun createCv(link: String, text: String, note: String) {
        viewModelScope.launch {
            pickRoute()
            val r = runCatching { api.cvCreate(link, text, note) }.getOrNull()
            if (r == null || r.has("error")) { cvMsg = r?.optString("error") ?: strings.serverError; return@launch }
            cvMsg = null; loadCvs(); openCv(r.optString("id"))
        }
    }

    fun saveCv(text: String) {
        val id = cvOpen ?: return
        viewModelScope.launch {
            val r = runCatching { api.cvSave(id, text) }.getOrNull()
            cvMsg = r?.optString("error")?.ifBlank { null } ?: if (r == null) strings.serverError else "✅ Saved. PDF updated."
            cvPdf = null; openCv(id)
        }
    }

    fun aiEditCv(note: String) {
        val id = cvOpen ?: return
        viewModelScope.launch {
            runCatching { api.cvAiEdit(id, note) }
            cvMsg = "✍️ Claude is changing your CV…"; cvPdf = null; openCv(id)
        }
    }

    fun restoreCv(v: String) {
        val id = cvOpen ?: return
        viewModelScope.launch { runCatching { api.cvRestore(id, v) }; cvMsg = "Restored $v"; cvPdf = null; openCv(id) }
    }

    /** Saves the PDF to Downloads/CVs and opens the share sheet. */
    fun shareCvPdf() {
        val f = cvPdf ?: return
        val app = getApplication<Application>()
        val title = cv?.optJSONObject("report")?.optString("job_title")?.ifBlank { null } ?: "Job"
        val name = (ownerName.ifBlank { "My" }.replace(Regex("[^A-Za-z0-9]+"), "_") + "_CV_") + title.replace(Regex("[^A-Za-z0-9]+"), "_").take(40) + ".pdf"
        viewModelScope.launch {
            val uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val v = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/CVs")
                }
                app.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)?.also { u ->
                    app.contentResolver.openOutputStream(u)?.use { it.write(f.readBytes()) }
                }
            } ?: run { cvMsg = "Couldn't save the PDF"; return@launch }
            cvMsg = "Saved to Downloads › CVs › $name"
            app.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType("application/pdf").putExtra(android.content.Intent.EXTRA_STREAM, uri)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share CV").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ---- activity log ----
    var activity by mutableStateOf(listOf<org.json.JSONObject>()); private set
    var activityKind by mutableStateOf("")
    fun loadActivity() {
        viewModelScope.launch {
            activity = runCatching { api.activity(activityKind).optJSONArray("items") }.getOrNull()
                ?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: activity
        }
    }

    // ---- autopilot day ----
    var autopilot by mutableStateOf<org.json.JSONObject?>(null); private set
    var autopilotMsg by mutableStateOf<String?>(null)
    fun loadAutopilot() { viewModelScope.launch { runCatching { api.autopilot() }.getOrNull()?.let { autopilot = it } } }
    fun saveAutopilot(on: Boolean, time: String, credits: Int) {
        viewModelScope.launch { runCatching { api.autopilotSave(on, time, credits) }.getOrNull()?.let { autopilot = it; autopilotMsg = "Saved" } }
    }
    fun runAutopilot() {
        viewModelScope.launch {
            val r = runCatching { api.autopilotRun() }.getOrNull()
            autopilotMsg = r?.optString("skipped")?.ifBlank { null } ?: r?.optString("error")?.ifBlank { null }
                ?: if (r?.optBoolean("ok") == true) "🤖 Started today's video. Follow it in the progress card." else strings.serverError
            loadAutopilot()
        }
    }

    // ---- naat / song studio ----
    var naats by mutableStateOf(listOf<org.json.JSONObject>()); private set
    var naatOpen by mutableStateOf<String?>(null)
    var naat by mutableStateOf<org.json.JSONObject?>(null); private set
    var naatSteps by mutableStateOf(listOf<String>()); private set
    var naatProgress by mutableStateOf<org.json.JSONObject?>(null); private set
    var naatMsg by mutableStateOf<String?>(null)
    private var naatPoll: kotlinx.coroutines.Job? = null

    fun loadNaats() { viewModelScope.launch { naats = runCatching { api.naatList().optJSONArray("items") }.getOrNull()?.let { a -> List(a.length()) { a.getJSONObject(it) } } ?: naats } }

    /** Opens a project and keeps it fresh while Claude is writing or making the video. */
    fun openNaat(id: String) {
        naatOpen = id; naat = null; naatSteps = emptyList(); naatProgress = null
        naatPoll?.cancel()
        naatPoll = viewModelScope.launch {
            while (naatOpen == id) {
                val g = runCatching { api.naatGet(id) }.getOrNull()
                if (g != null) naat = g
                val stg = g?.optJSONObject("status")?.optString("stage").orEmpty()
                naatProgress = if (stg.startsWith("making") || stg.startsWith("video") || stg.startsWith("sent")) runCatching { api.naatProgress(id) }.getOrNull() else null
                val job = g?.optJSONObject("status")?.optString("job").orEmpty()
                val running = job.isNotBlank() && runCatching { api.agentJob(job) }.getOrNull()?.let { j ->
                    naatSteps = j.optJSONArray("steps")?.let { x -> List(x.length()) { x.optString(it) } } ?: emptyList()
                    j.optString("status") == "running"
                } == true
                delay(if (running) 4_000 else 15_000)
            }
        }
    }

    fun createNaat(idea: String, style: String, voice: String = "kids", kids: Boolean = true) {
        viewModelScope.launch {
            val r = runCatching { api.naatCreate(idea, style, voice, kids) }.getOrNull()
            if (r == null || r.has("error")) { naatMsg = r?.optString("error") ?: strings.serverError; return@launch }
            naatMsg = null; loadNaats(); openNaat(r.optString("id"))
        }
    }

    fun reviseNaat(note: String) {
        val id = naatOpen ?: return
        viewModelScope.launch { runCatching { api.naatRevise(id, note) }; naatMsg = "Claude is updating the lyrics…"; openNaat(id) }
    }

    fun importSong(uri: android.net.Uri) {
        val id = naatOpen ?: return
        viewModelScope.launch {
            val cr = getApplication<Application>().contentResolver
            val name = runCatching { cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull() ?: "song.mp3"
            naatMsg = "Sending the song to the Mac…"
            pickRoute()
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { cr.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            if (bytes == null) { naatMsg = "Couldn't read that file"; return@launch }
            val r = runCatching { api.naatUpload(id, name, bytes) }.getOrNull()
            naatMsg = r?.optString("error")?.ifBlank { null } ?: if (r?.optBoolean("ok") == true) "✅ Song imported (${bytes.size / 1024} KB)" else strings.serverError
            openNaat(id)
        }
    }

    /** Owner tapped "raise today's limit" after the credit guard stopped a video. */
    fun raiseCreditLimit(limit: Int) {
        viewModelScope.launch {
            val r = runCatching { api.creditSettings(limit, null) }.getOrNull()
            naatMsg = if (r == null || r.has("error")) "Had nahi badli: ${r?.optString("error") ?: strings.serverError}"
                else "✅ Aaj ki had $limit credits. Ab \"Make the video\" dobara dabao."
            studio.loadGuard()
        }
    }

    fun makeNaatVideo(credits: Int, shorts: Boolean, override: Boolean = false) {
        val id = naatOpen ?: return
        viewModelScope.launch {
            val r = runCatching { api.naatMake(id, credits, shorts, override) }.getOrNull()
            if (r?.optBoolean("credit_guard") == true) { GuardAsk.ask = r.optString("error") to { makeNaatVideo(credits, shorts, true) }; return@launch }
            naatMsg = r?.optString("error")?.ifBlank { null } ?: if (r == null) strings.serverError
                else "🎬 Claude started the video (up to $credits credits). You'll get a notification when it's ready for your OK."
            openNaat(id)
        }
    }

    // ---- routines ----
    data class Step(val type: String, val arg: String)
    data class Routine(val id: String, val name: String, val emoji: String, val steps: List<Step>)
    var routines by mutableStateOf(listOf<Routine>()); private set
    var stepTypes by mutableStateOf(listOf<Pair<String, String>>()); private set
    var routineMsg by mutableStateOf<String?>(null)
    var editing by mutableStateOf<Routine?>(null)

    fun loadRoutines() {
        viewModelScope.launch {
            val r = runCatching { api.routines() }.getOrNull() ?: return@launch
            parseRoutines(r)
        }
    }

    private fun parseRoutines(r: org.json.JSONObject) {
        val a = r.optJSONArray("routines") ?: return
        routines = List(a.length()) { a.getJSONObject(it) }.map { o ->
            val st = o.optJSONArray("steps")
            Routine(o.optString("id"), o.optString("name"), o.optString("emoji"),
                st?.let { x -> List(x.length()) { x.getJSONObject(it).let { s -> Step(s.optString("type"), s.optString("arg")) } } } ?: emptyList())
        }
        r.optJSONObject("types")?.let { t -> stepTypes = t.keys().asSequence().map { it to t.optString(it) }.toList() }
    }

    fun runRoutine(r: Routine) {
        viewModelScope.launch {
            val res = runCatching { api.runRoutine(r.id) }.getOrNull()
            routineMsg = res?.optString("error")?.ifBlank { null } ?: if (res == null) strings.serverError
                else "${r.emoji} ${r.name} is running (${r.steps.size} steps). You'll get a notification when it's done."
        }
    }

    /** Saves [r] (new or edited); [delete] removes it. */
    fun saveRoutine(r: Routine, delete: Boolean = false) {
        val list = routines.filter { it.id != r.id } + if (delete) emptyList() else listOf(r)
        val arr = org.json.JSONArray().apply {
            (if (delete) list else routines.map { if (it.id == r.id) r else it }.let { if (routines.none { x -> x.id == r.id }) it + r else it }).forEach { x ->
                put(org.json.JSONObject().put("id", x.id).put("name", x.name).put("emoji", x.emoji).put("steps",
                    org.json.JSONArray().apply { x.steps.forEach { s -> put(org.json.JSONObject().put("type", s.type).put("arg", s.arg)) } }))
            }
        }
        viewModelScope.launch {
            val res = runCatching { api.saveRoutines(arr) }.getOrNull()
            if (res != null) { parseRoutines(res); routineMsg = if (delete) "Deleted ${r.name}" else "Saved ${r.name}" } else routineMsg = strings.serverError
        }
    }

    // ---- clipboard & files ----
    var transferMsg by mutableStateOf<String?>(null)
    var macFiles by mutableStateOf(listOf<org.json.JSONObject>()); private set
    var filePlace by mutableStateOf("From Phone")
    var fileSub by mutableStateOf("")
    var uploading by mutableStateOf<String?>(null); private set

    fun sendClipToMac() {
        val cm = getApplication<Application>().getSystemService(android.content.ClipboardManager::class.java)
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(getApplication())?.toString().orEmpty()
        if (text.isBlank()) { transferMsg = "Your phone's clipboard is empty"; return }
        viewModelScope.launch {
            pickRoute()
            val r = runCatching { api.setMacClip(text) }.getOrNull()
            transferMsg = if (r?.optBoolean("ok") == true) "Sent. Press ⌘V on the Mac to paste (${text.length} characters)" else strings.serverError
        }
    }

    fun getClipFromMac() {
        viewModelScope.launch {
            pickRoute()
            val t = runCatching { api.macClip() }.getOrNull()
            if (t == null) { transferMsg = strings.serverError; return@launch }
            if (t.isBlank()) { transferMsg = "The Mac's clipboard is empty"; return@launch }
            val cm = getApplication<Application>().getSystemService(android.content.ClipboardManager::class.java)
            cm.setPrimaryClip(android.content.ClipData.newPlainText("Mac", t))
            transferMsg = "Copied from the Mac. Paste it anywhere on the phone (${t.length} characters)"
        }
    }

    /** Sends files picked on the phone (or shared from another app) to the Mac's Downloads/From Phone. */
    fun sendFiles(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            uploading = "Connecting to the Mac…"
            if (!pickRoute()) { uploading = null; transferMsg = "Can't reach the Mac. Turn on Wi-Fi or Tailscale and share again."; return@launch }
            val cr = getApplication<Application>().contentResolver
            var ok = 0
            var why = ""
            for ((i, u) in uris.withIndex()) {
                val name = runCatching {
                    cr.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull() ?: "phone-file-${System.currentTimeMillis()}"
                uploading = "Sending ${i + 1}/${uris.size}: $name"
                val read = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { cr.openInputStream(u)?.use { it.readBytes() } } }
                val bytes = read.getOrNull()
                if (bytes == null) { why = "couldn't read $name (${read.exceptionOrNull()?.javaClass?.simpleName ?: "no access"})"; continue }
                val r = runCatching { api.upload(name, bytes) }
                if (r.getOrNull()?.optBoolean("ok") == true) ok++
                else why = "upload failed: " + (r.exceptionOrNull()?.message ?: r.getOrNull()?.optString("error") ?: "?")
            }
            uploading = null
            transferMsg = if (ok == uris.size) "✅ Sent $ok file${if (ok > 1) "s" else ""} to the Mac (Downloads › From Phone)"
                else "Sent $ok of ${uris.size}: $why"
            if (filePlace == "From Phone") loadFiles()
        }
    }

    /** Text shared from another app: put it on the Mac's clipboard. */
    fun sendTextToMac(text: String) {
        transferMsg = "Sending to the Mac…"
        viewModelScope.launch {
            if (!pickRoute()) { transferMsg = "Can't reach the Mac. Turn on Wi-Fi or Tailscale and share again."; return@launch }
            val r = runCatching { api.setMacClip(text) }
            transferMsg = if (r.getOrNull()?.optBoolean("ok") == true) "✅ Sent to the Mac's clipboard. Press ⌘V to paste"
                else "Failed: " + (r.exceptionOrNull()?.message ?: r.getOrNull()?.optString("error") ?: "unknown")
        }
    }

    fun loadFiles() {
        viewModelScope.launch {
            if (macOnline != true) pickRoute()
            val a = runCatching { api.fileList(filePlace, fileSub).optJSONArray("items") }.getOrNull()
            macFiles = a?.let { x -> List(x.length()) { x.getJSONObject(it) } } ?: emptyList()
        }
    }

    /** Saves a Mac file into the phone's Downloads folder, then offers to open it. */
    fun downloadFile(name: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            transferMsg = "Downloading $name…"
            val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/From Mac")
            }
            val uri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }
            if (uri == null) { transferMsg = "Couldn't save on the phone"; return@launch }
            val ok = runCatching {
                api.download(filePlace, fileSub, name) { input, _ -> app.contentResolver.openOutputStream(uri)?.use { out -> input.copyTo(out, 1 shl 16) } }
            }.getOrDefault(false)
            if (!ok) { app.contentResolver.delete(uri, null, null); transferMsg = "Download failed"; return@launch }
            transferMsg = "✅ Saved to Downloads › From Mac"
            runCatching {
                app.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, mime)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun revealOnMac(name: String) {
        viewModelScope.launch { runCatching { api.revealFile(filePlace, fileSub, name) }; transferMsg = "Shown in Finder on the Mac" }
    }

    var brief by mutableStateOf<org.json.JSONObject?>(null); private set
    fun loadBrief() { viewModelScope.launch { brief = runCatching { api.brief() }.onFailure { android.util.Log.w("Brief", "load failed", it) }.getOrNull()?.takeIf { !it.has("error") } ?: brief } }
    fun readBrief() {
        val b = brief ?: return
        val extra = " On your English practice, your streak is ${store.streak} days and you have ${store.xp} XP."
        voice.speak(b.optString("speech") + extra, Locale.US, flush = true)
    }
    fun saveBriefTime(time: String, on: Boolean) {
        viewModelScope.launch { runCatching { api.briefSettings(time, on) }; loadBrief() }
    }

    var keepAwake by mutableStateOf<Boolean?>(null); private set
    fun loadAwake() {
        viewModelScope.launch {
            val r = runCatching { api.awakeInfo() }.getOrNull() ?: return@launch
            keepAwake = r.optBoolean("on")
            r.optJSONObject("wake")?.let { prefs.edit().putString("wakeInfo", it.toString()).apply() }  // kept for when the Mac is asleep
        }
    }

    var waking by mutableStateOf<String?>(null); private set
    /** Sends Wake-on-LAN packets (home Wi-Fi broadcast + Tailscale), then waits up to a minute for the Mac to answer. */
    fun wakeMac() {
        val info = prefs.getString("wakeInfo", null)?.let { org.json.JSONObject(it) }
        if (info == null) { waking = "Open the Mac tab once while the Mac is on, so the app learns its network address."; return }
        viewModelScope.launch {
            waking = "Sending wake signal…"
            val n = runCatching { WakeOnLan.send(info) }.getOrDefault(0)
            if (n == 0) { waking = "Couldn't send the wake signal"; return@launch }
            for (i in 1..12) {
                waking = "Wake signal sent. Waiting for the Mac… ${i * 5}s"
                delay(5_000)
                if (pickRoute()) { macOnline = true; waking = "✅ Mac is awake"; refreshStats(); return@launch }
                if (i % 3 == 0) runCatching { WakeOnLan.send(info) }
            }
            waking = "No answer. Wake-on-LAN works best on home Wi-Fi; from outside, Tailscale can only wake it from light sleep."
        }
    }
    fun setAwake(on: Boolean) {
        keepAwake = on
        viewModelScope.launch { keepAwake = runCatching { api.setAwake(on).optBoolean("on") }.getOrNull() ?: !on }
    }

    var lockMsg by mutableStateOf<String?>(null)
    fun lockMac() {
        viewModelScope.launch {
            val r = runCatching { api.lockMac() }.getOrNull()
            if (r?.optBoolean("ok") == true) macLocked = true
            unlockSetup = false
            unlockMsg = r?.optString("result")?.ifBlank { null } ?: strings.serverError
        }
    }

    // ---- CV ----
    var resume by mutableStateOf<ResumeSummary?>(null); private set
    var resumeBusy by mutableStateOf(false); private set
    var resumeError by mutableStateOf<String?>(null); private set
    var useResume by mutableStateOf(prefs.getBoolean("useResume", true)); private set
    fun updateUseResume(on: Boolean) { useResume = on; prefs.edit().putBoolean("useResume", on).apply() }

    private fun parseResume(o: org.json.JSONObject?): ResumeSummary? = o?.let {
        val skills = it.optJSONArray("skills")?.let { a -> (0 until a.length()).map { i -> a.optString(i) } }.orEmpty()
        val projects = it.optJSONArray("projects")?.let { a -> (0 until a.length()).map { i -> a.getJSONObject(i) }
            .map { p -> p.optString("name") to p.optString("what") } }.orEmpty()
        ResumeSummary(it.optString("title"), it.optString("years"), skills, projects, it.optString("summary"))
    }

    fun loadResume() {
        if (!paired) return
        viewModelScope.launch { resume = runCatching { parseResume(api.resume().optJSONObject("summary")) }.getOrNull() }
    }

    fun uploadResume(pdf: ByteArray) {
        resumeBusy = true
        resumeError = null
        viewModelScope.launch {
            val r = runCatching { api.uploadResume(pdf) }.getOrNull()
            resumeBusy = false
            when {
                r == null -> resumeError = strings.serverError
                r.has("error") -> resumeError = r.optString("error")
                else -> { resume = parseResume(r.optJSONObject("summary")); updateUseResume(true) }
            }
        }
    }

    fun deleteResume() {
        viewModelScope.launch { runCatching { api.deleteResume() }; resume = null }
    }

    /** Interview, HR and your own roles ask about your real CV when one is uploaded and the switch is on. */
    private val resumeRoles = setOf("Interview", "HR", "Custom")

    // ---- CV interview: 10 planned questions from the CV, then a coach's report on every answer ----
    var cvPlanLoading by mutableStateOf(false); private set
    var cvFeedback by mutableStateOf<org.json.JSONObject?>(null); private set
    private var savedLength: Int? = null

    fun startCvInterview() {
        if (cvPlanLoading) return
        viewModelScope.launch {
            cvPlanLoading = true
            pickRoute()
            val p = runCatching { api.interviewPlan() }.getOrNull()
            cvPlanLoading = false
            val qs = p?.optJSONArray("questions")
            if (qs == null || qs.length() == 0) { notice = p?.optString("error")?.ifBlank { null } ?: strings.serverError; return@launch }
            val title = p.optString("title")
            savedLength = sessionLength
            sessionLength = qs.length()
            cvFeedback = null
            start(Role("CV Interview", "Hiring manager · $title",
                "${com.englishcoach.app.CV_MARK} a senior hiring manager interviewing the candidate for a $title role",
                qs.getJSONObject(0).optString("q"), Color(0xFF0F766E), Color(0xFFD1FAE5)))
        }
    }

    /** At the end of a CV interview: ask Claude for the full report and wait for it. */
    fun loadCvFeedback() {
        val sid = session
        viewModelScope.launch {
            cvFeedback = org.json.JSONObject().put("status", "working")
            runCatching { api.interviewFeedbackStart(sid) }
            repeat(60) {
                delay(5_000)
                val f = runCatching { api.interviewFeedback(sid) }.getOrNull() ?: return@repeat
                if (f.optString("status") != "working") { cvFeedback = f; savedLength?.let { sessionLength = it; savedLength = null }; return@launch }
            }
        }
    }

    // ---- storage cleaner ----
    var cleanItems by mutableStateOf<List<CleanItem>>(emptyList()); private set
    var cleanScanning by mutableStateOf(false); private set
    var cleanMessage by mutableStateOf<String?>(null); private set

    fun scanStorage() {
        cleanScanning = true
        cleanMessage = null
        viewModelScope.launch {
            runCatching { api.cleanupScan() }
            var restarts = 0
            while (true) {
                delay(2_000)
                val r = runCatching { api.cleanupResult() }.getOrNull() ?: continue
                val status = r.optString("status")
                if (status == "running") continue
                // the Mac restarted mid-scan (or never got the request): start again, a couple of times at most
                if (status == "idle" && restarts < 2) { restarts++; runCatching { api.cleanupScan() }; continue }
                if (r.has("error")) { cleanMessage = r.optString("error"); break }
                val arr = r.optJSONArray("items")
                cleanItems = arr?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map {
                    CleanItem(it.optString("kind"), it.optString("path"), it.optString("name"), it.optLong("bytes"),
                        if (it.isNull("last_used")) null else it.optLong("last_used"), it.optString("note"),
                        it.optString("verdict", "ask"), it.optString("reason"), it.optLong("growth"))
                } }.orEmpty()
                break
            }
            cleanScanning = false
        }
    }

    /** keep = never ask about it again · remove = move to the Trash · later = skip for now. */
    fun decide(item: CleanItem, decision: String) {
        cleanItems = when (decision) {
            "keep" -> cleanItems.map { if (it.path == item.path) it.copy(verdict = "keep", reason = strings.youKept) else it }
            "remove" -> cleanItems.filterNot { it.path == item.path }
            else -> cleanItems.filterNot { it.path == item.path } + item   // later: send to the back of the queue
        }
        viewModelScope.launch {
            runCatching { api.cleanupDecide(item.path, decision) }
            if (decision == "remove") { cleanMessage = strings.movedToTrash.format(1); refreshStats() }
        }
    }

    /** Loads the latest results the Mac already has (it scans daily by itself) without starting a new scan. */
    fun loadCleanup() {
        viewModelScope.launch {
            val r = runCatching { api.cleanupResult() }.getOrNull() ?: return@launch
            if (r.optString("status") == "done") {
                val arr = r.optJSONArray("items") ?: return@launch
                cleanItems = (0 until arr.length()).map { arr.getJSONObject(it) }.map {
                    CleanItem(it.optString("kind"), it.optString("path"), it.optString("name"), it.optLong("bytes"),
                        if (it.isNull("last_used")) null else it.optLong("last_used"), it.optString("note"),
                        it.optString("verdict", "ask"), it.optString("reason"), it.optLong("growth"))
                }
            } else if (cleanItems.isEmpty() && !cleanScanning) scanStorage()
        }
    }

    fun trash(items: List<CleanItem>) {
        viewModelScope.launch {
            val r = runCatching { api.cleanupTrash(items.map { it.path }) }.getOrNull()
            val moved = r?.optJSONArray("moved")?.length() ?: 0
            cleanItems = cleanItems.filterNot { c -> items.any { it.path == c.path } && moved > 0 }
            cleanMessage = strings.movedToTrash.format(moved)
            refreshStats()
        }
    }

    /**
     * Mouse/keyboard events go into ONE queue and are sent in order, several at a time, so on a slow link
     * keys never arrive twice or out of order. Plain letters typed one after another are joined into one "type".
     */
    private val inputQ = kotlinx.coroutines.channels.Channel<org.json.JSONObject>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    var inputPending by mutableStateOf(0); private set
    private val inputWorker = viewModelScope.launch {
        while (true) {
            val first = inputQ.receive()
            val evs = mutableListOf(first)
            while (evs.size < 60) evs += inputQ.tryReceive().getOrNull() ?: break
            val merged = mutableListOf<org.json.JSONObject>()
            for (e in evs) {
                val plain = e.optString("kind") == "key" && (e.optJSONArray("mods")?.length() ?: 0) == 0 &&
                    (e.optString("key").length == 1 || e.optString("key") == "space")
                val ch = if (e.optString("key") == "space") " " else e.optString("key")
                val last = merged.lastOrNull()
                if (plain && last != null && last.optString("kind") == "type") last.put("text", last.optString("text") + ch)
                else if (plain) merged += org.json.JSONObject().put("kind", "type").put("text", ch)
                else merged += e
            }
            runCatching { api.screenInput(org.json.JSONObject().put("display", display).put("batch", org.json.JSONArray(merged))) }
            inputPending = (inputPending - evs.size).coerceAtLeast(0)
        }
    }

    /** Queues one mouse/keyboard event for the Mac; see the server's screen_input for the kinds. */
    fun macInput(kind: String, vararg fields: Pair<String, Any>) {
        val o = org.json.JSONObject().put("kind", kind).put("display", display)
        fields.forEach { (k, v) -> o.put(k, if (v is List<*>) org.json.JSONArray(v) else v) }
        inputPending++
        inputQ.trySend(o)
    }

    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", false)); private set
    var level by mutableStateOf(prefs.getInt("level", 1)); private set
    var sessionLength by mutableStateOf(prefs.getInt("sessionLength", 8)); private set
    var speechRate by mutableStateOf(prefs.getFloat("speechRate", 1f)); private set
    var haptics by mutableStateOf(prefs.getBoolean("haptics", true)); private set
    var autoSpeak by mutableStateOf(prefs.getBoolean("autoSpeak", true)); private set
    /** true: answers go to Whisper on the Mac (understands mixed Urdu-English); false: the phone's English recognizer. */
    var useWhisper by mutableStateOf(prefs.getBoolean("whisper", false)); private set
    fun updateWhisper(on: Boolean) { useWhisper = on; prefs.edit().putBoolean("whisper", on).apply() }

    fun finishOnboarding() { onboarded = true; prefs.edit().putBoolean("onboarded", true).apply() }
    fun updateLevel(i: Int) { level = i; prefs.edit().putInt("level", i).apply() }
    fun updateSessionLength(n: Int) { sessionLength = n; prefs.edit().putInt("sessionLength", n).apply() }
    fun updateSpeechRate(r: Float) { speechRate = r; voice.rate = r; prefs.edit().putFloat("speechRate", r).apply() }
    fun updateHaptics(on: Boolean) { haptics = on; prefs.edit().putBoolean("haptics", on).apply() }
    fun updateAutoSpeak(on: Boolean) { autoSpeak = on; prefs.edit().putBoolean("autoSpeak", on).apply() }

    fun refreshStats() { viewModelScope.launch { macStats = api.stats(); macOnline = macStats != null } }

    fun resetProgress() { store.reset(); storeVersion++ }

    var screen by mutableStateOf(Screen.Home)
    var role by mutableStateOf(ROLES[0]); private set
    var phase by mutableStateOf(Phase.Ask); private set
    var question by mutableStateOf(""); private set
    var heard by mutableStateOf(""); private set
    var turn by mutableStateOf<TurnResult?>(null); private set
    var explanations by mutableStateOf<List<Explanation>>(emptyList()); private set
    var notice by mutableStateOf<String?>(null); private set
    var speaking by mutableStateOf(false); private set
    var micLevel by mutableStateOf(0f); private set
    var questionNumber by mutableStateOf(1); private set

    val store = Store(app)
    /** Bumped whenever [store] changes so screens that read it redraw. */
    var storeVersion by mutableStateOf(0); private set

    // ---- offline mode (6.2): a small model on the phone ----
    val offline = OfflineCoach(app)
    var offlinePref by mutableStateOf(prefs.getString("offlinePref", "auto")!!); private set   // auto | on | off
    var offlineReady by mutableStateOf(offline.downloaded); private set
    var offlineDl by mutableStateOf<String?>(null); private set
    var offlineActive by mutableStateOf(false); private set
    fun chooseOfflinePref(p: String) { offlinePref = p; prefs.edit().putString("offlinePref", p).apply() }

    fun downloadOffline() {
        if (offlineDl != null && !offlineDl!!.startsWith("⚠")) return
        viewModelScope.launch {
            offlineDl = "Checking the Mac…"
            pickRoute()
            val info = runCatching { api.offlineInfo() }.getOrNull()
            if (info == null || !info.optBoolean("ready")) { offlineDl = "⚠ The Mac is still getting the model. Try again in a few minutes."; return@launch }
            val total = info.optLong("bytes")
            var last = 0L
            val ok = offline.download(api, total) { have, tot ->
                val now = System.currentTimeMillis()
                if (now - last > 500) { last = now; offlineDl = "Downloading ${have * 100 / maxOf(tot, 1)}% · ${have / 1_000_000} of ${tot / 1_000_000} MB" }
            }
            offlineReady = offline.downloaded
            offlineDl = if (ok) null else "⚠ Download stopped at ${offline.partialBytes / 1_000_000} MB. Tap again to continue."
        }
    }

    fun deleteOffline() { offline.delete(); offlineReady = false }

    // ---- progress backup to the Mac (6.1) ----
    var backupInfo by mutableStateOf(prefs.getString("backupInfo", "") ?: ""); private set
    private var backupJob: Job? = null
    init {
        store.onSaved = { scheduleBackup() }
        viewModelScope.launch {            // fresh install / cleared app: bring the progress back from the Mac
            delay(4_000)
            if (paired && store.isEmpty) restoreBackup(auto = true)
        }
    }

    /** A few seconds after any change, send the progress to the Mac (quietly; it retries on the next change). */
    private fun scheduleBackup() {
        if (!paired) return
        backupJob?.cancel()
        backupJob = viewModelScope.launch {
            delay(8_000)
            val r = runCatching { api.backup(store.exportJson()) }.getOrNull()
            if (r?.optBoolean("ok") == true) {
                backupInfo = "Saved to the Mac ${r.optString("saved")} · ${r.optInt("answers")} answers, ${r.optInt("mistakes")} mistakes"
                prefs.edit().putString("backupInfo", backupInfo).apply()
            }
        }
    }

    fun backupNow() { scheduleBackup() }

    fun restoreBackup(auto: Boolean = false) {
        viewModelScope.launch {
            val r = runCatching { api.getBackup() }.getOrNull()
            val p = r?.optJSONObject("progress")
            if (p == null) { if (!auto) notice = "No backup on the Mac yet"; return@launch }
            store.onSaved = null
            store.importJson(p.toString())
            store.onSaved = { scheduleBackup() }
            storeVersion++
            notice = "✅ Progress restored from the Mac (saved ${r.optString("saved")})"
        }
    }
    var sessionMistakes by mutableStateOf<List<Mistake>>(emptyList()); private set
    var sessionAnswers by mutableStateOf(0); private set
    var reviewItem by mutableStateOf<Mistake?>(null); private set
    var reviewPassed by mutableStateOf<Boolean?>(null); private set

    val strings get() = Strings(roman)
    private var session = UUID.randomUUID().toString()
    private var retrying = false
    private var pollJob: Job? = null

    init {
        voice.onSpeaking = { speaking = it }
        voice.onLevel = { micLevel = it }
        discoverMac(app)
        // re-check the route whenever the phone switches networks (home Wi-Fi <-> mobile data)
        (app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager)
            .registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) = checkServer()
                override fun onLost(network: android.net.Network) = checkServer()
            })
        voice.rate = speechRate
        checkServer()
    }

    fun setRomanUrdu(on: Boolean) {
        roman = on
        prefs.edit().putBoolean("roman", on).apply()
    }

    fun saveServer(url: String) {
        serverUrl = normalizeServer(url) ?: url.trim()
        prefs.edit().putString("server", serverUrl).apply()
        checkServer()
    }

    private var reconnecting: Job? = null

    fun checkServer() {
        viewModelScope.launch {
            macOnline = pickRoute()
            if (macOnline == true && paired && resume == null) loadResume()   // the CV Interview card needs it
            // offline (Mac restarting, Wi-Fi blip): keep trying quietly every 8 s until it's back
            if (macOnline == false && reconnecting?.isActive != true) reconnecting = viewModelScope.launch {
                while (macOnline != true) {
                    delay(8_000)
                    macOnline = pickRoute()
                }
            }
        }
    }

    fun start(r: Role) {
        role = r
        session = UUID.randomUUID().toString()
        questionNumber = 1
        sessionMistakes = emptyList()
        sessionAnswers = 0
        screen = Screen.Practice
        ask(r.opening)
    }

    fun leave() {
        voice.stopSpeaking()
        voice.stopListening()
        pollJob?.cancel()
        screen = Screen.Home
    }

    private fun ask(q: String) {
        if (questionNumber > sessionLength) {
            screen = Screen.Report
            voice.speak(strings.sessionDone, flush = true)
            return
        }
        question = q
        pron = null
        heard = ""
        turn = null
        explanations = emptyList()
        notice = null
        retrying = false
        phase = Phase.Ask
        voice.speak(q, flush = true)
    }

    fun mic() {
        if (phase == Phase.Listening) { stopHearing(); return }
        notice = null
        heard = ""
        phase = Phase.Listening
        hear(onPartial = { heard = it }) { text ->
            micLevel = 0f
            if (text.isNullOrBlank()) {
                notice = strings.didntHear
                phase = if (retrying) Phase.Sheet else Phase.Ask
            } else {
                heard = text
                if (retrying) checkRetry(text) else send(text)
            }
        }
    }

    private fun send(text: String) {
        phase = Phase.Thinking
        viewModelScope.launch {
            var spokeCorrect = false
            var praised = false
            val prompt = "${role.prompt}. Speak to a ${LEVELS[level].lowercase()} English learner."
            val useOffline = offlineReady && (offlinePref == "on" || (offlinePref == "auto" && macOnline == false))
            val r = (if (useOffline) offline.turn(role.prompt.removePrefix(CV_MARK).trim(), question, text)?.also { offlineActive = true } else null) ?: runCatching {
                api.turnStream(session, prompt, text, (useResume && resume != null && role.title in resumeRoles) || role.title == "CV Interview", question) { kind, value ->
                    // early events: react while the rest of the answer is still being written on the Mac
                    when {
                        kind == "mistake" && value == "false" && !praised -> {
                            praised = true
                            phase = Phase.Praise
                            voice.speak(strings.praise, flush = true)
                        }
                        kind == "correct" && phase == Phase.Thinking -> {
                            turn = TurnResult(true, value, "", "", "", false, emptyList(), emptyList())
                            explanations = emptyList()
                            phase = Phase.Sheet
                            if (autoSpeak) { voice.speak(value, flush = true); spokeCorrect = true }
                        }
                    }
                }
            }.getOrElse {
                macOnline = false
                checkServer()
                // the Mac didn't answer: use the model on the phone if it's there
                val local = if (offlineReady && offlinePref != "off") offline.turn(role.prompt.removePrefix(CV_MARK).trim(), question, text) else null
                if (local == null) { notice = strings.serverError; phase = Phase.Ask; return@launch }
                offlineActive = true
                notice = "📴 Offline mode: the phone answered (the Mac isn't reachable)"
                local
            }.also { if (!useOffline && macOnline != false) offlineActive = false }
            turn = r
            store.recordAnswer()
            sessionAnswers++
            if (r.hasMistake) {
                store.addMistake(text, r)
                sessionMistakes = sessionMistakes + store.mistakes.first()
            }
            storeVersion++
            if (!r.hasMistake) {
                celebrate(r.nextQuestion, alreadyPraised = praised)
                return@launch
            }
            explanations = r.explanations
            phase = Phase.Sheet
            if (autoSpeak) {
                if (!spokeCorrect) voice.speak(r.correct, flush = true)
                speakExplanations()
            }
            if (r.pending) poll(r.explainId)
        }
    }

    private fun poll(id: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            repeat(40) {
                delay(2_000)
                val ex = runCatching { api.explanation(id) }.getOrNull() ?: return@repeat
                explanations = ex
                speakExplanations()
                return@launch
            }
        }
    }

    private fun speakExplanations() {
        val first = explanations.firstOrNull() ?: return
        if (roman) voice.speak(first.urdu, Voice.URDU) else voice.speak(first.en, Locale.US)
    }

    fun replay() {
        val r = turn ?: return
        voice.speak(r.correct, flush = true)
        speakExplanations()
    }

    /**
     * "Say it again": record the learner saying the corrected sentence and score each word on the Mac.
     * Falls back to the phone recognizer's text check when the Mac can't score it.
     */
    fun retry() {
        val target = turn?.correct ?: return
        if (phase == Phase.Listening) { stopHearing(); return }
        retrying = true
        notice = null
        pron = null
        phase = Phase.Listening
        voice.stopSpeaking()
        voice.stopListening()
        recording = viewModelScope.launch {
            delay(150)
            val wav = recorder.record()
            micLevel = 0f
            if (wav == null) { notice = if (recorder.lastProblem == "mic") strings.micBusy else strings.didntHear; phase = Phase.Sheet; return@launch }
            pronChecking = true
            phase = Phase.Sheet
            val r = api.pronounce(wav, target)
            pronChecking = false
            if (r == null) { notice = strings.serverError; checkServer(); return@launch }
            pron = r
            val missing = r.words.any { it.score == 0 }
            if (r.overall >= 70 && !missing) {
                store.recordFix()
                storeVersion++
                celebrate(turn?.nextQuestion.orEmpty())
            } else {
                notice = strings.tryAgain
                r.words.firstOrNull { it.level == "bad" }?.let { voice.speak(it.word, flush = true) }
            }
        }
    }

    /** Says one word slowly, for the tap-a-word help on the pronunciation card. */
    fun sayWord(word: String) {
        val old = speechRate
        voice.rate = 0.75f
        voice.speak(word, flush = true)
        voice.rate = old
    }

    fun next() {
        val r = turn ?: return
        questionNumber++
        ask(r.nextQuestion)
    }

    private fun checkRetry(text: String) {
        val target = turn?.correct.orEmpty()
        if (similarity(text, target) >= 0.8) {
            store.recordFix()
            storeVersion++
            celebrate(turn?.nextQuestion.orEmpty())
        } else {
            notice = strings.tryAgain
            phase = Phase.Sheet
        }
    }

    private fun celebrate(nextQ: String, alreadyPraised: Boolean = false) {
        phase = Phase.Praise
        if (!alreadyPraised) voice.speak(strings.praise.removeSuffix("!") + "!", flush = true)
        viewModelScope.launch {
            delay(if (alreadyPraised) 900 else 1_800)
            questionNumber++
            ask(nextQ)
        }
    }

    /** Review mode: say each saved mistake correctly; two passes in a row and it leaves the notebook. */
    fun startReview() {
        reviewPassed = null
        reviewItem = store.dueForReview.firstOrNull()
        screen = Screen.Review
        reviewItem?.let { voice.speak(strings.reviewPrompt, flush = true) }
    }

    fun reviewMic() {
        val item = reviewItem ?: return
        if (phase == Phase.Listening) { stopHearing(); return }
        heard = ""
        reviewPassed = null
        phase = Phase.Listening
        hear(onPartial = { heard = it }) { text ->
            micLevel = 0f
            phase = Phase.Ask
            if (text.isNullOrBlank()) { notice = strings.didntHear; return@hear }
            notice = null
            heard = text
            val ok = similarity(text, item.correct) >= 0.8
            reviewPassed = ok
            store.reviewed(item, ok)
            storeVersion++
            if (ok) {
                voice.speak(strings.praise, flush = true)
                viewModelScope.launch {
                    delay(1_400)
                    reviewPassed = null
                    heard = ""
                    reviewItem = store.dueForReview.firstOrNull { it.correct != item.correct } ?: store.dueForReview.firstOrNull()
                }
            } else {
                voice.speak(item.correct, flush = true)
            }
        }
    }

    fun skipReview() {
        val item = reviewItem ?: return
        reviewPassed = null
        heard = ""
        val due = store.dueForReview
        reviewItem = due.getOrNull((due.indexOf(item) + 1) % due.size.coerceAtLeast(1))
    }

    fun hearReview() { reviewItem?.let { voice.speak(it.correct, flush = true) } }

    override fun onCleared() = voice.release()

    companion object {
        /** No default Mac: the user types their Mac's Tailscale address on the pairing card (or in Settings). */
        const val DEFAULT_SERVER = ""
        /** A fixed home-network address for the Mac (null: Bonjour finds it on the home Wi-Fi). */
        val HOME_URL: String? = null

        /** "100.1.2.3" or "mac.tailnet.ts.net" -> "http://100.1.2.3:8787"; blank -> null. */
        fun normalizeServer(raw: String): String? {
            var u = raw.trim().trimEnd('/')
            if (u.isBlank()) return null
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            if (!Regex("^https?://[^/]+:\\d+").containsMatchIn(u)) u = Regex("^(https?://[^/]+)").replace(u) { it.value + ":8787" }
            return u
        }

        /** Home-network address ranges (192.168.x, 10.x, 172.16-31.x). */
        fun isPrivateLan(ip: String): Boolean = ip.startsWith("192.168.") || ip.startsWith("10.") ||
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(ip)

        /** Share of words in common, ignoring case and punctuation. */
        fun similarity(a: String, b: String): Double {
            fun words(s: String) = s.lowercase().replace(Regex("[^a-z0-9' ]"), " ").split(" ").filter { it.isNotBlank() }.toSet()
            val x = words(a)
            val y = words(b)
            if (x.isEmpty() || y.isEmpty()) return 0.0
            return (x intersect y).size.toDouble() / (x union y).size
        }
    }
}
