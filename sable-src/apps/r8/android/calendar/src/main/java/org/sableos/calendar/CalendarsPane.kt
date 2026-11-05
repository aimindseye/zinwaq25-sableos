package org.sableos.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.sableos.calendar.CalendarText.t
import org.sableos.design.SableSize
import org.sableos.design.sableFocusRing

/**
 * Calendars (L): every calendar of every account on the phone with a show switch. Hidden calendars
 * leave the agenda, week, month, busy rings and Start's month; the switch is Android's own
 * CalendarContract VISIBLE flag, so other calendar apps follow it too.
 */
@Composable
internal fun CalendarsList(
    calendars: List<CalendarSource>,
    onToggle: (CalendarSource, Boolean) -> Unit,
) {
    Text(text = t("calendars"), style = MaterialTheme.typography.headlineMedium)
    Text(
        text = t("calendars_hint"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    if (calendars.isEmpty()) {
        Text(
            text = t("no_calendars"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        calendars.forEach { source -> CalendarSourceRow(source, onToggle) }
    }
}

@Composable
private fun CalendarSourceRow(
    source: CalendarSource,
    onToggle: (CalendarSource, Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = SableSize.TouchTarget)
                .sableFocusRing()
                .toggleable(value = source.visible, role = Role.Switch) { onToggle(source, it) }
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(Color(source.color or OPAQUE), CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (source.account.isNotEmpty()) {
                Text(
                    text = source.account,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // The row is the toggle; the switch only shows its state.
        Switch(checked = source.visible, onCheckedChange = null)
    }
}

private const val OPAQUE = -0x1000000
