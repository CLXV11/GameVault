package com.clxv.gamevault.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cover presentation: NOTHING but the image. Transparent background, no spine,
 * no shadow, no frame, no border. Optional drag tilt (3D) or perfectly flat (2D).
 */
@Composable
fun Cover3D(
    coverModel: CoverModel,
    modifier: Modifier = Modifier,
    width: Dp = 140.dp,
    enabled: Boolean = true,
    onTiltChange: ((Float, Float) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val rotY = remember { Animatable(0f) }
    val rotX = remember { Animatable(0f) }
    val spring: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

    val frontBitmap by produceState<ImageBitmap?>(initialValue = null, coverModel.customCoverPath) {
        value = withContext(Dispatchers.IO) { decodeCoverBitmap(coverModel.customCoverPath) }
    }

    val (c1, c2) = coverModel.placeholderColors
    val corner: Shape = MaterialTheme.shapes.medium
    val aspect = 3f / 4f

    Box(
        modifier = modifier
            .width(width)
            .wrapContentHeight()
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        if (frontBitmap != null) {
            // The cover art, exactly as provided: natural aspect, transparent
            // margins show the wallpaper — no cropping, no stretching, no frame.
            Image(
                bitmap = frontBitmap!!,
                contentDescription = coverModel.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(corner)
                    .graphicsLayer {
                        rotationY = rotY.value
                        rotationX = rotX.value
                        cameraDistance = 14f * density
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        detectDragGestures(
                            onDragEnd = {
                                scope.launch { rotY.animateTo(0f, spring) }
                                scope.launch { rotX.animateTo(0f, spring) }
                                onTiltChange?.invoke(0f, 0f)
                            },
                            onDragCancel = {
                                scope.launch { rotY.animateTo(0f, spring) }
                                scope.launch { rotX.animateTo(0f, spring) }
                            },
                        ) { change, drag ->
                            change.consume()
                            scope.launch { rotY.snapTo((rotY.value + drag.x / 12f).coerceIn(-38f, 38f)) }
                            scope.launch { rotX.snapTo((rotX.value - drag.y / 14f).coerceIn(-22f, 22f)) }
                            onTiltChange?.invoke(rotY.value, rotX.value)
                        }
                    },
            )
        } else {
            // Minimal "?" placeholder: soft gradient + glyph (fixed 3:4 body)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .clip(corner)
                    .background(Brush.verticalGradient(listOf(Color(c1), Color(c2)))),
            ) {
                Text(
                    text = "?",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White.copy(alpha = 0.30f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

data class CoverModel(
    val title: String,
    val platformShort: String,
    val customCoverPath: String?,
    val placeholderColors: Pair<Long, Long>,
)

/** Sampled decode so full-size images never hit the main thread unscaled.
 *  Never throws: corrupt/missing files simply yield null. */
internal fun decodeCoverBitmap(path: String?, targetWidth: Int = 420): ImageBitmap? =
    runCatching {
        if (path == null || !File(path).exists()) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / (sample * 2) > targetWidth) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    }.getOrNull()
