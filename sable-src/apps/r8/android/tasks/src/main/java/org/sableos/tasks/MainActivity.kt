package org.sableos.tasks

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import org.sableos.design.SableGlobalTheme
import org.sableos.tasks.core.ReminderPreset
import org.sableos.tasks.core.TaskRecord
import org.sableos.tasks.core.TaskSchedule
import org.sableos.tasks.core.TasksKeys
import org.sableos.tasks.core.TasksText.t
import org.sableos.tasks.platform.TaskAlarms
import org.sableos.tasks.platform.taskRepository
import org.sableos.tasks.ui.TasksApp
import org.sableos.tasks.ui.TasksState
import java.time.Instant
import java.time.ZoneId

/**
 * Sable Tasks: tasks and reminders kept on the phone (ported from TenSeven Tasks). Text shared from
 * another app becomes a new task; a reminder notification opens its task.
 */
class MainActivity : ComponentActivity() {
    private val repository by lazy { taskRepository(this) }
    private val state = TasksState()
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refresh()
        setContent {
            SableGlobalTheme(window = window) {
                TasksApp(state, repository, onChanged = ::refresh, onAllowNotifications = ::allowNotifications)
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        state.tasks = repository.list()
        state.notificationsAllowed = TaskAlarms.notificationsAllowed(this)
    }

    private fun allowNotifications() {
        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when {
            intent.action == Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let(TaskRecord::fromSharedText)?.let { state.openEditor(it, isNew = true) }
            }

            intent.action == ACTION_FOLLOW_UP -> followUp(intent)

            else -> {
                intent.getStringExtra(EXTRA_TASK_ID)?.let(repository::find)?.let { state.openEditor(it, isNew = false) }
            }
        }
    }

    /** Hub's "follow up": a task named after the app, reminding tomorrow morning; no notification text. */
    private fun followUp(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_SOURCE_PACKAGE) ?: return
        val key = intent.getStringExtra(EXTRA_SOURCE_KEY) ?: return
        val app = intent.getStringExtra(EXTRA_SOURCE_APP)?.take(APP_NAME_MAX)?.ifBlank { null } ?: pkg
        val remind = TaskSchedule.preset(ReminderPreset.TomorrowMorning, Instant.now(), ZoneId.systemDefault())
        val task = repository.followUp(pkg, key, t("follow_up_in", app), remind)
        refresh()
        state.openEditor(task, isNew = false)
    }

    /** List keys arrive here only when no focused view used them, so typing in a field always wins. */
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        val modified = event.isAltPressed || event.isCtrlPressed || event.isMetaPressed
        val key = TasksKeys.screen(keyCode, modified)
        return (key != null && event.repeatCount == 0 && state.onScreenKey(key)) || super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_TASK_ID = "org.sableos.tasks.extra.TASK_ID"
        const val ACTION_FOLLOW_UP = "org.sableos.tasks.action.FOLLOW_UP"
        const val EXTRA_SOURCE_PACKAGE = "org.sableos.tasks.extra.SOURCE_PACKAGE"
        const val EXTRA_SOURCE_KEY = "org.sableos.tasks.extra.SOURCE_KEY"
        const val EXTRA_SOURCE_APP = "org.sableos.tasks.extra.SOURCE_APP"
        private const val APP_NAME_MAX = 100
    }
}
