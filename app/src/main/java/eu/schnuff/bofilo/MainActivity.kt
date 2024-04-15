package eu.schnuff.bofilo

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.snackbar.Snackbar
import eu.schnuff.bofilo.databinding.ActivityMainBinding
import eu.schnuff.bofilo.download.StoryDownloadService
import eu.schnuff.bofilo.download.StoryUnNewService
import eu.schnuff.bofilo.persistence.storylist.StoryListItem
import eu.schnuff.bofilo.persistence.storylist.StoryListViewModel
import eu.schnuff.bofilo.settings.Settings
import eu.schnuff.bofilo.settings.SettingsActivity
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: StoryListAdapter
    private lateinit var storyListViewModel: StoryListViewModel
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Handle potential new download request
        if (intent != null) {
            onNewIntent(intent)
            if (isFinishing)
                return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) {
            //todo when permission is granted
        } else {
            //request for the permission
            val intent = Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.fromParts("package", packageName, null)
            startActivity(intent)
        }

        // Initiate View
        binding = ActivityMainBinding.inflate(layoutInflater)
        settings = Settings(this)
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
                            scheduleDownload(item.url)
                        }
                        1 -> unNewStory(item)
                        2 -> thread {
                            scheduleDownload(item.url, true)
                        }
                        3 -> storyListViewModel.remove(item)
                    }
                    dialog.dismiss()
                }
                create()
                show()
            }
        }
        storyListViewModel.allItems.observe(this) {
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
        }
        binding.storyList.adapter = adapter
        (binding.storyList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        // Initiate console
        storyListViewModel.consoleOutput.observe(this) {
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
        }
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
    }

    override fun onNewIntent(intent: Intent) {
        // Handle potential new download request
        Log.d(this::class.simpleName, "Got new intent: %s --> %s".format(intent.action, intent.extras.toString()))
        super.onNewIntent(intent)

        val extra = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return

        when (intent.action) {
            Intent.ACTION_SEND -> ShareUpdateActivity.scheduleDownload(this, extra)
            ShareUnNewActivity.INTENT -> ShareUnNewActivity.unNewStory(this, extra.toUri())
            else -> Log.w(this::class.simpleName, "Intent is not supported: " + intent.action)
        }

        finish()
        //moveTaskToBack(true)
    }

    private fun scheduleDownload(url: String, force: Boolean = false) {
        StoryDownloadService.start(this, url, force)
    }

    private fun unNewStory(item: StoryListItem) {
        // Copy file to cache directory
        thread {
            val itemUri = item.uri?.toUri()

            if (itemUri == null) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "No file is associated with this entry", Toast.LENGTH_SHORT)
                        .show()
                }
                return@thread
            }
            StoryUnNewService.start(this, itemUri)
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
            R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
            R.id.action_reset -> { storyListViewModel.removeAll(); true }
            R.id.action_show_webview -> { startActivity(Intent(this, CaptchaActivity::class.java)); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
