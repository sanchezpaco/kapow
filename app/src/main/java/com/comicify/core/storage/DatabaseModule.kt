package com.comicify.core.storage

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ComicifyDatabase =
        Room.databaseBuilder(context, ComicifyDatabase::class.java, "comicify.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun comicDao(database: ComicifyDatabase): ComicDao = database.comicDao()

    @Provides
    fun readingStateDao(database: ComicifyDatabase): ReadingStateDao = database.readingStateDao()
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE comics ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
    }
}
