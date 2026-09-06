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
    fun database(@ApplicationContext context: Context): KapowDatabase =
        Room.databaseBuilder(context, KapowDatabase::class.java, "comicify.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
            .build()

    @Provides
    fun comicDao(database: KapowDatabase): ComicDao = database.comicDao()

    @Provides
    fun readingStateDao(database: KapowDatabase): ReadingStateDao = database.readingStateDao()

    @Provides
    fun pageDetectionDao(database: KapowDatabase): PageDetectionDao = database.pageDetectionDao()

    @Provides
    fun comicSettingsDao(database: KapowDatabase): ComicSettingsDao = database.comicSettingsDao()

    @Provides
    fun readingSessionDao(database: KapowDatabase): ReadingSessionDao = database.readingSessionDao()
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

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE comic_settings ADD COLUMN splitWidePages INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE comic_settings ADD COLUMN splitSuggested INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE comic_settings ADD COLUMN verticalScroll INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("storyTitle", "publisher", "writer", "penciller", "inker", "colorist", "summary").forEach { column ->
            db.execSQL("ALTER TABLE comics ADD COLUMN $column TEXT")
        }
        db.execSQL("ALTER TABLE comics ADD COLUMN readsRightToLeft INTEGER")
        db.execSQL("ALTER TABLE comics ADD COLUMN metadataVersion INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reading_session` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `comicId` INTEGER NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `pages` INTEGER NOT NULL, " +
                "`mode` TEXT NOT NULL, `finished` INTEGER NOT NULL, " +
                "FOREIGN KEY(`comicId`) REFERENCES `comics`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_session_comicId` ON `reading_session` (`comicId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_session_startedAt` ON `reading_session` (`startedAt`)")
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE comics ADD COLUMN contentHash TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_comics_contentHash` ON `comics` (`contentHash`)")
    }
}

private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE comics ADD COLUMN coverPage INTEGER NOT NULL DEFAULT 0")
    }
}
