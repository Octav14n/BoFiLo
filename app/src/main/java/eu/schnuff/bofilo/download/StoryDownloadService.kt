package eu.schnuff.bofilo.download

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import eu.schnuff.bofilo.CaptchaActivity
import eu.schnuff.bofilo.Helpers.copyFile
import eu.schnuff.bofilo.MainActivity
import eu.schnuff.bofilo.R
import eu.schnuff.bofilo.download.filewrapper.FileWrapper
import eu.schnuff.bofilo.persistence.storylist.StoryListItem
import eu.schnuff.bofilo.persistence.storylist.StoryListViewModel
import eu.schnuff.bofilo.settings.Settings
import org.apache.commons.text.StringEscapeUtils
import org.json.JSONObject
import org.mozilla.geckoview.*
import java.io.File
import java.security.InvalidParameterException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

const val SCRIPT = """
    document.documentElement.innerHTML
"""

class StoryDownloadService : IntentService("StoryDownloadService"), StoryDownloadListener,
    StoryDownloadHelper.FileInteraction, StoryDownloadWebRequest {
    private val py = Python.getInstance()
    private val helper = py.getModule("helper")
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var viewModel: StoryListViewModel
    private lateinit var settings: Settings
    private val outputBuilder = StringBuilder()

    //private val doneDir = File(cacheDir.absolutePath + "/done")
    val geckoSession: GeckoSession
        get() {
            if (!isWebViewInitialized)
                initWebView()
            return globalGeckoSession
        }
    private var privateIniModified = 0L
    val queue = LinkedBlockingQueue<String>(1)

    override fun onCreate() {
        super.onCreate()
        viewModel = StoryListViewModel(application)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BoFiLo::DownloadService"
        ).apply { setReferenceCounted(false) }
        settings = Settings(this)
        //if(!doneDir.isDirectory) doneDir.mkdir()
        py.getModule("os").callAttr("chdir", cacheDir.absolutePath)
        readPersonalIni()
    }

    override fun onBind(intent: Intent?): IBinder {
        return StoryDownloadBinder(this)
    }

    inner class StoryDownloadBinder(
        val Service: StoryDownloadService
    ) : Binder()

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null)
            return
        // This is where the magic happens
        val url = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: throw InvalidParameterException("Starting StoryDownloadService must provide url.")
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
            //contentResolver,
            cacheDir,
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
            outputBuilder.append(getString(R.string.console_finish_message).format(url))
        } catch (e: Throwable) {
            outputBuilder.append("Error downloading $url: ${e.message}:\n${e.localizedMessage}")
            Toast.makeText(
                baseContext,
                "Error downloading $url: ${e.message}:\n${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
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

    override fun fromUri(uri: Uri): FileWrapper = FileWrapper.fromUri(this, uri)
    override fun copyFile(src: Uri, dst: Uri, async: Boolean) {
        if (async)
            StoryWriteService.start(this, src, dst)
        else
            contentResolver.copyFile(src, dst)
    }

    override fun copyFile(item: StoryListItem, src: Uri, dstDir: Uri, mimeType: String, fileName: String) {
        StoryWriteService.start(this, item, src, dstDir, fileName, mimeType)
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
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(getString(R.string.foreground_name).format(item?.title ?: item?.url ?: "..."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // Set the intent which will fire when the user taps the notification
            .setContentIntent(pendingIntent)

        if (item != null) {
            builder.setContentText(
                getString(R.string.foreground_text).format(
                    item.progress ?: 0,
                    item.max ?: getString(R.string.download_default_max)
                )
            )
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
            val channelInteraction = NotificationChannel(
                CHANNEL_INTERACTION_ID,
                "Download needs interaction",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "A user interaction is required to continue the running download."
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(channelInteraction)
        }
    }

    companion object {
        //lateinit var geckoView: GeckoView
        lateinit var globalGeckoSession: GeckoSession
        lateinit var geckoRuntime: GeckoRuntime
        var isWebViewInitialized = false

        const val CHANNEL_ID = "StoryDownloadProgress"
        const val CHANNEL_INTERACTION_ID = "StoryDownloadInteraction"
        const val NOTIFICATION_ID = 3001
        var ActiveItem: StoryListItem? = null
            private set
        const val TAG = "download" // Debug TAG
    }

    fun initWebView() {
        //geckoView = GeckoView(this)
        globalGeckoSession = GeckoSession()
        globalGeckoSession.settings.apply {
            allowJavascript = true
            userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            val ua = Settings(this@StoryDownloadService).webViewUserAgent
            if (ua != "none")
                userAgentOverride = ua
        }
        globalGeckoSession.userAgent.accept {
            Log.i("UserAgent", it ?: "N/A")
        }
        geckoRuntime = GeckoRuntime.create(this)

        val messageDelegate = object : WebExtension.MessageDelegate {
            override fun onMessage(
                nativeApp: String,
                message: Any,
                sender: WebExtension.MessageSender
            ): GeckoResult<Any>? {
                val json = message as JSONObject
                if (!json.has("type"))
                    return null

                when (json.getString("type")) {
                    "webpage" -> queue.put(json.getString("innerHTML"))
                }

                return null
            }
        }

        geckoRuntime.apply {
            webExtensionController
                .ensureBuiltIn("resource://android/assets/webextension/", "webextension@bofilo.schnuff.eu")
                .accept(
                    { extension ->
                        globalGeckoSession.webExtensionController.setMessageDelegate(extension!!, messageDelegate, "browser")
                    },
                    { e -> Log.e("MessageDelegate", "Error registering WebExtension", e) }
                )
        }
        globalGeckoSession.open(geckoRuntime)

        isWebViewInitialized = true
        //geckoView.setSession(globalGeckoSession)
    }

    override fun webRequest(method: String, url: String): String {
        //val thread = HandlerThread("webRequestHandler", THREAD_PRIORITY_BACKGROUND)
        //thread.start()
        queue.clear()

        val handler = object : Handler(mainLooper) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                if (!isWebViewInitialized)
                    initWebView()

                when (method) {
                    "GET" -> {
                        globalGeckoSession.loadUri(url)
                    }
                }
            }
        }

        handler.sendEmptyMessage(0)


        /*while (true) {
            val p = queueMain.poll(500, TimeUnit.MICROSECONDS)
            if (p != null) {
                //thread.quit()
                return p
            }
        }*/
        while (true) {
            val ret = queue.poll(120, TimeUnit.SECONDS)
            if (ret.isNullOrEmpty()) {
                return ""
            } else if (ret.contains("| FanFiction</title>") || ret.contains("<p>New chapter/story can take up to 15 minutes to show up.</p>")) {
                // handler.sendEmptyMessage(1)
                return ret
            } else if (ret.contains("<meta http-equiv=\"refresh\"")) {
                // Do nothing, site will reload by itself.
            } else if (ret.contains("<meta name=\"captcha-bypass\" id=\"captcha-bypass\">")) {
                val intent = PendingIntent.getActivity(
                    this, 0, Intent(
                        this, CaptchaActivity::class.java
                    ).apply {
                        putExtra(CaptchaActivity.INTENT_EXTRA_URL, url)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }, PendingIntent.FLAG_UPDATE_CURRENT
                )

                startForeground(
                    NOTIFICATION_ID,
                    NotificationCompat.Builder(this, CHANNEL_INTERACTION_ID)
                        .setContentTitle("Captcha")
                        .setSmallIcon(R.drawable.ic_notification_download)
                        .apply {
                            foregroundServiceBehavior = Notification.FOREGROUND_SERVICE_IMMEDIATE
                        }
                        .setContentIntent(intent)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .build()
                )
            } else {
                Log.d(this::class.simpleName, "Unhandled HTML\n\n $ret")
            }
        }
    }
}
