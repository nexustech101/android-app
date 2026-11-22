package com.sitesnap.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sitesnap.app.AppViewModel
import com.sitesnap.app.ui.UpdateCard
import com.sitesnap.app.data.Job
import com.sitesnap.app.data.PhotoRepository
import com.sitesnap.app.ui.components.Card
import com.sitesnap.app.ui.components.CircleIconButton
import com.sitesnap.app.ui.components.Dock
import com.sitesnap.app.ui.components.DriveLogo
import com.sitesnap.app.ui.components.FeaturedJobCard
import com.sitesnap.app.ui.components.IconButtonStyle
import com.sitesnap.app.ui.components.JobRow
import com.sitesnap.app.ui.components.Overline
import com.sitesnap.app.ui.components.PillButton
import com.sitesnap.app.ui.components.relativeTime
import com.sitesnap.app.ui.theme.SiteSnapColors
import com.sitesnap.app.ui.theme.SiteSnapType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JobsScreen(
    vm: AppViewModel,
    onOpenJob: (Job) -> Unit,
    onShoot: (Job) -> Unit,
    onJobCreated: (Job) -> Unit,
    onSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val pending by vm.totalPending.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showNewJob by rememberSaveable { mutableStateOf(false) }
    val today = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()) }

    val filtered = remember(summaries, query) {
        if (query.isBlank()) summaries
        else summaries.filter { s ->
            listOf(s.job.name, s.job.address, s.job.workOrder).any { it.contains(query, ignoreCase = true) }
        }
    }
    val featured = if (query.isBlank()) filtered.firstOrNull() else null
    val rest = if (featured != null) filtered.drop(1) else filtered

    Box(Modifier.fillMaxSize().background(SiteSnapColors.Bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Overline(today)
                        Text("Jobs", style = SiteSnapType.Display, color = SiteSnapColors.Ink)
                    }
                    CircleIconButton(
                        if (searching) Icons.Outlined.Close else Icons.Outlined.Search,
                        if (searching) "Close search" else "Search jobs",
                        onClick = { searching = !searching; if (!searching) query = "" },
                    )
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(Icons.Outlined.Add, "New job", onClick = { showNewJob = true }, style = IconButtonStyle.Ink)
                }
            }
            item {
                AnimatedVisibility(searching) {
                    SearchField(query, onChange = { query = it })
                }
            }
            item {
                DriveStrip(
                    connected = state.driveConnected,
                    syncing = syncing,
                    pending = pending,
                    lastSyncAt = state.lastSyncAt,
                    wifiOnly = state.settings.wifiOnly,
                    onClick = onSettings,
                )
            }
            item { UpdateCard(vm) }
            if (summaries.isEmpty()) {
                item { EmptyJobs(onCreate = { showNewJob = true }) }
            }
            featured?.let { f ->
                item { Overline("Continue", Modifier.padding(start = 4.dp, top = 8.dp)) }
                item(key = "featured-${f.job.id}") {
                    FeaturedJobCard(f, onClick = { onOpenJob(f.job) }, onShoot = { onShoot(f.job) })
                }
            }
            if (rest.isNotEmpty()) {
                item { Overline(if (query.isBlank()) "All jobs · ${summaries.size}" else "${rest.size} results", Modifier.padding(start = 4.dp, top = 8.dp)) }
                items(rest, key = { it.job.id }) { s -> JobRow(s, onClick = { onOpenJob(s.job) }) }
            }
        }

        // Fade content out under the dock
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(SiteSnapColors.Bg.copy(alpha = 0f), SiteSnapColors.Bg.copy(alpha = 0.95f), SiteSnapColors.Bg)))
        )
        Box(Modifier.fillMaxSize().navigationBarsPadding()) {
            Dock(
                onJobs = {},
                onCamera = { featured?.let { onShoot(it.job) } ?: run { showNewJob = true } },
                onDrive = onSettings,
            )
        }
    }

    if (showNewJob) {
        NewJobSheet(
            driveConnected = state.driveConnected,
            onDismiss = { showNewJob = false },
            onCreate = { name, address, wo, sync ->
                vm.createJob(name, address, wo, sync) { job ->
                    showNewJob = false
                    onJobCreated(job)
                }
            },
        )
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(SiteSnapColors.Fill, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Search, null, tint = SiteSnapColors.InkMuted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Job, address or work order", style = SiteSnapType.Body, color = SiteSnapColors.InkSubtle)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = SiteSnapType.Body.copy(color = SiteSnapColors.Ink),
                cursorBrush = SolidColor(SiteSnapColors.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DriveStrip(connected: Boolean, syncing: Boolean, pending: Int, lastSyncAt: Long?, wifiOnly: Boolean, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), padding = PaddingValues(12.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(SiteSnapColors.Fill, CircleShape), contentAlignment = Alignment.Center) { DriveLogo() }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            !connected -> "Connect Google Drive"
                            syncing -> "Uploading ${if (pending == 1) "1 photo" else "$pending photos"}"
                            pending > 0 -> "$pending ${if (pending == 1) "photo" else "photos"} waiting"
                            else -> "Everything's backed up"
                        },
                        style = SiteSnapType.Label,
                        color = SiteSnapColors.Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        when {
                            !connected -> "Off"
                            pending > 0 && wifiOnly -> "Wi-Fi"
                            lastSyncAt != null -> relativeTime(lastSyncAt)
                            else -> ""
                        },
                        style = SiteSnapType.Caption,
                        color = SiteSnapColors.InkSubtle,
                    )
                }
                if (connected && syncing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = SiteSnapColors.Blue,
                        trackColor = SiteSnapColors.Fill,
                        gapSize = 0.dp,
                    )
                } else if (!connected) {
                    Text("Back up every job folder automatically", style = SiteSnapType.Caption, color = SiteSnapColors.InkMuted)
                }
            }
        }
    }
}

@Composable
private fun EmptyJobs(onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(64.dp).background(SiteSnapColors.Fill, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Folder, null, tint = SiteSnapColors.InkMuted)
        }
        Text("No jobs yet", style = SiteSnapType.Title, color = SiteSnapColors.Ink)
        Text("Create a job folder, then take your before-work photos.", style = SiteSnapType.BodySmall, color = SiteSnapColors.InkMuted)
        PillButton("New job", onClick = onCreate, icon = Icons.Outlined.Add, modifier = Modifier.padding(top = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewJobSheet(
    driveConnected: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, address: String, workOrder: String, syncToDrive: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var workOrder by rememberSaveable { mutableStateOf("") }
    var syncToDrive by rememberSaveable { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SiteSnapColors.Surface,
        scrimColor = SiteSnapColors.Ink.copy(alpha = 0.4f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("New job", style = SiteSnapType.Headline, color = SiteSnapColors.Ink)
                Text("Photos you take go straight into this folder.", style = SiteSnapType.BodySmall, color = SiteSnapColors.InkMuted)
            }
            FilledField("Job name", name, { name = it }, placeholder = "e.g. Henderson Roof Repair")
            FilledField("Property address", address, { address = it }, placeholder = "Street address", trailing = Icons.Outlined.Place)
            FilledField("Work order # · optional", workOrder, { workOrder = it }, placeholder = "e.g. WO-20931", words = false)
            Row(Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                DriveLogo(size = 22.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Back up to Google Drive", style = SiteSnapType.Label, color = SiteSnapColors.Ink)
                    Text(
                        if (driveConnected) "My Drive / ${PhotoRepository.ROOT_FOLDER} / ${name.ifBlank { "Job name" }}"
                        else "Connect Drive in settings to back up",
                        style = SiteSnapType.Caption,
                        color = SiteSnapColors.InkSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = syncToDrive,
                    onCheckedChange = { syncToDrive = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = SiteSnapColors.Ink,
                        uncheckedTrackColor = SiteSnapColors.FillStrong,
                        uncheckedBorderColor = SiteSnapColors.FillStrong,
                        uncheckedThumbColor = SiteSnapColors.Surface,
                    ),
                )
            }
            PillButton(
                "Create & start shooting",
                onClick = { onCreate(name, address, workOrder, syncToDrive) },
                icon = Icons.Outlined.PhotoCamera,
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                height = 56.dp,
            )
        }
    }
}

@Composable
private fun FilledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    trailing: ImageVector? = null,
    words: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().background(SiteSnapColors.Fill, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = SiteSnapType.Caption, color = SiteSnapColors.InkMuted)
            Box {
                if (value.isEmpty()) Text(placeholder, style = SiteSnapType.Body, color = SiteSnapColors.InkSubtle)
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = SiteSnapType.Body.copy(color = SiteSnapColors.Ink),
                    cursorBrush = SolidColor(SiteSnapColors.Accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = if (words) KeyboardCapitalization.Words else KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        trailing?.let { Icon(it, null, tint = SiteSnapColors.InkMuted, modifier = Modifier.size(20.dp)) }
    }
}
