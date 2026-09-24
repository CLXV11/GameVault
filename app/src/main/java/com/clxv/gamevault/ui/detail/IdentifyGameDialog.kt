package com.clxv.gamevault.ui.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.clxv.gamevault.R
import com.clxv.gamevault.core.model.Platform

@Composable
fun IdentifyGameDialog(current: Platform?, onDismiss: () -> Unit, onPick: (Platform) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.identify_title)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(Platform.entries.filter { it != Platform.UNKNOWN }) { p ->
                    TextButton(onClick = { onPick(p) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = p.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (p == current) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
