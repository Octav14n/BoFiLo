package eu.schnuff.bofilo.persistence

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.*

class StoryListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = StoryListDatabase.getDatabase(application).storyListDao()
    val allItems = dao.getAll()
    val consoleOutput: LiveData<String>
        get() = StoryListViewModel.consoleOutput

    fun setConsoleOutput(output: String){
        StoryListViewModel.consoleOutput.postValue(output)
    }

    fun get(url: String): StoryListItem {
        return implGet(url)!!
    }

    fun has(url: String): Boolean {
        return implGet(url) != null
    }

    private fun implGet(url: String) = dao.getByUrl(url)

    fun add(url: String, title: String? = null, progress: Int? = null, max: Int? = null): StoryListItem {
        // Add item.
        // if url is already in the db then copy data from it
        val m = implGet(url)
        val item = remove(m)?.copy(url, title, progress, max) ?: StoryListItem(title, url, progress, max)
        add(item)
        return item
    }

    private fun remove(story: StoryListItem?): StoryListItem? {
        story?.let { remove(it) }
        return story
    }

    fun start(item: StoryListItem) {
        update(item) {
            this.created = Date().time
        }
    }

    fun setUrl(item: StoryListItem, url: String) {
        remove(item)
        add(item.copy(url))
    }

    fun setProgress(item: StoryListItem, progress: Int, max: Int? = null) {
        update(item) {
            this.progress = progress
            this.max = max
        }
    }

    fun setTitle(item: StoryListItem, title: String) {
        update(item) {
            this.title = title
        }
    }

    fun setFinished(item: StoryListItem) {
        update(item) {
            this.finished = true
        }
    }


    // Interaction with the DataAccessObject
    // Add given item into the db
    private fun add(item: StoryListItem) = viewModelScope.launch {
        dao.insert(item)
    }

    // remove all items from the db
    fun removeAll() = viewModelScope.launch {
        dao.deleteAll()
    }
    // remove given item from the db.
    fun remove(item: StoryListItem) = viewModelScope.launch {
        dao.delete(item)
    }
    // update given item in the db
    private fun update(item: StoryListItem, apply: StoryListItem.() -> Unit) = viewModelScope.launch {
        item.apply(apply)
        dao.update(item)
    }

    companion object {
        private val consoleOutput = MutableLiveData<String>()
    }
}