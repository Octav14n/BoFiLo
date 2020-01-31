package eu.schnuff.bofilo

import android.app.IntentService
import android.content.Intent
import android.net.Uri
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.chaquo.python.PyException
import com.chaquo.python.Python
import eu.schnuff.bofilo.persistence.StoryListItem
import eu.schnuff.bofilo.persistence.StoryListViewModel
import java.io.File


class StoryDownloadService : IntentService("StoryDownloadService") {
    private val py = Python.getInstance()
    private val helper = py.getModule("helper")
    private var viewModel: StoryListViewModel? = null
    private var originalFile: Uri? = null
    private var cacheFile: Uri? = null
    private var fileName: String = ""

    override fun onCreate() {
        super.onCreate()
        viewModel = StoryListViewModel(application)
        py.getModule("os").callAttr("chdir", cacheDir.absolutePath)
    }

    override fun onHandleIntent(intent: Intent) {
        val url = intent.getStringExtra(PARAM_URL)!!

        ActiveItem = viewModel!!.get(url)
        if (!viewModel!!.has(url)) {
            viewModel!!.add(url)
        }
        viewModel!!.start(ActiveItem!!)
        val saveCache = defaultSharedPreference.getBoolean(Constants.PREF_SAVE_CACHE, false)

        try {
            helper.callAttr("start", this, url, saveCache)
            ActiveItem?.let {
                if (it.max != null && it.progress != null && it.max!! > 0 && it.progress!! >= it.max!!) {
                    this.finished()
                }
            }
        } catch (e: PyException) {
            Toast.makeText(baseContext, "Error downloading $url: ${e.message}:\n${e.localizedMessage}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            ActiveItem?.run {
                viewModel?.setTitle(this, e.localizedMessage)
            }
        } finally {
            ActiveItem = null
            cacheFile = null
        }
    }

    fun start(url: String) {
        Log.d("download", "start($ActiveItem --> $url)")
        viewModel!!.setUrl(ActiveItem!!, url)
    }
    fun chapters(now: Int, max: Int) {
        Log.d("download", "chapters($ActiveItem, $now, $max)")
        viewModel!!.setProgress(ActiveItem!!, now, max)
    }
    fun filename(name: String) {
        Log.d("download", "filename($name)")
        fileName = name
        cacheFile = File(cacheDir, name).toUri()
        if (originalFile == null) {
            Log.d("download", "\tnow copying file to cache.")

            val dir = getDir()
            originalFile = dir.findFile(name)?.uri
            originalFile?.let {
                copyFile(it, cacheFile!!)
            }
        }
    }
    fun title(title: String) = viewModel!!.setTitle(ActiveItem!!, title)
    private fun finished() {
        // copy data back to original file
        cacheFile?.let {
            copyFile(it, originalFile ?: getDir().createFile(Constants.MIME_EPUB, fileName)!!.uri)
            it.toFile().delete()
        }

        // "inform" user about finish
        viewModel!!.setFinished(ActiveItem!!)
    }
    private val defaultSharedPreference
    get() = PreferenceManager.getDefaultSharedPreferences(applicationContext)

    private fun getDir() = DocumentFile.fromTreeUri(applicationContext,
            defaultSharedPreference.getString(Constants.PREF_DEFAULT_DIRECTORY, cacheDir.absolutePath)?.toUri() ?: cacheDir.toUri())!!

    private fun copyFile(src: Uri, dst: Uri) {
        val input = contentResolver.openInputStream(src)!!
        val output = contentResolver.openOutputStream(dst)!!
        val buffer = ByteArray(1024)
        var len: Int
        while (input.read(buffer).also{ len = it } > 0) {
            output.write(buffer, 0, len)
        }
        input.close()
        output.close()
    }

    companion object {
        const val PARAM_ID = "id"
        const val PARAM_URL = "url"
        const val PARAM_DIR = "dir"
        var ActiveItem: StoryListItem? = null
        private set
    }
}
