package com.amaze.filemanager.utils

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.widget.EditText
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat

/**
 * Colors the cursor, selection handles, selection highlight (and optionally the underline) with the
 * app accent instead of the theme's default blue/teal.
 */
fun EditText.applyAccent(
    @ColorInt accent: Int,
    tintUnderline: Boolean,
) {
    highlightColor = ColorUtils.setAlphaComponent(accent, 0x55)
    if (tintUnderline) {
        ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(accent))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val cursor = GradientDrawable()
        cursor.setColor(accent)
        cursor.setSize((resources.displayMetrics.density * 2).toInt(), -1)
        textCursorDrawable = cursor
        textSelectHandle?.mutate()?.setTint(accent)
        textSelectHandleLeft?.mutate()?.setTint(accent)
        textSelectHandleRight?.mutate()?.setTint(accent)
    }
}
