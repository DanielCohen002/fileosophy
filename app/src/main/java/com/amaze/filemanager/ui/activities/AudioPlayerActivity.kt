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

import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import androidx.preference.PreferenceManager
import com.amaze.filemanager.R
import com.amaze.filemanager.ui.activities.superclasses.BasicActivity
import com.amaze.filemanager.ui.theme.AppTheme

/** Minimal built-in audio player: plays the one file that was opened, with seek and pause. */
class AudioPlayerActivity : BasicActivity() {
    private var player: MediaPlayer? = null
    private var prepared = false
    private var resumeAfterFocusLoss = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var playPause: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var elapsed: TextView
    private lateinit var total: TextView
    private lateinit var audioManager: AudioManager

    private val focusListener =
        AudioManager.OnAudioFocusChangeListener { change ->
            val p = player ?: return@OnAudioFocusChangeListener
            if (!prepared) return@OnAudioFocusChangeListener
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeAfterFocusLoss = false
                    p.pause()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    resumeAfterFocusLoss = p.isPlaying
                    p.pause()
                }
                AudioManager.AUDIOFOCUS_GAIN -> if (resumeAfterFocusLoss) p.start()
            }
            updatePlayPauseIcon()
        }

    private val progressTicker =
        object : Runnable {
            override fun run() {
                val p = player
                if (p != null && prepared && !seekBarDragging) {
                    seekBar.progress = p.currentPosition
                    elapsed.text = formatTime(p.currentPosition)
                }
                handler.postDelayed(this, PROGRESS_INTERVAL_MS)
            }
        }

    private var seekBarDragging = false

    override fun finish() {
        startMainIfTaskRoot()
        super.finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent.data
        if (uri == null) {
            finish()
            return
        }
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val accent =
            colorPreference
                .getCurrentUserColorPreferences(this, PreferenceManager.getDefaultSharedPreferences(this))
                .accent
        val (surfaceColor, textColor, secondaryTextColor) =
            when (appTheme) {
                AppTheme.LIGHT -> Triple(Color.WHITE, Color.BLACK, Color.DKGRAY)
                AppTheme.BLACK -> Triple(Color.BLACK, Color.WHITE, Color.LTGRAY)
                else -> Triple(Color.parseColor("#303030"), Color.WHITE, Color.LTGRAY)
            }

        val title = TextView(this)
        title.text = intent.getStringExtra(EXTRA_TITLE) ?: uri.lastPathSegment?.substringAfterLast('/')
        title.setTextColor(textColor)
        title.textSize = 20f
        title.gravity = Gravity.CENTER
        title.setPadding(48, 0, 48, 48)

        seekBar = SeekBar(this)
        seekBar.progressTintList = ColorStateList.valueOf(accent)
        seekBar.thumbTintList = ColorStateList.valueOf(accent)
        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) elapsed.text = formatTime(progress)
                }

                override fun onStartTrackingTouch(bar: SeekBar) {
                    seekBarDragging = true
                }

                override fun onStopTrackingTouch(bar: SeekBar) {
                    seekBarDragging = false
                    if (prepared) player?.seekTo(bar.progress)
                }
            },
        )

        elapsed = timeText(secondaryTextColor)
        total = timeText(secondaryTextColor)
        val times = LinearLayout(this)
        times.orientation = LinearLayout.HORIZONTAL
        times.addView(elapsed, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        total.gravity = Gravity.END
        times.addView(total, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        playPause = ImageButton(this)
        playPause.setBackgroundColor(Color.TRANSPARENT)
        playPause.setColorFilter(accent)
        playPause.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        playPause.setOnClickListener { togglePlayback() }
        playPause.contentDescription = getString(R.string.audio_play_pause)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(48, 48, 48, 48)
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(seekBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(times, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(playPause, LinearLayout.LayoutParams(PLAY_BUTTON_SIZE, PLAY_BUTTON_SIZE).apply { topMargin = 48 })
        setContentView(root)
        // Popup over the file list rather than a full-screen page, with rounded corners
        window.setBackgroundDrawable(
            GradientDrawable().apply {
                setColor(surfaceColor)
                cornerRadius =
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        CORNER_RADIUS_DP,
                        resources.displayMetrics,
                    )
            },
        )
        window.setLayout((resources.displayMetrics.widthPixels * POPUP_WIDTH_FRACTION).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        updatePlayPauseIcon()

        startPlayback(uri)
    }

    private fun timeText(color: Int): TextView {
        val t = TextView(this)
        t.setTextColor(color)
        t.text = formatTime(0)
        return t
    }

    private fun startPlayback(uri: Uri) {
        val p = MediaPlayer()
        player = p
        try {
            p.setDataSource(this, uri)
            p.setOnPreparedListener {
                prepared = true
                seekBar.max = it.duration
                total.text = formatTime(it.duration)
                if (requestAudioFocus()) it.start()
                updatePlayPauseIcon()
            }
            p.setOnCompletionListener {
                seekBar.progress = seekBar.max
                elapsed.text = formatTime(seekBar.max)
                updatePlayPauseIcon()
            }
            p.setOnErrorListener { _, _, _ ->
                Toast.makeText(this, R.string.audio_open_failed, Toast.LENGTH_LONG).show()
                finish()
                true
            }
            p.prepareAsync()
            handler.post(progressTicker)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.audio_open_failed, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocus(): Boolean =
        audioManager.requestAudioFocus(
            focusListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN,
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    private fun togglePlayback() {
        val p = player ?: return
        if (!prepared) return
        if (p.isPlaying) {
            p.pause()
        } else {
            // Restart from the beginning once a finished track is played again
            if (p.currentPosition >= p.duration) p.seekTo(0)
            if (requestAudioFocus()) p.start()
        }
        updatePlayPauseIcon()
    }

    private fun updatePlayPauseIcon() {
        val playing = player?.takeIf { prepared }?.isPlaying == true
        playPause.setImageResource(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
    }

    private fun formatTime(millis: Int): String {
        val seconds = millis / 1000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        handler.removeCallbacks(progressTicker)
        audioManager.abandonAudioFocus(focusListener)
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        private const val PROGRESS_INTERVAL_MS = 250L
        private const val PLAY_BUTTON_SIZE = 220
        private const val POPUP_WIDTH_FRACTION = 0.9f
        private const val CORNER_RADIUS_DP = 28f
    }
}
