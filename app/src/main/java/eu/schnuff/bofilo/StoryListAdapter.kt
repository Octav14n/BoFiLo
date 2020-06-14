package eu.schnuff.bofilo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.schnuff.bofilo.download.StoryDownloadService
import eu.schnuff.bofilo.persistence.storylist.StoryListItem
import eu.schnuff.bofilo.persistence.storylist.StoryListViewModel
import kotlinx.android.synthetic.main.story_list_detail.view.*

class StoryListAdapter(private val viewModel: StoryListViewModel) : RecyclerView.Adapter<StoryListAdapter.MyViewHolder>() {
    private var myDataset = emptyArray<StoryListItem>()
    var onLongClick: (StoryListItem) -> Unit = {}

    fun setAll(items: Array<StoryListItem>) {
        myDataset = items
        notifyDataSetChanged()
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder.
    // Each data item is just a string in this case that is shown in a TextView.
    class MyViewHolder(val view: View) : RecyclerView.ViewHolder(view)


    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): MyViewHolder {
        // create a new view
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.story_list_detail, parent, false) as View
        // set the view's size, margins, paddings and layout parameters
        return MyViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        val item = myDataset[position]
        holder.view.header.text = item.title
        holder.view.url.text = item.url
        if (item.finished) {
            // Hide progress items of finished downloads
            holder.view.progress.visibility = View.GONE
            holder.view.progress_text.visibility = View.GONE
        } else {
            // Handle items in the waiting queue
            val p = item.progress ?: 0
            val m = item.max
            holder.view.progress.visibility = View.VISIBLE
            if (item.url != StoryDownloadService.ActiveItem?.url) {
                // Handle waiting downloads
                holder.view.progress_text.visibility = View.GONE
                holder.view.progress.isIndeterminate = true
            } else {
                // Handle active download
                holder.view.progress_text.visibility = View.VISIBLE
                holder.view.progress_text.text = "$p/${m ?: "∞"}"
                // as long as no progress is made the progress is indeterminant
                // (because starting the download takes a while)
                holder.view.progress.isIndeterminate = (p < 1)
                holder.view.progress.progress = p
                // if no maximum is provided use an arbitrary number that is bigger than p
                holder.view.progress.max = m ?: (p + 1)
            }
        }
        holder.view.setOnLongClickListener {
            // At the moment we remove items by long clicking
            // TODO: give a options menu, what shall be done with the clicked item
            onLongClick(item)
            true
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = myDataset.size
}