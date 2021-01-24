package eu.schnuff.bofilo.persistence.filewrappercache

import android.net.Uri
import androidx.room.*

@Dao
interface FileWrapperCacheDao {
    @Query("SELECT * FROM file_wrapper_cache_item")
    fun get() : Array<FileWrapperCacheItem>

    @Query("SELECT * FROM file_wrapper_cache_item WHERE parentUri == :parentUri")
    fun get(parentUri: String) : Array<FileWrapperCacheItem>
    fun get(parentUri: Uri) = get(parentUri.toString())

    @Insert
    fun add(items: List<FileWrapperCacheItem>)

    @Insert
    fun add(item: FileWrapperCacheItem)
    fun add(childId: String, parentUri: Uri, fileName: String) = add(FileWrapperCacheItem(childId, parentUri.toString(), fileName))

    @Update
    fun set(item: FileWrapperCacheItem)
    fun set(childId: String, parentUri: Uri, fileName: String) = set(FileWrapperCacheItem(childId, parentUri.toString(), fileName))

    @Delete
    fun remove(items: List<FileWrapperCacheItem>)
}