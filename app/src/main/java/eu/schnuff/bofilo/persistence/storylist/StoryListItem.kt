package eu.schnuff.bofilo.persistence.storylist

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "story_list_item")
data class StoryListItem(var title: String?, @PrimaryKey val url: String, var uri: String?, var progress: Int?, var max: Int?, var finished: Boolean = false, var created: Long = Date().time) {
    var forceDownload:Boolean = false

    fun copy(newUrl:String, title: String? = null, uri: String? = null, progress: Int? = null, max: Int? = null, finished: Boolean? = null, created: Long? = null) =
        StoryListItem(
            title ?: this.title,
            newUrl,
            uri ?: this.uri,
            progress ?: this.progress,
            max ?: this.max,
            finished ?: this.finished,
            created ?: Date().time
        ).apply {
            this.forceDownload = this@StoryListItem.forceDownload
        }
}