package eu.schnuff.bofilo

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
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
    private var wakeLock: PowerManager.WakeLock? = null
    private var viewModel: StoryListViewModel? = null
    private var originalFile: Uri? = null
    private var cacheFile: Uri? = null
    private var fileName: String = ""
    private val outputBuilder = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        viewModel = StoryListViewModel(application)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BoFiLo::DownloadService").apply { setReferenceCounted(false) }
        py.getModule("os").callAttr("chdir", cacheDir.absolutePath)
    }

    override fun onHandleIntent(intent: Intent) {
        val url = intent.getStringExtra(PARAM_URL)!!

        ActiveItem = viewModel!!.get(url)
        if (ActiveItem == null) {
            return
            //viewModel!!.add(url)
        }
        wakeLock!!.acquire(20000)
        viewModel!!.start(ActiveItem!!)
        val saveCache = defaultSharedPreference.getBoolean(Constants.PREF_SAVE_CACHE, false)

        try {
            val personalini = File(filesDir, "personal.ini")
            if (personalini.exists()) {
                helper.callAttr("read_personal_ini", personalini.absolutePath)
            }
            helper.callAttr("start", this, url, saveCache)
            ActiveItem?.let {
                //if (it.max != null && it.progress != null && it.max!! > 0 && it.progress!! >= it.max!!) {
                this.finished()
                //}
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
            wakeLock!!.release()
            wakeLock!!.acquire(250)
            outputBuilder.append(getString(R.string.console_finish_message).format(url))
            outputBuilder.append("\n\n\n")
            // outputBuilder.clear()
            // viewModel?.setConsoleOutput("")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
    }

    fun output(output: String) {
        outputBuilder.append(output)
        Log.d(TAG, "console: $output")
        viewModel?.setConsoleOutput(outputBuilder.toString())
    }
    fun start(url: String) {
        Log.d(TAG, "start($ActiveItem --> $url)")
        viewModel!!.setUrl(ActiveItem!!, url)
    }
    fun getLogin(passwordOnly: Boolean = false): Array<String> {
        output("\n!!! Site needs Login. This is not implemented at the moment. Use personal.ini instead.\n\n")
        val password = "secret"
        val username = "secret"
        return arrayOf(password, username)
    }
    fun getIsAdult(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(applicationContext).getBoolean(Constants.PREF_IS_ADULT, false)
    }
    fun chapters(now: Int, max: Int) {
        Log.d(TAG, "chapters($ActiveItem, $now, $max)")
        viewModel!!.setProgress(ActiveItem!!, now, max)
        wakeLock!!.acquire(20000)
    }
    fun filename(name: String) {
        Log.d(TAG, "filename($name)")
        fileName = name
        cacheFile = File(cacheDir, name).toUri()
        if (originalFile == null) {
            if (isDir) {
                val dir = getDir()
                originalFile = dir.findFile(name)?.uri
                originalFile?.let {
                    Log.d(TAG, "\tnow copying file to cache.")
                    output("Copy extern epub file to cache.\n")
                    wakeLock!!.acquire(60000)
                    contentResolver.copyFile(it, cacheFile!!)
                } ?: output("File not found in folder.\n")
            }
        }
    }
    fun title(title: String) = viewModel!!.setTitle(ActiveItem!!, title)
    private fun finished() {
        // copy data back to original file
        cacheFile?.let {
            if (isDir) {
                if (ActiveItem!!.max != null && ActiveItem!!.progress != null && ActiveItem!!.max!! > 0 && ActiveItem!!.progress!! >= ActiveItem!!.max!!) {
                    contentResolver.copyFile(
                        it,
                        originalFile ?: getDir().createFile(Constants.MIME_EPUB, fileName)!!.uri
                    )
                }
                it.toFile().delete()
            }
        }

        // "inform" user about finish
        viewModel!!.setFinished(ActiveItem!!)
    }
    private val defaultSharedPreference
        get() = PreferenceManager.getDefaultSharedPreferences(applicationContext)

    private val isDir
        get() = defaultSharedPreference.contains(Constants.PREF_DEFAULT_DIRECTORY)
    private fun getDir() = DocumentFile.fromTreeUri(applicationContext,
            defaultSharedPreference.getString(Constants.PREF_DEFAULT_DIRECTORY, cacheDir.absolutePath)?.toUri() ?: cacheDir.toUri())!!

    companion object {
        const val PARAM_ID = "id"
        const val PARAM_URL = "url"
        const val PARAM_DIR = "dir"
        var ActiveItem: StoryListItem? = null
        private set
        const val TAG = "download"
    }
}
