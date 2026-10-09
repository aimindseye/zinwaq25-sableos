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

package org.vaachak.reader.leisure.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.local.HighlightDao
import org.vaachak.reader.core.data.repository.AiRepository
import org.vaachak.reader.core.data.repository.SettingsRepository
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.BookEntity
import org.vaachak.reader.core.domain.model.HighlightEntity
import javax.inject.Inject

@HiltViewModel
class SessionHistoryViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val highlightDao: HighlightDao,
    private val aiRepository: AiRepository,
    private val settingsRepository: SettingsRepository,
    private val vaultRepository: VaultRepository // <-- INJECT VAULT
) : ViewModel() {

    private val _recallMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val recallMap: StateFlow<Map<String, String>> = _recallMap.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _recentBooks = MutableStateFlow<List<BookEntity>>(emptyList())
    val recentBooks: StateFlow<List<BookEntity>> = _recentBooks.asStateFlow()

    fun triggerGlobalRecall() {
        viewModelScope.launch {
            _isLoading.value = true

            val profileId = vaultRepository.activeVaultId.first() // <-- PROFILE CHECK

            val books = bookDao.getAllBooks(profileId).first()
                .filter { it.progress > 0.0 && it.progress < 0.99 }
                .sortedByDescending { it.bookHash }
                .take(5)

            _recentBooks.value = books

            books.forEach { book ->
                launch {
                    try {
                        val highlights = highlightDao.getHighlightsForBook(book.bookHash, profileId)
                            .first()
                            .take(10)
                            .joinToString("\n") { it.text }

                        val summary = aiRepository.getRecallSummary(
                            bookTitle = book.title,
                            highlightsContext = highlights
                        )

                        if (settingsRepository.isAutoSaveRecapsEnabled.first()) {
                            saveRecapAsHighlight(book, summary, profileId) // <-- PASS PROFILE ID
                        }

                        _recallMap.update { it + (book.title to summary) }
                    } catch (e: Exception) {
                        // Log error or update UI state
                    }
                }
            }
            _isLoading.value = false
        }
    }

    private fun saveRecapAsHighlight(book: BookEntity, summary: String, profileId: String) {
        viewModelScope.launch {
            val recapHighlight = HighlightEntity(
                bookHashId  = book.bookHash,
                profileId = profileId, // <-- MULTI-TENANT FIX
                locatorJson = book.lastLocationJson ?: "",
                text = summary,
                color = -0x333334, // Gray for E-ink
                tag = "Recaps",
                created = System.currentTimeMillis()
            )
            highlightDao.insertHighlight(recapHighlight)
        }
    }
}