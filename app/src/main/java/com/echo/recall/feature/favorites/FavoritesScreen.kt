package com.echo.recall.feature.favorites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echo.recall.R
import com.echo.recall.core.designsystem.component.EchoEmptyState
import com.echo.recall.core.designsystem.component.ScreenHeader
import com.echo.recall.core.designsystem.component.SectionLabel
import com.echo.recall.feature.memory.MemoryCard

@Composable
fun FavoritesScreen(
    onOpenMemory: (String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.dock_favorites),
            subtitle = if (favorites.isEmpty()) null else "收藏的记忆永久保留",
        )
        if (favorites.isEmpty()) {
            EchoEmptyState(
                icon = Icons.Rounded.Star,
                text = stringResource(R.string.empty_favorites),
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn {
                item { SectionLabel("全部收藏") }
                items(favorites, key = { it.id }) { memory ->
                    MemoryCard(
                        memory = memory,
                        onClick = { onOpenMemory(memory.id) },
                        onToggleFavorite = { viewModel.toggleFavorite(memory) },
                    )
                }
            }
        }
    }
}
