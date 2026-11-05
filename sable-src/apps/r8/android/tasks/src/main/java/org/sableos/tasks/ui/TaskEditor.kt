@file:Suppress("FunctionNaming") // Compose functions are named like types.

package org.sableos.tasks.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.sableos.design.SableActionButton
import org.sableos.design.SableSpacing
import org.sableos.tasks.core.ReminderPreset
import org.sableos.tasks.core.TaskRecord
import org.sableos.tasks.core.TaskRepeat
import org.sableos.tasks.core.TaskSchedule
import org.sableos.tasks.core.TasksText.t
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar

/** New and edit form: title, details, due, reminder (quick times or a picked one) and repeat. */
@Suppress("LongMethod") // One form, read top to bottom.
@Composable
internal fun TaskEditor(
    task: TaskRecord,
    isNew: Boolean,
    onSave: (TaskRecord) -> Unit,
    onCancel: () -> Unit,
    onDelete: (TaskRecord) -> Unit,
) {
    val context = LocalContext.current
    var title by remember(task.id) { mutableStateOf(if (task.title == TasksState.NEW_PLACEHOLDER) "" else task.title) }
    var details by remember(task.id) { mutableStateOf(task.details) }
    var due by remember(task.id) { mutableStateOf(task.dueAtEpochMs) }
    var remind by remember(task.id) { mutableStateOf(task.remindAtEpochMs) }
    var repeat by remember(task.id) { mutableStateOf(task.repeat) }
    var error by remember(task.id) { mutableStateOf<String?>(null) }

    Text(if (isNew) t("new_task") else t("edit_task"), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(SableSpacing.Md))
    OutlinedTextField(
        value = title,
        onValueChange = { title = it.take(TaskRecord.MAX_TITLE) },
        label = { Text(t("title")) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = details,
        onValueChange = { details = it.take(TaskRecord.MAX_DETAILS) },
        label = { Text(t("details")) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(SableSpacing.Sm))
    WhenLine(t("due"), due?.let { formatWhen(context, it) } ?: t("none"))
    Row(horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm)) {
        TextButton(onClick = { pickDateTime(context, due ?: defaultTime()) { due = it } }) { Text(t("pick_time")) }
        TextButton(onClick = { due = null }) { Text(t("none")) }
    }
    WhenLine(t("remind"), remind?.let { formatWhen(context, it) } ?: t("none"))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm)) {
        ReminderPreset.entries.forEach { preset ->
            TextButton(onClick = { remind = TaskSchedule.preset(preset, Instant.now(), ZoneId.systemDefault()) }) {
                Text(presetLabel(preset))
            }
        }
        TextButton(onClick = { pickDateTime(context, remind ?: defaultTime()) { remind = it } }) {
            Text(t("pick_time"))
        }
        TextButton(onClick = { remind = null }) { Text(t("none")) }
    }
    TextButton(onClick = { repeat = TaskRepeat.entries[(repeat.ordinal + 1) % TaskRepeat.entries.size] }) {
        Text(t("repeat", repeatLabel(repeat)))
    }
    Spacer(Modifier.height(SableSpacing.Md))
    Row(horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm)) {
        SableActionButton(text = t("save"), onClick = {
            val name = title.trim()
            if (name.isEmpty()) {
                error = t("add_a_title")
            } else {
                onSave(
                    task.copy(
                        title = name,
                        details = details.trim(),
                        dueAtEpochMs = due,
                        remindAtEpochMs = remind,
                        repeat = repeat,
                        completed = task.completed,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
        })
        SableActionButton(text = t("cancel"), primary = false, onClick = onCancel)
        if (!isNew) SableActionButton(text = t("delete"), primary = false, onClick = { onDelete(task) })
    }
}

@Composable
private fun WhenLine(
    label: String,
    value: String,
) {
    Column(Modifier.fillMaxWidth().height(44.dp), verticalArrangement = Arrangement.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun presetLabel(preset: ReminderPreset): String =
    when (preset) {
        ReminderPreset.LaterToday -> t("later_today")
        ReminderPreset.ThisEvening -> t("this_evening")
        ReminderPreset.TomorrowMorning -> t("tomorrow_morning")
    }

private fun defaultTime(): Long =
    TaskSchedule.preset(ReminderPreset.TomorrowMorning, Instant.now(), ZoneId.systemDefault())

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
                DateFormat.is24HourFormat(context),
            ).show()
        },
        initial.get(Calendar.YEAR),
        initial.get(Calendar.MONTH),
        initial.get(Calendar.DAY_OF_MONTH),
    ).show()
}
