package com.degradatione.flmobile

/**
 * 3000 пресетов синтезатора: 5 волн × 10 срезов × 6 резонансов × 10 затуханий.
 * Это не сэмплы, а готовые положения крутилок — каждый звук реально синтезируется сразу.
 */
class NamedPatch(
    val name: String,
    val group: String,
    val patch: Patch,
) {
    val hint: String
        get() = "${patch.wave.label} · срез ${(patch.cutoff * 100).toInt()} · рез ${(patch.res * 100).toInt()} · зат ${(patch.decay * 100).toInt()}"
}

object Presets {

    private val cutoffs = floatArrayOf(0.05f, 0.15f, 0.25f, 0.35f, 0.45f, 0.55f, 0.65f, 0.75f, 0.85f, 0.95f)
    private val res = floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)
    private val decays = floatArrayOf(0.05f, 0.10f, 0.16f, 0.24f, 0.34f, 0.46f, 0.60f, 0.78f, 1.00f, 1.30f)

    val all: List<NamedPatch> by lazy {
        val list = ArrayList<NamedPatch>(3000)
        for (wave in Wave.entries) {
            for (ci in cutoffs.indices) {
                for (ri in res.indices) {
                    for (di in decays.indices) {
                        val p = Patch(
                            wave = wave,
                            cutoff = cutoffs[ci],
                            res = res[ri],
                            attack = if (di == 0) 0.001f else 0.004f + di * 0.004f,
                            decay = decays[di],
                            drive = if ((ci + ri) % 5 == 0) 0.35f else 0f,
                            crush = if ((ci + di) % 7 == 0) 0.3f else 0f,
                            pitchEnv = if (wave == Wave.SINE && ci < 2) 0.6f else 0f,
                            send = 0.05f + ri * 0.12f,
                        )
                        val group = when {
                            decays[di] < 0.2f -> "короткие"
                            decays[di] < 0.6f -> "средние"
                            else -> "долгие"
                        }
                        val name = "${wave.label} ${(cutoffs[ci] * 100).toInt()}/${(res[ri] * 100).toInt()}/${(decays[di] * 100).toInt()}"
                        list.add(NamedPatch(name, group, p))
                    }
                }
            }
        }
        list
    }

    fun search(query: String, limit: Int = 400): List<NamedPatch> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all.take(limit)
        return all.asSequence().filter {
            it.name.lowercase().contains(q) || it.group.contains(q)
        }.take(limit).toList()
    }
}