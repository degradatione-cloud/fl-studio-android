package com.degradatione.flmobile

import org.junit.Assert.assertEquals
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
        p.tracks[0].steps[0] = true
        p.tracks[0].steps[4] = true
        p.tracks[4].pitch = -5f
        p.tracks[2].mute = true

        val restored = Pattern().apply { assertTrue(loadJson(p.toJson())) }

        assertEquals(128f, restored.bpm, 0.001f)
        assertEquals(0.5f, restored.master, 0.001f)
        assertEquals(p.tracks.size, restored.tracks.size)
        assertTrue(restored.tracks[0].steps[0])
        assertTrue(restored.tracks[0].steps[4])
        assertEquals(-5f, restored.tracks[4].pitch, 0.001f)
        assertTrue(restored.tracks[2].mute)
    }

    @Test
    fun renderIsAudibleAndCorrectLength() {
        val p = Pattern().ensureDefaults()
        p.bpm = 120f
        for (t in p.tracks) t.steps[0] = true
        val player = Player(p)
        val pcm = player.render(Track.STEPS)
        // 16 шагов по 1/16 при 120 BPM = 4 удара * 0.5 с = 2 с
        val expectedFrames = (44100 * 60 / 120 / 4).toInt() * Track.STEPS
        assertEquals(expectedFrames * 2, pcm.size)
        val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("буфер тихий: peak=$peak", peak > 1000)
    }

    @Test
    fun silenceWhenNothingSelected() {
        val p = Pattern().ensureDefaults()
        val player = Player(p)
        val pcm = player.render(Track.STEPS)
        assertTrue(pcm.all { it.toInt() == 0 })
        assertTrue(!player.hasAnyStep())
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
        assertEquals(pcm.size * 2, bytes.size - 44)
    }
}