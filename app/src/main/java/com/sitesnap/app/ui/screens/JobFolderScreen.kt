package com.sitesnap.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sitesnap.app.AppViewModel
import com.sitesnap.app.data.Job
import com.sitesnap.app.data.Photo
import com.sitesnap.app.ui.components.ButtonStyle
import com.sitesnap.app.ui.components.CircleIconButton
import com.sitesnap.app.ui.components.Overline
import com.sitesnap.app.ui.components.PillButton
import com.sitesnap.app.ui.components.Segmented
import com.sitesnap.app.ui.components.card
import com.sitesnap.app.ui.components.photoCountLabel
import com.sitesnap.app.ui.theme.SiteSnapColors
import com.sitesnap.app.ui.theme.SiteSnapType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JobFolderScreen(vm: AppViewModel, job: Job, onBack: () -> Unit, onTakePhotos: () -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val photosFlow = remember(job.id) { vm.photos(job) }
    val photos by photosFlow.collectAsState(initial = emptyList())
    var filter by rememberSaveable { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDeleteJob by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Long?>(null) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected = emptySet() }

    val backsUp = state.driveConnected && job.syncToDrive
    val waiting = if (backsUp) photos.filter { it.id !in state.uploaded } else emptyList()
    val shown = when (filter) {
        1 -> waiting
        2 -> photos.filter { it.id in state.uploaded }
        else -> photos
    }
    val groups = remember(shown) {
        shown.groupBy { DateUtils.getRelativeTimeSpanString(it.takenAt, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString() }
    }
    val started = remember(job.createdAt) { SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(job.createdAt)) }

    Box(Modifier.fillMaxSize().background(SiteSnapColors.Bg)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    if (selecting) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircleIconButton(Icons.Outlined.Close, "Cancel selection", { selected = emptySet() })
                        Text(
                            "${selected.size} selected",
                            style = SiteSnapType.Title, color = SiteSnapColors.Ink,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                        Text(
                            if (selected.size == shown.size) "Select none" else "Select all",
                            style = SiteSnapType.Label, color = SiteSnapColors.Accent,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                                selected = if (selected.size == shown.size) emptySet() else shown.map { it.id }.toSet()
                            }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    } else Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
                        Box {
                            CircleIconButton(Icons.Outlined.MoreVert, "More", { menuOpen = true })
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = SiteSnapColors.Surface) {
                                if (backsUp) {
                                    DropdownMenuItem(text = { Text("Upload now", style = SiteSnapType.Label) }, onClick = { menuOpen = false; vm.syncNow() })
                                }
                                job.driveFolderId?.let { id ->
                                    DropdownMenuItem(
                                        text = { Text("Open in Google Drive", style = SiteSnapType.Label) },
                                        onClick = {
                                            menuOpen = false
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com/drive/folders/$id")))
                                            }
                                        },
                                    )
                                }
                                if (photos.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Select photos", style = SiteSnapType.Label) },
                                        onClick = { menuOpen = false; selected = setOfNotNull(shown.firstOrNull()?.id) },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete job", style = SiteSnapType.Label, color = SiteSnapColors.Danger) },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = SiteSnapColors.Danger) },
                                    onClick = { menuOpen = false; confirmDeleteJob = true },
                                )
                            }
                        }
                    }
                    Column(Modifier.padding(start = 2.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (job.workOrder.isNotBlank()) Overline(job.workOrder)
                        Text(job.name, style = SiteSnapType.Headline, color = SiteSnapColors.Ink)
                        if (job.address.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.Place, null, tint = SiteSnapColors.InkMuted, modifier = Modifier.size(15.dp))
                                Text(job.address, style = SiteSnapType.BodySmall, color = SiteSnapColors.InkMuted)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("${photos.size}", "Photos", null, Modifier.weight(1f))
                        if (backsUp) StatTile("${waiting.size}", "Waiting", if (waiting.isEmpty()) SiteSnapColors.Green else SiteSnapColors.Amber, Modifier.weight(1f))
                        else StatTile("Off", "Drive", SiteSnapColors.InkSubtle, Modifier.weight(1f))
                        StatTile(started, "Started", null, Modifier.weight(1f))
                    }
                    if (backsUp && photos.isNotEmpty()) {
                        Segmented(
                            options = listOf("All" to photos.size, "Waiting" to waiting.size, "Backed up" to photos.size - waiting.size),
                            selected = filter,
                            onSelect = { filter = it },
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                }
            }
            if (photos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No photos yet", style = SiteSnapType.Title, color = SiteSnapColors.Ink)
                        Text("Document the property before you start work.", style = SiteSnapType.BodySmall, color = SiteSnapColors.InkMuted)
                    }
                }
            }
            groups.forEach { (day, dayPhotos) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-$day") {
                    Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 12.dp, bottom = 4.dp)) {
                        Text(day, style = SiteSnapType.Label, color = SiteSnapColors.Ink, modifier = Modifier.weight(1f))
                        Text(photoCountLabel(dayPhotos.size), style = SiteSnapType.Caption, color = SiteSnapColors.InkSubtle)
                    }
                }
                items(dayPhotos, key = { it.id }) { photo ->
                    PhotoTile(
                        photo,
                        waiting = backsUp && photo.id !in state.uploaded,
                        selected = if (selecting) photo.id in selected else null,
                        onClick = {
                            if (selecting) selected = if (photo.id in selected) selected - photo.id else selected + photo.id
                            else viewing = photo.id
                        },
                        onLongClick = { selected = selected + photo.id },
                    )
                }
            }
        }

        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(160.dp)
                .background(Brush.verticalGradient(listOf(SiteSnapColors.Bg.copy(alpha = 0f), SiteSnapColors.Bg.copy(alpha = 0.95f), SiteSnapColors.Bg)))
        )
        if (selecting) {
            PillButton(
                "Delete ${photoCountLabel(selected.size)}",
                onClick = { confirmDeleteSelected = true },
                style = ButtonStyle.Danger,
                icon = Icons.Outlined.DeleteOutline,
                height = 56.dp,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
            )
        } else {
            PillButton(
                "Take photos",
                onClick = onTakePhotos,
                style = ButtonStyle.Accent,
                icon = Icons.Outlined.PhotoCamera,
                height = 56.dp,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
            )
        }

        viewing?.let { id ->
            PhotoViewer(
                photos = shown,
                startId = id,
                uploaded = state.uploaded.keys,
                onClose = { viewing = null },
                onRename = vm::renamePhoto,
                onDelete = { vm.deletePhotos(listOf(it)) },
            )
        }
    }

    if (confirmDeleteJob) {
        DeleteJobDialog(
            job = job,
            photoCount = photos.size,
            onDismiss = { confirmDeleteJob = false },
            onConfirm = { trash -> confirmDeleteJob = false; vm.deleteJob(job, trash) {} },
        )
    }
    if (confirmDeleteSelected) {
        val chosen = photos.filter { it.id in selected }
        DeletePhotosDialog(
            count = chosen.size,
            inDrive = chosen.count { it.id in state.uploaded },
            onDismiss = { confirmDeleteSelected = false },
            onConfirm = {
                confirmDeleteSelected = false
                vm.deletePhotos(chosen) { selected = emptySet() }
            },
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, dot: Color?, modifier: Modifier) {
    Column(modifier.card(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = SiteSnapType.Title, color = SiteSnapColors.Ink, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            dot?.let { Box(Modifier.size(6.dp).background(it, CircleShape)) }
            Text(label, style = SiteSnapType.Caption, color = SiteSnapColors.InkMuted)
        }
    }
}

/** [selected] is null outside selection mode. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(photo: Photo, waiting: Boolean, selected: Boolean?, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(SiteSnapColors.Fill)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select")
    ) {
        AsyncImage(model = photo.uri, contentDescription = photo.displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (selected != null) {
            if (selected) Box(Modifier.fillMaxSize().border(3.dp, SiteSnapColors.Accent, RoundedCornerShape(14.dp)).background(SiteSnapColors.Accent.copy(alpha = 0.18f)))
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp)
                    .background(if (selected) SiteSnapColors.Accent else Color.Black.copy(alpha = 0.3f), CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Outlined.Check, "Selected", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        if (waiting) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CloudUpload, "Not uploaded yet", tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
}
