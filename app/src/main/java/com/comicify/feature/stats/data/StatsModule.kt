package com.comicify.feature.stats.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StatsModule {

    @Binds
    @Singleton
    abstract fun bindReadingStatsRepository(impl: ReadingStatsRepositoryImpl): ReadingStatsRepository
}
