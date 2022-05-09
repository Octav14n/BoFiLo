package eu.schnuff.bofilo.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.*
import com.chaquo.python.Python
import eu.schnuff.bofilo.Helpers.copyFile
import eu.schnuff.bofilo.MainActivity
import eu.schnuff.bofilo.R
import eu.schnuff.bofilo.download.filewrapper.FileWrapper
import java.io.File

private const val EXTRA_PARAM_URI = "eu.schnuff.bofilo.download.extra.uri"
private const val WORK_NAME = "StoryUnNewWork"
private const val CHANNEL_ID = "StoryUnNewService"
private const val NOTIFICATION_ID = 3001
private const val IS_FOREGROUND = false

class StoryUnNewService(
    private val context: Context,
    private val workerParameters: WorkerParameters
    ) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        val itemUri = workerParameters.inputData.getString(EXTRA_PARAM_URI)?.toUri()

        if (itemUri != null) {
            try {
                start(itemUri)
            } catch (e: java.lang.Exception) {
                return Result.retry()
            }
        }

        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(workerParameters.inputData.getString(EXTRA_PARAM_URI) ?: "", "started")
    }

    private fun start(itemUri: Uri) {
        val original = FileWrapper.fromUri(context, itemUri)
        val originalName = original.name
        val cache = File(context.cacheDir, originalName)
        this.setForegroundAsync(createForegroundInfo(originalName, "started"))
        Log.d(this::class.simpleName, "loading file $originalName, uri: ${original.uri}.")
        if (!cache.isFile)
            cache.createNewFile()

        // call python methods to un-new the file.
        try {
            context.contentResolver.copyFile(original.uri, cache.toUri())

            val py = Python.getInstance()
            py.getModule("os").callAttr("chdir", context.cacheDir.absolutePath)
            val helper = py.getModule("helper")
            helper.callAttr("unnew", cache.absolutePath)
            //helper.close()

            // copy back
            context.contentResolver.copyFile(cache.toUri(), original.uri)
            this.setForegroundAsync(createForegroundInfo(originalName, "finished", false))
        } catch (e: Throwable) {
            Log.e(this::class.simpleName, "UnNew error", e)
            this.setForegroundAsync(createForegroundInfo(originalName, "failed", false))
        }

        cache.delete()
    }

    private fun createForegroundInfo(filename: String, status: String, isOngoing: Boolean = true): ForegroundInfo {
        createNotificationChannel()
        // Create an explicit intent for the Main Activity in your app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent =
            PendingIntent.getActivity(context, 0, intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE
                } else 0
            )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_unnew)
            .setContentTitle(context.getString(R.string.unnew_foreground_name).format(filename, status))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(isOngoing)
            // Set the intent which will fire when the user taps the notification
            .setContentIntent(pendingIntent)

        return ForegroundInfo(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.unnew_channel_name)
            val descriptionText = context.getString(R.string.unnew_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        fun start(context: Context, uri: Uri) {
            val manager = WorkManager.getInstance(context)
            manager.beginUniqueWork(this::class.qualifiedName!!, ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<StoryUnNewService>()
                    .setInputData(workDataOf(
                        EXTRA_PARAM_URI to uri.toString()
                    ))
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
                ).enqueue()
        }
    }
}
