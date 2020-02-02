package eu.schnuff.bofilo.download

import android.content.ContentResolver
import android.os.PowerManager
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.chaquo.python.PyObject
import eu.schnuff.bofilo.Constants
import eu.schnuff.bofilo.Helpers.copyFile
import eu.schnuff.bofilo.persistence.StoryListItem
import eu.schnuff.bofilo.persistence.StoryListViewModel
import java.io.File

class StoryDownloadHelper(
    private val progressListeners: Array<StoryDownloadListener>,
    private val helper: PyObject,
    private val wakeLock: PowerManager.WakeLock,
    private val contentResolver: ContentResolver,
    private val cacheDir: File,
    private val dstDir: DocumentFile?,
    private val srcDir: DocumentFile?,
    private val viewModel: StoryListViewModel,
    private val consoleOutput: StringBuilder,
    private val isAdult: Boolean,
    private val isSaveCache: Boolean,
    item: StoryListItem
) {
    var item = item
    private set
    private var fileName = "download_no_name.epub"
    set(value) {
        field = value
        cacheFile = File(cacheDir, fileName)
    }
    private var cacheFile = File(cacheDir, fileName)
    private var originalFile: DocumentFile? = null
    private var checkedForOriginalFile = false

    fun run() {
        // Tell the UI/db that we are starting to download
        viewModel.start(item)

        try {
            // Start the python-part of the service (including FanFicFare)
            helper.callAttr("start", this, item.url, isSaveCache)

            // Register the (hopefully) successful execution
            finished()
        } catch (e: Exception) {
            viewModel.setTitle(item, e.localizedMessage)
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
    fun output(output: String) {
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
    // Gets called by the script if the requested site needs login information
    @SuppressWarnings("unused")
    fun getLogin(passwordOnly: Boolean = false): Array<String> {
        // TODO implement user interaction to provide password or (username and password).
        output("\n! Site needs Login. This is not implemented at the moment. Use personal.ini instead.\n\n")
        val password = "secret"
        val username = "secret"
        return arrayOf(password, username)
    }
    // Gets called by the script if the requested site needs adult verification
    @SuppressWarnings("unused")
    fun getIsAdult() = isAdult
    // Gets called by the script if new chapters or new information to maximal-chapters is available
    @SuppressWarnings("unused")
    fun chapters(now: Int?, max: Int?) {
        wakeLock.acquire(20000)
        Log.d(TAG, "chapters(${item}, $now, $max)")
        val prevProgress = item.progress ?: 0
        val prevMax = item.max ?: 0
        viewModel.setProgress(item, now ?: 0, max)

        // Update notification
        if (now != null && max != null && prevProgress <= now && prevMax <= max)
            notifyProgress()
    }
    // Gets called by the script to announce the filename the epub will be saved to.
    @SuppressWarnings("unused")
    fun filename(name: String) {
        Log.d(TAG, "filename($name)")
        fileName = name
        if (!checkedForOriginalFile && srcDir != null && originalFile == null) {
            // if we have not provided a (update able) epub we will do so now
            // but only if a directory is specified in settings
            output("Starting search extern updateable epub\n")
            val startTime = System.currentTimeMillis()
            originalFile = srcDir.findFile(name)
            if (originalFile != null) {
                originalFile?.let {
                    // if the original file is available we will copy it to the cache directory
                    // because we use Storage-Access-Framework (uris begin with "content://") and python cant handle those.
                    Log.d(TAG, "\tnow copying file to cache.")
                    output("Copy extern epub file to cache.\n")
                    wakeLock.acquire(60000)
                    contentResolver.copyFile(it.uri, cacheFile.toUri())
                    cacheFile.setLastModified(it.lastModified())
                }
            }
            output("Search/Copy complete in %.2f sec.\n".format((System.currentTimeMillis() - startTime).toFloat() / 1000))
        }
        checkedForOriginalFile = true
    }
    // Gets called from script if a title is found
    @SuppressWarnings("unused")
    fun title(title: String) {
        viewModel.setTitle(item, title)
        notifyProgress()
    }

    private fun finished() {
        // copy data back to original file...
        if (dstDir != null) {
            val progress = item.progress
            val max = item.max
            if (max != null && progress != null && max > 0 && progress >= max) {
                // ... but only if we made progress (hopefully handles errors on the python side)
                contentResolver.copyFile(
                    cacheFile.toUri(),
                    originalFile?.uri ?: dstDir.createFile(Constants.MIME_EPUB, fileName)!!.uri
                )
            }
        }

        // "inform" user about finish
        output("\nfinished\n\n")
        viewModel.setFinished(item)
    }

    companion object {
        const val TAG = StoryDownloadService.TAG + "-helper" // Debug TAG
    }
}