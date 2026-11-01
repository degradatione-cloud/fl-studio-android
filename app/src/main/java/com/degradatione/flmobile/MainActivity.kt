package com.degradatione.flmobile

import android.content.ContentValues
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

object Fl {
    val bg = Color(0xFF161616)
    val panel = Color(0xFF232323)
    val row = Color(0xFF2B2B2B)
    val border = Color(0xFF3A3A3A)
    val stepOff = Color(0xFF343434)
    val stepOn = Color(0xFFFF8A00)
    val stepBeat = Color(0xFF4A4A4A)
    val orange = Color(0xFFFF8A00)
    val text = Color(0xFFEDEDED)
    val dim = Color(0xFF9A9A9A)
    val faint = Color(0xFF6A6A6A)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("fl", MODE_PRIVATE)
        val pattern = Pattern().ensureDefaults()
        prefs.getString("pattern", null)?.let { pattern.loadJson(it) }
        val player = Player(pattern)
        setContent { FlTheme { FlApp(pattern, player, prefs) } }
    }
}

@Composable
fun FlTheme(content: @Composable () -> Unit) {
    androidx.compose.material3.MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Fl.orange, background = Fl.bg, surface = Fl.panel, onSurface = Fl.text,
        ),
        content = content,
    )
}

@Composable
fun FlApp(pattern: Pattern, player: Player, prefs: android.content.SharedPreferences) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var activeStep by remember { mutableIntStateOf(-1) }
    var toast by remember { mutableStateOf<String?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    val ui = remember { Handler(Looper.getMainLooper()) }

    fun save() {
        prefs.edit().putString("pattern", pattern.toJson()).apply()
    }

    player.onStep = { s -> ui.post { activeStep = s } }

    Column(Modifier.fillMaxSize().background(Fl.bg).systemBarsPadding()) {
        // ── транспорт ─────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().background(Fl.panel).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).background(if (playing) Fl.orange else Fl.row, RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (playing) {
                            player.stop()
                            playing = false
                            activeStep = -1
                        } else {
                            if (!player.hasAnyStep()) {
                                toast = "Сначала поставь шаги в сетке"
                            } else {
                                playing = player.start()
                                if (!playing) toast = player.lastError ?: "Аудио не открылось"
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    "Плей",
                    tint = if (playing) Color.Black else Fl.orange,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.width(74.dp)) {
                Text("BPM", color = Fl.faint, fontSize = 8.5.sp)
                Text("${pattern.bpm.toInt()}", color = Fl.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Tap("−") {
                pattern.bpm = (pattern.bpm - 2f).coerceIn(40f, 240f)
                version++
                save()
            }
            Tap("+") {
                pattern.bpm = (pattern.bpm + 2f).coerceIn(40f, 240f)
                version++
                save()
            }
            Spacer(Modifier.weight(1f))
            Tap("WAV") {
                val res = runCatching {
                    val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
                    val f = File(dir, "fl-${System.currentTimeMillis()}.wav")
                    player.exportWav(f, bars = 2)
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                        put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/FLMobile")
                    }
                    val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("MediaStore отказал")
                    ctx.contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
                    "WAV ${f.length() / 1024} KB → Download/FLMobile"
                }
                toast = res.getOrElse { "Экспорт: ${it.message}" }
            }
        }

        // ── индикатор шагов ───────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().background(Fl.panel).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (i in 0 until Track.STEPS) {
                Box(
                    Modifier.weight(1f).height(6.dp).background(
                        if (i == activeStep) Fl.orange else if (i % 4 == 0) Fl.stepBeat else Fl.stepOff,
                        RoundedCornerShape(3.dp),
                    )
                )
            }
        }

        // ── каналы ────────────────────────────────────────────────────────
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            val tick = version
            for (t in pattern.tracks) {
                ChannelRow(
                    track = t,
                    tick = tick,
                    onChange = { version++; save() },
                    onRemove = {
                        pattern.remove(t)
                        version++
                        save()
                    },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Tap("+ канал") { addOpen = true }
                    DropdownMenu(addOpen, { addOpen = false }, containerColor = Fl.row) {
                        for (inst in Inst.entries) {
                            DropdownMenuItem(
                                text = { Text(inst.label, color = Fl.text, fontSize = 13.sp) },
                                onClick = {
                                    addOpen = false
                                    pattern.add(inst)
                                    version++
                                    save()
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${pattern.tracks.size} каналов · ${pattern.bpm.toInt()} BPM",
                    color = Fl.faint, fontSize = 9.5.sp,
                )
            }
        }

        // ── мастер ────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().background(Fl.panel).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MASTER", color = Fl.faint, fontSize = 9.sp, modifier = Modifier.width(52.dp))
            Box(Modifier.weight(1f)) {
                Slider(
                    value = pattern.master,
                    onValueChange = { pattern.master = it; version++; save() },
                    modifier = Modifier.height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Fl.orange, activeTrackColor = Fl.orange, inactiveTrackColor = Fl.border,
                    ),
                )
            }
            Text("${(pattern.master * 100).toInt()}%", color = Fl.dim, fontSize = 9.5.sp, modifier = Modifier.width(34.dp))
            Tap(if (pattern.muted) "🔇" else "🔊") { pattern.muted = !pattern.muted; version++ }
        }

        toast?.let {
            Text(
                it, color = Fl.text, fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().background(Fl.orange.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun ChannelRow(track: Track, tick: Int, onChange: () -> Unit, onRemove: () -> Unit) {
    // tick меняется при любой правке паттерна — так строка перерисовывается
    @Suppress("UNUSED_EXPRESSION") tick
    var instOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)
            .background(Fl.row, RoundedCornerShape(10.dp))
            .border(1.dp, Fl.border, RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { instOpen = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(Fl.orange, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(track.inst.label, color = Fl.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                DropdownMenu(instOpen, { instOpen = false }, containerColor = Fl.row) {
                    for (inst in Inst.entries) {
                        DropdownMenuItem(
                            text = { Text(inst.label, color = Fl.text, fontSize = 13.sp) },
                            onClick = {
                                instOpen = false
                                track.inst = inst
                                onChange()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.weight(1.6f)) {
                Slider(
                    value = track.gain,
                    onValueChange = { track.gain = it; onChange() },
                    modifier = Modifier.height(22.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Fl.text, activeTrackColor = Fl.orange, inactiveTrackColor = Fl.border,
                    ),
                )
            }
            SmallBtn(if (track.mute) "M*" else "M") { track.mute = !track.mute; onChange() }
            SmallBtn("×") { onRemove() }
        }
        Spacer(Modifier.height(5.dp))
        for (rowIdx in 0 until 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (col in 0 until 8) {
                    val step = rowIdx * 8 + col
                    val on = track.steps[step]
                    Box(
                        Modifier.weight(1f).height(30.dp)
                            .background(
                                when {
                                    on -> Fl.orange
                                    step % 4 == 0 -> Fl.stepBeat
                                    else -> Fl.stepOff
                                },
                                RoundedCornerShape(5.dp),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                track.steps[step] = !track.steps[step]
                                onChange()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${step + 1}",
                            color = if (on) Color.Black.copy(alpha = 0.65f) else Fl.faint,
                            fontSize = 8.sp,
                        )
                    }
                }
            }
            if (rowIdx == 0) Spacer(Modifier.height(3.dp))
        }
        if (track.inst.tonal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("pitch ${track.pitch.toInt()}", color = Fl.faint, fontSize = 9.sp, modifier = Modifier.width(54.dp))
                Box(Modifier.weight(1f)) {
                    Slider(
                        value = track.pitch,
                        onValueChange = { track.pitch = it; onChange() },
                        valueRange = -24f..24f,
                        modifier = Modifier.height(20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Fl.dim, activeTrackColor = Fl.dim, inactiveTrackColor = Fl.border,
                        ),
                    )
                }
                Text("decay", color = Fl.faint, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp))
                Box(Modifier.weight(0.8f)) {
                    Slider(
                        value = track.decay,
                        onValueChange = { track.decay = it; onChange() },
                        valueRange = 0.05f..1.2f,
                        modifier = Modifier.height(20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Fl.dim, activeTrackColor = Fl.dim, inactiveTrackColor = Fl.border,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun Tap(text: String, onClick: () -> Unit) {
    Box(
        Modifier.background(Fl.row, RoundedCornerShape(9.dp))
            .border(1.dp, Fl.border, RoundedCornerShape(9.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Fl.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
fun SmallBtn(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(width = 26.dp, height = 22.dp).background(Fl.panel, RoundedCornerShape(6.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Fl.dim, fontSize = 10.sp) }
}