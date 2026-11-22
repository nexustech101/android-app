package com.sitesnap.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitesnap.app.ui.theme.SiteSnapColors
import com.sitesnap.app.ui.theme.SiteSnapType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** Horizontal distance on the zoom dial between one zoom level and double it (1× to 2×, 2× to 4×…). */
private val DialPerOctave = 96.dp

/** Lens-style stops, like a phone camera: ultra-wide when the camera has one, then 1×, 2×, 5×, 10×. */
internal fun zoomStops(range: ClosedFloatingPointRange<Float>): List<Float> = buildList {
    if (range.start < 0.95f) add(range.start)
    add(1f)
    listOf(2f, 5f, 10f).filter { it <= range.endInclusive + 0.01f }.forEach(::add)
}

/** "0.5" → ".5", "2.0" → "2", "2.43" → "2.4". */
internal fun formatZoom(z: Float): String {
    val r = (z * 10).roundToInt() / 10f
    return when {
        r < 1f -> ".${(r * 10).roundToInt()}"
        r % 1f == 0f -> r.toInt().toString()
        else -> String.format(Locale.US, "%.1f", r)
    }
}

/**
 * Zoom controls under the viewfinder, like a phone's camera app:
 * - pinch the viewfinder with two fingers (handled by the camera screen),
 * - tap a chip to jump to a lens stop,
 * - tap the active chip, or drag sideways on the controls, for a slider (ruler dial) to zoom precisely.
 */
@Composable
internal fun ZoomControls(
    zoom: Float,
    range: ClosedFloatingPointRange<Float>,
    dialVisible: Boolean,
    onStopTapped: (Float) -> Unit,
    onScrub: (Float) -> Unit,
    onOpenSlider: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (range.endInclusive - range.start < 0.05f) return
    val stops = zoomStops(range)
    val haptics = LocalHapticFeedback.current
    val currentZoom by rememberUpdatedState(zoom)
    val currentRange by rememberUpdatedState(range)

    Box(
        modifier
            .fillMaxWidth()
            .height(84.dp)
            .pointerInput(Unit) {
                val perOctave = DialPerOctave.toPx()
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    val old = currentZoom
                    // Finger left pulls higher zoom values toward the centre mark, like turning a lens ring.
                    val next = 2f.pow(log2(old) - dx / perOctave).coerceIn(currentRange.start, currentRange.endInclusive)
                    if (zoomStops(currentRange).any { (old - it) * (next - it) < 0f }) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onScrub(next)
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedContent(
            targetState = dialVisible,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "zoom",
        ) { dial ->
            if (dial) ZoomDial(zoom, range, stops) else ZoomChips(zoom, stops, onStopTapped, onOpenSlider)
        }
    }
}

@Composable
private fun ZoomChips(zoom: Float, stops: List<Float>, onStopTapped: (Float) -> Unit, onOpenSlider: () -> Unit) {
    // The chip for the lens currently in use shows the live zoom value, e.g. "2.4×".
    val activeIndex = stops.indexOfLast { zoom >= it - 0.05f }.coerceAtLeast(0)
    Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.padding(bottom = 6.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stops.forEachIndexed { i, stop ->
                val active = i == activeIndex
                val size by animateDpAsState(if (active) 40.dp else 34.dp, label = "chip")
                Box(
                    Modifier.size(size).clip(CircleShape)
                        .background(if (active) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.08f))
                        .clickable(role = Role.Button, onClickLabel = if (active) "Zoom slider" else "Zoom ${formatZoom(stop)}×") {
                            if (active) onOpenSlider() else onStopTapped(stop)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (active) "${formatZoom(zoom)}×" else formatZoom(stop),
                        style = SiteSnapType.Caption.copy(fontWeight = FontWeight.Bold, fontSize = if (active) 12.sp else 11.sp),
                        color = if (active) SiteSnapColors.Ink else Color.White,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomDial(zoom: Float, range: ClosedFloatingPointRange<Float>, stops: List<Float>) {
    val measurer = rememberTextMeasurer()
    val ticks = remember(range) { dialTicks(range) }
    val labelStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${formatZoom(zoom)}×",
            style = SiteSnapType.Label,
            color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            val perOctave = DialPerOctave.toPx()
            val cx = size.width / 2
            val current = log2(zoom)
            val base = 14.dp.toPx()
            for (t in ticks) {
                val x = cx + (log2(t) - current) * perOctave
                if (x < -4f || x > size.width + 4f) continue
                val fade = (1f - (abs(x - cx) / cx).pow(2)).coerceIn(0f, 1f)
                val major = stops.any { abs(it - t) < 0.01f }
                val h = if (major) 14.dp.toPx() else 7.dp.toPx()
                drawLine(
                    Color.White.copy(alpha = (if (major) 0.95f else 0.55f) * fade),
                    Offset(x, base), Offset(x, base + h),
                    strokeWidth = (if (major) 2.dp else 1.dp).toPx(), cap = StrokeCap.Round,
                )
                if (major) {
                    val text = measurer.measure(formatZoom(t), labelStyle)
                    drawText(text, color = Color.White.copy(alpha = fade), topLeft = Offset(x - text.size.width / 2f, base + h + 4.dp.toPx()))
                }
            }
            // Centre mark: the zoom in use.
            drawLine(SiteSnapColors.Accent, Offset(cx, base - 6.dp.toPx()), Offset(cx, base + 18.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** Dial ticks: every 0.1× up to 2×, 0.25× to 5×, 0.5× to 10×, then whole steps. */
private fun dialTicks(range: ClosedFloatingPointRange<Float>): List<Float> = buildList {
    add(range.start)
    var t = (range.start * 10).roundToInt() / 10f
    while (t < range.endInclusive - 0.001f) {
        t += when {
            t < 2f -> 0.1f
            t < 5f -> 0.25f
            t < 10f -> 0.5f
            else -> 1f
        }
        t = (t * 100).roundToInt() / 100f
        if (t > range.start + 0.01f && t <= range.endInclusive + 0.001f) add(t)
    }
}

/** Focus ring that pops in where the user tapped the viewfinder. */
@Composable
internal fun FocusRing(point: Offset?, key: Int) {
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(key) {
        if (point == null) return@LaunchedEffect
        alpha.snapTo(1f)
        scale.snapTo(1.45f)
        scale.animateTo(1f)
        kotlinx.coroutines.delay(700)
        alpha.animateTo(0f)
    }
    if (point == null) return
    Canvas(Modifier.fillMaxSize()) {
        val r = 34.dp.toPx() * scale.value
        drawCircle(Color.White.copy(alpha = alpha.value), radius = r, center = point, style = Stroke(1.5.dp.toPx()))
        drawCircle(SiteSnapColors.Accent.copy(alpha = alpha.value), radius = 3.dp.toPx(), center = point)
    }
}
