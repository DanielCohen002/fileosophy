package com.amaze.filemanager.ui.dialogs

import android.app.Activity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.amaze.filemanager.R
import com.amaze.filemanager.utils.BookmarkOrder
import com.amaze.filemanager.utils.DataUtils
import java.util.Collections

/** Lets the user drag the sidebar bookmarks into any order. */
object ReorderBookmarksDialog {
    @JvmStatic
    fun show(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val items = ArrayList<Array<String>>(DataUtils.getInstance().books)
        BookmarkOrder.sort(prefs, items)

        val density = activity.resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val hint = TextView(activity)
        hint.setText(R.string.reorder_bookmarks_hint)
        hint.setPadding(pad, pad / 2, pad, pad / 2)
        val container = LinearLayout(activity)
        container.orientation = LinearLayout.VERTICAL
        container.addView(hint)

        val recycler = RecyclerView(activity)
        recycler.layoutManager = LinearLayoutManager(activity)
        recycler.layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        val adapter =
            object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(
                    parent: ViewGroup,
                    viewType: Int,
                ): RecyclerView.ViewHolder {
                    val tv = TextView(activity)
                    tv.layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    tv.setPadding(pad, pad, pad, pad)
                    tv.textSize = 16f
                    return object : RecyclerView.ViewHolder(tv) {}
                }

                override fun getItemCount() = items.size

                override fun onBindViewHolder(
                    holder: RecyclerView.ViewHolder,
                    position: Int,
                ) {
                    (holder.itemView as TextView).text = "☰   " + items[position][0]
                }
            }
        recycler.adapter = adapter
        container.addView(recycler)
        ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0,
            ) {
                override fun onMove(
                    rv: RecyclerView,
                    from: RecyclerView.ViewHolder,
                    to: RecyclerView.ViewHolder,
                ): Boolean {
                    Collections.swap(items, from.adapterPosition, to.adapterPosition)
                    adapter.notifyItemMoved(from.adapterPosition, to.adapterPosition)
                    return true
                }

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int,
                ) {}
            },
        ).attachToRecyclerView(recycler)

        MaterialDialog.Builder(activity)
            .title(R.string.reorder_bookmarks)
            .customView(container, false)
            .positiveText(R.string.done)
            .negativeText(R.string.cancel)
            .onPositive { _, _ -> BookmarkOrder.save(prefs, items) }
            .show()
    }
}
