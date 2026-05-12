package com.example.androidscribble.settings

import android.content.Context
import androidx.core.content.edit

/** Central configuration for the floating handwriting engine. */
data class ScribbleSettings(
    val baseStrokeWidth: Float = 5.5f,
    val pressureEnabled: Boolean = true,
    val pressureResponse: Float = 0.65f,
    val minPointDistancePx: Float = 1.5f,
    val keepRecentStrokes: Int = 8,
    val dynamicInkContrast: Boolean = true,
    val darkFallbackInk: Boolean = true,
    val triggerSizeDp: Int = 72,
    val triggerInactiveAlpha: Float = 0.58f,
    val tapSlopPx: Float = 12f,
    val recognitionEnabled: Boolean = true,
    val gesturesEnabled: Boolean = true,
    val emojiTrainingEnabled: Boolean = true,
    val emojiMatchThreshold: Float = 0.72f,
)

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): ScribbleSettings = ScribbleSettings(
        baseStrokeWidth = prefs.getFloat(KEY_BASE_STROKE_WIDTH, ScribbleSettings().baseStrokeWidth),
        pressureEnabled = prefs.getBoolean(KEY_PRESSURE_ENABLED, ScribbleSettings().pressureEnabled),
        pressureResponse = prefs.getFloat(KEY_PRESSURE_RESPONSE, ScribbleSettings().pressureResponse),
        minPointDistancePx = prefs.getFloat(KEY_MIN_POINT_DISTANCE, ScribbleSettings().minPointDistancePx),
        keepRecentStrokes = prefs.getInt(KEY_KEEP_RECENT_STROKES, ScribbleSettings().keepRecentStrokes),
        dynamicInkContrast = prefs.getBoolean(KEY_DYNAMIC_INK, ScribbleSettings().dynamicInkContrast),
        darkFallbackInk = prefs.getBoolean(KEY_DARK_FALLBACK_INK, ScribbleSettings().darkFallbackInk),
        triggerSizeDp = prefs.getInt(KEY_TRIGGER_SIZE, ScribbleSettings().triggerSizeDp),
        triggerInactiveAlpha = prefs.getFloat(KEY_TRIGGER_ALPHA, ScribbleSettings().triggerInactiveAlpha),
        tapSlopPx = prefs.getFloat(KEY_TAP_SLOP, ScribbleSettings().tapSlopPx),
        recognitionEnabled = prefs.getBoolean(KEY_RECOGNITION_ENABLED, ScribbleSettings().recognitionEnabled),
        gesturesEnabled = prefs.getBoolean(KEY_GESTURES_ENABLED, ScribbleSettings().gesturesEnabled),
        emojiTrainingEnabled = prefs.getBoolean(KEY_EMOJI_TRAINING, ScribbleSettings().emojiTrainingEnabled),
        emojiMatchThreshold = prefs.getFloat(KEY_EMOJI_THRESHOLD, ScribbleSettings().emojiMatchThreshold),
    )

    fun save(settings: ScribbleSettings) {
        prefs.edit {
            putFloat(KEY_BASE_STROKE_WIDTH, settings.baseStrokeWidth)
            putBoolean(KEY_PRESSURE_ENABLED, settings.pressureEnabled)
            putFloat(KEY_PRESSURE_RESPONSE, settings.pressureResponse)
            putFloat(KEY_MIN_POINT_DISTANCE, settings.minPointDistancePx)
            putInt(KEY_KEEP_RECENT_STROKES, settings.keepRecentStrokes)
            putBoolean(KEY_DYNAMIC_INK, settings.dynamicInkContrast)
            putBoolean(KEY_DARK_FALLBACK_INK, settings.darkFallbackInk)
            putInt(KEY_TRIGGER_SIZE, settings.triggerSizeDp)
            putFloat(KEY_TRIGGER_ALPHA, settings.triggerInactiveAlpha)
            putFloat(KEY_TAP_SLOP, settings.tapSlopPx)
            putBoolean(KEY_RECOGNITION_ENABLED, settings.recognitionEnabled)
            putBoolean(KEY_GESTURES_ENABLED, settings.gesturesEnabled)
            putBoolean(KEY_EMOJI_TRAINING, settings.emojiTrainingEnabled)
            putFloat(KEY_EMOJI_THRESHOLD, settings.emojiMatchThreshold)
        }
    }

    private companion object {
        const val PREFS_NAME = "scribble_settings"
        const val KEY_BASE_STROKE_WIDTH = "base_stroke_width"
        const val KEY_PRESSURE_ENABLED = "pressure_enabled"
        const val KEY_PRESSURE_RESPONSE = "pressure_response"
        const val KEY_MIN_POINT_DISTANCE = "min_point_distance"
        const val KEY_KEEP_RECENT_STROKES = "keep_recent_strokes"
        const val KEY_DYNAMIC_INK = "dynamic_ink"
        const val KEY_DARK_FALLBACK_INK = "dark_fallback_ink"
        const val KEY_TRIGGER_SIZE = "trigger_size"
        const val KEY_TRIGGER_ALPHA = "trigger_alpha"
        const val KEY_TAP_SLOP = "tap_slop"
        const val KEY_RECOGNITION_ENABLED = "recognition_enabled"
        const val KEY_GESTURES_ENABLED = "gestures_enabled"
        const val KEY_EMOJI_TRAINING = "emoji_training"
        const val KEY_EMOJI_THRESHOLD = "emoji_threshold"
    }
}
