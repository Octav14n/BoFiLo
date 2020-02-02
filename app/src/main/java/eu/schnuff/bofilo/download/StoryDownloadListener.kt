package eu.schnuff.bofilo.download

import eu.schnuff.bofilo.persistence.StoryListItem

interface StoryDownloadListener {
    fun onStoryDownloadProgress(item: StoryListItem)
}