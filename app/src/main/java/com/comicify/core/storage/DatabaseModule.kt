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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()

    @Provides
    fun comicDao(database: ComicifyDatabase): ComicDao = database.comicDao()

    @Provides
    fun readingStateDao(database: ComicifyDatabase): ReadingStateDao = database.readingStateDao()

    @Provides
    fun pageDetectionDao(database: ComicifyDatabase): PageDetectionDao = database.pageDetectionDao()

    @Provides
    fun comicSettingsDao(database: ComicifyDatabase): ComicSettingsDao = database.comicSettingsDao()
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE comics ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS page_detections (" +
                "documentUri TEXT NOT NULL, pageIndex INTEGER NOT NULL, modelVersion TEXT NOT NULL, " +
                "panels TEXT, bubbles TEXT, PRIMARY KEY(documentUri, pageIndex))",
        )
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE comics ADD COLUMN coverAmbient INTEGER")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS comic_settings (" +
                "documentUri TEXT NOT NULL, rightToLeft INTEGER, coverAlone INTEGER NOT NULL, " +
                "bubblesEnlarged INTEGER, guided INTEGER, PRIMARY KEY(documentUri))",
        )
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reading_states ADD COLUMN shelved INTEGER NOT NULL DEFAULT 1")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE comic_settings ADD COLUMN bubbleScale REAL")
    }
}
