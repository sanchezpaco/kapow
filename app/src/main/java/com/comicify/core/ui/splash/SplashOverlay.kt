package com.comicify.core.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val REFERENCE_WIDTH_DP = 360f
private const val REFERENCE_HEIGHT_DP = 800f
private const val PAGE_DP_PER_UNIT = 7.2f
private const val MARK_DP_PER_UNIT = 3.4f
private const val MAX_MARK_GROWTH = 1.6f
private const val PAGE_BLEED = 900f
private const val BURST_LINES = 14
private const val BURST_ANGLE_OFFSET = 0.3f
private val burstInnerRadius = 110.dp
private val burstOuterRadius = 300.dp
private val burstLineLength = 28.dp
private val burstLineMinLength = 6.dp
private val burstStroke = 4.dp
private val wipeEdgeStroke = 10.dp
private const val WIPE_TRAVEL = 1.7f
private const val WIPE_SLANT = 0.7f
private const val KEYLINE_INK_WIDTH = 1.6f
private const val KEYLINE_DRAWING_WIDTH = 2.2f
private const val WARM_UP_FRAMES = 3

@Composable
fun SplashOverlay(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        repeat(WARM_UP_FRAMES) { withFrameNanos {} }
        progress.animateTo(SplashTimeline.DURATION_MS.toFloat(), tween(SplashTimeline.DURATION_MS, easing = LinearEasing))
        onFinished()
    }
    val density = LocalDensity.current
    val metrics = remember(density) { SplashMetrics(density.density) }
    Canvas(modifier.fillMaxSize()) { drawSplash(progress.value, metrics) }
}

private class SplashMetrics(val density: Float) {
    val iconUnit = LogoShapes.ICON_CIRCLE_DIAMETER_DP / LogoShapes.ICON_VISIBLE_UNITS * density
    val iconRadius = LogoShapes.ICON_CIRCLE_DIAMETER_DP / 2f * density
    fun pageUnit(size: Size): Float = PAGE_DP_PER_UNIT * density * max(size.width, size.height) / (REFERENCE_HEIGHT_DP * density)
    fun markUnit(size: Size): Float {
        val growth = min(size.width, size.height) / (REFERENCE_WIDTH_DP * density)
        return MARK_DP_PER_UNIT * density * growth.coerceIn(1f, MAX_MARK_GROWTH)
    }
    fun markGrowth(size: Size): Float = markUnit(size) / (MARK_DP_PER_UNIT * density)
}

private fun DrawScope.drawSplash(t: Float, metrics: SplashMetrics) {
    val wipe = SplashTimeline.wipe(t)
    val edgeTop = wipe * size.width * WIPE_TRAVEL
    val edgeBottom = edgeTop - size.width * WIPE_SLANT
    val remaining = Path().apply {
        moveTo(edgeTop, 0f); lineTo(size.width * 2f, 0f); lineTo(size.width * 2f, size.height); lineTo(edgeBottom, size.height); close()
    }
    clipPath(remaining) {
        drawRect(LogoShapes.ink)
        drawPage(t, metrics)
        drawBurst(t, metrics)
        drawMark(t, metrics)
        drawSystemIcon(t, metrics)
    }
    if (wipe > 0f && wipe < 1f) {
        drawLine(LogoShapes.white, Offset(edgeTop, 0f), Offset(edgeBottom, size.height), strokeWidth = wipeEdgeStroke.toPx())
    }
}

private fun DrawScope.drawPage(t: Float, metrics: SplashMetrics) {
    val gutter = SplashTimeline.gutter(t)
    inLogoSpace(metrics.pageUnit(size)) {
        drawRect(LogoShapes.gutter, Offset(-PAGE_BLEED / 2, -PAGE_BLEED / 2), Size(PAGE_BLEED, PAGE_BLEED), alpha = gutter)
        rotate(LogoShapes.PAGE_TILT_DEGREES, LogoShapes.centre) {
            LogoShapes.panels.forEachIndexed { index, panel ->
                drawRect(LogoShapes.panelFill(index), panel.topLeft, panel.size, alpha = SplashTimeline.panelFilled(t, index))
            }
            LogoShapes.panels.forEachIndexed { index, panel -> drawKeyline(t, index, panel) }
        }
        drawVignette(gutter)
    }
}

private fun DrawScope.drawKeyline(t: Float, index: Int, panel: Rect) {
    val perimeter = LogoShapes.panelPerimeter(panel)
    val drawn = SplashTimeline.keylineDrawn(t, index)
    if (drawn <= 0f) return
    val inked = SplashTimeline.keylinesInked(t)
    drawRect(
        color = if (inked) LogoShapes.ink else LogoShapes.white,
        topLeft = panel.topLeft,
        size = panel.size,
        style = Stroke(
            width = if (inked) KEYLINE_INK_WIDTH else KEYLINE_DRAWING_WIDTH,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(perimeter, perimeter), phase = perimeter * (1f - drawn)),
        ),
    )
}

private fun DrawScope.drawVignette(alpha: Float) {
    if (alpha <= 0f) return
    drawRect(
        brush = Brush.radialGradient(
            LogoShapes.VIGNETTE_CLEAR_STOP to Color.Transparent,
            1f to Color.Black.copy(alpha = LogoShapes.VIGNETTE_EDGE_ALPHA),
            center = LogoShapes.vignetteCentre,
            radius = LogoShapes.VIGNETTE_RADIUS,
        ),
        topLeft = Offset(-PAGE_BLEED / 2, -PAGE_BLEED / 2),
        size = Size(PAGE_BLEED, PAGE_BLEED),
        alpha = alpha,
    )
}

private fun DrawScope.drawBurst(t: Float, metrics: SplashMetrics) {
    val p = SplashTimeline.burst(t)
    if (p <= 0f || p >= 1f) return
    val growth = metrics.markGrowth(size)
    val radius = SplashTimeline.lerp(burstInnerRadius.toPx(), burstOuterRadius.toPx(), SplashTimeline.outCubic(p)) * growth
    val length = (burstLineLength.toPx() * (1f - p) + burstLineMinLength.toPx()) * growth
    repeat(BURST_LINES) { i ->
        val angle = i.toFloat() / BURST_LINES * 2f * PI.toFloat() + BURST_ANGLE_OFFSET
        val direction = Offset(cos(angle), sin(angle))
        drawLine(
            color = LogoShapes.white,
            start = center + direction * radius,
            end = center + direction * (radius + length),
            strokeWidth = burstStroke.toPx() * growth,
            cap = StrokeCap.Round,
            alpha = 1f - p,
        )
    }
}

private fun DrawScope.drawMark(t: Float, metrics: SplashMetrics) {
    val bubbleScale = SplashTimeline.bubbleScale(t)
    val (squashX, squashY) = SplashTimeline.bubbleSquash(t)
    inLogoSpace(metrics.markUnit(size)) {
        if (bubbleScale > 0f) {
            scale(bubbleScale * squashX, bubbleScale * squashY, LogoShapes.bubbleCentre) { drawBubble() }
        }
        if (SplashTimeline.kVisible(t)) {
            scale(SplashTimeline.kScale(t), LogoShapes.kCentre) { drawK() }
        }
    }
}

private fun DrawScope.drawSystemIcon(t: Float, metrics: SplashMetrics) {
    val out = SplashTimeline.iconOut(t)
    if (out >= 1f) return
    val alpha = 1f - out
    scale(SplashTimeline.iconScale(t), center) {
        clipPath(Path().apply { addOval(Rect(center, metrics.iconRadius)) }) {
            inLogoSpace(metrics.iconUnit) {
                drawRect(LogoShapes.gutter, Offset(-PAGE_BLEED / 2, -PAGE_BLEED / 2), Size(PAGE_BLEED, PAGE_BLEED), alpha = alpha)
                rotate(LogoShapes.PAGE_TILT_DEGREES, LogoShapes.centre) {
                    LogoShapes.panels.forEachIndexed { index, panel ->
                        drawRect(LogoShapes.panelFill(index), panel.topLeft, panel.size, alpha = alpha)
                        drawRect(LogoShapes.ink, panel.topLeft, panel.size, style = Stroke(LogoShapes.PANEL_STROKE), alpha = alpha)
                    }
                }
                drawVignette(alpha)
            }
            inLogoSpace(metrics.iconUnit * LogoShapes.LAUNCHER_MARK_SCALE) {
                drawBubble(alpha)
                drawK(alpha)
            }
        }
    }
}

private fun DrawScope.drawBubble(alpha: Float = 1f) {
    val bubble = LogoShapes.bubble
    drawPath(bubble, LogoShapes.white, alpha = alpha, style = Stroke(LogoShapes.BUBBLE_HALO, join = StrokeJoin.Round))
    drawPath(bubble, LogoShapes.white, alpha = alpha)
    drawPath(bubble, LogoShapes.ink, alpha = alpha, style = Stroke(LogoShapes.BUBBLE_STROKE, join = StrokeJoin.Round))
}

private fun DrawScope.drawK(alpha: Float = 1f) {
    val k = LogoShapes.k
    translate(LogoShapes.kShadowOffset.x, LogoShapes.kShadowOffset.y) {
        drawPath(k, LogoShapes.ink, alpha = alpha, style = Stroke(LogoShapes.K_SHADOW_STROKE, join = StrokeJoin.Round))
    }
    drawPath(
        k,
        Brush.verticalGradient(
            0f to LogoShapes.yellow, 1f to LogoShapes.red,
            startY = LogoShapes.K_GRADIENT_TOP, endY = LogoShapes.K_GRADIENT_BOTTOM,
        ),
        alpha = alpha,
    )
    drawPath(k, LogoShapes.ink, alpha = alpha, style = Stroke(LogoShapes.K_STROKE, join = StrokeJoin.Round))
}

private fun DrawScope.inLogoSpace(pxPerUnit: Float, block: DrawScope.() -> Unit) {
    withTransform({
        translate(center.x, center.y)
        scale(pxPerUnit, pxPerUnit, Offset.Zero)
        translate(-LogoShapes.centre.x, -LogoShapes.centre.y)
    }, block)
}
