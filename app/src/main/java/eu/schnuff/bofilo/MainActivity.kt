package eu.schnuff.bofilo

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.recyclerview.widget.SimpleItemAnimator
import com.chaquo.python.Python
import com.google.android.material.snackbar.Snackbar
import eu.schnuff.bofilo.Helpers.copyFile
import eu.schnuff.bofilo.databinding.ActivityMainBinding
import eu.schnuff.bofilo.download.StoryDownloadService
import eu.schnuff.bofilo.download.filewrapper.FileWrapper
import eu.schnuff.bofilo.persistence.storylist.StoryListItem
import eu.schnuff.bofilo.persistence.storylist.StoryListViewModel
import eu.schnuff.bofilo.settings.Settings
import eu.schnuff.bofilo.settings.SettingsActivity
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: StoryListAdapter
    private lateinit var storyListViewModel: StoryListViewModel
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initiate View
        binding = ActivityMainBinding.inflate(layoutInflater)
        settings = Settings(this)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Initiate RecyclerView
        var initializedOldDownloads = false
        storyListViewModel = StoryListViewModel(application)
        adapter = StoryListAdapter(storyListViewModel)
        adapter.onLongClick = { item ->
            AlertDialog.Builder(this).apply {
                //setTitle(R.string.list_action_title)
                setItems(R.array.list_actions) { dialog, which ->
                    when (which) {
                        0 -> thread {
                            storyListViewModel.setFinished(item, false)
                            scheduleDownload(item.url)
                        }
                        1 -> thread {
                            unNewStory(item)
                        }
                        2 -> storyListViewModel.remove(item)
                    }
                    dialog.dismiss()
                }
                create()
                show()
            }
        }
        storyListViewModel.allItems.observe(this, {
            if (!initializedOldDownloads) {
                // if this is the first call then enqueue all non finished items for downloading.
                for (item in it) {
                    if (!item.finished) {
                        scheduleDownload(item.url)
                    }
                }
                initializedOldDownloads = true
            }
            adapter.setAll(it)
        })
        binding.storyList.adapter = adapter
        (binding.storyList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        // Initiate console
        storyListViewModel.consoleOutput.observe(this, {
            // Test if console shall be shown
            if (settings.showConsole) {
                if (it == "") {
                    // if no text is available then hide the console
                    binding.consoleOutputScroll.visibility = View.GONE
                } else {
                    // show the text and scroll to the newest entry
                    binding.consoleOutput.text = it
                    binding.consoleOutputScroll.visibility = View.VISIBLE
                }
            }
        })
        binding.consoleOutputScroll.viewTreeObserver.addOnGlobalLayoutListener {
            binding.consoleOutputScroll.fullScroll(View.FOCUS_DOWN)
        }

        // For debugging purposes the Icon in the bottom right starts many downloads
        binding.fab.setOnClickListener { view ->
            // Paste url from clipboard.
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription!!.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
                clipboard.primaryClip?.getItemAt(0)?.let {
                    scheduleDownload(it.text.toString())
                }
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
        thread {
            val item = if (storyListViewModel.has(url)) storyListViewModel.get(url) else storyListViewModel.add(url)
            if (item.finished)
                storyListViewModel.setFinished(item, false)
            val intent = Intent(this@MainActivity, StoryDownloadService::class.java).apply {
                putExtra(StoryDownloadService.PARAM_URL, item.url)
                // this.putExtra(StoryDownloadService.PARAM_DIR, "/")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun unNewStory(item: StoryListItem) {
        // Copy file to cache directory
        if (item.uri == null) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "No file is associated with this entry", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val original = FileWrapper.fromUri(this, item.uri!!.toUri())
        val cache = File(cacheDir, original.name)
        contentResolver.copyFile(original.uri, cache.toUri())

        // call python methods to un-new the file.
        try {
            val py = Python.getInstance()
            py.getModule("os").callAttr("chdir", cacheDir.absolutePath)
            val helper = py.getModule("helper")
            helper.callAttr("unnew", cache.absolutePath)
            helper.close()
        } catch (e: Throwable) {
            Log.w(this::class.simpleName, e)
            runOnUiThread {
                Toast.makeText(this@MainActivity, "An error occurred while running FanFicFare.", Toast.LENGTH_SHORT)
                    .show()
            }
            storyListViewModel.setConsoleOutput((storyListViewModel.consoleOutput.value ?: "") + e.message)
            cache.delete()
            return
        }

        // copy back
        contentResolver.copyFile(cache.toUri(), original.uri)
        cache.delete()
        runOnUiThread {
            Toast.makeText(this@MainActivity, "UnNew ${item.title} successful.", Toast.LENGTH_SHORT)
                .show()
        }
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
                storyListViewModel.removeAll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
