package eu.schnuff.bofilo.download

import android.app.*
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import eu.schnuff.bofilo.Helpers.copyFile
import eu.schnuff.bofilo.MainActivity
import eu.schnuff.bofilo.R
import eu.schnuff.bofilo.download.filewrapper.FileWrapper
import eu.schnuff.bofilo.persistence.storylist.StoryListItem
import eu.schnuff.bofilo.persistence.storylist.StoryListViewModel
import java.io.File
import kotlin.concurrent.thread

private const val EXTRA_PARAM_SRC_URI = "srcUri"
private const val EXTRA_PARAM_DST_URI = "dstUri"
private const val EXTRA_PARAM_DST_DIR_URI = "dstDirUri"
private const val EXTRA_PARAM_DST_FILE_NAME = "dstFileName"
private const val EXTRA_PARAM_DST_MIME_TYPE = "dstMimeType"
private const val EXTRA_PARAM_UPDATE_ITEM_URL = "updateItemUrl"
private const val CHANNEL_ID = "StoryWriteService"

class StoryWriteService : Service() {
    private lateinit var viewModel: StoryListViewModel

    override fun onCreate() {
        super.onCreate()
        viewModel = StoryListViewModel(application)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: throw IllegalStateException("No intent provided.")
        thread {
            super.onStartCommand(intent, flags, startId)
            val srcUri = intent.getStringExtra(EXTRA_PARAM_SRC_URI)?.toUri() ?: throw IllegalStateException("No SRC uri provided.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(startId, createNotification(srcUri), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(startId, createNotification(srcUri))
            }

            val dstUri = if (intent.hasExtra(EXTRA_PARAM_DST_URI))
                 intent.getStringExtra(EXTRA_PARAM_DST_URI)?.toUri() ?: throw IllegalStateException("No destination uri provided.")
            else {
                val dstDirUri = intent.getStringExtra(EXTRA_PARAM_DST_DIR_URI)?.toUri() ?: throw IllegalStateException("No destination dir uri provided.")
                val fileName = intent.getStringExtra(EXTRA_PARAM_DST_FILE_NAME) ?: throw IllegalStateException("No file name provided.")
                val mimeType = intent.getStringExtra(EXTRA_PARAM_DST_MIME_TYPE) ?: throw IllegalStateException("No Mime-Type provided.")
                val df = FileWrapper.fromUri(this, dstDirUri)

                Log.d(this::class.simpleName, "Now creating file: uri: %s, filename: %s, mime-type: %s".format(df.uri, fileName, mimeType))

                df.createFile(mimeType, fileName).uri
            }
            if (intent.hasExtra(EXTRA_PARAM_UPDATE_ITEM_URL)) {
                val url = intent.getStringExtra(EXTRA_PARAM_UPDATE_ITEM_URL) ?: throw IllegalStateException("Something went wrong with the url.")
                val item = viewModel.get(url)
                viewModel.setUri(item, dstUri)
            }

            startForeground(startId, createNotification(srcUri))
            Log.d(this::class.simpleName, "Now writing to %s".format(dstUri))

            contentResolver.copyFile(srcUri, dstUri)
            if (srcUri.scheme == ContentResolver.SCHEME_FILE) {
                Log.d(this::class.simpleName, "Now deleting $srcUri .")
                srcUri.path?.let {
                    File(it).delete()
                }
            }

            stopForeground(true)
            stopSelf(startId)
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
            .setContentTitle(getString(R.string.story_write_foreground_name).format(uri.lastPathSegment))
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun start(context: Context, item: StoryListItem, srcUri: Uri, dstDirUri: Uri, dstFileName: String, dstMimeType: String) {
            val intent = Intent(context, StoryWriteService::class.java).apply {
                putExtra(EXTRA_PARAM_UPDATE_ITEM_URL, item.url)
                putExtra(EXTRA_PARAM_SRC_URI, srcUri.toString())
                putExtra(EXTRA_PARAM_DST_DIR_URI, dstDirUri.toString())
                putExtra(EXTRA_PARAM_DST_FILE_NAME, dstFileName)
                putExtra(EXTRA_PARAM_DST_MIME_TYPE, dstMimeType)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}