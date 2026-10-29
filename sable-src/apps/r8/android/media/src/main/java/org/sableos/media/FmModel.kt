package org.sableos.media

import android.view.KeyEvent
import org.sableos.media.MediaText.t
import java.util.Locale

/**
 * The Sable FM bridge protocol, as the LineageOS FM Radio app exposes it on SableOS
 * (framework patch packages/apps/FMRadio 1061, com.android.fmradio.sable.SableFmProtocol).
 * The numbers must match that class; tests/run.sh checks they agree.
 *
 * Stations are in units of 100 kHz: 875 is 87.5 MHz.
 */
internal object FmBridgeProtocol {
    const val BRIDGE_PACKAGE = "com.android.fmradio"
    const val ACTION_CONTROL = "org.sableos.fm.action.CONTROL"

    /** Sable Tools and the playing notification open the FM screen with this action. */
    const val ACTION_OPEN_FM = "org.sableos.media.action.FM_RADIO"

    const val MSG_REGISTER = 1
    const val MSG_UNREGISTER = 2
    const val MSG_POWER_UP = 3
    const val MSG_POWER_DOWN = 4
    const val MSG_TUNE = 5
    const val MSG_SEEK = 6
    const val MSG_SCAN = 7
    const val MSG_STOP_SCAN = 8
    const val MSG_SET_SPEAKER = 9
    const val MSG_SET_MUTE = 10
    const val MSG_GET_STATE = 11
    const val MSG_STATE = 100

    const val KEY_POWERED = "powered"
    const val KEY_STATION = "station"
    const val KEY_ANTENNA = "antenna"
    const val KEY_SPEAKER = "speaker"
    const val KEY_MUTED = "muted"
    const val KEY_SCANNING = "scanning"
    const val KEY_SEEKING = "seeking"
    const val KEY_DEVICE_OPEN = "device_open"
    const val KEY_PS = "ps"
    const val KEY_RT = "rt"
    const val KEY_STATIONS = "stations"
    const val KEY_STATION_NAMES = "station_names"
    const val KEY_EVENT = "event"
    const val KEY_OK = "ok"

    /** FmListener.MSGID_SWITCH_ANTENNA: the headset (antenna) was plugged in or out. */
    const val EVENT_SWITCH_ANTENNA = 4

    const val LOWEST_STATION = 875
    const val HIGHEST_STATION = 1080
}

internal data class FmStation(
    val station: Int,
    val name: String = "",
)

/** What the FM screen shows; filled from the bridge's MSG_STATE bundles. */
internal data class FmUiState(
    /** The FM bridge (the LineageOS FM Radio app with the SableOS patch) is installed. */
    val available: Boolean = false,
    val connected: Boolean = false,
    val powered: Boolean = false,
    val station: Int = 0,
    val antenna: Boolean = false,
    val speaker: Boolean = false,
    val muted: Boolean = false,
    val scanning: Boolean = false,
    val seeking: Boolean = false,
    val deviceOpen: Boolean = false,
    val programService: String = "",
    val radioText: String = "",
    val stations: List<FmStation> = emptyList(),
    val lastOk: Boolean = true,
    val lastEvent: Int = 0,
)

internal object FmBand {
    const val STEP = 1

    /** Stations are 100 kHz units. */
    private const val STATIONS_PER_MHZ = 10

    fun isValid(station: Int): Boolean = station in FmBridgeProtocol.LOWEST_STATION..FmBridgeProtocol.HIGHEST_STATION

    /** "98.5" for 985. */
    fun format(station: Int): String =
        String.format(Locale.ROOT, "%d.%d", station / STATIONS_PER_MHZ, station % STATIONS_PER_MHZ)

    /** One 100 kHz step, wrapping at the band edges like the FM Radio app. */
    fun step(
        station: Int,
        up: Boolean,
    ): Int {
        val from = if (isValid(station)) station else FmBridgeProtocol.LOWEST_STATION
        val next = if (up) from + STEP else from - STEP
        return when {
            next > FmBridgeProtocol.HIGHEST_STATION -> FmBridgeProtocol.LOWEST_STATION
            next < FmBridgeProtocol.LOWEST_STATION -> FmBridgeProtocol.HIGHEST_STATION
            else -> next
        }
    }

    /** One line for the status panel. */
    fun status(state: FmUiState): String =
        when {
            !state.connected -> t("connecting_to_the_fm_tuner")
            state.scanning -> t("scanning_the_band")
            state.seeking -> t("seeking")
            !state.antenna -> t("plug_in_wired_headphones_they")
            !state.powered -> t("off")
            state.muted -> t("muted")
            state.speaker -> t("playing_on_the_speaker")
            else -> t("playing_on_headphones")
        }
}

/** Presets are the stations the user starred, in frequency order; the first nine are on Alt+1-9. */
internal object FmPresets {
    const val MAX_KEYED = 9

    fun ordered(favorites: Set<Int>): List<Int> = favorites.filter(FmBand::isValid).sorted()

    fun toggle(
        favorites: Set<Int>,
        station: Int,
    ): Set<Int> =
        when {
            !FmBand.isValid(station) -> favorites
            station in favorites -> favorites - station
            else -> favorites + station
        }

    /** The station for preset [number] (1-based), or null. */
    fun keyed(
        favorites: Set<Int>,
        number: Int,
    ): Int? = ordered(favorites).getOrNull(number - 1)?.takeIf { number in 1..MAX_KEYED }

    /** Starred stations first, then the stations the last scan found, each once. */
    fun list(
        favorites: Set<Int>,
        found: List<FmStation>,
    ): List<FmStation> {
        val names = found.associate { it.station to it.name }
        val starred = ordered(favorites).map { FmStation(it, names[it].orEmpty()) }
        return starred +
            found.filter { FmBand.isValid(it.station) && it.station !in favorites }.distinctBy { it.station }
    }
}

internal enum class FmKeyCommand {
    PowerToggle,
    SeekDown,
    SeekUp,
    TuneDown,
    TuneUp,
    Favorite,
    Output,
    Mute,
    Scan,
}

internal sealed interface FmKeyAction {
    data class Command(
        val command: FmKeyCommand,
    ) : FmKeyAction

    /** Alt+1-9 (Alt+W E R S D F Z X C on the Q25): tune preset [number]. */
    data class Preset(
        val number: Int,
    ) : FmKeyAction
}

/**
 * FM keys (MEDIA_GALLERY_UX.md input contract, applied to the FM mode):
 * Space on/off, J/K or Left/Right seek, Shift+J/K or Shift+Left/Right tune by 0.1 MHz,
 * F star, O speaker/headphones, M mute, R scan (again to stop), Alt+1-9 presets.
 * Anything else (Esc, volume, view keys) goes to [MediaKeymap].
 */
internal object FmKeymap {
    fun action(
        press: MediaKeyPress,
        textInputActive: Boolean,
    ): FmKeyAction? {
        if (textInputActive || press.ctrl) return null
        val preset = press.char?.takeIf { it in '1'..'9' }?.digitToInt()
        val action =
            when {
                preset != null -> FmKeyAction.Preset(preset)
                press.alt -> null
                else -> letterOrArrow(press.keyCode, press.shift)?.let(FmKeyAction::Command)
            }
        val repeatable = action is FmKeyAction.Command && action.command in REPEATABLE
        return action?.takeIf { press.repeatCount == 0 || repeatable }
    }

    private val REPEATABLE = setOf(FmKeyCommand.TuneDown, FmKeyCommand.TuneUp)

    private fun letterOrArrow(
        keyCode: Int,
        shift: Boolean,
    ): FmKeyCommand? =
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                FmKeyCommand.PowerToggle.takeIf { !shift }
            }

            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_J -> {
                if (shift) FmKeyCommand.TuneDown else FmKeyCommand.SeekDown
            }

            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_K -> {
                if (shift) FmKeyCommand.TuneUp else FmKeyCommand.SeekUp
            }

            KeyEvent.KEYCODE_P -> {
                FmKeyCommand.SeekDown.takeIf { !shift }
            }

            KeyEvent.KEYCODE_N -> {
                FmKeyCommand.SeekUp.takeIf { !shift }
            }

            KeyEvent.KEYCODE_F -> {
                FmKeyCommand.Favorite.takeIf { !shift }
            }

            KeyEvent.KEYCODE_O -> {
                FmKeyCommand.Output.takeIf { !shift }
            }

            KeyEvent.KEYCODE_M -> {
                FmKeyCommand.Mute.takeIf { !shift }
            }

            KeyEvent.KEYCODE_R -> {
                FmKeyCommand.Scan.takeIf { !shift }
            }

            else -> {
                null
            }
        }

    /** Media commands that keep working on the FM screen; play/seek/favorite keys belong to FM there. */
    val MEDIA_COMMANDS_ON_FM =
        setOf(
            MediaKeyCommand.Back,
            MediaKeyCommand.VolumeUp,
            MediaKeyCommand.VolumeDown,
            MediaKeyCommand.Search,
            MediaKeyCommand.ViewMode,
            MediaKeyCommand.Queue,
            MediaKeyCommand.Latest,
            MediaKeyCommand.Albums,
        )
}
