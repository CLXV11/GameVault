package com.clxv.gamevault.core.metadata

import com.clxv.gamevault.core.model.Platform

/**
 * Optional online metadata lookup. Disabled unless the user explicitly
 * configures a provider URL in settings. Implementations must:
 *  - never scrape blindly; only call the configured endpoint
 *  - cache results in the local DB
 *  - be fully overridable by the user afterwards
 *
 * The interface ships with [DisabledMetadataProvider] as the default so the
 * app is 100% functional offline.
 */
interface MetadataProvider {
    val id: String
    suspend fun lookup(title: String, platform: Platform): MetadataResult?
}

sealed class MetadataResult {
    data class Found(
        val title: String?,
        val coverUrl: String?,
        val description: String?,
        val releaseYear: Int?,
    ) : MetadataResult()
    data class NotFound(val reason: String) : MetadataResult()
}

class DisabledMetadataProvider : MetadataProvider {
    override val id = "disabled"
    override suspend fun lookup(title: String, platform: Platform): MetadataResult? = null
}

/**
 * Simple JSON-over-HTTP provider skeleton for the user's own endpoint.
 * Marked clearly as opt-in; not wired to any service by default.
 */
class HttpMetadataProvider(private val baseUrl: String) : MetadataProvider {
    override val id = "http"
    override suspend fun lookup(title: String, platform: Platform): MetadataResult? {
        if (baseUrl.isBlank()) return null
        // Intentionally minimal: GET "$baseUrl/lookup?title=...&platform=..."
        // The user configures the endpoint; no hardcoded services exist.
        return null
    }
}
