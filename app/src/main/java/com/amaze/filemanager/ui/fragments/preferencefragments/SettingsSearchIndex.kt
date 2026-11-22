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

import android.content.Context
import android.util.AttributeSet
import android.util.Xml
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import com.amaze.filemanager.R
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/**
 * One searchable setting, or a whole settings screen when [key] is null.
 *
 * @property screenTitle title of the screen the setting lives on
 * @property newScreen creates the screen to open for this result
 */
class SettingsSearchEntry(
    val key: String?,
    val title: String,
    val summary: String?,
    @StringRes val screenTitle: Int,
    val newScreen: () -> BasePrefsFragment,
)

/**
 * Builds the list of searchable settings straight from the preference XML files, so a setting
 * added to one of them is searchable without further work.
 */
object SettingsSearchIndex {
    private const val APP_NAMESPACE = "http://schemas.android.com/apk/res-auto"
    private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

    private class Screen(
        @XmlRes val xml: Int,
        @StringRes val title: Int,
        val newScreen: () -> BasePrefsFragment,
    )

    private val screens =
        listOf(
            Screen(R.xml.appearance_prefs, R.string.appearance) { AppearancePrefsFragment() },
            Screen(R.xml.ui_prefs, R.string.ui) { UiPrefsFragment() },
            Screen(R.xml.behavior_prefs, R.string.behavior) { BehaviorPrefsFragment() },
            Screen(R.xml.security_prefs, R.string.security) { SecurityPrefsFragment() },
            Screen(R.xml.backup_prefs, R.string.backup) { BackupPrefsFragment() },
            Screen(R.xml.color_prefs, R.string.color_title) { ColorPrefsFragment() },
            Screen(R.xml.bookmarks_prefs, R.string.show_bookmarks_pref) {
                BookmarksPrefsFragment()
            },
            Screen(R.xml.quickaccess_prefs, R.string.show_quick_access_pref) {
                QuickAccessesPrefsFragment()
            },
        )

    @JvmStatic
    fun build(context: Context): List<SettingsSearchEntry> =
        screens.flatMap { screen ->
            val screenEntry =
                SettingsSearchEntry(
                    null,
                    context.getString(screen.title),
                    null,
                    screen.title,
                    screen.newScreen,
                )
            listOf(screenEntry) + readSettings(context, screen)
        }

    /** Every word of [query] has to appear in the title or the summary of an entry. */
    @JvmStatic
    fun search(
        entries: List<SettingsSearchEntry>,
        query: String,
    ): List<SettingsSearchEntry> {
        val words = query.lowercase(Locale.getDefault()).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        return entries.filter { entry ->
            val haystack = "${entry.title} ${entry.summary.orEmpty()}".lowercase(Locale.getDefault())
            words.all { haystack.contains(it) }
        }
    }

    private fun readSettings(
        context: Context,
        screen: Screen,
    ): List<SettingsSearchEntry> {
        val settings = mutableListOf<SettingsSearchEntry>()
        val parser = context.resources.getXml(screen.xml)
        try {
            val attributes = Xml.asAttributeSet(parser)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name !in GROUP_TAGS) {
                    val key = attributes.read(context, "key")
                    val title = attributes.read(context, "title")
                    if (key != null && title != null) {
                        settings +=
                            SettingsSearchEntry(
                                key,
                                title,
                                attributes.read(context, "summary"),
                                screen.title,
                                screen.newScreen,
                            )
                    }
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }
        return settings
    }

    private val GROUP_TAGS = setOf("PreferenceScreen", "PreferenceCategory")

    /** Reads an attribute that is either a string resource reference or a literal string. */
    private fun AttributeSet.read(
        context: Context,
        name: String,
    ): String? {
        for (namespace in listOf(APP_NAMESPACE, ANDROID_NAMESPACE)) {
            val resourceId = getAttributeResourceValue(namespace, name, 0)
            if (resourceId != 0) return context.getString(resourceId)

            val literal = getAttributeValue(namespace, name)
            if (literal != null && !literal.startsWith("@")) return literal
        }
        return null
    }
}
