package com.comicify.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ComicEntity::class,
        ReadingStateEntity::class,
        PageDetectionEntity::class,
        ComicSettingsEntity::class,
        ReadingSessionEntity::class,
        ReadingListEntity::class,
        ReadingListEntryEntity::class,
    ],
    version = 14,
    exportSchema = false,
)
abstract class KapowDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao
    abstract fun readingStateDao(): ReadingStateDao
    abstract fun pageDetectionDao(): PageDetectionDao
    abstract fun comicSettingsDao(): ComicSettingsDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun readingListDao(): ReadingListDao
}
