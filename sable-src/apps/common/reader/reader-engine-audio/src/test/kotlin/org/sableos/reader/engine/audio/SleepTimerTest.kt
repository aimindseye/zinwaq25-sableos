package org.sableos.reader.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {
    private class FakeClock(var now: Long = 1_000L) : MonotonicClock {
        override fun nowMs(): Long = now
    }

    private val timeline = AudiobookTimeline(
        AudioManifest(
            title = "T",
            tracks = listOf(AudioTrack("content://a", "a", 600_000)),
            chapters = listOf(AudioChapter("One", 0), AudioChapter("Two", 200_000), AudioChapter("Three", 400_000)),
        ),
    )

    private fun timer(clock: FakeClock) = SleepTimer(clock, timeline)

    @Test
    fun aTimedSleepFiresOnceAtTheDeadlineAndDisarms() {
        val clock = FakeClock()
        val t = timer(clock)
        t.start(SleepMode.After(60_000), currentPositionMs = 5_000)
        assertTrue(t.isArmed)
        assertEquals(60_000L, t.remainingMs())
        clock.now += 59_999
        assertEquals(SleepAction.None, t.tick(10_000))
        clock.now += 1
        assertEquals(SleepAction.Pause(), t.tick(10_000))
        assertFalse(t.isArmed)
        assertEquals(SleepAction.None, t.tick(10_000))
        assertNull(t.remainingMs())
    }

    @Test
    fun cancelAndRestartReplaceTheDeadline() {
        val clock = FakeClock()
        val t = timer(clock)
        t.start(SleepMode.After(10_000), 0)
        t.cancel()
        clock.now += 20_000
        assertEquals(SleepAction.None, t.tick(0))
        t.start(SleepMode.After(5_000), 0)
        clock.now += 5_000
        assertEquals(SleepAction.Pause(), t.tick(0))
    }

    @Test
    fun extendingPushesTheDeadlineOnlyForTimedSleep() {
        val clock = FakeClock()
        val t = timer(clock)
        t.start(SleepMode.After(10_000), 0)
        assertTrue(t.extend(5_000))
        clock.now += 10_000
        assertEquals(SleepAction.None, t.tick(0))
        clock.now += 5_000
        assertEquals(SleepAction.Pause(), t.tick(0))
        t.start(SleepMode.EndOfChapter, 0)
        assertFalse(t.extend(1_000))
        assertFalse(timer(clock).extend(1_000))
    }

    @Test
    fun endOfChapterFiresWhenTheArmedChapterEndsAndSeeksBackToItsEnd() {
        val clock = FakeClock()
        val t = timer(clock)
        t.start(SleepMode.EndOfChapter, currentPositionMs = 150_000)
        assertEquals(SleepAction.None, t.tick(199_000))
        assertEquals(SleepAction.Pause(seekToMs = 200_000), t.tick(200_400))
        assertFalse(t.isArmed)
    }

    @Test
    fun endOfChapterInTheLastChapterFiresAtTheEndOfTheBook() {
        val t = timer(FakeClock())
        t.start(SleepMode.EndOfChapter, currentPositionMs = 500_000)
        assertEquals(SleepAction.None, t.tick(599_000))
        assertEquals(SleepAction.Pause(seekToMs = 600_000), t.tick(600_000))
    }

    @Test
    fun endOfChapterFollowsASeekIntoAnotherChapter() {
        val t = timer(FakeClock())
        t.start(SleepMode.EndOfChapter, currentPositionMs = 10_000)
        assertEquals(SleepAction.Pause(seekToMs = 200_000), t.tick(250_000))
    }

    @Test
    fun aTimerNeedsAPositiveDuration() {
        val result = runCatching { timer(FakeClock()).start(SleepMode.After(0), 0) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun theTimerNeverReadsAnyClockButTheInjectedOne() {
        var reads = 0
        val clock = MonotonicClock { reads++; 50L }
        val t = SleepTimer(clock, timeline)
        t.start(SleepMode.After(10), 0)
        t.tick(0)
        assertTrue(reads >= 2)
    }
}
