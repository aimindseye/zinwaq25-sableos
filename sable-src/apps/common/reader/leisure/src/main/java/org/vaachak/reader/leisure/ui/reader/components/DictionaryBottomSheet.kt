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

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.tid

private const val DICTIONARY_TEXT_SIZE_SP = 18f
private const val DICTIONARY_LINE_SPACING_EXTRA = 2f
private const val DICTIONARY_LINE_SPACING_MULTIPLIER = 1.2f

/**
 * Local dictionary result sheet. Sable Reader v2 has no remote AI surface; this sheet only ever
 * presents definitions from the on-device (embedded or StarDict) dictionaries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryBottomSheet(
    responseText: String?,
    isLoading: Boolean,
    isEink: Boolean = false,
    onDismiss: () -> Unit
) {
    val containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onSurface

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = containerColor,
        contentColor = contentColor,
        shape = if (isEink) MaterialTheme.shapes.extraSmall else BottomSheetDefaults.ExpandedShape,
        modifier = Modifier.tid(Tid.Screen.dictionarySheet)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            when {
                isLoading -> DictionaryLoading(isEink)
                !responseText.isNullOrBlank() -> DictionaryResult(responseText, contentColor)
                else -> Text(
                    text = "No definition found.",
                    color = if (isEink) Color.Gray else MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DictionaryLoading(isEink: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth()
    ) {
        CircularProgressIndicator(
            color = if (isEink) Color.Black else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Consulting Dictionary...",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isEink) Color.DarkGray else MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun DictionaryResult(htmlText: String, contentColor: Color) {
    val argb = contentColor.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            TextView(context).apply {
                textSize = DICTIONARY_TEXT_SIZE_SP
                movementMethod = LinkMovementMethod.getInstance()
                setLineSpacing(DICTIONARY_LINE_SPACING_EXTRA, DICTIONARY_LINE_SPACING_MULTIPLIER)
            }
        },
        update = { textView ->
            textView.text = HtmlCompat.fromHtml(htmlText, HtmlCompat.FROM_HTML_MODE_COMPACT)
            // Follow the active (SableOS Light/Dark or E-Ink) content color instead of a fixed black.
            textView.setTextColor(argb)
        }
    )
}
