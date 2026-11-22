package com.sitesnap.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.sitesnap.app.drive.DriveAuth
import kotlinx.coroutines.launch

/**
 * Returns a function that runs the Google Drive authorization flow, showing Google's
 * consent screen when needed, and reports the connected account email.
 */
@Composable
fun rememberDriveConnect(onConnected: (email: String?) -> Unit, onFailed: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connected by rememberUpdatedState(onConnected)
    val failed by rememberUpdatedState(onFailed)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // Google returns its reason (e.g. error 10 for a missing OAuth client) in the result data even
        // when the screen closes without granting access, so always read it.
        val auth = result.data?.let { data -> runCatching { DriveAuth.resultFromIntent(context, data) } }
        when {
            auth?.isFailure == true -> failed(DriveAuth.describe(auth.exceptionOrNull()!!))
            result.resultCode == Activity.RESULT_OK && auth != null -> connected(DriveAuth.email(auth.getOrThrow()))
            else -> failed("Google Drive access was not granted. Tap Connect and choose Allow on Google's screen.")
        }
    }

    return {
        scope.launch {
            runCatching { DriveAuth.authorize(context) }
                .onSuccess { result ->
                    val pending = result.pendingIntent
                    if (result.hasResolution() && pending != null) {
                        launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    } else {
                        connected(DriveAuth.email(result))
                    }
                }
                .onFailure { failed(DriveAuth.describe(it)) }
        }
    }
}
