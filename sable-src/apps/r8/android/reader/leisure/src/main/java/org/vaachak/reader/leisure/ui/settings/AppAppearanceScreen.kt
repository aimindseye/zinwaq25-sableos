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

package org.vaachak.reader.leisure.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.vaachak.reader.core.domain.model.ThemeMode
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.TidScreen
import org.vaachak.reader.leisure.ui.testability.tid
import org.vaachak.reader.leisure.utils.EinkHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppAppearanceScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    TidScreen(Tid.Screen.appAppearance) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.tid("app_appearance_back")) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // 1. APP THEME
            Text("INTERFACE THEME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ThemeOption(
                    name = "Light",
                    bg = Color(0xFFF0F0F0),
                    fg = Color.Black,
                    selected = uiState.themeMode == ThemeMode.LIGHT,
                    enabled = true,
                    modifier = Modifier.tid("app_appearance_theme_light")
                ) {
                    viewModel.setAppTheme(ThemeMode.LIGHT)
                }
                ThemeOption(
                    name = "Dark",
                    bg = Color(0xFF1E1E1E),
                    fg = Color.White,
                    selected = uiState.themeMode == ThemeMode.DARK,
                    enabled = true,
                    modifier = Modifier.tid("app_appearance_theme_dark")
                ) {
                    viewModel.setAppTheme(ThemeMode.DARK)
                }
                ThemeOption(
                    name = "E-Ink",
                    bg = Color.White,
                    fg = Color.Black,
                    selected = uiState.themeMode == ThemeMode.E_INK,
                    enabled = true,
                    modifier = Modifier.tid("app_appearance_theme_eink")
                ) {
                    viewModel.setAppTheme(ThemeMode.E_INK)
                }
            }

            // 2. SHARPNESS (Conditional)
            if (uiState.themeMode == ThemeMode.E_INK) {
                HorizontalDivider()

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Contrast, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("E-Ink Sharpness", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Increase contrast to make borders and icons clearer on E-Ink screens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Slider(
                        value = uiState.einkContrast,
                        onValueChange = { viewModel.setEinkContrast(it) },
                        valueRange = 0f..1f,
                        steps = 10,
                        modifier = Modifier.tid("app_appearance_contrast_slider"),
                        // [NEW] Trigger Refresh on Release
                        onValueChangeFinished = {
                            // 1. Log or logic in ViewModel
                            viewModel.onContrastChangedFinished()

                            // 2. Trigger Hardware Refresh directly
                            EinkHelper.requestFullRefresh(context)
                        }
                    )
                }
            }
        }
    }
    }
}
