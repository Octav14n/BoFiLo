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
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.AppBarLayout
import eu.schnuff.bofilo.download.StoryDownloadGeckoHelper
import eu.schnuff.bofilo.download.StoryDownloadService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.mozilla.geckoview.GeckoView


class CaptchaActivity : AppCompatActivity() {
    private lateinit var mainView: LinearLayout
    private var service: StoryDownloadService? = null
    private lateinit var webView: GeckoView
    private val connection = object: ServiceConnection {
        @SuppressLint("SetJavaScriptEnabled")
        override fun onServiceConnected(p0: ComponentName, p1: IBinder) {
            webView.setSession(StoryDownloadGeckoHelper.getSession(this@CaptchaActivity))
            webView.run {
                mainView.removeAllViews()
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

        bindService(
            Intent(this, StoryDownloadService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        webView.setSession(StoryDownloadGeckoHelper.getSession(this@CaptchaActivity))
        webView.run {
            mainView.removeAllViews()
            mainView.addView(this)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }


        findViewById<ImageButton>(R.id.reloadButton).setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                StoryDownloadGeckoHelper.reInitWebView()
                webView.setSession(StoryDownloadGeckoHelper.getSession(this@CaptchaActivity))
            }
        })

        findViewById<ImageButton>(R.id.reloadFloatingButton).setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                StoryDownloadGeckoHelper.reInitWebView()
                webView.setSession(StoryDownloadGeckoHelper.getSession(this@CaptchaActivity))
            }
        })


        if (intent != null)
            onNewIntent(intent)

        /*webView.setSession(StoryDownloadGeckoHelper.getSession(this@CaptchaActivity))
        webView.run {
            mainView.addView(this)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }*/

        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.appbar)) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(0, statusBars.top, 0, 0)
            insets
        }
    }

    companion object {
        const val INTENT_EXTRA_URL = "url"
    }
}