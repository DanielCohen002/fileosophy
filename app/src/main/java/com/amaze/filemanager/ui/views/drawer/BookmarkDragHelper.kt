package com.amaze.filemanager.ui.views.drawer

import android.content.Context
import android.content.SharedPreferences
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amaze.filemanager.utils.BookmarkOrder
import com.amaze.filemanager.utils.DataUtils
import com.amaze.filemanager.utils.Haptics

/**
 * Long-press a bookmark in the sidebar: the phone buzzes, then the row follows your finger while
 * the other bookmarks slide out of the way (with a click each time it passes one). Let go to drop.
 */
class BookmarkDragHelper(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val onOrderChanged: Runnable,
) : RecyclerView.OnItemTouchListener {
    private var dragging = false
    private var startY = 0f
    private var detector: GestureDetector? = null

    private class Row(val view: View, val name: String, val top: Int, val height: Int)

    private var rows: List<Row> = emptyList()
    private var dragIndex = -1
    private var targetIndex = -1

    fun attach(rv: RecyclerView) {
        detector =
            GestureDetector(
                rv.context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onLongPress(e: MotionEvent) {
                        startDrag(rv, e)
                    }
                },
            )
        rv.addOnItemTouchListener(this)
    }

    private fun firstText(v: View?): String? {
        if (v is TextView && v.text.isNotEmpty()) return v.text.toString()
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) firstText(v.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun bookmarkName(v: View?): String? {
        val text = firstText(v) ?: return null
        return DataUtils.getInstance().books.firstOrNull { it[0] == text }?.get(0)
    }

    private fun startDrag(
        rv: RecyclerView,
        e: MotionEvent,
    ) {
        val pressed = rv.findChildViewUnder(e.x, e.y) ?: return
        if (bookmarkName(pressed) == null) return
        rows =
            (0 until rv.childCount)
                .map { rv.getChildAt(it) }
                .mapNotNull { v -> bookmarkName(v)?.let { Row(v, it, v.top, v.height) } }
                .sortedBy { it.top }
        dragIndex = rows.indexOfFirst { it.view === pressed }
        if (dragIndex < 0) return
        targetIndex = dragIndex
        dragging = true
        startY = e.y
        pressed.alpha = 0.75f
        pressed.elevation = 12f
        rv.parent?.requestDisallowInterceptTouchEvent(true)
        Haptics.buzz(context)
    }

    override fun onInterceptTouchEvent(
        rv: RecyclerView,
        e: MotionEvent,
    ): Boolean {
        detector?.onTouchEvent(e)
        return dragging
    }

    override fun onTouchEvent(
        rv: RecyclerView,
        e: MotionEvent,
    ) {
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> onMove(e)
            MotionEvent.ACTION_UP -> finish(true)
            MotionEvent.ACTION_CANCEL -> finish(false)
        }
    }

    private fun onMove(e: MotionEvent) {
        if (!dragging || dragIndex < 0) return
        val drag = rows[dragIndex]
        val dy = e.y - startY
        drag.view.translationY = dy

        val center = drag.top + drag.height / 2f + dy
        var target = rows.indexOfFirst { center >= it.top && center < it.top + it.height }
        if (target < 0) target = if (center < rows.first().top) 0 else rows.size - 1
        if (target == targetIndex) return

        targetIndex = target
        Haptics.click(context)
        rows.forEachIndexed { i, row ->
            if (i == dragIndex) return@forEachIndexed
            val shift =
                when {
                    dragIndex < target && i in (dragIndex + 1)..target -> -drag.height.toFloat()
                    dragIndex > target && i in target until dragIndex -> drag.height.toFloat()
                    else -> 0f
                }
            row.view.animate().translationY(shift).setDuration(120).start()
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

    private fun finish(drop: Boolean) {
        if (!dragging) return
        val from = dragIndex
        val to = targetIndex
        val snapshot = rows
        dragging = false
        rows = emptyList()
        dragIndex = -1
        targetIndex = -1
        snapshot.forEach {
            it.view.animate().cancel()
            it.view.translationY = 0f
            it.view.alpha = 1f
            it.view.elevation = 0f
        }
        if (!drop || from < 0 || to < 0 || from == to) return

        val list = ArrayList<Array<String>>(DataUtils.getInstance().books)
        BookmarkOrder.sort(prefs, list)
        val a = list.indexOfFirst { it[0] == snapshot[from].name }
        val b = list.indexOfFirst { it[0] == snapshot[to].name }
        if (a < 0 || b < 0) return
        val item = list.removeAt(a)
        list.add(b, item)
        BookmarkOrder.save(prefs, list)
        onOrderChanged.run()
    }
}
