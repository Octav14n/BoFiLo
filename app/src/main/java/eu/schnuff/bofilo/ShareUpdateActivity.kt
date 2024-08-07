package eu.schnuff.bofilo

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import eu.schnuff.bofilo.download.StoryDownloadService
import eu.schnuff.bofilo.persistence.storylist.StoryListViewModel
import kotlin.concurrent.thread

class ShareUpdateActivity : AppCompatActivity() {
    private lateinit var storyListViewModel: StoryListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_update)

        intent?.let {
            onNewIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        // Handle potential new download request
        super.onNewIntent(intent)

        if (intent.action == Intent.ACTION_SEND || intent.action == "eu.schnuff.bofilo.action.download") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                scheduleDownload(this, it)
            }
        } else {
            Log.w(this::class.simpleName, "onNewIntent called with wrong action: " + intent.action)
        }
        finish()
    }


    companion object {
        fun scheduleDownload(context: Context, url: String, forceDownload: Boolean=false) {
            thread {
                StoryDownloadService.start(context, url, forceDownload)
                Handler(context.mainLooper).post {
                    Toast.makeText(
                        context,
                        context.getString(R.string.download_started_success).format(url),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}