package com.example.androidscribble.ml

import android.content.Context
import com.example.androidscribble.ink.InkStroke
import org.json.JSONArray

class CustomDictionary(context: Context) {
    private val prefs = context.getSharedPreferences("custom_dictionary", Context.MODE_PRIVATE)

    fun add(term: String) {
        val normalized = term.trim()
        if (normalized.isBlank()) return
        val values = entries().toMutableSet()
        values += normalized
        prefs.edit().putStringSet(KEY_TERMS, values).apply()
    }

    fun entries(): Set<String> = prefs.getStringSet(KEY_TERMS, emptySet()).orEmpty()

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
        prefs.edit().putString(KEY_EXAMPLES, JSONArray(examples.map { example ->
            org.json.JSONObject().put("recognized", example.recognized)
                .put("corrected", example.corrected)
                .put("stroke", example.serializedStroke)
        }).toString()).apply()
    }

    fun bias(candidates: List<String>): List<String> {
        val corrections = all().groupingBy { it.recognized to it.corrected }.eachCount()
        return candidates.sortedWith(compareByDescending<String> { candidate ->
            corrections.filterKeys { it.second == candidate }.values.maxOrNull() ?: 0
        }.thenBy { candidates.indexOf(it) })
    }

    fun all(): List<CorrectionExample> {
        val raw = prefs.getString(KEY_EXAMPLES, "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            CorrectionExample(item.getString("recognized"), item.getString("corrected"), item.getString("stroke"))
        }
    }

    private fun serialize(strokes: List<InkStroke>): String = JSONArray(strokes.map { stroke ->
        JSONArray(stroke.points.map { point -> JSONArray(listOf(point.x, point.y, point.t, point.pressure)) })
    }).toString()

    private companion object { const val KEY_EXAMPLES = "examples" }
}
