package eu.schnuff.bofilo.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "story_list_item")
data class StoryListItem(var title: String?, var url: String, var progress: Int?, var max: Int?, var finished: Boolean = false, val created: Long = Date().time) {
    @PrimaryKey(autoGenerate = true)
    val storyId: Int = 0

    fun copy(title: String? = null, progress: Int? = null, max: Int? = null, finished: Boolean? = null) = StoryListItem(
        title ?: this.title,
        url,
        progress ?: this.progress,
        max ?: this.max,
        finished ?: this.finished
    )
}