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

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

// ---------------------------------------------------------------------------
// Shared Data Models
// ---------------------------------------------------------------------------

data class Lang(val name: String, val code: String, val ttsTag: String)

// ---------------------------------------------------------------------------
// Shared UI Components
// ---------------------------------------------------------------------------

@Composable
fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
            Icon(icon, contentDescription = label)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun ProfessionalTranslationRow(currentText: String, onResult: (String, String) -> Unit) {
    val context = LocalContext.current

    // Kept only languages natively supported by ML Kit's local Latin/Devanagari OCR models
    val languages = listOf(
        Lang("English", TranslateLanguage.ENGLISH, "en-US"),
        Lang("Spanish", TranslateLanguage.SPANISH, "es-ES"),
        Lang("Hindi", TranslateLanguage.HINDI, "hi-IN"),
        Lang("Marathi", TranslateLanguage.MARATHI, "mr-IN")
    )

    var src by remember { mutableStateOf(languages[0]) }
    var target by remember { mutableStateOf(languages[2]) } // Defaults to Hindi
    var srcExp by remember { mutableStateOf(false) }
    var targetExp by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box {
                TextButton(onClick = { srcExp = true }) {
                    Text(src.name)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = srcExp, onDismissRequest = { srcExp = false }) {
                    languages.filter { it != target }.forEach {
                        DropdownMenuItem(text = { Text(it.name) }, onClick = { src = it; srcExp = false })
                    }
                }
            }

            IconButton(onClick = { val t = src; src = target; target = t }) {
                Icon(Icons.Default.SwapHoriz, null, tint = MaterialTheme.colorScheme.outline)
            }

            Box {
                TextButton(onClick = { targetExp = true }) {
                    Text(target.name)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = targetExp, onDismissRequest = { targetExp = false }) {
                    languages.filter { it != src }.forEach {
                        DropdownMenuItem(text = { Text(it.name) }, onClick = { target = it; targetExp = false })
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                if (currentText.isBlank()) return@OutlinedButton
                Toast.makeText(context, "Translating...", Toast.LENGTH_SHORT).show()
                val opts = TranslatorOptions.Builder().setSourceLanguage(src.code).setTargetLanguage(target.code).build()
                val client = Translation.getClient(opts)
                client.downloadModelIfNeeded(DownloadConditions.Builder().build()).addOnSuccessListener {
                    client.translate(currentText).addOnSuccessListener {
                        onResult(it, target.ttsTag)
                        client.close()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(Icons.Default.AutoAwesome, null)
            Spacer(Modifier.width(8.dp))
            Text("Translate")
        }
    }
}