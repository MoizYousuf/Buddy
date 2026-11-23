package com.englishcoach.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Keeps listening until the owner taps Stop: when the phone's recognizer ends on a pause it is started again,
 * and every piece of speech is added to the text. Pauses for thinking don't end it.
 */
class LongListen(private val ctx: Context, private val onText: (String) -> Unit, private val onPartial: (String) -> Unit, private val onLevel: (Float) -> Unit = {}) {
    private var rec: SpeechRecognizer? = null
    var active = false; private set

    fun start() {
        if (active) return
        active = true
        next()
    }

    fun stop() {
        active = false
        runCatching { rec?.stopListening(); rec?.destroy() }
        rec = null
        onPartial("")
    }

    private fun next() {
        if (!active) return
        runCatching { rec?.destroy() }
        val r = SpeechRecognizer.createSpeechRecognizer(ctx); rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onText)
                onPartial(""); next()
            }
            override fun onPartialResults(b: Bundle?) { b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial) }
            override fun onError(e: Int) { if (active) android.os.Handler(ctx.mainLooper).postDelayed({ next() }, 250) }
            override fun onRmsChanged(v: Float) { onLevel(((v + 2f) / 12f).coerceIn(0f, 1f)) }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(p: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")          // writes Roman Urdu / Hinglish words in English letters
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L))
    }
}
