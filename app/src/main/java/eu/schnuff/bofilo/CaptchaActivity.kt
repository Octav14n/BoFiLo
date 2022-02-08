package eu.schnuff.bofilo

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import eu.schnuff.bofilo.download.StoryDownloadService
import org.mozilla.geckoview.GeckoView


class CaptchaActivity : AppCompatActivity() {
    private lateinit var mainView: LinearLayout
    private var service: StoryDownloadService? = null
    private lateinit var webView: GeckoView
    private val connection = object: ServiceConnection {
        @SuppressLint("SetJavaScriptEnabled")
        override fun onServiceConnected(p0: ComponentName, p1: IBinder) {
            val binder = p1 as StoryDownloadService.StoryDownloadBinder
            service = binder.Service
            service?.run {
                webView.setSession(geckoSession)
            }
            /*webView!!.run {
                settings.apply {
                    javaScriptEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                }
                mainView.addView(this)
            }*/
            webView.run {
                mainView.addView(this)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }

        override fun onServiceDisconnected(p0: ComponentName) {
            service = null
            mainView.removeAllViews()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = GeckoView(this)
        setContentView(R.layout.activity_captcha)
        mainView = findViewById(R.id.captchaMain)

        bindService(Intent(this, StoryDownloadService::class.java), connection, Context.BIND_AUTO_CREATE)

        if (intent != null)
            onNewIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        service?.queue?.put("")
        unbindService(connection)
    }

//    override fun onNewIntent(intent: Intent) {
//        super.onNewIntent(intent)
//        webView?.loadUrl(intent.getStringExtra(INTENT_EXTRA_URL)!!)
//    }

    companion object {
        const val INTENT_EXTRA_URL = "url"
    }
}