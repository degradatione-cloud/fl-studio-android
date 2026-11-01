package com.degradatione.flmobile

import org.json.JSONArray
import org.json.JSONObject

class Track(
    val id: Int,
    var inst: Inst,
    var gain: Float = 0.75f,
    var pan: Float = 0f,
    var pitch: Float = 0f,
    var decay: Float = 0.35f,
    var mute: Boolean = false,
) {
    val steps = BooleanArray(STEPS)

    fun hasStep(i: Int) = steps[i % STEPS]

    companion object {
        const val STEPS = 16
    }
}

class Pattern {

    val tracks = mutableListOf<Track>()
    var bpm: Float = 120f
    var master: Float = 0.85f
    var muted = false

    fun ensureDefaults(): Pattern {
        if (tracks.isEmpty()) {
            add(Inst.KICK)
            add(Inst.SNARE)
            add(Inst.HAT)
            add(Inst.CLAP)
            add(Inst.BASS)
            add(Inst.LEAD)
            add(Inst.PAD)
            add(Inst.PLUCK)
        }
        return this
    }

    fun add(inst: Inst): Track {
        val t = Track(tracks.size + 1, inst)
        tracks.add(t)
        return t
    }

    fun remove(t: Track) {
        tracks.remove(t)
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (t in tracks) {
            val st = StringBuilder()
            for (i in 0 until Track.STEPS) st.append(if (t.steps[i]) '1' else '0')
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("inst", t.inst.name)
                    .put("gain", t.gain.toDouble())
                    .put("pan", t.pan.toDouble())
                    .put("pitch", t.pitch.toDouble())
                    .put("decay", t.decay.toDouble())
                    .put("mute", t.mute)
                    .put("steps", st.toString())
            )
        }
        return JSONObject()
            .put("bpm", bpm.toDouble())
            .put("master", master.toDouble())
            .put("tracks", arr)
            .toString()
    }

    fun loadJson(json: String): Boolean = runCatching {
        val o = JSONObject(json)
        bpm = o.optDouble("bpm", 120.0).toFloat().coerceIn(40f, 240f)
        master = o.optDouble("master", 0.85).toFloat().coerceIn(0f, 1f)
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
                decay = j.optDouble("decay", 0.35).toFloat(),
                mute = j.optBoolean("mute", false),
            )
            val s = j.optString("steps", "")
            for (k in 0 until minOf(Track.STEPS, s.length)) t.steps[k] = s[k] == '1'
            fresh.add(t)
        }
        tracks.clear()
        tracks.addAll(fresh)
        if (tracks.isEmpty()) ensureDefaults()
        true
    }.getOrDefault(false)
}