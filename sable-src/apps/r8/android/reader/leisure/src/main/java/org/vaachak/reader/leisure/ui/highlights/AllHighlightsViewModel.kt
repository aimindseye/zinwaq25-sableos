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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.local.HighlightDao
import org.vaachak.reader.core.data.repository.SettingsRepository
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.HighlightEntity
import javax.inject.Inject

@HiltViewModel
class AllHighlightsViewModel @Inject constructor(
    private val highlightDao: HighlightDao,
    private val bookDao: BookDao,
    private val settingsRepo: SettingsRepository,
    private val vaultRepository: VaultRepository // <-- INJECT VAULT
) : ViewModel() {

    private val _selectedTag = MutableStateFlow("All")
    val selectedTag: StateFlow<String> = _selectedTag.asStateFlow()

    val isEinkEnabled: StateFlow<Boolean> = settingsRepo.isEinkEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // --- MULTI-TENANT DATA FLOWS ---
    // Listen to the active vault and pull the correct data for that user

    private val activeHighlightsFlow = vaultRepository.activeVaultId
        .flatMapLatest { profileId -> highlightDao.getAllHighlights(profileId) }

    private val activeBooksFlow = vaultRepository.activeVaultId
        .flatMapLatest { profileId -> bookDao.getAllBooks(profileId) }

    private val activeTagsFlow = vaultRepository.activeVaultId
        .flatMapLatest { profileId -> highlightDao.getAllUniqueTags(profileId) }

    // --- 2. Group Highlights by Book Title ---
    val groupedHighlights: StateFlow<Map<String, List<HighlightEntity>>> =
        combine(
            activeHighlightsFlow,
            activeBooksFlow,
            _selectedTag
        ) { highlights, books, tag ->
            val titleMap = books.associate { it.bookHash to it.title }

            val filteredHighlights = if (tag == "All") {
                highlights
            } else {
                highlights.filter { it.tag == tag }
            }

            filteredHighlights.groupBy { entity ->
                titleMap[entity.bookHashId] ?: "Unknown Book"
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    // 3. Chip Data
    data class TagWithCount(val name: String, val count: Int)

    val availableTags: StateFlow<List<TagWithCount>> = combine(
        activeHighlightsFlow,
        activeTagsFlow
    ) { highlights, uniqueTags ->
        val allChip = TagWithCount("All", highlights.size)
        // Filter out null tags to prevent crashes
        val specificChips = uniqueTags.filterNotNull().map { tag ->
            TagWithCount(tag, highlights.count { it.tag == tag })
        }
        listOf(allChip) + specificChips
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(TagWithCount("All", 0)))


    fun updateFilter(tag: String) {
        _selectedTag.value = tag
    }

    fun deleteHighlight(id: String) {
        viewModelScope.launch {
            highlightDao.deleteHighlightById(id)
        }
    }
}