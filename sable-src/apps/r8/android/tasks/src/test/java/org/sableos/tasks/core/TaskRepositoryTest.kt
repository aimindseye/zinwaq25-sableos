package org.sableos.tasks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

class TaskRepositoryTest {
    private class Recorder(
        private val store: TaskStore,
    ) : TaskScheduler {
        val events = mutableListOf<String>()

        override fun cancel(id: String) {
            events += "cancel:$id"
        }

        override fun schedule(task: TaskRecord) {
            assertTrue("written before alarms change", store.list().any { it == task })
            events += "schedule:${task.id}"
        }
    }

    private fun withDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("sable-task-repo-").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun saveSchedulesAndDeleteCancels() =
        withDir { dir ->
            val store = TaskStore(dir)
            val scheduler = Recorder(store)
            val repo = TaskRepository(store, scheduler) { 1000L }
            val task = TaskRecord(title = "Prepare report")
            repo.save(task)
            assertEquals(listOf("cancel:${task.id}", "schedule:${task.id}"), scheduler.events)
            assertEquals(listOf(task), TaskRepository(TaskStore(dir), scheduler).list())
            repo.delete(task.id)
            assertTrue(repo.list().isEmpty())
            assertEquals("cancel:${task.id}", scheduler.events.last())
        }

    @Test
    fun aFailedWriteLeavesAlarmsAlone() =
        withDir { dir ->
            val blocked = File(dir, "not-a-directory").apply { writeText("blocked") }
            val store = TaskStore(blocked)
            val scheduler = Recorder(store)
            assertTrue(runCatching { TaskRepository(store, scheduler).save(TaskRecord(title = "New")) }.isFailure)
            assertTrue(scheduler.events.isEmpty())
        }

    @Test
    fun completingARepeatMovesItForwardAndSnoozeIsAnHour() =
        withDir { dir ->
            val store = TaskStore(dir)
            val now = Instant.parse("2026-11-02T16:00:00Z").toEpochMilli()
            val repo = TaskRepository(store, Recorder(store)) { now }
            val daily =
                TaskRecord(
                    title = "Daily",
                    zoneId = "UTC",
                    repeat = TaskRepeat.Daily,
                    dueAtEpochMs = now - 1000,
                    remindAtEpochMs = now - 2000,
                )
            val next = repo.complete(daily)
            assertFalse(next.completed)
            assertTrue(next.dueAtEpochMs!! > now && next.remindAtEpochMs!! > now)
            val once = repo.complete(TaskRecord(title = "Once"))
            assertTrue(once.completed)
            assertFalse(repo.complete(once).completed)
            assertEquals(now + TaskSchedule.SNOOZE_MILLIS, repo.snooze(daily).remindAtEpochMs)
        }

    @Test
    fun followUpsKeepOnlyAHashedKeyAndDoNotDuplicate() =
        withDir { dir ->
            val store = TaskStore(dir)
            val repo = TaskRepository(store, Recorder(store)) { 1000L }
            val first = repo.followUp("example.mail", "private-original-key", "Follow up in Mail", 2000L)
            val again = repo.followUp("example.mail", "private-original-key", "Follow up in Mail", null)
            assertEquals(first.id, again.id)
            assertNull(again.remindAtEpochMs)
            assertEquals(1, repo.list().size)
            assertEquals(64, again.originToken!!.length)
            assertTrue(dir.listFiles()!!.none { it.readText().contains("private-original-key") })
            repo.complete(again)
            val later = repo.followUp("example.mail", "private-original-key", "Follow up in Mail", 3000L)
            assertNotEquals(again.id, later.id)
        }
}
