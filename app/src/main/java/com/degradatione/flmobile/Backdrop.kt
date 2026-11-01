package com.degradatione.flmobile

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Живой фон в духе трёх референсов:
 *  • Dark Star Labs — чёрный + янтарный #FA5D29 + редкий холодный #49B3FC;
 *  • Yichen Xie — тонкие контурные пузыри, мягко плывущие по канвасу;
 *  • UntilLabs — «жизнь» частиц: потоковое поле, дыхание, реакция на удар.
 */
private class Mote(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var r: Float,
    var a: Float,
    var phase: Float,
    var cold: Boolean,
)

private fun noiseBitmap(size: Int = 160): ImageBitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val rnd = Random(7)
    val px = IntArray(size * size)
    for (i in px.indices) {
        val v = rnd.nextInt(0, 255)
        px[i] = (v shl 24) or (v shl 16) or (v shl 8) or v
    }
    bmp.setPixels(px, 0, size, 0, 0, size, size)
    return bmp.asImageBitmap()
}

@Composable
fun MysticBackdrop(activeStep: Int, modifier: Modifier = Modifier) {
    val rnd = remember { Random(20261101) }
    val motes = remember {
        List(46) {
            Mote(
                x = rnd.nextFloat(),
                y = rnd.nextFloat(),
                vx = 0f,
                vy = 0f,
                r = 0.012f + rnd.nextFloat() * 0.05f,
                a = 0.05f + rnd.nextFloat() * 0.13f,
                phase = rnd.nextFloat() * 6.28f,
                cold = rnd.nextFloat() < 0.18f,
            )
        }
    }
    var t by remember { mutableFloatStateOf(0f) }
    var pulse by remember { mutableFloatStateOf(0f) }
    var tick by remember { mutableIntStateOf(0) }
    val grain = remember { noiseBitmap() }
    val grainPaint = remember {
        Paint().apply {
            shader = ImageShader(grain, TileMode.Repeated, TileMode.Repeated)
        }
    }

    // каждый удар подталкивает облако — «сайт дышит» вместе с битом
    LaunchedEffect(activeStep) {
        if (activeStep >= 0) pulse = 1f
    }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            t += dt
            pulse = (pulse - dt * 1.6f).coerceAtLeast(0f)
            for (m in motes) {
                // потоковое поле: две синусоиды дают «течение», как curl noise в UntilLabs
                val flowX = sin(m.y * 7.0f + t * 0.33f + m.phase) * 0.010f
                val flowY = (cos(m.x * 5.5f + t * 0.27f + m.phase) * 0.010f) - 0.004f
                m.vx = (m.vx + flowX * dt) * 0.985f
                m.vy = (m.vy + flowY * dt) * 0.985f
                m.x += (m.vx + 0.006f * dt) * 1f
                m.y += m.vy
                if (m.x < -0.1f) m.x = 1.1f
                if (m.x > 1.1f) m.x = -0.1f
                if (m.y < -0.1f) m.y = 1.1f
                if (m.y > 1.1f) m.y = -0.1f
            }
            tick++
        }
    }

    Canvas(modifier) {
        tick // подписка на кадр (инвалидация только фазы рисования)
        val w = size.width
        val h = size.height
        val min = minOf(w, h)

        // мягкая тёмная виньетка, чтобы панели читались
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color(0xFF0C0C11), Color(0xFF040405)),
                center = Offset(w * 0.5f, h * 0.35f),
                radius = maxOf(w, h) * 0.85f,
            ),
            size = size,
        )

        for (m in motes) {
            val cx = m.x * w
            val cy = m.y * h
            val rr = m.r * min * (1f + pulse * 0.25f)
            val base = if (m.cold) Fl.cold else Fl.ember
            val alpha = (m.a + pulse * 0.10f) * (0.75f + 0.25f * sin(t * 0.9f + m.phase))
            drawCircle(
                color = base.copy(alpha = alpha.coerceIn(0f, 0.4f)),
                radius = rr,
                center = Offset(cx, cy),
                style = Stroke(width = 1.2f),
            )
            // редкие внутренние точки — как «искры» в пузыре
            if (m.phase > 4.6f) {
                drawCircle(
                    color = base.copy(alpha = alpha * 0.7f),
                    radius = 1.4f,
                    center = Offset(cx + rr * 0.4f, cy - rr * 0.3f),
                )
            }
        }

        // мелкое зерно поверх фона — «плёночность»
        drawIntoCanvas { canvas ->
            grainPaint.alpha = 0.035f
            canvas.drawRect(
                androidx.compose.ui.geometry.Rect(Offset.Zero, Size(w, h)),
                grainPaint,
            )
        }
    }
}

/** Моно-подпись в духе технической типографики Dark Star Labs. */
@Composable
fun MonoLabel(text: String, size: Int = 9, color: Color = Fl.dim) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = size.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        letterSpacing = 1.2.sp,
    )
}
