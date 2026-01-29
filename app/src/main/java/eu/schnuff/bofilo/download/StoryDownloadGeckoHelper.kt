package eu.schnuff.bofilo.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Message
import android.provider.SyncStateContract
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.PermissionChecker
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.normal.TedPermission
import eu.schnuff.bofilo.CaptchaActivity
import eu.schnuff.bofilo.Constants
import eu.schnuff.bofilo.Helpers
import eu.schnuff.bofilo.R
import eu.schnuff.bofilo.settings.Settings
import org.json.JSONObject
import org.mozilla.geckoview.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit


object StoryDownloadGeckoHelper {
    private const val CHANNEL_INTERACTION_ID = "StoryDownloadInteraction"

    lateinit var globalGeckoSession: GeckoSession
    private lateinit var geckoRuntime: GeckoRuntime
    private var isWebViewRuntimeInitialized = false
    private var isWebViewInitialized = false
    private val queue = LinkedBlockingQueue<String>(1)
    private var reInitStoppingRequest: Boolean = false

    fun getSession(context: Context): GeckoSession {
        if (!isWebViewInitialized)
            initWebView(context)
        return globalGeckoSession
    }

    fun reInitWebView() {
        isWebViewInitialized = false
    }

    private fun initWebViewRuntime(context: Context) {
        if (isWebViewRuntimeInitialized)
            return

        geckoRuntime = GeckoRuntime.create(context)

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
                    "captcha" -> createNotification(context, "")
                }

                return null
            }
        }

        geckoRuntime.apply {
            webExtensionController
                .ensureBuiltIn("resource://android/assets/webextension/", "webextension@bofilo.schnuff.eu")
                .accept(
                    { extension ->
                        extension?.run {
                            Handler(context.mainLooper).post {
                                globalGeckoSession.webExtensionController.setMessageDelegate(
                                    this,
                                    messageDelegate,
                                    "browser"
                                )
                            }
                        }
                    },
                    { e -> Log.e("MessageDelegate", "Error registering WebExtension", e) }
                )
        }

        isWebViewRuntimeInitialized = true
    }

    fun initWebView(context: Context) {
        globalGeckoSession = GeckoSession()
        globalGeckoSession.settings.apply {
            allowJavascript = true
            userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            val ua = Settings(context).webViewUserAgent
            if (ua != "none")
                userAgentOverride = ua
        }
        globalGeckoSession.userAgent.accept {
            Log.i("UserAgent", it ?: "N/A")
        }

        initWebViewRuntime(context)
        globalGeckoSession.open(geckoRuntime)

        isWebViewInitialized = true
        reInitStoppingRequest = true
    }

    private fun webRequestGet(context: Context, method: String, url: String) {
        val handler = object : Handler(context.mainLooper) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                when (method) {
                    "GET" -> {
                        getSession(context).loadUri(url)
                    }
                }
            }
        }

        handler.sendEmptyMessage(0)
    }

    fun webRequest(context: Context, method: String, url: String): String {
        queue.clear()
        webRequestGet(context, method, url)

        while (true) {
            val ret = queue.poll(500, TimeUnit.MILLISECONDS)
            if (reInitStoppingRequest) {
                webRequestGet(context, method, url)
                reInitStoppingRequest = false
                continue
            }

            if (ret.isNullOrEmpty())
                continue

            if (
                ret.contains("| FanFiction</title>") ||
                ret.contains("<p>New chapter/story can take up to 15 minutes to show up.</p>") ||
                ret.contains("<img alt=\"Archive of Our Own\"")) {
                // handler.sendEmptyMessage(1)
                NotificationManagerCompat.from(context).cancel(Constants.NOTIFICATION_ID_CAPTCHA)
                return ret
            //} else if (ret.contains("<meta http-equiv=\"refresh\"")) {
            //    // Do nothing, site will reload by itself.
            } else if (ret.contains("<body class=\"no-js\">")) {
                createNotification(context, url)
            } else {
                Log.d(this::class.simpleName, "Unhandled HTML\n\n $ret")
            }
        }
    }

    private fun createNotification(context: Context, url: String) {
        createNotificationChannel(context)

        val intent = PendingIntent.getActivity(
            context, 0, Intent(
                context, CaptchaActivity::class.java
            ).apply {
                putExtra(CaptchaActivity.INTENT_EXTRA_URL, url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK //or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_INTERACTION_ID)
            .setContentTitle("Captcha")
            .setSmallIcon(R.drawable.ic_notification_download)
            .setOngoing(true)
            .apply {
                foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            }
            .setContentIntent(intent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()

        if (ActivityCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(Constants.NOTIFICATION_ID_CAPTCHA, notification)
        } else {
            Log.d("SDGH", "Got Captcha but Permission POST_NOTIFICATIONS denied.")
        }
    }

    @SuppressLint("WrongConstant")
    private fun createNotificationChannel(context: Context) {
        val channelInteraction =
        NotificationChannelCompat.Builder(CHANNEL_INTERACTION_ID, NotificationManager.IMPORTANCE_MAX)
            .setName("Download needs interaction")
            .setDescription("A user interaction is required to continue the running download.")
            .setShowBadge(true)
            .setVibrationEnabled(true)
            .build()

        NotificationManagerCompat.from(context).createNotificationChannel(channelInteraction)
    }
}