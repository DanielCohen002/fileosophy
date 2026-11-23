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

package com.amaze.filemanager.utils

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.amaze.filemanager.BuildConfig
import com.amaze.filemanager.R
import com.amaze.filemanager.ui.activities.MainActivity
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URL

/** Looks for a newer Fileosophy release on GitHub when asked, and opens its page. Never downloads or installs anything itself. */
object UpdateChecker {
    private val log = LoggerFactory.getLogger(UpdateChecker::class.java)

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/DanielCohen002/fileosophy/releases/latest"
    private const val TIMEOUT_MS = 15_000


    private class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
    )

    fun checkForUpdate(activity: MainActivity) {
        Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show()
        Thread {
            val release = runCatching { fetchLatestRelease() }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                release
                    .onSuccess { showResult(activity, it) }
                    .onFailure {
                        log.warn("failed to check for update", it)
                        Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_LONG)
                            .show()
                    }
            }
        }.start()
    }

    private fun fetchLatestRelease(): Release {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Fileosophy")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("GitHub answered ${connection.responseCode}")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = json.getJSONArray("assets")
            val apkUrl =
                (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") }
                    ?.getString("browser_download_url")
                    ?: json.getString("html_url")
            return Release(
                json.getString("tag_name").removePrefix("v"),
                json.optString("body"),
                apkUrl,
            )
        } finally {
            connection.disconnect()
        }
    }

    /** true when [latest] has a higher number than [current], comparing 1.12.7 style versions */
    @JvmStatic
    fun isNewer(
        latest: String,
        current: String,
    ): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val difference = latestParts.getOrElse(i) { 0 } - currentParts.getOrElse(i) { 0 }
            if (difference != 0) return difference > 0
        }
        return false
    }

    private fun showResult(
        activity: MainActivity,
        release: Release,
    ) {
        if (!isNewer(release.version, BuildConfig.VERSION_NAME)) {
            Toast.makeText(
                activity,
                activity.getString(R.string.update_up_to_date, BuildConfig.VERSION_NAME),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        MaterialDialog.Builder(activity)
            .theme(activity.appTheme.materialDialogTheme)
            .title(activity.getString(R.string.update_available, release.version))
            .content(release.notes.ifBlank { activity.getString(R.string.update_no_notes) })
            .positiveText(R.string.update_download)
            .negativeText(R.string.update_later)
            .onPositive { _, _ ->
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl)))
                } catch (_: Exception) {
                }
            }
            .show()
    }
}
