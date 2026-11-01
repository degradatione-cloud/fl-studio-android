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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Waves
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

object Fl {
    val bg = Color(0xFF141416)
    val panel = Color(0xFF1E1E22)
    val card = Color(0xFF26262C)
    val cardHi = Color(0xFF2E2E36)
    val border = Color(0xFF33333C)
    val stepOff = Color(0xFF2F2F38)
    val beat = Color(0xFF41414D)
    val orange = Color(0xFFFF9500)
    val orangeSoft = Color(0xFFFFB74D)
    val green = Color(0xFF6FD08C)
    val text = Color(0xFFF2F2F5)
    val dim = Color(0xFFA6A6B2)
    val faint = Color(0xFF74747F)
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FlApp(pattern: Pattern, player: Player, prefs: android.content.SharedPreferences) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var activeStep by remember { mutableIntStateOf(-1) }
    var toast by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableIntStateOf(0) }
    var helpOpen by remember { mutableStateOf(false) }
    val ui = remember { Handler(Looper.getMainLooper()) }

    fun save() {
        prefs.edit().putString("pattern", pattern.toJson()).apply()
    }

    fun demoBeat() {
        val kick = pattern.tracks.getOrNull(0)
        val snare = pattern.tracks.getOrNull(1)
        val hat = pattern.tracks.getOrNull(2)
        kick?.let { t -> for (s in listOf(0, 4, 8, 12)) t.steps[s] = true }
        snare?.let { t -> for (s in listOf(4, 12)) t.steps[s] = true }
        hat?.let { t -> for (s in 0 until Track.STEPS step 2) t.steps[s] = true }
        if (pattern.tracks.getOrNull(4) != null) {
            val b = pattern.tracks[4]
            for (s in listOf(0, 3, 6, 10, 14)) b.steps[s] = true
            b.pitch = -5f
        }
        version++
        save()
        toast = "Демо-бит готов — жми play"
    }

    player.onStep = { s -> ui.post { activeStep = s } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Fl.bg).systemBarsPadding()) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            LandscapeLayout(
                pattern = pattern,
                player = player,
                playing = playing,
                activeStep = activeStep,
                selected = selected,
                onSelect = { selected = it },
                version = version,
                onPlaying = { playing = it; activeStep = -1 },
                onChange = { version++; save() },
                onToast = { toast = it },
                onHelp = { helpOpen = true },
                onDemo = { demoBeat() },
                onExport = {
                    toast = exportWav(ctx, player)
                },
            )
        } else {
            PortraitLayout(
                pattern = pattern,
                player = player,
                playing = playing,
                activeStep = activeStep,
                version = version,
                onPlaying = { playing = it; activeStep = -1 },
                onChange = { version++; save() },
                onToast = { toast = it },
                onHelp = { helpOpen = true },
                onDemo = { demoBeat() },
                onExport = { toast = exportWav(ctx, player) },
            )
        }

        ToastPill(
            visible = toast != null,
            text = toast.orEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
            onHide = { toast = null },
        )

        if (helpOpen) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { helpOpen = false },
                containerColor = Fl.panel,
                contentColor = Fl.text,
                dragHandle = {
                    Box(
                        Modifier.padding(vertical = 8.dp).size(width = 32.dp, height = 3.dp)
                            .background(Fl.border, RoundedCornerShape(2.dp))
                    )
                },
            ) { HelpSheet(onClose = { helpOpen = false }) }
        }
    }
}

private fun exportWav(ctx: android.content.Context, player: Player): String = runCatching {
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
    "Готово: WAV ${f.length() / 1024} KB в Download/FLMobile"
}.getOrElse { "Экспорт не вышел: ${it.message}" }

// ── портрет ────────────────────────────────────────────────────────────────
@Composable
private fun PortraitLayout(
    pattern: Pattern,
    player: Player,
    playing: Boolean,
    activeStep: Int,
    version: Int,
    onPlaying: (Boolean) -> Unit,
    onChange: () -> Unit,
    onToast: (String) -> Unit,
    onHelp: () -> Unit,
    onDemo: () -> Unit,
    onExport: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TransportBar(
            pattern = pattern,
            player = player,
            playing = playing,
            activeStep = activeStep,
            onChange = onChange,
            onPlaying = onPlaying,
            onToast = onToast,
            onHelp = onHelp,
            onExport = onExport,
        )
        if (pattern.tracks.none { t -> t.steps.any { it } }) {
            EmptyHint(onDemo = onDemo)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            val tick = version
            for (t in pattern.tracks) {
                ChannelCard(
                    track = t,
                    tick = tick,
                    activeStep = activeStep,
                    onChange = onChange,
                    onRemove = {
                        pattern.remove(t)
                        onChange()
                    },
                )
            }
            AddChannelRow(pattern, onChange)
            Spacer(Modifier.height(6.dp))
        }
        MasterBar(pattern, onChange)
    }
}

// ── ландшафт: слева каналы, справа редактор выбранного ─────────────────────
@Composable
private fun LandscapeLayout(
    pattern: Pattern,
    player: Player,
    playing: Boolean,
    activeStep: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    version: Int,
    onPlaying: (Boolean) -> Unit,
    onChange: () -> Unit,
    onToast: (String) -> Unit,
    onHelp: () -> Unit,
    onDemo: () -> Unit,
    onExport: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.42f).fillMaxHeight()) {
            TransportBar(
                pattern = pattern,
                player = player,
                playing = playing,
                activeStep = activeStep,
                onChange = onChange,
                onPlaying = onPlaying,
                onToast = onToast,
                onHelp = onHelp,
                onExport = onExport,
                compact = true,
            )
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                val tick = version
                pattern.tracks.forEachIndexed { idx, t ->
                    ChannelListItem(
                        track = t,
                        active = idx == selected.coerceIn(0, pattern.tracks.lastIndex),
                        tick = tick,
                        onClick = { onSelect(idx) },
                    )
                }
                AddChannelRow(pattern, onChange)
                Spacer(Modifier.height(6.dp))
            }
            MasterBar(pattern, onChange)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Fl.border))
        Column(Modifier.weight(0.58f).fillMaxHeight().padding(8.dp).verticalScroll(rememberScrollState())) {
            val idx = if (pattern.tracks.isEmpty()) -1 else selected.coerceIn(0, pattern.tracks.lastIndex)
            val track = if (idx < 0) null else pattern.tracks[idx]
            if (track == null) {
                EmptyHint(onDemo = onDemo)
            } else {
                ChannelEditor(
                    track = track,
                    activeStep = activeStep,
                    onChange = onChange,
                    onToast = onToast,
                )
            }
        }
    }
}

// ── куски интерфейса ───────────────────────────────────────────────────────
@Composable
private fun TransportBar(
    pattern: Pattern,
    player: Player,
    playing: Boolean,
    activeStep: Int,
    onChange: () -> Unit,
    onPlaying: (Boolean) -> Unit,
    onToast: (String) -> Unit,
    onHelp: () -> Unit,
    onExport: () -> Unit,
    compact: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().background(Fl.panel).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundPlay(playing) {
                if (playing) {
                    player.stop()
                    onPlaying(false)
                    onToast("Стоп")
                } else {
                    if (!player.hasAnyStep()) {
                        onToast("Сначала поставь шаги в сетке")
                    } else {
                        val ok = player.start()
                        onPlaying(ok)
                        onToast(if (ok) "Играю · ${pattern.bpm.toInt()} BPM" else (player.lastError ?: "Аудио не открылось"))
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (playing) "играет" else "на паузе",
                    color = if (playing) Fl.orange else Fl.dim, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                )
                Text(
                    "${pattern.bpm.toInt()} BPM · шаг ${if (activeStep >= 0) activeStep + 1 else 1}/16",
                    color = Fl.faint, fontSize = 9.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            if (!compact) {
                BpmStepper(pattern, onChange)
                Spacer(Modifier.width(6.dp))
            }
            SoftBtn("WAV", onClick = onExport)
            Spacer(Modifier.width(4.dp))
            SoftBtn("?", onClick = onHelp)
        }
        if (compact) {
            Spacer(Modifier.height(6.dp))
            BpmStepper(pattern, onChange)
        }
        Spacer(Modifier.height(6.dp))
        StepLeds(activeStep)
    }
}

@Composable
private fun StepLeds(activeStep: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until Track.STEPS) {
            val on = i == activeStep
            Box(
                Modifier.weight(1f).height(5.dp).background(
                    if (on) Fl.orange else if (i % 4 == 0) Fl.beat else Fl.stepOff,
                    RoundedCornerShape(3.dp),
                )
            )
        }
    }
}

@Composable
private fun BpmStepper(pattern: Pattern, onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SoftBtn("−") {
            pattern.bpm = (pattern.bpm - 2f).coerceIn(40f, 240f)
            onChange()
        }
        Text(
            "${pattern.bpm.toInt()}",
            color = Fl.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(40.dp), textAlign = TextAlign.Center,
        )
        SoftBtn("+") {
            pattern.bpm = (pattern.bpm + 2f).coerceIn(40f, 240f)
            onChange()
        }
    }
}

@Composable
private fun ChannelCard(
    track: Track,
    tick: Int,
    activeStep: Int,
    onChange: () -> Unit,
    onRemove: () -> Unit,
) {
    @Suppress("UNUSED_EXPRESSION") tick
    var instOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)
            .background(Fl.card, RoundedCornerShape(14.dp))
            .border(1.dp, Fl.border, RoundedCornerShape(14.dp))
            .padding(10.dp)
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
                    Box(Modifier.size(9.dp).background(Fl.orange, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(track.inst.label, color = Fl.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("  ▾", color = Fl.faint, fontSize = 10.sp)
                }
                InstMenu(instOpen, onDismiss = { instOpen = false }) { inst ->
                    instOpen = false
                    track.inst = inst
                    onChange()
                }
            }
            Spacer(Modifier.weight(1f))
            SoftBtn(if (track.mute) "M выкл" else "M вкл") {
                track.mute = !track.mute
                onChange()
            }
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(26.dp).background(Fl.panel, RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRemove,
                    ),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Удалить", tint = Fl.faint, modifier = Modifier.size(13.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        StepGrid(track, activeStep, onChange)
        Spacer(Modifier.height(6.dp))
        LabeledSlider("громкость", track.gain, 0f..1f) { track.gain = it; onChange() }
        if (track.inst.tonal) {
            LabeledSlider("тон", track.pitch, -24f..24f, valueText = { "${it.toInt()}" }) {
                track.pitch = it; onChange()
            }
            LabeledSlider("затухание", track.decay, 0.05f..1.2f, valueText = { "%.2f".format(it) }) {
                track.decay = it; onChange()
            }
        }
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
        Text(
            "${track.steps.count { it }}/16", color = Fl.faint, fontSize = 9.sp,
        )
    }
}

@Composable
private fun ChannelEditor(
    track: Track,
    activeStep: Int,
    onChange: () -> Unit,
    onToast: (String) -> Unit,
) {
    var instOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(Fl.card, RoundedCornerShape(16.dp))
            .border(1.dp, Fl.border, RoundedCornerShape(16.dp)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                SoftBtn("${track.inst.label} ▾") { instOpen = true }
                InstMenu(instOpen, onDismiss = { instOpen = false }) { inst ->
                    instOpen = false
                    track.inst = inst
                    onChange()
                }
            }
            Spacer(Modifier.weight(1f))
            SoftBtn(if (track.mute) "M выкл" else "M вкл") { track.mute = !track.mute; onChange() }
        }
        Spacer(Modifier.height(10.dp))
        Text("Сетка 1/16 · тап по клетке", color = Fl.faint, fontSize = 9.5.sp)
        Spacer(Modifier.height(5.dp))
        StepGrid(track, activeStep, onChange, big = true)
        Spacer(Modifier.height(10.dp))
        LabeledSlider("громкость", track.gain, 0f..1f) { track.gain = it; onChange() }
        if (track.inst.tonal) {
            LabeledSlider("тон", track.pitch, -24f..24f, valueText = { "${it.toInt()}" }) {
                track.pitch = it; onChange()
            }
            LabeledSlider("затухание", track.decay, 0.05f..1.2f, valueText = { "%.2f".format(it) }) {
                track.decay = it; onChange()
            }
        } else {
            LabeledSlider("затухание", track.decay, 0.05f..1.2f, valueText = { "%.2f".format(it) }) {
                track.decay = it; onChange()
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "шагов включено: ${track.steps.count { it }} из 16",
            color = Fl.faint, fontSize = 9.5.sp,
        )
    }
}

@Composable
private fun StepGrid(track: Track, activeStep: Int, onChange: () -> Unit, big: Boolean = false) {
    val rows = if (big) 2 else 2
    for (r in 0 until rows) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (big) 4.dp else 3.dp)) {
            for (c in 0 until 8) {
                val step = r * 8 + c
                val on = track.steps[step]
                val pulse = step == activeStep
                val scale by animateFloatAsState(
                    if (pulse) 1.06f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "stepPulse",
                )
                Box(
                    Modifier.weight(1f).height(if (big) 46.dp else 32.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .background(
                            when {
                                on && pulse -> Fl.orangeSoft
                                on -> Fl.orange
                                pulse -> Fl.beat
                                step % 4 == 0 -> Fl.beat
                                else -> Fl.stepOff
                            },
                            RoundedCornerShape(7.dp),
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
                        color = if (on) Color.Black.copy(alpha = 0.6f) else Fl.faint,
                        fontSize = if (big) 10.sp else 8.sp,
                    )
                }
            }
        }
        if (r == 0) Spacer(Modifier.height(if (big) 4.dp else 3.dp))
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String = { "${(it * 100).toInt()}%" },
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Fl.faint, fontSize = 9.5.sp, modifier = Modifier.weight(1f))
            Text(valueText(value), color = Fl.dim, fontSize = 9.5.sp)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = Fl.text, activeTrackColor = Fl.orange, inactiveTrackColor = Fl.border,
            ),
        )
    }
}

@Composable
private fun AddChannelRow(pattern: Pattern, onChange: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            SoftBtn("+ канал") { open = true }
            InstMenu(open, onDismiss = { open = false }) { inst ->
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
        Modifier.fillMaxWidth().background(Fl.panel).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Waves, null, tint = Fl.faint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("мастер", color = Fl.faint, fontSize = 9.5.sp, modifier = Modifier.width(44.dp))
        Box(Modifier.weight(1f)) {
            Slider(
                value = pattern.master,
                onValueChange = { pattern.master = it; onChange() },
                modifier = Modifier.height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Fl.orange, activeTrackColor = Fl.orange, inactiveTrackColor = Fl.border,
                ),
            )
        }
        Text("${(pattern.master * 100).toInt()}%", color = Fl.dim, fontSize = 9.5.sp, modifier = Modifier.width(34.dp))
        SoftBtn(if (pattern.muted) "🔇" else "🔊") { pattern.muted = !pattern.muted; onChange() }
    }
}

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
            Text("Поставь пару шагов — или нажми демо", color = Fl.dim, fontSize = 10.sp)
        }
        SoftBtn("Демо-бит", accent = true, onClick = onDemo)
    }
}

@Composable
private fun HelpSheet(onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Как это работает", color = Fl.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        for ((t, d) in listOf(
            "Клетки" to "каждая клетка — нота на шаге 1/16. Тап включает и выклчает.",
            "Цвет" to "оранжевая клетка — нота, светлее — играет прямо сейчас.",
            "Каналы" to "слева список каналов, тап по имени меняет инструмент.",
            "Телефон боком" to "переверни — каналы слева, большой редактор справа.",
            "WAV" to "экспорт двух тактов в Download/FLMobile.",
        )) {
            Row(Modifier.padding(vertical = 4.dp)) {
                Box(Modifier.size(6.dp).background(Fl.orange, CircleShape).padding(top = 6.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(t, color = Fl.text, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                    Text(d, color = Fl.dim, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SoftBtn("Понятно", accent = true, onClick = onClose)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ToastPill(visible: Boolean, text: String, modifier: Modifier = Modifier, onHide: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(140)),
        modifier = modifier,
    ) {
        Text(
            text, color = Fl.text, fontSize = 10.5.sp,
            modifier = Modifier.background(Fl.card, RoundedCornerShape(14.dp))
                .border(1.dp, Fl.border, RoundedCornerShape(14.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onHide)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
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
        Modifier.size(44.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .background(if (playing) Fl.orange else Fl.cardHi, CircleShape)
            .border(1.dp, if (playing) Fl.orange else Fl.border, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
            "Плей",
            tint = if (playing) Color.Black else Fl.orange,
            modifier = Modifier.size(22.dp),
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
            .background(if (accent) Fl.orange else Fl.cardHi, RoundedCornerShape(10.dp))
            .border(1.dp, if (accent) Fl.orange else Fl.border, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (accent) Color.Black else Fl.text, fontSize = 10.5.sp,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}