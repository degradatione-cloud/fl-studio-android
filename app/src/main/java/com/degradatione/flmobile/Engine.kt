package com.degradatione.flmobile

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

/** WAV 16-bit stereo — чтобы экспорт открывался везде. */
object Wav {
    fun write(file: File, pcm: ShortArray, sampleRate: Int) {
        val dataSize = pcm.size * 2
        val out = FileOutputStream(file)
        fun le32(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
        )
        fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
        out.write("RIFF".toByteArray())
        out.write(le32(36 + dataSize))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(le32(16))
        out.write(le16(1))              // PCM
        out.write(le16(2))              // stereo
        out.write(le32(sampleRate))
        out.write(le32(sampleRate * 2 * 2))
        out.write(le16(4))
        out.write(le16(16))
        out.write("data".toByteArray())
        out.write(le32(dataSize))
        val bytes = ByteArray(pcm.size * 2)
        for (i in pcm.indices) {
            val v = pcm[i].toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        out.write(bytes)
        out.close()
    }
}

/**
 * Секвенсор: 16 шагов, живые голоса на каждый канал, потоковый AudioTrack.
 * Тот же код умеет рендерить офлайн — для экспорта WAV.
 */
class Player(val pattern: Pattern) {

    companion object {
        const val SR = 44100
        const val STEPS = Track.STEPS
    }

    @Volatile var playing = false
        private set
    @Volatile var currentStep = -1
        private set
    var onStep: ((Int) -> Unit)? = null

    private val voices = HashMap<Int, Voice>()
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    private fun stepSamples(): Int = (SR * 60.0 / pattern.bpm / 4.0).toInt().coerceAtLeast(64)

    private fun voiceOf(t: Track): Voice = voices.getOrPut(t.id) { Voice() }

    private fun fireStep(step: Int) {
        currentStep = step
        for (t in pattern.tracks) {
            if (t.mute || !t.steps[step]) continue
            voiceOf(t).trigger(t.inst, t.pitch, t.decay)
        }
        onStep?.invoke(step)
    }

    /** Возвращает пару (L, R) для одного сэмпла. */
    private fun mixSample(): Pair<Double, Double> {
        var l = 0.0
        var r = 0.0
        for (t in pattern.tracks) {
            val v = voices[t.id] ?: continue
            if (!v.active) continue
            val s = v.next(SR.toDouble()) * t.gain
            val pan = t.pan.coerceIn(-1f, 1f)
            l += s * (1.0 - pan.coerceAtLeast(0f)) * 0.7
            r += s * (1.0 + pan.coerceAtMost(0f)) * 0.7
        }
        val m = if (pattern.muted) 0.0 else pattern.master.toDouble()
        return softClip(l * m * 1.6) to softClip(r * m * 1.6)
    }

    fun start(): Boolean {
        if (playing) return true
        val minBytes = AudioTrack.getMinBufferSize(
            SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        val at = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBytes * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (t: Throwable) {
            lastError = "AudioTrack: ${t.message}"
            return false
        }
        track = at
        at.play()
        playing = true
        thread = Thread {
            val frames = 512
            val buf = ShortArray(frames * 2)
            var step = 0
            var inStep = 0
            var stepLen = stepSamples()
            while (playing) {
                val sl = stepSamples()
                if (sl != stepLen) stepLen = sl
                for (i in 0 until frames) {
                    if (inStep <= 0) {
                        fireStep(step)
                        step = (step + 1) % STEPS
                        inStep = stepLen
                    }
                    val (l, r) = mixSample()
                    buf[i * 2] = (l * 32000).toInt().coerceIn(-32768, 32767).toShort()
                    buf[i * 2 + 1] = (r * 32000).toInt().coerceIn(-32768, 32767).toShort()
                    inStep--
                }
                try {
                    at.write(buf, 0, buf.size)
                } catch (t: Throwable) {
                    lastError = "write: ${t.message}"
                    break
                }
            }
        }.apply {
            name = "fl-audio"
            priority = Thread.MAX_PRIORITY
            start()
        }
        return true
    }

    @Volatile var lastError: String? = null

    fun stop() {
        playing = false
        thread?.join(400)
        thread = null
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
        currentStep = -1
        voices.clear()
    }

    /** Офлайн-рендер N шагов в стерео-буфер. */
    fun render(steps: Int): ShortArray {
        voices.clear()
        val stepLen = stepSamples()
        val total = stepLen * steps
        val out = ShortArray(total * 2)
        var step = 0
        var inStep = 0
        for (i in 0 until total) {
            if (inStep <= 0) {
                for (t in pattern.tracks) {
                    if (t.mute || !t.steps[step]) continue
                    voiceOf(t).trigger(t.inst, t.pitch, t.decay)
                }
                step = (step + 1) % STEPS
                inStep = stepLen
            }
            val (l, r) = mixSample()
            out[i * 2] = (l * 32000).toInt().coerceIn(-32768, 32767).toShort()
            out[i * 2 + 1] = (r * 32000).toInt().coerceIn(-32768, 32767).toShort()
            inStep--
        }
        return out
    }

    fun exportWav(file: File, bars: Int = 2): File {
        val pcm = render(STEPS * bars)
        Wav.write(file, pcm, SR)
        return file
    }

    /** Есть ли вообще звук в паттерне (для подсказок UI). */
    fun hasAnyStep(): Boolean = pattern.tracks.any { t -> t.steps.any { it } }

    fun previewNote(t: Track) {
        val v = Voice()
        v.trigger(t.inst, t.pitch, t.decay)
        srcPreview = v
    }

    @Volatile private var srcPreview: Voice? = null
}