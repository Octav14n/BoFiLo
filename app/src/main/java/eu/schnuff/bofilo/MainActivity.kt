package eu.schnuff.bofilo

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        // adapter.init(application)
        storyListViewModel = StoryListViewModel(application)
        adapter = StoryListAdapter(storyListViewModel!!)
        val storyListItems = storyListViewModel!!.allItems
        var initializedOldDownloads = false
        storyListItems.observe(this, Observer {
            it?.let {
                val finished = it.toMutableList()
                if (!initializedOldDownloads) {
                    for (item in it) {
                        if (!item.finished) {
                            scheduleDownload(item.url)
                            finished.remove(item)
                        }
                    }
                    initializedOldDownloads = true
                }
                adapter!!.setAll(finished.toTypedArray())
            }
        })
        story_list.adapter = adapter
        (story_list.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        fab.setOnClickListener { view ->
            // Test urls.
            for (url in listOf(
//                // author: Rorschach's Blot (short)
//                "https://www.fanfiction.net/s/2100544/1/Past-Lives",
//                "https://www.fanfiction.net/s/3248583/1/Ground-Hog-Day",
//                "https://www.fanfiction.net/s/3635811/1/Hermione-the-Harem-Girl",
//                // author: Rorschach's Blot (longer)
//                "https://www.fanfiction.net/s/2318355/1/Make-A-Wish",
//                "https://www.fanfiction.net/s/3032621/1/The-Hunt-For-Harry-Potter",
//                "https://www.fanfiction.net/s/3695087/1/Larceny-Lechery-and-Luna-Lovegood",
//                // author: dogbertcarroll
//                "https://www.tthfanfic.org/Story-14005/dogbertcarroll+Walking+in+the+shadows.htm",
//                "https://www.tthfanfic.org/Story-33037/dogbertcarroll+Wood+it+Work.htm",
//                "https://www.tthfanfic.org/Story-21322/dogbertcarroll+I+wouldn+t+exactly+call+that+sitting.htm",
//                // site: SpaceBattles.com
//                "https://forums.spacebattles.com/threads/this-bites-one-piece-si.356819/"
                "https://forum.questionablequesting.com/threads/multiversal-mayhem-r34-economy-marvel-mcu.11304/"
            ).reversed()) {
                scheduleDownload(url)
            }

            Snackbar.make(view, "Downloads scheduled.", Snackbar.LENGTH_SHORT).show()
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

    private fun scheduleDownload(url: String) = GlobalScope.async {
        val newUrl = storyListViewModel!!.add(url).url
        startService(Intent(this@MainActivity, StoryDownloadService::class.java).apply {
            putExtra(StoryDownloadService.PARAM_URL, newUrl)
            // this.putExtra(StoryDownloadService.PARAM_DIR, "/")
        })
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
