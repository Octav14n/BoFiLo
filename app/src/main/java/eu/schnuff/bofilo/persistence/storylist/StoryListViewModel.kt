package eu.schnuff.bofilo.persistence.storylist

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import eu.schnuff.bofilo.persistence.AppDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*

class StoryListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(
        application
    ).storyListDao()
    val allItems = dao.getAll()
    val consoleOutput: LiveData<String>
        get() = Companion.consoleOutput

    fun setConsoleOutput(output: String) {
        Companion.consoleOutput.postValue(output)
    }

    fun get(url: String): StoryListItem {
        return implGet(url)!!
    }


    fun has(url: String): Boolean {
        return implGet(url) != null
    }

    private fun implGet(url: String) = dao.getByUrl(url)

    fun add(url: String, title: String? = null, uri: String? = null, progress: Int? = null, max: Int? = null): StoryListItem {
        // Add item.
        val item = recreate(implGet(url), url, uri, title, progress, max)
        runBlocking {
            add(item).join()
        }
        return item
    }

    private fun recreate(item: StoryListItem?, url: String, uri: String?, title: String?, progress: Int?, max: Int?): StoryListItem {
        // if url is already in the db then copy data from it
        return if (item == null) {
            StoryListItem(title, url, uri, progress, max)
        } else {
            remove(item)
            item.copy(title = title, url = url, uri = uri, progress = progress, max = max)
        }
    }

    private fun remove(story: StoryListItem?) {
        story?.let { remove(it) }
    }

    fun start(item: StoryListItem) {
        update(item) {
            this.created = Date().time
        }
    }

    fun setUrl(item: StoryListItem, url: String): StoryListItem {
        remove(item)
        val newItem = item.copy(url)
        add(newItem)
        return newItem
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

    fun setUri(item: StoryListItem, uri: Uri) {
        update(item) {
            this.uri = uri.toString()
        }
    }

    fun setFinished(item: StoryListItem, finishedValue: Boolean = true) {
        runBlocking {
            update(item) {
                this.finished = finishedValue
            }.join()
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