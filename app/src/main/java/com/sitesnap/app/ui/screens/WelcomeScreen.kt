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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoCamera
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.sitesnap.app.R
import com.sitesnap.app.SyncStatus
import com.sitesnap.app.ui.components.ButtonStyle
import com.sitesnap.app.ui.components.DriveLogo
import com.sitesnap.app.ui.components.PillButton
import com.sitesnap.app.ui.components.StatusPill
import com.sitesnap.app.ui.rememberDriveConnect
import com.sitesnap.app.ui.theme.SiteSnapColors
import com.sitesnap.app.ui.theme.SiteSnapType
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

/** Time each slide stays up before the carousel moves on by itself (until the user swipes). */
private const val AutoAdvanceMillis = 4_500L

/**
 * One onboarding slide: a demo job photo with a Humaaans person (Pablo Stanley, via Blush) standing in
 * front of it, plus the text shown under the carousel.
 */
private class Slide(
    @DrawableRes val photo: Int,
    @DrawableRes val person: Int,
    val personHeight: Dp,
    /** The person stands at the card's left edge (else its right edge). */
    val personAtStart: Boolean,
    /** Flip the person so they face into the photo. */
    val mirrorPerson: Boolean,
    val title: String,
    val body: String,
    val badge: @Composable () -> Unit,
)

private val slides = listOf(
    Slide(
        photo = R.drawable.onboarding_house,
        person = R.drawable.humaaans_walk,
        personHeight = 166.dp,
        personAtStart = true,
        mirrorPerson = false,
        title = "Before photos,\nsorted by job.",
        body = "Make a folder for each job with its address and work order number.",
        badge = { GlassChip(Icons.Outlined.Folder, "1420 Oak St") },
    ),
    Slide(
        photo = R.drawable.onboarding_deck,
        person = R.drawable.humaaans_point,
        personHeight = 166.dp,
        personAtStart = false,
        mirrorPerson = true,
        title = "Shoot straight\ninto the job.",
        body = "Pinch to zoom, tap to focus. Photos are named by date, address and number.",
        badge = { GlassChip(Icons.Outlined.PhotoCamera, "Lakeview Deck 003") },
    ),
    Slide(
        photo = R.drawable.onboarding_kitchen,
        person = R.drawable.humaaans_sit,
        personHeight = 152.dp,
        personAtStart = false,
        mirrorPerson = true,
        title = "Backed up to\nGoogle Drive.",
        body = "Each job gets its own Drive folder. New photos upload in the background.",
        badge = { StatusPill(SyncStatus.Synced, 0, label = "Backed up to Drive") },
    ),
)

/**
 * First-run onboarding: a full-screen photo carousel with page dots, and sign-in to Google Drive
 * (or carry on offline) always one tap away underneath.
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
                    scaleY = lerp(1f, 0.88f, offset)
                    alpha = lerp(1f, 0.75f, offset)
                },
            )
        }

        PageDots(count = slides.size, current = pager.currentPage, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp))

        AnimatedContent(
            targetState = pager.currentPage,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            label = "slide text",
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
        ) { page ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(slides[page].title, style = SiteSnapType.Headline, color = SiteSnapColors.Ink, minLines = 2)
                Text(slides[page].body, style = SiteSnapType.Body, color = SiteSnapColors.InkMuted, minLines = 2)
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

@Composable
private fun SlideCard(slide: Slide, modifier: Modifier) {
    val shape = RoundedCornerShape(32.dp)
    Box(modifier.semantics { contentDescription = slide.title.replace('\n', ' ') }) {
        // Bottom padding leaves room for the person's feet to step past the card's edge.
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
                .shadow(18.dp, shape, spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(shape),
        ) {
            Image(painterResource(slide.photo), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.padding(16.dp)) { slide.badge() }
        }
        Image(
            painterResource(slide.person),
            contentDescription = null,
            modifier = Modifier
                .align(if (slide.personAtStart) Alignment.BottomStart else Alignment.BottomEnd)
                .padding(horizontal = 18.dp)
                .height(slide.personHeight)
                .graphicsLayer { if (slide.mirrorPerson) scaleX = -1f },
        )
    }
}

@Composable
private fun GlassChip(icon: ImageVector, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(text, style = SiteSnapType.Caption, color = Color.White)
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
