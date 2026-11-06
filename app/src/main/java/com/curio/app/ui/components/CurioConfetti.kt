package com.curio.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.curio.app.ui.theme.CurioMotion
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════
// Confetti burst — the reward animation
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Confetti / sparkle burst — see Curio design contract section 0.5 (\"Rewarding
 * moments (topic revealed, entry saved) get a small confetti / sparkle
 * burst — a scatter of 6 to 10 tiny shapes in the category's accent color,
 * fading and falling with slight rotation, ~600ms total\").
 *
 * Upgraded with richer physics and more shape types. Particles are emitted
 * from the center, given an initial outward velocity + slight upward bias,
 * then fall with simulated gravity. Each particle also rotates and may
 * leave a fading trail.
 *
 * Supports single-color or multi-color bursts (pass a list for palette bursts).
 *
 * Usage:
 * ```
 * var burstKey by remember { mutableStateOf(0) }
 * ConfettiBurst(
 *     colors = listOf(category.accent),
 *     trigger = burstKey,
 *     modifier = Modifier.fillMaxSize(),
 *     onComplete = { /* maybe navigate */ }
 * )
 * ```
 */
@Composable
fun ConfettiBurst(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    trigger: Any = Unit,
    particleCount: Int = CurioMotion.ConfettiParticleCount,
    durationMs: Int = CurioMotion.Durations.Confetti,
    onComplete: () -> Unit = {}
) {
    val particles = remember(trigger) {
        ConfettiFactory.makeParticles(particleCount)
    }
    val progress = remember(trigger) { Animatable(0f) }

    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = LinearEasing
            )
        )
        onComplete()
    }

    Canvas(modifier = modifier) {
        drawConfettiParticles(
            particles = particles,
            progress = progress.value,
            colors = colors,
            canvasSize = size
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Ember burst — floating upward particle cloud
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Ember burst — a cloud of particles that float UPWARD (like glowing embers
 * or fireflies) instead of falling. Feels celebratory and magical. Use for
 * save success, onboarding completion, and other warm feel-good moments.
 *
 * Particles drift up with gentle horizontal sway, fade out, and rotate
 * slowly — like sparks rising from a campfire in the category accent color.
 */
@Composable
fun EmberBurst(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    trigger: Any = Unit,
    particleCount: Int = 12,
    durationMs: Int = CurioMotion.Durations.ConfettiLong,
    onComplete: () -> Unit = {}
) {
    val particles = remember(trigger) {
        EmberFactory.makeEmbers(particleCount)
    }
    val progress = remember(trigger) { Animatable(0f) }

    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = FastOutSlowInEasing
            )
        )
        onComplete()
    }

    Canvas(modifier = modifier) {
        drawEmberParticles(
            embers = particles,
            progress = progress.value,
            colors = colors,
            canvasSize = size
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Sparkle ring — expanding + fading circle accent
// ═══════════════════════════════════════════════════════════════════════════

/**
 * A simple circular spark that pulses around a point — used inline as a
 * sparkle accent (e.g. near a topic's image on Reveal). Independent of the
 * full confetti burst so it can be embedded in cards without triggering
 * the whole animation.
 *
 * Upgraded to render multiple concentric rings that expand at different
 * rates for a richer \"ripple\" effect.
 *
 * @param ringCount Number of concentric rings (default 3 for a ripple effect).
 */
@Composable
fun CurioSparkle(
    color: Color,
    modifier: Modifier = Modifier,
    trigger: Any = Unit,
    size: Dp = 24.dp,
    ringCount: Int = 3,
    onComplete: () -> Unit = {}
) {
    val progress = remember(trigger) { Animatable(0f) }

    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = CurioMotion.Durations.Confetti,
                easing = LinearEasing
            )
        )
        onComplete()
    }

    Canvas(modifier = modifier) {
        val maxRadiusPx = (this.size.minDimension / 2f).coerceAtLeast(1f)
        repeat(ringCount) { ringIndex ->
            val ringDelay = ringIndex * 0.12f
            val ringProgress = ((progress.value - ringDelay) / (1f - ringDelay))
                .coerceIn(0f, 1f)
            val radius = maxRadiusPx * ringProgress
            val alpha = ((1f - ringProgress) * 0.7f).coerceIn(0f, 1f)
            if (alpha > 0f && radius > 0f) {
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(this.size.width / 2f, this.size.height / 2f),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Particle data types
// ═══════════════════════════════════════════════════════════════════════════

/**
 * A single confetti particle's parameters. Position is parameterized as
 * fractions of the canvas size so the particle moves correctly regardless
 * of the actual screen dimensions. Velocity is in normalized units per
 * millisecond (multiplied by progress at draw time).
 */
private data class ConfettiParticle(
    val angleDeg: Float,            // 0 = up, increases clockwise
    val speedPxPerMs: Float,        // initial outward speed (px per ms)
    val upwardBiasPxPerMs: Float,  // initial upward kick (px per ms)
    val gravityPxPerMs2: Float,    // downward acceleration
    val rotationDeg: Float,        // initial rotation
    val rotationSpeedDegPerMs: Float, // rotation per ms
    val sizePx: Float,             // particle bounding box size in px
    val shape: ConfettiShape,
    val colorIndex: Int            // which color from the palette (0-based)
)

private enum class ConfettiShape {
    Circle, Star4, Star5, Ribbon, Diamond, Heart
}

/**
 * A single ember particle — floats UPWARD with gentle horizontal sway.
 */
private data class EmberParticle(
    val angleDeg: Float,            // initial direction (radial from center)
    val speedPxPerMs: Float,        // outward speed
    val swayAmplitudePx: Float,     // horizontal sine-wave amplitude
    val swayFrequency: Float,       // sine-wave frequency
    val riseSpeedPxPerMs: Float,    // upward velocity
    val rotationSpeedDegPerMs: Float,
    val sizePx: Float,
    val shape: ConfettiShape,
    val colorIndex: Int,
    val initialOpacity: Float       // 0.5 to 1.0 variability
)

// ═══════════════════════════════════════════════════════════════════════════
// Confetti factory
// ═══════════════════════════════════════════════════════════════════════════

private object ConfettiFactory {
    fun makeParticles(count: Int): List<ConfettiParticle> = List(count) {
        val angle = Random.nextFloat() * 360f
        ConfettiParticle(
            angleDeg = angle,
            speedPxPerMs = Random.nextFloat() * 0.40f + 0.15f,
            upwardBiasPxPerMs = Random.nextFloat() * 0.30f + 0.20f,
            gravityPxPerMs2 = 0.0009f + Random.nextFloat() * 0.0005f,
            rotationDeg = Random.nextFloat() * 360f,
            rotationSpeedDegPerMs = (Random.nextFloat() - 0.5f) * 1.2f,
            sizePx = 6f + Random.nextFloat() * 6f,
            shape = ConfettiShape.values()[Random.nextInt(ConfettiShape.values().size)],
            colorIndex = 0 // will be resolved at draw time
        )
    }
}

private object EmberFactory {
    fun makeEmbers(count: Int): List<EmberParticle> = List(count) {
        val angle = Random.nextFloat() * 360f
        EmberParticle(
            angleDeg = angle,
            speedPxPerMs = Random.nextFloat() * 0.18f + 0.05f,
            swayAmplitudePx = Random.nextFloat() * 30f + 10f,
            swayFrequency = Random.nextFloat() * 0.03f + 0.01f,
            riseSpeedPxPerMs = Random.nextFloat() * 0.25f + 0.15f,
            rotationSpeedDegPerMs = (Random.nextFloat() - 0.5f) * 0.6f,
            sizePx = 5f + Random.nextFloat() * 8f,
            shape = ConfettiShape.values()[Random.nextInt(ConfettiShape.values().size)],
            colorIndex = 0,
            initialOpacity = 0.5f + Random.nextFloat() * 0.5f
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Draw routines
// ═══════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawConfettiParticles(
    particles: List<ConfettiParticle>,
    progress: Float,
    colors: List<Color>,
    canvasSize: Size
) {
    if (canvasSize.width <= 0f || canvasSize.height <= 0f) return
    if (colors.isEmpty()) return

    val cx = canvasSize.width / 2f
    val cy = canvasSize.height / 2f
    val totalMs = CurioMotion.Durations.Confetti.toFloat()
    val t = progress * totalMs

    particles.forEach { p ->
        val dist = p.speedPxPerMs * t
        val upOffset = p.upwardBiasPxPerMs * t - 0.5f * p.gravityPxPerMs2 * t * t
        val angleRad = Math.toRadians(p.angleDeg.toDouble()).toFloat()
        val dx = cos(angleRad) * dist
        val dy = sin(angleRad) * dist + upOffset
        val px = cx + dx
        val py = cy + dy

        // Fade during second half
        val alpha = if (progress < 0.5f) 1f else (1f - (progress - 0.5f) * 2f).coerceIn(0f, 1f)
        val colorIndex = p.colorIndex % colors.size
        val tinted = colors[colorIndex].copy(alpha = alpha)

        val rotation = p.rotationDeg + p.rotationSpeedDegPerMs * t

        rotate(degrees = rotation, pivot = Offset(px, py)) {
            drawConfettiShape(p.shape, tinted, px, py, p.sizePx)
        }
    }
}

private fun DrawScope.drawEmberParticles(
    embers: List<EmberParticle>,
    progress: Float,
    colors: List<Color>,
    canvasSize: Size
) {
    if (canvasSize.width <= 0f || canvasSize.height <= 0f) return
    if (colors.isEmpty()) return

    val cx = canvasSize.width / 2f
    val cy = canvasSize.height / 2f
    val totalMs = CurioMotion.Durations.ConfettiLong.toFloat()
    val t = progress * totalMs

    embers.forEach { e ->
        // Radial spread + upward rise + sine-wave horizontal sway
        val angleRad = Math.toRadians(e.angleDeg.toDouble()).toFloat()
        val dist = e.speedPxPerMs * t
        val sway = sin(t * e.swayFrequency) * e.swayAmplitudePx
        val dx = cos(angleRad) * dist + sway
        // Rise upward (negative = up on screen)
        val dy = sin(angleRad) * dist - e.riseSpeedPxPerMs * t
        val px = cx + dx
        val py = cy + dy

        // Fade out at the end
        val alpha = (1f - progress).coerceIn(0f, 1f) * e.initialOpacity
        val colorIndex = e.colorIndex % colors.size
        val tinted = colors[colorIndex].copy(alpha = alpha)

        val rotation = e.rotationSpeedDegPerMs * t

        rotate(degrees = rotation, pivot = Offset(px, py)) {
            drawConfettiShape(e.shape, tinted, px, py, e.sizePx)
        }
    }
}

private fun DrawScope.drawConfettiShape(
    shape: ConfettiShape,
    color: Color,
    cx: Float,
    cy: Float,
    size: Float
) {
    val halfSize = size / 2f
    when (shape) {
        ConfettiShape.Circle -> {
            drawCircle(
                color = color,
                radius = halfSize,
                center = Offset(cx, cy)
            )
        }
        ConfettiShape.Star4 -> {
            // 4-point star
            val path = Path().apply {
                moveTo(cx, cy - halfSize)
                lineTo(cx + halfSize * 0.35f, cy - halfSize * 0.35f)
                lineTo(cx + halfSize, cy)
                lineTo(cx + halfSize * 0.35f, cy + halfSize * 0.35f)
                lineTo(cx, cy + halfSize)
                lineTo(cx - halfSize * 0.35f, cy + halfSize * 0.35f)
                lineTo(cx - halfSize, cy)
                lineTo(cx - halfSize * 0.35f, cy - halfSize * 0.35f)
                close()
            }
            drawPath(path = path, color = color)
        }
        ConfettiShape.Star5 -> {
            // 5-point star
            val path = Path()
            for (i in 0 until 10) {
                val a = Math.toRadians((i * 36.0 - 90.0)).toFloat()
                val r = if (i % 2 == 0) halfSize else halfSize * 0.38f
                val x = cx + cos(a) * r
                val y = cy + sin(a) * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path = path, color = color)
        }
        ConfettiShape.Ribbon -> {
            val w = 2.5f
            val h = size
            drawRect(
                color = color,
                topLeft = Offset(cx - w / 2f, cy - h / 2f),
                size = Size(w, h)
            )
        }
        ConfettiShape.Diamond -> {
            val path = Path().apply {
                moveTo(cx, cy - halfSize)
                lineTo(cx + halfSize * 0.6f, cy)
                lineTo(cx, cy + halfSize)
                lineTo(cx - halfSize * 0.6f, cy)
                close()
            }
            drawPath(path = path, color = color)
        }
        ConfettiShape.Heart -> {
            val s = halfSize * 0.7f
            val path = Path().apply {
                // Simple heart using two arcs + triangle bottom
                moveTo(cx, cy + s * 0.7f)
                // Left lobe
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        cx - s, cy - s * 0.6f, cx, cy + s * 0.2f
                    ),
                    startAngleDegrees = 210f,
                    sweepAngleDegrees = 150f,
                    forceMoveTo = false
                )
                // Right lobe
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        cx, cy - s * 0.6f, cx + s, cy + s * 0.2f
                    ),
                    startAngleDegrees = 240f,
                    sweepAngleDegrees = 150f,
                    forceMoveTo = false
                )
                lineTo(cx, cy + s)
                close()
            }
            drawPath(path = path, color = color)
        }
    }
}
