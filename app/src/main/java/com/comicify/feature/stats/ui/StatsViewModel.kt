package com.comicify.feature.stats.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicify.feature.library.data.LibraryRepository
import com.comicify.feature.library.domain.LibraryCatalog
import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.stats.data.ReadingStatsRepository
import com.comicify.feature.stats.domain.ReadingStats
import com.comicify.feature.stats.domain.readingStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

data class StatsUiState(
    val loading: Boolean = true,
    val stats: ReadingStats? = null,
    val finished: List<LibraryComic> = emptyList(),
    val resume: LibraryComic? = null,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    stats: ReadingStatsRepository,
    library: LibraryRepository,
) : ViewModel() {

    val state: StateFlow<StatsUiState> = combine(stats.readings, library.library) { readings, comics ->
        val summary = readingStats(readings, ZoneId.systemDefault(), System.currentTimeMillis())
        StatsUiState(
            loading = false,
            stats = summary,
            finished = summary.finishedThisMonth.mapNotNull { id -> comics.firstOrNull { it.id == id } },
            resume = LibraryCatalog.continueReading(comics).firstOrNull(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}
