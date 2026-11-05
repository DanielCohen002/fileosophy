/*
 * Copyright (C) 2014-2026 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.ui.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.SparseArray
import android.view.GestureDetector
import android.view.Gravity
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.OverScroller
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.amaze.filemanager.R
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Minimal built-in PDF reader: pages rendered with [PdfRenderer] into a single custom view that
 * handles pan, fling, pinch zoom and double tap itself, plus a page indicator and share.
 */
class PdfViewerActivity : AppCompatActivity() {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    // PdfRenderer allows only one open page at a time and is not thread safe
    private val renderExecutor = Executors.newSingleThreadExecutor()
    private lateinit var pagesView: PdfPagesView
    private lateinit var pageIndicator: TextView

    @Volatile private var destroyed = false

    private val hideIndicator = Runnable { pageIndicator.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent.data
        if (uri == null) {
            finish()
            return
        }

        val toolbar = Toolbar(this)
        toolbar.setBackgroundColor(Color.parseColor("#202020"))
        toolbar.setTitleTextColor(Color.WHITE)
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        toolbar.navigationIcon?.setTint(Color.WHITE)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = intent.getStringExtra(EXTRA_TITLE) ?: uri.lastPathSegment?.substringAfterLast('/')
        toolbar.menu.add(R.string.share).apply {
            setIcon(R.drawable.ic_share_white_24dp)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType("application/pdf")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        getString(R.string.share),
                    ),
                )
                true
            }
        }

        pagesView = PdfPagesView(this)
        pageIndicator = TextView(this)
        pageIndicator.setTextColor(Color.WHITE)
        pageIndicator.setBackgroundColor(Color.parseColor("#B0000000"))
        pageIndicator.setPadding(32, 12, 32, 12)
        pageIndicator.visibility = View.GONE

        val content = FrameLayout(this)
        content.addView(pagesView)
        content.addView(
            pageIndicator,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply { bottomMargin = 48 },
        )

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.fitsSystemWindows = true
        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        open(uri)
    }

    private fun open(uri: Uri) {
        renderExecutor.execute {
            try {
                val descriptor =
                    contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("no descriptor")
                pfd = descriptor
                val r = PdfRenderer(descriptor)
                renderer = r
                val sizes = (0 until r.pageCount).map { i -> r.openPage(i).use { it.width to it.height } }
                runOnUiThread { if (!destroyed) pagesView.setPages(sizes) }
            } catch (e: SecurityException) {
                fail(R.string.pdf_password_protected)
            } catch (e: Exception) {
                fail(R.string.pdf_open_failed)
            }
        }
    }

    override fun finish() {
        startMainIfTaskRoot()
        super.finish()
    }

    private fun fail(message: Int) =
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            finish()
        }

    private fun showPageIndicator(page: Int, total: Int) {
        pageIndicator.text = getString(R.string.pdf_page_indicator, page + 1, total)
        pageIndicator.visibility = View.VISIBLE
        pageIndicator.removeCallbacks(hideIndicator)
        pageIndicator.postDelayed(hideIndicator, 1500)
    }

    override fun onDestroy() {
        destroyed = true
        super.onDestroy()
        renderExecutor.execute {
            try {
                renderer?.close()
                pfd?.close()
            } catch (_: Exception) {
            }
        }
        renderExecutor.shutdown()
    }

    /**
     * Draws all pages stacked vertically at fit-width, under one pan/zoom transform. Zooming only
     * changes the transform, so it is always smooth; sharper bitmaps are rendered once it settles.
     */
    private inner class PdfPagesView(context: Context) : View(context) {
        private var pageSizes: List<Pair<Int, Int>> = emptyList()
        private var tops = FloatArray(0)
        private var heights = FloatArray(0)
        private var docHeight = 0f
        private var scale = 1f
        private var tx = 0f
        private var ty = 0f
        private var scaling = false
        private var lastPage = -1

        private val flingScroller = OverScroller(context)
        private var zoomAnimator: ValueAnimator? = null
        private val dst = RectF()
        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val whitePaint = Paint().apply { color = Color.WHITE }
        private val pending = HashSet<Long>()
        private val baseCache = LruCache<Int, Bitmap>(cacheBytes(16))
        private val sharpCache = LruCache<Long, Bitmap>(cacheBytes(8))

        // last sharp bitmap per visible page, shown while a newer one renders
        private val sharpByPage = SparseArray<Bitmap>()

        private val scaleDetector =
            ScaleGestureDetector(
                context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                        scaling = true
                        flingScroller.forceFinished(true)
                        return true
                    }

                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        zoomTo(scale * detector.scaleFactor, detector.focusX, detector.focusY, tx, ty, scale)
                        return true
                    }

                    override fun onScaleEnd(detector: ScaleGestureDetector) {
                        scaling = false
                        invalidate()
                    }
                },
            ).apply { isQuickScaleEnabled = false }

        private val gestureDetector =
            GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean {
                        flingScroller.forceFinished(true)
                        zoomAnimator?.cancel()
                        return true
                    }

                    override fun onScroll(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        distanceX: Float,
                        distanceY: Float,
                    ): Boolean {
                        tx -= distanceX
                        ty -= distanceY
                        clampTransform()
                        onTransformChanged()
                        return true
                    }

                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float,
                    ): Boolean {
                        if (scaling) return false
                        val contentWidth = width * scale
                        val contentHeight = docHeight * scale
                        val minX = if (contentWidth <= width) tx.toInt() else (width - contentWidth).toInt()
                        val maxX = if (contentWidth <= width) tx.toInt() else 0
                        val minY = if (contentHeight <= height) 0 else (height - contentHeight).toInt()
                        flingScroller.fling(
                            tx.toInt(),
                            ty.toInt(),
                            velocityX.toInt(),
                            velocityY.toInt(),
                            minX,
                            maxX,
                            minY,
                            0,
                        )
                        postInvalidateOnAnimation()
                        return true
                    }

                    // Fires on the second tap's lift: onDoubleTap runs before that tap's onDown,
                    // which cancels zoom animations, so starting the animation there would be undone
                    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                        if (e.actionMasked == MotionEvent.ACTION_UP) {
                            val target = if (abs(scale - 1f) > 0.05f) 1f else DOUBLE_TAP_ZOOM
                            animateZoomTo(target, e.x, e.y)
                        }
                        return true
                    }
                },
            )

        private fun cacheBytes(divisor: Int) = (Runtime.getRuntime().maxMemory() / divisor).toInt()

        fun setPages(sizes: List<Pair<Int, Int>>) {
            pageSizes = sizes
            layoutPages()
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            layoutPages()
        }

        private fun layoutPages() {
            if (width == 0 || pageSizes.isEmpty()) return
            tops = FloatArray(pageSizes.size)
            heights = FloatArray(pageSizes.size)
            var y = 0f
            pageSizes.forEachIndexed { i, (pw, ph) ->
                tops[i] = y
                heights[i] = width.toFloat() * ph / pw
                y += heights[i] + PAGE_GAP
            }
            docHeight = y - PAGE_GAP
            clampTransform()
        }

        private fun clampTransform() {
            val contentWidth = width * scale
            tx =
                if (contentWidth <= width) {
                    (width - contentWidth) / 2f
                } else {
                    tx.coerceIn(width - contentWidth, 0f)
                }
            val contentHeight = docHeight * scale
            ty = if (contentHeight <= height) 0f else ty.coerceIn(height - contentHeight, 0f)
        }

        /** Zooms to [newScale] keeping the content under ([fx], [fy]) fixed, relative to the given start state. */
        private fun zoomTo(
            newScale: Float,
            fx: Float,
            fy: Float,
            fromTx: Float,
            fromTy: Float,
            fromScale: Float,
        ) {
            val clamped = newScale.coerceIn(MIN_ZOOM, MAX_ZOOM)
            val ratio = clamped / fromScale
            tx = fx - (fx - fromTx) * ratio
            ty = fy - (fy - fromTy) * ratio
            scale = clamped
            clampTransform()
            onTransformChanged()
        }

        private fun animateZoomTo(target: Float, fx: Float, fy: Float) {
            val fromScale = scale
            val fromTx = tx
            val fromTy = ty
            zoomAnimator?.cancel()
            scaling = true
            zoomAnimator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 220
                    addUpdateListener {
                        val f = it.animatedFraction
                        zoomTo(fromScale + (target - fromScale) * f, fx, fy, fromTx, fromTy, fromScale)
                    }
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                scaling = false
                                invalidate()
                            }
                        },
                    )
                    start()
                }
        }

        private fun onTransformChanged() {
            invalidate()
            if (pageSizes.isEmpty() || heights.isEmpty()) return
            val centerY = (height / 2f - ty) / scale
            val page = min(tops.indexOfLast { it <= centerY }.coerceAtLeast(0), pageSizes.size - 1)
            if (page != lastPage) {
                lastPage = page
                showPageIndicator(page, pageSizes.size)
            }
        }

        override fun computeScroll() {
            if (flingScroller.computeScrollOffset()) {
                tx = flingScroller.currX.toFloat()
                ty = flingScroller.currY.toFloat()
                clampTransform()
                onTransformChanged()
                postInvalidateOnAnimation()
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            return true
        }

        /** Width in pixels sharp bitmaps should have at the current zoom. */
        private fun sharpWidth(): Int =
            if (scale <= 1.05f) width else (ceil(width * scale / SHARP_STEP) * SHARP_STEP).toInt()

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.parseColor("#404040"))
            if (heights.isEmpty()) return
            val visibleTop = -ty / scale
            val visibleBottom = (height - ty) / scale
            val wantSharp = sharpWidth()
            canvas.save()
            canvas.translate(tx, ty)
            canvas.scale(scale, scale)
            for (i in pageSizes.indices) {
                val top = tops[i]
                if (top + heights[i] < visibleTop || top > visibleBottom) {
                    sharpByPage.remove(i)
                    continue
                }
                dst.set(0f, top, width.toFloat(), top + heights[i])
                canvas.drawRect(dst, whitePaint)
                val sharp = sharpCache.get(sharpKey(i, wantSharp))
                if (sharp != null) sharpByPage.put(i, sharp)
                val bitmap = sharp ?: sharpByPage.get(i) ?: baseCache.get(i)
                if (bitmap != null) canvas.drawBitmap(bitmap, null, dst, bitmapPaint)
                if (!scaling) {
                    if (baseCache.get(i) == null) request(i, width)
                    if (wantSharp > width && sharp == null) request(i, wantSharp)
                }
            }
            canvas.restore()
        }

        private fun sharpKey(page: Int, targetWidth: Int) = page.toLong() * 100_000L + targetWidth

        private fun request(page: Int, targetWidth: Int) {
            val key = sharpKey(page, targetWidth)
            if (!pending.add(key)) return
            val (pw, ph) = pageSizes[page]
            // Beyond the edge cap the bitmap is rendered smaller and upscaled, to bound memory
            val fullHeight = targetWidth.toFloat() * ph / pw
            val shrink = min(1f, MAX_BITMAP_EDGE / max(targetWidth.toFloat(), fullHeight))
            val bitmapWidth = max(1, (targetWidth * shrink).toInt())
            val bitmapHeight = max(1, (fullHeight * shrink).toInt())
            renderExecutor.execute {
                if (destroyed) return@execute
                val bitmap =
                    try {
                        val bmp = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        renderer!!.openPage(page).use {
                            it.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                        bmp
                    } catch (_: Exception) {
                        null
                    } catch (_: OutOfMemoryError) {
                        null
                    }
                post {
                    pending.remove(key)
                    if (bitmap == null) return@post
                    if (targetWidth == width) baseCache.put(page, bitmap) else sharpCache.put(key, bitmap)
                    invalidate()
                }
            }
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        private const val MIN_ZOOM = 0.5f
        private const val MAX_ZOOM = 10f
        private const val DOUBLE_TAP_ZOOM = 2.5f
        private const val PAGE_GAP = 8f
        private const val SHARP_STEP = 256
        private const val MAX_BITMAP_EDGE = 4096f
    }
}
