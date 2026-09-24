package com.clxv.gamevault.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clxv.gamevault.core.covers.CoverManager
import com.clxv.gamevault.core.model.Platform
import com.clxv.gamevault.data.local.entity.GameWithFiles
import com.clxv.gamevault.data.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: GameRepository,
    private val coverManager: CoverManager,
) : ViewModel() {

    private val gameId: String = checkNotNull(savedStateHandle["gameId"])

    val game: StateFlow<GameWithFiles?> = repo.observeGameWithFiles(gameId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val collectionIds: StateFlow<List<String>> = repo.collectionIdsFor(gameId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // "recently viewed" metadata — viewed, not executed.
        viewModelScope.launch { repo.touchPlayed(gameId) }
    }

    fun toggleFavorite() = viewModelScope.launch {
        game.value?.let { repo.setFavorite(listOf(it.game.id), !it.game.favorite) }
    }

    fun identifyManually(platform: Platform) = viewModelScope.launch {
        repo.identifyManually(gameId, platform)
    }

    fun rename(newTitle: String) = viewModelScope.launch { repo.renameTitle(gameId, newTitle) }
    fun setNotes(n: String) = viewModelScope.launch { repo.setNotes(gameId, n) }

    /** Suspends until the cover is persisted — callers must stay on screen until done. */
    suspend fun saveCustomCover(bitmap: android.graphics.Bitmap): Boolean = runCatching {
        val path = coverManager.saveCustomCover(gameId, bitmap)
        val g = game.value?.game ?: return@runCatching false
        repo.updateGame(g.copy(customCoverPath = path))
        true
    }.getOrDefault(false)

    suspend fun resetCover(): Boolean = runCatching {
        coverManager.resetCover(gameId)
        val g = game.value?.game ?: return@runCatching false
        repo.updateGame(g.copy(customCoverPath = null))
        true
    }.getOrDefault(false)

    fun hide() = viewModelScope.launch { repo.setHidden(listOf(gameId), true) }
    fun deleteFromLibrary() = viewModelScope.launch { repo.removeFromLibrary(listOf(gameId)) }
}
