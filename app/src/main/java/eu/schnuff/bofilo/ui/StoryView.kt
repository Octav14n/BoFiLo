package eu.schnuff.bofilo.ui

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.os.Build
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import eu.schnuff.bofilo.R
import eu.schnuff.bofilo.databinding.StoryListDetailBinding
import eu.schnuff.bofilo.download.StoryDownloadService
import eu.schnuff.bofilo.persistence.storylist.StoryListItem

class StoryView(val binding: StoryListDetailBinding) : RecyclerView.ViewHolder(binding.root), PopupMenu.OnMenuItemClickListener {

    private lateinit var story: StoryListItem
    private var menuCallback: StoryActionInterface? = null

    fun setStory(story: StoryListItem) {
        this.story = story
        updateView()
    }

    fun setMenuCallback(callback: StoryActionInterface) {
        menuCallback = callback
    }

    fun updateView() {
        binding.title.text = story.title
        binding.url.text = story.url


        // initialize progress. Assume we are finished already.
        binding.progress.visibility = View.GONE
        binding.progressText.text = "${story.max}"
        binding.progressText.visibility = View.VISIBLE

        if (!story.finished) {
            calculateProgress()
        }
        initializeMenu()

    }

    private fun calculateProgress() {
        // Handle items in the waiting queue
        val currentProgress = story.progress ?: 0
        val maxValue = story.max
        binding.progress.visibility = View.VISIBLE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (story.forceDownload) {
                // todo: Is this color now stuck to the forced one because we dont reset it?
                val color = BlendModeColorFilter(ContextCompat.getColor(binding.root.context, R.color.colorListProgressForce), BlendMode.SRC_IN)
                binding.progress.progressDrawable.colorFilter = color
                binding.progress.indeterminateDrawable.colorFilter = color
            }
        }

        if (story.url != StoryDownloadService.ActiveItem?.url) {
            // Handle waiting downloads
            binding.progressText.visibility = View.GONE
            binding.progress.isIndeterminate = true
        } else {
            // Handle active download
            binding.progressText.visibility = View.VISIBLE
            binding.progressText.text = "$currentProgress/${maxValue ?: "∞"}"
            // as long as no progress is made the progress is indeterminant
            // (because starting the download takes a while)
            binding.progress.isIndeterminate = (currentProgress < 1)
            binding.progress.progress = currentProgress
            // if no maximum is provided use an arbitrary number that is bigger than p
            binding.progress.max = maxValue ?: (currentProgress + 1)
        }
    }

    fun initializeMenu() {
        binding.moreIcon.setOnClickListener {
            val context = binding.root.context
            val popup = PopupMenu(context, it)
            popup.setOnMenuItemClickListener(this)
            popup.menuInflater.inflate(R.menu.menu_story, popup.menu)
            popup.setForceShowIcon(true)
            popup.show()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_restart -> {
                menuCallback?.restart(story)
                true
            }
            R.id.action_unnew -> {
                menuCallback?.unnew(story)
                true
            }
            R.id.action_force_download -> {
                menuCallback?.forcedownload(story)
                true
            }
            R.id.action_delete -> {
                menuCallback?.delete(story)
                true
            }
            else -> {
                false
            }
        }
    }
}