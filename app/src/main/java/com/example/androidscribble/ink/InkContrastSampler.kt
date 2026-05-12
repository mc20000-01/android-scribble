package com.example.androidscribble.ink

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.PixelCopy
import android.os.Handler
import android.os.Looper
import android.view.Window
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * Samples pixels under the overlay and returns an ink color with strong contrast.
 * MediaProjection can be plugged in here for true system-wide sampling; PixelCopy is used when the host window is available.
 */
class InkContrastSampler {
    private var lastInk = Color.White

    fun sampleFromWindow(window: Window?, point: Offset, onColor: (Color) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || window == null) {
            onColor(lastInk)
            return
        }
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val rect = android.graphics.Rect(point.x.roundToInt(), point.y.roundToInt(), point.x.roundToInt() + 1, point.y.roundToInt() + 1)
        PixelCopy.request(window, rect, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                lastInk = bitmap.getPixel(0, 0).contrastingInk()
            }
            bitmap.recycle()
            onColor(lastInk)
        }, Handler(Looper.getMainLooper()))
    }

    fun highContrastFallback(backgroundIsDark: Boolean): Color = if (backgroundIsDark) Color.White else Color.Black

    private fun Int.contrastingInk(): Color {
        val luminance = (0.299 * AndroidColor.red(this) + 0.587 * AndroidColor.green(this) + 0.114 * AndroidColor.blue(this)) / 255.0
        return if (luminance > 0.55) Color.Black else Color.White
    }
}
