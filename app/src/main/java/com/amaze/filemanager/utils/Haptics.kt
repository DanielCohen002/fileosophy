package com.amaze.filemanager.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** Small vibrations used as feedback while dragging. */
object Haptics {
    /** A short click, e.g. when an item is picked or a bookmark moves past another. */
    @JvmStatic
    fun click(context: Context) = vibrate(context, 15, true)

    /** A slightly longer buzz, e.g. when a long press turns into a drag. */
    @JvmStatic
    fun buzz(context: Context) = vibrate(context, 60, false)

    @Suppress("DEPRECATION")
    private fun vibrate(
        context: Context,
        ms: Long,
        click: Boolean,
    ) {
        val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vib.hasVibrator()) return
        when {
            click && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            else -> vib.vibrate(ms)
        }
    }
}
