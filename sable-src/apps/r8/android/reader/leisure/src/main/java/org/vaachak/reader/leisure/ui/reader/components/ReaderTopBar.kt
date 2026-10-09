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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.tid

@Composable
fun ReaderTopBar(
    bookTitle: String,
    isEink: Boolean,
    showRecap: Boolean,
    showDebugHighlightButton: Boolean,
    isBookmarked: Boolean,
    isTtsActive: Boolean,
    onBack: () -> Unit,
    onTocClick: () -> Unit,
    onSearchClick: () -> Unit,
    onHighlightsClick: () -> Unit,
    onBookmarksListClick: () -> Unit,
    onBookmarkToggleClick: () -> Unit,
    onRecapClick: () -> Unit,
    onTtsClick: () -> Unit,
    onAddHighlightClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.surface
    val contentColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onSurface
    val dividerColor = if (isEink) Color.Black else MaterialTheme.colorScheme.outlineVariant

    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.tid(Tid.Reader.back)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = contentColor)
                }

                Text(
                    text = bookTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isEink) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                IconButton(onClick = onTtsClick, modifier = Modifier.tid(Tid.Reader.TTS)) {
                    Icon(
                        imageVector = if (isTtsActive) Icons.Default.PauseCircle else Icons.Default.Headphones,
                        contentDescription = if (isTtsActive) "Pause Read Aloud" else "Start Read Aloud",
                        tint = if (isTtsActive && !isEink) MaterialTheme.colorScheme.primary else contentColor
                    )
                }

                IconButton(onClick = onBookmarkToggleClick, modifier = Modifier.tid(Tid.Reader.BOOKMARK)) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Remove Bookmark" else "Add Bookmark",
                        tint = if (isBookmarked && !isEink) MaterialTheme.colorScheme.primary else contentColor
                    )
                }

                IconButton(onClick = onTocClick, modifier = Modifier.tid(Tid.Reader.TOC)) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Table of Contents", tint = contentColor)
                }

                IconButton(onClick = onBookmarksListClick, modifier = Modifier.tid(Tid.Reader.bookmarksList)) {
                    Icon(Icons.Default.Bookmarks, contentDescription = "Bookmarks List", tint = contentColor)
                }

                IconButton(onClick = onHighlightsClick, modifier = Modifier.tid(Tid.Reader.highlights)) {
                    Icon(Icons.Default.Edit, contentDescription = "Highlights", tint = contentColor)
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.tid(Tid.Reader.overflow)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = contentColor)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = containerColor
                    ) {
                        DropdownMenuItem(
                            text = { Text("Search") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.tid(Tid.Reader.SEARCH),
                            onClick = { showMenu = false; onSearchClick() }
                        )
                        if (showRecap) {
                            DropdownMenuItem(
                                text = { Text("Recap") },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                modifier = Modifier.tid(Tid.Reader.menuRecap),
                                onClick = { showMenu = false; onRecapClick() }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            modifier = Modifier.tid(Tid.Reader.SETTINGS),
                            onClick = { showMenu = false; onSettingsClick() }
                        )
                    }
                }
            }
            HorizontalDivider(thickness = if (isEink) 1.dp else 0.5.dp, color = dividerColor)
        }
    }
}
