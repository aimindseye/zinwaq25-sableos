// Ring drawing ported from Titan AI (https://github.com/andrehafner/aiassistant,
// ui/LauncherScreens.kt StatRing/NetRing), Copyright (c) 2026 Andre Hafner,
// MIT License. See THIRD_PARTY_NOTICES.md. Sable keeps the eased value change
// and drops Titan AI's always-running shimmer and glow pulse to save battery.

package org.sableos.start.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sable.start.model.StartText.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.sableos.start.platform.DeviceStats
import org.sableos.start.platform.DeviceStatsFormat
import org.sableos.start.platform.DeviceStatsSampler
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// SableOS 2.0 is black and white (UI direction, 2026-11-04): every arc is white, and only a value
// past its limit turns red (the error colour).
private const val SAMPLE_INTERVAL_MS = 3_000L

/** Samples the device every three seconds while HOME is started; null until the first sample. */
@Composable
private fun rememberDeviceStats(): DeviceStats? {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sampler = remember { DeviceStatsSampler() }
    var stats by remember { mutableStateOf<DeviceStats?>(null) }
    LaunchedEffect(lifecycle, sampler) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                stats = withContext(Dispatchers.IO) { sampler.sample(context) }
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }
    return stats
}

/**
 * The Start header's status as one line of stat labels (Sable 2.0, UI direction 2026-11-04: no
 * dials on home): Wi-Fi and mobile download rate, CPU, RAM, battery and cell bars, in grey. The
 * Device Monitor frame keeps its dials.
 */
@Composable
fun SableStatusLabels(modifier: Modifier = Modifier) {
    val st = rememberDeviceStats() ?: return
    val line = statusLabelLine(st, mobile = t("mobile"), cell = t("cell"))
    val low = st.battery <= LOW_BATTERY && !st.charging
    Text(
        text = line,
        style = MaterialTheme.typography.labelMedium,
        color = if (low) MaterialTheme.colorScheme.error else Color(0xFFA6A6A6),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { contentDescription = line },
    )
}

/** "Wi-Fi ↓1.2K · CPU 23% · RAM 61% · BAT 82%+ · Cell 3/4"; a part with no reading is left out. */
internal fun statusLabelLine(st: DeviceStats, mobile: String, cell: String): String =
    listOfNotNull(
        st.wifiDown?.let { "Wi-Fi ↓" + DeviceStatsFormat.rate(it) },
        st.mobileDown?.let { "$mobile ↓" + DeviceStatsFormat.rate(it) },
        st.cpu?.let { "CPU ${percent(it)}" },
        "RAM ${percent(st.ram)}",
        "BAT ${percent(st.battery)}" + if (st.charging) "+" else "",
        st.signal?.let { "$cell ${(it * CELL_BARS).roundToInt()}/$CELL_BARS" },
    ).joinToString(" · ")

private fun percent(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100).roundToInt()}%"

private const val LOW_BATTERY = 0.2f
private const val CELL_BARS = 4

/**
 * A 270° gauge: a dim track, an arc that eases to each new sample, a bright
 * cap at its tip and the value in the middle. A null fraction shows a dash.
 */
@Composable
internal fun StatRing(
    label: String,
    fraction: Float?,
    color: Color,
    size: Dp,
    /** A thinner arc just inside the first (upload under download). */
    innerFraction: Float? = null,
    innerColor: Color = color,
    /** Replaces the percentage in the middle; two short lines at most. */
    center: List<String>? = null,
    /** Fraction above which the arc turns the error colour; above 1 never. */
    hotAbove: Float = 0.85f,
) {
    val value by animateFloatAsState(
        targetValue = (fraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "ring",
    )
    val innerValue by animateFloatAsState(
        targetValue = (innerFraction ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "inner",
    )
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val arcColor = if (fraction != null && fraction > hotAbove) MaterialTheme.colorScheme.error else color
    val textColor = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val centerLines = center ?: listOf(if (fraction == null) "–" else "${(value * 100).roundToInt()}")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = "$label ${centerLines.joinToString(" ")}" },
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = size.toPx() * 0.11f
                val inset = stroke / 2 + 1.dp.toPx()
                val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                val startAngle = 135f
                val sweepMax = 270f
                drawArc(track, startAngle, sweepMax, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (fraction != null && value > 0.005f) {
                    val sweep = sweepMax * value
                    drawArc(
                        arcColor.copy(alpha = 0.22f),
                        startAngle,
                        sweep,
                        false,
                        topLeft,
                        arcSize,
                        style = Stroke(stroke * 1.9f, cap = StrokeCap.Round),
                    )
                    drawArc(arcColor, startAngle, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    val rad = Math.toRadians((startAngle + sweep).toDouble())
                    val r = arcSize.width / 2
                    drawCircle(
                        Color.White.copy(alpha = 0.9f),
                        radius = stroke * 0.45f,
                        center =
                            Offset(
                                this.size.width / 2 + (r * cos(rad)).toFloat(),
                                this.size.height / 2 + (r * sin(rad)).toFloat(),
                            ),
                    )
                }
                if (innerFraction != null) {
                    val innerInset = inset + stroke * 1.15f
                    val innerSize = Size(this.size.width - innerInset * 2, this.size.height - innerInset * 2)
                    val innerTopLeft = Offset(innerInset, innerInset)
                    val innerStroke = Stroke(stroke * 0.45f, cap = StrokeCap.Round)
                    drawArc(track, startAngle, sweepMax, false, innerTopLeft, innerSize, style = innerStroke)
                    if (innerValue > 0.005f) {
                        drawArc(
                            innerColor.copy(alpha = 0.9f),
                            startAngle,
                            sweepMax * innerValue,
                            false,
                            innerTopLeft,
                            innerSize,
                            style = innerStroke,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val twoLines = centerLines.size > 1
                centerLines.take(2).forEachIndexed { i, line ->
                    Text(
                        text = line,
                        fontSize = if (twoLines) 9.sp else 11.sp,
                        lineHeight = if (twoLines) 10.sp else 12.sp,
                        color = if (i == 0 && fraction != null) textColor else muted,
                        fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        Text(
            text = label,
            fontSize = 10.sp,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(y = (-3).dp),
        )
    }
}

