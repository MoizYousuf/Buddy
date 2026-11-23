package com.englishcoach.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Records one answer as a 16 kHz mono WAV for Whisper on the Mac.
 * Learns the room's noise in the first moment, then stops by itself after [silenceMs] of quiet once
 * the learner has spoken, when [stop] is called, after [maxMs], or after [giveUpMs] if nobody speaks.
 * [onLevel] gets 0..1 loudness for the voice wave.
 */
class Recorder(private val onLevel: (Float) -> Unit) {
    @Volatile private var stopRequested = false

    /** Why the last [record] returned null, for a helpful message. */
    var lastProblem: String? = null; private set

    fun stop() { stopRequested = true }

    @SuppressLint("MissingPermission")  // RECORD_AUDIO is requested at app start
    suspend fun record(silenceMs: Long = 1000, maxMs: Long = 15_000, giveUpMs: Long = 8_000): ByteArray? = withContext(Dispatchers.IO) {
        stopRequested = false
        lastProblem = null
        val rate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // MIC (not VOICE_RECOGNITION): keeps the phone's gain/AGC, so normal speaking volume is loud enough
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate))
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); lastProblem = "mic"; return@withContext null }
        val pcm = ByteArrayOutputStream()
        val buf = ShortArray(rate / 20)  // 50 ms chunks
        var spoke = false
        var quietMs = 0L
        var noise = 1f                  // quietest level heard so far = the room's noise floor
        var peak = 0f                   // loudest level heard = the learner's voice
        var failures = 0
        val start = System.currentTimeMillis()
        rec.startRecording()
        try {
            while (coroutineContext.isActive && !stopRequested) {
                val elapsed = System.currentTimeMillis() - start
                if (elapsed > maxMs || (!spoke && elapsed > giveUpMs)) break
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) { if (++failures > 20) { lastProblem = "mic"; break }; continue }
                val bytes = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
                var sum = 0.0
                for (i in 0 until n) { bytes.putShort(buf[i]); sum += buf[i].toDouble() * buf[i] }
                pcm.write(bytes.array())
                val level = ((20 * log10(sqrt(sum / n) + 1) - 30) / 50).toFloat().coerceIn(0f, 1f)
                onLevel(level)
                // noise floor = quietest non-empty chunk (it may creep up a little if the room gets louder);
                // speech = clearly above it; silence = back near the floor compared with how loud the voice was
                if (level > 0.001f) noise = minOf(level, noise * 0.998f + level * 0.002f)
                peak = maxOf(peak, level)
                val chunkMs = n * 1000L / rate
                val quietLine = noise + maxOf(0.05f, (peak - noise) * 0.35f)
                if (level > noise + 0.1f && elapsed > 300) { spoke = true; quietMs = 0 }
                else if (spoke && level < quietLine) quietMs += chunkMs
                if (spoke && quietMs >= silenceMs) break
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
            onLevel(0f)
        }
        when {
            spoke || (stopRequested && pcm.size() > rate) -> wav(pcm.toByteArray(), rate)  // a manual stop keeps what was said
            else -> { if (lastProblem == null) lastProblem = "silence"; null }
        }
    }

    private fun wav(pcm: ByteArray, rate: Int): ByteArray {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        h.put("data".toByteArray()).putInt(pcm.size)
        return h.array() + pcm
    }
}
