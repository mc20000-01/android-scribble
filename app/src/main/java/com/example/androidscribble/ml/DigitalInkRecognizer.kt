package com.example.androidscribble.ml

import com.example.androidscribble.ink.InkStroke
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ScribbleRecognizer(
    private val dictionary: CustomDictionary,
    private val corrections: CorrectionRepository,
    languageTag: String = "en-US",
) {
    private val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
        ?: error("Unsupported Digital Ink language tag: $languageTag")
    private val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
    private val modelManager = RemoteModelManager.getInstance()
    private val downloadConditions = DownloadConditions.Builder().build()
    private val modelDownloadMutex = Mutex()
    @Volatile
    private var lastModelDownloadError: String? = null
    private val recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())

    val modelDownloadErrorMessage: String?
        get() = lastModelDownloadError

    suspend fun ensureModelDownloaded(): Boolean = withContext(Dispatchers.IO) {
        modelDownloadMutex.withLock {
            val alreadyDownloaded = runCatching { isModelDownloaded() }.getOrElse { throwable ->
                lastModelDownloadError = throwable.downloadErrorMessage()
                return@withLock false
            }

            if (alreadyDownloaded) {
                lastModelDownloadError = null
                return@withLock true
            }

            runCatching {
                Tasks.await(modelManager.download(model, downloadConditions))
                isModelDownloaded()
            }.onSuccess { downloaded ->
                lastModelDownloadError = if (downloaded) null else MODEL_DOWNLOAD_UNAVAILABLE_MESSAGE
            }.onFailure { throwable ->
                lastModelDownloadError = throwable.downloadErrorMessage()
            }.getOrDefault(false)
        }
    }

    suspend fun recognize(strokes: List<InkStroke>): String? = withContext(Dispatchers.IO) {
        if (!ensureModelDownloaded()) {
            throw ModelDownloadException(lastModelDownloadError ?: MODEL_DOWNLOAD_UNAVAILABLE_MESSAGE)
        }

        val result = Tasks.await(recognizer.recognize(strokes.toMlKitInk()))
        val candidates = result.candidates.map { it.text }
        corrections.bias(dictionary.boost(candidates)).firstOrNull()
    }

    private fun isModelDownloaded(): Boolean = Tasks.await(modelManager.isModelDownloaded(model))

    private fun Throwable.downloadErrorMessage(): String = localizedMessage?.takeIf { it.isNotBlank() }
        ?: MODEL_DOWNLOAD_UNAVAILABLE_MESSAGE

    private fun List<InkStroke>.toMlKitInk(): Ink {
        val builder = Ink.builder()
        forEach { stroke ->
            val strokeBuilder = Ink.Stroke.builder()
            stroke.points.forEach { point -> strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, point.t)) }
            builder.addStroke(strokeBuilder.build())
        }
        return builder.build()
    }

    class ModelDownloadException(message: String) : IllegalStateException(message)

    companion object {
        const val MODEL_DOWNLOAD_UNAVAILABLE_MESSAGE =
            "Handwriting recognition needs its ML Kit model. Connect to the internet and try downloading again."
    }
}
