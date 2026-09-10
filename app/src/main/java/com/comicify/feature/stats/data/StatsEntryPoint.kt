package com.comicify.feature.stats.data

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StatsEntryPoint {
    fun readingStatsRepository(): ReadingStatsRepository
}
