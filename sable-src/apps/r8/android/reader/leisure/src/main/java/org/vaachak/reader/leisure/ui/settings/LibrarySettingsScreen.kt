/*
 * Copyright (c) 2026 Piyush Daiya
 * ... (License Header)
 */

package org.vaachak.reader.leisure.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.vaachak.reader.core.domain.model.CoverAspectRatio
import org.vaachak.reader.core.domain.model.DitheringMode
import org.vaachak.reader.leisure.ui.reader.components.VaachakHeader
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.TidScreen
import org.vaachak.reader.leisure.ui.testability.tid

@Composable
fun LibrarySettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val prefs = state.bookshelfPreferences

    val containerColor = if (state.isOfflineMode) Color.White else MaterialTheme.colorScheme.background
    val contentColor = if (state.isOfflineMode) Color.Black else MaterialTheme.colorScheme.onBackground

    TidScreen(Tid.Screen.librarySettings) {
    Scaffold(
        topBar = {
            VaachakHeader(
                title = "Library Settings",
                onBack = { navController.popBackStack() },
                showBackButton = true,
                isEink = state.isOfflineMode,
                backButtonModifier = Modifier.tid("library_settings_back")
            )
        },
        containerColor = containerColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // --- SECTION 1: COVER STYLE (ASPECT RATIO) ---
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cover style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Style 1: Original
                    CoverStyleCard(
                        modifier = Modifier.weight(1f).tid("library_settings_cover_original"),
                        title = "Original ratio",
                        description = "Display original cover aspect ratio from metadata",
                        isSelected = prefs.coverAspectRatio == CoverAspectRatio.ORIGINAL,
                        onClick = { viewModel.setCoverAspectRatio(CoverAspectRatio.ORIGINAL) },
                        contentColor = contentColor,
                        isEink = state.isOfflineMode
                    )

                    // Style 2: Uniform (NeoReader default)
                    CoverStyleCard(
                        modifier = Modifier.weight(1f).tid("library_settings_cover_uniform"),
                        title = "Uniform ratio",
                        description = "Uniform cover aspect ratio with image auto-fit (width & height)",
                        isSelected = prefs.coverAspectRatio == CoverAspectRatio.UNIFORM,
                        onClick = { viewModel.setCoverAspectRatio(CoverAspectRatio.UNIFORM) },
                        contentColor = contentColor,
                        isEink = state.isOfflineMode
                    )
                }
            }

            // --- SECTION 2: COVER ELEMENTS (TOGGLES) ---
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cover Element", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)

                // Pill Container for Toggles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (state.isOfflineMode) Color.LightGray.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CoverElementToggle("Format", prefs.showFormatBadge) {
                        viewModel.toggleCoverElement(prefs, format = it)
                    }
                    CoverElementToggle("Favorite", prefs.showFavoriteIcon) {
                        viewModel.toggleCoverElement(prefs, favorite = it)
                    }
                    CoverElementToggle("Progress", prefs.showProgressBadge) {
                        viewModel.toggleCoverElement(prefs, progress = it)
                    }
                    CoverElementToggle("Sync status", prefs.showSyncStatus) {
                        viewModel.toggleCoverElement(prefs, sync = it)
                    }
                }
            }

            HorizontalDivider(color = contentColor.copy(alpha = 0.1f))

            // --- SECTION 3: BOOKSHELF ORGANIZATION ---
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Bookshelf Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Smart Stacks, ${if (prefs.groupBySeries) "On" else "Off"}"
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Smart Stacks", style = MaterialTheme.typography.bodyLarge, color = contentColor)
                        Text("Automatically group books by author or series", style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = prefs.groupBySeries,
                        onCheckedChange = { viewModel.setGroupBySeries(it) },
                        modifier = Modifier.tid("library_settings_smart_stacks")
                    )
                }
            }

            HorizontalDivider(color = contentColor.copy(alpha = 0.1f))

            // --- SECTION 4: E-INK OPTIMIZATION ---
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("E-ink Optimization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cover Dithering Mode", style = MaterialTheme.typography.bodyLarge, color = contentColor)
                        Text("Applies Floyd-Steinberg algorithm to book covers to reduce ghosting.", style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.6f))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DitheringChip("Auto", prefs.ditheringMode == DitheringMode.AUTO, contentColor) { viewModel.setDitheringMode(DitheringMode.AUTO) }
                    DitheringChip("Always On", prefs.ditheringMode == DitheringMode.ALWAYS_ON, contentColor) { viewModel.setDitheringMode(DitheringMode.ALWAYS_ON) }
                    DitheringChip("Off", prefs.ditheringMode == DitheringMode.OFF, contentColor) { viewModel.setDitheringMode(DitheringMode.OFF) }
                }
            }
        }
    }
    }
}

// --- HELPER COMPONENTS ---

@Composable
fun CoverStyleCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    contentColor: Color,
    isEink: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.2f)
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isEink) 0.1f else 0.5f) else Color.Transparent

    Card(
        onClick = onClick,
        modifier = modifier
            .height(180.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, $description, ${if (isSelected) "Selected" else "Not Selected"}"
            },
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Placeholder graphic for the visual style
            Box(
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).background(contentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Book, contentDescription = null, tint = contentColor.copy(alpha = 0.3f), modifier = Modifier.size(40.dp))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(description, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = contentColor.copy(alpha = 0.7f), maxLines = 3)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = contentColor)
                }
            }
        }
    }
}

@Composable
fun CoverElementToggle(label: String, isChecked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable { onToggle(!isChecked) }
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label, ${if (isChecked) "Checked" else "Unchecked"}"
            }
    ) {
        Icon(
            imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (isChecked) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DitheringChip(label: String, isSelected: Boolean, contentColor: Color, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = contentColor.copy(alpha = 0.1f),
            selectedLabelColor = contentColor
        ),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Dithering Mode: $label, ${if (isSelected) "Selected" else "Not Selected"}"
        }
    )
}
