package org.sableos.calendar

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Calendar
import java.util.Date

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

private enum class CalendarMode(
    val label: String,
) {
    Agenda("AGENDA"),
    Month("MONTH"),
    NewEvent("NEW"),
}

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

                when (mode) {
                    CalendarMode.Agenda -> {
                        ApprovedCalendarLanding(
                            selectedDate = selectedDate,
                            events = events,
                            onSelectDate = { selectedDate = it },
                            onOpenMonth = { mode = CalendarMode.Month },
                            onNewEvent = { mode = CalendarMode.NewEvent },
                        )
                    }

                    CalendarMode.Month -> {
                        SableActionButton(
                            text = "‹  agenda",
                            primary = false,
                            onClick = { mode = CalendarMode.Agenda },
                        )
                        Spacer(Modifier.height(SableSpacing.Md))
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
                        SableActionButton(
                            text = "‹  agenda",
                            primary = false,
                            onClick = { mode = CalendarMode.Agenda },
                        )
                        Spacer(Modifier.height(SableSpacing.Md))
                        NewEventView(
                            repository = repository,
                            selectedDate = selectedDate,
                            onSaved = {
                                onRefresh()
                                mode = CalendarMode.Agenda
                            },
                            onRequestPermissions = onRequestPermissions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovedCalendarLanding(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    onSelectDate: (LocalDate) -> Unit,
    onOpenMonth: () -> Unit,
    onNewEvent: () -> Unit,
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
        text = "TODAY · " + selectedDate.dayOfMonth,
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
            AgendaEventRow(event)
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

@Composable
private fun CalendarPivot(
    selected: CalendarMode,
    onSelect: (CalendarMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
    ) {
        CalendarMode.entries.forEach { mode ->
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(mode) }
                        .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        if (mode == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (mode == selected) 3.dp else 1.dp)
                        .background(
                            if (mode == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun AgendaView(
    date: LocalDate,
    events: List<CalendarEvent>,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹",
            modifier = Modifier.clickable(onClick = onPreviousDay),
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text =
                date.dayOfWeek.name
                    .lowercase()
                    .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "›",
            modifier = Modifier.clickable(onClick = onNextDay),
            style = MaterialTheme.typography.headlineLarge,
        )
    }
    Spacer(Modifier.height(SableSpacing.Md))

    if (events.isEmpty()) {
        Text(
            text = "nothing scheduled",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        events.forEach { event ->
            AgendaEventRow(event)
        }
    }
}

@Composable
private fun AgendaEventRow(event: CalendarEvent) {
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
        Column {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = event.calendarName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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

@Composable
private fun NewEventView(
    repository: CalendarRepository,
    selectedDate: LocalDate,
    onSaved: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var startMillis by remember(selectedDate) {
        mutableStateOf(
            selectedDate
                .atTime(9, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
        )
    }
    var status by remember { mutableStateOf<String?>(null) }

    if (!repository.canWrite()) {
        PermissionPanel(onRequestPermissions)
        return
    }

    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Event title") },
    )
    Spacer(Modifier.height(SableSpacing.Md))

    val calendar =
        remember(startMillis) {
            Calendar.getInstance().apply {
                timeInMillis = startMillis
            }
        }
    val display =
        android.text.format.DateFormat
            .getMediumDateFormat(context)
            .format(Date(startMillis)) +
            " · " +
            android.text.format.DateFormat
                .getTimeFormat(context)
                .format(Date(startMillis))

    SableActionButton(
        text = display,
        primary = false,
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val picked =
                        Calendar.getInstance().apply {
                            timeInMillis = startMillis
                            set(year, month, day)
                        }
                    startMillis = picked.timeInMillis
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
            ).show()
        },
    )
    Spacer(Modifier.height(SableSpacing.Sm))
    SableActionButton(
        text = "Choose time",
        primary = false,
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val picked =
                        Calendar.getInstance().apply {
                            timeInMillis = startMillis
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                    startMillis = picked.timeInMillis
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                android.text.format.DateFormat
                    .is24HourFormat(context),
            ).show()
        },
    )
    Spacer(Modifier.height(SableSpacing.Lg))

    SableActionButton(
        text = "Save event",
        onClick = {
            scope.launch {
                val created =
                    withContext(Dispatchers.IO) {
                        repository.createEvent(
                            title = title,
                            startMillis = startMillis,
                            endMillis = startMillis + 60 * 60 * 1000L,
                        )
                    }
                if (created != null) {
                    onSaved()
                } else {
                    status = "Unable to save event."
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
