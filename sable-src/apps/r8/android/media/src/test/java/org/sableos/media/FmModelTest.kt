package org.sableos.media

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FmModelTest {
    private fun fm(
        keyCode: Int,
        char: Char? = null,
        shift: Boolean = false,
        alt: Boolean = false,
        repeat: Int = 0,
    ) = FmKeymap.action(MediaKeyPress(keyCode, char, shift, alt, repeatCount = repeat), textInputActive = false)

    private fun command(c: FmKeyCommand) = FmKeyAction.Command(c)

    @Test
    fun seekTuneAndPowerKeys() {
        assertEquals(command(FmKeyCommand.PowerToggle), fm(KeyEvent.KEYCODE_SPACE, ' '))
        assertEquals(command(FmKeyCommand.SeekDown), fm(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(command(FmKeyCommand.SeekUp), fm(KeyEvent.KEYCODE_K, 'k'))
        assertEquals(command(FmKeyCommand.TuneDown), fm(KeyEvent.KEYCODE_J, 'J', shift = true))
        assertEquals(command(FmKeyCommand.TuneUp), fm(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true, repeat = 4))
        assertEquals(command(FmKeyCommand.Output), fm(KeyEvent.KEYCODE_O, 'o'))
        assertEquals(command(FmKeyCommand.Scan), fm(KeyEvent.KEYCODE_R, 'r'))
        assertEquals(command(FmKeyCommand.Favorite), fm(KeyEvent.KEYCODE_F, 'f'))
        assertEquals(command(FmKeyCommand.Mute), fm(KeyEvent.KEYCODE_M, 'm'))
    }

    @Test
    fun presetsFromTheAltDigitLayer() {
        // Alt+W is 1 and Alt+C is 9 on the Q25.
        assertEquals(FmKeyAction.Preset(1), fm(KeyEvent.KEYCODE_W, '1', alt = true))
        assertEquals(FmKeyAction.Preset(9), fm(KeyEvent.KEYCODE_C, '9', alt = true))
        assertNull(fm(KeyEvent.KEYCODE_U, '_', alt = true))
    }

    @Test
    fun heldKeysRepeatOnlyFineTuneAndTextInputWins() {
        assertNull(fm(KeyEvent.KEYCODE_SPACE, ' ', repeat = 1))
        assertNull(fm(KeyEvent.KEYCODE_DPAD_LEFT, repeat = 1))
        assertNull(FmKeymap.action(MediaKeyPress(KeyEvent.KEYCODE_SPACE, ' '), textInputActive = true))
    }

    @Test
    fun bandStepsWrapAndFormat() {
        assertEquals(876, FmBand.step(875, up = true))
        assertEquals(1080, FmBand.step(875, up = false))
        assertEquals(875, FmBand.step(1080, up = true))
        assertEquals(1080, FmBand.step(0, up = false))
        assertEquals("98.5", FmBand.format(985))
        assertEquals("108.0", FmBand.format(1080))
    }

    @Test
    fun presetsAreStarredStationsInFrequencyOrder() {
        val favorites = setOf(1015, 889, 2000)
        assertEquals(listOf(889, 1015), FmPresets.ordered(favorites))
        assertEquals(889, FmPresets.keyed(favorites, 1))
        assertNull(FmPresets.keyed(favorites, 3))
        assertEquals(setOf(1015, 889, 2000, 950), FmPresets.toggle(favorites, 950))
        assertEquals(setOf(1015, 2000), FmPresets.toggle(favorites, 889))
        val list = FmPresets.list(setOf(1015), listOf(FmStation(889, "Jazz"), FmStation(1015, "News"), FmStation(889)))
        assertEquals(listOf(FmStation(1015, "News"), FmStation(889, "Jazz")), list)
    }

    @Test
    fun statusNamesTheAntenna() {
        val connected = FmUiState(available = true, connected = true)
        assertEquals("Plug in wired headphones: they are the antenna", FmBand.status(connected))
        assertEquals("Off", FmBand.status(connected.copy(antenna = true)))
        assertEquals(
            "Playing on the speaker",
            FmBand.status(connected.copy(antenna = true, powered = true, speaker = true)),
        )
    }
}
