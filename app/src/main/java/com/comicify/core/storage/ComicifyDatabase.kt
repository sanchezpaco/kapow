package com.comicify.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ComicEntity::class, ReadingStateEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ComicifyDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao
    abstract fun readingStateDao(): ReadingStateDao
}
