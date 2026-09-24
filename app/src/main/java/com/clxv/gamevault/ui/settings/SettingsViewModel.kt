package com.clxv.gamevault.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clxv.gamevault.core.scanner.LibraryScanner
import com.clxv.gamevault.core.settings.AppSettings
import com.clxv.gamevault.core.settings.LibraryView
import com.clxv.gamevault.core.settings.SettingsManager
import com.clxv.gamevault.core.settings.ThemeMode
import com.clxv.gamevault.data.local.AppDatabase
import com.clxv.gamevault.data.local.entity.LibraryRootEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val roots: List<LibraryRootEntity> = emptyList(),
    val scanHistory: List<com.clxv.gamevault.data.local.entity.ScanHistoryEntity> = emptyList(),
    val scanning: Boolean = false,
    val gameCount: Int = 0,
    val fileCount: Long = 0,
    val totalBytes: Long = 0,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val db: AppDatabase,
    val scanner: LibraryScanner,
) : ViewModel() {

    private val roots = db.libraryRootDao().observeAll()
    private val history = db.libraryRootDao().observeHistory()
    private val counts = combine(db.gameDao().observeGameCount(), db.gameDao().observeFileCount(), db.gameDao().observeTotalBytes()) { a, b, c -> Triple(a, b, c) }

    val ui: StateFlow<SettingsUiState> = combine(
        settingsManager.settings, roots, history, counts, scanner.progress,
    ) { s, r, h, c, p ->
        SettingsUiState(s, r, h, p.running, c.first, c.second, c.third)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setTheme(t: ThemeMode) = viewModelScope.launch { settingsManager.setTheme(t) }
    fun setCoverScale(f: Float) = viewModelScope.launch { settingsManager.setCoverScale(f) }
    fun setShowUnknown(b: Boolean) = viewModelScope.launch { settingsManager.setShowUnknown(b) }
    fun setGyro(b: Boolean) = viewModelScope.launch { settingsManager.setGyroTilt(b) }
    fun setMetadataEnabled(b: Boolean) = viewModelScope.launch { settingsManager.setMetadataEnabled(b) }
    fun setMetadataUrl(u: String) = viewModelScope.launch { settingsManager.setMetadataUrl(u) }

    fun addRoot(uri: android.net.Uri, displayName: String) = viewModelScope.launch {
        db.libraryRootDao().add(LibraryRootEntity(UUID.randomUUID().toString(), uri.toString(), displayName))
    }

    fun removeRoot(id: String) = viewModelScope.launch { db.libraryRootDao().delete(id) }
    fun rescan() = viewModelScope.launch { scanner.scanAllRoots() }
    fun cancelScan() = viewModelScope.launch { scanner.cancel() }
}
