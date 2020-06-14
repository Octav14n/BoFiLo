package eu.schnuff.bofilo.download

import eu.schnuff.bofilo.persistence.storylist.StoryListItem

interface StoryDownloadListener {
    fun onStoryDownloadProgress(item: StoryListItem)
}