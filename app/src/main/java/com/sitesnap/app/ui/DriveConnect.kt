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
        if (result.resultCode == Activity.RESULT_OK) {
            runCatching { DriveAuth.resultFromIntent(context, result.data) }
                .onSuccess { connected(DriveAuth.email(it)) }
                .onFailure { failed(it.message ?: "Authorization failed") }
        } else {
            failed("Google Drive access was not granted")
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
                .onFailure { failed(it.message ?: "Couldn't reach Google Play services") }
        }
    }
}
