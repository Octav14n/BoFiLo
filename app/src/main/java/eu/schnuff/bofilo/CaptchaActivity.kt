package eu.schnuff.bofilo

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.schnuff.bofilo.download.StoryDownloadService


class CaptchaActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var service: StoryDownloadService? = null
    private val connection = object: ServiceConnection {
        override fun onServiceConnected(p0: ComponentName, p1: IBinder) {
            val binder = p1 as StoryDownloadService.StoryDownloadBinder
            service = binder.Service
        }

        override fun onServiceDisconnected(p0: ComponentName) {
            service = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_captcha)
        webView = findViewById(R.id.captchaWebView)
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true

        bindService(Intent(this, StoryDownloadService::class.java), connection, Context.BIND_AUTO_CREATE)

        if (intent != null)
            onNewIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        service?.queue?.put("")
        unbindService(connection)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        webView.loadUrl(intent.getStringExtra(INTENT_EXTRA_URL)!!)
    }

    companion object {
        const val INTENT_EXTRA_URL = "url"
    }
}