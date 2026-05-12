package com.example.androidscribble.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.example.androidscribble.ink.MediaProjectionPermissionStore
import com.example.androidscribble.ml.CorrectionRepository
import com.example.androidscribble.ml.CustomDictionary
import com.example.androidscribble.ml.ScribbleRecognizer
import com.example.androidscribble.settings.LearningActivity
import kotlinx.coroutines.launch

class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OnboardingScreen(this) }
    }
}

@Composable
private fun OnboardingScreen(activity: Activity) {
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val mediaProjectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        MediaProjectionPermissionStore.update(result.resultCode, result.data)
    }
    val recognizer = remember(activity) { ScribbleRecognizer(CustomDictionary(activity), CorrectionRepository(activity)) }
    val coroutineScope = rememberCoroutineScope()
    var modelStatus by remember { mutableStateOf("Checking handwriting recognition model…") }
    var isModelDownloading by remember { mutableStateOf(false) }

    fun downloadModel() {
        if (isModelDownloading) return
        isModelDownloading = true
        modelStatus = "Downloading handwriting recognition model…"
        coroutineScope.launch {
            val downloaded = recognizer.ensureModelDownloaded()
            isModelDownloading = false
            modelStatus = if (downloaded) {
                "Handwriting recognition model is ready."
            } else {
                "Model download failed: ${recognizer.modelDownloadErrorMessage ?: ScribbleRecognizer.MODEL_DOWNLOAD_UNAVAILABLE_MESSAGE}"
            }
        }
    }

    LaunchedEffect(recognizer) {
        isModelDownloading = true
        val downloaded = recognizer.ensureModelDownloaded()
        isModelDownloading = false
        modelStatus = if (downloaded) {
            "Handwriting recognition model is ready."
        } else {
            "Model download failed: ${recognizer.modelDownloadErrorMessage ?: ScribbleRecognizer.MODEL_DOWNLOAD_UNAVAILABLE_MESSAGE}"
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Android Scribble", style = MaterialTheme.typography.headlineMedium)
                Text("Enable the permissions below so the floating canvas can recognize handwriting and inject text into any focused field.")
                PermissionCard("1. Accessibility Service", "Primary service for gestures and text injection.") {
                    activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                PermissionCard("2. Display Over Other Apps", "Required for the trigger and handwriting canvas overlay.") {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")))
                }
                PermissionCard("3. Screen capture", "Optional, in-memory screen sampling for dynamic ink contrast while Android Scribble remains running.") {
                    val manager = activity.getSystemService<MediaProjectionManager>()
                    manager?.createScreenCaptureIntent()?.let { mediaProjectionLauncher.launch(it) }
                }
                PermissionCard("4. Ignore Battery Optimizations", "Helps keep the handwriting service alive.") {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${activity.packageName}"))
                    activity.startActivity(intent)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionCard("5. Notifications", "Shows the keep-alive foreground notification.") {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                ModelDownloadCard(
                    status = modelStatus,
                    isDownloading = isModelDownloading,
                    onDownloadClick = ::downloadModel,
                )
                PermissionCard("Custom terms and corrections", "Teach Android Scribble your vocabulary and review saved correction examples.") {
                    activity.startActivity(Intent(activity, LearningActivity::class.java))
                }
                Spacer(Modifier.height(8.dp))
                Text("After enabling Accessibility, tap the floating pen. Dimmed means pass-through mode; bright means writing mode is intercepting strokes.")
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, description: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Open") }
        }
    }
}

@Composable
private fun ModelDownloadCard(status: String, isDownloading: Boolean, onDownloadClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Handwriting recognition model", style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onDownloadClick,
                enabled = !isDownloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isDownloading) "Downloading…" else "Download / retry")
            }
        }
    }
}
