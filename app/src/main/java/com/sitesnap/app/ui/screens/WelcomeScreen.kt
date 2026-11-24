package com.sitesnap.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.sitesnap.app.R
import com.sitesnap.app.ui.components.ButtonStyle
import com.sitesnap.app.ui.components.DriveLogo
import com.sitesnap.app.ui.components.PillButton
import com.sitesnap.app.ui.rememberDriveConnect
import com.sitesnap.app.ui.theme.SiteSnapColors
import com.sitesnap.app.ui.theme.SiteSnapType
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

/** Time each slide stays up before the carousel moves on by itself (until the user swipes). */
private const val AutoAdvanceMillis = 4_500L

/** Card colours, matching the Play Store screenshots: accent orange, ink, and a warm grey. */
private enum class CardTheme(val bg: Color, val overline: Color, val title: Color) {
    Accent(SiteSnapColors.Accent, SiteSnapColors.Ink, Color.White),
    Ink(SiteSnapColors.Ink, SiteSnapColors.Accent, Color.White),
    Warm(Color(0xFFE6E4DE), SiteSnapColors.Accent, SiteSnapColors.Ink),
}

/**
 * One onboarding slide: a coloured card with a short headline and a phone showing that part of the
 * app (rendered by store/make_onboarding.py), plus the sentence shown under the carousel.
 */
private class Slide(
    @DrawableRes val phone: Int,
    val theme: CardTheme,
    val overline: String,
    val title: String,
    val body: String,
)

private val slides = listOf(
    Slide(
        phone = R.drawable.onboarding_jobs,
        theme = CardTheme.Accent,
        overline = "JOBS",
        title = "Every property,\nits own folder",
        body = "Make a folder for each job with its address and work order number.",
    ),
    Slide(
        phone = R.drawable.onboarding_camera,
        theme = CardTheme.Ink,
        overline = "CAMERA",
        title = "Shoot straight into\nthe job folder",
        body = "Pinch to zoom, tap to focus. Photos are named by date, address and number.",
    ),
    Slide(
        phone = R.drawable.onboarding_folder,
        theme = CardTheme.Warm,
        overline = "JOB FOLDER",
        title = "Before photos,\nsorted by day",
        body = "See at a glance which photos are backed up and which are still waiting.",
    ),
    Slide(
        phone = R.drawable.onboarding_drive,
        theme = CardTheme.Accent,
        overline = "GOOGLE DRIVE",
        title = "Backed up to\nyour Google Drive",
        body = "Each job gets its own Drive folder. Sync on Wi-Fi, or whenever you choose.",
    ),
)

/**
 * First-run onboarding: a full-screen carousel of app previews with page dots, and sign-in to Google
 * Drive (or carry on offline) always one tap away underneath.
 */
@Composable
fun WelcomeScreen(onConnected: (String?) -> Unit, onSkip: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val connect = rememberDriveConnect(onConnected = onConnected, onFailed = { error = it })
    val pager = rememberPagerState { slides.size }
    AutoAdvance(pager)

    Column(Modifier.fillMaxSize().background(SiteSnapColors.Bg).systemBarsPadding()) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(30.dp).background(SiteSnapColors.Accent, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_logo_mark), null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text("SiteSnap", style = SiteSnapType.Subtitle, color = SiteSnapColors.Ink)
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 32.dp),
            pageSpacing = 10.dp,
        ) { page ->
            // Neighbouring cards peek in at the sides, a little shorter and dimmer than the current one.
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceAtMost(1f)
            SlideCard(
                slides[page],
                Modifier.fillMaxSize().graphicsLayer {
                    scaleY = lerp(1f, 0.9f, offset)
                    alpha = lerp(1f, 0.7f, offset)
                },
            )
        }

        PageDots(count = slides.size, current = pager.currentPage, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp))

        AnimatedContent(
            targetState = pager.currentPage,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "slide text",
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
        ) { page ->
            Text(
                slides[page].body,
                style = SiteSnapType.Body,
                color = SiteSnapColors.InkMuted,
                textAlign = TextAlign.Center,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
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

/** Moves to the next slide every few seconds, and stops for good once the user swipes. */
@Composable
private fun AutoAdvance(pager: PagerState) {
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    var auto by remember { mutableStateOf(true) }
    LaunchedEffect(dragged) { if (dragged) auto = false }
    LaunchedEffect(auto) {
        while (auto && pager.currentPage < pager.pageCount - 1) {
            delay(AutoAdvanceMillis)
            pager.animateScrollToPage(pager.currentPage + 1, animationSpec = tween(600))
        }
    }
}

/** Coloured card: overline and headline on top, and a phone preview that runs off the bottom edge. */
@Composable
private fun SlideCard(slide: Slide, modifier: Modifier) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier
            .padding(bottom = 12.dp)
            .shadow(14.dp, shape, spotColor = Color.Black.copy(alpha = 0.2f))
            .clip(shape)
            .background(slide.theme.bg)
            .semantics { contentDescription = slide.title.replace('\n', ' ') },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            slide.overline,
            style = SiteSnapType.Overline.copy(letterSpacing = 2.sp),
            color = slide.theme.overline,
            modifier = Modifier.padding(top = 26.dp),
        )
        Text(
            slide.title,
            style = SiteSnapType.Headline,
            color = slide.theme.title,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
        )
        Image(
            painterResource(slide.phone),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize().padding(top = 4.dp).padding(horizontal = 14.dp),
        )
    }
}

/** Page indicator: the current page is a longer orange pill, the others small grey dots. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "Slide ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = i == current
            val width by animateDpAsState(if (active) 22.dp else 8.dp, label = "dot")
            Spacer(
                Modifier.width(width).height(8.dp).clip(CircleShape)
                    .background(if (active) SiteSnapColors.Accent else SiteSnapColors.FillStrong),
            )
        }
    }
}
