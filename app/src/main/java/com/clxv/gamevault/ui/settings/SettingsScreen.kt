package com.clxv.gamevault.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clxv.gamevault.R
import com.clxv.gamevault.core.settings.ThemeColor
import com.clxv.gamevault.core.settings.ThemeMode
import com.clxv.gamevault.data.repository.GameRepository
import com.clxv.gamevault.ui.components.formatBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HiddenViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val hidden: StateFlow<List<com.clxv.gamevault.data.local.entity.GameEntity>> =
        repo.observeHidden().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun unhide(ids: List<String>) = viewModelScope.launch { repo.setHidden(ids, false) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
    hiddenVm: HiddenViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsState()
    val context = LocalContext.current
    var metaUrl by remember(state.settings.metadataProviderUrl) { mutableStateOf(state.settings.metadataProviderUrl) }
    var showHidden by remember { mutableStateOf(false) }
    var confirmClean by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val backgrounds = listOf(
        "none" to stringResource(R.string.bg_none),
        "cloud_house.jpg" to "Cloud House",
        "rain_street.jpg" to "Rain Street",
        "canal_bridge.jpg" to "Canal Bridge",
        "orbit.jpg" to "Orbit",
        "stop_sign.jpg" to "Stop Sign",
        "knight_rest.jpg" to "Knight",
        "sakura_pikachu.jpg" to "Sakura",
    )

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val name = DocumentFile.fromTreeUri(context, it)?.name ?: it.lastPathSegment ?: it.toString()
            vm.addRoot(it, name)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                ),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle(stringResource(R.string.library_folders)) }
            items(state.roots, key = { it.id }) { r ->
                ListItem(
                    headlineContent = { Text(r.displayName) },
                    supportingContent = { Text(r.uri) },
                    trailingContent = {
                        IconButton(onClick = {
                            runCatching {
                                context.contentResolver.releasePersistableUriPermission(
                                    android.net.Uri.parse(r.uri),
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            }
                            vm.removeRoot(r.id)
                        }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.remove)) }
                    },
                )
            }
            item {
                OutlinedButton(
                    onClick = { treeLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.CreateNewFolder, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_folder))
                }
            }

            item { SectionTitle(stringResource(R.string.scan)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.rescan() },
                        enabled = !state.scanning && state.roots.isNotEmpty(),
                    ) { Text(stringResource(R.string.rescan)) }
                    if (state.scanning) {
                        TextButton(onClick = { vm.cancelScan() }) { Text(stringResource(R.string.cancel_scan)) }
                    }
                }
                if (state.scanning) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            items(state.scanHistory.take(5)) { h ->
                Text(
                    "${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(h.startedAt))}" +
                    "  ·  ${h.status}  ·  " + stringResource(R.string.games_count, h.gamesAdded + h.gamesUpdated),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { SectionTitle(stringResource(R.string.appearance)) }
            item {
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
                Row {
                    ThemeMode.entries.forEach { t ->
                        FilterChip(
                            selected = state.settings.theme == t,
                            onClick = { vm.setTheme(t) },
                            label = { Text(stringResource(when (t) {
                                ThemeMode.SYSTEM -> R.string.theme_system
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.DARK -> R.string.theme_dark
                            })) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
            item {
                Column {
                    Text(stringResource(R.string.cover_size), style = MaterialTheme.typography.titleSmall)
                    Slider(value = state.settings.coverScale, onValueChange = { vm.setCoverScale(it) }, valueRange = 0.6f..1.6f)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.show_unknown), modifier = Modifier.weight(1f))
                    Switch(checked = state.settings.showUnknown, onCheckedChange = { vm.setShowUnknown(it) })
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.gyro_tilt), modifier = Modifier.weight(1f))
                    Switch(checked = state.settings.gyroTilt, onCheckedChange = { vm.setGyro(it) })
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.view_3d), modifier = Modifier.weight(1f))
                    Switch(checked = state.settings.cover3d, onCheckedChange = { vm.setCover3d(it) })
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.show_new_badge), modifier = Modifier.weight(1f))
                    Switch(checked = state.settings.showNewBadge, onCheckedChange = { vm.setShowNewBadge(it) })
                }
            }

            item { SectionTitle(stringResource(R.string.theme_color)) }
            item {
                // Scrollable — long labels in some locales must never clip the last chip
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ThemeColor.entries.size) { i ->
                        val c = ThemeColor.entries[i]
                        FilterChip(
                            selected = state.settings.themeColor == c,
                            onClick = { vm.setThemeColor(c) },
                            label = { Text(stringResource(when (c) {
                                ThemeColor.DEFAULT -> R.string.theme_default
                                ThemeColor.EMERALD -> R.string.theme_emerald
                                ThemeColor.SUNSET -> R.string.theme_sunset
                                ThemeColor.AMETHYST -> R.string.theme_amethyst
                                ThemeColor.GRAPHITE -> R.string.theme_graphite
                            }), maxLines = 1) },
                        )
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.background)) }
            item {
                // Image picker: real thumbnails, tap to apply
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(backgrounds.size) { i ->
                        val (file, label) = backgrounds[i]
                        val selected = state.settings.background == file
                        WallpaperThumb(
                            file = file,
                            label = label,
                            selected = selected,
                            onClick = { vm.setBackground(file) },
                        )
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.metadata_provider)) }
            item {
                Text(stringResource(R.string.metadata_provider_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.enabled), modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.settings.metadataProviderEnabled,
                        onCheckedChange = { vm.setMetadataEnabled(it) },
                    )
                }
                OutlinedTextField(
                    value = metaUrl, onValueChange = { metaUrl = it },
                    label = { Text("Endpoint URL") },
                    enabled = state.settings.metadataProviderEnabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { vm.setMetadataUrl(metaUrl) }) { Text(stringResource(R.string.save)) }
            }

            item { SectionTitle(stringResource(R.string.support)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        runCatching {
                            context.startActivity(android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/CLXV11/GameVault")))
                        }
                    }) {
                        Icon(Icons.Outlined.Code, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.github))
                    }
                    FilledTonalButton(onClick = {
                        runCatching {
                            context.startActivity(android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://t.me/EPCD11")))
                        }
                    }) {
                        Icon(Icons.Outlined.Send, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.telegram))
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.storage_stats)) }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.games_count, state.gameCount))
                        Text(stringResource(R.string.files_count, state.fileCount.toInt()))
                        Text(stringResource(R.string.total_size_x, formatBytes(state.totalBytes)))
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showHidden = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.hidden_games))
                }
            }
            item {
                OutlinedButton(
                    onClick = { confirmClean = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.clean_unknown)) }
            }
            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val f = vm.exportLibraryJson(context)
                            if (f != null) {
                                runCatching {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context, context.packageName + ".fileprovider", f)
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        android.content.Intent.createChooser(send, null))
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.export_library)) }
            }
        }
    }

    if (confirmClean) {
        AlertDialog(
            onDismissRequest = { confirmClean = false },
            title = { Text(stringResource(R.string.clean_unknown)) },
            text = { Text(stringResource(R.string.clean_unknown_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.cleanUnknown(); confirmClean = false }) {
                    Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClean = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showHidden) {
        val hidden by hiddenVm.hidden.collectAsState()
        AlertDialog(
            onDismissRequest = { showHidden = false },
            title = { Text(stringResource(R.string.hidden_games)) },
            text = {
                if (hidden.isEmpty()) Text("—")
                else Column {
                    hidden.forEach { g ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.title, modifier = Modifier.weight(1f), maxLines = 1)
                            TextButton(onClick = { hiddenVm.unhide(listOf(g.id)) }) {
                                Text(stringResource(R.string.unhide))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHidden = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary)
    }
}


/** Wallpaper thumbnail loaded from assets (sampled) with selection ring. */
@Composable
private fun WallpaperThumb(
    file: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(initialValue = null, file) {
        value = if (file == "none") null else
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    context.assets.open("backgrounds/$file").use { input ->
                        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                        android.graphics.BitmapFactory.decodeStream(input, null, opts)
                    }
                }.getOrNull()
            }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            border = if (selected)
                androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
            else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.width(120.dp).height(74.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp!!.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
