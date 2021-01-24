package eu.schnuff.bofilo.persistence.filewrappercache

import androidx.room.Entity

@Entity(tableName = "file_wrapper_cache_item", primaryKeys = ["childId", "parentUri"])
data class FileWrapperCacheItem(
    val childId: String,
    val parentUri: String,
    val filename: String
)
