package eu.schnuff.bofilo

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.chaquo.python.PyException
import com.chaquo.python.Python
import eu.schnuff.bofilo.persistence.StoryListItem
import eu.schnuff.bofilo.persistence.StoryListViewModel
import java.io.File
import eu.schnuff.bofilo.copyFile
import java.lang.Exception


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
        // This is where the magic happens
        val url = intent.getStringExtra(PARAM_URL)!!

        ActiveItem = viewModel!!.get(url)
        if (ActiveItem == null || ActiveItem?.finished == true) {
            // exit if item is canceled/finished in the meantime
            return
        }
        // Show notification and stop Display-off from killing the service
        wakeLock!!.acquire(20000)
        startForeground(NOTIFICATION_ID, createNotification())

        // Tell the UI/db that we are starting to download
        viewModel!!.start(ActiveItem!!)
        val saveCache = defaultSharedPreference.getBoolean(Constants.PREF_SAVE_CACHE, false)

        try {
            // Load personal.ini
            val personalini = File(filesDir, "personal.ini")
            if (personalini.exists()) {
                helper.callAttr("read_personal_ini", personalini.absolutePath)
            }

            // Start the python-part of the service (including FanFicFare)
            helper.callAttr("start", this, url, saveCache)

            // Register the (hopefully) successful execution
            if (ActiveItem != null)
                this.finished()

        } catch (e: Exception) {
            Toast.makeText(baseContext, "Error downloading $url: ${e.message}:\n${e.localizedMessage}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            ActiveItem?.run {
                viewModel?.setTitle(this, e.localizedMessage)
            }
        } finally {
            // Reset everything for next download
            ActiveItem = null
            cacheFile = null
            wakeLock!!.release()
            wakeLock!!.acquire(250)
            outputBuilder.append(getString(R.string.console_finish_message).format(url))
            outputBuilder.append("\n\n\n")
            stopForeground(true)
            // outputBuilder.clear()
            // viewModel?.setConsoleOutput("")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
    }

    // Gets called by the script, everything in here will be displayed in the console
    fun output(output: String) {
        outputBuilder.append(output)
        Log.d(TAG, "console: $output")
        viewModel?.setConsoleOutput(outputBuilder.toString())
    }
    // Gets called by the script, the sanitized version of the url gets passed
    fun start(url: String) {
        Log.d(TAG, "start($ActiveItem --> $url)")
        viewModel!!.setUrl(ActiveItem!!, url)
    }
    // Gets called by the script if the requested site needs login information
    fun getLogin(passwordOnly: Boolean = false): Array<String> {
        // TODO implement user interaction to provide password or (username and password).
        output("\n!!! Site needs Login. This is not implemented at the moment. Use personal.ini instead.\n\n")
        val password = "secret"
        val username = "secret"
        return arrayOf(password, username)
    }
    // Gets called by the script if the requested site needs adult verification
    fun getIsAdult(): Boolean {
        return defaultSharedPreference.getBoolean(Constants.PREF_IS_ADULT, false)
    }
    // Gets called by the script if new chapters or new information to maximal-chapters is available
    fun chapters(now: Int?, max: Int?) {
        wakeLock!!.acquire(20000)
        Log.d(TAG, "chapters($ActiveItem, $now, $max)")
        val prevProgress = ActiveItem!!.progress ?: 0
        val prevMax = ActiveItem!!.max ?: 0
        viewModel!!.setProgress(ActiveItem!!, now ?: 0, max)

        // Update notification
        if (now != null && max != null && prevProgress <= now && prevMax <= max)
            startForeground(NOTIFICATION_ID, createNotification())
    }
    // Gets called by the script to announce the filename the epub will be saved to.
    fun filename(name: String) {
        Log.d(TAG, "filename($name)")
        fileName = name
        cacheFile = File(cacheDir, name).toUri()
        if (originalFile == null) {
            // if we have not provided a (update able) epub we will do so now
            if (isDir) {
                // but only if a directory is specified in settings
                val dir = getDir()
                originalFile = dir.findFile(name)?.uri
                originalFile?.let {
                    // if the original file is available we will copy it to the cache directory
                    // because we use Storage-Access-Framework (uris begin with "content://") and python cant handle those.
                    Log.d(TAG, "\tnow copying file to cache.")
                    output("Copy extern epub file to cache.\n")
                    wakeLock!!.acquire(60000)
                    contentResolver.copyFile(it, cacheFile!!)
                } ?: output("File not found in folder.\n")
            }
        }
    }
    // Gets called from script if a title is found
    fun title(title: String) {
        viewModel!!.setTitle(ActiveItem!!, title)
        startForeground(NOTIFICATION_ID, createNotification())
    }
    private fun finished() {
        // copy data back to original file...
        cacheFile?.let {
            if (isDir) {
                if (ActiveItem!!.max != null && ActiveItem!!.progress != null && ActiveItem!!.max!! > 0 && ActiveItem!!.progress!! >= ActiveItem!!.max!!) {
                    // ... but only if we made progress (hopefully handles errors on the python side)
                    contentResolver.copyFile(
                        it,
                        originalFile ?: getDir().createFile(Constants.MIME_EPUB, fileName)!!.uri
                    )
                }
                // delete the file in the cache directory
                it.toFile().delete()
            }
        }

        // "inform" user about finish
        viewModel!!.setFinished(ActiveItem!!)
    }

    // provide shortcut
    private val defaultSharedPreference
        get() = PreferenceManager.getDefaultSharedPreferences(applicationContext)

    // Returns weather the default directory is configured or not
    private val isDir
        get() = defaultSharedPreference.contains(Constants.PREF_DEFAULT_DIRECTORY)
    private fun getDir() = DocumentFile.fromTreeUri(applicationContext,
            defaultSharedPreference.getString(Constants.PREF_DEFAULT_DIRECTORY, cacheDir.absolutePath)?.toUri() ?: cacheDir.toUri())!!

    private fun createNotification(): Notification {
        createNotificationChannel()
        // Create an explicit intent for the Main Activity in your app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(getString(R.string.foreground_name).format(ActiveItem?.title ?: ActiveItem?.url ?: "..."))
            .setContentText(getString(R.string.foreground_text).format(ActiveItem?.progress ?: 0, ActiveItem?.max ?: getString(R.string.download_default_max)))
            .setProgress(ActiveItem?.max ?: 1, ActiveItem?.progress ?: 0, (ActiveItem?.progress == null || ActiveItem?.max == 0))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent that will fire when the user taps the notification
            .setContentIntent(pendingIntent)

        return builder.build()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


    companion object {
        const val PARAM_URL = "url"
        const val CHANNEL_ID = "StoryDownloadProgress"
        const val NOTIFICATION_ID = 3001
        var ActiveItem: StoryListItem? = null
        private set
        const val TAG = "download" // Debug TAG
    }
}
