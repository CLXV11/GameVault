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

    val ui: StateFlow<LibraryUiState> = combine(
        query.debounce(200).flatMapLatest { q ->
            if (q.isBlank()) repo.observeLibrary() else repo.search(q)
        },
        platformFilter, favoritesOnly, settings.settings, _selection,
    ) { games, plat, favOnly, s, selection ->
        LibraryUiState(
            games = games
                .filter { g -> plat == null || g.platform == plat.name }
                .filter { g -> !favOnly || g.favorite },
            query = query.value,
            platformFilter = plat,
            favoritesOnly = favOnly,
            view = s.view,
            coverScale = s.coverScale,
            showUnknown = s.showUnknown,
            selection = selection,
            loading = false,
            platformsPresent = games.map { Platform.valueOf(it.platform) }.distinct().sortedBy { it.label },
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
