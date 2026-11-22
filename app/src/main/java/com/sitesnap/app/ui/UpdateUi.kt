package com.sitesnap.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sitesnap.app.AppViewModel
import com.sitesnap.app.update.Release
import com.sitesnap.app.update.UpdateState
import com.sitesnap.app.ui.components.ButtonStyle
import com.sitesnap.app.ui.components.Card
import com.sitesnap.app.ui.components.PillButton
import com.sitesnap.app.ui.components.formatBytes
import com.sitesnap.app.ui.theme.SiteSnapColors
import com.sitesnap.app.ui.theme.SiteSnapType

/** Release this state is about, if any. */
val UpdateState.release: Release?
    get() = when (this) {
        is UpdateState.Available -> release
        is UpdateState.NeedsPermission -> release
        is UpdateState.Downloading -> release
        is UpdateState.Installing -> release
        is UpdateState.Failed -> release
        else -> null
    }

/**
 * Sends the user to "Install unknown apps" for SiteSnap, and resumes the update when they come back
 * having allowed it.
 */
@Composable
fun rememberAllowInstalls(vm: AppViewModel): () -> Unit {
    val context = LocalContext.current
    val update by vm.update.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val waiting = update as? UpdateState.NeedsPermission
        if (waiting != null && vm.updater.canInstall()) vm.installUpdate(waiting.release)
    }
    return {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent) }
    }
}

/** Home-screen card shown while an update is available, downloading, or needs attention. */
@Composable
fun UpdateCard(vm: AppViewModel, modifier: Modifier = Modifier) {
    val update by vm.update.collectAsStateWithLifecycle()
    val dismissed by vm.dismissedUpdate.collectAsStateWithLifecycle()
    val allowInstalls = rememberAllowInstalls(vm)
    val release = update.release ?: return
    if (update is UpdateState.Available && dismissed == release.version) return

    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(SiteSnapColors.AccentSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.SystemUpdate, null, tint = SiteSnapColors.Accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(updateTitle(update), style = SiteSnapType.Label, color = SiteSnapColors.Ink)
                Text(updateDetail(update, release), style = SiteSnapType.Caption, color = SiteSnapColors.InkMuted)
            }
        }
        val s = update
        if (s is UpdateState.Downloading || s is UpdateState.Installing) {
            LinearProgressIndicator(
                progress = { (s as? UpdateState.Downloading)?.progress ?: 1f },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(6.dp),
                color = SiteSnapColors.Accent,
                trackColor = SiteSnapColors.Fill,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s is UpdateState.Available) {
                    PillButton("Later", onClick = { vm.dismissUpdate(release) }, style = ButtonStyle.Secondary, height = 44.dp, modifier = Modifier.weight(1f))
                }
                when (s) {
                    is UpdateState.NeedsPermission ->
                        PillButton("Allow installs", onClick = allowInstalls, style = ButtonStyle.Accent, height = 44.dp, modifier = Modifier.weight(1f))
                    is UpdateState.Failed ->
                        PillButton("Try again", onClick = { vm.installUpdate(release) }, style = ButtonStyle.Accent, height = 44.dp, modifier = Modifier.weight(1f))
                    else ->
                        PillButton("Update", onClick = { vm.installUpdate(release) }, style = ButtonStyle.Accent, height = 44.dp, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

fun updateTitle(state: UpdateState): String = when (state) {
    is UpdateState.Available -> "Update available"
    is UpdateState.NeedsPermission -> "Allow SiteSnap to install updates"
    is UpdateState.Downloading -> "Downloading update…"
    is UpdateState.Installing -> "Installing update…"
    is UpdateState.Failed -> "Update didn't finish"
    UpdateState.Checking -> "Checking for updates…"
    UpdateState.UpToDate -> "You're up to date"
    UpdateState.Idle -> "Updates"
}

@Composable
private fun updateDetail(state: UpdateState, release: Release): String = when (state) {
    is UpdateState.Downloading -> "Version ${release.version} · ${(state.progress * 100).toInt()}%"
    is UpdateState.NeedsPermission -> "Turn on \"Allow from this source\", then come back"
    is UpdateState.Failed -> state.message
    else -> buildString {
        append("Version ${release.version}")
        if (release.apkSize > 0) append(" · ${formatBytes(release.apkSize)}")
    }
}
