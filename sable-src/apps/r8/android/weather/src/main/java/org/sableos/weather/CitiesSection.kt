package org.sableos.weather

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import org.sableos.weather.cities.City
import org.sableos.weather.cities.CityCatalog
import org.sableos.weather.cities.CityEdit
import org.sableos.weather.cities.CityEffect
import org.sableos.weather.cities.CityIds
import org.sableos.weather.cities.CityScreen
import org.sableos.weather.cities.CityScreenModel
import org.sableos.weather.cities.DialogButton
import org.sableos.weather.input.KeyMapper
import org.sableos.weather.input.RawKey
import android.view.KeyEvent as AndroidKeyEvent

/**
 * User-managed cities (IR-015): select, add from the offline catalog or a manual "Name, lat, lon, Area/Zone" entry,
 * remove, and reset to the built-in cities. Keyboard behaviour comes from [CityScreenModel]; every item stays a
 * touch target. [onActiveCityChanged] is called whenever an edit changes the active city.
 */
@Composable
internal fun CitiesSection(
    repository: WeatherRepository,
    onActiveCityChanged: (WeatherLocation) -> Unit,
) {
    var cityState by remember { mutableStateOf(repository.cities.load()) }
    var results by remember { mutableStateOf(emptyList<City>()) }
    val model = CityScreenModel(cityState.cities, results.size)
    var screen by remember { mutableStateOf(model.initial(cityState.selected)) }
    var fieldFocused by remember { mutableStateOf(false) }
    val listFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }

    fun applyEdit(edit: CityEdit) {
        repository.afterCityEdit(edit)
        cityState = edit.state
        results = emptyList()
        screen = CityScreenModel(edit.state.cities).afterChange(screen, edit.state.selected.name)
        if (edit.refresh) onActiveCityChanged(edit.state.selected.toLocation())
    }

    fun runEffect(effect: CityEffect): Boolean =
        when (effect) {
            is CityEffect.Select -> {
                applyEdit(repository.cities.select(effect.name))
                true
            }

            is CityEffect.AddResult -> {
                results.getOrNull(effect.index)?.let { applyEdit(repository.cities.add(it)) }
                true
            }

            is CityEffect.Remove -> {
                applyEdit(repository.cities.remove(effect.name))
                true
            }

            CityEffect.Reset -> {
                applyEdit(repository.cities.reset())
                true
            }

            is CityEffect.Search -> {
                results = CityCatalog.search(effect.text, exclude = cityState.cities)
                true
            }

            // Leaving the section: let the system handle Back, and let Tab move focus on.
            CityEffect.Exit, is CityEffect.FocusLeaves -> false

            CityEffect.None -> true
        }

    fun onKey(event: AndroidKeyEvent): Boolean {
        if (event.action != AndroidKeyEvent.ACTION_DOWN) return false
        val raw = RawKey(event.keyCode, event.unicodeChar, event.metaState, event.repeatCount > 0)
        val reduction = model.reduce(screen, KeyMapper.map(raw, editableFocused = fieldFocused))
        screen = reduction.state
        if (!reduction.consumed) return false
        return runEffect(reduction.effect)
    }

    // Not on first composition: grabbing focus at launch would scroll the forecast away. The first arrow key
    // reaches the section through normal focus traversal.
    var focusPasses by remember { mutableStateOf(0) }
    LaunchedEffect(screen.screen) {
        focusPasses += 1
        if (focusPasses == 1) return@LaunchedEffect
        runCatching {
            if (screen.screen == CityScreen.Add) fieldFocus.requestFocus() else listFocus.requestFocus()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(listFocus)
                .focusable()
                .onPreviewKeyEvent { onKey(it.nativeKeyEvent) },
    ) {
        Text(
            text = "cities",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        when (screen.screen) {
            CityScreen.List -> {
                CityList(
                    cities = cityState.cities,
                    selected = cityState.selected,
                    focusedId = if (screen.focusVisible) model.focusedId(screen) else null,
                    onSelect = { runEffect(CityEffect.Select(it)) },
                    onRemove = { name ->
                        screen =
                            model.onTouch(screen).copy(
                                screen = CityScreen.ConfirmRemove,
                                pendingRemove = name,
                                dialog = DialogButton.Cancel,
                            )
                    },
                    onAdd = { screen = model.onTouch(screen).copy(screen = CityScreen.Add, query = "") },
                    onReset = {
                        screen = model.onTouch(screen).copy(screen = CityScreen.ConfirmReset, dialog = DialogButton.Cancel)
                    },
                )
            }

            CityScreen.Add -> {
                OutlinedTextField(
                    value = screen.query,
                    onValueChange = { text ->
                        val r = model.onQueryChanged(screen, text)
                        screen = r.state
                        runEffect(r.effect)
                    },
                    singleLine = true,
                    label = { Text("city, or Name, lat, lon, Area/Zone") },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .focusRequester(fieldFocus)
                            .onFocusChanged { fieldFocused = it.isFocused },
                )
                if (results.isEmpty() && screen.query.isNotBlank()) {
                    Text(
                        text = "No match. Enter Name, latitude, longitude, Area/Zone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                results.forEachIndexed { index, city ->
                    CityRow(
                        title = city.name,
                        detail = city.timezone,
                        trailing = "add",
                        focused = screen.focusVisible && screen.resultFocus == index,
                        onClick = { runEffect(CityEffect.AddResult(index)) },
                    )
                }
                CityRow(
                    title = "cancel",
                    detail = null,
                    trailing = "",
                    focused = false,
                    onClick = { screen = model.afterChange(screen, cityState.selected.name) },
                )
            }

            CityScreen.ConfirmRemove, CityScreen.ConfirmReset -> {
                ConfirmBlock(
                    question =
                        if (screen.screen == CityScreen.ConfirmRemove) {
                            "Remove ${screen.pendingRemove}?"
                        } else {
                            "Reset to the built-in cities? Added cities are removed."
                        },
                    focused = if (screen.focusVisible) screen.dialog else null,
                    onCancel = { screen = screen.copy(screen = CityScreen.List, pendingRemove = null) },
                    onConfirm = {
                        val effect = screen.pendingRemove?.let { CityEffect.Remove(it) } ?: CityEffect.Reset
                        screen = screen.copy(screen = CityScreen.List)
                        runEffect(effect)
                    },
                )
            }
        }
    }
}

@Composable
private fun CityList(
    cities: List<City>,
    selected: City,
    focusedId: String?,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    onReset: () -> Unit,
) {
    cities.forEach { city ->
        val isSelected = city == selected
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .focusOutline(focusedId == CityIds.row(city.name))
                        .clickable { onSelect(city.name) }
                        .padding(vertical = 12.dp),
            ) {
                Text(
                    text = city.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (isSelected) "current · manual city" else city.timezone,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "remove",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .focusOutline(focusedId == CityIds.remove(city.name))
                        .clickable { onRemove(city.name) }
                        .padding(12.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    CityRow("add city", "type a name, or press any letter", "+", focusedId == CityIds.ADD, onAdd)
    CityRow("reset to built-in cities", null, "", focusedId == CityIds.RESET, onReset)
}

@Composable
private fun CityRow(
    title: String,
    detail: String?,
    trailing: String,
    focused: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusOutline(focused)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = trailing,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ConfirmBlock(
    question: String,
    focused: DialogButton?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = question,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "cancel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier =
                    Modifier
                        .focusOutline(focused == DialogButton.Cancel)
                        .clickable(onClick = onCancel)
                        .padding(12.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = "confirm",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .focusOutline(focused == DialogButton.Confirm)
                        .clickable(onClick = onConfirm)
                        .padding(12.dp),
            )
        }
    }
}

@Composable
private fun Modifier.focusOutline(focused: Boolean): Modifier =
    if (focused) border(2.dp, MaterialTheme.colorScheme.primary) else this
