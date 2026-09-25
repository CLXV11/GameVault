package com.clxv.gamevault.ui.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clxv.gamevault.data.local.entity.CollectionWithGames
import com.clxv.gamevault.data.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CollectionsViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {

    val collections: StateFlow<List<com.clxv.gamevault.data.local.dao.CollectionWithCount>> =
        repo.observeCollectionsWithCounts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observeCollection(id: String): Flow<CollectionWithGames?> = repo.observeCollection(id)

    fun create(name: String) = viewModelScope.launch { repo.createCollection(name) }
    fun organize() = viewModelScope.launch { repo.organizeByPlatform() }
    fun delete(id: String) = viewModelScope.launch { repo.deleteCollection(id) }
    fun removeGames(collectionId: String, gameIds: List<String>) = viewModelScope.launch {
        repo.removeFromCollection(collectionId, gameIds)
    }
}
