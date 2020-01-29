package eu.schnuff.bofilo

import android.app.IntentService
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.chaquo.python.PyException
import com.chaquo.python.Python
import java.lang.IllegalStateException

class StoryDownloadService : IntentService("StoryDownloadService") {
    private val py = Python.getInstance()
    private val helper = py.getModule("helper")
    private var url: String? = null
    private var id: Int = -1

    override fun onHandleIntent(intent: Intent) {
        val url = intent.getStringExtra(PARAM_URL)!!
        val dir = intent.getStringExtra(PARAM_IN_DIR) ?: cacheDir.absolutePath
        py.getModule("os").callAttr("chdir", dir)

        try {
            helper.callAttr("start", this, url)
        } catch (e: PyException) {
            Toast.makeText(applicationContext, "Error downloading $url: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            send(ACTION_ERROR) {
                putExtra(PARAM_OUT_TEXT, e.localizedMessage)
            }
        }
        this.finished()
    }

    private fun send(action: String, apply: Intent.() -> Unit = {}) {
        if (id < 0) { throw IllegalStateException("Download ID is not defined.") }
        Log.d("intent", "perform $action broadcast.")
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(action).apply {
            putExtra(PARAM_URL, url)
            putExtra(PARAM_ID, )
        }.apply(apply))
    }

    fun start(url: String) {
        this.url = url
        send(ACTION_START)
    }

    fun chapters(now: Int, max: Int) = send(ACTION_PROGRESS) {
        putExtra(PARAM_OUT_PROGRESS, now)
        putExtra(PARAM_OUT_PROGRESS_MAX, max)
    }
    fun title(title: String) = send(ACTION_TITLE) {
        putExtra(PARAM_OUT_TEXT, title)
    }
    fun finished() = send(ACTION_FINISHED)

    companion object {
        const val PARAM_ID = "id"
        const val PARAM_URL = "url"
        const val PARAM_IN_DIR = "dir"
        const val PARAM_OUT_PROGRESS = "progress"
        const val PARAM_OUT_PROGRESS_MAX = "progress_max"
        const val PARAM_OUT_TEXT = "text"
        private const val ACTION_BASE = "eu.schnuff.bofilo"
        const val ACTION_START = "$ACTION_BASE.action_start"
        const val ACTION_PROGRESS = "$ACTION_BASE.action_progress"
        const val ACTION_TITLE = "$ACTION_BASE.action_title"
        const val ACTION_ERROR = "$ACTION_BASE.action_error"
        const val ACTION_FINISHED = "$ACTION_BASE.action_finished"
    }
}
