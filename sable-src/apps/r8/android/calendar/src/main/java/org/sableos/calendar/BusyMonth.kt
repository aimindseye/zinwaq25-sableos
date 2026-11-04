package org.sableos.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.sableos.calendar.CalendarText.t
import org.sableos.design.sableFocusRing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Cell and ring sizes: compact over the agenda, full in the month view (V). */
private data class RingSizes(
    val cell: Dp,
    val ring: Dp,
    val stroke: Dp,
)

private val COMPACT = RingSizes(cell = 36.dp, ring = 30.dp, stroke = 3.dp)
private val FULL = RingSizes(cell = 48.dp, ring = 40.dp, stroke = 4.dp)
private const val TRACK_ALPHA = 0.35f
private const val SELECTED_ALPHA = 0.22f
private const val RING_START_DEGREES = -90f

/**
 * The month with a busy ring on each day (CalendarBusy): the arc is how much of 08:00–20:00 is
 * booked, a thin outer ring marks an all-day event, and today is bold. Every day is focusable, so the
 * trackpad moves through the month and Enter picks a day.
 */
@Composable
internal fun BusyMonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    busy: Map<LocalDate, BusyDay>,
    compact: Boolean,
    onSelectDate: (LocalDate) -> Unit,
) {
    val sizes = if (compact) COMPACT else FULL
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth()) {
        weekdayInitials().forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CalendarBusy.monthCells(month).chunked(DAYS_PER_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).height(sizes.cell), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            BusyDayCell(date, busy[date], date == selectedDate, date == today, sizes) {
                                onSelectDate(date)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BusyDayCell(
    date: LocalDate,
    day: BusyDay?,
    selected: Boolean,
    today: Boolean,
    sizes: RingSizes,
    onClick: () -> Unit,
) {
    val arc = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = TRACK_ALPHA)
    val allDay = MaterialTheme.colorScheme.tertiary
    val sweep = CalendarBusy.sweepDegrees(day)
    val label = dayLabel(date, day)
    Box(
        modifier =
            Modifier
                .size(sizes.ring + sizes.stroke * 2)
                .clip(CircleShape)
                .background(if (selected) arc.copy(alpha = SELECTED_ALPHA) else Color.Transparent)
                .sableFocusRing()
                .clickable(onClick = onClick)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(sizes.ring)) {
            val stroke = sizes.stroke.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (day != null) {
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            }
            if (sweep > 0f) {
                drawArc(
                    arc,
                    RING_START_DEGREES,
                    sweep,
                    false,
                    Offset(inset, inset),
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            if (day?.allDay == true) {
                drawCircle(allDay, radius = size.minDimension / 2 + stroke, style = Stroke(stroke / 2))
            }
        }
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
            color = if (selected || today) arc else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val DAYS_PER_WEEK = 7

private fun weekdayInitials(): List<String> {
    val monday = LocalDate.of(2024, 1, 1)
    val format = DateTimeFormatter.ofPattern("EEEEE", Locale.getDefault())
    return (0 until DAYS_PER_WEEK).map { monday.plusDays(it.toLong()).format(format) }
}

private fun dayLabel(
    date: LocalDate,
    day: BusyDay?,
): String {
    val name = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
    return if (day == null) name else t("day_events", name, day.count.toString())
}
