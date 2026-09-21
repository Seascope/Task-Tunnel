package com.example.tasktunnel.attention

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AttentionUiState(
    val episodes: List<AttentionEpisode> = emptyList(),
    val metrics: AttentionMetrics = AttentionMetrics(0),
    val dailyRecap: DailyAttentionRecap = DailyAttentionRecap(),
    val review: AttentionReview = AttentionReview(DailyAttentionRecap(), emptyList(), null),
    val sevenDayReview: SevenDayReview = SevenDayReview(ReviewPeriodSummary(0, 0, 0, 0, 0, 0), emptyList(), emptyList(), emptyList(), false),
    val historyAvailable: Boolean = true,
)

class AttentionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AttentionHistory.repository(application)
    private val sevenDaysAgoMillis = System.currentTimeMillis() - SEVEN_DAYS_MILLIS
    private val historyAvailable = MutableStateFlow(true)

    val uiState = combine(
        repository.observeRecent().catch {
            historyAvailable.value = false
            emit(emptyList())
        },
        repository.observeDriftEpisodesSince(sevenDaysAgoMillis).catch {
            historyAvailable.value = false
            emit(0)
        },
        historyAvailable,
    ) { events, driftCount, available ->
        val nowMillis = System.currentTimeMillis()
        AttentionUiState(
            episodes = AttentionEpisodeGrouper.group(events),
            metrics = AttentionMetrics(driftCount),
            dailyRecap = DailyAttentionRecap.from(events, nowMillis),
            review = AttentionReview.from(events, nowMillis),
            sevenDayReview = SevenDayReview.from(events, nowMillis),
            historyAvailable = available,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttentionUiState())

    fun clearHistory() {
        viewModelScope.launch {
            runCatching { repository.clear() }
                .onSuccess { historyAvailable.value = true }
                .onFailure { historyAvailable.value = false }
        }
    }

    companion object {
        private const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1_000
    }
}
