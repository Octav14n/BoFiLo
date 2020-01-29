package eu.schnuff.bofilo

import android.app.Application
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.schnuff.bofilo.persistence.StoryListItem
import eu.schnuff.bofilo.persistence.StoryListViewModel
import kotlinx.android.synthetic.main.story_list_detail.view.*
import java.lang.IllegalArgumentException

class StoryListAdapter(private val myDataset: MutableList<StoryListItem>) : RecyclerView.Adapter<StoryListAdapter.MyViewHolder>() {
//    private var storyListViewModel: StoryListViewModel? = null
//    fun init(application: Application) {
//        storyListViewModel = StoryListViewModel(application)
//    }

    fun setAll(items: Iterable<StoryListItem>) {
        myDataset.clear()
        myDataset.addAll(items)
        notifyDataSetChanged()
    }

//    fun add(url: String, title: String? = null, progress: Int? = null, max: Int? = null) {
//        val item = remove(url)?.copy(title, progress, max) ?: StoryListItem(title, url, progress, max)
//
//        myDataset.add(0, item)
//        storyListViewModel!!.add(item)
//        if (myDataset.size == 1) {
//            notifyDataSetChanged()
//        } else {
//            notifyItemInserted(0)
//        }
//    }
//
//    private fun remove(url: String): StoryListItem? {
//        val i = myDataset.indexOfFirst { s -> s.url == url }
//        if (i > -1) {
//            val m = myDataset.removeAt(i)
//            storyListViewModel!!.remove(m)
//            notifyItemRemoved(i)
//            return m
//        }
//        return null
//    }
//
//    fun setProgress(url: String, progress: Int = 0, max: Int? = null) {
//        val i = myDataset.indexOfFirst { s -> s.url == url }
//        if (i > -1) {
//            myDataset[i].progress = progress
//            myDataset[i].max = max
//            storyListViewModel!!.update(myDataset[i])
//            notifyItemChanged(i)
//            Log.d("chapter", "setProgress called with $progress/$max.")
//        } else {
//            throw IllegalArgumentException("No entry with url: $url found.")
//        }
//    }
//
//    fun setTitle(url: String, title: String) {
//        val i = myDataset.indexOfFirst { s -> s.url == url }
//        if (i > -1) {
//            myDataset[i].title = title
//            storyListViewModel!!.update(myDataset[i])
//            notifyItemChanged(i)
//        } else {
//            throw IllegalArgumentException("No entry with url: $url found.")
//        }
//    }
//
//    fun setFinished(url: String) {
//        val i = myDataset.indexOfFirst { s -> s.url == url }
//        if (i > -1) {
//            myDataset[i].finished = true
//            storyListViewModel!!.update(myDataset[i])
//            notifyItemChanged(i)
//            Log.d("chapter", "setFinished called.")
//        } else {
//            throw IllegalArgumentException("No entry with url: $url found.")
//        }
//    }

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
        val i = myDataset[position]
        holder.view.header.text = i.title
        holder.view.url.text = i.url
        if (!i.finished) {
            val p = i.progress ?: 0
            val m = i.max
            holder.view.progress.visibility = View.VISIBLE
            if (m != null) {
                holder.view.progress_text.visibility = View.VISIBLE
                holder.view.progress.isIndeterminate = false
                holder.view.progress_text.text = "%d/%d".format(p, m)
                holder.view.progress.progress = p
                holder.view.progress.max = m
            }
        } else {
            holder.view.progress.visibility = View.GONE
            holder.view.progress_text.visibility = View.GONE
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = myDataset.size
}