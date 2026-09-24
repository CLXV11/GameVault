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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File

/**
 * Premium 3D cover: front + spine, perspective tilt with spring settle,
 * soft shadow and a subtle reflection. No text is rendered on the spine,
 * reflection or back surfaces, so nothing ever appears mirrored.
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

    val frontBitmap = remember(coverModel.customCoverPath) {
        val p = coverModel.customCoverPath
        if (p != null && File(p).exists()) BitmapFactory.decodeFile(p)?.asImageBitmap() else null
    }

    val (c1, c2) = coverModel.placeholderColors
    val corner: Shape = MaterialTheme.shapes.medium
    val aspect = 3f / 4f
    val spineW = width * 0.055f

    Box(
        modifier = modifier
            .width(width)
            .height(width / aspect)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        // Soft drop shadow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = 7.dp)
                .blur(12.dp)
                .alpha(0.35f)
                .background(Color.Black, corner),
        )

        // Tilt body (spine + front), clipped to the cover bounds
        Box(
            modifier = Modifier
                .fillMaxSize()
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
        ) {
            // Spine — gradient only, no text
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(spineW)
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(Color(c2), Color(c1)))),
            )
            // Front face
            CoverFace(
                bitmap = frontBitmap,
                title = coverModel.title,
                platformShort = coverModel.platformShort,
                c1 = Color(c1), c2 = Color(c2), shape = corner,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(width - spineW)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CoverFace(
    bitmap: ImageBitmap?,
    title: String,
    platformShort: String,
    c1: Color,
    c2: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c1, c2))),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap, contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // "?" placeholder — shown when no cover exists yet
            Text(
                text = "?",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White.copy(alpha = 0.30f),
                modifier = Modifier.align(Alignment.Center),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                    .let { m -> m },
            )
            Surface(
                color = Color.Black.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.TopStart).offset(8.dp, 8.dp),
            ) {
                Text(
                    text = platformShort,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.offset(x = 6.dp, y = 3.dp)
                        .let { m -> m },
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
