package com.sitesnap.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.sitesnap.app.data.Job
import com.sitesnap.app.data.PhotoRepository
import com.sitesnap.app.ui.components.CircleIconButton
import com.sitesnap.app.ui.components.IconButtonStyle
import com.sitesnap.app.ui.components.PillButton
import com.sitesnap.app.ui.theme.SiteSnapColors
import com.sitesnap.app.ui.theme.SiteSnapType
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraScreen(job: Job, photoRepo: PhotoRepository, onPhotoSaved: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    LightSystemBarIcons()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(SiteSnapColors.CameraBg)) {
        if (granted) {
            CameraContent(job, photoRepo, onPhotoSaved, onClose)
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Camera access is needed to photograph the property.", style = SiteSnapType.Body, color = Color.White)
                PillButton("Allow camera", onClick = { permission.launch(Manifest.permission.CAMERA) })
                CircleIconButton(Icons.Outlined.Close, "Close", onClose, style = IconButtonStyle.Glass)
            }
        }
    }
}

@Composable
private fun CameraContent(job: Job, photoRepo: PhotoRepository, onPhotoSaved: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var backCamera by remember { mutableStateOf(true) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var lastUri by remember { mutableStateOf<Uri?>(null) }
    var sessionCount by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // text, success

    LaunchedEffect(flashMode) { imageCapture.flashMode = flashMode }
    LaunchedEffect(backCamera) {
        val provider = context.cameraProvider()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val selector = if (backCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        runCatching {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            zoom = 1f
        }.onFailure { toast = "Couldn't start the camera" to false }
    }
    LaunchedEffect(zoom, camera) { camera?.cameraControl?.setZoomRatio(zoom) }
    LaunchedEffect(toast, sessionCount) { if (toast != null) { delay(2_200); toast = null } }

    val zoomRange = camera?.cameraInfo?.zoomState?.value
    val zoomStops = buildList {
        val min = zoomRange?.minZoomRatio ?: 1f
        val max = zoomRange?.maxZoomRatio ?: 1f
        if (min < 0.9f) add(min)
        add(1f)
        if (max >= 2f) add(2f)
    }

    fun takePhoto() {
        if (capturing) return
        capturing = true
        imageCapture.takePicture(
            photoRepo.captureOptions(job),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    lastUri = output.savedUri
                    sessionCount++
                    toast = "Saved to job folder" to true
                    onPhotoSaved()
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    toast = "Photo not saved" to false
                }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            // Rule-of-thirds grid
            Canvas(Modifier.fillMaxSize()) {
                val line = Color.White.copy(alpha = 0.25f)
                for (f in listOf(1f / 3, 2f / 3)) {
                    drawLine(line, Offset(size.width * f, 0f), Offset(size.width * f, size.height), strokeWidth = 1f)
                    drawLine(line, Offset(0f, size.height * f), Offset(size.width, size.height * f), strokeWidth = 1f)
                }
            }
            Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))

            // Top controls
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(Icons.Outlined.Close, "Close camera", onClose, style = IconButtonStyle.Glass)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))
                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Folder, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(job.name, style = SiteSnapType.Label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Box(Modifier.size(28.dp).background(SiteSnapColors.Accent, CircleShape), contentAlignment = Alignment.Center) {
                        Text("$sessionCount", style = SiteSnapType.Caption, color = Color.White)
                    }
                }
                Spacer(Modifier.weight(1f))
                CircleIconButton(
                    when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Outlined.FlashOn
                        ImageCapture.FLASH_MODE_OFF -> Icons.Outlined.FlashOff
                        else -> Icons.Outlined.FlashAuto
                    },
                    "Flash",
                    onClick = {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                            else -> ImageCapture.FLASH_MODE_AUTO
                        }
                    },
                    style = IconButtonStyle.Glass,
                    tint = if (flashMode == ImageCapture.FLASH_MODE_OFF) Color.White else SiteSnapColors.Accent,
                )
            }

            // Toast + zoom, bottom of viewfinder
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AnimatedVisibility(toast != null, enter = fadeIn(), exit = fadeOut()) {
                    val (text, ok) = toast ?: ("" to true)
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(20.dp).background(if (ok) SiteSnapColors.Green else SiteSnapColors.Accent, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(if (ok) Icons.Outlined.Check else Icons.Outlined.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                        Text(text, style = SiteSnapType.Caption, color = Color.White)
                    }
                }
                if (zoomStops.size > 1) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        zoomStops.forEach { stop ->
                            val active = kotlin.math.abs(zoom - stop) < 0.05f
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(if (active) Color.White else Color.Transparent)
                                    .clickable(role = Role.Button) { zoom = stop },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(zoomLabel(stop, active), style = SiteSnapType.Caption, color = if (active) SiteSnapColors.Ink else Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Bottom controls
        Column(
            Modifier.fillMaxWidth().background(SiteSnapColors.CameraBg).navigationBarsPadding().padding(top = 22.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, Color.White, RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.1f))
                ) {
                    lastUri?.let { AsyncImage(model = it, contentDescription = "Last photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Box(
                    Modifier.size(80.dp).border(4.dp, Color.White, CircleShape).padding(8.dp).clip(CircleShape)
                        .background(if (capturing) Color.White.copy(alpha = 0.6f) else Color.White)
                        .clickable(role = Role.Button, onClickLabel = "Take photo") { takePhoto() },
                )
                CircleIconButton(Icons.Outlined.Cameraswitch, "Switch camera", { backCamera = !backCamera }, style = IconButtonStyle.Glass, size = 48.dp)
            }
            Text(
                photoRepo.relativePath(job).trimEnd('/'),
                style = SiteSnapType.Caption,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

private fun zoomLabel(stop: Float, active: Boolean): String {
    val base = if (stop < 1f) ".${(stop * 10).toInt()}" else stop.toInt().toString()
    return if (active) "$base×" else base
}

private suspend fun Context.cameraProvider(): ProcessCameraProvider = suspendCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
}

/** The camera is dark edge to edge, so use white status/nav bar icons while it's on screen. */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val wasLightStatus = controller?.isAppearanceLightStatusBars
        val wasLightNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            wasLightStatus?.let { controller.isAppearanceLightStatusBars = it }
            wasLightNav?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
}
