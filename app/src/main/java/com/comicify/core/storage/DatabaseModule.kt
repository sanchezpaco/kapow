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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)
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

    @Provides
    fun bookmarkDao(database: KapowDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun readingListDao(database: KapowDatabase): ReadingListDao = database.readingListDao()
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
        db.execSQL("ALTER TABLE comics ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE comics ADD COLUMN metadataEdited INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bookmark` (" +
                "`comicId` INTEGER NOT NULL, `pageIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`comicId`, `pageIndex`), " +
                "FOREIGN KEY(`comicId`) REFERENCES `comics`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmark_comicId` ON `bookmark` (`comicId`)")
    }
}

private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE comics ADD COLUMN coverPage INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reading_list` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reading_list_entry` (" +
                "`listId` INTEGER NOT NULL, `comicId` INTEGER NOT NULL, `ordering` INTEGER NOT NULL, " +
                "PRIMARY KEY(`listId`, `comicId`), " +
                "FOREIGN KEY(`listId`) REFERENCES `reading_list`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`comicId`) REFERENCES `comics`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_list_entry_comicId` ON `reading_list_entry` (`comicId`)")
    }
}

private const val READING_TYPE_OF_RTL = "CASE %1\$s WHEN 1 THEN 'Manga' WHEN 0 THEN 'Comic' END"

private val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `comics_typed` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `documentUri` TEXT NOT NULL, " +
                "`displayName` TEXT NOT NULL, `series` TEXT NOT NULL, `issueNumber` INTEGER, `year` INTEGER, " +
                "`pageCount` INTEGER, `coverPath` TEXT, `addedAt` INTEGER NOT NULL, `favorite` INTEGER NOT NULL, " +
                "`coverAmbient` INTEGER, `storyTitle` TEXT, `publisher` TEXT, `writer` TEXT, `penciller` TEXT, " +
                "`inker` TEXT, `colorist` TEXT, `summary` TEXT, `readingType` TEXT, " +
                "`metadataVersion` INTEGER NOT NULL, `contentHash` TEXT, `rating` INTEGER NOT NULL, " +
                "`metadataEdited` INTEGER NOT NULL, `coverPage` INTEGER NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO `comics_typed` SELECT id, documentUri, displayName, series, issueNumber, year, " +
                "pageCount, coverPath, addedAt, favorite, coverAmbient, storyTitle, publisher, writer, penciller, " +
                "inker, colorist, summary, " + READING_TYPE_OF_RTL.format("readsRightToLeft") + ", " +
                "metadataVersion, contentHash, rating, metadataEdited, coverPage FROM `comics`",
        )
        db.execSQL("DROP TABLE `comics`")
        db.execSQL("ALTER TABLE `comics_typed` RENAME TO `comics`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_comics_documentUri` ON `comics` (`documentUri`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_comics_contentHash` ON `comics` (`contentHash`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `comic_settings_typed` (" +
                "`documentUri` TEXT NOT NULL, `readingType` TEXT, `coverAlone` INTEGER NOT NULL, " +
                "`bubblesEnlarged` INTEGER, `guided` INTEGER, `bubbleScale` REAL, " +
                "`splitWidePages` INTEGER NOT NULL, `splitSuggested` INTEGER NOT NULL, " +
                "`verticalScroll` INTEGER NOT NULL, PRIMARY KEY(`documentUri`))",
        )
        db.execSQL(
            "INSERT INTO `comic_settings_typed` SELECT documentUri, " +
                READING_TYPE_OF_RTL.format("rightToLeft") + ", coverAlone, bubblesEnlarged, guided, bubbleScale, " +
                "splitWidePages, splitSuggested, verticalScroll FROM `comic_settings`",
        )
        db.execSQL("DROP TABLE `comic_settings`")
        db.execSQL("ALTER TABLE `comic_settings_typed` RENAME TO `comic_settings`")
    }
}
