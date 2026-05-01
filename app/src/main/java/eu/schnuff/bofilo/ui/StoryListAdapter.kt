package eu.schnuff.bofilo.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.schnuff.bofilo.databinding.StoryListDetailBinding
import eu.schnuff.bofilo.persistence.storylist.StoryListItem

class StoryListAdapter : RecyclerView.Adapter<StoryView>() {
    private var storyData = emptyArray<StoryListItem>()
    private var menuCallback: StoryActionInterface? = null

    fun setStories(items: Array<StoryListItem>) {
        storyData = items
        notifyDataSetChanged()
    }

    fun setCallback(callback: StoryActionInterface) {
        menuCallback = callback
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoryView {
        return StoryView(StoryListDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: StoryView, position: Int) {
        val item = storyData[position]
        holder.setStory(item)
        menuCallback?.let { holder.setMenuCallback(it) }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = storyData.size
}