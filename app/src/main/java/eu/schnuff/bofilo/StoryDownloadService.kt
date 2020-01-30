package eu.schnuff.bofilo

import android.app.IntentService
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.chaquo.python.PyException
import com.chaquo.python.Python
import eu.schnuff.bofilo.persistence.StoryListItem
import eu.schnuff.bofilo.persistence.StoryListViewModel

class StoryDownloadService : IntentService("StoryDownloadService") {
    private val py = Python.getInstance()
    private val helper = py.getModule("helper")
    private var viewModel: StoryListViewModel? = null
    private var item: StoryListItem? = null

    override fun onCreate() {
        super.onCreate()
        viewModel = StoryListViewModel(application)
    }

    override fun onHandleIntent(intent: Intent) {
        val url = intent.getStringExtra(PARAM_URL)!!
        val dir = intent.getStringExtra(PARAM_DIR) ?: cacheDir.absolutePath

        item = viewModel!!.get(url)
        ActiveItem = item
        viewModel!!.start(item!!)

        py.getModule("os").callAttr("chdir", dir)

        try {
            helper.callAttr("start", this, url)
            this.finished()
        } catch (e: PyException) {
            Toast.makeText(baseContext, "Error downloading $url: ${e.message}:\n${e.localizedMessage}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            if (item != null)
                viewModel!!.setTitle(item!!, e.localizedMessage)
            item = null
        }
    }

    fun start(url: String) {
        Log.d("download", "start(${this.item} --> $url)")
        viewModel!!.setUrl(item!!, url)
    }
    fun chapters(now: Int, max: Int) {
        Log.d("download", "chapters($item, $now, $max)")
        viewModel!!.setProgress(item!!, now, max)
    }
    fun title(title: String) = viewModel!!.setTitle(item!!, title)
    private fun finished() {
        viewModel!!.setFinished(item!!)
        item = null
    }

    companion object {
        const val PARAM_ID = "id"
        const val PARAM_URL = "url"
        const val PARAM_DIR = "dir"
        var ActiveItem: StoryListItem? = null
        private set
    }
}
