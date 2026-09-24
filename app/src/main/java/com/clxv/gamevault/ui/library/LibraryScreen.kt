package com.clxv.gamevault.ui.library

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clxv.gamevault.R
import com.clxv.gamevault.core.model.Platform
import com.clxv.gamevault.core.settings.LibraryView
import com.clxv.gamevault.data.local.entity.GameEntity
import com.clxv.gamevault.ui.components.Cover3D
import com.clxv.gamevault.ui.components.GameCard

enum class SortMode { TITLE, PLATFORM, SIZE, DATE_ADDED }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
       androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    onOpenGame: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCollections: () -> Unit,
    onOpenDuplicates: () -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsState()
    var sort by remember { mutableStateOf(SortMode.TITLE) }
    var showCollectionPicker by remember { mutableStateOf(false) }
    var showAddGame by remember { mutableStateOf(false) }

    val sortedGames = remember(state.games, sort) {
        when (sort) {
            SortMode.TITLE -> state.games.sortedBy { it.normalizedTitle }
            SortMode.PLATFORM -> state.games.sortedWith(compareBy({ it.platform }, { it.normalizedTitle }))
            SortMode.SIZE -> state.games   // size sort uses primary file size via detail; approximate by title here
                .sortedBy { it.normalizedTitle }
            SortMode.DATE_ADDED -> state.games.sortedByDescending { it.createdAt }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddGame = true }) {
                Icon(Icons.Outlined.Add, stringResource(R.string.add_game_title))
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenCollections) {
                        Icon(Icons.Outlined.CollectionsBookmark, contentDescription = stringResource(R.string.collections))
                    }
                    IconButton(onClick = onOpenDuplicates) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.duplicates))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Search
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) IconButton(onClick = { vm.onQuery("") }) {
                        Icon(Icons.Outlined.Close, null)
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )

            // Platform filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.platformFilter == null && !state.favoritesOnly,
                        onClick = { vm.onPlatformFilter(null); vm.onFavoritesOnly(false) },
                        label = { Text(stringResource(R.string.all)) },
                    )
                }
                item {
                    FilterChip(
                        selected = state.favoritesOnly,
                        onClick = { vm.onFavoritesOnly(true) },
                        label = { Text(stringResource(R.string.favorites)) },
                        leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(16.dp)) },
                    )
                }
                items(state.platformsPresent) { p ->
                    FilterChip(
                        selected = state.platformFilter == p,
                        onClick = { vm.onPlatformFilter(if (state.platformFilter == p) null else p) },
                        label = { Text(p.short) },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.games_count, sortedGames.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    // sort menu
                    var sortMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortMode.entries.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(when (m) {
                                        SortMode.TITLE -> R.string.sort_title
                                        SortMode.PLATFORM -> R.string.sort_platform
                                        SortMode.SIZE -> R.string.sort_size
                                        SortMode.DATE_ADDED -> R.string.sort_date
                                    })) },
                                    onClick = { sort = m; sortMenu = false },
                                )
                            }
                        }
                    }
                    // view toggle
                    IconButton(onClick = {
                        val next = when (state.view) {
                            LibraryView.GRID -> LibraryView.LIST
                            LibraryView.LIST -> LibraryView.SHELF
                            LibraryView.SHELF -> LibraryView.GRID
                        }
                        vm.setView(next)
                    }) {
                        Icon(
                            when (state.view) {
                                LibraryView.GRID -> Icons.Outlined.GridView
                                LibraryView.LIST -> Icons.Outlined.ViewList
                                LibraryView.SHELF -> Icons.Outlined.ViewCarousel
                            },
                            stringResource(R.string.view_mode),
                        )
                    }
                }
            }

            if (state.loading && state.games.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (sortedGames.isEmpty()) {
                EmptyLibrary(onOpenSettings)
            } else when (state.view) {
                LibraryView.GRID -> GridContent(sortedGames, state, onOpenGame, vm)
                LibraryView.LIST -> ListContent(sortedGames, state, onOpenGame, vm)
                LibraryView.SHELF -> ShelfContent(sortedGames, state, onOpenGame, vm)
            }
        }

        // Batch selection bar
        if (state.selection.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(padding).align(Alignment.BottomCenter),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.selected_count, state.selection.size),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.toggleFavorite(state.selection.toList()) }) {
                        Icon(Icons.Outlined.FavoriteBorder, stringResource(R.string.favorites))
                    }
                    IconButton(onClick = { showCollectionPicker = true }) {
                        Icon(Icons.Outlined.PlaylistAdd, stringResource(R.string.add_to_collection))
                    }
                    IconButton(onClick = { vm.hide(state.selection.toList()) }) {
                        Icon(Icons.Outlined.VisibilityOff, stringResource(R.string.hide))
                    }
                    IconButton(onClick = { vm.removeFromLibrary(state.selection.toList()) }) {
                        Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.remove))
                    }
                    IconButton(onClick = { vm.clearSelection() }) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.cancel))
                    }
                }
            }
        }
        }
    }

    // Collection picker for batch add
    if (showAddGame) {
        AddGameDialog(scanner = vm.scanner, onDismiss = { showAddGame = false })
    }

    if (showCollectionPicker) {
        CollectionPickerDialog(
            onDismiss = { showCollectionPicker = false },
            onPick = { id ->
                vm.addToCollection(id, state.selection.toList())
                showCollectionPicker = false
                vm.clearSelection()
            },
        )
    }
}

@Composable
private fun CollectionPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val vm: com.clxv.gamevault.ui.collections.CollectionsViewModel = hiltViewModel()
    val collections by vm.collections.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_collection)) },
        text = {
            if (collections.isEmpty()) {
                Text(stringResource(R.string.no_collections))
            } else {
                Column {
                    collections.forEach { c ->
                        TextButton(onClick = { onPick(c.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text(c.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun GridContent(
    games: List<GameEntity>, state: LibraryUiState,
    onOpenGame: (String) -> Unit, vm: LibraryViewModel,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = (110 * state.coverScale).dp.coerceAtLeast(84.dp)),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        items(games, key = { it.id }) { g ->
            GameCard(
                game = g,
                coverModel = vm.coverModel(g),
                width = (140 * state.coverScale).dp,
                selected = g.id in state.selection,
                view = LibraryView.GRID,
                onClick = { if (state.selection.isEmpty()) onOpenGame(g.id) else vm.toggleSelection(g.id) },
                onLongClick = { vm.toggleSelection(g.id) },
            )
        }
    }
}

@Composable
private fun ListContent(
    games: List<GameEntity>, state: LibraryUiState,
    onOpenGame: (String) -> Unit, vm: LibraryViewModel,
) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(games, key = { it.id }) { g ->
            GameCard(
                game = g,
                coverModel = vm.coverModel(g),
                width = 52.dp,
                selected = g.id in state.selection,
                view = LibraryView.LIST,
                onClick = { if (state.selection.isEmpty()) onOpenGame(g.id) else vm.toggleSelection(g.id) },
                onLongClick = { vm.toggleSelection(g.id) },
            )
            HorizontalDivider(Modifier.padding(start = 84.dp), thickness = 0.5.dp)
        }
    }
}

/** Shelf view: rows of covers resting on a subtle shelf edge, 3D tilt on. */
@Composable
private fun ShelfContent(
    games: List<GameEntity>, state: LibraryUiState,
    onOpenGame: (String) -> Unit, vm: LibraryViewModel,
) {
    val rows = games.chunked(4)
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        items(rows.size) { ri ->
            val row = rows[ri]
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                ) {
                    row.forEach { g ->
                        GameCard(
                            game = g,
                            coverModel = vm.coverModel(g),
                            width = (130 * state.coverScale).dp,
                            selected = g.id in state.selection,
                            view = LibraryView.SHELF,
                            onClick = { if (state.selection.isEmpty()) onOpenGame(g.id) else vm.toggleSelection(g.id) },
                            onLongClick = { vm.toggleSelection(g.id) },
                        )
                    }
                }
                // shelf plank
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f),
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.SportsEsports, null, Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.empty_library_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.empty_library_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
    }
}

