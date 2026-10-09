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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.vaachak.reader.core.domain.model.HighlightEntity
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.tid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookHighlightsOverlay(
    highlights: List<HighlightEntity>,
    onHighlightClick: (HighlightEntity) -> Unit,
    onDismiss: () -> Unit,
    isEink: Boolean
) {
    val containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.background
    val contentColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onBackground

    Scaffold(
        modifier = Modifier.tid(Tid.Screen.bookHighlights),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Highlights & Notes",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss, modifier = Modifier.tid(Tid.BookHighlights.close)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Highlights"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                    titleContentColor = contentColor,
                    navigationIconContentColor = contentColor
                )
            )
        },
        containerColor = containerColor
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (highlights.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = "No highlights", tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No highlights yet.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(highlights) { index, highlight ->
                        HighlightItem(
                            highlight = highlight,
                            isEink = isEink,
                            isFirstVisibleItem = index == 0,
                            onClick = onHighlightClick
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = if(isEink) Color.LightGray else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HighlightItem(
    highlight: HighlightEntity,
    isEink: Boolean,
    isFirstVisibleItem: Boolean,
    onClick: (HighlightEntity) -> Unit
) {
    val textColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onSurface

    // Tag Color Logic
    val tagBgColor = if (isEink) Color.Black else MaterialTheme.colorScheme.secondaryContainer
    val tagTextColor = if (isEink) Color.White else MaterialTheme.colorScheme.onSecondaryContainer

    // THE FIX: Lock the cross-module property into a local, immutable variable
    val safeTag = highlight.tag

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isFirstVisibleItem) Modifier.tid(Tid.BookHighlights.first) else Modifier)
            .clickable { onClick(highlight) }
            .semantics(mergeDescendants = true) {
                contentDescription = "Highlight: ${highlight.text}, Tag: ${safeTag ?: "None"}"
            }
            .padding(16.dp)
    ) {
        // 1. Tag Label (if exists) - Now using the local safeTag
        if (!safeTag.isNullOrEmpty()) {
            Surface(
                color = tagBgColor,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = safeTag.uppercase(), // Compiler is happy now!
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = tagTextColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // 2. Highlighted Text
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(Color(highlight.color).copy(alpha = if(isEink) 0.5f else 1f))
            )
            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = highlight.text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
