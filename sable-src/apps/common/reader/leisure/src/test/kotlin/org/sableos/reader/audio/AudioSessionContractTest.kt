package org.sableos.reader.audio

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sableos.reader.engine.audio.SleepMode

@RunWith(RobolectricTestRunner::class)
class AudioSessionContractTest {
    @Test
    fun sleepModesRoundTripThroughTheCommandBundle() {
        val after = SleepMode.After(15 * 60_000L)
        assertEquals(after, AudioSessionContract.sleepModeOf(AudioSessionContract.sleepArgs(after)))
        val endOfChapter = AudioSessionContract.sleepArgs(SleepMode.EndOfChapter)
        assertEquals(SleepMode.EndOfChapter, AudioSessionContract.sleepModeOf(endOfChapter))
        assertNull(AudioSessionContract.sleepModeOf(AudioSessionContract.sleepArgs(null)))
    }

    @Test
    fun malformedSleepCommandsAreNotInterpreted() {
        assertNull(AudioSessionContract.sleepModeOf(Bundle()))
        val noDuration = Bundle().apply {
            putString(AudioSessionContract.KEY_SLEEP_KIND, AudioSessionContract.SLEEP_AFTER)
        }
        assertNull(AudioSessionContract.sleepModeOf(noDuration))
        val negative = Bundle().apply {
            putString(AudioSessionContract.KEY_SLEEP_KIND, AudioSessionContract.SLEEP_AFTER)
            putLong(AudioSessionContract.KEY_SLEEP_DURATION_MS, -5L)
        }
        assertNull(AudioSessionContract.sleepModeOf(negative))
        val unknown = Bundle().apply { putString(AudioSessionContract.KEY_SLEEP_KIND, "WHENEVER") }
        assertNull(AudioSessionContract.sleepModeOf(unknown))
    }

    @Test
    fun theCommandActionsArePrivateToThisApp() {
        listOf(AudioSessionContract.openBook, AudioSessionContract.setSleep, AudioSessionContract.setSkip).forEach {
            assertTrue(it.customAction.startsWith("org.sableos.reader.audio."))
        }
    }

    @Test
    fun onlyLocalSourcesAreAcceptedByTheSessionPolicy() {
        assertTrue(AudioSessionCallback.isPlayable("content://media/external/audio/1"))
        assertTrue(AudioSessionCallback.isPlayable("file:///sdcard/book.m4b"))
        assertFalse(AudioSessionCallback.isPlayable("https://example.invalid/book.mp3"))
        assertFalse(AudioSessionCallback.isPlayable("http://example.invalid/book.mp3"))
        assertFalse(AudioSessionCallback.isPlayable("rtsp://example.invalid/live"))
    }
}
