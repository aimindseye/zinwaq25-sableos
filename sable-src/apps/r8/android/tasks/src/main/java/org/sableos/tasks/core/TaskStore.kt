package org.sableos.tasks.core

import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID

/**
 * One file per task in app storage, written atomically (temporary file, then a move), so a failed write
 * never loses the old copy. Ported from TenSeven Tasks; the format name is Sable's own.
 */
class TaskStore(
    private val directory: File,
) {
    fun list(): List<TaskRecord> =
        directory
            .listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }
            .orEmpty()
            .mapNotNull { file ->
                runCatching { decode(file.name.removeSuffix(SUFFIX), file.inputStream().use(::loadProperties)) }.getOrNull()
            }.sortedWith(compareBy<TaskRecord> { it.completed }.thenByDescending { it.updatedAtEpochMs })

    @Synchronized
    fun save(task: TaskRecord) {
        directory.mkdirs()
        val destination = file(task.id)
        val temporary = File(directory, task.id + ".tmp-" + UUID.randomUUID())
        temporary.outputStream().use { properties(task).store(it, "Sable Tasks v1") }
        try {
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temporary.exists()) Files.delete(temporary.toPath())
        }
    }

    fun delete(id: String) {
        Files.deleteIfExists(file(id).toPath())
    }

    private fun file(id: String): File {
        require(ID.matches(id)) { "Invalid task ID" }
        return File(directory, "$id$SUFFIX")
    }

    private fun decode(
        id: String,
        props: Properties,
    ): TaskRecord {
        require(props.getProperty("format") == FORMAT && props.getProperty("version") == "1")

        fun optionalLong(key: String): Long? = props.getProperty(key)?.takeIf(String::isNotBlank)?.toLong()
        return TaskRecord(
            id = id,
            title = props.getProperty("title") ?: error("Missing task title"),
            details = props.getProperty("details", ""),
            completed = props.getProperty("completed") == "true",
            dueAtEpochMs = optionalLong("dueAt"),
            remindAtEpochMs = optionalLong("remindAt"),
            zoneId = props.getProperty("zone", "UTC"),
            priority = TaskPriority.valueOf(props.getProperty("priority", TaskPriority.Normal.name)),
            repeat = TaskRepeat.valueOf(props.getProperty("repeat", TaskRepeat.Never.name)),
            originPackage = props.getProperty("originPackage")?.takeIf(String::isNotBlank),
            originToken = props.getProperty("originToken")?.takeIf(String::isNotBlank),
            updatedAtEpochMs = props.getProperty("updatedAt", "0").toLong(),
        )
    }

    private fun properties(task: TaskRecord) =
        Properties().apply {
            setProperty("format", FORMAT)
            setProperty("version", "1")
            setProperty("title", task.title)
            setProperty("details", task.details)
            setProperty("completed", task.completed.toString())
            setProperty("dueAt", task.dueAtEpochMs?.toString().orEmpty())
            setProperty("remindAt", task.remindAtEpochMs?.toString().orEmpty())
            setProperty("zone", task.zoneId)
            setProperty("priority", task.priority.name)
            setProperty("repeat", task.repeat.name)
            setProperty("updatedAt", task.updatedAtEpochMs.toString())
            setProperty("originPackage", task.originPackage.orEmpty())
            setProperty("originToken", task.originToken.orEmpty())
        }

    private fun loadProperties(stream: InputStream): Properties = Properties().apply { load(stream) }

    private companion object {
        const val FORMAT = "sable-task"
        const val SUFFIX = ".task"
        val ID = Regex("[0-9a-f-]{36}")
    }
}
