package eu.schnuff.bofilo.persistence

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.*

class StoryListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = StoryListDatabase.getDatabase(application).storyListDao()
    val allItems = dao.getAll()

    fun get(url: String): StoryListItem {
        return implGet(url)!!
    }

    fun has(url: String): Boolean {
        return implGet(url) != null
    }

    private fun implGet(url: String) = dao.getByUrl(url)

    fun add(url: String, title: String? = null, progress: Int? = null, max: Int? = null): StoryListItem {
        val m = implGet(url)
        val item = remove(m)?.copy(url, title, progress, max) ?: StoryListItem(title, url, progress, max)
        add(item)
        return item
    }

    private fun remove(story: StoryListItem?): StoryListItem? {
        story?.let { remove(it) }
        return story
    }

//    private fun remove(url: String): StoryListItem? {
//        val m = implGet(url)
//        if (m != null) {
//            remove(m)
//        }
//        return m
//    }

    fun start(item: StoryListItem) {
        update(item) {
            this.created = Date().time
        }
    }

    fun setUrl(item: StoryListItem, url: String) {
        remove(item)
        add(item.copy(url))
    }

//    fun setUrl(oldUrl: String, url: String) {
//        val m = get(oldUrl)
//        remove(m)
//        val new = m.copy(url)
//        add(new)
//    }

    fun setProgress(item: StoryListItem, progress: Int, max: Int? = null) {
        update(item) {
            this.progress = progress
            this.max = max
        }
    }

//    fun setProgress(url: String, progress: Int = 0, max: Int? = null) {
//        update(url) {
//            this.progress = progress
//            this.max = max
//            true
//        }
//    }

    fun setTitle(item: StoryListItem, title: String) {
        update(item) {
            this.title = title
        }
    }

//    fun setTitle(url: String, title: String) {
//        update(url) {
//            this.title = title
//            true
//        }
//    }

    fun setFinished(item: StoryListItem) {
        update(item) {
            this.finished = true
        }
    }

//    fun setFinished(url: String) {
//        update(url) {
//            this.finished = true
//            true
//        }
//    }

    private fun add(item: StoryListItem) = viewModelScope.launch {
        dao.insert(item)
    }
//    private fun update(url: String, apply: StoryListItem.() -> Boolean) = update(get(url), apply)
    private fun update(item: StoryListItem, apply: StoryListItem.() -> Unit) = viewModelScope.launch {
        item.apply(apply)
        dao.update(item)
    }
    fun remove(item: StoryListItem) = viewModelScope.launch {
        dao.delete(item)
    }
    private fun remove(url: String) = viewModelScope.launch {
        dao.delete(url)
    }
    fun removeAll() = viewModelScope.launch {
        dao.deleteAll()
    }
}