package com.example.androidscribble.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidscribble.ml.CorrectionExample
import com.example.androidscribble.ml.CorrectionRepository
import com.example.androidscribble.ml.CustomDictionary

class LearningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LearningScreen(
                dictionary = CustomDictionary(this),
                corrections = CorrectionRepository(this),
                onClose = ::finish,
            )
        }
    }
}

@Composable
private fun LearningScreen(
    dictionary: CustomDictionary,
    corrections: CorrectionRepository,
    onClose: () -> Unit,
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var newTerm by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Add words, names, acronyms, or phrases that Android Scribble should prioritize.") }
    val terms = remember(refreshKey) { dictionary.entries().sortedWith(String.CASE_INSENSITIVE_ORDER) }
    val examples = remember(refreshKey) { corrections.all() }

    fun refresh(message: String) {
        refreshKey++
        status = message
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Custom terms and corrections", style = MaterialTheme.typography.headlineSmall)
                Text("Teach Android Scribble the words you use and review saved correction examples that bias future handwriting recognition.")
                Text(status, style = MaterialTheme.typography.bodyMedium)

                DictionarySection(
                    terms = terms,
                    newTerm = newTerm,
                    onTermChange = { newTerm = it },
                    onAddTerm = {
                        val term = newTerm.trim()
                        dictionary.add(term)
                        newTerm = ""
                        refresh(if (term.isBlank()) "Enter a term before adding it." else "Added \"$term\" to your custom dictionary.")
                    },
                    onRemoveTerm = { term ->
                        val removed = dictionary.remove(term)
                        refresh(if (removed) "Removed \"$term\" from your custom dictionary." else "Could not find \"$term\" in your custom dictionary.")
                    },
                )

                CorrectionsSection(
                    examples = examples,
                    onDelete = { index, example ->
                        val deleted = corrections.deleteAt(index)
                        refresh(if (deleted) "Deleted correction from \"${example.recognized}\" to \"${example.corrected}\"." else "That correction was already removed.")
                    },
                    onClear = {
                        corrections.clear()
                        refresh("Cleared all saved correction examples.")
                    },
                )

                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
    }
}

@Composable
private fun DictionarySection(
    terms: List<String>,
    newTerm: String,
    onTermChange: (String) -> Unit,
    onAddTerm: () -> Unit,
    onRemoveTerm: (String) -> Unit,
) {
    LearningSection("Custom dictionary") {
        Text("Custom terms are boosted above similar ML Kit candidates when handwriting is recognized.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newTerm,
                onValueChange = onTermChange,
                label = { Text("New term") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Button(onClick = onAddTerm) { Text("Add") }
        }
        if (terms.isEmpty()) {
            Text("No custom terms yet.")
        } else {
            terms.forEach { term ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(term, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onRemoveTerm(term) }) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun CorrectionsSection(
    examples: List<CorrectionExample>,
    onDelete: (Int, CorrectionExample) -> Unit,
    onClear: () -> Unit,
) {
    LearningSection("Saved correction examples") {
        Text("These examples are stored locally and used to prefer corrections you have made before.")
        if (examples.isEmpty()) {
            Text("No correction examples saved yet.")
        } else {
            examples.forEachIndexed { index, example ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Recognized: ${example.recognized.ifBlank { "—" }}")
                        Text("Corrected: ${example.corrected.ifBlank { "—" }}")
                        Text("Stroke data: ${example.serializedStroke.length} characters", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onDelete(index, example) }) { Text("Delete example") }
                    }
                }
            }
            Button(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("Clear all corrections") }
        }
    }
}

@Composable
private fun LearningSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
