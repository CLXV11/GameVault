package com.clxv.gamevault.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.clxv.gamevault.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(onBack: () -> Unit, vm: CollectionsViewModel = hiltViewModel()) {
    val collections by vm.collections.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.collections)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { IconButton(onClick = { showCreate = true }) { Icon(Icons.Outlined.Add, null) } },
            )
        },
    ) { padding ->
        val sel = selectedId
        if (sel == null) {
            if (collections.isEmpty()) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_collections), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.padding(padding)) {
                    items(collections, key = { it.id }) { c ->
                        ListItem(
                            headlineContent = { Text(c.name) },
                            modifier = Modifier.fillMaxWidth().clickable { selectedId = c.id },
                            trailingContent = {
                                IconButton(onClick = { vm.delete(c.id) }) {
                                    Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete))
                                }
                            },
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        } else {
            val colFlow = remember(sel) { vm.observeCollection(sel) }
            val col by colFlow.collectAsState(initial = null)
            Column(Modifier.padding(padding).fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selectedId = null }) { Text("‹ " + stringResource(R.string.back)) }
                    Text(col?.collection?.name ?: "", style = MaterialTheme.typography.titleMedium)
                }
                LazyColumn {
                    items(col?.games ?: emptyList(), key = { it.id }) { g ->
                        ListItem(
                            headlineContent = { Text(g.title) },
                            supportingContent = { Text(g.platform) },
                            trailingContent = {
                                IconButton(onClick = { vm.removeGames(sel, listOf(g.id)) }) {
                                    Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.remove_from_collection))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.new_collection)) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.collection_name_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.create(name); showCreate = false }) {
                    Text(stringResource(R.string.create))
                }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
