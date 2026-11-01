package com.amaze.filemanager.utils

import android.content.SharedPreferences
import org.json.JSONArray

/** Keeps the user's custom order of sidebar bookmarks (by path). Unknown bookmarks go last, A-Z. */
object BookmarkOrder {
    private const val KEY = "bookmark_order"

    private fun load(prefs: SharedPreferences): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Sorts [books] in place: saved order first, then anything new alphabetically. */
    @JvmStatic
    fun sort(
        prefs: SharedPreferences,
        books: MutableList<Array<String>>,
    ) {
        val index = load(prefs).withIndex().associate { it.value to it.index }
        books.sortWith(
            compareBy<Array<String>> { index[it[1]] ?: Int.MAX_VALUE }.thenComparing(BookSorter()),
        )
    }

    @JvmStatic
    fun save(
        prefs: SharedPreferences,
        books: List<Array<String>>,
    ) {
        val arr = JSONArray()
        books.forEach { arr.put(it[1]) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}
