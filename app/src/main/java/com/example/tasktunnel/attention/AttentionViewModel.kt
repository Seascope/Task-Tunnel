package com.example.tasktunnel.attention

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.tasktunnel.usage.SurfaceUsageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
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
    private val database = AttentionDatabase.getInstance(application)
    private val repository = AttentionEventRepository(database.attentionEventDao())
    private val surfaceUsageRepository = SurfaceUsageRepository(database.surfaceUsageSegmentDao())
    private val historyAvailable = MutableStateFlow(true)
    private val analysisWindowStartMillis = System.currentTimeMillis() - ANALYSIS_QUERY_WINDOW_MILLIS

    val uiState = combine(
        repository.observeRecent().catch {
            historyAvailable.value = false
            emit(emptyList())
        },
        repository.observeSince(analysisWindowStartMillis).catch {
            historyAvailable.value = false
            emit(emptyList())
        },
        timeRefreshes(),
        historyAvailable,
    ) { recentEvents, analysisEvents, nowMillis, available ->
        AttentionUiState(
            episodes = AttentionEpisodeGrouper.group(recentEvents),
            metrics = AttentionMetrics.from(analysisEvents, nowMillis),
            dailyRecap = DailyAttentionRecap.from(analysisEvents, nowMillis),
            review = AttentionReview.from(analysisEvents, nowMillis),
            sevenDayReview = SevenDayReview.from(analysisEvents, nowMillis),
            historyAvailable = available,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttentionUiState())

    fun clearHistory() {
        viewModelScope.launch {
            runCatching {
                LocalHistoryWriteGate.runExclusive {
                    database.withTransaction {
                        repository.clear()
                        surfaceUsageRepository.clear()
                    }
                }
            }
                .onSuccess { historyAvailable.value = true }
                .onFailure { historyAvailable.value = false }
        }
    }

    private fun timeRefreshes(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TIME_REFRESH_MILLIS)
        }
    }

    companion object {
        private const val TIME_REFRESH_MILLIS = 60_000L
        // Review patterns use a 30-calendar-day window. Query one extra day so local-midnight and
        // daylight-saving boundaries cannot trim valid evidence before the pure analyzers filter it.
        private const val ANALYSIS_QUERY_WINDOW_MILLIS = 31L * 24 * 60 * 60 * 1_000
    }
}
