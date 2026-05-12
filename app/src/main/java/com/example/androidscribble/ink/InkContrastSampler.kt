package com.example.androidscribble.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.core.content.getSystemService
import kotlin.math.roundToInt

/** Samples pixels under the overlay and returns an ink color with strong contrast. */
class InkContrastSampler(
    screenImageSource: ScreenImageSource? = null,
    private val screenImageSourceFactory: (() -> ScreenImageSource?)? = null,
) {
    private var screenImageSource: ScreenImageSource? = screenImageSource
    private var lastInk = Color.White

    fun sampleFromScreen(point: Offset, onColor: (Color) -> Unit) {
        val pixel = ensureScreenImageSource()?.samplePixel(point.x.roundToInt(), point.y.roundToInt())
        if (pixel != null) {
            lastInk = pixel.contrastingInk()
        }
        onColor(lastInk)
    }

    fun sampleFromWindow(window: Window?, point: Offset, onColor: (Color) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || window == null) {
            sampleFromScreen(point, onColor)
            return
        }
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val rect = Rect(point.x.roundToInt(), point.y.roundToInt(), point.x.roundToInt() + 1, point.y.roundToInt() + 1)
        PixelCopy.request(window, rect, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                lastInk = bitmap.getPixel(0, 0).contrastingInk()
            }
            bitmap.recycle()
            onColor(lastInk)
        }, Handler(Looper.getMainLooper()))
    }

    fun release() {
        screenImageSource?.release()
        screenImageSource = null
    }

    private fun ensureScreenImageSource(): ScreenImageSource? {
        if (screenImageSource == null) {
            screenImageSource = screenImageSourceFactory?.invoke()
        }
        return screenImageSource
    }

    fun highContrastFallback(backgroundIsDark: Boolean): Color = if (backgroundIsDark) Color.White else Color.Black

    private fun Int.contrastingInk(): Color {
        val luminance = (0.299 * AndroidColor.red(this) + 0.587 * AndroidColor.green(this) + 0.114 * AndroidColor.blue(this)) / 255.0
        return if (luminance > 0.55) Color.Black else Color.White
    }
}

interface ScreenImageSource {
    fun samplePixel(x: Int, y: Int): Int?
    fun release()
}

class MediaProjectionScreenImageSource(
    context: Context,
    grant: MediaProjectionPermissionStore.Grant,
) : ScreenImageSource {
    private val appContext = context.applicationContext
    private val captureThread = HandlerThread("ScribbleScreenCapture").apply { start() }
    private val captureHandler = Handler(captureThread.looper)
    private val projectionManager = appContext.getSystemService<MediaProjectionManager>()
    private val metrics = appContext.resources.displayMetrics
    private val width = metrics.widthPixels.coerceAtLeast(1)
    private val height = metrics.heightPixels.coerceAtLeast(1)
    private val densityDpi = metrics.densityDpi
    private var imageReader: ImageReader? = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, MAX_IMAGES)
    private var mediaProjection: MediaProjection? = projectionManager?.getMediaProjection(grant.resultCode, grant.data)
    private var virtualDisplay: VirtualDisplay? = null
    private var released = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            close(stopProjection = false)
        }
    }

    init {
        mediaProjection?.registerCallback(projectionCallback, captureHandler)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AndroidScribbleInkContrast",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            captureHandler,
        )
    }

    override fun samplePixel(x: Int, y: Int): Int? {
        if (released) return null
        val boundedX = x.coerceIn(0, width - 1)
        val boundedY = y.coerceIn(0, height - 1)
        val image = imageReader?.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes.firstOrNull() ?: return null
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val offset = boundedY * rowStride + boundedX * pixelStride
            if (offset + 3 >= buffer.capacity()) return null
            val red = buffer.get(offset).toInt() and 0xff
            val green = buffer.get(offset + 1).toInt() and 0xff
            val blue = buffer.get(offset + 2).toInt() and 0xff
            val alpha = buffer.get(offset + 3).toInt() and 0xff
            AndroidColor.argb(alpha, red, green, blue)
        } finally {
            image.close()
        }
    }

    override fun release() {
        close(stopProjection = true)
    }

    private fun close(stopProjection: Boolean) {
        if (released) return
        released = true
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        val projection = mediaProjection
        mediaProjection = null
        runCatching { projection?.unregisterCallback(projectionCallback) }
        if (stopProjection) runCatching { projection?.stop() }
        captureThread.quitSafely()
    }

    private companion object {
        const val MAX_IMAGES = 2
    }
}
