package com.degradatione.flmobile

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class Inst(val label: String, val tonal: Boolean) {
    KICK("Kick", false),
    SNARE("Snare", false),
    HAT("Hat", false),
    CLAP("Clap", false),
    BASS("Bass", true),
    LEAD("Lead", true),
    PAD("Pad", true),
    PLUCK("Pluck", true);

    companion object {
        fun byName(n: String?): Inst = entries.firstOrNull { it.name == n } ?: KICK
    }
}

/**
 * Голос канала: один генератор с огибающей. Без сэмплов — всё синтезируется,
 * поэтому APK маленький и ничего не надо скачивать.
 */
class Voice {

    private var inst = Inst.KICK
    private var pitch = 0f
    private var decay = 0.35f
    private var phase = 0.0
    private var phase2 = 0.0
    private var phase3 = 0.0
    private var env = 0.0
    private var t = 0.0
    private var seed = 0x2545F491
    private var lp = 0.0
    private var prevNoise = 0.0

    fun trigger(inst: Inst, pitch: Float, decay: Float) {
        this.inst = inst
        this.pitch = pitch.coerceIn(-24f, 24f)
        this.decay = decay.coerceIn(0.05f, 1.5f)
        phase = 0.0
        phase2 = 0.0
        phase3 = 0.0
        env = 1.0
        t = 0.0
        lp = 0.0
    }

    val active: Boolean get() = env > 0.0006

    private fun noise(): Double {
        seed = seed xor (seed shl 13)
        seed = seed xor (seed ushr 17)
        seed = seed xor (seed shl 5)
        return ((seed and 0xFFFF).toDouble() / 32768.0) - 1.0
    }

    private fun freq(): Double = 110.0 * 2.0.pow(pitch / 12.0)

    fun next(sr: Double): Double {
        if (!active) return 0.0
        val dt = 1.0 / sr
        val f = freq()
        var out = 0.0
        when (inst) {
            Inst.KICK -> {
                val sweep = 45.0 + 110.0 * exp(-t * 42.0)
                phase += sweep / sr
                out = sin(phase * 2.0 * PI) * env
                env *= exp(-dt / (decay * 0.45))
                if (t < 0.004) out += 0.35 * env
            }
            Inst.SNARE -> {
                phase += 180.0 / sr
                out = noise() * 0.75 * env + sin(phase * 2.0 * PI) * 0.3 * env
                env *= exp(-dt / (decay * 0.30))
            }
            Inst.HAT -> {
                val n = noise()
                out = (n - prevNoise) * 0.5 * env
                prevNoise = n
                env *= exp(-dt / (decay * 0.10))
            }
            Inst.CLAP -> {
                val inBurst = t < 0.006 || (t in 0.014..0.020) || (t in 0.030..0.038)
                out = if (inBurst) noise() * env else 0.0
                env *= exp(-dt / (decay * 0.35))
            }
            Inst.BASS -> {
                phase = (phase + f / sr) % 1.0
                val saw = 2.0 * phase - 1.0
                val cutoff = 0.06 + 0.25 * env
                lp += cutoff * (saw - lp)
                out = lp * env * 1.4
                env *= exp(-dt / (decay * 0.6))
            }
            Inst.LEAD -> {
                phase = (phase + f / sr) % 1.0
                val sq = if (phase < 0.5) 0.7 else -0.7
                out = sq * env * 0.8
                env *= exp(-dt / (decay * 0.5))
            }
            Inst.PAD -> {
                phase = (phase + f * 0.994 / sr) % 1.0
                phase2 = (phase2 + f / sr) % 1.0
                phase3 = (phase3 + f * 1.006 / sr) % 1.0
                val saw = (2.0 * phase - 1.0) + (2.0 * phase2 - 1.0) + (2.0 * phase3 - 1.0)
                val attack = if (t < 0.08) t / 0.08 else 1.0
                out = saw / 3.0 * env * attack * 0.7
                env *= exp(-dt / (decay * 1.1))
            }
            Inst.PLUCK -> {
                phase = (phase + f / sr) % 1.0
                val tri = 1.0 - 4.0 * abs(phase - 0.5)
                out = tri * env * 0.9
                env *= exp(-dt / (decay * 0.4))
            }
        }
        t += dt
        return out
    }
}

/** Одна мягкая сатурация вместо клиппинга. */
fun softClip(x: Double): Double {
    val a = abs(x)
    return if (a < 0.85) x else if (x > 0) 0.85 + (1.0 - 0.85) * (1.0 - exp(-(a - 0.85) * 8.0)) else
        -softClip(-x)
}