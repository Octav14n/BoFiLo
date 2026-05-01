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
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.snackbar.Snackbar
import eu.schnuff.bofilo.databinding.ActivityMainBinding
import eu.schnuff.bofilo.download.StoryDownloadService
import eu.schnuff.bofilo.download.StoryUnNewService
import eu.schnuff.bofilo.persistence.storylist.StoryListItem
import eu.schnuff.bofilo.persistence.storylist.StoryListViewModel
import eu.schnuff.bofilo.settings.Settings
import eu.schnuff.bofilo.ui.StoryActionInterface
import eu.schnuff.bofilo.ui.StoryListAdapter
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity(), StoryActionInterface {
    private lateinit var storyListAdapter: StoryListAdapter
    private lateinit var storyListViewModel: StoryListViewModel
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings

    private var mainMenu: PopupMenu? = null

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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prepareToolbarMenu()

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        settings = Settings(this)

        // Initiate RecyclerView
        var initializedOldDownloads = false
        storyListViewModel = StoryListViewModel(application)
        storyListAdapter = StoryListAdapter()
        storyListAdapter.setCallback(this)

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
            storyListAdapter.setStories(it)
        }
        binding.storyList.adapter = storyListAdapter
        (binding.storyList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        initializeConsole()

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

    override fun onResume() {
        super.onResume()
        initializeConsole()
    }

    override fun onNewIntent(intent: Intent) {
        // Handle potential new download request
        Log.d(this::class.simpleName, "Got new intent: %s --> %s".format(intent.action, intent.extras.toString()))
        super.onNewIntent(intent)

        val extra = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return

        when (intent.action) {
            Intent.ACTION_SEND, "eu.schnuff.bofilo.action.download" -> ShareUpdateActivity.scheduleDownload(this, extra)
            ShareUnNewActivity.INTENT -> ShareUnNewActivity.unNewStory(this, extra.toUri())
            else -> Log.w(this::class.simpleName, "Intent is not supported: " + intent.action)
        }

        finish()
        //moveTaskToBack(true)
    }


    /**
     * This method hides the console window if the user disabled it.
     * If it is enabled, the listeners are initialized.
     */
    private fun initializeConsole() {
        if (!settings.showConsole) {
            setConsoleVisibility(false)
            return
        }
        // Initiate console
        handleConsoleVisibility(storyListViewModel.consoleOutput.value?: "")
        storyListViewModel.consoleOutput.observe(this) {
            handleConsoleVisibility(it)
        }
        binding.consoleOutputScroll.viewTreeObserver.addOnGlobalLayoutListener {
            binding.consoleOutputScroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    /**
     * This method handles incoming text for the console window.
     * If the incoming text is blank, the console window is hidden.
     */
    private fun handleConsoleVisibility(text: String) {
        // Test if console shall be shown
        if (text.isBlank()) {
            // if no text is available then hide the console
            setConsoleVisibility(false)
        } else {
            // show the text and scroll to the newest entry
            binding.consoleOutput.text = text
            setConsoleVisibility(true)
        }
    }

    /**
     * Handles the visibility of the console itself.
     * It also enables/disables the associated menu entry
     */
    private fun setConsoleVisibility(visible: Boolean) {
        if(visible) {
            binding.consoleOutputContainer.visibility = View.VISIBLE
        } else {
            binding.consoleOutputContainer.visibility = View.GONE
        }

        if(mainMenu == null) {
            // early exit
            return
        }

        val item = mainMenu?.menu?.findItem(R.id.action_close_console)
        item?.isVisible = visible
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

    private fun prepareToolbarMenu() {

        mainMenu = PopupMenu(this@MainActivity, binding.moreButton)
        // Inflating popup menu from popup_menu.xml file
        mainMenu?.menuInflater?.inflate(R.menu.menu_main, mainMenu?.menu)

        binding.moreButton.setOnClickListener {

            mainMenu?.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.action_settings -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
                    R.id.action_reset -> { storyListViewModel.removeAll(); true }
                    R.id.action_show_webview -> { startActivity(Intent(this, CaptchaActivity::class.java)); true }
                    R.id.action_close_console -> {
                        setConsoleVisibility(false)
                        true
                    }
                    else -> super.onOptionsItemSelected(item)
                }
            }
            mainMenu?.show()
        }
    }


    override fun restart(story: StoryListItem) {
        scheduleDownload(story.url)
    }

    override fun unnew(story: StoryListItem) {
        unNewStory(story)
    }

    override fun forcedownload(story: StoryListItem) {
        scheduleDownload(story.url, true)
    }

    override fun delete(story: StoryListItem) {
        storyListViewModel.remove(story)
    }
}
