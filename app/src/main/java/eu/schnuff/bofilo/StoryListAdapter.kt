package eu.schnuff.bofilo

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.ColorFilter
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import eu.schnuff.bofilo.databinding.StoryListDetailBinding
import eu.schnuff.bofilo.download.StoryDownloadService
import eu.schnuff.bofilo.persistence.storylist.StoryListItem
import eu.schnuff.bofilo.persistence.storylist.StoryListViewModel

class StoryListAdapter(private val viewModel: StoryListViewModel) : RecyclerView.Adapter<StoryListAdapter.MyViewHolder>() {
    private var myDataset = emptyArray<StoryListItem>()
    var onLongClick: (StoryListItem) -> Unit = {}
    private lateinit var progressColorNormal: ColorFilter
    private lateinit var progressColorForce: ColorFilter

    fun setAll(items: Array<StoryListItem>) {
        myDataset = items
        notifyDataSetChanged()
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder.
    // Each data item is just a string in this case that is shown in a TextView.
    class MyViewHolder(val binding: StoryListDetailBinding) : RecyclerView.ViewHolder(binding.root)


    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): MyViewHolder {
        // create a new view
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            progressColorNormal = BlendModeColorFilter(ContextCompat.getColor(parent.context, R.color.colorListProgressNormal), BlendMode.SRC_IN)
            progressColorForce = BlendModeColorFilter(ContextCompat.getColor(parent.context, R.color.colorListProgressForce), BlendMode.SRC_IN)
        }
        return MyViewHolder(StoryListDetailBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        val item = myDataset[position]
        holder.binding.header.text = item.title
        holder.binding.url.text = item.url
        if (item.finished) {
            // Hide progress items of finished downloads
            holder.binding.progress.visibility = View.GONE
            holder.binding.progressText.text = "${item.max}"
        } else {
            // Handle items in the waiting queue
            val p = item.progress ?: 0
            val m = item.max
            holder.binding.progress.visibility = View.VISIBLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                holder.binding.progress.progressDrawable.colorFilter =
                    if (item.forceDownload) progressColorForce else progressColorNormal
                holder.binding.progress.indeterminateDrawable.colorFilter = if (item.forceDownload) progressColorForce else progressColorNormal
            }
            if (item.url != StoryDownloadService.ActiveItem?.url) {
                // Handle waiting downloads
                holder.binding.progressText.visibility = View.GONE
                holder.binding.progress.isIndeterminate = true
            } else {
                // Handle active download
                holder.binding.progressText.visibility = View.VISIBLE
                holder.binding.progressText.text = "$p/${m ?: "∞"}"
                // as long as no progress is made the progress is indeterminant
                // (because starting the download takes a while)
                holder.binding.progress.isIndeterminate = (p < 1)
                holder.binding.progress.progress = p
                // if no maximum is provided use an arbitrary number that is bigger than p
                holder.binding.progress.max = m ?: (p + 1)
            }
        }
        holder.binding.root.setOnClickListener {
            // At the moment we remove items by long clicking
            // TODO: give a options menu, what shall be done with the clicked item
            onLongClick(item)
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = myDataset.size
}