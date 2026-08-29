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
import kotlin.math.hypot
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
private val burstInnerRadius = 100.dp
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
private val panelsInsideIcon = setOf(1, 3, 4, 7)

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
    val iconMarkUnit = iconUnit * LogoShapes.LAUNCHER_MARK_SCALE
    val iconRadius = LogoShapes.ICON_CIRCLE_DIAMETER_DP / 2f * density
    fun pageUnit(size: Size): Float = PAGE_DP_PER_UNIT * density * max(size.width, size.height) / (REFERENCE_HEIGHT_DP * density)
    fun heroMarkUnit(size: Size): Float = MARK_DP_PER_UNIT * density * markGrowth(size)
    fun markGrowth(size: Size): Float = (min(size.width, size.height) / (REFERENCE_WIDTH_DP * density)).coerceIn(1f, MAX_MARK_GROWTH)
    fun coveringRadius(size: Size): Float = hypot(size.width, size.height) / 2f
}

private fun DrawScope.drawSplash(t: Float, metrics: SplashMetrics) {
    clipPath(wipeRemainder(t)) {
        drawRect(LogoShapes.ink)
        clipPath(openingCircle(t, metrics)) {
            drawPage(t, metrics)
            drawBurst(t, metrics)
            drawMark(t, metrics)
        }
    }
    drawWipeEdge(t)
}

private fun DrawScope.openingCircle(t: Float, metrics: SplashMetrics): Path {
    val radius = SplashTimeline.lerp(metrics.iconRadius, metrics.coveringRadius(size), SplashTimeline.circleOpen(t))
    return Path().apply { addOval(Rect(center, radius)) }
}

private fun DrawScope.wipeRemainder(t: Float): Path {
    val edgeTop = wipeEdgeTop(t)
    return Path().apply {
        moveTo(edgeTop, 0f); lineTo(size.width * 2f, 0f); lineTo(size.width * 2f, size.height); lineTo(wipeEdgeBottom(edgeTop), size.height); close()
    }
}

private fun DrawScope.wipeEdgeTop(t: Float): Float = SplashTimeline.wipe(t) * size.width * WIPE_TRAVEL
private fun DrawScope.wipeEdgeBottom(edgeTop: Float): Float = edgeTop - size.width * WIPE_SLANT

private fun DrawScope.drawWipeEdge(t: Float) {
    val wipe = SplashTimeline.wipe(t)
    if (wipe <= 0f || wipe >= 1f) return
    val edgeTop = wipeEdgeTop(t)
    drawLine(LogoShapes.white, Offset(edgeTop, 0f), Offset(wipeEdgeBottom(edgeTop), size.height), strokeWidth = wipeEdgeStroke.toPx())
}

private fun DrawScope.drawPage(t: Float, metrics: SplashMetrics) {
    val unit = SplashTimeline.lerp(metrics.iconUnit, metrics.pageUnit(size), SplashTimeline.pageGrown(t))
    inLogoSpace(unit) {
        drawRect(LogoShapes.gutter, Offset(-PAGE_BLEED / 2, -PAGE_BLEED / 2), Size(PAGE_BLEED, PAGE_BLEED))
        rotate(LogoShapes.PAGE_TILT_DEGREES, LogoShapes.centre) {
            LogoShapes.panels.forEachIndexed { index, panel ->
                val filled = if (index in panelsInsideIcon) 1f else SplashTimeline.panelFilled(t, index)
                drawRect(LogoShapes.panelFill(index), panel.topLeft, panel.size, alpha = filled)
            }
            LogoShapes.panels.forEachIndexed { index, panel -> drawKeyline(t, index, panel) }
        }
        drawVignette()
    }
}

private fun DrawScope.drawKeyline(t: Float, index: Int, panel: Rect) {
    if (index in panelsInsideIcon) {
        drawRect(LogoShapes.ink, panel.topLeft, panel.size, style = Stroke(KEYLINE_INK_WIDTH))
        return
    }
    val drawn = SplashTimeline.keylineDrawn(t, index)
    if (drawn <= 0f) return
    val perimeter = LogoShapes.panelPerimeter(panel)
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

private fun DrawScope.drawVignette() {
    drawRect(
        brush = Brush.radialGradient(
            LogoShapes.VIGNETTE_CLEAR_STOP to Color.Transparent,
            1f to Color.Black.copy(alpha = LogoShapes.VIGNETTE_EDGE_ALPHA),
            center = LogoShapes.vignetteCentre,
            radius = LogoShapes.VIGNETTE_RADIUS,
        ),
        topLeft = Offset(-PAGE_BLEED / 2, -PAGE_BLEED / 2),
        size = Size(PAGE_BLEED, PAGE_BLEED),
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
    val unit = SplashTimeline.lerp(metrics.iconMarkUnit, metrics.heroMarkUnit(size), SplashTimeline.markPop(t))
    val (squashX, squashY) = SplashTimeline.markSquash(t)
    withTransform({
        translate(center.x, center.y)
        scale(unit * squashX, unit * squashY, Offset.Zero)
        translate(-LogoShapes.centre.x, -LogoShapes.centre.y)
    }) {
        drawBubble()
        drawK()
    }
}

private fun DrawScope.drawBubble() {
    val bubble = LogoShapes.bubble
    drawPath(bubble, LogoShapes.white, style = Stroke(LogoShapes.BUBBLE_HALO, join = StrokeJoin.Round))
    drawPath(bubble, LogoShapes.white)
    drawPath(bubble, LogoShapes.ink, style = Stroke(LogoShapes.BUBBLE_STROKE, join = StrokeJoin.Round))
}

private fun DrawScope.drawK() {
    val k = LogoShapes.k
    translate(LogoShapes.kShadowOffset.x, LogoShapes.kShadowOffset.y) {
        drawPath(k, LogoShapes.ink, style = Stroke(LogoShapes.K_SHADOW_STROKE, join = StrokeJoin.Round))
    }
    drawPath(
        k,
        Brush.verticalGradient(
            0f to LogoShapes.yellow, 1f to LogoShapes.red,
            startY = LogoShapes.K_GRADIENT_TOP, endY = LogoShapes.K_GRADIENT_BOTTOM,
        ),
    )
    drawPath(k, LogoShapes.ink, style = Stroke(LogoShapes.K_STROKE, join = StrokeJoin.Round))
}

private fun DrawScope.inLogoSpace(pxPerUnit: Float, block: DrawScope.() -> Unit) {
    withTransform({
        translate(center.x, center.y)
        scale(pxPerUnit, pxPerUnit, Offset.Zero)
        translate(-LogoShapes.centre.x, -LogoShapes.centre.y)
    }, block)
}
