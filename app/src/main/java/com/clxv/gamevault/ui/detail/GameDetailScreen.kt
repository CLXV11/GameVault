package com.clxv.gamevault.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clxv.gamevault.R
import com.clxv.gamevault.core.model.Platform
import com.clxv.gamevault.core.model.ScanStatus
import com.clxv.gamevault.data.local.entity.GameWithFiles
import com.clxv.gamevault.ui.components.Cover3D
import com.clxv.gamevault.ui.components.PlatformIcon
import com.clxv.gamevault.ui.components.formatBytes
import com.clxv.gamevault.ui.components.formatDate
import com.clxv.gamevault.ui.components.platformLabel
import com.clxv.gamevault.ui.library.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    onBack: () -> Unit,
    onEditCover: (String) -> Unit,
    vm: GameDetailViewModel = hiltViewModel(),
    libraryVm: LibraryViewModel = hiltViewModel(),
) {
    val game by vm.game.collectAsState()
    val context = LocalContext.current
    var showIdentify by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    val g: GameWithFiles = game ?: return Box(Modifier.fillMaxSize()) {
        LinearProgressIndicator(Modifier.align(Alignment.Center))
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(g.game.title, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { vm.toggleFavorite() }) {
                        Icon(
                            imageVector = if (g.game.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(R.string.favorites),
                            tint = if (g.game.favorite) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Cover3D(coverModel = libraryVm.coverModel(g.game), width = 190.dp, enabled = true)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(status = g.files.firstOrNull()?.status ?: ScanStatus.UNKNOWN.name)
                    SuggestionChip(
                        onClick = { showIdentify = true },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                runCatching { Platform.valueOf(g.game.platform) }.getOrNull()
                                    ?.let { PlatformIcon(it, 18.dp) }
                                Spacer(Modifier.width(6.dp))
                                Text(platformLabel(g.game.platform))
                            }
                        },
                    )
                    if (g.game.region != "UNKNOWN") {
                        AssistChip(onClick = {}, label = { Text(g.game.region) })
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Open through the system chooser; GameVault never executes files itself.
                    OutlinedButton(onClick = {
                        g.files.firstOrNull()?.let { f ->
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse(f.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, null))
                            }
                        }
                    }) { Text(stringResource(R.string.open_file)) }
                    OutlinedButton(onClick = { onEditCover(g.game.id) }) { Text(stringResource(R.string.edit_cover)) }
                    OutlinedButton(onClick = { showIdentify = true }) { Text(stringResource(R.string.identify)) }
                }
            }
            item {
                OutlinedButton(onClick = { showNotes = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (g.game.notes.isBlank()) stringResource(R.string.notes) else g.game.notes, maxLines = 2)
                }
            }
            item { Text(stringResource(R.string.file_info), style = MaterialTheme.typography.titleMedium) }
            items(g.files, key = { it.id }) { f ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(f.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        Text(f.displayPath, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.size_x, formatBytes(f.size)), style = MaterialTheme.typography.labelMedium)
                            Text(f.format, style = MaterialTheme.typography.labelMedium)
                            Text(stringResource(R.string.confidence_x, "${(f.confidence * 100).toInt()}%"),
                                style = MaterialTheme.typography.labelMedium)
                        }
                        if (f.detectionReasons.isNotBlank()) {
                            Text(f.detectionReasons.replace("\n", " • "), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.last_viewed_x, formatDate(g.game.lastPlayedAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.hide(); onBack() }) {
                        Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.hide))
                    }
                    TextButton(onClick = { showRemoveConfirm = true }) {
                        Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.remove))
                    }
                }
            }
        }
    }

    if (showIdentify) {
        IdentifyGameDialog(
            current = runCatching { Platform.valueOf(g.game.platform) }.getOrNull(),
            onDismiss = { showIdentify = false },
            onPick = { vm.identifyManually(it); showIdentify = false },
        )
    }
    if (showNotes) {
        var notes by remember(g.game.notes) { mutableStateOf(g.game.notes) }
        AlertDialog(
            onDismissRequest = { showNotes = false },
            title = { Text(stringResource(R.string.notes)) },
            text = {
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    placeholder = { Text(stringResource(R.string.notes_hint)) },
                )
            },
            confirmButton = { TextButton(onClick = { vm.setNotes(notes); showNotes = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { showNotes = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text(stringResource(R.string.remove_confirm_title)) },
            text = { Text(stringResource(R.string.remove_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteFromLibrary(); onBack() }) {
                    Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showRemoveConfirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (label, color) = when (runCatching { ScanStatus.valueOf(status) }.getOrNull()) {
        ScanStatus.DETECTED -> stringResource(R.string.status_detected) to MaterialTheme.colorScheme.primary
        ScanStatus.PROBABLY -> stringResource(R.string.status_probably) to MaterialTheme.colorScheme.tertiary
        ScanStatus.MANUAL   -> stringResource(R.string.status_manual) to MaterialTheme.colorScheme.secondary
        ScanStatus.INVALID  -> stringResource(R.string.status_invalid) to MaterialTheme.colorScheme.error
        else                -> stringResource(R.string.status_unknown) to MaterialTheme.colorScheme.outline
    }
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
