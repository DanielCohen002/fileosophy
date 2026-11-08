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
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.afollestad.materialdialogs.MaterialDialog
import com.amaze.filemanager.BuildConfig
import com.amaze.filemanager.R
import com.amaze.filemanager.ui.activities.MainActivity
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Looks for a newer Fileosophy release on GitHub, downloads it and hands it to the installer. */
object UpdateChecker {
    private val log = LoggerFactory.getLogger(UpdateChecker::class.java)

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/DanielCohen002/fileosophy/releases/latest"
    private const val TIMEOUT_MS = 15_000
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val UPDATES_DIRECTORY = "updates"

    private const val PREFERENCE_CHANGELOG_SHOWN_VERSION = "update_changelog_shown_version"
    private const val PREFERENCE_CHANGELOG_VERSION = "update_changelog_version"
    private const val PREFERENCE_CHANGELOG_NOTES = "update_changelog_notes"

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

    /**
     * Shows the notes of the release that was installed through [checkForUpdate], once, the first
     * time the new version starts.
     */
    @JvmStatic
    fun showChangelogIfUpdated(activity: MainActivity) {
        val prefs = activity.prefs
        val current = BuildConfig.VERSION_NAME
        if (prefs.getString(PREFERENCE_CHANGELOG_VERSION, "") != current) return
        if (prefs.getString(PREFERENCE_CHANGELOG_SHOWN_VERSION, "") == current) return
        prefs.edit().putString(PREFERENCE_CHANGELOG_SHOWN_VERSION, current).apply()

        val notes = prefs.getString(PREFERENCE_CHANGELOG_NOTES, "").orEmpty()
        MaterialDialog.Builder(activity)
            .theme(activity.appTheme.materialDialogTheme)
            .title(activity.getString(R.string.update_whats_new, current))
            .content(notes.ifBlank { activity.getString(R.string.update_no_notes) })
            .positiveText(R.string.update_ok)
            .show()
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
                    ?: throw IllegalStateException("release has no apk")
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
            .positiveText(R.string.update_download_install)
            .negativeText(R.string.update_later)
            .onPositive { _, _ -> download(activity, release) }
            .show()
    }

    private fun download(
        activity: MainActivity,
        release: Release,
    ) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            // the user comes back and taps the button again once this is allowed
            Toast.makeText(activity, R.string.update_allow_installs, Toast.LENGTH_LONG).show()
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
            return
        }

        val progress =
            MaterialDialog.Builder(activity)
                .theme(activity.appTheme.materialDialogTheme)
                .title(activity.getString(R.string.update_downloading, release.version))
                .progress(false, 100)
                .cancelable(false)
                .show()
        Thread {
            val apk = runCatching { downloadApk(activity, release, progress) }
            activity.runOnUiThread {
                progress.dismiss()
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                apk
                    .onSuccess {
                        rememberChangelog(activity, release)
                        install(activity, it)
                    }
                    .onFailure {
                        log.warn("failed to download update", it)
                        Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG)
                            .show()
                    }
            }
        }.start()
    }

    private fun rememberChangelog(
        activity: MainActivity,
        release: Release,
    ) {
        activity.prefs.edit()
            .putString(PREFERENCE_CHANGELOG_VERSION, release.version)
            .putString(PREFERENCE_CHANGELOG_NOTES, release.notes)
            .apply()
    }

    private fun downloadApk(
        activity: MainActivity,
        release: Release,
        progress: MaterialDialog,
    ): File {
        val directory = File(activity.getExternalFilesDir(null), UPDATES_DIRECTORY)
        directory.deleteRecursively()
        directory.mkdirs()
        val target = File(directory, "fileosophy-${release.version}.apk")

        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Fileosophy")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("download answered ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            var received = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        if (total > 0) {
                            activity.runOnUiThread {
                                progress.setProgress((received * 100 / total).toInt())
                            }
                        }
                    }
                }
            }
            if (total > 0 && received != total) throw IllegalStateException("download cut short")
        } finally {
            connection.disconnect()
        }
        return target
    }

    private fun install(
        activity: MainActivity,
        apk: File,
    ) {
        val uri = FileProvider.getUriForFile(activity, activity.packageName, apk)
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}
