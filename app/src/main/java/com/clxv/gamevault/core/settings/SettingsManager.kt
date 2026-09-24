package com.clxv.gamevault.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class LibraryView { GRID, LIST, SHELF }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val view: LibraryView = LibraryView.GRID,
    val coverScale: Float = 1.0f,
    val showUnknown: Boolean = true,
    val gyroTilt: Boolean = true,
    val metadataProviderEnabled: Boolean = false,   // OFF unless the user configures one
    val metadataProviderUrl: String = "",
)

@Singleton
class SettingsManager @Inject constructor(@ApplicationContext context: Context) {

    private val ds = context.dataStore

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val VIEW = stringPreferencesKey("view")
        val COVER_SCALE = floatPreferencesKey("cover_scale")
        val SHOW_UNKNOWN = booleanPreferencesKey("show_unknown")
        val GYRO = booleanPreferencesKey("gyro_tilt")
        val META_ENABLED = booleanPreferencesKey("meta_enabled")
        val META_URL = stringPreferencesKey("meta_url")
    }

    val settings: Flow<AppSettings> = ds.data.map { p ->
        AppSettings(
            theme = runCatching { ThemeMode.valueOf(p[Keys.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            view = runCatching { LibraryView.valueOf(p[Keys.VIEW] ?: "GRID") }.getOrDefault(LibraryView.GRID),
            coverScale = p[Keys.COVER_SCALE] ?: 1.0f,
            showUnknown = p[Keys.SHOW_UNKNOWN] ?: true,
            gyroTilt = p[Keys.GYRO] ?: true,
            metadataProviderEnabled = p[Keys.META_ENABLED] ?: false,
            metadataProviderUrl = p[Keys.META_URL] ?: "",
        )
    }

    suspend fun setTheme(t: ThemeMode) = ds.edit { it[Keys.THEME] = t.name }
    suspend fun setView(v: LibraryView) = ds.edit { it[Keys.VIEW] = v.name }
    suspend fun setCoverScale(s: Float) = ds.edit { it[Keys.COVER_SCALE] = s.coerceIn(0.6f, 1.6f) }
    suspend fun setShowUnknown(b: Boolean) = ds.edit { it[Keys.SHOW_UNKNOWN] = b }
    suspend fun setGyroTilt(b: Boolean) = ds.edit { it[Keys.GYRO] = b }
    suspend fun setMetadataEnabled(b: Boolean) = ds.edit { it[Keys.META_ENABLED] = b }
    suspend fun setMetadataUrl(u: String) = ds.edit { it[Keys.META_URL] = u }
}
