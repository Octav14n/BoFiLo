package eu.schnuff.bofilo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.SimpleItemAnimator
import eu.schnuff.bofilo.persistence.StoryListDatabase
import eu.schnuff.bofilo.persistence.StoryListViewModel

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import java.lang.IllegalArgumentException

class MainActivity : AppCompatActivity() {
    private val adapter = StoryListAdapter(mutableListOf())
    private var storyListViewModel: StoryListViewModel? = null
    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) = when (intent.action) {
            StoryDownloadService.ACTION_START -> storyListViewModel!!.add(
                intent.getStringExtra(StoryDownloadService.PARAM_URL)
            )
            StoryDownloadService.ACTION_PROGRESS -> storyListViewModel!!.setProgress(
                intent.getStringExtra(StoryDownloadService.PARAM_URL),
                intent.getIntExtra(StoryDownloadService.PARAM_OUT_PROGRESS, 0),
                intent.getIntExtra(StoryDownloadService.PARAM_OUT_PROGRESS_MAX, -1)
            )
            StoryDownloadService.ACTION_TITLE -> storyListViewModel!!.setTitle(
                intent.getStringExtra(StoryDownloadService.PARAM_URL),
                intent.getStringExtra(StoryDownloadService.PARAM_OUT_TEXT)
            )
            StoryDownloadService.ACTION_ERROR -> storyListViewModel!!.setTitle(
                intent.getStringExtra(StoryDownloadService.PARAM_URL),
                intent.getStringExtra(StoryDownloadService.PARAM_OUT_TEXT)
            )
            StoryDownloadService.ACTION_FINISHED -> storyListViewModel!!.setFinished(
                intent.getStringExtra(StoryDownloadService.PARAM_URL)
            )
            else -> throw IllegalArgumentException("cannot be called with action '${intent.action}'.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        // adapter.init(application)
        storyListViewModel = StoryListViewModel(application)
        val storyListItems = storyListViewModel!!.allItems
        storyListItems.observe(this, Observer {
            it?.let {
                for (item in it) {
                    if (!item.finished) {
                        scheduleDownload(item.url)
                    }
                }
                adapter.setAll(it)
            }
        })
        story_list.adapter = adapter
        (story_list.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        LocalBroadcastManager.getInstance(applicationContext).registerReceiver(receiver, IntentFilter().apply {
            addAction(StoryDownloadService.ACTION_START)
            addAction(StoryDownloadService.ACTION_PROGRESS)
            addAction(StoryDownloadService.ACTION_TITLE)
            addAction(StoryDownloadService.ACTION_FINISHED)
        })

        fab.setOnClickListener { view ->
            val url = "https://www.fanfiction.net/s/12979639/1/Jane-Arc-s-Yuri-Harem"

            scheduleDownload(url)

            Snackbar.make(view, "Download started.", Snackbar.LENGTH_SHORT).show()
                    //.setAction("Action", null)
        }

        if (intent != null)
            onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                scheduleDownload(it)
            }
        }
    }

    private fun scheduleDownload(url: String) {
        val storyId = storyListViewModel!!.add(url).storyId
        startService(Intent(this, StoryDownloadService::class.java).apply {
            this.putExtra(StoryDownloadService.PARAM_URL, url)
            // this.putExtra(StoryDownloadService.PARAM_DIR, "/")
        })
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(applicationContext).unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}
