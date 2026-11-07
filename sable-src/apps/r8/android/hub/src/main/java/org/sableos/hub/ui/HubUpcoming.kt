package org.sableos.hub.ui

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.sableos.design.sableFocusRing
import org.sableos.hub.HubText.t
import org.sableos.hub.HubUpcomingEvent

private const val CALENDAR_PACKAGE = "org.sableos.calendar"

/**
 * "Coming up": the next calendar events at the top of Hub, as BlackBerry 10's Hub showed them. It
 * is empty (and takes no room) without Sable Calendar, without calendar access, or without events;
 * a tap opens Sable Calendar.
 */
@Composable
internal fun HubUpcomingStrip(events: List<HubUpcomingEvent>) {
    if (events.isEmpty()) return
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = t("coming_up"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        events.forEach { event ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .sableFocusRing()
                        .clickable { openCalendar(context) }
                        .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = whenLabel(context, event),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "Today 14:00", "Tomorrow", "Mon 09:30": the day by name, then the time unless all day. */
private fun whenLabel(
    context: Context,
    event: HubUpcomingEvent,
): String {
    val day =
        DateUtils
            .getRelativeTimeSpanString(
                event.beginMillis,
                System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString()
    return if (event.allDay) {
        day
    } else {
        "$day ${DateUtils.formatDateTime(context, event.beginMillis, DateUtils.FORMAT_SHOW_TIME)}"
    }
}

private fun openCalendar(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(CALENDAR_PACKAGE) ?: return
    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
