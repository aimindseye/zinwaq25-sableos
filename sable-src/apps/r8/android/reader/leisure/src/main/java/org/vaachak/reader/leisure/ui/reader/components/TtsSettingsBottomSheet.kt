/*
 *  Copyright (c) 2026 Piyush Daiya
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 */

package org.vaachak.reader.leisure.ui.reader.components


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.vaachak.reader.core.domain.model.TtsSettings
import org.vaachak.reader.leisure.ui.reader.ReaderViewModel
import org.vaachak.reader.leisure.ui.settings.TtsOptionChip
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.tid
import java.util.Locale


data class ParsedVoice(
    val voice: AndroidTtsEngine.Voice,
    val countryName: String,
    val label: String
)

fun parseLocalVoice(voice: AndroidTtsEngine.Voice, index: Int): ParsedVoice? {
    val voiceId = voice.id.value
    // Reject cloud/network voices
    if (!voiceId.endsWith("local", ignoreCase = true)) return null

    return try {
        val parts = voiceId.split("-")
        val locale = Locale.Builder()
            .setLanguage(parts[0])
            .setRegion(parts[1])
            .build()

        ParsedVoice(voice, locale.displayCountry, "Voice $index")
    } catch (e: Exception) {
        null
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    ttsSettings: TtsSettings,
    isEink: Boolean,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onStyleChange: (String) -> Unit,
    onAutoPageTurnChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onVoiceChange: (AndroidTtsEngine.Voice) -> Unit,
    viewModel: ReaderViewModel
) {
    val containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.surface
    val contentColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onSurface

    // Read from ViewModel RAM, not DataStore
    val currentVoiceId by viewModel.currentTtsVoiceId.collectAsStateWithLifecycle()
    val currentLang by viewModel.currentTtsLanguage.collectAsStateWithLifecycle()

    // 1. INVISIBLE SCRIM (Prevents E-ink hardware from dimming the screen)
    if (isVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }

    // 2. THE BOTTOM SHEET CONTENT
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }),
        exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight }),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier = Modifier
                    .tid(Tid.Screen.ttsSettings)
                    .fillMaxWidth()
                    .background(containerColor, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    // Block clicks from passing through the sheet to the scrim behind it
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(24.dp, bottom = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(20.dp) // Tighter spacing
            ) {

                // Custom Drag Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 4.dp)
                            .background(contentColor.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TTS Settings", style = MaterialTheme.typography.titleMedium, color = contentColor, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.tid(Tid.Tts.settingsClose)) {
                        Icon(Icons.Default.Close, contentDescription = "Close TTS Settings", tint = contentColor)
                    }
                }

                // --- ROW 1: SPEED & PITCH (Side-by-Side) ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CompactStepper(
                        title = "Speed",
                        value = ttsSettings.defaultSpeed,
                        onValueChange = onSpeedChange,
                        format = { "${"%.1f".format(it)}x" },
                        modifier = Modifier.weight(1f)
                    )
                    CompactStepper(
                        title = "Pitch",
                        value = ttsSettings.pitch,
                        onValueChange = onPitchChange,
                        format = { "${"%.1f".format(it)}x" },
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(color = contentColor.copy(alpha = 0.2f))

                // --- ROW 2: LANGUAGE & VOICE (Side-by-Side) ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {

                    // Language Dropdown
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Language", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f))
                        var langExpanded by remember { mutableStateOf(false) }
                        val languages = listOf("default" to "Default", "en" to "English", "hi" to "Hindi", "gu" to "Gujarati")
                        val currentLangLabel = languages.find { it.first == currentLang }?.second ?: "Default"

                        Box {
                            OutlinedButton(
                                onClick = { langExpanded = true },
                                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                                    contentDescription = "Language Selection: $currentLangLabel"
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(currentLangLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, color = contentColor)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = contentColor)
                            }
                            DropdownMenu(
                                expanded = langExpanded,
                                onDismissRequest = { langExpanded = false },
                                containerColor = containerColor
                            ) {
                                languages.forEach { (code, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, color = contentColor) },
                                        onClick = { onLanguageChange(code); langExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    // Grouped Voice Combo Dropdown
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Voice / Accent", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f))
                        var voiceExpanded by remember { mutableStateOf(false) }

                        val rawVoices = viewModel.getVoicesForLanguage(currentLang)
                        val groupedVoices = remember(currentLang, rawVoices) {
                            var index = 1
                            rawVoices.mapNotNull { parseLocalVoice(it, index++) }
                                .groupBy { it.countryName }
                        }

                        val activeVoiceLabel = groupedVoices.values.flatten()
                            .find { it.voice.id.value == currentVoiceId }?.label ?: "Default"

                        ExposedDropdownMenuBox(
                            expanded = voiceExpanded,
                            onExpandedChange = { voiceExpanded = !voiceExpanded }
                        ) {
                            OutlinedTextField(
                                value = activeVoiceLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Voice / Accent", color = contentColor) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                    focusedTextColor = contentColor,
                                    unfocusedTextColor = contentColor,
                                    focusedTrailingIconColor = contentColor,
                                    unfocusedTrailingIconColor = contentColor
                                ),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "Voice Selection: $activeVoiceLabel"
                                    }
                            )

                            ExposedDropdownMenu(
                                expanded = voiceExpanded,
                                onDismissRequest = { voiceExpanded = false },
                                modifier = Modifier.fillMaxHeight(0.5f),
                                containerColor = containerColor
                            ) {
                                groupedVoices.forEach { (country, parsedVoices) ->
                                    // Group Header (Not clickable)
                                    DropdownMenuItem(
                                        text = { Text(country, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                        onClick = { },
                                        enabled = false
                                    )
                                    // Group Items
                                    parsedVoices.forEach { parsed ->
                                        DropdownMenuItem(
                                            text = { Text("   ${parsed.label}", color = contentColor) }, // Indented
                                            onClick = { onVoiceChange(parsed.voice); voiceExpanded = false }
                                        )
                                    }
                                }
                                if (groupedVoices.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No offline voices", color = contentColor) },
                                        onClick = { voiceExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = contentColor.copy(alpha = 0.2f))

                // --- ROW 3: STYLE & AUTO-TURN (Side-by-Side) ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Highlight Style", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TtsOptionChip("Underline", ttsSettings.visualStyle == "underline", isEink) { onStyleChange("underline") }
                            TtsOptionChip("Block", ttsSettings.visualStyle == "highlight", isEink) { onStyleChange("highlight") }
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Auto-Turn Pages", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f))
                        Switch(
                            checked = ttsSettings.isAutoPageTurnEnabled,
                            onCheckedChange = onAutoPageTurnChange,
                            modifier = Modifier.semantics(mergeDescendants = true) {
                                contentDescription = "Auto-Turn Pages, ${if (ttsSettings.isAutoPageTurnEnabled) "On" else "Off"}"
                            }
                        )
                    }
                }
            }
        }
    }
}

// Compact Stepper Helper for Row 1
@Composable
fun CompactStepper(title: String, value: Float, onValueChange: (Float) -> Unit, format: (Float) -> String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onValueChange(value - 0.1f) }) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease $title")
            }
            Text(format(value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { onValueChange(value + 0.1f) }) {
                Icon(Icons.Default.Add, contentDescription = "Increase $title")
            }
        }
    }
}
