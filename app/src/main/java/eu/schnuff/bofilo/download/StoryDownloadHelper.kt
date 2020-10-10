package eu.schnuff.bofilo.download

import android.content.ContentResolver
import android.os.PowerManager
import android.util.Log
import androidx.core.net.toUri
import com.chaquo.python.PyObject
import eu.schnuff.bofilo.Constants
import eu.schnuff.bofilo.Helpers.copyFile
import eu.schnuff.bofilo.download.filewrapper.FileWrapper
import eu.schnuff.bofilo.persistence.storylist.StoryListItem
import eu.schnuff.bofilo.persistence.storylist.StoryListViewModel
import java.io.File
import kotlin.concurrent.thread

class StoryDownloadHelper(
    private val progressListeners: Array<StoryDownloadListener>,
    private val helper: PyObject,
    private val wakeLock: PowerManager.WakeLock,
    private val contentResolver: ContentResolver,
    private val cacheDir: File,
    private val dstDir: FileWrapper?,
    private val srcDir: FileWrapper?,
    private val viewModel: StoryListViewModel,
    private val consoleOutput: StringBuilder,
    private val isAdult: Boolean,
    private val isSaveCache: Boolean,
    item: StoryListItem
) {
    var item = item
    private set
    // Gets called by the script to announce the filename the epub will be saved to.
    @SuppressWarnings("unused")
    var filename: String = ""
        get() = if (field.isEmpty()) "download_no_name.epub" else field
        set (value) {
            if (value.isEmpty() || value == field)
                return
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
                originalFile?.let {
                    // if the original file is available we will copy it to the cache directory
                    // because we use Storage-Access-Framework (uris begin with "content://") and python can't handle those.
                    Log.d(TAG, "\tnow copying file to cache.")
                    add_output("Copy extern epub file to cache.\n")
                    wakeLock.acquire(60000)
                    contentResolver.copyFile(it.uri, cacheFile.toUri())
                    cacheFile.setLastModified(it.lastModified)
                }
                add_output("%s complete in %.2f sec.\n".format((if (cacheFile.exists()) "Search + Copy" else "Search"), (System.currentTimeMillis() - startTime).toFloat() / 1000))
            }
            checkedForOriginalFile = true

        }
    private var cacheFile = File(cacheDir, filename)
    private var originalFile: FileWrapper? = null
    private var checkedForOriginalFile = false

    fun run() {
        // Tell the UI/db that we are starting to download
        viewModel.start(item)

        try {
            // Start the python-part of the service (including FanFicFare)
            helper.callAttr("start", this, item.url, isSaveCache)
        } catch (e: Exception) {
            viewModel.setTitle(item, e.message ?: e.toString())
            throw e
        } finally {
            // delete the file in the cache directory
            if (cacheFile.exists())
                cacheFile.delete()
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
    fun filename(value: String) { this.filename = value }
    // Gets called by the script if the requested site needs login information
    @SuppressWarnings("unused")
    fun get_login(passwordOnly: Boolean = false): Array<String> {
        // TODO implement user interaction to provide a password or (username and password).
        add_output("\n! Site needs Login. This is not implemented at the moment. Use personal.ini instead.\n\n")
        val password = "secret"
        val username = "secret"
        return arrayOf(password, username)
    }
    // Gets called by the script if the requested site needs adult verification
    @SuppressWarnings("unused")
    fun is_adult() = isAdult
    // Gets called by the script if new chapters or new information to maximal-chapters is available
    @SuppressWarnings("unused")
    fun chapters(now: Int?, max: Int?) {
        thread {
            wakeLock.acquire(20000)
            Log.d(TAG, "chapters(${item}, $now, $max)")
            val prevProgress = item.progress ?: 0
            val prevMax = item.max ?: 0
            viewModel.setProgress(item, now ?: 0, max)

            // Update notification
            if (now != null && max != null && prevProgress <= now && prevMax <= max)
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
                val startTime = System.currentTimeMillis()
                val outputFile = originalFile ?: dstDir.createFile(Constants.MIME_EPUB, filename)
                // originalFile?.delete()
                // val outputFile = dstDir.createFile(Constants.MIME_EPUB, filename)
                try {
                    contentResolver.copyFile(
                        cacheFile.toUri(),
                        outputFile.uri
                    )
                    add_output("writing complete in %.2f sec.\n".format((System.currentTimeMillis() - startTime).toFloat() / 1000))
                } catch (e: java.lang.Exception) {
                    add_output("writing failed in %.2f sec.\n".format((System.currentTimeMillis() - startTime).toFloat() / 1000))
                    add_output(e.toString())
                }
            }
        }

        // "inform" user about finish
        viewModel.setFinished(item)
    }

    companion object {
        const val TAG = StoryDownloadService.TAG + "-helper" // Debug TAG
    }
}