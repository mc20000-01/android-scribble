package com.example.androidscribble.symbols

import android.content.Context
import androidx.core.content.edit
import com.example.androidscribble.ink.InkStroke
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight local template library for user-trained symbols that should inject emoji instead of text.
 * Templates are normalized into a small point vector so matching is fast enough to run before ML Kit.
 */
class SymbolEmojiTrainer(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun train(label: String, emoji: String, strokes: List<InkStroke>) {
        val normalized = strokes.normalizeForTemplate()
        if (emoji.isBlank() || normalized.isEmpty()) return
        val templates = templates().toMutableList()
        templates += EmojiTemplate(label.ifBlank { emoji }, emoji.trim(), normalized)
        prefs.edit { putString(KEY_TEMPLATES, templates.toJson().toString()) }
    }

    fun match(strokes: List<InkStroke>, threshold: Float): EmojiMatch? {
        val candidate = strokes.normalizeForTemplate()
        if (candidate.isEmpty()) return null
        return templates()
            .asSequence()
            .map { template -> EmojiMatch(template.emoji, template.label, candidate.similarityTo(template.points)) }
            .filter { it.confidence >= threshold }
            .maxByOrNull { it.confidence }
    }

    fun templates(): List<EmojiTemplate> {
        val raw = prefs.getString(KEY_TEMPLATES, null) ?: return defaultTemplates()
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            EmojiTemplate(
                label = item.getString("label"),
                emoji = item.getString("emoji"),
                points = item.getJSONArray("points").toPoints(),
            )
        }.ifEmpty { defaultTemplates() }
    }

    fun clearCustomTemplates() {
        prefs.edit { remove(KEY_TEMPLATES) }
    }

    private fun List<InkStroke>.normalizeForTemplate(): List<TemplatePoint> {
        val raw = flatMap { stroke -> stroke.points }
        if (raw.size < 2) return emptyList()
        val minX = raw.minOf { it.x }
        val maxX = raw.maxOf { it.x }
        val minY = raw.minOf { it.y }
        val maxY = raw.maxOf { it.y }
        val scale = max(maxX - minX, maxY - minY).takeIf { it > 0f } ?: return emptyList()
        val normalized = raw.map { TemplatePoint((it.x - minX) / scale, (it.y - minY) / scale, it.pressure) }
        return normalized.resample(TEMPLATE_POINTS)
    }

    private fun List<TemplatePoint>.resample(count: Int): List<TemplatePoint> {
        if (size <= count) return this
        return List(count) { index -> this[(index * (lastIndex.toFloat() / (count - 1))).toInt().coerceIn(indices)] }
    }

    private fun List<TemplatePoint>.similarityTo(other: List<TemplatePoint>): Float {
        val count = min(size, other.size)
        if (count == 0) return 0f
        val averageDistance = (0 until count).sumOf { index ->
            val a = this[index]
            val b = other[index]
            hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) + abs(a.pressure - b.pressure) * 0.1
        } / count
        return (1.0 - averageDistance).coerceIn(0.0, 1.0).toFloat()
    }

    private fun JSONArray.toPoints(): List<TemplatePoint> = (0 until length()).map { index ->
        val point = getJSONArray(index)
        TemplatePoint(point.getDouble(0).toFloat(), point.getDouble(1).toFloat(), point.optDouble(2, 1.0).toFloat())
    }

    private fun List<EmojiTemplate>.toJson(): JSONArray = JSONArray(map { template ->
        JSONObject()
            .put("label", template.label)
            .put("emoji", template.emoji)
            .put("points", JSONArray(template.points.map { JSONArray(listOf(it.x, it.y, it.pressure)) }))
    })

    private fun defaultTemplates(): List<EmojiTemplate> = listOf(
        EmojiTemplate("heart", "❤️", listOf(0.50f to 0.95f, 0.08f to 0.45f, 0.30f to 0.12f, 0.50f to 0.32f, 0.70f to 0.12f, 0.92f to 0.45f, 0.50f to 0.95f).map { TemplatePoint(it.first, it.second, 1f) }),
        EmojiTemplate("smile", "🙂", listOf(0.08f to 0.45f, 0.25f to 0.78f, 0.50f to 0.90f, 0.75f to 0.78f, 0.92f to 0.45f).map { TemplatePoint(it.first, it.second, 1f) }),
        EmojiTemplate("star", "⭐", listOf(0.50f to 0.05f, 0.62f to 0.38f, 0.95f to 0.38f, 0.68f to 0.58f, 0.78f to 0.92f, 0.50f to 0.70f, 0.22f to 0.92f, 0.32f to 0.58f, 0.05f to 0.38f, 0.38f to 0.38f, 0.50f to 0.05f).map { TemplatePoint(it.first, it.second, 1f) }),
    )

    private companion object {
        const val PREFS_NAME = "symbol_emoji_training"
        const val KEY_TEMPLATES = "templates"
        const val TEMPLATE_POINTS = 48
    }
}

data class TemplatePoint(val x: Float, val y: Float, val pressure: Float)
data class EmojiTemplate(val label: String, val emoji: String, val points: List<TemplatePoint>)
data class EmojiMatch(val emoji: String, val label: String, val confidence: Float)
