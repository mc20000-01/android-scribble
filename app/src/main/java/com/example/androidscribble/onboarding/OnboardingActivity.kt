package com.example.androidscribble.onboarding

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.androidscribble.accessibility.ScribbleAccessibilityService
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
private fun OnboardingScreen(activity: ComponentActivity) {
    var permissionRefreshKey by remember { mutableStateOf(0) }
    var isScreenCaptureGranted by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRefreshKey++
    }
    val mediaProjectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        MediaProjectionPermissionStore.update(result.resultCode, result.data)
        isScreenCaptureGranted = result.resultCode == Activity.RESULT_OK && result.data != null
    }
    val recognizer = remember(activity) { ScribbleRecognizer(CustomDictionary(activity), CorrectionRepository(activity)) }
    val coroutineScope = rememberCoroutineScope()
    var modelStatus by remember { mutableStateOf("Checking handwriting recognition model…") }
    var isModelDownloading by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshKey++
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    val isAccessibilityGranted = remember(permissionRefreshKey, activity) { isAccessibilityServiceEnabled(activity) }
    val isOverlayGranted = remember(permissionRefreshKey, activity) { isOverlayPermissionGranted(activity) }
    val isBatteryOptimizationGranted = remember(permissionRefreshKey, activity) { isIgnoringBatteryOptimizations(activity) }
    val isNotificationGranted = remember(permissionRefreshKey, activity) { isNotificationPermissionGranted(activity) }

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
                PermissionCard(
                    title = "1. Accessibility Service",
                    description = "Primary service for gestures and text injection.",
                    isGranted = isAccessibilityGranted,
                ) {
                    activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                PermissionCard(
                    title = "2. Display Over Other Apps",
                    description = "Required for the trigger and handwriting canvas overlay.",
                    isGranted = isOverlayGranted,
                ) {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")))
                }
                PermissionCard(
                    title = "3. Screen capture",
                    description = "Optional, in-memory screen sampling for dynamic ink contrast while Android Scribble remains running.",
                    isGranted = isScreenCaptureGranted,
                ) {
                    val manager = activity.getSystemService<MediaProjectionManager>()
                    manager?.createScreenCaptureIntent()?.let { mediaProjectionLauncher.launch(it) }
                }
                PermissionCard(
                    title = "4. Ignore Battery Optimizations",
                    description = "Helps keep the handwriting service alive.",
                    isGranted = isBatteryOptimizationGranted,
                ) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${activity.packageName}"))
                    activity.startActivity(intent)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionCard(
                        title = "5. Notifications",
                        description = "Shows the keep-alive foreground notification.",
                        isGranted = isNotificationGranted,
                    ) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                ModelDownloadCard(
                    status = modelStatus,
                    isDownloading = isModelDownloading,
                    onDownloadClick = ::downloadModel,
                )
                ActionCard("Custom terms and corrections", "Teach Android Scribble your vocabulary and review saved correction examples.") {
                    activity.startActivity(Intent(activity, LearningActivity::class.java))
                }
                Spacer(Modifier.height(8.dp))
                Text("After enabling Accessibility, tap the floating pen. Dimmed means pass-through mode; bright means writing mode is intercepting strokes.")
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, description: String, isGranted: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(if (isGranted) "Status: Granted" else "Status: Needs setup", style = MaterialTheme.typography.labelLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onClick,
                enabled = !isGranted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isGranted) "Granted" else "Open setup")
            }
        }
    }
}

@Composable
private fun ActionCard(title: String, description: String, onClick: () -> Unit) {
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

private fun isOverlayPermissionGranted(activity: Activity): Boolean = Settings.canDrawOverlays(activity)

private fun isNotificationPermissionGranted(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = ComponentName(context, ScribbleAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()

    return enabledServices
        .split(':')
        .mapNotNull(ComponentName::unflattenFromString)
        .any { it == expectedComponentName }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService<PowerManager>() ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}
