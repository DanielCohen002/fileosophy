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

package com.amaze.filemanager.filesystem

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.storage.StorageManager
import androidx.preference.PreferenceManager
import com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rikka.shizuku.Shizuku
import java.io.File

/**
 * Unmounts removable storage through Shizuku, the only way for a regular app to run the `sm
 * unmount` command Android otherwise reserves for system apps.
 */
object ShizukuStorageEjector {
    private val log: Logger = LoggerFactory.getLogger(ShizukuStorageEjector::class.java)

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
    const val REQUEST_CODE_PERMISSION = 4242

    enum class State {
        UNSUPPORTED,
        NOT_INSTALLED,
        NOT_RUNNING,
        NO_PERMISSION,
        READY,
    }

    fun interface Callback {
        fun onResult(success: Boolean)
    }

    /** The Shizuku API library needs Android 7.0 */
    @JvmStatic
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    /** Whether the one-tap eject switch in the settings is on, it is on by default. */
    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PreferencesConstants.PREFERENCE_SHIZUKU_EJECT_ENABLED, true)

    @JvmStatic
    fun getState(context: Context): State =
        when {
            !isSupported() -> State.UNSUPPORTED
            !isShizukuInstalled(context) -> State.NOT_INSTALLED
            !Shizuku.pingBinder() || Shizuku.isPreV11() -> State.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                State.NO_PERMISSION
            else -> State.READY
        }

    @JvmStatic
    fun requestPermission() = Shizuku.requestPermission(REQUEST_CODE_PERMISSION)

    @JvmStatic
    fun addPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        if (isSupported()) Shizuku.addRequestPermissionResultListener(listener)
    }

    @JvmStatic
    fun removePermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) {
        if (isSupported()) Shizuku.removeRequestPermissionResultListener(listener)
    }

    /**
     * Unmounts the volume mounted at [storagePath] on a background thread and reports whether it
     * worked through [callback], which is invoked on that background thread.
     */
    @JvmStatic
    fun unmountAsync(
        context: Context,
        storagePath: String,
        callback: Callback,
    ) {
        val appContext = context.applicationContext
        Thread {
            val success =
                try {
                    unmount(appContext, storagePath)
                } catch (e: Exception) {
                    log.warn("failed to unmount $storagePath through Shizuku", e)
                    false
                }
            callback.onResult(success)
        }.start()
    }

    private fun unmount(
        context: Context,
        storagePath: String,
    ): Boolean {
        val volumeId = findVolumeId(context, storagePath) ?: return false
        return run("sm", "unmount", volumeId).exitCode == 0
    }

    /**
     * `sm list-volumes public` prints lines like `public:8,1 mounted 1A2B-3C4D`, the id the
     * unmount command needs followed by the state and the volume uuid.
     */
    private fun findVolumeId(
        context: Context,
        storagePath: String,
    ): String? {
        val volumes =
            run("sm", "list-volumes", "public")
                .output
                .lines()
                .map { it.trim().split(Regex("\\s+")) }
                .filter { it.size >= 2 && it[0].startsWith("public:") && it[1] == "mounted" }

        val uuid = volumeUuid(context, storagePath)
        return volumes.firstOrNull { uuid != null && it.getOrNull(2).equals(uuid, true) }?.get(0)
            ?: volumes.singleOrNull()?.get(0)
    }

    private fun volumeUuid(
        context: Context,
        storagePath: String,
    ): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val storageManager = context.getSystemService(StorageManager::class.java)
        return storageManager.getStorageVolume(File(storagePath))?.uuid
    }

    private class CommandResult(val exitCode: Int, val output: String)

    private fun run(vararg command: String): CommandResult {
        // newProcess is private in the Shizuku API since v13 but still the supported way to run a shell
        // command for simple use cases
        val newProcess =
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            )
        newProcess.isAccessible = true
        val process = newProcess.invoke(null, command, null, null) as Process
        val output = process.inputStream.bufferedReader().readText()
        return CommandResult(process.waitFor(), output)
    }

    private fun isShizukuInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
}
