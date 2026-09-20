package com.example.tasktunnel.attention

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AttentionUiState(
    val episodes: List<AttentionEpisode> = emptyList(),
    val metrics: AttentionMetrics = AttentionMetrics(0),
)

class AttentionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AttentionHistory.repository(application)
    private val sevenDaysAgoMillis = System.currentTimeMillis() - SEVEN_DAYS_MILLIS

    val uiState = combine(
        repository.observeRecent(),
        repository.observeDriftEpisodesSince(sevenDaysAgoMillis),
    ) { events, driftCount ->
        AttentionUiState(
            episodes = AttentionEpisodeGrouper.group(events),
            metrics = AttentionMetrics(driftCount),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttentionUiState())

    fun clearHistory() {
        viewModelScope.launch { repository.clear() }
    }

    companion object {
        private const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1_000
    }
}
