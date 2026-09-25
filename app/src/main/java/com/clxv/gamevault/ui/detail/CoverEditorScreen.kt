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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clxv.gamevault.R
import com.clxv.gamevault.ui.library.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cover editor with LIVE preview: the crop window shows exactly what will be
 * saved. Pinch/pan/rotate/fit/fill, then save. The original file is never
 * touched; the result is stored in the app's private cover directory.
 *
 * Math note: the image is laid out with ContentScale.Fit (centered), and the
 * graphics layer scales about the center. So the visible image rect in
 * bitmap pixels is:
 *   left = (winW - imgW * fitScale * userScale) / 2 + offset.x   (in view px)
 * normalized by (fitScale * userScale * imgW).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverEditorScreen(
    onBack: () -> Unit,
    vm: GameDetailViewModel = hiltViewModel(),
    libraryVm: LibraryViewModel = hiltViewModel(),
) {
    val coverManager = libraryVm.coverManager
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var source by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var fill by remember { mutableStateOf(false) }
    var win by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scale = 1f; offset = Offset.Zero; error = false
        scope.launch {
            source = withContext(Dispatchers.IO) { coverManager.decodeSampled(uri) }
            error = source == null
        }
    }

    // live cropped preview — exactly what "Save cover" will persist
    val preview by produceState<Bitmap?>(initialValue = null, source, scale, offset, fill, win) {
        val src = source ?: return@produceState
        val w = win; if (w.width == 0 || w.height == 0) return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching { computeCrop(src, w, scale, offset, fill) }.getOrNull()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_cover)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                ),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (source != null) {
                        TextButton(onClick = { fill = !fill; offset = Offset.Zero; scale = 1f }) {
                            Text(stringResource(if (fill) R.string.fill else R.string.fit))
                        }
                        IconButton(onClick = {
                            source?.let { source = coverManager.rotate(it, 90f) }
                            offset = Offset.Zero; scale = 1f
                        }) { Icon(Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.rotate)) }
                        TextButton(
                            enabled = !saving && preview != null,
                            onClick = {
                                val out = preview ?: return@TextButton
                                saving = true
                                // Stay on screen until the cover is fully persisted —
                                // leaving early used to cancel the save mid-flight.
                                scope.launch {
                                    // Fully finish (file write + DB update) before leaving;
                                    // the button shows "…" and the screen stays put meanwhile.
                                    val ok = runCatching { vm.saveCustomCover(out) }.getOrDefault(false)
                                    saving = false
                                    if (ok) onBack()
                                }
                            },
                        ) { Text(if (saving) "…" else stringResource(R.string.save_cover)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (source == null && !error) {
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.cover_editor_hint), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(stringResource(R.string.pick_image)) }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = {
                    scope.launch { vm.resetCover(); onBack() }
                }) {
                    Text(stringResource(R.string.reset_cover), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
            } else if (error) {
                Spacer(Modifier.weight(1f))
                Text("⚠", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                Text("Could not decode this image.", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(stringResource(R.string.pick_image)) }
                Spacer(Modifier.weight(1f))
            } else {
                val src = source!!
                // editing stage
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .aspectRatio(3f / 4f)
                            .fillMaxHeight()
                            .onSizeChanged { win = it }
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                    ) {
                        val transform = rememberTransformableState { zoom, pan, _ ->
                            val fit = fitScale(src, win)
                            val fillS = fillScale(src, win)
                            val minS = if (fill) fillS / fit else 1f
                            scale = (scale * zoom).coerceIn(minS, 8f)
                            offset = clampOffset(offset + pan, src, win, fit * scale)
                        }
                        Image(
                            bitmap = src.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = offset.x
                                    translationY = offset.y
                                    scaleX = scale; scaleY = scale
                                }
                                .transformable(transform),
                        )
                        // live preview inset (what will be saved)
                        preview?.let { p ->
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .width(64.dp)
                                    .aspectRatio(3f / 4f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp)),
                            ) {
                                Image(
                                    bitmap = p.asImageBitmap(),
                                    contentDescription = stringResource(R.string.preview),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
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

private fun fitScale(bmp: Bitmap, win: IntSize): Float =
    if (win.width == 0) 1f
    else minOf(win.width.toFloat() / bmp.width, win.height.toFloat() / bmp.height)

private fun fillScale(bmp: Bitmap, win: IntSize): Float =
    if (win.width == 0) 1f
    else maxOf(win.width.toFloat() / bmp.width, win.height.toFloat() / bmp.height)

private fun clampOffset(o: Offset, bmp: Bitmap, win: IntSize, eff: Float): Offset {
    if (win.width == 0) return o
    val dispW = bmp.width * eff
    val dispH = bmp.height * eff
    val maxX = maxOf((dispW - win.width) / 2f, 0f)
    val maxY = maxOf((dispH - win.height) / 2f, 0f)
    return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
}

/** The exact crop that will be persisted, derived from the same math as the preview. */
private fun computeCrop(src: Bitmap, win: IntSize, scale: Float, offset: Offset, fill: Boolean): Bitmap {
    val fit = fitScale(src, win)
    val eff = fit * scale
    val leftPx = (win.width - src.width * eff) / 2f + offset.x
    val topPx = (win.height - src.height * eff) / 2f + offset.y
    val wPx = win.width / eff
    val hPx = win.height / eff
    val x = (leftPx / eff).toInt().coerceIn(0, src.width - 1)
    val y = (topPx / eff).toInt().coerceIn(0, src.height - 1)
    val w = wPx.toInt().coerceIn(1, src.width - x)
    val h = hPx.toInt().coerceIn(1, src.height - y)
    return Bitmap.createBitmap(src, x, y, w, h)
}
