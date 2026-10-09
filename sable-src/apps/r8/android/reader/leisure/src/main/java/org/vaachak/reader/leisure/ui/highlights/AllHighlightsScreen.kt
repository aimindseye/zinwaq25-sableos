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

package org.vaachak.reader.leisure.ui.highlights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import org.vaachak.reader.core.domain.model.HighlightEntity
import org.vaachak.reader.leisure.ui.reader.components.VaachakHeader
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.TidScreen
import org.vaachak.reader.leisure.ui.testability.tid
import org.vaachak.reader.leisure.ui.testability.tids

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AllHighlightsScreen(
    onBack: () -> Unit,
    onHighlightClick: (String, String) -> Unit,
    viewModel: AllHighlightsViewModel = hiltViewModel()
) {
    val groupedHighlights by viewModel.groupedHighlights.collectAsState()
    val tags by viewModel.availableTags.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val isEink by viewModel.isEinkEnabled.collectAsState()
    var isFilterExpanded by remember { mutableStateOf(false) }

    // Colors
    val containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.background
    val contentColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onBackground

    TidScreen(Tid.Screen.highlights) {
        Scaffold(
            topBar = {
                VaachakHeader(
                    title = "Highlights (${groupedHighlights.values.sumOf { it.size }})",
                    onBack = onBack,
                    isEink = isEink,
                    backButtonModifier = Modifier.tid(Tid.Highlights.BACK)
                )
            },
            containerColor = containerColor
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
            // --- 1. COMPACT FILTER BAR ---
            Surface(
                color = if (isEink) Color.White else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .tid(Tid.Highlights.FILTER_BAR)
                    .fillMaxWidth()
                    .clickable { isFilterExpanded = !isFilterExpanded }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Filter Highlights: Currently showing $selectedTag. Tap to change."
                    }
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp), // Reduced Padding
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(14.dp), tint = contentColor)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Filter: $selectedTag",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        }
                        Icon(
                            imageVector = if (isFilterExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isFilterExpanded) "Collapse Filter" else "Expand Filter",
                            tint = contentColor,
                            modifier = Modifier
                                .tid(Tid.Highlights.FILTER_TOGGLE)
                                .size(16.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = isFilterExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            tags.forEachIndexed { index, tagData ->
                                val chipModifier = if (index == 0) {
                                    Modifier.tids(
                                        Tid.Highlights.filterChip(tagData.name),
                                        Tid.Highlights.FILTER_CHIP_FIRST
                                    )
                                } else {
                                    Modifier.tid(Tid.Highlights.filterChip(tagData.name))
                                }
                                FilterChip(
                                    selected = selectedTag == tagData.name,
                                    onClick = {
                                        viewModel.updateFilter(tagData.name)
                                        isFilterExpanded = false
                                    },
                                    label = { Text("${tagData.name} (${tagData.count})", fontSize = 11.sp) },
                                    modifier = chipModifier.height(28.dp), // Slimmer chips
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (isEink) Color.Black else MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = if (isEink) Color.White else MaterialTheme.colorScheme.onPrimary,
                                        containerColor = Color.Transparent,
                                        labelColor = contentColor
                                    ),
                                    border = if (isEink) BorderStroke(1.dp, Color.Black) else FilterChipDefaults.filterChipBorder(enabled = true, selected = selectedTag == tagData.name)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = if (isEink) Color.Black else MaterialTheme.colorScheme.outlineVariant)
                }
            }

            // --- 2. LIST AREA ---
            if (groupedHighlights.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No highlights found.", color = if(isEink) Color.Black else Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    var firstHighlightAssigned = false
                    groupedHighlights.forEach { (bookTitle, highlights) ->
                        stickyHeader {
                            Surface(
                                color = if (isEink) Color.White else MaterialTheme.colorScheme.surfaceVariant,
                                border = if(isEink) BorderStroke(1.dp, Color.Black) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp), tint = contentColor)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = bookTitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                        itemsIndexed(highlights, key = { _, highlight -> highlight.id }) { index, highlight ->
                            val isFirstVisibleItem = !firstHighlightAssigned
                            if (isFirstVisibleItem) {
                                firstHighlightAssigned = true
                            }
                            CompactHighlightItem(
                                highlight = highlight,
                                isEink = isEink,
                                isFirstItem = isFirstVisibleItem,
                                onClick = { onHighlightClick(highlight.bookHashId, highlight.locatorJson) },
                                onDelete = { viewModel.deleteHighlight(highlight.id) }
                            )
                            if (!isEink) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.2f))
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
fun CompactHighlightItem(
    highlight: HighlightEntity,
    isEink: Boolean,
    isFirstItem: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val modifier = if (isEink) Modifier.fillMaxWidth().border(0.5.dp, Color.Black) else Modifier.fillMaxWidth()

    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = if (isFirstItem) {
            modifier.tids(Tid.Highlights.item(highlight.id), Tid.Highlights.FIRST)
        } else {
            modifier.tid(Tid.Highlights.item(highlight.id))
        }.semantics(mergeDescendants = true) {
            contentDescription = "Highlight: ${highlight.text}, Tag: ${highlight.tag ?: "None"}"
        }
    ) {
        Row(
            // OPTIMIZATION: Tighter padding (12dp) allows more content
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {

                // --- THE FIX ---
                // Extract to a local, non-nullable variable immediately
                val safeTag = highlight.tag ?: ""

                // Now we safely check that it actually has text AND isn't "General"
                if (safeTag.isNotEmpty() && safeTag != "General") {
                    Text(
                        text = "#${safeTag.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp, // Smaller tag text
                        color = if (isEink) Color.Black else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = highlight.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp, // Slightly more compact font
                    lineHeight = 20.sp,
                    //maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isEink) Color.Black else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = if (isFirstItem) {
                    Modifier.tids(Tid.Highlights.delete(highlight.id), Tid.Highlights.DELETE_FIRST)
                } else {
                    Modifier.tid(Tid.Highlights.delete(highlight.id))
                }.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Highlight",
                    modifier = Modifier.size(16.dp), // Smaller Icon
                    tint = if (isEink) Color.Black else MaterialTheme.colorScheme.error.copy(alpha=0.6f)
                )
            }
        }
    }
}
