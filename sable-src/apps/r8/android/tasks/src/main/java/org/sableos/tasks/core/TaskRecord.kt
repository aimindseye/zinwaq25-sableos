package org.sableos.tasks.core

import java.time.ZoneId
import java.util.UUID

enum class TaskPriority { Low, Normal, High }

enum class TaskRepeat { Never, Daily, Weekly, Monthly }

/**
 * One task, kept in app storage only: no account, no Calendar entry, no sync. Ported from TenSeven
 * Tasks (aimindseye/tenseven-launcher, Apache-2.0). A follow-up made from a Hub notification keeps
 * the source app's package and a one-way digest of its key, never the notification's text.
 */
data class TaskRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val details: String = "",
    val completed: Boolean = false,
    val dueAtEpochMs: Long? = null,
    val remindAtEpochMs: Long? = null,
    val zoneId: String = ZoneId.systemDefault().id,
    val priority: TaskPriority = TaskPriority.Normal,
    val repeat: TaskRepeat = TaskRepeat.Never,
    val originPackage: String? = null,
    val originToken: String? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
) {
    init {
        require(UUID_PATTERN.matches(id)) { "Invalid task ID" }
        require(title.isNotBlank() && title.length <= MAX_TITLE) { "Task title is required" }
        require(details.length <= MAX_DETAILS) { "Task details too long" }
        require(dueAtEpochMs == null || dueAtEpochMs >= 0) { "Invalid due date" }
        require(remindAtEpochMs == null || remindAtEpochMs >= 0) { "Invalid reminder date" }
        require(updatedAtEpochMs >= 0) { "Invalid update time" }
        require(originPackage == null || (originPackage.isNotBlank() && originPackage.length <= MAX_TITLE))
        require(originToken == null || originToken.matches(TOKEN_PATTERN))
        ZoneId.of(zoneId)
    }

    companion object {
        const val MAX_TITLE = 240
        const val MAX_DETAILS = 16_000
        private val UUID_PATTERN = Regex("[0-9a-f-]{36}")
        private val TOKEN_PATTERN = Regex("[0-9a-f]{64}")

        /** A task from shared text: the first line is the title, the rest the details. */
        fun fromSharedText(text: String): TaskRecord? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            val title =
                trimmed
                    .lineSequence()
                    .first()
                    .trim()
                    .take(MAX_TITLE)
            val details = trimmed.substringAfter('\n', "").trim().take(MAX_DETAILS)
            return TaskRecord(title = title, details = details)
        }
    }
}
