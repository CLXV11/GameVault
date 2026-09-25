package com.clxv.gamevault.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clxv.gamevault.core.model.Platform
import com.clxv.gamevault.core.model.ScanStatus
import com.clxv.gamevault.data.local.entity.GameEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCard(
    game: GameEntity,
    coverModel: CoverModel,
    width: Dp,
    selected: Boolean,
    view: com.clxv.gamevault.core.settings.LibraryView,
    cover3d: Boolean = true,
    tiltEnabled: Boolean = true,
    isNew: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (view) {
        com.clxv.gamevault.core.settings.LibraryView.GRID -> {
            Column(
                modifier = modifier
                    .width(width)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    if (cover3d) Cover3D(coverModel = coverModel, width = width, enabled = !selected && tiltEnabled)
                    else FlatCover(coverModel = coverModel, width = width)
                    if (game.favorite) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
                        )
                    }
                    if (isNew) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(bottomEnd = 8.dp),
                            modifier = Modifier.align(Alignment.TopStart),
                        ) {
                            Text(
                                stringResource(R.string.new_badge),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (selected) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            modifier = Modifier.matchParentSize(),
                        ) {}
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = platformLabel(game.platform),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        com.clxv.gamevault.core.settings.LibraryView.LIST -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (cover3d) Cover3D(coverModel = coverModel, width = 52.dp, enabled = false)
                else FlatCover(coverModel = coverModel, width = 52.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(game.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        platformLabel(game.platform),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (game.favorite) {
                    Icon(Icons.Outlined.FavoriteBorder, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                if (selected) {
                    RadioButton(selected = true, onClick = null)
                }
            }
        }
        com.clxv.gamevault.core.settings.LibraryView.SHELF -> {
            // Shelf mode renders handled by the shelf composable in LibraryScreen;
            // this branch is unused but keeps the exhaustive when cheap.
            GridCardFallback(game, coverModel, width, selected, cover3d, onClick, onLongClick, modifier)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCardFallback(
    game: GameEntity, coverModel: CoverModel, width: Dp, selected: Boolean, cover3d: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier,
) {
    Column(modifier = modifier.width(width).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        if (cover3d) Cover3D(coverModel = coverModel, width = width, enabled = false)
        else FlatCover(coverModel = coverModel, width = width)
        Spacer(Modifier.height(6.dp))
        Text(game.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun platformLabel(name: String): String =
    runCatching { Platform.valueOf(name).label }.getOrDefault(name)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble(); var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format(Locale.US, "%.1f %s", v, units[i])
}

fun formatDate(ts: Long?): String {
    if (ts == null) return "—"
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))
}


/** Flat 2D cover variant (no perspective), used when 3D mode is off. Pure image. */
@Composable
fun FlatCover(
    coverModel: CoverModel,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val frontBitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null, coverModel.customCoverPath,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.clxv.gamevault.ui.components.decodeCoverBitmap(coverModel.customCoverPath)
        }
    }
    val (c1, c2) = coverModel.placeholderColors
    Box(
        modifier = modifier
            .width(width)
            .aspectRatio(3f / 4f)
            .clip(MaterialTheme.shapes.medium),
    ) {
        if (frontBitmap != null) {
            Image(
                bitmap = frontBitmap!!, contentDescription = coverModel.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(composeColor(c1), composeColor(c2)))),
            ) {
                Text(
                    "?", style = MaterialTheme.typography.headlineMedium,
                    color = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

private fun composeColor(v: Long) = Color(v)
