package com.sitesnap.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sitesnap.app.AppViewModel
import com.sitesnap.app.SyncStatus
import com.sitesnap.app.data.PhotoRepository
import com.sitesnap.app.ui.components.ButtonStyle
import com.sitesnap.app.ui.components.Card
import com.sitesnap.app.ui.components.CircleIconButton
import com.sitesnap.app.ui.components.DriveLogo
import com.sitesnap.app.ui.components.Overline
import com.sitesnap.app.ui.components.PillButton
import com.sitesnap.app.ui.components.StatusPill
import com.sitesnap.app.ui.components.formatBytes
import com.sitesnap.app.ui.components.relativeTime
import com.sitesnap.app.ui.rememberAllowInstalls
import com.sitesnap.app.ui.rememberDriveConnect
import com.sitesnap.app.ui.release
import com.sitesnap.app.ui.updateTitle
import com.sitesnap.app.update.UpdateState
import com.sitesnap.app.ui.theme.SiteSnapColors
import com.sitesnap.app.ui.theme.SiteSnapType

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    val connect = rememberDriveConnect(onConnected = vm::onDriveConnected, onFailed = { error = it })

    Column(
        Modifier
            .fillMaxSize()
            .background(SiteSnapColors.Bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack, modifier = Modifier.padding(top = 6.dp))
        Text("Drive & sync", style = SiteSnapType.Headline, color = SiteSnapColors.Ink, modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 6.dp))

        // Account
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.driveConnected) {
                    Box(Modifier.size(44.dp).background(SiteSnapColors.Ink, CircleShape), contentAlignment = Alignment.Center) {
                        Text((state.accountEmail?.take(1) ?: "G").uppercase(), style = SiteSnapType.Subtitle, color = SiteSnapColors.OnInk)
                    }
                } else {
                    Box(Modifier.size(44.dp).background(SiteSnapColors.Fill, CircleShape), contentAlignment = Alignment.Center) { DriveLogo() }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.driveConnected) state.accountEmail ?: "Google account" else "Not connected",
                        style = SiteSnapType.Subtitle, color = SiteSnapColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (state.driveConnected) "Google Drive" else "Photos stay on this phone",
                        style = SiteSnapType.BodySmall, color = SiteSnapColors.InkMuted,
                    )
                }
                if (state.driveConnected) StatusPill(SyncStatus.Synced, 0, label = "Connected")
                else StatusPill(SyncStatus.LocalOnly, 0, label = "Off")
            }
        }
        error?.let { Text(it, style = SiteSnapType.Caption, color = SiteSnapColors.Accent, modifier = Modifier.padding(start = 4.dp)) }

        // Backup progress
        Card(padding = PaddingValues(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DriveLogo()
                Text("My Drive / ${PhotoRepository.ROOT_FOLDER}", style = SiteSnapType.Label, color = SiteSnapColors.Ink)
            }
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${storage.backedUp}", style = SiteSnapType.Display, color = SiteSnapColors.Ink)
                Text(
                    "of ${storage.backupEligible} photos backed up",
                    style = SiteSnapType.BodySmall, color = SiteSnapColors.InkMuted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            LinearProgressIndicator(
                progress = { if (storage.backupEligible == 0) 0f else storage.backedUp / storage.backupEligible.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp),
                color = SiteSnapColors.Ink,
                trackColor = SiteSnapColors.Fill,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Text(
                buildString {
                    append("${formatBytes(storage.bytes)} on this phone")
                    when {
                        syncing -> append(" · syncing now")
                        state.lastSyncAt != null -> append(" · last sync ${relativeTime(state.lastSyncAt!!)}")
                    }
                },
                style = SiteSnapType.Caption, color = SiteSnapColors.InkSubtle,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Overline("Sync", Modifier.padding(start = 4.dp, top = 6.dp))
        Card(padding = PaddingValues(0.dp)) {
            ToggleRow("Auto-sync new photos", "Upload right after you shoot", state.settings.autoSync, vm::setAutoSync)
            HorizontalDivider(color = SiteSnapColors.Hairline)
            ToggleRow("Wi-Fi only", "Save mobile data on site", state.settings.wifiOnly, vm::setWifiOnly)
        }

        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.driveConnected) {
                PillButton("Sync now", onClick = vm::syncNow, style = ButtonStyle.Secondary, icon = Icons.Outlined.Sync, enabled = !syncing, modifier = Modifier.fillMaxWidth())
                PillButton("Disconnect Google Drive", onClick = vm::disconnectDrive, style = ButtonStyle.Ghost, icon = Icons.AutoMirrored.Outlined.Logout)
            } else {
                PillButton(
                    "Connect Google Drive", onClick = { error = null; connect() },
                    leading = { DriveLogo() }, modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Overline("App", Modifier.padding(start = 4.dp, top = 6.dp))
        AppUpdateCard(vm)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AppUpdateCard(vm: AppViewModel) {
    val update by vm.update.collectAsStateWithLifecycle()
    val allowInstalls = rememberAllowInstalls(vm)
    val release = update.release
    val status = when {
        !vm.updater.enabled -> "Update checks aren't set up for this build"
        update is UpdateState.Idle -> "Checks GitHub for new versions"
        update is UpdateState.Available -> "Version ${release?.version} is available"
        update is UpdateState.Downloading -> "Downloading ${release?.version} · ${((update as UpdateState.Downloading).progress * 100).toInt()}%"
        update is UpdateState.Failed -> (update as UpdateState.Failed).message
        else -> updateTitle(update)
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("SiteSnap ${vm.updater.currentVersion}", style = SiteSnapType.Label, color = SiteSnapColors.Ink)
                Text(status, style = SiteSnapType.Caption, color = SiteSnapColors.InkMuted)
            }
            if (vm.updater.enabled) {
                when (val s = update) {
                    is UpdateState.Available ->
                        PillButton("Update", onClick = { vm.installUpdate(s.release) }, style = ButtonStyle.Accent, height = 40.dp)
                    is UpdateState.NeedsPermission ->
                        PillButton("Allow", onClick = allowInstalls, style = ButtonStyle.Accent, height = 40.dp)
                    is UpdateState.Failed ->
                        if (s.release != null) PillButton("Retry", onClick = { vm.installUpdate(s.release) }, style = ButtonStyle.Accent, height = 40.dp)
                        else PillButton("Check", onClick = vm::checkForUpdate, style = ButtonStyle.Secondary, height = 40.dp)
                    is UpdateState.Idle, is UpdateState.UpToDate ->
                        PillButton("Check", onClick = vm::checkForUpdate, style = ButtonStyle.Secondary, height = 40.dp)
                    else -> Unit
                }
            }
        }
        (update as? UpdateState.Downloading)?.let { d ->
            LinearProgressIndicator(
                progress = { d.progress },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(6.dp),
                color = SiteSnapColors.Accent,
                trackColor = SiteSnapColors.Fill,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = SiteSnapType.Label, color = SiteSnapColors.Ink)
            Text(subtitle, style = SiteSnapType.Caption, color = SiteSnapColors.InkSubtle)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = SiteSnapColors.Ink,
                uncheckedTrackColor = SiteSnapColors.FillStrong,
                uncheckedBorderColor = SiteSnapColors.FillStrong,
                uncheckedThumbColor = SiteSnapColors.Surface,
            ),
        )
    }
}
