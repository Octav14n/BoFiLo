package eu.schnuff.bofilo.download

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.chaquo.python.Python
import eu.schnuff.bofilo.Helpers.copyFile
import eu.schnuff.bofilo.MainActivity
import eu.schnuff.bofilo.R
import eu.schnuff.bofilo.download.filewrapper.FileWrapper
import java.io.File
import java.io.FileNotFoundException
import kotlin.concurrent.thread

private const val EXTRA_PARAM_URI = "eu.schnuff.bofilo.download.extra.uri"
private const val CHANNEL_ID = "StoryUnNewService"
private const val IS_FOREGROUND = false

class StoryUnNewService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        thread {
            super.onStartCommand(intent, flags, startId)
            val itemUri: Uri = intent?.getStringExtra(EXTRA_PARAM_URI)?.toUri()
                ?: throw IllegalStateException("No $EXTRA_PARAM_URI provided.")

            val original =
                FileWrapper.fromUri(this, itemUri)
            val originalName = original.name
            val cache = File(cacheDir, originalName)
            if (IS_FOREGROUND)
                startForeground(startId, createNotification(originalName, "started"))
            Log.d(this::class.simpleName, "loading file $originalName, uri: ${original.uri}.")
            if (!cache.isFile)
                cache.createNewFile()

            // call python methods to un-new the file.
            try {
                contentResolver.copyFile(original.uri, cache.toUri())

                val py = Python.getInstance()
                py.getModule("os").callAttr("chdir", cacheDir.absolutePath)
                val helper = py.getModule("helper")
                helper.callAttr("unnew", cache.absolutePath)
                //helper.close()

                // copy back
                contentResolver.copyFile(cache.toUri(), original.uri)
                if (IS_FOREGROUND)
                    startForeground(startId, createNotification(originalName, "finished"))
            } catch (e: Throwable) {
                Log.e(this::class.simpleName, "UnNew error", e)
                if (IS_FOREGROUND)
                    startForeground(startId, createNotification(originalName, "failed"))
                else
                    Toast.makeText(this, "Failed to UnNew '$originalName'", Toast.LENGTH_SHORT).show()
            }

            cache.delete()
            if (IS_FOREGROUND)
                stopForeground(false)
            stopSelf(startId)
        }
        return START_REDELIVER_INTENT
    }

    private fun createNotification(filename: String, status: String): Notification {
        createNotificationChannel()
        // Create an explicit intent for the Main Activity in your app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unnew)
            .setContentTitle(getString(R.string.unnew_foreground_name).format(filename, status))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent which will fire when the user taps the notification
            .setContentIntent(pendingIntent)

        return builder.build()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.unnew_channel_name)
            val descriptionText = getString(R.string.unnew_channel_description)
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
        fun start(context: Context, uri: Uri) {
            val intent = Intent(context, StoryUnNewService::class.java).apply {
                putExtra(EXTRA_PARAM_URI, uri.toString())
            }
            if (IS_FOREGROUND && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }
}
