package com.sitesnap.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sitesnap.app.R
import com.sitesnap.app.SyncStatus
import com.sitesnap.app.ui.components.ButtonStyle
import com.sitesnap.app.ui.components.DriveLogo
import com.sitesnap.app.ui.components.PillButton
import com.sitesnap.app.ui.components.StatusPill
import com.sitesnap.app.ui.rememberDriveConnect
import com.sitesnap.app.ui.theme.SiteSnapColors
import com.sitesnap.app.ui.theme.SiteSnapType

@Composable
fun WelcomeScreen(onConnected: (String?) -> Unit, onSkip: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val connect = rememberDriveConnect(onConnected = onConnected, onFailed = { error = it })

    Column(Modifier.fillMaxSize().background(SiteSnapColors.Bg).systemBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            PhotoStack(Modifier.fillMaxWidth().height(340.dp))
            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(30.dp).background(SiteSnapColors.Accent, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_logo_mark), null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Text("SiteSnap", style = SiteSnapType.Subtitle, color = SiteSnapColors.Ink)
                }
                Text("Before photos,\nsorted by job.", style = SiteSnapType.Display, color = SiteSnapColors.Ink)
                Text(
                    "Shoot straight into a job folder. Every photo is saved on your phone and backed up to your Google Drive.",
                    style = SiteSnapType.Body,
                    color = SiteSnapColors.InkMuted,
                )
            }
        }
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            error?.let { Text(it, style = SiteSnapType.Caption, color = SiteSnapColors.Accent, textAlign = TextAlign.Center) }
            PillButton(
                text = "Continue with Google Drive",
                onClick = { error = null; connect() },
                modifier = Modifier.fillMaxWidth(),
                leading = { DriveLogo(size = 20.dp) },
                height = 56.dp,
            )
            PillButton("Use offline for now", onClick = onSkip, style = ButtonStyle.Ghost)
        }
    }
}

/** Three tilted photo cards, matching the Figma hero. */
@Composable
private fun PhotoStack(modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val center = maxWidth / 2
        Tilted(R.drawable.welcome_kitchen, x = center - 162.dp, y = 70.dp, size = 150.dp, angle = 8f)
        Tilted(R.drawable.welcome_deck, x = center + 16.dp, y = 54.dp, size = 146.dp, angle = -7f)
        Tilted(R.drawable.welcome_house, x = center - 88.dp, y = 96.dp, size = 184.dp, angle = -2f)
        StatusPill(
            SyncStatus.Synced, 0, label = "Backed up to Drive",
            modifier = Modifier.offset(x = center + 16.dp, y = 286.dp).shadow(8.dp, RoundedCornerShape(50), spotColor = Color.Black.copy(alpha = 0.15f)),
        )
        Box(
            Modifier
                .offset(x = center - 120.dp, y = 262.dp)
                .size(60.dp)
                .background(SiteSnapColors.Bg, CircleShape)
                .padding(4.dp)
                .background(SiteSnapColors.Accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.PhotoCamera, null, tint = SiteSnapColors.OnInk) }
    }
}

@Composable
private fun Tilted(@DrawableRes res: Int, x: Dp, y: Dp, size: Dp, angle: Float) {
    val shape = RoundedCornerShape(24.dp)
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .offset(x, y)
            .rotate(angle)
            .size(size)
            .shadow(20.dp, shape, spotColor = Color.Black.copy(alpha = 0.3f))
            .border(5.dp, SiteSnapColors.Surface, shape)
            .clip(shape),
    )
}
