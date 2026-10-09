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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.vaachak.reader.core.domain.model.UserProfile
import org.vaachak.reader.leisure.R
import java.util.Locale

// --- SHARED FONT DEFINITIONS ---
val OpenDyslexicFamily = androidx.compose.ui.text.font.FontFamily(
    Font(R.font.open_dyslexic_regular, FontWeight.Normal),
    Font(R.font.open_dyslexic_bold, FontWeight.Bold)
)

val IAWriterFamily = androidx.compose.ui.text.font.FontFamily(
    Font(R.font.ia_writer_duospace_regular, FontWeight.Normal),
    Font(R.font.ia_writer_duospace_bold, FontWeight.Bold)
)

val AccessibleDfaFamily = androidx.compose.ui.text.font.FontFamily(
    Font(R.font.accessible_dfa_regular, FontWeight.Normal)
)

val MonospaceFamily = androidx.compose.ui.text.font.FontFamily(
    Font(R.font.monospace_regular, FontWeight.Normal),
    Font(R.font.monospace_bold, FontWeight.Bold)
)

// --- 1. The Container for Sections ---
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

// --- 2. The Standard Row ---
@Composable
fun SettingsTile(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (subtitle != null) "$title, $subtitle" else title
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

// --- 3. The User Profile Card ---
@Composable
fun UserProfileCard(
    profile: UserProfile,
    onLoginClick: () -> Unit,
    onManageClick: () -> Unit
) {
    val description = if (profile.isAuthenticated) {
        "Profile: ${profile.username ?: "User"}, Sync Active on ${profile.deviceName}"
    } else {
        "Sign in to Sync, Backup your library & progress"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { if (profile.isAuthenticated) onManageClick() else onLoginClick() }
            .semantics(mergeDescendants = true) {
                contentDescription = description
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (profile.isAuthenticated)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (profile.isAuthenticated) {
                    Text(
                        text = profile.username ?: "User",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Sync Active • ${profile.deviceName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Sign in to Sync",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Backup your library & progress",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- Preview Components ---

@Composable
fun CustomThemePreviewCard(
    themeStr: String,
    fontName: String,
    fontSize: Double,
    lineHeight: Double
) {
    val theme = when(themeStr) { "dark" -> Theme.DARK; "sepia" -> Theme.SEPIA; else -> Theme.LIGHT }
    val (bg, fg) = when(theme) {
        Theme.DARK -> Color(0xFF121212) to Color(0xFFE0E0E0)
        Theme.SEPIA -> Color(0xFFF5E6D3) to Color(0xFF5F4B32)
        else -> Color.White to Color.Black
    }

    val name = fontName.lowercase()
    val previewFont = when {
        name.contains("writer") -> IAWriterFamily
        name.contains("dyslexic") -> OpenDyslexicFamily
        name.contains("accessible") -> AccessibleDfaFamily
        name.contains("mono") -> MonospaceFamily
        name.contains("serif") && !name.contains("sans") -> androidx.compose.ui.text.font.FontFamily.Serif
        name.contains("cursive") -> androidx.compose.ui.text.font.FontFamily.Cursive
        name.contains("sans") -> androidx.compose.ui.text.font.FontFamily.SansSerif
        else -> androidx.compose.ui.text.font.FontFamily.Default
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "The quick brown fox jumps over the lazy dog.\nThis is a second line to demonstrate height.\nAnd a third line for good measure.",
                color = fg,
                fontSize = (16 * fontSize).sp,
                lineHeight = (16 * fontSize * lineHeight).sp,
                fontFamily = previewFont,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
fun FontFamilyGrid(
    currentFamily: FontFamily?,
    enabled: Boolean,
    onSelect: (FontFamily?) -> Unit
) {
    val fonts = listOf(
        "Original" to null,
        "Sans" to FontFamily.SANS_SERIF,
        "Serif" to FontFamily.SERIF,
        "Mono" to FontFamily.MONOSPACE,
        "Cursive" to FontFamily.CURSIVE,
        "Dyslexic" to FontFamily.OPEN_DYSLEXIC,
        "Accessible" to FontFamily.ACCESSIBLE_DFA,
        "Writer" to FontFamily.IA_WRITER_DUOSPACE
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fonts.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, family) ->
                    val isSelected = currentFamily?.name == family?.name
                    val bgColor = if (isSelected && enabled) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    val borderColor = when {
                        !enabled -> MaterialTheme.colorScheme.outline.copy(alpha=0.2f)
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    val textColor = when {
                        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha=0.38f)
                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .border(if(isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
                            .clickable(enabled = enabled) { onSelect(family) }
                            .semantics { contentDescription = "Font Family: $name" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            fontSize = 11.sp,
                            fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = textColor,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LabelledSlider(
    label: String,
    value: Float?, // Nullable for Auto
    defaultVisualValue: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    activeColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit
) {
    val isOverridden = (value != null)

    val currentColor = if (isOverridden && enabled) activeColor else Color.Gray.copy(alpha = 0.6f)
    val labelText = if (isOverridden) String.format(Locale.US, "%.1f%s", value, unit) else "Auto"
    val labelFontWeight = if (isOverridden) FontWeight.Bold else FontWeight.Normal

    Column(modifier = modifier.semantics(mergeDescendants = true) {
        contentDescription = "$label, $labelText"
    }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, color = if(enabled) Color.Unspecified else Color.Gray)
            Text(labelText, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, fontWeight = labelFontWeight, color = currentColor)
        }
        Slider(
            value = value ?: defaultVisualValue,
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.height(16.dp),
            colors = SliderDefaults.colors(
                thumbColor = currentColor,
                activeTrackColor = currentColor,
                inactiveTrackColor = currentColor.copy(alpha = 0.24f),
                disabledThumbColor = Color.Gray,
                disabledActiveTrackColor = Color.Gray.copy(alpha=0.3f)
            )
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun ThemeOption(
    name: String,
    bg: Color,
    fg: Color,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(enabled = enabled) { onClick() }
            .semantics(mergeDescendants = true) {
                contentDescription = "Theme: $name, ${if (selected) "Selected" else "Not Selected"}"
            }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (enabled) bg else Color.Gray.copy(alpha=0.1f))
                .border(
                    if (selected && enabled) 2.dp else 1.dp,
                    if (enabled) (if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) else Color.Transparent,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Aa",
                color = if (enabled) fg else Color.Gray,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = if(enabled) MaterialTheme.colorScheme.onSurface else Color.Gray,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun CompactSettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    primaryColor: Color,
    isEink: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isEink) Color.Black else Color.Gray.copy(alpha = 0.3f)
    val bgColor = if (checked) primaryColor.copy(alpha = 0.05f) else Color.Transparent

    Column(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable { onCheckedChange(!checked) }
            .padding(12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $subtitle, ${if (checked) "On" else "Off"}"
            },
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.scale(0.7f).height(24.dp),
                colors = SwitchDefaults.colors(checkedTrackColor = primaryColor)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            fontSize = 11.sp,
            lineHeight = 12.sp
        )
    }
}

@Composable
fun AlignmentOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    activeColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(60.dp).clickable(enabled = enabled) { onClick() }
            .semantics(mergeDescendants = true) {
                contentDescription = "Alignment: $label, ${if (selected) "Selected" else "Not Selected"}"
            }
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected && enabled) activeColor.copy(alpha=0.1f) else Color.Transparent)
                .border(
                    1.dp,
                    if(selected && enabled) activeColor else Color.Gray.copy(alpha = if(enabled) 0.5f else 0.2f),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                tint = if(!enabled) Color.Gray.copy(alpha=0.5f) else if(selected) activeColor else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = if(selected) FontWeight.Bold else FontWeight.Normal,
            color = if(enabled) Color.Unspecified else Color.Gray
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

@Composable
fun TtsFilterOption(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
fun TtsOptionChip(label: String, selected: Boolean, isEink: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = if (isEink) Color.Black else MaterialTheme.colorScheme.primary,
            selectedLabelColor = if (isEink) Color.White else MaterialTheme.colorScheme.onPrimary
        ),
        border = if (isEink && !selected) BorderStroke(1.dp, Color.Black) else null
    )
}
@Composable
fun SettingsToggleTile(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $subtitle, ${if (checked) "On" else "Off"}"
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun TtsStepperSetting(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueFormatter: (Float) -> String
) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: ${valueFormatter(value)}"
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedIconButton(onClick = { onValueChange(value - 0.1f) }) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
            }
            Text(text = valueFormatter(value), style = MaterialTheme.typography.headlineSmall)
            OutlinedIconButton(onClick = { onValueChange(value + 0.1f) }) {
                Icon(Icons.Default.Add, contentDescription = "Increase $label")
            }
        }
    }
}