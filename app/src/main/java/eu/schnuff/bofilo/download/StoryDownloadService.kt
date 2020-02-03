package eu.schnuff.bofilo.download

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.chaquo.python.Python
import eu.schnuff.bofilo.Constants
import eu.schnuff.bofilo.MainActivity
import eu.schnuff.bofilo.R
import eu.schnuff.bofilo.download.filewrapper.FileWrapper
import eu.schnuff.bofilo.persistence.StoryListItem
import eu.schnuff.bofilo.persistence.StoryListViewModel
import java.io.File


class StoryDownloadService : IntentService("StoryDownloadService"), StoryDownloadListener {
    private val py = Python.getInstance()
    private val helper = py.getModule("helper")
    private var wakeLock: PowerManager.WakeLock? = null
    private var viewModel: StoryListViewModel? = null
    private val outputBuilder = StringBuilder()
    private var privateIniModified = 0L

    override fun onCreate() {
        super.onCreate()
        viewModel = StoryListViewModel(application)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BoFiLo::DownloadService").apply { setReferenceCounted(false) }
        py.getModule("os").callAttr("chdir", cacheDir.absolutePath)
        readPersonalIni()
    }

    override fun onHandleIntent(intent: Intent) {
        // This is where the magic happens
        val url = intent.getStringExtra(PARAM_URL)!!
        startForeground(StoryDownloadService.NOTIFICATION_ID, createNotification(null))

        if (!viewModel!!.has(url)) {
            // exit if item is canceled/finished in the meantime
            stopForeground(true)
            return
        }
        val item = viewModel!!.get(url)
        if (item.finished) {
            // exit if item is canceled/finished in the meantime
            stopForeground(true)
            return
        }
        // Show notification and stop Display-off from killing the service
        wakeLock!!.acquire(20000)
        onStoryDownloadProgress(item)
        val downloadHelper = StoryDownloadHelper(
            arrayOf(this),
            helper,
            wakeLock!!,
            contentResolver,
            cacheDir,
            if (isDstDir()) getDstDir() else null,
            if (isSrcDir()) getSrcDir() else if (isDstDir()) getDstDir() else null,
            viewModel!!,
            outputBuilder,
            defaultSharedPreference.getBoolean(Constants.PREF_IS_ADULT, false),
            defaultSharedPreference.getBoolean(Constants.PREF_SAVE_CACHE, false),
            item
        )

        try {
            readPersonalIni()
            downloadHelper.run()
        } catch (e: Exception) {
            Toast.makeText(baseContext, "Error downloading $url: ${e.message}:\n${e.localizedMessage}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        } finally {
            // Reset everything for next download
            ActiveItem = null
            outputBuilder.append(getString(R.string.console_finish_message).format(url))
            outputBuilder.append("\n\n\n")
            wakeLock!!.release()
            wakeLock!!.acquire(250)
            stopForeground(true)
            // outputBuilder.clear()
            // viewModel?.setConsoleOutput("")
        }
    }

    override fun onStoryDownloadProgress(item: StoryListItem) {
        ActiveItem = item
        startForeground(StoryDownloadService.NOTIFICATION_ID, createNotification(item))
    }

    override fun onDestroy() {
        super.onDestroy()
        ActiveItem = null
        wakeLock?.release()
    }

    // Load personal.ini
    private fun readPersonalIni() {
        try {
            val personalini = File(filesDir, "personal.ini")
            if (personalini.exists() && personalini.lastModified() > privateIniModified) {
                helper.callAttr("read_personal_ini", personalini.absolutePath)
                // if read successfully we dont need to read it twice (except if it has changed)
                privateIniModified = personalini.lastModified()
            }
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Reading personal ini resulted in error", e)
        }
    }

    // provide shortcut
    private val defaultSharedPreference
        get() = PreferenceManager.getDefaultSharedPreferences(applicationContext)

    // Returns weather the default directory is configured or not
    private fun isDstDir() = defaultSharedPreference.contains(Constants.PREF_DEFAULT_DIRECTORY)
    private fun getDstDir() = FileWrapper.fromUri(applicationContext,
            defaultSharedPreference.getString(Constants.PREF_DEFAULT_DIRECTORY, cacheDir.absolutePath)?.toUri() ?: cacheDir.toUri())
    // Returns weather the src dir is configured
    private fun isSrcDir() = defaultSharedPreference.contains(Constants.PREF_DEFAULT_DIRECTORY) && defaultSharedPreference.getBoolean(Constants.PREF_DEFAULT_SRC_DIRECTORY_ENABLED, false)
    private fun getSrcDir() = FileWrapper.fromUri(applicationContext,
        defaultSharedPreference.getString(Constants.PREF_DEFAULT_SRC_DIRECTORY, cacheDir.absolutePath)?.toUri() ?: getDstDir().uri)

    private fun createNotification(item: StoryListItem?): Notification {
        createNotificationChannel()
        // Create an explicit intent for the Main Activity in your app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(getString(R.string.foreground_name).format(item?.title ?: item?.url ?: "..."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent that will fire when the user taps the notification
            .setContentIntent(pendingIntent)

        if (item != null) {
            builder.setContentText(getString(R.string.foreground_text).format(
                       item.progress ?: 0,
                       item.max ?: getString(R.string.download_default_max)
                   ))
                   .setProgress(
                       item.max ?: 1,
                       item.progress ?: 0,
                       (item.progress == null || item.max == 0)
                   )
        }

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
