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

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceManager
import com.amaze.filemanager.R
import com.amaze.filemanager.TagsHelper
import com.amaze.filemanager.application.AppConfig
import com.amaze.filemanager.database.UtilsHandler
import com.amaze.filemanager.database.models.OperationData
import com.amaze.filemanager.ui.activities.MainActivity
import com.amaze.filemanager.utils.DataUtils
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupPrefsFragment : BasePrefsFragment() {
    private val TAG: String = TagsHelper.getTag(BasePrefsFragment::class.java)
    private val log: Logger = LoggerFactory.getLogger(BackupPrefsFragment::class.java)

    companion object {
        val IMPORT_BACKUP_FILE: Int = 2
        private const val BOOKMARKS_KEY = "fileosophy_bookmarks"
    }

    override val title = R.string.backup

    /**
     * A fresh name per export: saving over an existing file written by an earlier install is refused
     * by Android, which used to make re-exporting fail.
     */
    private fun backupFileName(): String =
        "fileosophy_backup_" + SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date()) + ".json"

    /** Export app settings to a JSON file */
    fun exportPrefs() {
        val map: MutableMap<String?, Any?> =
            PreferenceManager
                .getDefaultSharedPreferences(requireActivity()).all.toMutableMap()
        // Bookmarks live in a database, not in the preferences, so they are added alongside
        map[BOOKMARKS_KEY] = AppConfig.getInstance().utilsHandler.bookmarksList.map { it.toList() }

        val gsonString: String = Gson().toJson(map)

        try {
            val file = File(context?.cacheDir?.absolutePath + File.separator + backupFileName())

            val fileWriter = FileWriter(file)

            fileWriter.append(gsonString)

            Log.i(TAG, "wrote export to: ${file.absolutePath}")

            fileWriter.flush()
            fileWriter.close()

            Toast.makeText(
                context,
                getString(R.string.select_save_location),
                Toast.LENGTH_SHORT,
            ).show()

            val intent = Intent(context, MainActivity::class.java)

            intent.action = Intent.ACTION_SEND
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))

            startActivity(intent)
        } catch (e: IOException) {
            Toast.makeText(context, getString(R.string.exporting_failed), Toast.LENGTH_SHORT).show()
            log.error(getString(R.string.exporting_failed), e)
        }
    }

    /** Import app settings from a JSON file */
    private fun importPrefs() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*"),
                IMPORT_BACKUP_FILE,
            )
        } else {
            startActivityForResult(
                Intent.createChooser(
                    Intent(Intent.ACTION_GET_CONTENT)
                        .setType("application/json"),
                    "Choose backup file",
                ),
                IMPORT_BACKUP_FILE,
            )
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        val nonNull = data != null && data.data != null

        if (requestCode == IMPORT_BACKUP_FILE &&
            resultCode == Activity.RESULT_OK &&
            nonNull
        ) {
            val uri = data!!.data

            Log.i(TAG, "read import file: $uri")

            try {
                val inputStream =
                    uri?.let {
                        context?.contentResolver?.openInputStream(it)
                    }

                val text = inputStream?.bufferedReader()?.use { it.readText() } ?: throw IOException("empty")
                val json = JsonParser.parseString(text).asJsonObject

                val prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity())
                val editor: SharedPreferences.Editor = prefs.edit()

                val bookmarks = json.remove(BOOKMARKS_KEY)

                for ((key, value) in json.entrySet())
                    if (key.isNotEmpty()) storePreference(editor, key, value, prefs.all[key])

                if (bookmarks != null && bookmarks.isJsonArray) restoreBookmarks(bookmarks.asJsonArray)

                editor.apply()

                Toast.makeText(
                    context,
                    getString(R.string.importing_completed),
                    Toast.LENGTH_SHORT,
                ).show()

                startActivity(
                    Intent(
                        context,
                        MainActivity::class.java,
                    ),
                ) // restart Amaze for changes to take effect
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    getString(R.string.importing_failed),
                    Toast.LENGTH_SHORT,
                ).show()
                log.error(getString(R.string.importing_failed), e)
            }
        } else if (resultCode != Activity.RESULT_CANCELED) {
            Toast.makeText(context, getString(R.string.unknown_error), Toast.LENGTH_SHORT).show()
        }
    }

    /** Adds the backed-up bookmarks; ones that already exist are left alone. */
    private fun restoreBookmarks(bookmarks: JsonArray) {
        val handler = AppConfig.getInstance().utilsHandler
        for (entry in bookmarks) {
            val pair = entry.asJsonArray
            handler.saveToDatabase(
                OperationData(UtilsHandler.Operation.BOOKMARKS, pair[0].asString, pair[1].asString),
            ).blockingAwait()
        }
        DataUtils.getInstance().setBooks(handler.bookmarksList)
    }

    /**
     * Stores one backed-up value. JSON does not keep number types, so an existing preference with
     * the same key decides between Int, Long and Float.
     */
    private fun storePreference(
        editor: SharedPreferences.Editor,
        key: String,
        value: JsonElement,
        current: Any?,
    ) {
        try {
            when {
                value.isJsonArray ->
                    editor.putStringSet(key, value.asJsonArray.map { it.asString }.toSet())
                value.isJsonPrimitive && value.asJsonPrimitive.isBoolean ->
                    editor.putBoolean(key, value.asBoolean)
                value.isJsonPrimitive && value.asJsonPrimitive.isString ->
                    editor.putString(key, value.asString)
                value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> storeNumber(editor, key, value, current)
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "${getString(R.string.import_failed_for)} $key",
                Toast.LENGTH_SHORT,
            ).show()
            log.error("${getString(R.string.import_failed_for)} $key", e)
        }
    }

    private fun storeNumber(
        editor: SharedPreferences.Editor,
        key: String,
        value: JsonElement,
        current: Any?,
    ) {
        val number = value.asBigDecimal
        when {
            current is Long -> editor.putLong(key, number.toLong())
            current is Float -> editor.putFloat(key, number.toFloat())
            current is Int -> editor.putInt(key, number.toInt())
            number.scale() > 0 -> editor.putFloat(key, number.toFloat())
            number.toLong() in Int.MIN_VALUE..Int.MAX_VALUE -> editor.putInt(key, number.toInt())
            else -> editor.putLong(key, number.toLong())
        }
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.backup_prefs, rootKey)

        findPreference<Preference>(
            PreferencesConstants.PREFERENCE_EXPORT_SETTINGS,
        )?.onPreferenceClickListener =
            OnPreferenceClickListener {
                exportPrefs()
                true
            }

        findPreference<Preference>(
            PreferencesConstants.PREFERENCE_IMPORT_SETTINGS,
        )?.onPreferenceClickListener =
            OnPreferenceClickListener {
                importPrefs()
                true
            }
    }
}
