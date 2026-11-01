package com.degradatione.flmobile

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

enum class Inst(val label: String, val tonal: Boolean) {
    KICK("Kick", false),
    SNARE("Snare", false),
    HAT("Hat", false),
    CLAP("Clap", false),
    TOM("Tom", false),
    BASS("Bass", true),
    LEAD("Lead", true),
    PAD("Pad", true),
    PLUCK("Pluck", true),
    KEYS("Keys", true);

    companion object {
        fun byName(n: String?): Inst = entries.firstOrNull { it.name == n } ?: KICK
    }
}

enum class Wave(val label: String) {
    SINE("Sine"), SAW("Saw"), SQUARE("Square"), TRI("Tri"), NOISE("Noise");
}

/** Патч одного канала: 9 крутилок, как в синтезаторе. */
class Patch(
    var wave: Wave = Wave.SAW,
    var cutoff: Float = 0.45f,
    var res: Float = 0.15f,
    var attack: Float = 0.004f,
    var decay: Float = 0.30f,
    var drive: Float = 0f,
    var crush: Float = 0f,
    var pitchEnv: Float = 0f,
    var send: Float = 0.15f,
) {
    fun copy(): Patch = Patch(wave, cutoff, res, attack, decay, drive, crush, pitchEnv, send)

    fun describe(): String =
        "${wave.label}·C${(cutoff * 100).roundToInt()}·R${(res * 100).roundToInt()}·D${(decay * 100).roundToInt()}"
}

/**
 * Голос: генератор → SVF-фильтр → драйв → биткраш → огибающая.
 * Барабаны сохраняют свой характер (свип у кика, хайпасс у хэта).
 */
class Voice {

    private val patch = Patch()
    private var inst = Inst.KICK
    private var pitch = 0f
    private var phase = 0.0
    private var phase2 = 0.0
    private var phase3 = 0.0
    private var env = 0.0
    private var t = 0.0
    private var seed = 0x2545F491
    private var low = 0.0
    private var band = 0.0
    private var prevNoise = 0.0
    private var vel = 1.0

    fun trigger(inst: Inst, patch: Patch, pitch: Float, velocity: Float = 1f) {
        vel = velocity.coerceIn(0.05f, 1.5f).toDouble()
        this.inst = inst
        this.patch.wave = patch.wave
        this.patch.cutoff = patch.cutoff
        this.patch.res = patch.res
        this.patch.attack = patch.attack
        this.patch.decay = patch.decay
        this.patch.drive = patch.drive
        this.patch.crush = patch.crush
        this.patch.pitchEnv = patch.pitchEnv
        this.patch.send = patch.send
        this.pitch = pitch.coerceIn(-24f, 24f)
        phase = 0.0
        phase2 = 0.0
        phase3 = 0.0
        env = 0.0
        t = 0.0
        low = 0.0
        band = 0.0
    }

    val active: Boolean get() = env > 0.0008

    private fun noise(): Double {
        seed = seed xor (seed shl 13)
        seed = seed xor (seed ushr 17)
        seed = seed xor (seed shl 5)
        return ((seed and 0xFFFF).toDouble() / 32768.0) - 1.0
    }

    private fun baseFreq(): Double = 110.0 * 2.0.pow(pitch / 12.0)

    private fun osc(f: Double): Double = when (patch.wave) {
        Wave.SINE -> sin(phase * 2.0 * PI)
        Wave.SAW -> 2.0 * phase - 1.0
        Wave.SQUARE -> if (phase < 0.5) 0.8 else -0.8
        Wave.TRI -> 1.0 - 4.0 * abs(phase - 0.5)
        Wave.NOISE -> noise()
    }

    fun next(sr: Double): Double {
        if (env <= 0.0008) return 0.0
        val dt = 1.0 / sr
        var f = baseFreq()

        // характер барабанов
        when (inst) {
            Inst.KICK -> {
                val sweep = 60.0 + 120.0 * exp(-t * 45.0)
                f = sweep * 2.0.pow((patch.pitchEnv * 6.0) / 12.0)
            }
            Inst.TOM -> f *= 1.6
            Inst.SNARE, Inst.CLAP, Inst.HAT -> Unit
            else -> Unit
        }

        phase = (phase + f / sr) % 1.0
        phase2 = (phase2 + f * 1.006 / sr) % 1.0
        phase3 = (phase3 + f * 0.994 / sr) % 1.0

        var src = when (inst) {
            Inst.HAT -> {
                val n = noise()
                val hp = (n - prevNoise) * 0.6
                prevNoise = n
                hp
            }
            Inst.SNARE -> osc(f) * 0.55 + noise() * 0.55
            Inst.CLAP -> {
                val inBurst = t < 0.006 || (t in 0.014..0.020) || (t in 0.030..0.038)
                if (inBurst) noise() else 0.0
            }
            Inst.PAD -> (osc(f) + (2.0 * phase2 - 1.0) * 0.6 + (2.0 * phase3 - 1.0) * 0.6) / 2.2
            else -> osc(f)
        }

        // Chamberlin SVF
        val cutoffHz = (60.0 + patch.cutoff.pow(2).toDouble() * 9000.0).coerceIn(60.0, sr * 0.42)
        val fcoef = 2.0 * sin(PI * min(cutoffHz, sr * 0.42) / sr)
        val q = 0.35 + patch.res.toDouble() * 6.0
        low += fcoef * band
        val high = src - low - q * band
        band += fcoef * high
        var out = if (inst == Inst.HAT) high else low

        // драйв
        if (patch.drive > 0.001f) {
            val g = 1.0 + patch.drive.toDouble() * 12.0
            out = tanh(out * g) / (1.0 + patch.drive.toDouble() * 2.5)
        }
        // биткраш
        if (patch.crush > 0.001f) {
            val levels = 2.0.pow(1.0 + (1.0 - patch.crush.toDouble()) * 9.0)
            out = (out * levels).roundToInt() / levels
        }

        // огибающая: атака + экспоненциальный спад
        val atk = patch.attack.coerceIn(0.001f, 0.4f).toDouble()
        env = if (t < atk) t / atk else exp(-(t - atk) / (patch.decay.coerceIn(0.03f, 1.6f).toDouble() * 0.45))
        t += dt
        return out * env * vel
    }
}

/** Мастер-эффекты: дилей и дешёвый, но живой реверб на линиях задержки. */
class Fx(sr: Int) {

    private val delayBuf = FloatArray((sr * 0.6).toInt())
    private var dPos = 0
    private val revLens = intArrayOf(1557, 1617, 1491, 1422)
    private val combs = Array(revLens.size) { FloatArray(revLens[it]) }
    private val combPos = IntArray(revLens.size)
    var delayMix = 0.18f
    var reverbMix = 0.22f

    fun process(send: Double, sr: Int): Pair<Double, Double> {
        if (send == 0.0) return 0.0 to 0.0
        // дилей
        val dIdx = dPos
        val delayed = delayBuf[dIdx].toDouble()
        delayBuf[dIdx] = (send + delayed * 0.42).toFloat()
        dPos = (dPos + 1) % delayBuf.size

        // реверб (4 комба)
        var wet = 0.0
        for (i in combs.indices) {
            val p = combPos[i]
            val v = combs[i][p].toDouble()
            combs[i][p] = (send * 0.6 + v * 0.78).toFloat()
            combPos[i] = (p + 1) % combs[i].size
            wet += v
        }
        wet /= combs.size
        val l = delayed * delayMix + wet * reverbMix
        val r = delayed * delayMix * 0.86 + wet * reverbMix * 1.12
        return l to r
    }
}

fun softClip(x: Double): Double {
    val a = abs(x)
    return if (a < 0.85) x else if (x > 0) 0.85 + (1.0 - 0.85) * (1.0 - exp(-(a - 0.85) * 8.0)) else -softClip(-x)
}