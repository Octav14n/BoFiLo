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
    private var isWebViewInitialized = false
    private val queue = LinkedBlockingQueue<String>(1)

    fun getSession(context: Context): GeckoSession {
        if (!isWebViewInitialized)
            initWebView(context)
        return globalGeckoSession
    }

    fun initWebView(context: Context) {
        //geckoView = GeckoView(this)
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
                }

                return null
            }
        }

        geckoRuntime.apply {
            webExtensionController
                .ensureBuiltIn("resource://android/assets/webextension/", "webextension@bofilo.schnuff.eu")
                .accept(
                    { extension ->
                        Handler(context.mainLooper).post {
                            globalGeckoSession.webExtensionController.setMessageDelegate(
                                extension!!,
                                messageDelegate,
                                "browser"
                            )
                        }
                    },
                    { e -> Log.e("MessageDelegate", "Error registering WebExtension", e) }
                )
        }
        globalGeckoSession.open(geckoRuntime)

        isWebViewInitialized = true
        //geckoView.setSession(globalGeckoSession)
    }

    fun webRequest(context: Context, method: String, url: String): String {
        //val thread = HandlerThread("webRequestHandler", THREAD_PRIORITY_BACKGROUND)
        //thread.start()
        queue.clear()

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
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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

        with(NotificationManagerCompat.from(context)) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                TedPermission.create()
                    .setPermissions(Manifest.permission.POST_NOTIFICATIONS)
                    .setPermissionListener(object : PermissionListener {
                        @SuppressLint("MissingPermission")
                        override fun onPermissionGranted() {
                            notify(-1, notification)
                        }

                        override fun onPermissionDenied(deniedPermissions: List<String?>?) {
                            Log.w("SDGH", "Permission POST_NOTIFICATIONS denied.")
                        }

                    })
            } else {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w("SDGH", "Permission POST_NOTIFICATIONS denied.")
                    return
                }
                notify(-1, notification)
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun createNotificationChannel(context: Context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channelInteraction = NotificationChannel(
                CHANNEL_INTERACTION_ID,
                "Download needs interaction",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "A user interaction is required to continue the running download."
            }

            notificationManager.createNotificationChannel(channelInteraction)
        }
    }
}