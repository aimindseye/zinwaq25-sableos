package org.sableos.calendar

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sableos.design.SableActionButton
import org.sableos.design.SableGlobalTheme
import org.sableos.design.SableRefreshableSurface
import org.sableos.design.SableSpacing
import org.sableos.design.sableFocusRing
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Formatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var repository: CalendarRepository
    private var refreshGeneration by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = CalendarRepository(applicationContext)

        setContent {
            val permissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    refreshGeneration += 1
                }

            SableGlobalTheme(window = window) {
                CalendarApp(
                    repository = repository,
                    refreshGeneration = refreshGeneration,
                    onRefresh = { refreshGeneration += 1 },
                    onRequestPermissions = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR,
                            ),
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) {
            refreshGeneration += 1
        }
    }
}

private enum class CalendarMode {
    Agenda,
    Month,
    NewEvent,
    Detail,
    Edit,
}

/** An agenda event awaiting an explicit delete confirmation. */
private data class PendingDelete(
    val eventId: Long,
    val title: String,
    val recurring: Boolean,
)

private fun defaultEventStart(date: LocalDate): Long =
    date
        .atTime(9, 0)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

@Composable
private fun CalendarApp(
    repository: CalendarRepository,
    refreshGeneration: Int,
    onRefresh: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    var mode by remember { mutableStateOf(CalendarMode.Agenda) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    // Unsaved new event kept while the user backs out to the agenda, restored on re-entry this session.
    var eventDraft by remember { mutableStateOf<EventDraft?>(null) }
    var openEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var detail by remember { mutableStateOf<CalendarEventDetail?>(null) }
    var editDraft by remember { mutableStateOf<EventDraft?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Hardware Back/Esc goes up one level: edit → detail → agenda; only the agenda leaves Calendar.
    BackHandler(enabled = mode != CalendarMode.Agenda) {
        mode =
            if (mode == CalendarMode.Edit && detail != null) {
                CalendarMode.Detail
            } else {
                CalendarMode.Agenda
            }
        editDraft = null
    }

    LaunchedEffect(refreshGeneration, selectedDate) {
        refreshing = true
        try {
            events =
                withContext(Dispatchers.IO) {
                    repository.agenda(selectedDate)
                }
        } finally {
            refreshing = false
        }
    }

    LaunchedEffect(openEvent, refreshGeneration) {
        val occurrence = openEvent ?: return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { repository.event(occurrence) }
        detail = loaded
        if (loaded == null && (mode == CalendarMode.Detail || mode == CalendarMode.Edit)) {
            // Deleted or moved elsewhere (sync): there is nothing left to show.
            status = "This event is no longer in the calendar."
            mode = CalendarMode.Agenda
        }
    }

    val openDetail: (CalendarEvent) -> Unit = { event ->
        status = null
        if (openEvent != event) detail = null
        openEvent = event
        mode = CalendarMode.Detail
    }
    val openEditor: (CalendarEvent) -> Unit = { event ->
        status = null
        if (!event.canModify) {
            status = "“${event.title}” is in a read-only calendar."
        } else {
            if (openEvent != event) detail = null
            openEvent = event
            editDraft = null
            mode = CalendarMode.Edit
        }
    }
    val askDelete: (Long, String, Boolean, Boolean) -> Unit = { id, title, recurring, canModify ->
        status = null
        if (canModify) {
            pendingDelete = PendingDelete(id, title, recurring)
        } else {
            status = "“$title” is in a read-only calendar."
        }
    }

    pendingDelete?.let { pending ->
        DeleteConfirmDialog(
            message = EventEditPolicy.deleteConfirmation(pending.title, pending.recurring),
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) { repository.deleteEvent(pending.eventId) }
                    status = if (deleted) "Deleted “${pending.title}”." else "Unable to delete “${pending.title}”."
                    if (deleted) {
                        openEvent = null
                        detail = null
                        mode = CalendarMode.Agenda
                    }
                    onRefresh()
                }
            },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        SableRefreshableSurface(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = SableSpacing.ScreenHorizontal,
                            vertical = SableSpacing.ScreenVertical,
                        ),
            ) {
                Text(
                    text = "calendar",
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    text = "Local CalendarProvider UI · account sync remains separate.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(SableSpacing.Lg))

                if (!repository.canRead()) {
                    PermissionPanel(onRequestPermissions)
                    return@Column
                }

                status?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(SableSpacing.Sm))
                }

                when (mode) {
                    CalendarMode.Agenda -> {
                        ApprovedCalendarLanding(
                            selectedDate = selectedDate,
                            events = events,
                            onSelectDate = { selectedDate = it },
                            onOpenMonth = { mode = CalendarMode.Month },
                            onNewEvent = {
                                status = null
                                mode = CalendarMode.NewEvent
                            },
                            onOpenEvent = openDetail,
                            onEditEvent = openEditor,
                            onDeleteEvent = { event ->
                                askDelete(event.id, event.title, event.recurring, event.canModify)
                            },
                        )
                    }

                    CalendarMode.Month -> {
                        BackToAgenda { mode = CalendarMode.Agenda }
                        MonthView(
                            month = YearMonth.from(selectedDate),
                            selectedDate = selectedDate,
                            onSelectDate = { date ->
                                selectedDate = date
                                mode = CalendarMode.Agenda
                            },
                        )
                    }

                    CalendarMode.NewEvent -> {
                        BackToAgenda { mode = CalendarMode.Agenda }
                        if (!repository.canWrite()) {
                            PermissionPanel(onRequestPermissions)
                        } else {
                            EventEditor(
                                draft = eventDraft ?: EventDraft.newAt(defaultEventStart(selectedDate)),
                                onDraftChange = { eventDraft = it },
                                timesEditable = true,
                                saveLabel = "Save event",
                                onSave = { draft ->
                                    val created = withContext(Dispatchers.IO) { repository.createEvent(draft) }
                                    if (created != null) {
                                        eventDraft = null
                                        onRefresh()
                                        mode = CalendarMode.Agenda
                                    }
                                    created != null
                                },
                            )
                        }
                    }

                    CalendarMode.Detail -> {
                        val loaded = detail
                        BackToAgenda { mode = CalendarMode.Agenda }
                        if (loaded == null) {
                            LoadingLine()
                        } else {
                            EventDetailView(
                                detail = loaded,
                                onEdit = {
                                    status = null
                                    editDraft = null
                                    if (loaded.canModify) {
                                        mode = CalendarMode.Edit
                                    } else {
                                        status = "“${loaded.title}” is in a read-only calendar."
                                    }
                                },
                                onDelete = {
                                    askDelete(loaded.id, loaded.title, loaded.recurring, loaded.canModify)
                                },
                                onBack = { mode = CalendarMode.Agenda },
                            )
                        }
                    }

                    CalendarMode.Edit -> {
                        val loaded = detail
                        SableActionButton(
                            text = "‹  event",
                            primary = false,
                            onClick = {
                                editDraft = null
                                mode = if (loaded != null) CalendarMode.Detail else CalendarMode.Agenda
                            },
                        )
                        Spacer(Modifier.height(SableSpacing.Md))
                        if (loaded == null) {
                            LoadingLine()
                        } else {
                            EventEditor(
                                draft = editDraft ?: loaded.toDraft(),
                                onDraftChange = { editDraft = it },
                                timesEditable = loaded.timesEditable,
                                saveLabel = "Save changes",
                                onSave = { draft ->
                                    val saved = withContext(Dispatchers.IO) { repository.updateEvent(loaded, draft) }
                                    if (saved) {
                                        editDraft = null
                                        status = "Saved."
                                        // The detail shows the opened occurrence: follow a moved single event.
                                        val times = if (loaded.timesEditable) draft else loaded.toDraft()
                                        openEvent =
                                            openEvent?.copy(
                                                title = draft.title.trim(),
                                                startMillis = times.startMillis,
                                                endMillis = times.endMillis,
                                            )
                                        onRefresh()
                                        mode = CalendarMode.Detail
                                    }
                                    saved
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackToAgenda(onClick: () -> Unit) {
    SableActionButton(
        text = "‹  agenda",
        primary = false,
        onClick = onClick,
    )
    Spacer(Modifier.height(SableSpacing.Md))
}

@Composable
private fun LoadingLine() {
    Text(
        text = "Loading…",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ApprovedCalendarLanding(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    onSelectDate: (LocalDate) -> Unit,
    onOpenMonth: () -> Unit,
    onNewEvent: () -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onEditEvent: (CalendarEvent) -> Unit,
    onDeleteEvent: (CalendarEvent) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenMonth),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalendarIdentityMark()
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text =
                    selectedDate.month.name
                        .lowercase()
                        .replaceFirstChar { it.uppercase() }
                        .lowercase(),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = selectedDate.year.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(18.dp))
    CalendarWeekStrip(
        selectedDate = selectedDate,
        onSelectDate = onSelectDate,
    )
    Spacer(Modifier.height(18.dp))
    Text(
        text = CalendarLabels.dayHeading(selectedDate, LocalDate.now()),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))

    if (events.isEmpty()) {
        Text(
            text = "nothing scheduled",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
    } else {
        events.forEach { event ->
            AgendaEventRow(
                event = event,
                onOpen = { onOpenEvent(event) },
                onEdit = { onEditEvent(event) },
                onDelete = { onDeleteEvent(event) },
            )
        }
        Spacer(Modifier.height(14.dp))
    }

    SableActionButton(
        text = "+  new event",
        primary = false,
        onClick = onNewEvent,
    )
}

@Composable
private fun CalendarIdentityMark() {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier =
            Modifier
                .size(58.dp)
                .shadow(4.dp, shape)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                        ),
                    ),
                ).border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    shape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_sable_calendar),
            contentDescription = null,
            modifier = Modifier.size(42.dp),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun CalendarWeekStrip(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
) {
    val monday =
        selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(7) { offset ->
            val date = monday.plusDays(offset.toLong())
            val selected = date == selectedDate
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ).border(
                            1.dp,
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            RoundedCornerShape(6.dp),
                        ).clickable { onSelectDate(date) }
                        .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = date.dayOfWeek.name.take(1),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** Agenda row: Enter opens the event, E edits, Del asks to delete. */
@Composable
private fun AgendaEventRow(
    event: CalendarEvent,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val time =
        if (event.allDay) {
            "all day"
        } else {
            android.text.format.DateFormat
                .getTimeFormat(context)
                .format(Date(event.startMillis))
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .calendarKeys(onEdit = onEdit, onDelete = onDelete)
                .sableFocusRing()
                .clickable(onClick = onOpen)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            modifier = Modifier.width(76.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Box(
            Modifier
                .width(3.dp)
                .height(48.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (event.recurring) event.calendarName + " · repeats" else event.calendarName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** E / Del / Esc on a focused calendar element ([CalendarKeys]); Enter stays the element's click. */
private fun Modifier.calendarKeys(
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
): Modifier =
    onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val modified = event.isAltPressed || event.isCtrlPressed || event.isMetaPressed
        val handler =
            when (CalendarKeys.action(event.nativeKeyEvent.keyCode, modified)) {
                CalendarKeyAction.Edit -> onEdit
                CalendarKeyAction.Delete -> onDelete
                CalendarKeyAction.Back -> onBack
                null -> null
            }
        handler?.invoke()
        handler != null
    }

@Composable
private fun EventDetailView(
    detail: CalendarEventDetail,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val firstAction = remember { FocusRequester() }
    // Focus lands on the detail's first action so E / Del / Esc work without touching the screen.
    LaunchedEffect(detail.id) {
        withFrameNanos { }
        runCatching { firstAction.requestFocus() }
    }

    Column(
        modifier = Modifier.fillMaxWidth().calendarKeys(onEdit = onEdit, onDelete = onDelete, onBack = onBack),
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
    ) {
        Text(
            text = detail.title,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = eventWhen(context, detail),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        DetailLine("Calendar", if (detail.recurring) detail.calendarName + " · repeats" else detail.calendarName)
        if (detail.location.isNotEmpty()) DetailLine("Where", detail.location)
        if (detail.organizer.isNotEmpty()) DetailLine("Organizer", detail.organizer)
        if (detail.attendees.isNotEmpty()) {
            DetailLine(
                "Guests",
                detail.attendees.joinToString("\n") { attendee ->
                    listOf(attendee.name.ifEmpty { attendee.email }, attendee.status)
                        .filter(String::isNotEmpty)
                        .joinToString(" · ")
                },
            )
        }
        if (detail.description.isNotEmpty()) DetailLine("Notes", detail.description)

        Spacer(Modifier.height(SableSpacing.Sm))
        if (detail.canModify) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
            ) {
                SableActionButton(
                    text = "Edit  (E)",
                    modifier = Modifier.weight(1f).focusRequester(firstAction),
                    onClick = onEdit,
                )
                SableActionButton(
                    text = "Delete  (Del)",
                    modifier = Modifier.weight(1f),
                    primary = false,
                    onClick = onDelete,
                )
            }
        } else {
            Text(
                text = "This calendar is read-only here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SableActionButton(
                text = "Back",
                modifier = Modifier.focusRequester(firstAction),
                primary = false,
                onClick = onBack,
            )
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun eventWhen(
    context: android.content.Context,
    detail: CalendarEventDetail,
): String {
    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY
    // All-day events are stored at UTC midnight with an exclusive end: format them in UTC.
    val range =
        if (detail.allDay) {
            val end = maxOf(detail.startMillis, detail.endMillis - 1)
            DateUtils
                .formatDateRange(
                    context,
                    Formatter(StringBuilder(), Locale.getDefault()),
                    detail.startMillis,
                    end,
                    flags,
                    "UTC",
                ).toString()
        } else {
            DateUtils.formatDateRange(
                context,
                detail.startMillis,
                detail.endMillis,
                flags or DateUtils.FORMAT_SHOW_TIME,
            )
        }
    return if (detail.allDay) "$range · all day" else range
}

@Composable
private fun DeleteConfirmDialog(
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cancel = remember { FocusRequester() }
    // Focus starts on Cancel: Enter right away keeps the event; deleting is a deliberate move.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { cancel.requestFocus() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete event?") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.focusRequester(cancel),
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun MonthView(
    month: YearMonth,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
) {
    Text(
        text =
            month.month.name
                .lowercase()
                .replaceFirstChar { it.uppercase() } +
                " " +
                month.year,
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(SableSpacing.Md))

    Row(Modifier.fillMaxWidth()) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(6.dp))

    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val cells =
        List<LocalDate?>(leading) { null } +
            (1..month.lengthOfMonth()).map(month::atDay)

    cells.chunked(7).forEach { week ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(7) { index ->
                val date = week.getOrNull(index)
                val selected = date == selectedDate
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clickable(enabled = date != null) {
                                date?.let(onSelectDate)
                            }.background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                RoundedCornerShape(5.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    date?.let {
                        Text(
                            text = it.dayOfMonth.toString(),
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** New-event and edit-event form. [onSave] runs the provider write and reports success. */
@Composable
private fun EventEditor(
    draft: EventDraft,
    onDraftChange: (EventDraft) -> Unit,
    timesEditable: Boolean,
    saveLabel: String,
    onSave: suspend (EventDraft) -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }

    OutlinedTextField(
        value = draft.title,
        onValueChange = { onDraftChange(draft.copy(title = it)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Event title") },
    )
    Spacer(Modifier.height(SableSpacing.Md))

    if (timesEditable) {
        SableActionButton(
            text = "Starts " + formatDateTime(context, draft.startMillis),
            primary = false,
            onClick = {
                pickDateTime(context, draft.startMillis) { picked -> onDraftChange(draft.withStart(picked)) }
            },
        )
        Spacer(Modifier.height(SableSpacing.Sm))
        SableActionButton(
            text = "Ends " + formatDateTime(context, draft.endMillis),
            primary = false,
            onClick = {
                pickDateTime(context, draft.endMillis) { picked -> onDraftChange(draft.copy(endMillis = picked)) }
            },
        )
    } else {
        Text(
            text = "Times of repeating and all-day events are kept; change them in the account's calendar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(SableSpacing.Md))

    OutlinedTextField(
        value = draft.location,
        onValueChange = { onDraftChange(draft.copy(location = it)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Location") },
    )
    Spacer(Modifier.height(SableSpacing.Sm))
    OutlinedTextField(
        value = draft.description,
        onValueChange = { onDraftChange(draft.copy(description = it)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        label = { Text("Notes") },
    )
    Spacer(Modifier.height(SableSpacing.Lg))

    SableActionButton(
        text = saveLabel,
        onClick = {
            val problem = EventEditPolicy.validate(draft)
            if (problem != null) {
                status = problem
            } else {
                scope.launch {
                    status = if (onSave(draft)) null else "Unable to save event."
                }
            }
        },
    )
    status?.let {
        Spacer(Modifier.height(SableSpacing.Sm))
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun formatDateTime(
    context: android.content.Context,
    millis: Long,
): String =
    android.text.format.DateFormat
        .getMediumDateFormat(context)
        .format(Date(millis)) +
        " · " +
        android.text.format.DateFormat
            .getTimeFormat(context)
            .format(Date(millis))

/** System date picker, then time picker; [onPicked] gets the combined local time. */
private fun pickDateTime(
    context: android.content.Context,
    initialMillis: Long,
    onPicked: (Long) -> Unit,
) {
    val initial = Calendar.getInstance().apply { timeInMillis = initialMillis }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val picked =
                        Calendar.getInstance().apply {
                            timeInMillis = initialMillis
                            set(year, month, day, hour, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                    onPicked(picked.timeInMillis)
                },
                initial.get(Calendar.HOUR_OF_DAY),
                initial.get(Calendar.MINUTE),
                android.text.format.DateFormat
                    .is24HourFormat(context),
            ).show()
        },
        initial.get(Calendar.YEAR),
        initial.get(Calendar.MONTH),
        initial.get(Calendar.DAY_OF_MONTH),
    ).show()
}

@Composable
private fun PermissionPanel(onRequestPermissions: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(
            modifier = Modifier.padding(SableSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Md),
        ) {
            Text(
                text = "Calendar access",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text =
                    "Sable Calendar uses Android CalendarProvider. " +
                        "Grant calendar access to read and create local events.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SableActionButton(
                text = "Review access",
                onClick = onRequestPermissions,
            )
        }
    }
}
