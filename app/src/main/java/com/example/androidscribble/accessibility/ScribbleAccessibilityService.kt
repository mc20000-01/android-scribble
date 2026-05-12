package com.example.androidscribble.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.androidscribble.R
import com.example.androidscribble.ink.GestureClassifier
import com.example.androidscribble.ink.InkContrastSampler
import com.example.androidscribble.ink.MediaProjectionPermissionStore
import com.example.androidscribble.ink.MediaProjectionScreenImageSource
import com.example.androidscribble.ink.InkStroke
import com.example.androidscribble.ink.ScribbleGesture
import com.example.androidscribble.ml.CorrectionRepository
import com.example.androidscribble.ml.CustomDictionary
import com.example.androidscribble.ml.ScribbleRecognizer
import com.example.androidscribble.ml.ScribbleRecognizer.ModelDownloadException
import com.example.androidscribble.ui.ScribbleCanvasOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScribbleAccessibilityService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var triggerView: TextView
    private lateinit var canvasView: ComposeView
    private lateinit var textInjector: TextInjector
    private lateinit var recognizer: ScribbleRecognizer
    private lateinit var inkContrastSampler: InkContrastSampler
    private val pendingTextStrokes = mutableListOf<InkStroke>()
    private var pendingTextCommitJob: Job? = null
    private var writingMode = false

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        textInjector = TextInjector(this)
        recognizer = ScribbleRecognizer(CustomDictionary(this), CorrectionRepository(this))
        startForeground(NOTIFICATION_ID, keepAliveNotification())
        inkContrastSampler = createInkContrastSampler()
        addCanvasOverlay()
        addTriggerButton()
        setWritingMode(false)
        prepareRecognitionModel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        clearPendingTextStrokes()
        runCatching { windowManager.removeView(triggerView) }
        runCatching { windowManager.removeView(canvasView) }
        if (::inkContrastSampler.isInitialized) inkContrastSampler.release()
        super.onDestroy()
    }

    private fun addCanvasOverlay() {
        canvasView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ScribbleAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@ScribbleAccessibilityService)
            setContent {
                ScribbleCanvasOverlay(inkContrastSampler = inkContrastSampler, onStrokeFinished = ::handleStroke)
            }
        }
        windowManager.addView(canvasView, canvasLayoutParams(touchable = false))
    }


    private fun createInkContrastSampler(): InkContrastSampler = InkContrastSampler(
        screenImageSourceFactory = {
            MediaProjectionPermissionStore.consume()?.let { grant ->
                runCatching { MediaProjectionScreenImageSource(this, grant) }.getOrNull()
            }
        },
    )

    private fun addTriggerButton() {
        triggerView = TextView(this).apply {
            text = "✍"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundResource(R.drawable.trigger_button_background)
            elevation = 16f
        }
        val params = triggerLayoutParams()
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        triggerView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downX).toInt()
                    params.y = startY + (event.rawY - downY).toInt()
                    windowManager.updateViewLayout(triggerView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = kotlin.math.hypot((event.rawX - downX).toDouble(), (event.rawY - downY).toDouble())
                    if (moved < 12.0) setWritingMode(!writingMode)
                    true
                }
                else -> false
            }
        }
        windowManager.addView(triggerView, params)
    }

    private fun setWritingMode(enabled: Boolean) {
        writingMode = enabled
        triggerView.alpha = if (enabled) 1f else 0.58f
        windowManager.updateViewLayout(canvasView, canvasLayoutParams(touchable = enabled))
    }

    private fun prepareRecognitionModel() {
        serviceScope.launch {
            if (!recognizer.ensureModelDownloaded()) {
                showRecognitionUnavailableMessage(recognizer.modelDownloadErrorMessage)
            }
        }
    }

    private fun handleStroke(stroke: InkStroke) {
        when (GestureClassifier.classify(stroke)) {
            ScribbleGesture.ScratchDelete -> {
                clearPendingTextStrokes()
                textInjector.scratchDeleteWord()
            }
            ScribbleGesture.CircleSelect -> {
                clearPendingTextStrokes()
                textInjector.selectWordNearCursor()
            }
            ScribbleGesture.VerticalSlash -> {
                clearPendingTextStrokes()
                textInjector.insertSpace()
            }
            ScribbleGesture.Text -> {
                pendingTextStrokes += stroke
                schedulePendingTextCommit()
            }
        }
    }

    private fun schedulePendingTextCommit() {
        pendingTextCommitJob?.cancel()
        pendingTextCommitJob = serviceScope.launch {
            delay(TEXT_STROKE_DEBOUNCE_MS)
            commitPendingTextStrokes()
        }
    }

    private suspend fun commitPendingTextStrokes() {
        val strokesToRecognize = pendingTextStrokes.toList()
        if (strokesToRecognize.isEmpty()) return

        try {
            recognizer.recognize(strokesToRecognize)?.let { recognizedText ->
                textInjector.insertText(recognizedText)
                pendingTextStrokes.clear()
            }
        } catch (exception: ModelDownloadException) {
            showRecognitionUnavailableMessage(exception.message)
        }
    }

    private fun clearPendingTextStrokes() {
        pendingTextCommitJob?.cancel()
        pendingTextCommitJob = null
        pendingTextStrokes.clear()
    }

    private fun showRecognitionUnavailableMessage(message: String?) {
        Toast.makeText(
            this,
            message ?: ScribbleRecognizer.MODEL_DOWNLOAD_UNAVAILABLE_MESSAGE,
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun canvasLayoutParams(touchable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun triggerLayoutParams() = WindowManager.LayoutParams(
        72.dp,
        72.dp,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 24.dp
        y = 96.dp
    }

    private fun keepAliveNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Android Scribble", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_scribble_foreground)
            .setContentTitle("Android Scribble is ready")
            .setContentText("Tap the floating pen to toggle writing mode.")
            .setOngoing(true)
            .build()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val CHANNEL_ID = "scribble_keep_alive"
        const val NOTIFICATION_ID = 1842
        const val TEXT_STROKE_DEBOUNCE_MS = 800L
    }
}
