package eu.schnuff.bofilo.persistence

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class StoryListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = StoryListDatabase.getDatabase(application).storyListDao()
    val allItems = dao.getAll()

    fun get(storyId: Int): StoryListItem {
        return implGet(storyId)!!
    }

    private fun implGet(storyId: Int): StoryListItem? {
        return allItems.value!!.find { s -> s.storyId == storyId }
    }

    private fun find(url: String): StoryListItem? {
        return allItems.value!!.find { s -> s.url == url }
    }

    fun add(url: String, title: String? = null, progress: Int? = null, max: Int? = null): StoryListItem {
        val item = remove(find(url))?.copy(title, progress, max) ?: StoryListItem(title, url, progress, max)
        add(item)
        return item
    }

    private fun remove(story: StoryListItem?): StoryListItem? {
        story?.let { remove(it) }
        return story
    }

//    private fun remove(storyId: Int): StoryListItem? {
//        val m = implGet(storyId)
//        if (m != null) {
//            remove(m)
//        }
//        return m
//    }

    fun setProgress(storyId: Int, progress: Int = 0, max: Int? = null) {
        val m = get(storyId)
        m.progress = progress
        m.max = max
        update(m)
    }

    fun setTitle(storyId: Int, title: String) {
        val m = get(storyId)
        m.title = title
        update(m)
    }

    fun setFinished(storyId: Int) {
        val m = get(storyId)
        m.finished = true
        update(m)
    }

    private fun add(item: StoryListItem) = viewModelScope.launch {
        dao.insert(item)
    }
    private fun update(item: StoryListItem) = viewModelScope.launch {
        dao.update(item)
    }
    private fun remove(item: StoryListItem) = viewModelScope.launch {
        dao.delete(item)
    }
}