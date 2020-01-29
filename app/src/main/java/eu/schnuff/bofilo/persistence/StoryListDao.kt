package eu.schnuff.bofilo.persistence

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface StoryListDao {
    @Query("SELECT * FROM story_list_item ORDER BY created")
    fun getAll(): LiveData<List<StoryListItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: StoryListItem)

    @Delete
    suspend fun delete(item: StoryListItem)

    @Update
    suspend fun update(item: StoryListItem)
}