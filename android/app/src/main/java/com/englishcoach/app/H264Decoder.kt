package com.englishcoach.app

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface

/** Hardware H.264 decoder that draws straight onto [surface] (the Mac screen in the Remote view). */
class H264Decoder(surface: Surface, width: Int, height: Int, config: ByteArray) {
    private val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val info = MediaCodec.BufferInfo()
    private var pts = 0L
    var frames = 0; private set

    init {
        val f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        if (android.os.Build.VERSION.SDK_INT >= 30) f.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        codec.configure(f, surface, null, 0)
        codec.start()
        feed(config, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
    }

    fun feed(data: ByteArray, flags: Int = 0) {
        val i = codec.dequeueInputBuffer(20_000)
        if (i >= 0) {
            codec.getInputBuffer(i)?.apply { clear(); put(data) }
            codec.queueInputBuffer(i, 0, data.size, pts, flags)
            pts += 40_000
        }
        while (true) {
            val o = codec.dequeueOutputBuffer(info, 0)
            if (o < 0) break
            codec.releaseOutputBuffer(o, true); frames++
        }
    }

    fun release() = runCatching { codec.stop(); codec.release() }
}
