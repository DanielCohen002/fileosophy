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

package com.amaze.filemanager.ui.fragments.preferencefragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.folderselector.FolderChooserDialog
import com.amaze.filemanager.ui.activities.PreferencesActivity
import java.io.File

abstract class BasePrefsFragment : PreferenceFragmentCompat(), FolderChooserDialog.FolderCallback {
    protected val activity: PreferencesActivity
        get() = requireActivity() as PreferencesActivity

    abstract val title: Int

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // Opened from a settings search result: bring the setting into view and blink it
        val key = arguments?.getString(ARG_SCROLL_TO_KEY)
        if (savedInstanceState == null && key != null) {
            view.post { scrollToAndBlink(key) }
        }
    }

    /** Scrolls the setting with [key] to the top of the list and blinks its row. */
    private fun scrollToAndBlink(key: String) {
        val position =
            (listView.adapter as? PreferenceGroup.PreferencePositionCallback)
                ?.getPreferenceAdapterPosition(key) ?: return
        if (position == RecyclerView.NO_POSITION) return

        (listView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
        // wait for the layout pass so the row view exists
        listView.postDelayed(
            {
                if (!isAdded) return@postDelayed
                val row =
                    listView.findViewHolderForAdapterPosition(position)?.itemView
                        ?: return@postDelayed
                val originalBackground = row.background
                ValueAnimator.ofObject(
                    ArgbEvaluator(),
                    Color.TRANSPARENT,
                    ColorUtils.setAlphaComponent(activity.accent, BLINK_ALPHA),
                ).apply {
                    duration = BLINK_DURATION_MS
                    repeatCount = BLINK_REPEAT_COUNT
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener { row.setBackgroundColor(it.animatedValue as Int) }
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                row.background = originalBackground
                            }
                        },
                    )
                    start()
                }
            },
            BLINK_DELAY_MS,
        )
    }

    override fun onResume() {
        super.onResume()

        activity.supportActionBar?.title = getString(title)
    }

    override fun onFolderSelection(
        dialog: FolderChooserDialog,
        folder: File,
    ) {
        dialog.dismiss()
    }

    override fun onFolderChooserDismissed(dialog: FolderChooserDialog) {
        dialog.dismiss()
    }

    companion object {
        const val ARG_SCROLL_TO_KEY = "scrollToKey"

        // same blink as the file list uses for a file it jumped to
        private const val BLINK_ALPHA = 150
        private const val BLINK_DURATION_MS = 180L
        private const val BLINK_REPEAT_COUNT = 3
        private const val BLINK_DELAY_MS = 200L
    }
}
