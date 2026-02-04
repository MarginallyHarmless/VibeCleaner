package com.example.photocleanup.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReviewedPhoto::class, PhotoHash::class, DuplicateGroup::class],
    version = 6,
    exportSchema = false
)
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun reviewedPhotoDao(): ReviewedPhotoDao
    abstract fun photoHashDao(): PhotoHashDao
    abstract fun duplicateGroupDao(): DuplicateGroupDao

    companion object {
        @Volatile
        private var INSTANCE: PhotoDatabase? = null

        /**
         * Migration from version 1 to 2.
         * Adds photo_hashes and duplicate_groups tables for duplicate detection feature.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create photo_hashes table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS photo_hashes (
                        uri TEXT PRIMARY KEY NOT NULL,
                        hash INTEGER NOT NULL,
                        fileSize INTEGER NOT NULL,
                        width INTEGER NOT NULL,
                        height INTEGER NOT NULL,
                        dateAdded INTEGER NOT NULL,
                        lastScanned INTEGER NOT NULL,
                        bucketId INTEGER NOT NULL,
                        bucketName TEXT NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_photo_hashes_hash ON photo_hashes(hash)")

                // Create duplicate_groups table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS duplicate_groups (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        groupId TEXT NOT NULL,
                        photoUri TEXT NOT NULL,
                        similarityScore INTEGER NOT NULL,
                        isKept INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_duplicate_groups_groupId ON duplicate_groups(groupId)")
            }
        }

        /**
         * Migration from version 2 to 3.
         * Adds algorithmVersion column to photo_hashes table for cache invalidation
         * when the hashing algorithm changes.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photo_hashes ADD COLUMN algorithmVersion INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration from version 3 to 4.
         * Adds pHash column for two-stage duplicate detection using DCT-based perceptual hashing.
         * The pHash provides stricter confirmation after dHash candidate selection.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photo_hashes ADD COLUMN pHash INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration from version 4 to 5.
         * Adds colorHistogram column for color pre-filtering to reduce false positives.
         * Photos with different color distributions are quickly rejected before hash comparison.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photo_hashes ADD COLUMN colorHistogram TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Migration from version 5 to 6.
         * Adds edgeHash column for Sobel-based edge detection hash.
         * Edge hash captures structural patterns and is brightness-invariant, allowing
         * detection of duplicates with different lighting/exposure that fail color matching.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photo_hashes ADD COLUMN edgeHash INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): PhotoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhotoDatabase::class.java,
                    "photo_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
