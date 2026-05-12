package com.example.androidscribble.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidscribble.ink.InkContrastSampler
import com.example.androidscribble.symbols.SymbolEmojiTrainer
import com.example.androidscribble.ui.ScribbleCanvasOverlay
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = SettingsRepository(this)
        val emojiTrainer = SymbolEmojiTrainer(this)
        setContent { SettingsScreen(repository, emojiTrainer, onClose = ::finish) }
    }
}

@Composable
private fun SettingsScreen(
    repository: SettingsRepository,
    emojiTrainer: SymbolEmojiTrainer,
    onClose: () -> Unit,
) {
    var settings by remember { mutableStateOf(repository.load()) }
    fun update(transform: (ScribbleSettings) -> ScribbleSettings) {
        settings = transform(settings)
        repository.save(settings)
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Android Scribble Settings", style = MaterialTheme.typography.headlineSmall)
                Text("Fine tune every major part of the floating handwriting engine. Long-press the pen trigger to reopen this menu.")

                SettingsSection("Ink and pressure") {
                    SliderSetting("Base stroke width", settings.baseStrokeWidth, 2f..18f, "%.1f px") {
                        update { current -> current.copy(baseStrokeWidth = it) }
                    }
                    SwitchSetting("Estimated pressure", settings.pressureEnabled) {
                        update { current -> current.copy(pressureEnabled = it) }
                    }
                    SliderSetting("Pressure response", settings.pressureResponse, 0f..1f, "%.2f") {
                        update { current -> current.copy(pressureResponse = it) }
                    }
                    SliderSetting("Point spacing", settings.minPointDistancePx, 0.5f..8f, "%.1f px") {
                        update { current -> current.copy(minPointDistancePx = it) }
                    }
                    SliderSetting("Visible stroke history", settings.keepRecentStrokes.toFloat(), 1f..32f, "%.0f strokes") {
                        update { current -> current.copy(keepRecentStrokes = it.roundToInt()) }
                    }
                }

                SettingsSection("Contrast and overlay") {
                    SwitchSetting("Dynamic ink contrast", settings.dynamicInkContrast) {
                        update { current -> current.copy(dynamicInkContrast = it) }
                    }
                    SwitchSetting("Dark fallback ink", settings.darkFallbackInk) {
                        update { current -> current.copy(darkFallbackInk = it) }
                    }
                    SliderSetting("Trigger size", settings.triggerSizeDp.toFloat(), 48f..112f, "%.0f dp") {
                        update { current -> current.copy(triggerSizeDp = it.roundToInt()) }
                    }
                    SliderSetting("Inactive trigger opacity", settings.triggerInactiveAlpha, 0.20f..1f, "%.2f") {
                        update { current -> current.copy(triggerInactiveAlpha = it) }
                    }
                    SliderSetting("Tap vs drag slop", settings.tapSlopPx, 4f..32f, "%.0f px") {
                        update { current -> current.copy(tapSlopPx = it) }
                    }
                }

                SettingsSection("Recognition and gestures") {
                    SwitchSetting("ML Kit recognition", settings.recognitionEnabled) {
                        update { current -> current.copy(recognitionEnabled = it) }
                    }
                    SwitchSetting("Scribble edit gestures", settings.gesturesEnabled) {
                        update { current -> current.copy(gesturesEnabled = it) }
                    }
                    SwitchSetting("Symbol-to-emoji training", settings.emojiTrainingEnabled) {
                        update { current -> current.copy(emojiTrainingEnabled = it) }
                    }
                    SliderSetting("Emoji match threshold", settings.emojiMatchThreshold, 0.40f..0.95f, "%.2f") {
                        update { current -> current.copy(emojiMatchThreshold = it) }
                    }
                }

                EmojiTrainingSection(emojiTrainer)

                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}


@Composable
private fun SwitchSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderSetting(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: String, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label: ${format.format(value)}")
        Slider(value = value, valueRange = range, onValueChange = onChange)
    }
}

@Composable
private fun EmojiTrainingSection(emojiTrainer: SymbolEmojiTrainer) {
    var label by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🙂") }
    var status by remember { mutableStateOf("Draw one sample below to train the current emoji template.") }
    val templates = remember(status) { emojiTrainer.templates() }

    SettingsSection("Symbol → emoji trainer") {
        Text("Create personal shorthand: draw a symbol in the pad and Android Scribble will inject the emoji when a future stroke matches it.")
        OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Symbol label") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = emoji, onValueChange = { emoji = it }, label = { Text("Emoji output") }, modifier = Modifier.fillMaxWidth())
        Card(Modifier.fillMaxWidth().height(220.dp)) {
            ScribbleCanvasOverlay(
                modifier = Modifier.fillMaxSize(),
                inkContrastSampler = InkContrastSampler(),
            ) { stroke ->
                emojiTrainer.train(label, emoji, listOf(stroke))
                status = "Trained ${emoji.ifBlank { "emoji" }} from ${stroke.points.size} points."
            }
        }
        Text(status)
        Text("Templates: ${templates.joinToString { "${it.label} ${it.emoji}" }}")
        Button(onClick = {
            emojiTrainer.clearCustomTemplates()
            status = "Custom templates cleared; built-in heart, smile, and star templates remain."
        }) { Text("Reset emoji templates") }
    }
}
