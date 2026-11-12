package org.sableos.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import org.sableos.design.SableActionButton
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
    actions: CalendarsActions,
    colors: Map<Long, Int>,
    onColor: (CalendarSource) -> Unit,
) {
    Text(text = t("calendars"), style = MaterialTheme.typography.headlineMedium)
    Text(
        text = t("calendars_hint"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    // Connecting calendars comes first: Android's add-account screen, a Phone calendar, an .ics file.
    SableActionButton(text = t("add_account"), primary = false, onClick = actions.onAddAccount)
    Text(
        text = t("add_account_hint"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SableActionButton(text = t("phone_calendar"), primary = false, onClick = actions.onPhoneCalendar)
    SableActionButton(text = t("import_ics"), primary = false, onClick = actions.onImportIcs)
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
        calendars.forEach { source -> CalendarSourceRow(source, colors[source.id] ?: source.color, onToggle, onColor) }
    }
}

/** What the three "connect a calendar" buttons do. */
internal class CalendarsActions(
    val onAddAccount: () -> Unit,
    val onPhoneCalendar: () -> Unit,
    val onImportIcs: () -> Unit,
)

@Composable
private fun CalendarSourceRow(
    source: CalendarSource,
    color: Int,
    onToggle: (CalendarSource, Boolean) -> Unit,
    onColor: (CalendarSource) -> Unit,
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
        // The colour its events show; a tap goes to the next colour in the palette.
        Box(
            Modifier
                .size(SableSize.TouchTarget)
                .sableFocusRing()
                .clickable(role = Role.Button, onClickLabel = t("colour")) { onColor(source) },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(20.dp).background(Color(color or OPAQUE), CircleShape))
        }
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
