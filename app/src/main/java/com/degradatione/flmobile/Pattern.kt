package com.degradatione.flmobile

import org.json.JSONArray
import org.json.JSONObject

class Track(
    val id: Int,
    var inst: Inst,
    var gain: Float = 0.75f,
    var pan: Float = 0f,
    var pitch: Float = 0f,
    var mute: Boolean = false,
    var solo: Boolean = false,
) {
    val patch = Patch()
    val steps = BooleanArray(Pattern.MAX_STEPS)
    val accents = BooleanArray(Pattern.MAX_STEPS)

    fun hasStep(i: Int) = steps[i % Pattern.MAX_STEPS]
}

class Pattern {

    companion object {
        const val MAX_STEPS = 64
        const val MAX_BARS = 4
    }

    val tracks = mutableListOf<Track>()
    var bpm: Float = 120f
    var master: Float = 0.85f
    var muted = false
    var bars: Int = 1
    var swing: Float = 0f

    val steps: Int get() = bars * 16

    fun ensureDefaults(): Pattern {
        if (tracks.isEmpty()) {
            add(Inst.KICK).apply { patch.wave = Wave.SINE; patch.decay = 0.35f; patch.cutoff = 0.25f }
            add(Inst.SNARE).apply { patch.wave = Wave.NOISE; patch.decay = 0.22f; patch.cutoff = 0.7f }
            add(Inst.HAT).apply { patch.wave = Wave.NOISE; patch.decay = 0.06f; patch.cutoff = 0.95f }
            add(Inst.CLAP).apply { patch.wave = Wave.NOISE; patch.decay = 0.18f; patch.cutoff = 0.8f }
            add(Inst.BASS).apply {
                patch.wave = Wave.SAW; patch.cutoff = 0.32f; patch.res = 0.35f; patch.decay = 0.35f
                pitch = -5f
            }
            add(Inst.LEAD).apply { patch.wave = Wave.SQUARE; patch.cutoff = 0.6f; patch.decay = 0.25f }
            add(Inst.PAD).apply { patch.wave = Wave.SAW; patch.cutoff = 0.5f; patch.decay = 0.9f; patch.send = 0.5f }
            add(Inst.PLUCK).apply { patch.wave = Wave.TRI; patch.cutoff = 0.65f; patch.decay = 0.3f; patch.send = 0.3f }
        }
        return this
    }

    fun add(inst: Inst): Track {
        val t = Track(tracks.size + 1, inst)
        if (!inst.tonal) t.patch.wave = Wave.NOISE
        tracks.add(t)
        return t
    }

    fun remove(t: Track) {
        tracks.remove(t)
    }

    fun anySteps(): Boolean = tracks.any { t -> (0 until steps).any { t.steps[it] } }

    fun clearSteps() {
        for (t in tracks) {
            for (i in 0 until MAX_STEPS) {
                t.steps[i] = false
                t.accents[i] = false
            }
        }
    }

    fun copyBar(fromBar: Int, toBar: Int) {
        val src = fromBar * 16
        val dst = toBar * 16
        for (t in tracks) {
            for (i in 0 until 16) {
                t.steps[dst + i] = t.steps[src + i]
                t.accents[dst + i] = t.accents[src + i]
            }
        }
    }

    /** Генератор бита: плотность 0..1 и «музыкальность» через питч из пентатоники. */
    fun randomize(density: Float, seed: Int = System.currentTimeMillis().toInt()) {
        val rnd = java.util.Random(seed.toLong())
        val scale = intArrayOf(0, 3, 5, 7, 10, 12)
        clearSteps()
        for (t in tracks) {
            val d = when (t.inst) {
                Inst.KICK -> density * 0.45f
                Inst.SNARE -> density * 0.28f
                Inst.CLAP -> density * 0.18f
                Inst.HAT -> density * 0.85f
                Inst.TOM -> density * 0.3f
                else -> density * 0.5f
            }
            for (i in 0 until steps) {
                val onBeat = i % 4 == 0
                val p = d * (if (onBeat) 1.6f else 0.7f)
                if (rnd.nextFloat() < p) {
                    t.steps[i] = true
                    t.accents[i] = rnd.nextFloat() < 0.22f
                }
            }
            if (t.inst.tonal) {
                t.pitch = (scale[rnd.nextInt(scale.size)] - 5).toFloat()
            }
        }
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (t in tracks) {
            val st = StringBuilder()
            val ac = StringBuilder()
            for (i in 0 until MAX_STEPS) {
                st.append(if (t.steps[i]) '1' else '0')
                ac.append(if (t.accents[i]) '1' else '0')
            }
            val p = t.patch
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("inst", t.inst.name)
                    .put("gain", t.gain.toDouble())
                    .put("pan", t.pan.toDouble())
                    .put("pitch", t.pitch.toDouble())
                    .put("mute", t.mute)
                    .put("solo", t.solo)
                    .put("steps", st.toString())
                    .put("accents", ac.toString())
                    .put(
                        "patch",
                        JSONObject()
                            .put("wave", p.wave.name)
                            .put("cutoff", p.cutoff.toDouble())
                            .put("res", p.res.toDouble())
                            .put("attack", p.attack.toDouble())
                            .put("decay", p.decay.toDouble())
                            .put("drive", p.drive.toDouble())
                            .put("crush", p.crush.toDouble())
                            .put("pitchEnv", p.pitchEnv.toDouble())
                            .put("send", p.send.toDouble())
                    )
            )
        }
        return JSONObject()
            .put("bpm", bpm.toDouble())
            .put("master", master.toDouble())
            .put("bars", bars)
            .put("swing", swing.toDouble())
            .put("tracks", arr)
            .toString()
    }

    fun loadJson(json: String): Boolean = runCatching {
        val o = JSONObject(json)
        bpm = o.optDouble("bpm", 120.0).toFloat().coerceIn(40f, 240f)
        master = o.optDouble("master", 0.85).toFloat().coerceIn(0f, 1f)
        bars = o.optInt("bars", 1).coerceIn(1, MAX_BARS)
        swing = o.optDouble("swing", 0.0).toFloat().coerceIn(0f, 0.6f)
        val arr = o.optJSONArray("tracks") ?: JSONArray()
        val fresh = mutableListOf<Track>()
        for (i in 0 until arr.length()) {
            val j = arr.getJSONObject(i)
            val t = Track(
                id = j.optInt("id", i + 1),
                inst = Inst.byName(j.optString("inst")),
                gain = j.optDouble("gain", 0.75).toFloat(),
                pan = j.optDouble("pan", 0.0).toFloat(),
                pitch = j.optDouble("pitch", 0.0).toFloat(),
                mute = j.optBoolean("mute", false),
                solo = j.optBoolean("solo", false),
            )
            val s = j.optString("steps", "")
            val a = j.optString("accents", "")
            for (k in 0 until minOf(MAX_STEPS, s.length)) t.steps[k] = s[k] == '1'
            for (k in 0 until minOf(MAX_STEPS, a.length)) t.accents[k] = a[k] == '1'
            j.optJSONObject("patch")?.let { p ->
                t.patch.wave = Wave.entries.firstOrNull { it.name == p.optString("wave") } ?: t.patch.wave
                t.patch.cutoff = p.optDouble("cutoff", 0.45).toFloat()
                t.patch.res = p.optDouble("res", 0.15).toFloat()
                t.patch.attack = p.optDouble("attack", 0.004).toFloat()
                t.patch.decay = p.optDouble("decay", 0.3).toFloat()
                t.patch.drive = p.optDouble("drive", 0.0).toFloat()
                t.patch.crush = p.optDouble("crush", 0.0).toFloat()
                t.patch.pitchEnv = p.optDouble("pitchEnv", 0.0).toFloat()
                t.patch.send = p.optDouble("send", 0.15).toFloat()
            }
            fresh.add(t)
        }
        tracks.clear()
        tracks.addAll(fresh)
        if (tracks.isEmpty()) ensureDefaults()
        true
    }.getOrDefault(false)
}