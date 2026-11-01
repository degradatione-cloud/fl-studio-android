package com.degradatione.flmobile

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

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
        out.write(le16(1))
        out.write(le16(2))
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
 * Секвенсор: шаги (до 4 тактов), свинг, акценты, соло, посылы в дилей/реверб.
 * Тот же код рендерит офлайн — для WAV.
 */
class Player(val pattern: Pattern) {

    companion object {
        const val SR = 44100
    }

    @Volatile var playing = false
        private set
    @Volatile var currentStep = -1
        private set
    @Volatile var lastError: String? = null

    var onStep: ((Int) -> Unit)? = null

    private val voices = HashMap<Int, Voice>()
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private val fx = Fx(SR)

    fun stepSamples(): Int = (SR * 60.0 / pattern.bpm / 4.0).toInt().coerceAtLeast(64)

    private fun voiceOf(t: Track): Voice = voices.getOrPut(t.id) { Voice() }

    private fun audible(t: Track): Boolean {
        val soloExists = pattern.tracks.any { it.solo }
        return if (soloExists) t.solo else !t.mute
    }

    private fun fireStep(step: Int) {
        currentStep = step
        for (t in pattern.tracks) {
            if (!audible(t) || !t.steps[step]) continue
            val vel = if (t.accents[step]) 1f else 0.62f
            voiceOf(t).trigger(t.inst, t.patch, t.pitch, vel)
        }
        onStep?.invoke(step)
    }

    private fun mixSample(): Pair<Double, Double> {
        var l = 0.0
        var r = 0.0
        var send = 0.0
        for (t in pattern.tracks) {
            if (!audible(t)) continue
            val v = voices[t.id] ?: continue
            val s = v.next(SR.toDouble()) * t.gain
            val pan = t.pan.coerceIn(-1f, 1f)
            l += s * (1.0 - pan.coerceAtLeast(0f)) * 0.7
            r += s * (1.0 + pan.coerceAtMost(0f)) * 0.7
            send += s * t.patch.send
        }
        val (wl, wr) = fx.process(send * 0.5, SR)
        l += wl
        r += wr
        val m = if (pattern.muted) 0.0 else pattern.master.toDouble()
        return softClip(l * m * 1.5) to softClip(r * m * 1.5)
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
            while (playing) {
                val stepLen = stepSamples()
                val swingSamples = (stepLen * pattern.swing.coerceIn(0f, 0.6f) * 0.5f).toInt()
                for (i in 0 until frames) {
                    if (inStep <= 0) {
                        fireStep(step)
                        val swingHere = if (step % 2 == 0) stepLen + swingSamples else stepLen - swingSamples
                        step = (step + 1) % pattern.steps
                        inStep = swingHere.coerceAtLeast(32)
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
                    if (!audible(t) || !t.steps[step]) continue
                    val vel = if (t.accents[step]) 1f else 0.62f
                    voiceOf(t).trigger(t.inst, t.patch, t.pitch, vel)
                }
                step = (step + 1) % steps
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
        val actualBars = bars.coerceAtMost(pattern.bars)
        val pcm = render(pattern.steps * actualBars)
        Wav.write(file, pcm, SR)
        return file
    }

    fun hasAnyStep(): Boolean = pattern.anySteps()

    /** Короткое прослушивание патча (для листа пресетов). */
    fun preview(inst: Inst, patch: Patch, pitch: Float = 0f) {
        runCatching {
            val n = (SR * 0.7).toInt()
            val buf = ShortArray(n * 2)
            val v = Voice()
            v.trigger(inst, patch, pitch, 1f)
            for (i in 0 until n) {
                var s = v.next(SR.toDouble())
                s = softClip(s * 0.8)
                buf[i * 2] = (s * 30000).toInt().coerceIn(-32768, 32767).toShort()
                buf[i * 2 + 1] = (s * 30000).toInt().coerceIn(-32768, 32767).toShort()
            }
            val at = AudioTrack.Builder()
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
                .setBufferSizeInBytes(buf.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            at.write(buf, 0, buf.size)
            at.play()
            Thread {
                Thread.sleep(900)
                runCatching { at.stop(); at.release() }
            }.apply { isDaemon = true }.start()
        }
    }

    fun peak(pcm: ShortArray): Int = pcm.maxOfOrNull { abs(it.toInt()) } ?: 0
}