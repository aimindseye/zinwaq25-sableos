package org.sableos.tasks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

class TaskStoreTest {
    private fun withDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("sable-tasks-").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun roundTripsEveryField() =
        withDir { dir ->
            val store = TaskStore(dir)
            val task =
                TaskRecord(
                    title = "Email the report",
                    details = "Line one\nRésumé and ગુજરાતી",
                    dueAtEpochMs = 1_900_000_000_000L,
                    remindAtEpochMs = 1_899_999_000_000L,
                    priority = TaskPriority.High,
                    repeat = TaskRepeat.Weekly,
                    zoneId = "America/New_York",
                    originPackage = "example.mail",
                    originToken = "a".repeat(64),
                    updatedAtEpochMs = 16L,
                )
            store.save(task)
            assertEquals(listOf(task), store.list())
            store.save(task.copy(completed = true))
            assertTrue(store.list().single().completed)
            store.delete(task.id)
            assertTrue(store.list().isEmpty())
        }

    @Test
    fun corruptFilesAreSkippedAndBlankTitlesRefused() =
        withDir { dir ->
            File(dir, UUID.randomUUID().toString() + ".task").writeText("incorrect-format")
            assertTrue(TaskStore(dir).list().isEmpty())
            assertTrue(runCatching { TaskRecord(title = " ") }.isFailure)
        }

    @Test
    fun aFailedReplaceKeepsTheOldDataAndLeavesNoTemporaryFile() =
        withDir { dir ->
            val task = TaskRecord(title = "Must not erase data")
            val blocker = File(dir, task.id + ".task").apply { mkdir() }
            val existing = File(blocker, "existing").apply { writeText("retain") }
            assertTrue(runCatching { TaskStore(dir).save(task) }.isFailure)
            assertEquals("retain", existing.readText())
            assertTrue(dir.listFiles()!!.none { it.name.contains(".tmp-") })
        }
}
