package com.clxv.gamevault.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clxv.gamevault.R
import com.clxv.gamevault.core.detection.DetectionEngine
import com.clxv.gamevault.core.detection.SafByteReader
import com.clxv.gamevault.core.model.Platform
import com.clxv.gamevault.ui.components.platformLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manual add: pick any file -> the app sniffs it (bounded reads) and proposes
 * a title + platform automatically; the user can edit the title and confirm.
 */
@Composable
fun AddGameDialog(
    scanner: com.clxv.gamevault.core.scanner.LibraryScanner,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var detected by remember { mutableStateOf<Platform?>(null) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        picked = uri
        busy = true
        scope.launch {
            val info = withContext(Dispatchers.IO) {
                var name = uri.lastPathSegment ?: "file"
                var size = 0L
                runCatching {
                    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val si = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (c.moveToFirst()) {
                            if (ni >= 0) c.getString(ni)?.let { name = it }
                            if (si >= 0) size = c.getLong(si)
                        }
                    }
                }
                val engine = DetectionEngine(SafByteReader(context, uri))
                val det = engine.detect(name, if (size > 0) size else 1)
                Triple(name, engine.normalizeTitle(name), det)
            }
            fileName = info.first
            title = info.second
            detected = if (info.third.platform != Platform.UNKNOWN) info.third.platform else null
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.add_game_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.pick_file)) }

                if (picked != null) {
                    Text(
                        fileName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.game_title_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    detected?.let { p ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.auto_detected_x, platformLabel(p.name))) },
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.add_game_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked != null && title.isNotBlank() && !busy,
                onClick = {
                    scope.launch {
                        scanner.addSingleFile(picked!!, title.trim(), detected)
                        onDismiss()
                    }
                },
            ) { Text(stringResource(R.string.save_game)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
