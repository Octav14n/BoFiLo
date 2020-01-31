package eu.schnuff.bofilo.persistence

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface StoryListDao {
    @Query("SELECT * FROM story_list_item ORDER BY finished ASC, created DESC")
    fun getAll(): LiveData<Array<StoryListItem>>

    @Query("SELECT * FROM story_list_item WHERE url = :url")
    fun getByUrl(url: String): StoryListItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: StoryListItem)

    @Delete
    suspend fun delete(item: StoryListItem)

    @Query("DELETE FROM story_list_item WHERE url = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM story_list_item")
    suspend fun deleteAll()

    @Update
    suspend fun update(item: StoryListItem)
}