package org.sableos.media

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.sableos.design.SableActionButton
import org.sableos.design.SableDenseRow
import org.sableos.design.SableRowAction
import org.sableos.design.SableSpacing
import org.sableos.design.SableValuePanel

/** Whether the FM destination is offered in the top navigation (the FM bridge is installed). */
internal val LocalFmAvailable = staticCompositionLocalOf { false }

private const val FM_KEY_HINT =
    "Space on/off · J/K seek · Shift+J/K ±0.1 · Alt+1-9 presets · F star · O output · M mute · R scan"
private const val FM_ACTION_TUNE = "tune"
private const val FM_ACTION_STAR = "star"
private val FmBadgeSize = 48.dp

internal data class FmScreenActions(
    val onPower: () -> Unit,
    val onSeek: (up: Boolean) -> Unit,
    val onStep: (up: Boolean) -> Unit,
    val onTune: (Int) -> Unit,
    val onToggleFavorite: (Int) -> Unit,
    val onToggleSpeaker: () -> Unit,
    val onToggleMute: () -> Unit,
    val onScan: () -> Unit,
)

/** Sable Media FM mode (SABLE_MEDIA_FM_RADIO_MODE): tuner, presets and the stations the last scan found. */
@Composable
internal fun FmScreen(
    fm: FmUiState,
    favorites: Set<Int>,
    actions: FmScreenActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg)) {
        if (!fm.available) {
            SableValuePanel(
                label = "fm radio",
                value = "Not available",
                supportingText = "This device has no FM tuner service Sable Media can use.",
            )
        } else {
            FmTunerPanel(fm)
            FmControls(fm, favorites, actions)
            Text(
                text = FM_KEY_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FmStationList(fm, favorites, actions)
        }
    }
}

@Composable
private fun FmTunerPanel(fm: FmUiState) {
    val tuned = FmBand.isValid(fm.station)
    val name =
        fm.programService.ifBlank {
            fm.stations
                .firstOrNull { it.station == fm.station }
                ?.name
                .orEmpty()
        }
    SableValuePanel(
        label = if (tuned) "${FmBand.format(fm.station)} MHz" else "fm radio",
        value = FmBand.status(fm),
        supportingText = listOf(name, fm.radioText).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
    )
    if (fm.connected && !fm.antenna) {
        Text(
            text =
                "FM uses wired headphones as its antenna. Plug them in to listen; " +
                    "Speaker then plays through the speaker with the headphones still in.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** One button of a three-button row. */
private data class FmButton(
    val text: String,
    val primary: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun FmControls(
    fm: FmUiState,
    favorites: Set<Int>,
    actions: FmScreenActions,
) {
    val rows =
        listOf(
            listOf(
                FmButton("◀ Seek") { actions.onSeek(false) },
                FmButton(if (fm.powered) "Turn off" else "Turn on", primary = true, onClick = actions.onPower),
                FmButton("Seek ▶") { actions.onSeek(true) },
            ),
            listOf(
                FmButton("−0.1") { actions.onStep(false) },
                FmButton(if (fm.speaker) "Headphones" else "Speaker", onClick = actions.onToggleSpeaker),
                FmButton("+0.1") { actions.onStep(true) },
            ),
            listOf(
                FmButton(if (fm.muted) "Unmute" else "Mute", onClick = actions.onToggleMute),
                FmButton(if (fm.scanning) "Stop scan" else "Scan", onClick = actions.onScan),
                FmButton(if (fm.station in favorites) "Unstar" else "Star") { actions.onToggleFavorite(fm.station) },
            ),
        )
    rows.forEach { buttons ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            buttons.forEach { button ->
                SableActionButton(
                    text = button.text,
                    modifier = Modifier.weight(1f),
                    primary = button.primary,
                    onClick = button.onClick,
                )
            }
        }
    }
}

/** A row of the station list. */
private data class FmStationItem(
    val station: FmStation,
    val presetNumber: Int?,
    val starred: Boolean,
    val current: Boolean,
)

@Composable
private fun FmStationList(
    fm: FmUiState,
    favorites: Set<Int>,
    actions: FmScreenActions,
) {
    val list = FmPresets.list(favorites, fm.stations)
    if (list.isEmpty()) {
        Text(
            text = "No stations yet. Press R to scan, or star a station with F.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val presets = FmPresets.ordered(favorites)
    list.forEach { station ->
        val index = presets.indexOf(station.station)
        FmStationRow(
            item =
                FmStationItem(
                    station = station,
                    presetNumber = if (index in 0 until FmPresets.MAX_KEYED) index + 1 else null,
                    starred = station.station in favorites,
                    current = station.station == fm.station && fm.powered,
                ),
            onTune = { actions.onTune(station.station) },
            onToggleFavorite = { actions.onToggleFavorite(station.station) },
        )
    }
}

@Composable
private fun FmStationRow(
    item: FmStationItem,
    onTune: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val station = item.station
    val starred = item.starred
    val current = item.current
    val presetNumber = item.presetNumber
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (current) MediaPink else MaterialTheme.colorScheme.outlineVariant),
    ) {
        SableDenseRow(
            title = "${FmBand.format(station.station)} MHz" + if (starred) " ★" else "",
            subtitle =
                listOfNotNull(
                    station.name.takeIf { it.isNotBlank() },
                    presetNumber?.let { "Alt+$it" },
                    "playing".takeIf { current },
                ).joinToString(" · ").ifBlank { null },
            onClick = onTune,
            modifier = Modifier.padding(horizontal = SableSpacing.Md, vertical = SableSpacing.Xs),
            actions =
                listOf(
                    SableRowAction(FM_ACTION_TUNE, "Tune", primary = true),
                    SableRowAction(FM_ACTION_STAR, if (starred) "Remove star ★" else "Star ☆"),
                ),
            onAction = { action ->
                when (action.id) {
                    FM_ACTION_TUNE -> onTune()
                    FM_ACTION_STAR -> onToggleFavorite()
                }
            },
            leading = {
                Surface(
                    modifier = Modifier.width(FmBadgeSize),
                    shape = MaterialTheme.shapes.small,
                    color = MediaPink.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, MediaPink.copy(alpha = 0.7f)),
                ) {
                    Box(modifier = Modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = "FM",
                            style = MaterialTheme.typography.labelLarge,
                            color = MediaPink,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            },
        )
    }
}
