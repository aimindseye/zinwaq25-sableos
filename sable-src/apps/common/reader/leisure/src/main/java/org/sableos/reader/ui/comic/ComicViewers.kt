package org.sableos.reader.ui.comic

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.sableos.reader.comic.ComicReadingMode

/** Paged viewer: left-to-right, right-to-left (reversed layout) or top-to-bottom. One page at a time. */
@Composable
internal fun PagerViewer(state: ComicReaderUiState, viewModel: ComicReaderViewModel) {
    val mode = state.settings.mode
    val pager = rememberPagerState(initialPage = state.pageIndex.coerceIn(0, state.pageCount - 1)) { state.pageCount }
    PagerSync(pager, viewModel)
    val page: @Composable (Int) -> Unit = { index ->
        ComicPage(index, state, viewModel, strip = false, modifier = Modifier.fillMaxSize())
    }
    if (mode == ComicReadingMode.VERTICAL_PAGER) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), pageContent = { page(it) })
    } else {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = mode.isRightToLeft,
            pageContent = { page(it) },
        )
    }
}

@Composable
private fun PagerSync(pager: PagerState, viewModel: ComicReaderViewModel) {
    LaunchedEffect(pager, viewModel) {
        viewModel.scrollRequests.collect { index -> if (pager.currentPage != index) pager.scrollToPage(index) }
    }
    LaunchedEffect(pager, viewModel) {
        snapshotFlow { pager.settledPage }.collect { viewModel.onAction(ComicUiAction.PageSettled(it)) }
    }
}

/** Continuous viewer: a strip of pages fitting the width; webtoon is the same strip with no gap between pages. */
@Composable
internal fun StripViewer(state: ComicReaderUiState, viewModel: ComicReaderViewModel, scroll: ScrollTarget) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.pageIndex.coerceIn(0, state.pageCount - 1))
    LaunchedEffect(list, viewModel) {
        viewModel.scrollRequests.collect { index -> list.scrollToItem(index) }
    }
    LaunchedEffect(list, viewModel) {
        snapshotFlow { list.firstVisibleItemIndex }.collect { viewModel.onAction(ComicUiAction.PageSettled(it)) }
    }
    DisposableEffect(list, scroll) {
        scroll.scrollBy = { delta -> list.scrollBy(delta) }
        onDispose { scroll.scrollBy = null }
    }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(state.settings.pageGapDp.dp),
    ) {
        items(count = state.pageCount, key = { it }) { index ->
            ComicPage(index, state, viewModel, strip = true)
        }
    }
}
