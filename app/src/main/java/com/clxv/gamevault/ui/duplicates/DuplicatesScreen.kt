package com.clxv.gamevault.ui.duplicates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clxv.gamevault.R
import com.clxv.gamevault.data.local.entity.FileRecordEntity
import com.clxv.gamevault.data.repository.GameRepository
import com.clxv.gamevault.ui.components.formatBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DuplicatesViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    var groups by mutableStateOf<List<List<FileRecordEntity>>>(emptyList()); private set
    init { refresh() }
    fun refresh() = viewModelScope.launch { groups = repo.duplicates() }
    fun remove(gameIds: List<String>) = viewModelScope.launch { repo.removeFromLibrary(gameIds); refresh() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(onBack: () -> Unit, vm: DuplicatesViewModel = hiltViewModel()) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.duplicates)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        if (vm.groups.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_duplicates), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.padding(padding), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(vm.groups, key = { it.first().quickHash }) { group ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(R.string.duplicate_group, formatBytes(group.first().size), group.size),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            group.forEach { f ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(f.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        Text(f.displayPath, style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                    IconButton(onClick = { vm.remove(listOf(f.gameId)) }) {
                                        Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.remove))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
