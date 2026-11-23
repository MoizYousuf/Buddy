package com.englishcoach.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Wraps Android speech recognition (listening) and text-to-speech (speaking). */
class Voice(context: Context) {

    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val queue = ArrayDeque<Pair<String, Locale>>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Called with true while the coach is speaking, so the character can move its mouth. */
    var onSpeaking: (Boolean) -> Unit = {}

    /** Mic loudness while listening, 0 (silence) to 1 (loud), for the voice wave. */
    var onLevel: (Float) -> Unit = {}

    /** Speaking speed, 1.0 = normal. */
    var rate = 1f

    init {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = onSpeaking(true)
                override fun onDone(id: String?) { if (id == "last") onSpeaking(false) }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) = onSpeaking(false)
            })
            while (queue.isNotEmpty()) queue.removeFirst().let { speak(it.first, it.second) }
        }
    }

    /** What the app said recently, so the mic can ignore the app's own voice (echo). */
    private val recentSpeech = ArrayDeque<Pair<Long, String>>()
    private fun words(t: String) = t.lowercase().split(Regex("[^a-z0-9']+")).filter { it.length > 1 }

    /** True when [heard] is mostly words the app itself spoke in the last 15 s (the mic picked up the speaker). */
    fun isEcho(heard: String): Boolean {
        val now = System.currentTimeMillis()
        while (recentSpeech.isNotEmpty() && now - recentSpeech.first().first > 15_000) recentSpeech.removeFirst()
        val h = words(heard); if (h.size < 2) return false
        val said = recentSpeech.flatMap { words(it.second) }.toSet()
        return h.count { it in said } >= (h.size * 0.7)
    }

    fun speak(text: String, locale: Locale = Locale.US, flush: Boolean = false) {
        if (text.isBlank()) return
        recentSpeech.addLast(System.currentTimeMillis() to text)
        if (recentSpeech.size > 12) recentSpeech.removeFirst()
        val t = tts ?: return
        if (!ttsReady) { queue.addLast(text to locale); return }
        t.language = locale
        t.setSpeechRate(rate)
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        t.speak(text, mode, null, "last")
    }

    fun stopSpeaking() { tts?.stop(); onSpeaking(false) }

    /** Listens once. [onPartial] streams words as they're heard; [onResult] gets the final text or null. */
    fun listen(onPartial: (String) -> Unit, onResult: (String?) -> Unit) {
        stopSpeaking()
        recognizer.cancel()  // drop any session still finishing, so its late callbacks can't reach this one
        // The recognizer can send an error *after* the result (seen on the S24). Deliver exactly once,
        // and if it errors after hearing something, use the last words it heard instead of "didn't catch that".
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
        var delivered = false
        var lastPartial = ""
        var heardSpeech = false
        var retries = 0
        fun deliver(text: String?) {
            if (delivered) return
            delivered = true
            onResult(text?.ifBlank { null }?.takeUnless { isEcho(it) })   // the app's own voice is not an answer
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) =
                deliver(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.ifBlank { null } ?: lastPartial)
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                    lastPartial = it
                    if (!delivered) onPartial(it)
                }
            }
            override fun onError(error: Int) {
                // Right after the coach speaks, Google's speech service (shared with text-to-speech) can refuse
                // to start: busy / client / "no match" before any speech. Quietly try again instead of failing.
                val startupError = !heardSpeech && lastPartial.isBlank() && error in setOf(
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SERVER_DISCONNECTED)
                if (startupError && retries < 3 && !delivered) {
                    retries++
                    recognizer.cancel()
                    main.postDelayed({ if (!delivered) recognizer.startListening(intent) }, 350L * retries)
                    return
                }
                deliver(lastPartial)
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { heardSpeech = true }
            override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        main.postDelayed({ if (!delivered) recognizer.startListening(intent) }, 250)  // let the speech service settle after TTS
    }

    fun stopListening() = recognizer.stopListening()

    fun release() {
        recognizer.destroy()
        tts?.shutdown()
    }

    companion object {
        val URDU: Locale = Locale.forLanguageTag("ur-PK")
    }
}
