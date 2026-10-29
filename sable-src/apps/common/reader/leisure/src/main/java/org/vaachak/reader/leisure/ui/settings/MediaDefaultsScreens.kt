package org.vaachak.reader.leisure.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.sableos.reader.comic.ComicFit
import org.sableos.reader.comic.ComicReadingMode
import org.sableos.reader.engine.audio.AudioPlaybackSettings
import org.sableos.reader.engine.audio.AudioTimeFormat
import org.sableos.reader.ui.comic.comicModeLabel
import org.vaachak.reader.leisure.ui.testability.tid

/** Starting reading mode and fit for comics that have no choice of their own yet. */
@Composable
fun ComicDefaultsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val defaults by viewModel.comicDefaults.collectAsStateWithLifecycle()
    DefaultsScaffold("Comics", "comic_defaults_back", onBack) {
        Text(
            "Used when you open a comic for the first time. Press A while reading to change one comic.",
            style = MaterialTheme.typography.bodyMedium,
        )
        DefaultsSection("READING MODE") {
            ComicReadingMode.entries.forEach { mode ->
                TtsFilterOption(comicModeLabel(mode), defaults.mode == mode) {
                    viewModel.setComicDefaults(defaults.copy(mode = mode))
                }
            }
        }
        HorizontalDivider()
        DefaultsSection("FIT (PAGED MODES)") {
            TtsFilterOption("Whole page", defaults.fit == ComicFit.FIT_PAGE) {
                viewModel.setComicDefaults(defaults.copy(fit = ComicFit.FIT_PAGE))
            }
            TtsFilterOption("Page width", defaults.fit == ComicFit.FIT_WIDTH) {
                viewModel.setComicDefaults(defaults.copy(fit = ComicFit.FIT_WIDTH))
            }
        }
    }
}

/** Starting speed and skip lengths for audiobooks that have no choice of their own yet. */
@Composable
fun AudiobookDefaultsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val defaults by viewModel.audioDefaults.collectAsStateWithLifecycle()
    DefaultsScaffold("Audiobooks", "audio_defaults_back", onBack) {
        Text(
            "Used when you start an audiobook for the first time. Press A while listening to change one audiobook " +
                "or set the sleep timer.",
            style = MaterialTheme.typography.bodyMedium,
        )
        DefaultsSection("SPEED") {
            AudioPlaybackSettings.SPEEDS.forEach { speed ->
                TtsFilterOption("$speed×", defaults.speed == speed) {
                    viewModel.setAudioDefaults(defaults.copy(speed = speed))
                }
            }
        }
        HorizontalDivider()
        DefaultsSection("SKIP BACK (LEFT)") {
            AudioPlaybackSettings.SKIP_BACK_CHOICES_MS.forEach { ms ->
                TtsFilterOption(AudioTimeFormat.interval(ms), defaults.skipBackMs == ms) {
                    viewModel.setAudioDefaults(defaults.copy(skipBackMs = ms))
                }
            }
        }
        HorizontalDivider()
        DefaultsSection("SKIP FORWARD (RIGHT)") {
            AudioPlaybackSettings.SKIP_FORWARD_CHOICES_MS.forEach { ms ->
                TtsFilterOption(AudioTimeFormat.interval(ms), defaults.skipForwardMs == ms) {
                    viewModel.setAudioDefaults(defaults.copy(skipForwardMs = ms))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultsScaffold(title: String, backTid: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.tid(backTid)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@Composable
private fun DefaultsSection(label: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}
