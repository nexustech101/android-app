package com.sitesnap.app.ui.components

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sitesnap.app.JobSummary
import com.sitesnap.app.R
import com.sitesnap.app.SyncStatus
import com.sitesnap.app.ui.theme.SiteSnapColors
import com.sitesnap.app.ui.theme.SiteSnapType

// ---------- Surfaces ----------

/** Borderless white card with the soft two-layer shadow from the design. */
fun Modifier.card(shape: Shape = RoundedCornerShape(24.dp)): Modifier = this
    .shadow(elevation = 10.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.10f))
    .background(SiteSnapColors.Surface, shape)
    .clip(shape)

@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .card(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = SiteSnapColors.InkMuted) {
    Text(text.uppercase(), style = SiteSnapType.Overline, color = color, modifier = modifier)
}

@Composable
fun DriveLogo(modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Image(painterResource(R.drawable.ic_google_drive), contentDescription = "Google Drive", modifier = modifier.size(size))
}

// ---------- Buttons ----------

enum class ButtonStyle { Primary, Secondary, Accent, Ghost, Danger }

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    val (bg, fg) = when (style) {
        ButtonStyle.Primary -> SiteSnapColors.Ink to SiteSnapColors.OnInk
        ButtonStyle.Secondary -> SiteSnapColors.Fill to SiteSnapColors.Ink
        ButtonStyle.Accent -> SiteSnapColors.Accent to SiteSnapColors.OnInk
        ButtonStyle.Ghost -> Color.Transparent to SiteSnapColors.InkMuted
        ButtonStyle.Danger -> SiteSnapColors.Danger to SiteSnapColors.OnInk
    }
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .height(height)
            .then(
                if (style == ButtonStyle.Accent) Modifier.shadow(14.dp, shape, spotColor = SiteSnapColors.Accent, ambientColor = SiteSnapColors.Accent)
                else Modifier
            )
            .clip(shape)
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        icon?.let { Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp)) }
        Text(text, style = SiteSnapType.Label, color = fg, maxLines = 1)
    }
}

enum class IconButtonStyle { Surface, Ink, Glass, Accent }

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IconButtonStyle = IconButtonStyle.Surface,
    size: Dp = 44.dp,
    tint: Color? = null,
) {
    val (bg, fg) = when (style) {
        IconButtonStyle.Surface -> SiteSnapColors.Surface to SiteSnapColors.Ink
        IconButtonStyle.Ink -> SiteSnapColors.Ink to SiteSnapColors.OnInk
        IconButtonStyle.Glass -> Color.White.copy(alpha = 0.16f) to Color.White
        IconButtonStyle.Accent -> SiteSnapColors.Accent to SiteSnapColors.OnInk
    }
    Box(
        modifier = modifier
            .size(size)
            .then(if (style == IconButtonStyle.Surface) Modifier.card(CircleShape) else Modifier.clip(CircleShape).background(bg))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint ?: fg, modifier = Modifier.size(size * 0.48f))
    }
}

// ---------- Status ----------

@Composable
fun StatusPill(status: SyncStatus, pendingCount: Int, modifier: Modifier = Modifier, label: String? = null) {
    val (fg, bg, text) = when (status) {
        SyncStatus.Synced -> Triple(SiteSnapColors.Green, SiteSnapColors.GreenSoft, "Backed up")
        SyncStatus.Syncing -> Triple(SiteSnapColors.Blue, SiteSnapColors.BlueSoft, "Uploading")
        SyncStatus.Pending -> Triple(SiteSnapColors.Amber, SiteSnapColors.AmberSoft, "$pendingCount waiting")
        SyncStatus.LocalOnly -> Triple(SiteSnapColors.InkMuted, SiteSnapColors.Fill, "On phone only")
    }
    Row(
        modifier = modifier.background(bg, RoundedCornerShape(50)).padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(fg, CircleShape))
        Text(label ?: text, style = SiteSnapType.Caption, color = fg)
    }
}

// ---------- Job cards ----------

@Composable
private fun PhotoOrPlaceholder(uri: Uri?, modifier: Modifier, iconSize: Dp = 22.dp) {
    Box(modifier.background(SiteSnapColors.Fill), contentAlignment = Alignment.Center) {
        if (uri != null) {
            AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.Folder, null, tint = SiteSnapColors.InkSubtle, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun JobRow(summary: JobSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .card(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoOrPlaceholder(summary.recentPhotos.firstOrNull(), Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(summary.job.name, style = SiteSnapType.Subtitle, color = SiteSnapColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (summary.job.address.isNotBlank()) {
                Text(summary.job.address, style = SiteSnapType.BodySmall, color = SiteSnapColors.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(summary.status, summary.pendingCount)
                Text(photoCountLabel(summary.photoCount), style = SiteSnapType.Caption, color = SiteSnapColors.InkSubtle)
            }
        }
    }
}

@Composable
fun FeaturedJobCard(summary: JobSummary, onClick: () -> Unit, onShoot: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .card(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 14.dp),
    ) {
        val photos = summary.recentPhotos
        Row(
            Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp)),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (photos.isEmpty()) {
                Box(Modifier.fillMaxSize().background(SiteSnapColors.Fill), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.PhotoCamera, null, tint = SiteSnapColors.InkSubtle)
                        Text("No photos yet", style = SiteSnapType.Caption, color = SiteSnapColors.InkMuted)
                    }
                }
            } else {
                PhotoOrPlaceholder(photos[0], Modifier.weight(1f).fillMaxHeight())
                if (photos.size > 1) {
                    Column(Modifier.width(104.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PhotoOrPlaceholder(photos[1], Modifier.weight(1f).fillMaxWidth())
                        PhotoOrPlaceholder(photos.getOrNull(2), Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(summary.job.name, style = SiteSnapType.Title, color = SiteSnapColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(summary.job.address, photoCountLabel(summary.photoCount)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = SiteSnapType.BodySmall,
                    color = SiteSnapColors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusPill(summary.status, summary.pendingCount)
            }
            Spacer(Modifier.width(12.dp))
            CircleIconButton(Icons.Outlined.PhotoCamera, "Take photos for ${summary.job.name}", onShoot, style = IconButtonStyle.Accent, size = 48.dp)
        }
    }
}

// ---------- Segmented control ----------

@Composable
fun Segmented(options: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(SiteSnapColors.Fill, RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, (label, count) ->
            val active = i == selected
            Row(
                Modifier
                    .weight(1f)
                    .then(if (active) Modifier.card(RoundedCornerShape(10.dp)) else Modifier.clip(RoundedCornerShape(10.dp)))
                    .clickable { onSelect(i) }
                    .padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = SiteSnapType.Label, color = if (active) SiteSnapColors.Ink else SiteSnapColors.InkMuted, maxLines = 1)
                Text("$count", style = SiteSnapType.Caption, color = SiteSnapColors.InkSubtle)
            }
        }
    }
}

// ---------- Floating dock ----------

@Composable
fun BoxScope.Dock(onJobs: () -> Unit, onCamera: () -> Unit, onDrive: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp)
            .width(240.dp)
            .height(68.dp)
            .shadow(24.dp, shape, spotColor = Color.Black.copy(alpha = 0.4f))
            .background(SiteSnapColors.Ink, shape)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockItem(Icons.Outlined.Folder, "Jobs", active = true, onClick = onJobs)
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(SiteSnapColors.Accent).clickable(role = Role.Button, onClick = onCamera),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.PhotoCamera, "Quick camera", tint = SiteSnapColors.OnInk) }
        DockItem(Icons.Outlined.CloudDone, "Drive & sync", active = false, onClick = onDrive)
    }
}

@Composable
private fun RowScope.DockItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White.copy(alpha = if (active) 1f else 0.55f), modifier = Modifier.size(22.dp))
    }
}

// ---------- Formatting ----------

fun photoCountLabel(count: Int) = if (count == 1) "1 photo" else "$count photos"

fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

@Composable
fun formatBytes(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)
