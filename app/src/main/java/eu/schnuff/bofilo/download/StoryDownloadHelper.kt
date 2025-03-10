package eu.schnuff.bofilo.download

import android.net.Uri
import android.os.PowerManager
import android.util.Log
import androidx.core.net.toUri
import com.chaquo.python.PyObject
import eu.schnuff.bofilo.Constants
import eu.schnuff.bofilo.download.filewrapper.FileWrapper
import eu.schnuff.bofilo.persistence.storylist.StoryListItem
import eu.schnuff.bofilo.persistence.storylist.StoryListViewModel
import java.io.File
import java.nio.file.Files
import kotlin.concurrent.thread

class StoryDownloadHelper(
    private val progressListeners: Array<StoryDownloadListener>,
    private val helper: PyObject,
    private val wakeLock: PowerManager.WakeLock,
    private val cacheDir: File,
    private val dstDir: Uri?,
    private val srcDir: FileWrapper?,
    private val viewModel: StoryListViewModel,
    private val consoleOutput: StringBuilder,
    private val isAdult: Boolean,
    private val isSaveCache: Boolean,
    private val fileInteraction: FileInteraction,
    private val webRequest: StoryDownloadWebRequest,
    item: StoryListItem
) {
    var item = item
    private set
    private var originalFile = item.uri?.let{ uri -> fileInteraction.fromUri(Uri.parse(uri)) }
    // Gets called by the script to announce the filename the epub will be saved to.
    @SuppressWarnings("unused")
    var filename: String = originalFile?.name ?: ""
        get() = if (field.isEmpty()) "download_no_name.epub" else field
        set (value) {
            if (value.isEmpty() || value == field) {
                if (originalFile == null)
                    field = ""
                return
            }
            field = value
            cacheFile = File(cacheDir, value)
            Log.d(TAG, "filename($value)")
            if (!checkedForOriginalFile && srcDir != null && originalFile == null) {
                // if we have not provided a (update able) epub and
                // the user has specified a directory in settings
                // we will now search/provide an update able epub
                add_output("Starting search extern update able epub\n")
                val startTime = System.currentTimeMillis()
                originalFile = srcDir.getChild(value)
                originalFile?.uri?.let { uri ->
                    Log.d(TAG, "found file at uri '%s'".format(uri))
                    viewModel.setUri(item, uri)
                }
                loadOriginalFile()
                add_output("%s complete in %.2f sec.\n".format((if (cacheFile.exists()) "Search + Copy" else "Search"), (System.currentTimeMillis() - startTime).toFloat() / 1000))
            }
            checkedForOriginalFile = true

        }
    private var cacheFile = File(cacheDir, filename)
    private var checkedForOriginalFile = false

    fun run() {
        // Tell the UI/db that we are starting to download
        viewModel.start(item)

        originalFile?.let {
            add_output("Load extern update able epub\n")
            val startTime = System.currentTimeMillis()
            loadOriginalFile()
            add_output("Loading complete in %.2f sec.\n".format((System.currentTimeMillis() - startTime).toFloat() / 1000))
        }

        try {
            // Start the python-part of the service (including FanFicFare)
            val argument = originalFile?.name ?: item.url
            helper.callAttr("start", this, argument, isSaveCache, item.forceDownload)
            viewModel.setForcedDownload(item, false)
        } catch (e: Exception) {
            viewModel.setTitle(item, e.message ?: e.toString())
            // delete the file in the cache directory
            if (cacheFile.exists())
                cacheFile.delete()
            throw e
        }
    }

    private fun loadOriginalFile() {
        originalFile?.let {
            // if the original file is available we will copy it to the cache directory
            // because we use Storage-Access-Framework (uris begin with "content://") and python can't handle those.
            if (!it.isFile) {
                originalFile = null
                filename = ""
                return
            }
            if (cacheFile.exists())
                return
            Log.d(TAG, "\tnow copying file ${it.name} to cache.")
            add_output("Copy extern ${it.name} file to cache.\n")
            wakeLock.acquire(60000)
            try {
                cacheFile.writeBytes(ByteArray(0))
                fileInteraction.copyFile(it.uri, cacheFile.toUri())
                cacheFile.setLastModified(0) //it.lastModified)
                filename = cacheFile.name
            } catch (e: Throwable) {
                add_output("Error loading file ${e.message}")
                Log.e(TAG, e.message, e)
                originalFile = null
            }
        }
    }

    private fun notifyProgress() {
        for (listener in progressListeners) {
            listener.onStoryDownloadProgress(item)
        }
    }

    // Gets called by the script, everything in here will be displayed in the console
    @SuppressWarnings("unused")
    fun add_output(output: String) {
        consoleOutput.append(output)
        Log.d(TAG, "console: $output")
        viewModel.setConsoleOutput(consoleOutput.toString())
    }
    // Gets called by the script, the sanitized version of the url gets passed
    @SuppressWarnings("unused")
    fun start(url: String) {
        Log.d(TAG, "start(${item} --> $url)")
        item = viewModel.setUrl(item, url)
    }
    // If the script got a title this function gets called
    @SuppressWarnings("unused")
    fun title(value: String) {
        viewModel.setTitle(item, value)
        notifyProgress()
    }
    // If the script requests a website-quell-code this function gets called
    @SuppressWarnings("unused")
    fun web_request(method: String, url: String, @Suppress("UNUSED_PARAMETER") kargs: PyObject): String {
        Thread.sleep(3000)
        return webRequest.webRequest(method, url)
        // Log.i(this::class.simpleName, "got return for url $url:\n\n$ret")
    }

    fun filename(value: String) { this.filename = value }
    // Gets called by the script if the requested site needs login information
    @Suppress("UNUSED_PARAMETER")
    @SuppressWarnings("unused")
    fun get_login(passwordOnly: Boolean = false): Array<String> {
        // TODO implement user interaction to provide a password or (username and password).
        add_output("\n! Site needs Login. This is not implemented at the moment.")
        if (File("personal.ini").exists())
            add_output(" Add login details to personal.ini instead.\n\n")
        else
            add_output(" Use personal.ini instead.\n\n")
        val password = ""
        val username = ""
        return arrayOf(password, username)
    }
    // Gets called by the script if the requested site needs adult verification
    @SuppressWarnings("unused")
    fun is_adult() = isAdult
    // Gets called by the script if new chapters or new information to maximal-chapters is available
    @SuppressWarnings("unused")
    fun chapters(now: Int?, max: Int?) {
        if (now == null || max == null || now == 0)
            return
        thread {
            wakeLock.acquire(20000)
            Log.d(TAG, "chapters(${item}, $now, $max)")
            val prevProgress = item.progress ?: 0
            val prevMax = item.max ?: 0
            viewModel.setProgress(item, now, max)

            // Update notification
            if (prevProgress <= now && prevMax <= max)
                notifyProgress()
        }
    }

    fun finish(success: Boolean) {
        // copy data back to original file...
        if (success && dstDir != null) {
            val progress = item.progress
            val max = item.max
            if (max != null && // Make sure the script made progress
                progress != null &&
                max > 0 &&
                progress >= max &&
                cacheFile.exists() && // Make sure the cache file has been written correct
                cacheFile.length() > 0 &&
                cacheFile.lastModified() > originalFile?.lastModified ?: 0L) {
                // ... but only if we made progress (hopefully handles errors on the python side)
                add_output("Starting to write file to output directory\n")
                if (originalFile != null) {
                    fileInteraction.copyFile(
                        cacheFile.toUri(),
                        originalFile!!.uri,
                        async = true
                    )
                } else {
                    fileInteraction.copyFile(
                        item,
                        cacheFile.toUri(),
                        dstDir,
                        Constants.MIME_EPUB,
                        filename
                    )
                }
            }
        } else {
            viewModel.setUri(item, null)
            // delete the file in the cache directory
            if (cacheFile.exists())
                cacheFile.delete()
        }

        // "inform" user about finish
        viewModel.setFinished(item)
    }

    interface FileInteraction {
        fun fromUri(uri: Uri): FileWrapper
        fun copyFile(src: Uri, dst: Uri, async: Boolean = false)
        fun copyFile(item: StoryListItem, src: Uri, dstDir: Uri, mimeType: String, fileName: String)
    }

    companion object {
        const val TAG = StoryDownloadService.TAG + "-helper" // Debug TAG
    }
}