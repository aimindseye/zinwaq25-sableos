/*
 *
 *  *
 *  *  *  Copyright (c) 2026 Piyush Daiya
 *  *  *  *
 *  *  *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  *  *  * of this software and associated documentation files (the "Software"), to deal
 *  *  *  * in the Software without restriction, including without limitation the rights
 *  *  *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  *  *  * copies of the Software, and to permit persons to whom the Software is
 *  *  *  * furnished to do so, subject to the following conditions:
 *  *  *  *
 *  *  *  * The above copyright notice and this permission notice shall be included in all
 *  *  *  * copies or substantial portions of the Software.
 *  *  *  *
 *  *  *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  *  *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  *  *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  *  *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  *  *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  *  *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  *  *  * SOFTWARE.
 *  *
 *
 */


package org.vaachak.textreader

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// ---------------------------------------------------------------------------
// Voice Mapping Utility
// ---------------------------------------------------------------------------

object VoiceNameMapper {
    fun getFriendlyName(voice: Voice): String {
        val locale = voice.locale
        val language = locale.getDisplayLanguage(Locale.US)
        val country = locale.getDisplayCountry(Locale.US)

        // Fix: Strip the redundant suffixes before grabbing the ID
        val cleanName = voice.name.lowercase().removeSuffix("-local").removeSuffix("-network")
        val variantId = cleanName.split("-").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: "Default"

        val type = if (voice.isNetworkConnectionRequired) "Cloud" else "Local"

        return "$language ${if (country.isNotEmpty()) "($country)" else ""} - $variantId ($type)"
    }
}

// ---------------------------------------------------------------------------
// TTS Settings Dialog Composable
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsDialog(ttsEngine: TextToSpeech, currentLocale: Locale, onDismiss: () -> Unit) {
    val (rate, setRate) = remember { mutableFloatStateOf(1.0f) }
    val (pitch, setPitch) = remember { mutableFloatStateOf(1.0f) }
    val (voices, setVoices) = remember { mutableStateOf<List<Voice>>(emptyList()) }
    val (selected, setSelected) = remember { mutableStateOf<Voice?>(null) }
    val (exp, setExp) = remember { mutableStateOf(false) }

    LaunchedEffect(currentLocale) {
        try {
            val filtered = ttsEngine.voices?.filter { v ->
                if (v.locale.language == "en") v.locale.country in listOf("US", "GB")
                else v.locale.language == currentLocale.language
            }?.sortedBy { it.name } ?: emptyList()
            setVoices(filtered)
            setSelected(ttsEngine.voice)
        } catch (_: Exception) {}
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Audio Settings") },
        text = {
            Column {
                Text("Speed: ${"%.1f".format(rate)}x", style = MaterialTheme.typography.labelMedium)
                Slider(value = rate, onValueChange = setRate, onValueChangeFinished = { ttsEngine.setSpeechRate(rate) }, valueRange = 0.5f..2.0f)

                Text("Pitch: ${"%.1f".format(pitch)}x", style = MaterialTheme.typography.labelMedium)
                Slider(value = pitch, onValueChange = setPitch, onValueChangeFinished = { ttsEngine.setPitch(pitch) }, valueRange = 0.5f..2.0f)

                if (voices.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Voice Variant", style = MaterialTheme.typography.labelMedium)
                    Box {
                        OutlinedButton(onClick = { setExp(true) }, modifier = Modifier.fillMaxWidth()) {
                            Text(selected?.let { VoiceNameMapper.getFriendlyName(it) } ?: "Default", maxLines = 1, fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = exp, onDismissRequest = { setExp(false) }) {
                            voices.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text(VoiceNameMapper.getFriendlyName(v), fontSize = 12.sp) },
                                    onClick = {
                                        setSelected(v)
                                        ttsEngine.voice = v
                                        setExp(false)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
