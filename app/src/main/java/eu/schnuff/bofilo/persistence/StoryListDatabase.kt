package eu.schnuff.bofilo.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [StoryListItem::class], version = 1, exportSchema = false)
abstract class StoryListDatabase : RoomDatabase() {
    abstract fun storyListDao(): StoryListDao

    companion object {
        @Volatile
        private var INSTANCE: StoryListDatabase? = null

        fun getDatabase(
            context: Context
        ): StoryListDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StoryListDatabase::class.java,
                    "word_database"
                )
                    .build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}