package com.echo.recall.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recall.core.data.MemoryRepository
import com.echo.recall.core.data.db.MemoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: MemoryRepository,
) : ViewModel() {

    val favorites: StateFlow<List<MemoryEntity>> = repository.observeFavorites().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun toggleFavorite(memory: MemoryEntity) {
        viewModelScope.launch { repository.setFavorited(memory.id, !memory.favorited) }
    }
}
