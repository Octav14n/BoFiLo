package eu.schnuff.bofilo.download

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import eu.schnuff.bofilo.MainActivity
import eu.schnuff.bofilo.R
import eu.schnuff.bofilo.download.filewrapper.FileWrapper
import eu.schnuff.bofilo.persistence.storylist.StoryListItem
import eu.schnuff.bofilo.persistence.storylist.StoryListViewModel
import eu.schnuff.bofilo.settings.Settings
import java.io.File
import java.security.InvalidParameterException


class StoryDownloadService : IntentService("StoryDownloadService"), StoryDownloadListener {
    private val py = Python.getInstance()
    private val helper = py.getModule("helper")
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var viewModel: StoryListViewModel
    private lateinit var settings: Settings
    private val outputBuilder = StringBuilder()
    //private val doneDir = File(cacheDir.absolutePath + "/done")
    private var privateIniModified = 0L

    override fun onCreate() {
        super.onCreate()
        viewModel = StoryListViewModel(application)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BoFiLo::DownloadService").apply { setReferenceCounted(false) }
        settings = Settings(this)
        //if(!doneDir.isDirectory) doneDir.mkdir()
        py.getModule("os").callAttr("chdir", cacheDir.absolutePath)
        readPersonalIni()
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null)
            return
        // This is where the magic happens
        val url = intent.getStringExtra(PARAM_URL) ?: throw InvalidParameterException("Starting StoryDownloadService must provide url '$PARAM_URL'.")
        startForeground(NOTIFICATION_ID, createNotification(null))

        if (!viewModel.has(url)) {
            // exit if item has been canceled/finished in the meantime
            Log.d(TAG, "could not download $url because it is no longer in the Database")
            stopForeground(true)
            return
        }
        val item = viewModel.get(url)
        if (item.finished) {
            // exit if item has been canceled/finished in the meantime
            Log.d(TAG, "could not download $url because it is already finished")
            stopForeground(true)
            return
        }
        // Show notification and stop Display-off from killing the service
        wakeLock.acquire(20000)
        onStoryDownloadProgress(item)
        val downloadHelper = StoryDownloadHelper(
            arrayOf(this),
            helper,
            wakeLock,
            contentResolver,
            cacheDir,
            settings.dstDir?.run { FileWrapper.fromUri(applicationContext, this) },
            (settings.srcDir ?: settings.dstDir)?.run { FileWrapper.fromUri(applicationContext, this) },
            viewModel,
            outputBuilder,
            settings.isAdult,
            settings.saveCache,
            this,
            item
        )

        try {
            readPersonalIni()
            downloadHelper.run()
            outputBuilder.append(getString(R.string.console_finish_message).format(url))
        } catch (e: Throwable) {
            outputBuilder.append("Error downloading $url: ${e.message}:\n${e.localizedMessage}")
            Toast.makeText(baseContext, "Error downloading $url: ${e.message}:\n${e.localizedMessage}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        } finally {
            // Reset everything for next download
            ActiveItem = null
            outputBuilder.append("\n\n\n")
            wakeLock.release()
            wakeLock.acquire(250)
            stopForeground(true)
            // outputBuilder.clear()
            // viewModel?.setConsoleOutput("")
        }
    }

    override fun onStoryDownloadProgress(item: StoryListItem) {
        ActiveItem = item
        startForeground(NOTIFICATION_ID, createNotification(item))
    }

    override fun onDestroy() {
        super.onDestroy()
        ActiveItem = null
        wakeLock.release()
    }

    // Load personal.ini
    private fun readPersonalIni() {
        try {
            val personalIni = File(filesDir, "personal.ini")
            if (personalIni.exists() && personalIni.lastModified() > privateIniModified) {
                helper.callAttr("read_personal_ini", personalIni.absolutePath)
                // if read successfully we do not need to read it twice (except if it has changed)
                privateIniModified = personalIni.lastModified()
            }
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Reading personal ini resulted in error", e)
        }
    }

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
            // Set the intent which will fire when the user taps the notification
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
