package com.example.androidscribble.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import androidx.compose.runtime.mutableStateOf
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
import com.example.androidscribble.ink.InkStroke
import com.example.androidscribble.ink.ScribbleGesture
import com.example.androidscribble.ml.CorrectionRepository
import com.example.androidscribble.ml.CustomDictionary
import com.example.androidscribble.ml.ScribbleRecognizer
import com.example.androidscribble.settings.SettingsActivity
import com.example.androidscribble.settings.SettingsRepository
import com.example.androidscribble.settings.ScribbleSettings
import com.example.androidscribble.symbols.SymbolEmojiTrainer
import com.example.androidscribble.ui.ScribbleCanvasOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var emojiTrainer: SymbolEmojiTrainer
    private val settingsState = mutableStateOf(ScribbleSettings())
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
        settingsRepository = SettingsRepository(this)
        settingsState.value = settingsRepository.load()
        emojiTrainer = SymbolEmojiTrainer(this)
        recognizer = ScribbleRecognizer(CustomDictionary(this), CorrectionRepository(this))
        startForeground(NOTIFICATION_ID, keepAliveNotification())
        addCanvasOverlay()
        addTriggerButton()
        setWritingMode(false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        runCatching { windowManager.removeView(triggerView) }
        runCatching { windowManager.removeView(canvasView) }
        super.onDestroy()
    }

    private fun addCanvasOverlay() {
        canvasView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ScribbleAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@ScribbleAccessibilityService)
            setContent {
                ScribbleCanvasOverlay(settings = settingsState.value, inkContrastSampler = InkContrastSampler(), onStrokeFinished = ::handleStroke)
            }
        }
        windowManager.addView(canvasView, canvasLayoutParams(touchable = false))
    }

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
        triggerView.setOnLongClickListener {
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }
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
                    if (moved < settingsState.value.tapSlopPx) setWritingMode(!writingMode)
                    true
                }
                else -> false
            }
        }
        windowManager.addView(triggerView, params)
    }

    private fun setWritingMode(enabled: Boolean) {
        writingMode = enabled
        settingsState.value = settingsRepository.load()
        triggerView.alpha = if (enabled) 1f else settingsState.value.triggerInactiveAlpha
        (triggerView.layoutParams as? WindowManager.LayoutParams)?.let { params ->
            params.width = settingsState.value.triggerSizeDp.dp
            params.height = settingsState.value.triggerSizeDp.dp
            windowManager.updateViewLayout(triggerView, params)
        }
        windowManager.updateViewLayout(canvasView, canvasLayoutParams(touchable = enabled))
    }

    private fun handleStroke(stroke: InkStroke) {
        val currentSettings = settingsRepository.load()
        if (currentSettings.gesturesEnabled) {
            when (GestureClassifier.classify(stroke)) {
                ScribbleGesture.ScratchDelete -> { textInjector.scratchDeleteWord(); return }
                ScribbleGesture.CircleSelect -> { textInjector.selectWordNearCursor(); return }
                ScribbleGesture.VerticalSlash -> { textInjector.insertSpace(); return }
                ScribbleGesture.Text -> Unit
            }
        }
        if (currentSettings.emojiTrainingEnabled) {
            emojiTrainer.match(listOf(stroke), currentSettings.emojiMatchThreshold)?.let { match ->
                textInjector.insertText(match.emoji)
                return
            }
        }
        if (currentSettings.recognitionEnabled) {
            serviceScope.launch {
                recognizer.recognize(listOf(stroke))?.let { textInjector.insertText(it) }
            }
        }
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
        settingsState.value.triggerSizeDp.dp,
        settingsState.value.triggerSizeDp.dp,
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
    }
}
