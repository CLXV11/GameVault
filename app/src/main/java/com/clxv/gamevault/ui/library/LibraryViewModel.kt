package com.clxv.gamevault.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clxv.gamevault.core.model.Platform
import com.clxv.gamevault.core.scanner.LibraryScanner
import com.clxv.gamevault.core.settings.LibraryView
import com.clxv.gamevault.core.settings.SettingsManager
import com.clxv.gamevault.data.local.entity.GameEntity
import com.clxv.gamevault.data.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val games: List<GameEntity> = emptyList(),
    val query: String = "",
    val platformFilter: Platform? = null,
    val favoritesOnly: Boolean = false,
    val view: LibraryView = LibraryView.GRID,
    val coverScale: Float = 1.0f,
    val showUnknown: Boolean = true,
    val cover3d: Boolean = true,
    val platformCounts: Map<String, Int> = emptyMap(),
    val gameSizes: Map<String, Long> = emptyMap(),
    val gyroTilt: Boolean = true,
    val totalBytes: Long = 0L,
    val recent: List<GameEntity> = emptyList(),
    val selection: Set<String> = emptySet(),
    val loading: Boolean = true,
    val platformsPresent: List<Platform> = emptyList(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: GameRepository,
    private val settings: SettingsManager,
    val scanner: LibraryScanner,
    val coverManager: com.clxv.gamevault.core.covers.CoverManager,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val platformFilter = MutableStateFlow<Platform?>(null)
    private val favoritesOnly = MutableStateFlow(false)
    private val _selection = MutableStateFlow<Set<String>>(emptySet())

    private val gamesFlow = query.debounce(200).flatMapLatest { q ->
        if (q.isBlank()) repo.observeLibrary() else repo.search(q)
    }

    private data class Core(
        val games: List<com.clxv.gamevault.data.local.entity.GameEntity>,
        val q: String,
        val plat: Platform?,
        val favOnly: Boolean,
        val s: com.clxv.gamevault.core.settings.AppSettings,
    )

    private val platformCounts = repo.observePlatformCounts()
    private val recentGames = repo.observeRecentGames()
    private val totalBytes = repo.observeTotalBytes()
    private val gameSizes = repo.observeGameSizes()

    private val core = combine(
        gamesFlow, query, platformFilter, favoritesOnly, settings.settings,
    ) { games, q, plat, favOnly, s -> Core(games, q, plat, favOnly, s) }

    private val core2 = combine(core, platformCounts, recentGames) { c, counts, recent ->
        Triple(c, counts, recent)
    }

    val ui: StateFlow<LibraryUiState> = combine(
        core2, gameSizes, _selection, totalBytes,
    ) { (c, counts, recent), sizes, selection, bytes ->
        LibraryUiState(
            games = c.games
                .filter { g -> c.s.showUnknown || g.platform != Platform.UNKNOWN.name }
                .filter { g -> c.plat == null || g.platform == c.plat.name }
                .filter { g -> !c.favOnly || g.favorite },
            query = c.q,
            platformFilter = c.plat,
            favoritesOnly = c.favOnly,
            view = c.s.view,
            coverScale = c.s.coverScale,
            showUnknown = c.s.showUnknown,
            cover3d = c.s.cover3d,
            selection = selection,
            loading = false,
            platformCounts = counts.associate { it.platform to it.total },
            gameSizes = sizes.associate { it.gameId to it.size },
            gyroTilt = c.s.gyroTilt,
            totalBytes = bytes,
            recent = recent,
            platformsPresent = c.games.map { Platform.valueOf(it.platform) }.distinct().sortedBy { it.label },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryUiState())

    fun onQuery(q: String) { query.value = q }
    fun setView(v: com.clxv.gamevault.core.settings.LibraryView) =
        viewModelScope.launch { settings.setView(v) }
    fun onPlatformFilter(p: Platform?) { platformFilter.value = p }
    fun onFavoritesOnly(b: Boolean) { favoritesOnly.value = b }

    fun toggleFavorite(ids: List<String>) = viewModelScope.launch {
        val anyFav = ui.value.games.filter { it.id in ids }.any { it.favorite }
        repo.setFavorite(ids, !anyFav)
    }

    fun hide(ids: List<String>) = viewModelScope.launch { repo.setHidden(ids, true); clearSelection() }
    fun addManualGame(uri: android.net.Uri, title: String, platform: Platform?) =
        viewModelScope.launch { scanner.addSingleFile(uri, title, platform) }
    fun setCover3d(b: Boolean) = viewModelScope.launch { settings.setCover3d(b) }
    /** Batch manual identification for all selected games. */
    fun identifyMany(ids: List<String>, platform: Platform) = viewModelScope.launch {
        ids.forEach { repo.identifyManually(it, platform) }
        clearSelection()
    }

    /** Pick a random visible game — returns its id for navigation. */
    fun randomGameId(): String? = ui.value.games.randomOrNull()?.id
    fun removeFromLibrary(ids: List<String>) = viewModelScope.launch { repo.removeFromLibrary(ids); clearSelection() }
    fun addToCollection(collectionId: String, ids: List<String>) = viewModelScope.launch {
        repo.addToCollection(collectionId, ids)
    }

    fun toggleSelection(id: String) {
        _selection.value = if (id in _selection.value) _selection.value - id else _selection.value + id
    }
    fun clearSelection() { _selection.value = emptySet() }

    fun coverModel(game: GameEntity) = com.clxv.gamevault.ui.components.CoverModel(
        title = game.title,
        platformShort = com.clxv.gamevault.ui.components.platformLabel(game.platform).take(12),
        customCoverPath = game.customCoverPath,
        placeholderColors = coverManager.placeholderColors(game.title, game.platform),
    )
}
