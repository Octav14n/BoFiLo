package eu.schnuff.bofilo.ui

import eu.schnuff.bofilo.persistence.storylist.StoryListItem

interface StoryActionInterface {
    fun restart(story: StoryListItem)
    fun unnew(story: StoryListItem)
    fun forcedownload(story: StoryListItem)
    fun delete(story: StoryListItem)
}