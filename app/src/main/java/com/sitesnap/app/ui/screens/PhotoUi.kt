package com.sitesnap.app.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sitesnap.app.data.Job
import com.sitesnap.app.data.Photo
import com.sitesnap.app.ui.components.CircleIconButton
import com.sitesnap.app.ui.components.IconButtonStyle
import com.sitesnap.app.ui.components.photoCountLabel
import com.sitesnap.app.ui.theme.SiteSnapColors
import com.sitesnap.app.ui.theme.SiteSnapType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Full-screen photo viewer for a job: swipe between photos, rename, delete, or open in another app. */
@Composable
internal fun PhotoViewer(
    photos: List<Photo>,
    startId: Long,
    uploaded: Set<Long>,
    onClose: () -> Unit,
    onRename: (Photo, String) -> Unit,
    onDelete: (Photo) -> Unit,
) {
    BackHandler(onBack = onClose)
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = photos.indexOfFirst { it.id == startId }.coerceAtLeast(0)) { photos.size }
    val photo = photos[pager.currentPage.coerceIn(0, photos.lastIndex)]
    var renaming by remember { mutableStateOf<Photo?>(null) }
    var deleting by remember { mutableStateOf<Photo?>(null) }
    val taken = remember(photo.takenAt) { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(photo.takenAt)) }

    Box(Modifier.fillMaxSize().background(SiteSnapColors.CameraBg)) {
        HorizontalPager(state = pager, key = { photos[it].id }, modifier = Modifier.fillMaxSize()) { page ->
            AsyncImage(
                model = photos[page].uri,
                contentDescription = photos[page].displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.Outlined.Close, "Close", onClose, style = IconButtonStyle.Glass)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(photo.displayName.substringBeforeLast('.'), style = SiteSnapType.Label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${pager.currentPage + 1} of ${photos.size} · $taken" + if (photo.id in uploaded) " · In Drive" else "",
                    style = SiteSnapType.Caption, color = Color.White.copy(alpha = 0.7f), maxLines = 1,
                )
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f))
                .navigationBarsPadding().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ViewerAction(Icons.Outlined.DriveFileRenameOutline, "Rename") { renaming = photo }
            ViewerAction(Icons.AutoMirrored.Outlined.OpenInNew, "Open") {
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(photo.uri, photo.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { context.startActivity(view) }
            }
            ViewerAction(Icons.Outlined.DeleteOutline, "Delete", tint = Color(0xFFFF8A80)) { deleting = photo }
        }
    }

    renaming?.let { p -> RenamePhotoDialog(p, onDismiss = { renaming = null }, onRename = { onRename(p, it); renaming = null }) }
    deleting?.let { p ->
        DeletePhotosDialog(
            count = 1,
            inDrive = if (p.id in uploaded) 1 else 0,
            onDismiss = { deleting = null },
            onConfirm = { onDelete(p); deleting = null },
        )
    }
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier.clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, style = SiteSnapType.Caption, color = tint)
    }
}

@Composable
internal fun RenamePhotoDialog(photo: Photo, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    val base = photo.displayName.substringBeforeLast('.')
    val ext = photo.displayName.substringAfterLast('.', "jpg")
    var field by remember { mutableStateOf(TextFieldValue(base, TextRange(0, base.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val valid = field.text.isNotBlank() && field.text.trim() != base

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SiteSnapColors.Surface,
        title = { Text("Rename photo", style = SiteSnapType.Title, color = SiteSnapColors.Ink) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = true,
                suffix = { Text(".$ext", color = SiteSnapColors.InkSubtle) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onRename(field.text.trim()) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SiteSnapColors.Accent,
                    cursorColor = SiteSnapColors.Accent,
                    focusedTextColor = SiteSnapColors.Ink,
                    unfocusedTextColor = SiteSnapColors.Ink,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(field.text.trim()) }, enabled = valid) {
                Text("Rename", style = SiteSnapType.Label, color = if (valid) SiteSnapColors.Accent else SiteSnapColors.InkSubtle)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = SiteSnapType.Label, color = SiteSnapColors.InkMuted) } },
    )
}

/** Confirms deleting [count] photos, [inDrive] of which are backed up and will go to Drive's trash. */
@Composable
internal fun DeletePhotosDialog(count: Int, inDrive: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val what = if (count == 1) "this photo" else "$count photos"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SiteSnapColors.Surface,
        title = { Text("Delete $what?", style = SiteSnapType.Title, color = SiteSnapColors.Ink) },
        text = {
            Text(
                buildString {
                    append(if (count == 1) "It will be deleted from this phone." else "They will be deleted from this phone.")
                    if (inDrive > 0) {
                        append(if (inDrive == 1 && count == 1) " The copy in Google Drive" else " The ${photoCountLabel(inDrive)} in Google Drive")
                        append(" will move to Drive's trash, where you can restore it for 30 days.")
                    }
                },
                style = SiteSnapType.Body, color = SiteSnapColors.InkMuted,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", style = SiteSnapType.Label, color = SiteSnapColors.Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = SiteSnapType.Label, color = SiteSnapColors.InkMuted) } },
    )
}

/** Confirms deleting a whole job. Drive copies are kept unless the user ticks the box. */
@Composable
internal fun DeleteJobDialog(job: Job, photoCount: Int, onDismiss: () -> Unit, onConfirm: (trashInDrive: Boolean) -> Unit) {
    var trashInDrive by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SiteSnapColors.Surface,
        title = { Text("Delete this job?", style = SiteSnapType.Title, color = SiteSnapColors.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "“${job.name}”" + (if (photoCount > 0) " and its ${photoCountLabel(photoCount)}" else "") +
                        " will be deleted from this phone. This can't be undone.",
                    style = SiteSnapType.Body, color = SiteSnapColors.InkMuted,
                )
                if (job.driveFolderId != null) {
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { trashInDrive = !trashInDrive },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = trashInDrive,
                            onCheckedChange = { trashInDrive = it },
                            colors = CheckboxDefaults.colors(checkedColor = SiteSnapColors.Danger),
                        )
                        Column {
                            Text("Also move its Google Drive folder to trash", style = SiteSnapType.Label, color = SiteSnapColors.Ink)
                            Text(
                                if (trashInDrive) "You can restore it from Drive's trash for 30 days." else "Otherwise the copies in Drive are kept.",
                                style = SiteSnapType.Caption, color = SiteSnapColors.InkMuted,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(trashInDrive) }) { Text("Delete job", style = SiteSnapType.Label, color = SiteSnapColors.Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = SiteSnapType.Label, color = SiteSnapColors.InkMuted) } },
    )
}
