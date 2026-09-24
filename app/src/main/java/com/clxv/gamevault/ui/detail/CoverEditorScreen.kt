package com.clxv.gamevault.ui.detail

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clxv.gamevault.R
import com.clxv.gamevault.ui.library.LibraryViewModel
import kotlinx.coroutines.launch

/**
 * Custom cover editor: system image picker -> pinch/pan inside a fixed 3:4
 * crop window -> rotate -> fit/fill -> save. The original game file is
 * never touched; the result is stored in the app's private covers dir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverEditorScreen(
    onBack: () -> Unit,
    vm: GameDetailViewModel = hiltViewModel(),
    libraryVm: LibraryViewModel = hiltViewModel(),
) {
    val coverManager = libraryVm.coverManager
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var fill by remember { mutableStateOf(false) }
    var windowSize by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            scale = 1f; offset = Offset.Zero
            scope.launch { source = coverManager.decodeSampled(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_cover)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (source != null) {
                        TextButton(onClick = { fill = !fill }) {
                            Text(stringResource(if (fill) R.string.fill else R.string.fit))
                        }
                        IconButton(onClick = {
                            source?.let { source = coverManager.rotate(it, 90f) }
                            scale = 1f; offset = Offset.Zero
                        }) { Icon(Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.rotate)) }
                        TextButton(
                            enabled = !saving,
                            onClick = {
                                val src = source ?: return@TextButton
                                val win = windowSize
                                if (win.width == 0) return@TextButton
                                saving = true
                                scope.launch {
                                    val base = if (fill)
                                        maxOf(win.width.toFloat() / src.width, win.height.toFloat() / src.height)
                                    else
                                        minOf(win.width.toFloat() / src.width, win.height.toFloat() / src.height)
                                    val eff = base * scale
                                    val cropped = coverManager.crop(
                                        src,
                                        left = (-offset.x / eff) / src.width,
                                        top = (-offset.y / eff) / src.height,
                                        width = (win.width / eff) / src.width,
                                        height = (win.height / eff) / src.height,
                                    )
                                    vm.saveCustomCover(cropped)
                                    saving = false
                                    onBack()
                                }
                            },
                        ) { Text(stringResource(R.string.save_cover)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (source == null) {
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.cover_editor_hint), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(stringResource(R.string.pick_image)) }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { vm.resetCover(); onBack() }) {
                    Text(stringResource(R.string.reset_cover), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
            } else {
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .aspectRatio(3f / 4f)
                            .fillMaxHeight()
                            .onSizeChanged { windowSize = it }
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                    ) {
                        val transform = rememberTransformableState { zoom, pan, _ ->
                            val bmp = source ?: return@rememberTransformableState
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = clamp(offset + pan, scale, windowSize, bmp)
                        }
                        val bmp = source!!
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val base = if (fill)
                                        maxOf(windowSize.width.toFloat() / bmp.width, windowSize.height.toFloat() / bmp.height)
                                    else
                                        minOf(windowSize.width.toFloat() / bmp.width, windowSize.height.toFloat() / bmp.height)
                                    scaleX = base * scale; scaleY = base * scale
                                    translationX = offset.x; translationY = offset.y
                                }
                                .transformable(transform),
                        )
                        Box(Modifier.matchParentSize()
                            .border(1.5.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(12.dp)))
                    }
                }
                Text(
                    stringResource(R.string.crop_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
        }
    }
}

private fun clamp(o: Offset, scale: Float, win: IntSize, bmp: Bitmap): Offset {
    if (win.width == 0) return o
    val base = minOf(win.width.toFloat() / bmp.width, win.height.toFloat() / bmp.height)
    val eff = base * scale
    val scaledW = bmp.width * eff
    val scaledH = bmp.height * eff
    val maxX = maxOf((scaledW - win.width) / 2f, 0f)
    val maxY = maxOf((scaledH - win.height) / 2f, 0f)
    return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
}
