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
            val storyListViewModel = StoryListViewModel(context.applicationContext as Application)

            thread {
                val item = if (storyListViewModel.has(url)) storyListViewModel.get(url) else storyListViewModel.add(url)
                if (item.finished)
                    storyListViewModel.setFinished(item, false)
                val intent = Intent(context, StoryDownloadService::class.java).apply {
                    putExtra(Intent.EXTRA_TEXT, item.url)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
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