package com.example.androidscribble.ml

import com.example.androidscribble.ink.InkStroke
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScribbleRecognizer(
    private val dictionary: CustomDictionary,
    private val corrections: CorrectionRepository,
    languageTag: String = "en-US",
) {
    private val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
        ?: error("Unsupported Digital Ink language tag: $languageTag")
    private val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
    private val recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())

    suspend fun recognize(strokes: List<InkStroke>): String? = withContext(Dispatchers.IO) {
        val result = Tasks.await(recognizer.recognize(strokes.toMlKitInk()))
        val candidates = result.candidates.map { it.text }
        corrections.bias(dictionary.boost(candidates)).firstOrNull()
    }

    private fun List<InkStroke>.toMlKitInk(): Ink {
        val builder = Ink.builder()
        forEach { stroke ->
            val strokeBuilder = Ink.Stroke.builder()
            stroke.points.forEach { point -> strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, point.t)) }
            builder.addStroke(strokeBuilder.build())
        }
        return builder.build()
    }
}
