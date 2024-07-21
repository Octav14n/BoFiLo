package eu.schnuff.bofilo.download

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.chaquo.python.Python
import eu.schnuff.bofilo.Helpers.copyFile
import eu.schnuff.bofilo.MainActivity
import eu.schnuff.bofilo.R
import eu.schnuff.bofilo.download.filewrapper.FileWrapper
import eu.schnuff.bofilo.persistence.storylist.StoryListItem
import eu.schnuff.bofilo.persistence.storylist.StoryListViewModel
import eu.schnuff.bofilo.settings.Settings
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.InvalidParameterException
import kotlin.concurrent.thread


class StoryDownloadService(
    private val context: Context,
    private val workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters), StoryDownloadListener,
    StoryDownloadHelper.FileInteraction, StoryDownloadWebRequest {
    private val py = Python.getInstance()
    private val helper = py.getModule("helper")
    private val wakeLock: PowerManager.WakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
        PowerManager.PARTIAL_WAKE_LOCK,
        "BoFiLo::DownloadService"
    ).apply { setReferenceCounted(false) }
    private val viewModel: StoryListViewModel = StoryListViewModel(context.applicationContext as Application)
    private val settings: Settings = Settings(context)
    private val outputBuilder = StringBuilder()
    private var item: StoryListItem? = null

    private var privateIniModified = 0L

    init {
        py.getModule("os").callAttr("chdir", context.cacheDir.absolutePath)
        readPersonalIni()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(item)
    }

    override suspend fun doWork(): Result {
        while (true) {
            val items = viewModel.getUnfinished()
            if (items.isEmpty())
                break

            for (item in items) {
                if (item.finished)
                    continue
                this.item = item
                run(item)
            }
        }

        return Result.success()
    }

    private fun run(item: StoryListItem): Result {
        // This is where the magic happens
        Log.d(TAG, "worker started with url: '${item.url}'.")
        runBlocking {
            setForeground(createForegroundInfo(null))
        }

        // Show notification and stop Display-off from killing the service
        wakeLock.acquire(30000)
        onStoryDownloadProgress(item)
        val downloadHelper = StoryDownloadHelper(
            arrayOf(this),
            helper,
            wakeLock,
            //contentResolver,
            context.cacheDir,
            settings.dstDir,
            (settings.srcDir ?: settings.dstDir)?.run { FileWrapper.fromUri(applicationContext, this) },
            viewModel,
            outputBuilder,
            settings.isAdult,
            settings.saveCache,
            this,
            this,
            item
        )

        try {
            readPersonalIni()
            downloadHelper.run()
            outputBuilder.append(context.getString(R.string.console_finish_message).format(item.url))
        } catch (e: Throwable) {
            outputBuilder.append("Error downloading ${item.url}: ${e.message}:\n${e.localizedMessage}")
            Toast.makeText(
                context,
                "Error downloading ${item.url}: ${e.message}:\n${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
            e.printStackTrace()
        } finally {
            // Reset everything for next download
            ActiveItem = null
            outputBuilder.append("\n\n\n")
            wakeLock.release()
            setForegroundAsync(createForegroundInfo(item, false))
            // outputBuilder.clear()
            // viewModel?.setConsoleOutput("")
        }

        return Result.success()
    }


    override fun fromUri(uri: Uri): FileWrapper = FileWrapper.fromUri(context, uri)
    override fun copyFile(src: Uri, dst: Uri, async: Boolean) {
        if (async)
            StoryWriteService.start(context, src, dst)
        else
            context.contentResolver.copyFile(src, dst)
    }

    override fun copyFile(item: StoryListItem, src: Uri, dstDir: Uri, mimeType: String, fileName: String) {
        StoryWriteService.start(context, item, src, dstDir, fileName, mimeType)
    }

    override fun onStoryDownloadProgress(item: StoryListItem) {
        ActiveItem = item
        setForegroundAsync(createForegroundInfo(item))
    }

    // Load personal.ini
    private fun readPersonalIni() {
        try {
            val personalIni = File(context.filesDir, "personal.ini")
            if (personalIni.exists() && personalIni.lastModified() > privateIniModified) {
                helper.callAttr("read_personal_ini", personalIni.absolutePath)
                // if read successfully we do not need to read it twice (except if it has changed)
                privateIniModified = personalIni.lastModified()
            }
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Reading personal ini resulted in error", e)
        }
    }

    private fun createForegroundInfo(item: StoryListItem?, isOngoing: Boolean = true): ForegroundInfo {
        createNotificationChannel()
        // Create an explicit intent for the Main Activity in your app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(context.getString(R.string.foreground_name).format(item?.title ?: item?.url ?: "..."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(isOngoing)
            // Set the intent which will fire when the user taps the notification
            .setContentIntent(pendingIntent)

        if (item != null) {
            builder.setContentText(
                context.getString(R.string.foreground_text).format(
                    item.progress ?: 0,
                    item.max ?: context.getString(R.string.download_default_max)
                )
            )
                .setProgress(
                    item.max ?: 1,
                    item.progress ?: 0,
                    (item.progress == null || item.max == 0)
                )
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ForegroundInfo(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else
            ForegroundInfo(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager


            val name = context.getString(R.string.channel_name)
            val descriptionText = context.getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "StoryDownloadProgress"
        const val NOTIFICATION_ID = 3003
        var ActiveItem: StoryListItem? = null
            private set
        const val TAG = "download" // Debug TAG

        fun start(context: Context, url: String, forceDownload: Boolean) {
            thread {
                val viewModel = StoryListViewModel(context.applicationContext as Application)
                val item = viewModel.get(url) ?: viewModel.add(url)
                if (item.finished) {
                    viewModel.setFinished(item, false)
                }
                viewModel.setForcedDownload(item, forceDownload)

                val manager = WorkManager.getInstance(context)
                manager.beginUniqueWork(
                    this::class.qualifiedName!!, ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<StoryDownloadService>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                ).enqueue()
            }
        }
    }

    override fun webRequest(method: String, url: String): String {
        return StoryDownloadGeckoHelper.webRequest(context, method, url)
    }


}
