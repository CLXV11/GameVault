package com.clxv.gamevault.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
                    Cover3D(coverModel = coverModel, width = width, enabled = !selected)
                    if (game.favorite) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
                        )
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
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
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
                Cover3D(coverModel = coverModel, width = 52.dp, enabled = false)
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
            GridCardFallback(game, coverModel, width, selected, onClick, onLongClick, modifier)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCardFallback(
    game: GameEntity, coverModel: CoverModel, width: Dp, selected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier,
) {
    Column(modifier = modifier.width(width).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Cover3D(coverModel = coverModel, width = width, enabled = false)
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
