package com.degradatione.flmobile

import android.content.ContentValues
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

object Fl {
    // тёмная база + янтарь и редкий холодный акцент (Dark Star Labs)
    val bg = Color(0xFF050507)
    val panel = Color(0xE60A0A0D)
    val card = Color(0xEB111116)
    val cardHi = Color(0xF21A1A21)
    val border = Color(0xFF262630)
    val stepOff = Color(0xFF15151B)
    val beat = Color(0xFF2B2B34)
    val ember = Color(0xFFFA5D29)
    val cold = Color(0xFF49B3FC)
    val accent = Color(0xFFFF9667)
    val orange = Color(0xFFFA5D29)
    val orangeSoft = Color(0xFFFF9667)
    val text = Color(0xFFEDEDED)
    val dim = Color(0xFF9C9CA6)
    val faint = Color(0xFF5E5E6A)
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlApp(pattern: Pattern, player: Player, prefs: android.content.SharedPreferences) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var activeStep by remember { mutableIntStateOf(-1) }
    var toast by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableIntStateOf(0) }
    var bar by remember { mutableIntStateOf(0) }
    var helpOpen by remember { mutableStateOf(false) }
    var soundsOpen by remember { mutableStateOf(false) }
    var density by remember { mutableStateOf(0.45f) }
    val ui = remember { Handler(Looper.getMainLooper()) }

    fun save() {
        prefs.edit().putString("pattern", pattern.toJson()).apply()
    }

    fun demo() {
        pattern.clearSteps()
        pattern.bars = 1
        pattern.tracks.getOrNull(0)?.let { t -> for (s in listOf(0, 4, 8, 12)) t.steps[s] = true }
        pattern.tracks.getOrNull(1)?.let { t -> for (s in listOf(4, 12)) { t.steps[s] = true; t.accents[s] = true } }
        pattern.tracks.getOrNull(2)?.let { t -> for (s in 0 until 16 step 2) t.steps[s] = true }
        pattern.tracks.getOrNull(3)?.let { t -> t.steps[7] = true; t.steps[15] = true }
        pattern.tracks.getOrNull(4)?.let { t ->
            for (s in listOf(0, 3, 6, 10, 14)) t.steps[s] = true
            t.pitch = -5f
        }
        version++
        save()
        toast = "Демо-бит: 4 такта не нужны, но можно включить"
    }

    player.onStep = { s -> ui.post { activeStep = s } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Fl.bg).systemBarsPadding()) {
        val landscape = maxWidth > maxHeight
        val tick = version

        MysticBackdrop(if (playing) activeStep else -1, Modifier.fillMaxSize())

        val transport: @Composable () -> Unit = {
            TransportBar(
                pattern = pattern,
                player = player,
                playing = playing,
                activeStep = activeStep,
                bar = bar,
                onBar = { bar = it },
                onChange = { version++; save() },
                onPlaying = { playing = it; activeStep = -1 },
                onToast = { toast = it },
                onHelp = { helpOpen = true },
                onSounds = { soundsOpen = true },
                onExport = { toast = exportWav(ctx, player) },
            )
        }

        val actions: @Composable () -> Unit = {
            ActionsRow(
                pattern = pattern,
                density = density,
                onDensity = { density = it },
                onChange = { version++; save() },
                onDemo = { demo() },
                onToast = { toast = it },
            )
        }

        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(0.40f).fillMaxHeight()) {
                    transport()
                    actions()
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        pattern.tracks.forEachIndexed { idx, t ->
                            ChannelListItem(
                                track = t,
                                active = idx == selected.coerceIn(0, pattern.tracks.lastIndex.coerceAtLeast(0)),
                                tick = tick,
                                onClick = { selected = idx },
                            )
                        }
                        AddChannelRow(pattern) { version++; save() }
                        Spacer(Modifier.height(6.dp))
                    }
                    MasterBar(pattern) { version++; save() }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Fl.border))
                Column(Modifier.weight(0.60f).fillMaxHeight().padding(8.dp).verticalScroll(rememberScrollState())) {
                    val idx = if (pattern.tracks.isEmpty()) -1 else selected.coerceIn(0, pattern.tracks.lastIndex)
                    val t = if (idx < 0) null else pattern.tracks[idx]
                    if (t == null) EmptyHint { demo() } else ChannelEditor(
                        track = t,
                        tick = tick,
                        activeStep = activeStep,
                        player = player,
                        onChange = { version++; save() },
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                transport()
                actions()
                if (!pattern.anySteps()) EmptyHint { demo() }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    for (t in pattern.tracks) {
                        ChannelCard(
                            track = t,
                            tick = tick,
                            bar = bar,
                            activeStep = activeStep,
                            player = player,
                            onChange = { version++; save() },
                            onRemove = { pattern.remove(t); version++; save() },
                        )
                    }
                    AddChannelRow(pattern) { version++; save() }
                    Spacer(Modifier.height(6.dp))
                }
                MasterBar(pattern) { version++; save() }
            }
        }

        ToastPill(toast, Modifier.align(Alignment.BottomCenter).padding(10.dp)) { toast = null }

        if (helpOpen) {
            Sheet(onDismiss = { helpOpen = false }) { HelpSheet { helpOpen = false } }
        }
        if (soundsOpen) {
            Sheet(onDismiss = { soundsOpen = false }) {
                SoundsSheet(
                    player = player,
                    track = pattern.tracks.getOrNull(selected.coerceIn(0, pattern.tracks.lastIndex.coerceAtLeast(0))),
                    onApply = { version++; save() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Fl.panel,
        contentColor = Fl.text,
        dragHandle = {
            Box(
                Modifier.padding(vertical = 8.dp).size(width = 32.dp, height = 3.dp)
                    .background(Fl.border, RoundedCornerShape(2.dp))
            )
        },
    ) { content() }
}

private fun exportWav(ctx: android.content.Context, player: Player): String = runCatching {
    val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
    val f = File(dir, "fl-${System.currentTimeMillis()}.wav")
    player.exportWav(f, bars = player.pattern.bars)
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
        put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/FLMobile")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("MediaStore отказал")
    ctx.contentResolver.openOutputStream(uri)!!.use { out -> f.inputStream().use { it.copyTo(out) } }
    "Готово: WAV ${f.length() / 1024} KB в Download/FLMobile"
}.getOrElse { "Экспорт не вышел: ${it.message}" }

// ── транспорт ──────────────────────────────────────────────────────────────
@Composable
private fun TransportBar(
    pattern: Pattern,
    player: Player,
    playing: Boolean,
    activeStep: Int,
    bar: Int,
    onBar: (Int) -> Unit,
    onChange: () -> Unit,
    onPlaying: (Boolean) -> Unit,
    onToast: (String) -> Unit,
    onHelp: () -> Unit,
    onSounds: () -> Unit,
    onExport: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Fl.panel).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundPlay(playing) {
                if (playing) {
                    player.stop()
                    onPlaying(false)
                    onToast("Стоп")
                } else {
                    if (!player.hasAnyStep()) onToast("Сначала поставь шаги в сетке")
                    else {
                        val ok = player.start()
                        onPlaying(ok)
                        onToast(if (ok) "Играю · ${pattern.bpm.toInt()} BPM · ${pattern.bars} такт(а)" else (player.lastError ?: "Аудио не открылось"))
                    }
                }
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (playing) "играет" else "на паузе",
                    color = if (playing) Fl.orange else Fl.dim, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                )
                Text(
                    "шаг ${if (activeStep >= 0) activeStep % 16 + 1 else 1}/16 · такт ${activeStep / 16 + 1}/${pattern.bars}",
                    color = Fl.faint, fontSize = 9.sp, fontFamily = Fl.mono, letterSpacing = 0.4.sp,
                )
            }
            SoftBtn("Звуки") { onSounds() }
            Spacer(Modifier.width(4.dp))
            SoftBtn("WAV", onClick = onExport)
            Spacer(Modifier.width(4.dp))
            SoftBtn("?") { onHelp() }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SoftBtn("−") {
                pattern.bpm = (pattern.bpm - 2f).coerceIn(40f, 240f); onChange()
            }
            Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${pattern.bpm.toInt()}", color = Fl.text, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium, fontFamily = Fl.mono, letterSpacing = (-0.4).sp,
                )
                Text("BPM", color = Fl.faint, fontSize = 8.sp)
            }
            SoftBtn("+") {
                pattern.bpm = (pattern.bpm + 2f).coerceIn(40f, 240f); onChange()
            }
            Spacer(Modifier.width(8.dp))
            Text("такты", color = Fl.faint, fontSize = 8.5.sp)
            Spacer(Modifier.width(4.dp))
            for (n in 1..Pattern.MAX_BARS) {
                BarChip(
                    label = "$n",
                    active = pattern.bars == n,
                    onClick = {
                        pattern.bars = n
                        if (bar >= n) onBar(n - 1)
                        onChange()
                    },
                )
                Spacer(Modifier.width(3.dp))
            }
            Spacer(Modifier.weight(1f))
            if (pattern.bars > 1) {
                Text("такт", color = Fl.faint, fontSize = 8.5.sp)
                Spacer(Modifier.width(3.dp))
                for (b in 0 until pattern.bars) {
                    BarChip("${b + 1}", active = bar == b, onClick = { onBar(b) })
                    Spacer(Modifier.width(3.dp))
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("свинг", color = Fl.faint, fontSize = 8.5.sp, modifier = Modifier.width(38.dp))
            Box(Modifier.weight(1f)) {
                Slider(
                    value = pattern.swing,
                    onValueChange = { pattern.swing = it; onChange() },
                    valueRange = 0f..0.6f,
                    modifier = Modifier.height(20.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Fl.orange, activeTrackColor = Fl.orange, inactiveTrackColor = Fl.border,
                    ),
                )
            }
            Text("${(pattern.swing * 100).toInt()}%", color = Fl.dim, fontSize = 8.5.sp, modifier = Modifier.width(30.dp))
            StepLeds(activeStep, pattern.steps)
        }
    }
}

@Composable
private fun StepLeds(activeStep: Int, total: Int) {
    Row(Modifier.width(70.dp), horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
        for (i in 0 until total) {
            Box(
                Modifier.weight(1f).height(5.dp).background(
                    if (i == activeStep) Fl.orange else if (i % 4 == 0) Fl.beat else Fl.stepOff,
                    RoundedCornerShape(2.dp),
                )
            )
        }
    }
}

@Composable
private fun ActionsRow(
    pattern: Pattern,
    density: Float,
    onDensity: (Float) -> Unit,
    onChange: () -> Unit,
    onDemo: () -> Unit,
    onToast: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Fl.panel).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SoftBtn("Демо") { onDemo() }
        SoftBtn("Рандом") {
            pattern.randomize(density)
            onChange()
            onToast("Сгенерил бит · плотность ${(density * 100).toInt()}%")
        }
        SoftBtn("Очистить") {
            pattern.clearSteps(); onChange(); onToast("Сетка пустая")
        }
        SoftBtn("Копир.") {
            if (pattern.bars < 2) onToast("Сначала сделай 2+ такта")
            else {
                pattern.copyBar(0, 1); onChange(); onToast("Такт 1 → такт 2")
            }
        }
        Spacer(Modifier.weight(1f))
        Text("плотн.", color = Fl.faint, fontSize = 8.5.sp)
        Box(Modifier.width(70.dp)) {
            Slider(
                value = density,
                onValueChange = onDensity,
                modifier = Modifier.height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Fl.dim, activeTrackColor = Fl.dim, inactiveTrackColor = Fl.border,
                ),
            )
        }
    }
}

// ── каналы ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelCard(
    track: Track,
    tick: Int,
    bar: Int,
    activeStep: Int,
    player: Player,
    onChange: () -> Unit,
    onRemove: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") tick
    var instOpen by remember { mutableStateOf(false) }
    var knobs by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)
            .background(Fl.card, RoundedCornerShape(14.dp))
            .border(1.dp, Fl.border, RoundedCornerShape(14.dp))
            .padding(9.dp)
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
                    Box(Modifier.size(9.dp).background(if (track.mute) Fl.faint else Fl.orange, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(track.inst.label, color = Fl.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    Text("  ▾", color = Fl.faint, fontSize = 10.sp)
                }
                InstMenu(instOpen, { instOpen = false }) { inst ->
                    instOpen = false
                    track.inst = inst
                    if (!inst.tonal) track.patch.wave = Wave.NOISE
                    onChange()
                }
            }
            Spacer(Modifier.weight(1f))
            MiniBtn(if (track.solo) "S" else "S", on = track.solo) { track.solo = !track.solo; onChange() }
            Spacer(Modifier.width(3.dp))
            MiniBtn("M", on = track.mute) { track.mute = !track.mute; onChange() }
            Spacer(Modifier.width(3.dp))
            MiniBtn("♪", on = knobs) { knobs = !knobs }
            Spacer(Modifier.width(3.dp))
            Box(
                Modifier.size(24.dp).background(Fl.panel, RoundedCornerShape(7.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRemove,
                    ),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Удалить", tint = Fl.faint, modifier = Modifier.size(12.dp)) }
        }
        Spacer(Modifier.height(5.dp))
        StepGrid(
            track = track,
            bar = bar,
            activeStep = activeStep,
            onChange = onChange,
            big = false,
        )
        AnimatedVisibility(visible = knobs) {
            Column {
                Spacer(Modifier.height(5.dp))
                Knobs(track, player) { onChange() }
            }
        }
    }
}

@Composable
private fun ChannelEditor(
    track: Track,
    tick: Int,
    activeStep: Int,
    player: Player,
    onChange: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") tick
    var instOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(Fl.card, RoundedCornerShape(16.dp))
            .border(1.dp, Fl.border, RoundedCornerShape(16.dp)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                SoftBtn("${track.inst.label} ▾") { instOpen = true }
                InstMenu(instOpen, { instOpen = false }) { inst ->
                    instOpen = false
                    track.inst = inst
                    if (!inst.tonal) track.patch.wave = Wave.NOISE
                    onChange()
                }
            }
            Spacer(Modifier.weight(1f))
            MiniBtn("S", on = track.solo) { track.solo = !track.solo; onChange() }
            Spacer(Modifier.width(4.dp))
            MiniBtn("M", on = track.mute) { track.mute = !track.mute; onChange() }
        }
        Spacer(Modifier.height(8.dp))
        StepGrid(track, bar = -1, activeStep = activeStep, onChange = onChange, big = true)
        Spacer(Modifier.height(8.dp))
        Knobs(track, player) { onChange() }
        Spacer(Modifier.height(3.dp))
        Text(
            "включено ${track.steps.count { it }} из ${track.steps.size} · пресет ${track.patch.describe()}",
            color = Fl.faint, fontSize = 9.sp,
        )
    }
}

@Composable
private fun ChannelListItem(track: Track, active: Boolean, tick: Int, onClick: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") tick
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
            .background(if (active) Fl.cardHi else Fl.card, RoundedCornerShape(11.dp))
            .border(1.dp, if (active) Fl.orange.copy(alpha = 0.6f) else Fl.border, RoundedCornerShape(11.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(if (track.mute) Fl.faint else Fl.orange, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(
            track.inst.label, color = if (active) Fl.text else Fl.dim, fontSize = 11.5.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (track.solo) Text("S ", color = Fl.orange, fontSize = 9.sp)
        Text("${track.steps.count { it }}", color = Fl.faint, fontSize = 9.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepGrid(track: Track, bar: Int, activeStep: Int, onChange: () -> Unit, big: Boolean) {
    // bar = -1 → все такты; иначе только выбранный такт
    val from = if (bar < 0) 0 else bar * 16
    val to = if (bar < 0) track.steps.size else (from + 16).coerceAtMost(track.steps.size)
    var s = from
    while (s < to) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (big) 3.dp else 2.dp),
        ) {
            for (c in 0 until 8) {
                val step = s + c
                if (step >= to) {
                    Spacer(Modifier.weight(1f))
                    continue
                }
                val on = track.steps[step]
                val acc = track.accents[step]
                val pulse = step == activeStep
                val scale by animateFloatAsState(
                    if (pulse) 1.07f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "pulse",
                )
                Box(
                    Modifier.weight(1f).height(if (big) 40.dp else 28.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .background(
                            when {
                                on && pulse -> Fl.orangeSoft
                                on && acc -> Fl.accent
                                on -> Fl.orange
                                pulse -> Fl.beat
                                step % 4 == 0 -> Fl.beat
                                else -> Fl.stepOff
                            },
                            RoundedCornerShape(6.dp),
                        )
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                track.steps[step] = !track.steps[step]
                                if (!track.steps[step]) track.accents[step] = false
                                onChange()
                            },
                            onLongClick = {
                                track.steps[step] = true
                                track.accents[step] = !track.accents[step]
                                onChange()
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${step % 16 + 1}",
                        color = if (on) Color.Black.copy(alpha = 0.55f) else Fl.faint,
                        fontSize = if (big) 9.sp else 7.5.sp,
                        fontFamily = Fl.mono,
                    )
                }
            }
        }
        s += 8
        if (s < to) Spacer(Modifier.height(if (big) 3.dp else 2.dp))
    }
}

@Composable
private fun Knobs(track: Track, player: Player, onChange: () -> Unit) {
    val p = track.patch
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (w in Wave.entries) {
            val on = p.wave == w
            Box(
                Modifier.weight(1f).background(if (on) Fl.orange else Fl.panel, RoundedCornerShape(6.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        p.wave = w
                        onChange()
                        player.preview(track.inst, p, track.pitch)
                    }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    w.label, color = if (on) Color.Black else Fl.dim, fontSize = 8.sp,
                    maxLines = 1, overflow = TextOverflow.Clip,
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    val knobs: List<Triple<String, Float, Pair<Float, Float>>> = listOf(
        Triple("срез", p.cutoff, 0f to 1f),
        Triple("резонанс", p.res, 0f to 1f),
        Triple("атака", p.attack, 0.001f to 0.4f),
        Triple("затухание", p.decay, 0.03f to 1.6f),
        Triple("драйв", p.drive, 0f to 1f),
        Triple("краш", p.crush, 0f to 1f),
        Triple("свип", p.pitchEnv, 0f to 1f),
        Triple("посыл", p.send, 0f to 1f),
    )
    for (row in 0 until 4) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (col in 0 until 2) {
                val k = knobs[row * 2 + col]
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(k.first, color = Fl.faint, fontSize = 8.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (k.third.first >= 1f) "${(k.second * 100).toInt()}%"
                            else if (k.first == "затухание") "%.2f".format(k.second)
                            else "${(k.second * 100).toInt()}",
                            color = Fl.dim, fontSize = 8.sp,
                        )
                    }
                    Box(Modifier.fillMaxWidth()) {
                        Slider(
                            value = k.second.coerceIn(k.third.first, k.third.second),
                            onValueChange = { v ->
                                when (k.first) {
                                    "срез" -> p.cutoff = v
                                    "резонанс" -> p.res = v
                                    "атака" -> p.attack = v
                                    "затухание" -> p.decay = v
                                    "драйв" -> p.drive = v
                                    "краш" -> p.crush = v
                                    "свип" -> p.pitchEnv = v
                                    "посыл" -> p.send = v
                                }
                                onChange()
                            },
                            valueRange = k.third.first..k.third.second,
                            modifier = Modifier.height(18.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Fl.dim, activeTrackColor = Fl.orange, inactiveTrackColor = Fl.border,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddChannelRow(pattern: Pattern, onChange: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            SoftBtn("+ канал") { open = true }
            InstMenu(open, { open = false }) { inst ->
                open = false
                pattern.add(inst)
                onChange()
            }
        }
        Spacer(Modifier.weight(1f))
        Text("${pattern.tracks.size} каналов", color = Fl.faint, fontSize = 9.5.sp)
    }
}

@Composable
private fun InstMenu(expanded: Boolean, onDismiss: () -> Unit, onPick: (Inst) -> Unit) {
    DropdownMenu(expanded, onDismiss, containerColor = Fl.card) {
        for (inst in Inst.entries) {
            DropdownMenuItem(
                text = { Text(inst.label, color = Fl.text, fontSize = 13.sp) },
                onClick = { onPick(inst) },
            )
        }
    }
}

@Composable
private fun MasterBar(pattern: Pattern, onChange: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Fl.panel).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Waves, null, tint = Fl.faint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text("мастер", color = Fl.faint, fontSize = 9.sp, modifier = Modifier.width(42.dp))
        Box(Modifier.weight(1f)) {
            Slider(
                value = pattern.master,
                onValueChange = { pattern.master = it; onChange() },
                modifier = Modifier.height(22.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Fl.orange, activeTrackColor = Fl.orange, inactiveTrackColor = Fl.border,
                ),
            )
        }
        Text("${(pattern.master * 100).toInt()}%", color = Fl.dim, fontSize = 9.sp, modifier = Modifier.width(32.dp))
        SoftBtn(if (pattern.muted) "🔇" else "🔊") { pattern.muted = !pattern.muted; onChange() }
    }
}

// ── лист звуков ────────────────────────────────────────────────────────────
@Composable
private fun SoundsSheet(player: Player, track: Track?, onApply: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    val items = remember(query, group) {
        val base = Presets.search(query, 600)
        if (group == null) base else base.filter { it.group == group }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Звуки", color = Fl.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${Presets.all.size} пресетов", color = Fl.faint, fontSize = 9.5.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (track == null) "Выбери канал — пресет применится к нему"
            else "Применится к «${track.inst.label}» · тап = послушать и поставить",
            color = Fl.dim, fontSize = 9.5.sp,
        )
        Spacer(Modifier.height(6.dp))
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("поиск: saw 45, tri, короткие…", color = Fl.faint, fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Fl.card, unfocusedContainerColor = Fl.card,
                focusedTextColor = Fl.text, unfocusedTextColor = Fl.text,
                cursorColor = Fl.orange,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (g in listOf(null, "короткие", "средние", "долгие")) {
                val on = group == g
                Box(
                    Modifier.background(if (on) Fl.orange else Fl.card, RoundedCornerShape(8.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            group = g
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(g ?: "все", color = if (on) Color.Black else Fl.dim, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("${items.size}", color = Fl.faint, fontSize = 9.sp)
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
            items(items) { np ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        .background(Fl.card, RoundedCornerShape(9.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (track != null) {
                                track.patch.wave = np.patch.wave
                                track.patch.cutoff = np.patch.cutoff
                                track.patch.res = np.patch.res
                                track.patch.attack = np.patch.attack
                                track.patch.decay = np.patch.decay
                                track.patch.drive = np.patch.drive
                                track.patch.crush = np.patch.crush
                                track.patch.pitchEnv = np.patch.pitchEnv
                                track.patch.send = np.patch.send
                                onApply()
                                player.preview(track.inst, track.patch, track.pitch)
                            }
                        }
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(np.name, color = Fl.text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text(np.hint, color = Fl.faint, fontSize = 8.5.sp)
                    }
                    Text(np.group, color = Fl.faint, fontSize = 8.5.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HelpSheet(onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Что тут можно", color = Fl.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        for ((t, d) in listOf(
            "Клетки" to "тап — нота вкл/выкл, долгий тап — акцент (громче).",
            "♪ в канале" to "раскрывает крутилки: волна, срез, резонанс, атака, затухание, драйв, краш, свип, посыл.",
            "Звуки" to "3000 готовых пресетов: тап — послушал и поставил на канал.",
            "Такты" to "1–4 такта, свинг, копирование первого такта во второй.",
            "Рандом" to "генерит бит по плотности — от редкого до стенки.",
            "Соло и мьют" to "S — оставить только этот канал, M — заглушить.",
            "Телефон боком" to "слева каналы, справа большой редактор со всеми крутилками.",
            "WAV" to "экспорт всех тактов в Download/FLMobile.",
        )) {
            Row(Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.size(5.dp).background(Fl.orange, CircleShape).padding(top = 5.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(t, color = Fl.text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(d, color = Fl.dim, fontSize = 9.5.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SoftBtn("Понятно", accent = true, onClick = onClose)
        Spacer(Modifier.height(12.dp))
    }
}

// ── мелочь ─────────────────────────────────────────────────────────────────
@Composable
private fun EmptyHint(onDemo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            .background(Fl.orange.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .border(1.dp, Fl.orange.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Тишина в эфире", color = Fl.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("Поставь шаги или нажми демо", color = Fl.dim, fontSize = 10.sp)
        }
        SoftBtn("Демо-бит", accent = true, onClick = onDemo)
    }
}

@Composable
private fun ToastPill(text: String?, modifier: Modifier = Modifier, onHide: () -> Unit) {
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(140)),
        modifier = modifier,
    ) {
        Text(
            text.orEmpty(), color = Fl.text, fontSize = 10.sp,
            modifier = Modifier.background(Fl.card, RoundedCornerShape(14.dp))
                .border(1.dp, Fl.border, RoundedCornerShape(14.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onHide)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun BarChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.background(if (active) Fl.orange else Fl.cardHi, RoundedCornerShape(6.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, color = if (active) Color.Black else Fl.dim, fontSize = 8.5.sp)
    }
}

@Composable
private fun MiniBtn(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(24.dp)
            .background(if (on) Fl.orange else Fl.panel, RoundedCornerShape(7.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (on) Color.Black else Fl.dim, fontSize = 9.sp) }
}

@Composable
private fun RoundPlay(playing: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "play",
    )
    Box(
        Modifier.size(42.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .background(if (playing) Fl.orange else Fl.cardHi, CircleShape)
            .border(1.dp, if (playing) Fl.orange else Fl.border, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
            "Плей",
            tint = if (playing) Color.Black else Fl.orange,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun SoftBtn(text: String, accent: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "soft")
    Box(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .background(if (accent) Fl.orange else Fl.cardHi, RoundedCornerShape(9.dp))
            .border(1.dp, if (accent) Fl.orange else Fl.border, RoundedCornerShape(9.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (accent) Color.Black else Fl.text, fontSize = 10.sp,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}