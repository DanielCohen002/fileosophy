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

import android.os.Bundle
import androidx.preference.Preference
import com.amaze.filemanager.R

/** Lists the settings matching what the user typed in the settings toolbar search. */
class SettingsSearchFragment : BasePrefsFragment() {
    override val title = R.string.setting

    private var query = ""
    private lateinit var entries: List<SettingsSearchEntry>

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        entries = SettingsSearchIndex.build(requireContext())
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        updateQuery(arguments?.getString(ARG_QUERY).orEmpty())
    }

    fun updateQuery(newQuery: String) {
        query = newQuery
        val screen = preferenceScreen
        screen.removeAll()

        if (query.isBlank()) {
            screen.addPreference(messagePreference(R.string.settings_search_prompt))
            return
        }

        val results = SettingsSearchIndex.search(entries, query)
        if (results.isEmpty()) {
            screen.addPreference(messagePreference(R.string.settings_search_no_results))
        }
        results.forEach { screen.addPreference(resultPreference(it)) }
    }

    private fun messagePreference(message: Int) =
        Preference(requireContext()).apply {
            setTitle(message)
            isSelectable = false
            isIconSpaceReserved = false
        }

    private fun resultPreference(entry: SettingsSearchEntry) =
        Preference(requireContext()).apply {
            title = entry.title
            summary = getString(R.string.settings_search_location, getString(entry.screenTitle))
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                activity.hideKeyboard()
                activity.pushFragment(
                    entry.newScreen().apply {
                        arguments = Bundle().apply { putString(ARG_SCROLL_TO_KEY, entry.key) }
                    },
                )
                true
            }
        }

    companion object {
        const val ARG_QUERY = "query"
    }
}
