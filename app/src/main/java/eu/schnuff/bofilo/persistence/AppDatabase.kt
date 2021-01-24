package eu.schnuff.bofilo.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.schnuff.bofilo.persistence.filewrappercache.FileWrapperCacheDao
import eu.schnuff.bofilo.persistence.filewrappercache.FileWrapperCacheItem
import eu.schnuff.bofilo.persistence.storylist.StoryListDao
import eu.schnuff.bofilo.persistence.storylist.StoryListItem

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE `file_wrapper_cache_item` (" +
                "`childId` TEXT NOT NULL, " +
                "`parentUri` TEXT NOT NULL, " +
                "`filename` TEXT NOT NULL, " +
                "PRIMARY KEY(`childId`, `parentUri`))")
    }
}

@Database(entities = [StoryListItem::class, FileWrapperCacheItem::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storyListDao(): StoryListDao
    abstract fun fileWrapperCacheDao(): FileWrapperCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(
            context: Context
        ): AppDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "word_database"
                ).addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}