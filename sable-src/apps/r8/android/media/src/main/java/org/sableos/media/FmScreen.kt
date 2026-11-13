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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.sableos.design.SableActionButton
import org.sableos.design.SableDenseRow
import org.sableos.design.SableRowAction
import org.sableos.design.SableSpacing
import org.sableos.design.SableValuePanel
import org.sableos.media.MediaText.t

/** Whether the FM destination is offered in the top navigation (the FM bridge is installed). */
internal val LocalFmAvailable = staticCompositionLocalOf { false }

private val FM_KEY_HINT: String get() = t("space_on_off_j_k")
private const val FM_ACTION_TUNE = "tune"
private const val FM_ACTION_STAR = "star"
private const val FM_ACTION_NAME = "name"
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
    /** Gives a station a name of the person's own; a blank name removes it. */
    val onRename: (Int, String) -> Unit = { _, _ -> },
)

/** Sable Media FM mode (SABLE_MEDIA_FM_RADIO_MODE): tuner, presets and the stations the last scan found. */
@Composable
internal fun FmScreen(
    fm: FmUiState,
    favorites: Set<Int>,
    actions: FmScreenActions,
    names: Map<Int, String> = emptyMap(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg)) {
        if (!fm.available) {
            SableValuePanel(
                label = t("fm_radio"),
                value = t("not_available"),
                supportingText = t("this_device_has_no_fm"),
            )
        } else {
            FmTunerPanel(fm, names)
            FmControls(fm, favorites, actions)
            Text(
                text = FM_KEY_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FmStationList(fm, favorites, names, actions)
        }
    }
}

@Composable
private fun FmTunerPanel(
    fm: FmUiState,
    names: Map<Int, String>,
) {
    val tuned = FmBand.isValid(fm.station)
    val name =
        names[fm.station] ?: fm.programService.ifBlank {
            fm.stations
                .firstOrNull { it.station == fm.station }
                ?.name
                .orEmpty()
        }
    SableValuePanel(
        label = if (tuned) "${FmBand.format(fm.station)} MHz" else t("fm_radio"),
        value = FmBand.status(fm),
        supportingText = listOf(name, fm.radioText).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
    )
    if (fm.connected && !fm.antenna) {
        Text(
            text =
                t("fm_uses_wired_headphones_as"),
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
                FmButton(t("seek")) { actions.onSeek(false) },
                FmButton(if (fm.powered) t("turn_off") else t("turn_on"), primary = true, onClick = actions.onPower),
                FmButton(t("seek_2")) { actions.onSeek(true) },
            ),
            listOf(
                FmButton("−0.1") { actions.onStep(false) },
                FmButton(if (fm.speaker) t("headphones") else t("speaker"), onClick = actions.onToggleSpeaker),
                FmButton("+0.1") { actions.onStep(true) },
            ),
            listOf(
                FmButton(if (fm.muted) t("unmute") else t("mute"), onClick = actions.onToggleMute),
                FmButton(if (fm.scanning) t("stop_scan") else t("scan"), onClick = actions.onScan),
                FmButton(
                    if (fm.station in
                        favorites
                    ) {
                        t("unstar")
                    } else {
                        t("star")
                    },
                ) { actions.onToggleFavorite(fm.station) },
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
    /** The person's own name for the station, or blank. */
    val customName: String,
    val presetNumber: Int?,
    val starred: Boolean,
    val current: Boolean,
)

@Composable
private fun FmStationList(
    fm: FmUiState,
    favorites: Set<Int>,
    names: Map<Int, String>,
    actions: FmScreenActions,
) {
    val list = FmPresets.list(favorites, fm.stations)
    if (list.isEmpty()) {
        Text(
            text = t("no_stations_yet_press_r"),
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
                    customName = names[station.station].orEmpty(),
                    presetNumber = if (index in 0 until FmPresets.MAX_KEYED) index + 1 else null,
                    starred = station.station in favorites,
                    current = station.station == fm.station && fm.powered,
                ),
            onTune = { actions.onTune(station.station) },
            onToggleFavorite = { actions.onToggleFavorite(station.station) },
            onRename = { name -> actions.onRename(station.station, name) },
        )
    }
}

@Composable
private fun FmStationRow(
    item: FmStationItem,
    onTune: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: (String) -> Unit,
) {
    var naming by remember(item.station.station) { mutableStateOf(false) }
    val station = item.station
    val starred = item.starred
    val current = item.current
    val presetNumber = item.presetNumber
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (current) MediaAccent else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            SableDenseRow(
                title = item.customName.ifBlank { "${FmBand.format(station.station)} MHz" } + if (starred) " ★" else "",
                subtitle =
                    listOfNotNull(
                        "${FmBand.format(station.station)} MHz".takeIf { item.customName.isNotBlank() },
                        station.name.takeIf { it.isNotBlank() && it != item.customName },
                        presetNumber?.let { "Alt+$it" },
                        "playing".takeIf { current },
                    ).joinToString(" · ").ifBlank { null },
                onClick = onTune,
                modifier = Modifier.padding(horizontal = SableSpacing.Md, vertical = SableSpacing.Xs),
                actions =
                    listOf(
                        SableRowAction(FM_ACTION_TUNE, t("tune"), primary = true),
                        SableRowAction(FM_ACTION_STAR, if (starred) t("remove_star") else t("star_2")),
                    ) + listOfNotNull(SableRowAction(FM_ACTION_NAME, t("fm_name")).takeIf { starred }),
                onAction = { action ->
                    when (action.id) {
                        FM_ACTION_TUNE -> onTune()
                        FM_ACTION_STAR -> onToggleFavorite()
                        FM_ACTION_NAME -> naming = true
                    }
                },
                leading = { FmFrequencyTile(station.station) },
            )
            if (naming) {
                FmNameEditor(
                    initial = item.customName.ifBlank { station.name },
                    onSave = { name ->
                        naming = false
                        onRename(name)
                    },
                    onCancel = { naming = false },
                )
            }
        }
    }
}

/** Names a starred station (owner, 2026-11-13): a name of the person's own, or blank to clear it. */
@Composable
private fun FmNameEditor(
    initial: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    Column(
        modifier = Modifier.padding(horizontal = SableSpacing.Md, vertical = SableSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(FmPresets.MAX_NAME) },
            label = { Text(t("station_name")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(name) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm)) {
            SableActionButton(text = t("hub_save"), modifier = Modifier.weight(1f), onClick = { onSave(name) })
            SableActionButton(
                text = t("fm_clear_name"),
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = { onSave("") },
            )
            SableActionButton(text = t("cancel"), primary = false, modifier = Modifier.weight(1f), onClick = onCancel)
        }
    }
}

/** The row's tile: the station's frequency, as FM stations have no picture. */
@Composable
private fun FmFrequencyTile(station: Int) {
    Surface(
        modifier = Modifier.width(FmBadgeSize),
        shape = MaterialTheme.shapes.small,
        color = MediaAccent.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, MediaAccent.copy(alpha = 0.7f)),
    ) {
        Box(modifier = Modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
            Text(
                text = FmBand.format(station),
                style = MaterialTheme.typography.labelLarge,
                color = MediaAccent,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
