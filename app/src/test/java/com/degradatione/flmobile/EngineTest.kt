package com.degradatione.flmobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EngineTest {

    @Test
    fun patternJsonRoundTrip() {
        val p = Pattern().ensureDefaults()
        p.bpm = 128f
        p.master = 0.5f
        p.bars = 2
        p.swing = 0.25f
        p.tracks[0].steps[0] = true
        p.tracks[0].accents[0] = true
        p.tracks[4].pitch = -5f
        p.tracks[4].patch.cutoff = 0.33f
        p.tracks[4].patch.wave = Wave.SQUARE
        p.tracks[2].mute = true
        p.tracks[5].solo = true

        val restored = Pattern().apply { assertTrue(loadJson(p.toJson())) }

        assertEquals(128f, restored.bpm, 0.001f)
        assertEquals(0.5f, restored.master, 0.001f)
        assertEquals(2, restored.bars)
        assertEquals(0.25f, restored.swing, 0.001f)
        assertEquals(p.tracks.size, restored.tracks.size)
        assertTrue(restored.tracks[0].steps[0])
        assertTrue(restored.tracks[0].accents[0])
        assertEquals(-5f, restored.tracks[4].pitch, 0.001f)
        assertEquals(0.33f, restored.tracks[4].patch.cutoff, 0.001f)
        assertEquals(Wave.SQUARE, restored.tracks[4].patch.wave)
        assertTrue(restored.tracks[2].mute)
        assertTrue(restored.tracks[5].solo)
    }

    @Test
    fun renderIsAudibleAndCorrectLength() {
        val p = Pattern().ensureDefaults()
        p.bpm = 120f
        for (t in p.tracks) t.steps[0] = true
        val player = Player(p)
        val pcm = player.render(p.steps)
        val expectedFrames = player.stepSamples() * p.steps
        assertEquals(expectedFrames * 2, pcm.size)
        val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("буфер тихий: peak=$peak", peak > 1000)
    }

    @Test
    fun fourBarsRenderFourTimesLonger() {
        val p = Pattern().ensureDefaults()
        p.bpm = 100f
        p.tracks[0].steps[0] = true
        val one = Player(p).render(16)
        p.bars = 4
        val four = Player(p).render(64)
        assertEquals(one.size * 4, four.size)
    }

    @Test
    fun silenceWhenNothingSelected() {
        val p = Pattern().ensureDefaults()
        val player = Player(p)
        val pcm = player.render(p.steps)
        assertTrue(pcm.all { it.toInt() == 0 })
        assertTrue(!player.hasAnyStep())
    }

    @Test
    fun crushAndDriveChangeTheSignal() {
        val p = Pattern().ensureDefaults()
        p.bars = 1
        val t = p.tracks[0]
        t.steps[0] = true
        val clean = Player(p).render(16).toList()

        p.tracks.forEach { it.steps.fill(false) }
        t.patch.crush = 0.9f
        t.patch.drive = 0.8f
        t.steps[0] = true
        val dirty = Player(p).render(16).toList()

        assertNotEquals(clean, dirty)
    }

    @Test
    fun presetBankHasThreeThousandRealPatches() {
        assertEquals(3000, Presets.all.size)
        assertEquals(3000, Presets.all.map { it.name }.distinct().size)
        val signatures = Presets.all.map {
            "${it.patch.wave}/${it.patch.cutoff}/${it.patch.res}/${it.patch.decay}"
        }.distinct().size
        assertEquals(3000, signatures)
        assertTrue(Presets.all.all { it.patch.decay > 0f && it.patch.cutoff in 0f..1f })
    }

    @Test
    fun presetsActuallySound() {
        for (np in listOf(Presets.all.first(), Presets.all[1500], Presets.all.last())) {
            val v = Voice()
            v.trigger(Inst.LEAD, np.patch, 0f, 1f)
            var peak = 0.0
            repeat(8000) { peak = maxOf(peak, kotlin.math.abs(v.next(44100.0))) }
            assertTrue("пресет ${np.name} молчит", peak > 0.005)
        }
    }

    @Test
    fun randomizeFillsSomethingAndKeepsInstruments() {
        val p = Pattern().ensureDefaults()
        p.bars = 2
        p.randomize(0.9f, seed = 42)
        assertTrue(p.anySteps())
        assertEquals(8, p.tracks.size)
    }

    @Test
    fun wavHeaderIsValid() {
        val file = File.createTempFile("flm", ".wav")
        val pcm = ShortArray(200)
        for (i in pcm.indices) pcm[i] = (i * 3).toShort()
        Wav.write(file, pcm, 44100)
        val bytes = file.readBytes()
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals("fmt ", String(bytes, 12, 4))
        assertEquals("data", String(bytes, 36, 4))
        assertEquals(44 + pcm.size * 2, bytes.size)
    }
}