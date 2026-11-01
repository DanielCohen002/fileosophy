package com.amaze.filemanager.ui.views.drawer

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amaze.filemanager.utils.BookmarkOrder
import com.amaze.filemanager.utils.DataUtils

/**
 * Long-press a bookmark in the sidebar: the phone vibrates, then the row follows your finger.
 * Drop it on another bookmark to move it to that spot.
 */
class BookmarkDragHelper(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val onOrderChanged: Runnable,
) : RecyclerView.OnItemTouchListener {
    private var dragging = false
    private var dragView: View? = null
    private var dragName: String? = null
    private var startY = 0f
    private var detector: GestureDetector? = null

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
        val v = rv.findChildViewUnder(e.x, e.y) ?: return
        val name = bookmarkName(v) ?: return
        dragging = true
        dragView = v
        dragName = name
        startY = e.y
        v.alpha = 0.7f
        v.elevation = 12f
        rv.parent?.requestDisallowInterceptTouchEvent(true)
        vibrate()
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vib.vibrate(60)
        }
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
            MotionEvent.ACTION_MOVE -> dragView?.translationY = e.y - startY
            MotionEvent.ACTION_UP -> finish(rv, e, true)
            MotionEvent.ACTION_CANCEL -> finish(rv, e, false)
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

    private fun finish(
        rv: RecyclerView,
        e: MotionEvent,
        drop: Boolean,
    ) {
        val view = dragView
        val name = dragName
        dragging = false
        dragView = null
        dragName = null
        if (view != null) {
            view.translationY = 0f
            view.alpha = 1f
            view.elevation = 0f
        }
        if (!drop || name == null) return
        val targetName = bookmarkName(rv.findChildViewUnder(e.x, e.y)) ?: return
        if (targetName == name) return
        val list = ArrayList<Array<String>>(DataUtils.getInstance().books)
        BookmarkOrder.sort(prefs, list)
        val from = list.indexOfFirst { it[0] == name }
        val to = list.indexOfFirst { it[0] == targetName }
        if (from < 0 || to < 0) return
        val item = list.removeAt(from)
        list.add(to, item)
        BookmarkOrder.save(prefs, list)
        onOrderChanged.run()
    }
}
