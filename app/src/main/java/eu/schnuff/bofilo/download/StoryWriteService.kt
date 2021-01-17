package eu.schnuff.bofilo.download

import android.app.*
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import eu.schnuff.bofilo.Helpers.copyFile
import eu.schnuff.bofilo.MainActivity
import eu.schnuff.bofilo.R
import java.io.File
import kotlin.concurrent.thread

private const val EXTRA_PARAM_SRC_URI = "srcUri"
private const val EXTRA_PARAM_DST_URI = "dstUri"
private const val CHANNEL_ID = "StoryWriteService"

class StoryWriteService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: throw IllegalStateException("No intent provided.")
        thread {
            super.onStartCommand(intent, flags, startId)
            val srcUri = intent.getStringExtra(EXTRA_PARAM_SRC_URI)?.toUri() ?: throw IllegalStateException("No SRC uri provided.")
            val dstUri = intent.getStringExtra(EXTRA_PARAM_DST_URI)?.toUri() ?: throw IllegalStateException("No DST uri provided.")

            startForeground(startId, createNotification(dstUri))

            contentResolver.copyFile(srcUri, dstUri)
            if (srcUri.scheme == ContentResolver.SCHEME_FILE) {
                Log.d(this::class.simpleName, "Now deleting $srcUri .")
                srcUri.path?.let {
                    File(it).delete()
                }
            }

            stopForeground(false)
            stopSelf()
        }

        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    private fun createNotification(uri: Uri): Notification {
        createNotificationChannel()
        // Create an explicit intent for the Main Activity in your app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unnew)
            .setContentTitle(getString(R.string.story_write_foreground_name).format(uri))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent which will fire when the user taps the notification
            .setContentIntent(pendingIntent)

        return builder.build()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.story_write_channel_name)
            val descriptionText = getString(R.string.story_write_channel_description)
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
        fun start(context: Context, srcUri: Uri, dstUri: Uri) {
            val intent = Intent(context, StoryWriteService::class.java).apply {
                putExtra(EXTRA_PARAM_SRC_URI, srcUri.toString())
                putExtra(EXTRA_PARAM_DST_URI, dstUri.toString())
            }
            context.startForegroundService(intent)
        }
    }
}