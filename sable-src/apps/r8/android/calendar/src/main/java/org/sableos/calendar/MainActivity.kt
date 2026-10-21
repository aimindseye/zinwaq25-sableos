package org.sableos.calendar

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateUtils
import android.view.KeyEvent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sableos.design.SableActionButton
import org.sableos.design.SableGlobalTheme
import org.sableos.design.SableRefreshableSurface
import org.sableos.design.SableSpacing
import org.sableos.design.sableFocusRing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Formatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var repository: CalendarRepository
    private var refreshGeneration by mutableIntStateOf(0)

    /** C/T/V/J/K/R for the current screen; set by [CalendarApp]. */
    private var screenKeys: (CalendarScreenKey) -> Boolean = { false }

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
                    registerKeys = { screenKeys = it },
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

    /**
     * Screen keys arrive here only when no focused view used them, so a text field always gets its
     * letters first; [CalendarAppState.onScreenKey] also ignores them in the editors.
     */
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val modified = event.isAltPressed || event.isCtrlPressed || event.isMetaPressed
        val key = CalendarScreenKeys.action(keyCode, modified)
        return (key != null && event.repeatCount == 0 && screenKeys(key)) || super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) {
            refreshGeneration += 1
        }
    }
}

/** [Agenda] is the browsing screen, which shows [CalendarAppState.view]: agenda, week or month. */
private enum class CalendarMode {
    Agenda,
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

/** Screen state for [CalendarApp]: every field is Compose state, so readers recompose on change. */
private class CalendarAppState {
    var mode by mutableStateOf(CalendarMode.Agenda)
    var view by mutableStateOf(CalendarView.Agenda)
    var selectedDate by mutableStateOf(LocalDate.now())
    var events by mutableStateOf<List<CalendarEvent>>(emptyList())
    var refreshing by mutableStateOf(false)

    // Unsaved new event kept while the user backs out to the agenda, restored on re-entry this session.
    var eventDraft by mutableStateOf<EventDraft?>(null)
    var openEvent by mutableStateOf<CalendarEvent?>(null)
    var detail by mutableStateOf<CalendarEventDetail?>(null)
    var editDraft by mutableStateOf<EventDraft?>(null)
    var pendingDelete by mutableStateOf<PendingDelete?>(null)
    var status by mutableStateOf<String?>(null)

    /** Hardware Back/Esc goes up one level: edit → detail → agenda; only the agenda leaves Calendar. */
    fun goBack() {
        if (mode == CalendarMode.Agenda) {
            view = CalendarView.Agenda
            return
        }
        mode =
            if (mode == CalendarMode.Edit && detail != null) {
                CalendarMode.Detail
            } else {
                CalendarMode.Agenda
            }
        editDraft = null
    }

    fun onDetailLoaded(loaded: CalendarEventDetail?) {
        detail = loaded
        if (loaded == null && (mode == CalendarMode.Detail || mode == CalendarMode.Edit)) {
            // Deleted or moved elsewhere (sync): there is nothing left to show.
            status = "This event is no longer in the calendar."
            mode = CalendarMode.Agenda
        }
    }

    fun openDetail(event: CalendarEvent) {
        status = null
        if (openEvent != event) detail = null
        openEvent = event
        mode = CalendarMode.Detail
    }

    fun openEditor(event: CalendarEvent) {
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

    fun askDelete(
        id: Long,
        title: String,
        recurring: Boolean,
        canModify: Boolean,
    ) {
        status = null
        if (canModify) {
            pendingDelete = PendingDelete(id, title, recurring)
        } else {
            status = "“$title” is in a read-only calendar."
        }
    }

    /** True while Back has somewhere to go inside Calendar. */
    val canGoBack: Boolean
        get() = mode != CalendarMode.Agenda || view != CalendarView.Agenda

    /** The browsing selection; its detail loads into [detail] for the agenda's side pane. */
    fun select(event: CalendarEvent) {
        status = null
        if (openEvent != event) detail = null
        openEvent = event
    }

    /** After a reload: keep the selection if it is still listed, else pick the current or next event. */
    fun syncSelection(nowMillis: Long) {
        if (mode != CalendarMode.Agenda) return
        val current = openEvent
        if (current == null || events.none { it.id == current.id && it.startMillis == current.startMillis }) {
            val initial = EventCursor.initial(events, nowMillis)
            if (initial == null) {
                openEvent = null
                detail = null
            } else {
                select(initial)
            }
        }
    }

    /** C create, T today, V view, J/K next/previous event, R refresh. Never while editing. */
    fun onScreenKey(
        key: CalendarScreenKey,
        onRefresh: () -> Unit,
    ): Boolean {
        if (pendingDelete != null || mode == CalendarMode.NewEvent || mode == CalendarMode.Edit) return false
        when (key) {
            CalendarScreenKey.Create -> startNewEvent()
            CalendarScreenKey.Today -> {
                status = null
                selectedDate = LocalDate.now()
                mode = CalendarMode.Agenda
            }
            CalendarScreenKey.View -> {
                status = null
                mode = CalendarMode.Agenda
                view = view.next()
            }
            CalendarScreenKey.Next -> step(forward = true)
            CalendarScreenKey.Previous -> step(forward = false)
            CalendarScreenKey.Refresh -> {
                onRefresh()
                status = "Refreshed."
            }
        }
        return true
    }

    /** J/K: the next event in the agenda, week or open detail; a day at a time in the month. */
    private fun step(forward: Boolean) {
        if (mode == CalendarMode.Agenda && view == CalendarView.Month) {
            selectedDate = selectedDate.plusDays(if (forward) 1L else -1L)
            return
        }
        val next = EventCursor.step(events, openEvent, forward)
        when {
            next == null -> status = "Nothing scheduled."
            mode == CalendarMode.Detail -> openDetail(next)
            else -> select(next)
        }
    }

    fun startNewEvent() {
        status = null
        mode = CalendarMode.NewEvent
    }

    fun editFromDetail(loaded: CalendarEventDetail) {
        status = null
        editDraft = null
        if (loaded.canModify) {
            mode = CalendarMode.Edit
        } else {
            status = "“${loaded.title}” is in a read-only calendar."
        }
    }

    fun onDeleteFinished(
        title: String,
        deleted: Boolean,
    ) {
        status = if (deleted) "Deleted “$title”." else "Unable to delete “$title”."
        if (deleted) {
            openEvent = null
            detail = null
            mode = CalendarMode.Agenda
        }
    }

    fun leaveEditor(hasDetail: Boolean) {
        editDraft = null
        mode = if (hasDetail) CalendarMode.Detail else CalendarMode.Agenda
    }

    /** After a successful edit: the detail shows the opened occurrence, so follow a moved single event. */
    fun onEditSaved(
        loaded: CalendarEventDetail,
        draft: EventDraft,
    ) {
        editDraft = null
        status = "Saved."
        val times = if (loaded.timesEditable) draft else loaded.toDraft()
        openEvent =
            openEvent?.copy(
                title = draft.title.trim(),
                startMillis = times.startMillis,
                endMillis = times.endMillis,
            )
    }
}

@Composable
private fun CalendarApp(
    repository: CalendarRepository,
    refreshGeneration: Int,
    onRefresh: () -> Unit,
    registerKeys: ((CalendarScreenKey) -> Boolean) -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val state = remember { CalendarAppState() }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = state.canGoBack) { state.goBack() }
    SideEffect { registerKeys { key -> state.onScreenKey(key, onRefresh) } }

    // The week view lists its five days; the agenda and month list the selected day.
    val weekView = state.view == CalendarView.Week
    LaunchedEffect(refreshGeneration, state.selectedDate, weekView) {
        state.refreshing = true
        try {
            state.events =
                withContext(Dispatchers.IO) {
                    if (weekView) {
                        val days = WeekLayout.days(state.selectedDate)
                        repository.range(days.first(), days.last().plusDays(1))
                    } else {
                        repository.agenda(state.selectedDate)
                    }
                }
            state.syncSelection(System.currentTimeMillis())
        } finally {
            state.refreshing = false
        }
    }

    LaunchedEffect(state.openEvent, refreshGeneration) {
        val occurrence = state.openEvent ?: return@LaunchedEffect
        state.onDetailLoaded(withContext(Dispatchers.IO) { repository.event(occurrence) })
    }

    state.pendingDelete?.let { pending ->
        DeleteConfirmDialog(
            message = EventEditPolicy.deleteConfirmation(pending.title, pending.recurring),
            onDismiss = { state.pendingDelete = null },
            onConfirm = {
                state.pendingDelete = null
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) { repository.deleteEvent(pending.eventId) }
                    state.onDeleteFinished(pending.title, deleted)
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
            isRefreshing = state.refreshing,
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
                CalendarHeader()
                if (repository.canRead()) {
                    StatusLine(state.status)
                    CalendarModeContent(state, repository, onRefresh, onRequestPermissions)
                } else {
                    PermissionPanel(onRequestPermissions)
                }
            }
        }
    }
}

@Composable
private fun CalendarHeader() {
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
}

@Composable
private fun StatusLine(status: String?) {
    status?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SableSpacing.Sm))
    }
}

@Composable
private fun CalendarModeContent(
    state: CalendarAppState,
    repository: CalendarRepository,
    onRefresh: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    when (state.mode) {
        CalendarMode.Agenda ->
            when (state.view) {
                CalendarView.Agenda -> AgendaPane(state)
                CalendarView.Week -> WeekPane(state)
                CalendarView.Month -> MonthPane(state)
            }
        CalendarMode.NewEvent -> NewEventPane(state, repository, onRefresh, onRequestPermissions)
        CalendarMode.Detail -> DetailPane(state)
        CalendarMode.Edit -> EditPane(state, repository, onRefresh)
    }
}

/** Wide enough (the Q25's ~597 dp) for the agenda and the event detail side by side. */
private const val SPLIT_MIN_WIDTH_DP = 520

@Composable
private fun AgendaPane(state: CalendarAppState) {
    val split = LocalConfiguration.current.screenWidthDp >= SPLIT_MIN_WIDTH_DP
    ApprovedCalendarLanding(
        selectedDate = state.selectedDate,
        events = state.events,
        selected = state.openEvent,
        onSelectDate = { state.selectedDate = it },
        onOpenMonth = { state.view = CalendarView.Month },
        onNewEvent = state::startNewEvent,
        onOpenEvent = state::openDetail,
        onFocusEvent = state::select,
        onEditEvent = state::openEditor,
        onDeleteEvent = { event ->
            state.askDelete(event.id, event.title, event.recurring, event.canModify)
        },
        sidePane =
            if (split) {
                { AgendaDetailPreview(state.openEvent, state.detail) }
            } else {
                null
            },
    )
    KeyDock()
}

@Composable
private fun MonthPane(state: CalendarAppState) {
    BackToAgenda { state.view = CalendarView.Agenda }
    MonthView(
        month = YearMonth.from(state.selectedDate),
        selectedDate = state.selectedDate,
        onSelectDate = { date ->
            state.selectedDate = date
            state.view = CalendarView.Agenda
        },
    )
    KeyDock()
}

@Composable
private fun NewEventPane(
    state: CalendarAppState,
    repository: CalendarRepository,
    onRefresh: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    BackToAgenda { state.mode = CalendarMode.Agenda }
    if (!repository.canWrite()) {
        PermissionPanel(onRequestPermissions)
        return
    }
    EventEditor(
        draft = state.eventDraft ?: EventDraft.newAt(defaultEventStart(state.selectedDate)),
        onDraftChange = { state.eventDraft = it },
        timesEditable = true,
        saveLabel = "Save event",
        onSave = { draft ->
            val created = withContext(Dispatchers.IO) { repository.createEvent(draft) }
            if (created != null) {
                state.eventDraft = null
                onRefresh()
                state.mode = CalendarMode.Agenda
            }
            created != null
        },
    )
}

@Composable
private fun DetailPane(state: CalendarAppState) {
    val loaded = state.detail
    BackToAgenda { state.mode = CalendarMode.Agenda }
    if (loaded == null) {
        LoadingLine()
        return
    }
    EventDetailView(
        detail = loaded,
        onEdit = { state.editFromDetail(loaded) },
        onDelete = { state.askDelete(loaded.id, loaded.title, loaded.recurring, loaded.canModify) },
        onBack = { state.mode = CalendarMode.Agenda },
    )
}

@Composable
private fun EditPane(
    state: CalendarAppState,
    repository: CalendarRepository,
    onRefresh: () -> Unit,
) {
    val loaded = state.detail
    SableActionButton(
        text = "‹  event",
        primary = false,
        onClick = { state.leaveEditor(hasDetail = loaded != null) },
    )
    Spacer(Modifier.height(SableSpacing.Md))
    if (loaded == null) {
        LoadingLine()
        return
    }
    EventEditor(
        draft = state.editDraft ?: loaded.toDraft(),
        onDraftChange = { state.editDraft = it },
        timesEditable = loaded.timesEditable,
        saveLabel = "Save changes",
        onSave = { draft ->
            val saved = withContext(Dispatchers.IO) { repository.updateEvent(loaded, draft) }
            if (saved) {
                state.onEditSaved(loaded, draft)
                onRefresh()
                state.mode = CalendarMode.Detail
            }
            saved
        },
    )
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
    selected: CalendarEvent?,
    onSelectDate: (LocalDate) -> Unit,
    onOpenMonth: () -> Unit,
    onNewEvent: () -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onFocusEvent: (CalendarEvent) -> Unit,
    onEditEvent: (CalendarEvent) -> Unit,
    onDeleteEvent: (CalendarEvent) -> Unit,
    sidePane: (@Composable () -> Unit)?,
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

    val list: @Composable () -> Unit = {
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
                    selected = selected?.id == event.id && selected.startMillis == event.startMillis,
                    onOpen = { onOpenEvent(event) },
                    onFocus = { onFocusEvent(event) },
                    onEdit = { onEditEvent(event) },
                    onDelete = { onDeleteEvent(event) },
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        SableActionButton(
            text = "+  new event  (C)",
            primary = false,
            onClick = onNewEvent,
        )
    }
    if (sidePane == null) {
        list()
    } else {
        // Design panel 4: the agenda on the left, the selected event's detail on the right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Md),
        ) {
            Column(Modifier.weight(1f)) { list() }
            Column(Modifier.weight(1f)) { sidePane() }
        }
    }
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

/** Agenda row: Enter opens the event, E edits, Del asks to delete; focus (or J/K) selects it. */
@Composable
private fun AgendaEventRow(
    event: CalendarEvent,
    selected: Boolean,
    onOpen: () -> Unit,
    onFocus: () -> Unit,
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
                .border(
                    if (selected) 2.dp else 0.dp,
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(8.dp),
                ).onFocusChanged { if (it.isFocused) onFocus() }
                .calendarKeys(onEdit = onEdit, onDelete = onDelete)
                .sableFocusRing()
                .clickable(onClick = onOpen)
                .padding(vertical = 8.dp, horizontal = 6.dp),
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

/** Key hints under the browsing views (design: "[C Create] [T Today] [V View] [J/K Event]"). */
@Composable
private fun KeyDock() {
    Spacer(Modifier.height(SableSpacing.Md))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = CalendarScreenKeys.DOCK,
            modifier = Modifier.padding(horizontal = SableSpacing.Md, vertical = SableSpacing.Sm),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The agenda's right pane: the selected event, read-only; Enter on its row opens the full detail. */
@Composable
private fun AgendaDetailPreview(
    event: CalendarEvent?,
    detail: CalendarEventDetail?,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(SableSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            Text(
                text = "DETAIL",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                event == null ->
                    Text(
                        text = "No event selected. J/K move through the day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                detail == null || detail.id != event.id -> LoadingLine()

                else -> {
                    Text(text = detail.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = eventWhen(context, detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (detail.recurring) detail.calendarName + " · repeats" else detail.calendarName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (detail.location.isNotEmpty()) {
                        Text(text = detail.location, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (detail.attendees.isNotEmpty()) {
                        val accepted = detail.attendees.count { it.status == "accepted" }
                        Text(
                            text = "${detail.attendees.size} guests · $accepted accepted",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = if (detail.canModify) "Enter open · E edit · Del delete" else "Read-only calendar",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val NOW_TICK_MS = 60_000L
private val nowLineColor = Color(0xFFFF6978)
private val hourHeight = 44.dp
private val timeColumnWidth = 44.dp
private val minBlockHeight = 22.dp

/**
 * Week view (design panel 3, "Calendar 5-column now view"): five day columns over the hours that
 * matter, events as blocks, a NOW line across today, and the selected event under the grid.
 * Tap or Enter on a block selects it, again opens it; J/K step through the week's events.
 */
@Composable
private fun WeekPane(state: CalendarAppState) {
    val zone = ZoneId.systemDefault()
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(NOW_TICK_MS)
            value = LocalDateTime.now()
        }
    }
    val days = WeekLayout.days(state.selectedDate)
    val hours = WeekLayout.hours(days, state.events, now, zone)
    val blocks = WeekLayout.blocks(days, state.events, hours, zone)
    val nowLine = WeekLayout.nowLine(days, hours, now)
    val gridHeight = hourHeight * (hours.last - hours.first)
    val selected = state.openEvent

    Text(text = WeekLayout.title(days), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(SableSpacing.Sm))
    state.events.filter { it.allDay }.forEach { event ->
        Text(
            text = "All day · " + event.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(timeColumnWidth))
        days.forEach { day ->
            Text(
                text = WeekLayout.columnLabel(day),
                modifier = Modifier.weight(1f).clickable { state.selectedDate = day }.padding(vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (day == state.selectedDate) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(gridHeight)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(timeColumnWidth).fillMaxHeight()) {
                for (hour in hours.first until hours.last) {
                    Text(
                        text = "%02d:00".format(Locale.ROOT, hour),
                        modifier = Modifier.offset(y = hourHeight * (hour - hours.first)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            days.forEachIndexed { column, _ ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    blocks.filter { it.column == column }.forEach { block ->
                        WeekEventBlock(
                            block = block,
                            gridHeight = gridHeight,
                            selected = selected?.id == block.event.id && selected.startMillis == block.event.startMillis,
                            onClick = {
                                if (selected == block.event) state.openDetail(block.event) else state.select(block.event)
                            },
                        )
                    }
                }
            }
        }
        nowLine?.let { y ->
            Box(
                Modifier
                    .offset(y = gridHeight * y)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(nowLineColor),
            )
            Text(
                text = "NOW",
                modifier = Modifier.offset(x = timeColumnWidth, y = gridHeight * y - 16.dp),
                style = MaterialTheme.typography.labelSmall,
                color = nowLineColor,
            )
        }
    }
    Spacer(Modifier.height(SableSpacing.Sm))
    WeekSelection(state)
    KeyDock()
}

@Composable
private fun WeekEventBlock(
    block: WeekBlock,
    gridHeight: androidx.compose.ui.unit.Dp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val height = maxOf(gridHeight * (block.bottom - block.top), minBlockHeight)
    Box(
        Modifier
            .offset(y = gridHeight * block.top)
            .padding(horizontal = 2.dp)
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .border(if (selected) 2.dp else 1.dp, MaterialTheme.colorScheme.primary, shape)
            .sableFocusRing()
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Text(
            text = block.event.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Selected: Mon 10:00-11:30", title and calendar, with the keys that act on it. */
@Composable
private fun WeekSelection(state: CalendarAppState) {
    val event = state.openEvent
    val context = LocalContext.current
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .calendarKeys(
                    onEdit = { event?.let(state::openEditor) },
                    onDelete = { event?.let { state.askDelete(it.id, it.title, it.recurring, it.canModify) } },
                ).sableFocusRing()
                .clickable(enabled = event != null) { event?.let(state::openDetail) },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(SableSpacing.Md)) {
            if (event == null) {
                Text(
                    text = "No event this week. C creates one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val time =
                    if (event.allDay) {
                        "all day"
                    } else {
                        DateUtils.formatDateRange(
                            context,
                            event.startMillis,
                            event.endMillis,
                            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_TIME,
                        )
                    }
                Text(text = "Selected: $time", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = event.title + " · " + event.calendarName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "[Enter] open  [E] edit  [Del] delete  [T] today",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
