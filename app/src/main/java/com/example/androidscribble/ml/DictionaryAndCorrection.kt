package com.example.androidscribble.ml

import android.content.Context
import com.example.androidscribble.ink.InkStroke
import org.json.JSONArray
import org.json.JSONObject

class CustomDictionary(context: Context) {
    private val prefs = context.getSharedPreferences("custom_dictionary", Context.MODE_PRIVATE)

    fun add(term: String) {
        val normalized = term.trim()
        if (normalized.isBlank()) return
        val values = entries().toMutableSet()
        values += normalized
        prefs.edit().putStringSet(KEY_TERMS, values).apply()
    }

    fun remove(term: String): Boolean {
        val normalized = term.trim()
        if (normalized.isBlank()) return false
        val values = entries().toMutableSet()
        val removed = values.remove(normalized)
        if (removed) prefs.edit().putStringSet(KEY_TERMS, values).apply()
        return removed
    }

    fun entries(): Set<String> = prefs.getStringSet(KEY_TERMS, emptySet()).orEmpty().toSet()

    fun boost(candidates: List<String>): List<String> {
        val dictionary = entries()
        return candidates.sortedWith(compareByDescending<String> { it in dictionary }.thenBy { candidates.indexOf(it) })
    }

    private companion object { const val KEY_TERMS = "terms" }
}

data class CorrectionExample(val recognized: String, val corrected: String, val serializedStroke: String)

class CorrectionRepository(context: Context) {
    private val prefs = context.getSharedPreferences("correction_layer", Context.MODE_PRIVATE)

    fun remember(recognized: String, corrected: String, strokes: List<InkStroke>) {
        val examples = all().toMutableList()
        examples += CorrectionExample(recognized, corrected, serialize(strokes))
        save(examples)
    }

    fun deleteAt(index: Int): Boolean {
        val examples = all().toMutableList()
        if (index !in examples.indices) return false
        examples.removeAt(index)
        save(examples)
        return true
    }

    fun delete(example: CorrectionExample): Boolean {
        val examples = all().toMutableList()
        val removed = examples.remove(example)
        if (removed) save(examples)
        return removed
    }

    fun clear() {
        prefs.edit().putString(KEY_EXAMPLES, "[]").apply()
    }

    fun bias(candidates: List<String>): List<String> {
        val corrections = all().groupingBy { it.recognized to it.corrected }.eachCount()
        return candidates.sortedWith(compareByDescending<String> { candidate ->
            corrections.filterKeys { it.second == candidate }.values.maxOrNull() ?: 0
        }.thenBy { candidates.indexOf(it) })
    }

    fun all(): List<CorrectionExample> {
        val raw = prefs.getString(KEY_EXAMPLES, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                CorrectionExample(
                    recognized = item.optString("recognized"),
                    corrected = item.optString("corrected"),
                    serializedStroke = item.optString("stroke"),
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun save(examples: List<CorrectionExample>) {
        prefs.edit().putString(KEY_EXAMPLES, JSONArray(examples.map { example ->
            JSONObject()
                .put("recognized", example.recognized)
                .put("corrected", example.corrected)
                .put("stroke", example.serializedStroke)
        }).toString()).apply()
    }

    private fun serialize(strokes: List<InkStroke>): String = JSONArray(strokes.map { stroke ->
        JSONArray(stroke.points.map { point -> JSONArray(listOf(point.x, point.y, point.t)) })
    }).toString()

    private companion object { const val KEY_EXAMPLES = "examples" }
}
