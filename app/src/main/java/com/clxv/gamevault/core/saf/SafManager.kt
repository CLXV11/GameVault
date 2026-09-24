package com.clxv.gamevault.core.saf

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.UriPermission
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage Access Framework glue. The app never asks for broad storage
 * permissions — every library folder is user-picked via
 * [Intent.ACTION_OPEN_DOCUMENT_TREE] and persisted with takePersistableUriPermission.
 */
@Singleton
class SafManager @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        const val REQ_OPEN_TREE = 1001

        fun openTreeIntent(): Intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                // Narrow the picker to removable storage roots when possible.
                putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            }
    }

    fun takePermission(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    fun persistedTreeUris(): List<Uri> =
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri }

    /** Call when a permission is revoked/lost so we stop tracking the root. */
    fun release(uri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    fun documentFile(treeUri: Uri): DocumentFile? =
        DocumentFile.fromTreeUri(context, treeUri)

    /** Human readable display path when the provider exposes one. */
    fun displayPath(uri: Uri): String {
        val docId = DocumentsContractCompat.getDocumentId(context, uri) ?: return uri.lastPathSegment ?: uri.toString()
        val parts = docId.split(":")
        val base = uri.authority?.substringBefore(".") ?: "storage"
        return "/" + listOf(base, parts.lastOrNull().orEmpty()).filter { it.isNotBlank() }.joinToString("/")
    }
}

/** Minimal android.provider.DocumentsContract wrapper kept local for testability. */
object DocumentsContractCompat {
    fun getDocumentId(context: Context, uri: Uri): String? = try {
        val docUri = if (android.provider.DocumentsContract.isTreeUri(uri))
            android.provider.DocumentsContract.buildDocumentUriUsingTree(
                uri, android.provider.DocumentsContract.getTreeDocumentId(uri))
        else uri
        android.provider.DocumentsContract.getDocumentId(docUri)
    } catch (e: Exception) {
        null
    }
}
