package com.amaze.filemanager.ui.activities

import android.app.Activity
import android.content.Intent

/**
 * MainActivity is singleInstance, so a viewer it opens lives in its own task. When that viewer is
 * the last screen of its task (e.g. after the app was closed and restored), closing it would
 * leave the app instead of returning to the file manager; bring the file manager forward first.
 */
fun Activity.startMainIfTaskRoot() {
    if (isTaskRoot && !isFinishing) {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
