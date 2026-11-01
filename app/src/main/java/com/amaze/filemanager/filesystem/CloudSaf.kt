package com.amaze.filemanager.filesystem

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Cloud storage through Android's document picker (Storage Access Framework): Google Drive,
 * Dropbox, OneDrive, ... as long as their Android app is installed. No API keys involved.
 *
 * A path here is a content:// document URI of a provider other than the device's own storage:
 *   root of the cloud : content://AUTH/tree/TREE
 *   an existing item  : content://AUTH/tree/TREE/document/DOC
 *   a not-yet-created child (used while creating/copying): <parent path>/<name>
 */
object CloudSaf {
    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /** child path -> parent path, filled while listing so "back" can go up. */
    private val parents = ConcurrentHashMap<String, String>()

    @JvmStatic
    fun isCloudUri(path: String?): Boolean {
        if (path == null || !path.startsWith("content://")) return false
        val uri = Uri.parse(path)
        return uri.authority != EXTERNAL_STORAGE_AUTHORITY && uri.pathSegments.contains("tree")
    }

    private fun isVirtualChild(path: String): Boolean {
        val s = Uri.parse(path).pathSegments
        return !(s.size == 2 || (s.size == 4 && s[2] == "document"))
    }

    @JvmStatic
    fun parentOf(path: String): String? =
        if (isVirtualChild(path)) path.substringBeforeLast("/") else parents[path]

    @JvmStatic
    fun rememberParent(
        child: String,
        parent: String,
    ) {
        parents[child] = parent
    }

    /** The tree root (what permission was granted for) of any path of this cloud. */
    @JvmStatic
    fun rootOf(path: String): Uri {
        val uri = Uri.parse(path)
        val s = uri.pathSegments
        return Uri.Builder()
            .scheme(uri.scheme)
            .authority(uri.authority)
            .appendPath("tree")
            .appendPath(s[s.indexOf("tree") + 1])
            .build()
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    /** Finds the document for [path]; with [create] a missing child is created. */
    @JvmStatic
    fun resolve(
        context: Context,
        path: String,
        create: Boolean,
    ): DocumentFile? {
        if (isVirtualChild(path)) {
            val parent = parentOf(path) ?: return null
            val parentDoc = resolve(context, parent, false) ?: return null
            val name = Uri.decode(path.substringAfterLast("/"))
            parentDoc.findFile(name)?.let { return it }
            if (!create) return null
            return parentDoc.createFile(guessMime(name), name)
        }
        val requested = Uri.parse(path)
        val doc = DocumentFile.fromTreeUri(context, requested) ?: return null
        // safety net: never hand back a different document than asked for (e.g. the whole folder
        // when a single file was requested), because the caller may delete or overwrite it
        if (requested.pathSegments.size == 4 && doc.uri != requested) return null
        return if (doc.exists()) doc else null
    }
}
