package com.clxv.gamevault.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File

/**
 * Premium 3D cover: front + spine + optional back, perspective transform,
 * soft shadow, subtle floor reflection, and drag-based tilt with spring
 * settle. All transforms run on the render thread via [graphicsLayer]
 * (GPU-accelerated, no recomposition per frame).
 */
@Composable
fun Cover3D(
    coverModel: CoverModel,
    modifier: Modifier = Modifier,
    width: Dp = 140.dp,
    showBack: Boolean = true,
    enabled: Boolean = true,
    onTiltChange: ((Float, Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val rotY = remember { Animatable(0f) }
    val rotX = remember { Animatable(0f) }
    val spring: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

    // Front cover bitmap: custom cover file, else generated placeholder.
    val frontBitmap = remember(coverModel.customCoverPath, coverModel.title, coverModel.platformShort) {
        val p = coverModel.customCoverPath
        if (p != null && File(p).exists()) {
            BitmapFactory.decodeFile(p)?.asImageBitmap()
        } else null
    }

    val (c1, c2) = coverModel.placeholderColors
    val corner = MaterialTheme.shapes.medium

    val aspect = 3f / 4f
    val spineW = width * 0.06f

    Box(
        modifier = modifier.width(width + spineW).height(width / aspect),
        contentAlignment = Alignment.Center,
    ) {
        // Soft shadow behind the cover
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = 8.dp)
                .blur(14.dp)
                .alpha(0.35f)
                .background(Color.Black, corner),
        )

        // Reflection below
        if (frontBitmap != null || coverModel.title.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(width / aspect * 0.42f)
                    .offset(y = (width / aspect) * 0.98f)
                    .graphicsLayer { alpha = 0.18f; scaleY = -1f }
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.9f), Color.Transparent),
                            ),
                        )
                    },
            ) {
                CoverFace(
                    bitmap = frontBitmap, title = coverModel.title, subtitle = coverModel.platformShort,
                    c1 = Color(c1), c2 = Color(c2), shape = corner,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 3D body
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
                        val maxY = 38f
                        val maxX = 22f
                        val targetY = (rotY.value + drag.x / 12f).coerceIn(-maxY, maxY)
                        val targetX = (rotX.value - drag.y / 14f).coerceIn(-maxX, maxX)
                        scope.launch { rotY.snapTo(targetY) }
                        scope.launch { rotX.snapTo(targetX) }
                        onTiltChange?.invoke(targetY, targetX)
                    }
                },
        ) {
            // Spine (visible when rotated)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(spineW)
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(listOf(Color(c2), Color(c1))),
                    ),
            )

            // Front face
            CoverFace(
                bitmap = frontBitmap,
                title = coverModel.title,
                subtitle = coverModel.platformShort,
                c1 = Color(c1), c2 = Color(c2), shape = corner,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(width)
                    .fillMaxSize()
                    .clipToBounds(),
            )

            // Back cover (opposite the spine)
            if (showBack) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(width)
                        .fillMaxSize()
                        .offset(x = -width + spineW)
                        .graphicsLayer { rotationY = 180f },
                ) {
                    CoverFace(
                        bitmap = null,
                        title = coverModel.title,
                        subtitle = coverModel.platformShort,
                        c1 = Color(c2), c2 = Color(c1), shape = corner,
                        modifier = Modifier.fillMaxSize(),
                        isBack = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverFace(
    bitmap: ImageBitmap?,
    title: String,
    subtitle: String,
    c1: Color,
    c2: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    isBack: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c1, c2))),
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            // Generated placeholder: platform-tinted panel, title initial + text
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c1, c2)))) {
                Text(
                    text = title.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.align(Alignment.Center),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = if (isBack) 6 else 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(if (isBack) Alignment.TopCenter else Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                        .then(if (isBack) Modifier else Modifier),
                )
                if (!isBack) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.align(Alignment.TopStart).offset(8.dp, 8.dp),
                    ) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.offset(0.dp, 0.dp).let { it },
                        )
                    }
                }
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
