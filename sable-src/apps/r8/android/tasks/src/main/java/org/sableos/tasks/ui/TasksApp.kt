package org.sableos.tasks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.sableos.design.SableActionButton
import org.sableos.design.SableSize
import org.sableos.design.SableSpacing
import org.sableos.design.sableFocusRing
import org.sableos.tasks.core.ReminderPreset
import org.sableos.tasks.core.RowKey
import org.sableos.tasks.core.ScreenKey
import org.sableos.tasks.core.TaskGroup
import org.sableos.tasks.core.TaskRecord
import org.sableos.tasks.core.TaskRepeat
import org.sableos.tasks.core.TaskRepository
import org.sableos.tasks.core.TaskSchedule
import org.sableos.tasks.core.TasksKeys
import org.sableos.tasks.core.TasksText.t
import java.time.Instant
import java.time.ZoneId

@Composable
fun TasksApp(
    state: TasksState,
    repository: TaskRepository,
    onChanged: () -> Unit,
    onAllowNotifications: () -> Unit,
) {
    BackHandler(enabled = state.editing != null) { state.closeEditor() }
    state.pendingDelete?.let { task -> DeleteDialog(state, repository, task, onChanged) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SableSpacing.ScreenHorizontal, vertical = SableSpacing.ScreenVertical),
        ) {
            val editing = state.editing
            if (editing == null) {
                TaskList(state, repository, onChanged, onAllowNotifications)
            } else {
                TaskEditor(
                    task = editing,
                    isNew = state.editingIsNew,
                    onSave = { saved ->
                        repository.save(saved)
                        state.closeEditor()
                        onChanged()
                    },
                    onCancel = state::closeEditor,
                    onDelete = { state.pendingDelete = it },
                )
            }
        }
    }
}

@Composable
private fun TaskList(
    state: TasksState,
    repository: TaskRepository,
    onChanged: () -> Unit,
    onAllowNotifications: () -> Unit,
) {
    Text(text = t("tasks"), style = MaterialTheme.typography.displayLarge)
    state.status?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!state.notificationsAllowed) {
        Text(
            text = t("reminders_off"),
            modifier = Modifier.fillMaxWidth().sableFocusRing().clickable(onClick = onAllowNotifications).padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.height(SableSpacing.Md))
    val sections = TaskSchedule.sections(state.tasks, state.showDone, Instant.now(), ZoneId.systemDefault())
    if (sections.isEmpty()) {
        Text(t("no_tasks"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    sections.forEach { (group, tasks) ->
        Text(
            text = groupLabel(group),
            style = MaterialTheme.typography.labelLarge,
            color = if (group == TaskGroup.Overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        tasks.forEach { task ->
            TaskRow(
                task = task,
                onOpen = { state.openEditor(task, isNew = false) },
                onKey = { key ->
                    when (key) {
                        RowKey.Done -> repository.complete(task)
                        RowKey.Tomorrow -> {
                            val at = TaskSchedule.preset(ReminderPreset.TomorrowMorning, Instant.now(), ZoneId.systemDefault())
                            repository.save(task.copy(remindAtEpochMs = at, updatedAtEpochMs = System.currentTimeMillis()))
                        }
                        RowKey.Delete -> state.pendingDelete = task
                    }
                    onChanged()
                },
            )
        }
    }
    Spacer(Modifier.height(SableSpacing.Md))
    Row {
        SableActionButton(text = t("new_task"), primary = false, onClick = { state.onScreenKey(ScreenKey.New) })
        Spacer(Modifier.width(SableSpacing.Sm))
        SableActionButton(
            text = if (state.showDone) t("hide_done") else t("show_done"),
            primary = false,
            onClick = { state.showDone = !state.showDone },
        )
    }
    Spacer(Modifier.height(SableSpacing.Md))
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = TasksKeys.HINT,
            modifier = Modifier.padding(horizontal = SableSpacing.Md, vertical = SableSpacing.Sm),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskRecord,
    onOpen: () -> Unit,
    onKey: (RowKey) -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = SableSize.TouchTarget)
                .onPreviewKeyEvent { event ->
                    val modified = event.isAltPressed || event.isCtrlPressed || event.isMetaPressed
                    val key = TasksKeys.row(event.nativeKeyEvent.keyCode, modified)
                    if (key != null && event.type == KeyEventType.KeyUp) onKey(key)
                    key != null
                }.sableFocusRing()
                .clickable(onClick = onOpen)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.completed, onCheckedChange = { onKey(RowKey.Done) })
        Column(Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val line = taskLine(context, task)
            if (line.isNotEmpty()) {
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DeleteDialog(
    state: TasksState,
    repository: TaskRepository,
    task: TaskRecord,
    onChanged: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { state.pendingDelete = null },
        text = { Text(t("delete_task", task.title)) },
        confirmButton = {
            TextButton(onClick = {
                repository.delete(task.id)
                state.pendingDelete = null
                state.closeEditor()
                state.status = t("deleted", task.title)
                onChanged()
            }) { Text(t("delete")) }
        },
        dismissButton = { TextButton(onClick = { state.pendingDelete = null }) { Text(t("cancel")) } },
    )
}

internal fun groupLabel(group: TaskGroup): String =
    when (group) {
        TaskGroup.Overdue -> t("group_overdue")
        TaskGroup.Today -> t("group_today")
        TaskGroup.Tomorrow -> t("group_tomorrow")
        TaskGroup.ThisWeek -> t("group_this_week")
        TaskGroup.Later -> t("group_later")
        TaskGroup.NoDate -> t("group_no_date")
        TaskGroup.Done -> t("group_done")
    }

internal fun repeatLabel(repeat: TaskRepeat): String =
    when (repeat) {
        TaskRepeat.Never -> t("never")
        TaskRepeat.Daily -> t("every_day")
        TaskRepeat.Weekly -> t("every_week")
        TaskRepeat.Monthly -> t("every_month")
    }

/** "Due Nov 5 · 17:00 · Remind … · Every week · From Gmail". */
private fun taskLine(
    context: android.content.Context,
    task: TaskRecord,
): String =
    listOfNotNull(
        task.dueAtEpochMs?.let { t("due_at", formatWhen(context, it)) },
        task.remindAtEpochMs?.let { t("remind_at", formatWhen(context, it)) },
        task.repeat.takeIf { it != TaskRepeat.Never }?.let(::repeatLabel),
        task.originPackage?.let { pkg -> t("from_app", appLabel(context, pkg)) },
    ).joinToString(" · ")

private fun appLabel(
    context: android.content.Context,
    pkg: String,
): String =
    runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

internal fun formatWhen(
    context: android.content.Context,
    millis: Long,
): String =
    android.text.format.DateFormat.getMediumDateFormat(context).format(java.util.Date(millis)) + " · " +
        android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(millis))
