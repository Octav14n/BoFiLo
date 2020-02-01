package eu.schnuff.bofilo

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.snackbar.Snackbar
import eu.schnuff.bofilo.persistence.StoryListViewModel
import eu.schnuff.bofilo.settings.SettingsActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

class MainActivity : AppCompatActivity() {
    private var adapter: StoryListAdapter? = null
    private var storyListViewModel: StoryListViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initiate View
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        // Initiate RecyclerView
        var initializedOldDownloads = false
        storyListViewModel = StoryListViewModel(application)
        storyListViewModel!!.allItems.observe(this, Observer {
            if (initializedOldDownloads) {
                // at the 2nd and later calls only pass the data through
                adapter!!.setAll(it)
            } else {
                // if this is the first call then enqueue all non finished items for downloading.
                val finished = it.toMutableList()
                for (item in it) {
                    if (!item.finished) {
                        scheduleDownload(item.url)
                        finished.remove(item)
                    }
                }
                initializedOldDownloads = true
                adapter!!.setAll(finished.toTypedArray())
            }
        })

        adapter = StoryListAdapter(storyListViewModel!!)
        story_list.adapter = adapter
        (story_list.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        // Initiate console
        storyListViewModel!!.consoleOutput.observe(this, Observer {
            // Test if console shall be shown
            if (PreferenceManager.getDefaultSharedPreferences(applicationContext).getBoolean(Constants.PREF_SHOW_CONSOLE, true)) {
                if (it == "") {
                    // if no text is available then hide the console
                    consoleOutputScroll.visibility = View.GONE
                } else {
                    // show the text and scroll to the newest entry
                    consoleOutput.text = it
                    consoleOutputScroll.visibility = View.VISIBLE
                    consoleOutputScroll.fullScroll(View.FOCUS_DOWN)
                }
            }
        })

        // For debugging purposes the Icon in the bottom right starts many downloads
        fab.setOnClickListener { view ->
            // Test urls.
            for (url in listOf(
                // author: Rorschach's Blot (short)
                "https://www.fanfiction.net/s/2100544/1/Past-Lives",
                "https://www.fanfiction.net/s/3248583/1/Ground-Hog-Day",
                "https://www.fanfiction.net/s/3635811/1/Hermione-the-Harem-Girl",
                // author: Rorschach's Blot (longer)
                "https://www.fanfiction.net/s/2318355/1/Make-A-Wish",
                "https://www.fanfiction.net/s/3032621/1/The-Hunt-For-Harry-Potter",
                "https://www.fanfiction.net/s/3695087/1/Larceny-Lechery-and-Luna-Lovegood",
                // author: dogbertcarroll
                "https://www.tthfanfic.org/Story-14005/dogbertcarroll+Walking+in+the+shadows.htm",
                "https://www.tthfanfic.org/Story-33037/dogbertcarroll+Wood+it+Work.htm",
                "https://www.tthfanfic.org/Story-21322/dogbertcarroll+I+wouldn+t+exactly+call+that+sitting.htm",
                // site: SpaceBattles.com
                "https://forums.spacebattles.com/threads/this-bites-one-piece-si.356819/"
            ).reversed()) {
                scheduleDownload(url)
            }

            Snackbar.make(view, "Downloads scheduled.", Snackbar.LENGTH_SHORT).show()
                    //.setAction("Action", null)
        }

        // Handle potential new download request
        if (intent != null)
            onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        // Handle potential new download request
        super.onNewIntent(intent)

        if (intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                scheduleDownload(it)
            }
        }
    }

    private fun scheduleDownload(url: String) {
        Thread {
            val newUrl = storyListViewModel!!.add(url).url
            val intent = Intent(this@MainActivity, StoryDownloadService::class.java).apply {
                putExtra(StoryDownloadService.PARAM_URL, newUrl)
                // this.putExtra(StoryDownloadService.PARAM_DIR, "/")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }.start()
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
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_reset -> {
                storyListViewModel!!.removeAll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
