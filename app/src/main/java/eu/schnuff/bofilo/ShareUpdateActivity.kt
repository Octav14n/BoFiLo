package eu.schnuff.bofilo

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
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

        if (intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                scheduleDownload(this, it)
            }
        }
        finish()
    }


    companion object {
        fun scheduleDownload(context: Context, url: String) {
            thread {
                StoryDownloadService.start(context, url)
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