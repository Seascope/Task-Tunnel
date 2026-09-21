package com.example.tasktunnel.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tasktunnel.attention.AttentionApp
import com.example.tasktunnel.attention.AttentionDatabase
import com.example.tasktunnel.tunnel.DetectedSurface
import com.example.tasktunnel.usage.SurfaceUsageRepository
import com.example.tasktunnel.usage.SurfaceUsageSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.util.TimeZone

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SurfaceUsageRepository(
        AttentionDatabase.getInstance(application).surfaceUsageSegmentDao(),
    )
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val uiState = merge(
        repository.observeRecent().map { RefreshSignal.DataChanged }.catch { emit(RefreshSignal.Unavailable) },
        refreshRequests.map { RefreshSignal.DataChanged }.onStart { emit(RefreshSignal.DataChanged) },
        midnightRefreshes().map { RefreshSignal.DataChanged },
    ).map { signal ->
        when (signal) {
            RefreshSignal.Unavailable -> HomeUiState.Unavailable
            RefreshSignal.DataChanged -> runCatching { HomeUiState.Loaded(loadToday()) }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    HomeUiState.Unavailable
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** Call from the host's resume lifecycle callback to refresh Today after the app returns. */
    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    private suspend fun loadToday(): HomePresentation {
        val (startMillis, endMillis) = todayRange(System.currentTimeMillis())
        val apps = HOME_APPS.map { app ->
            repository.summary(app, startMillis, endMillis).toHomeAppUsage()
        }.filter { it.totalTrackedDurationMillis > 0L }
        return HomePresentation(
            totalTrackedTodayMillis = apps.sumOf(HomeAppUsage::totalTrackedDurationMillis),
            apps = apps,
        )
    }

    private fun SurfaceUsageSummary.toHomeAppUsage(): HomeAppUsage {
        val rows = surfaceDurations()
            .map { (kind, durationMillis) -> HomeSurfaceUsage(kind, durationMillis) }
            .filter { it.durationMillis > 0L }
        return HomeAppUsage(
            app = app,
            totalTrackedDurationMillis = totalTrackedDurationMillis,
            surfaceRows = rows,
            coverage = coverage,
            shouldDeemphasizeComposition = coverage < MIN_COMPOSITION_COVERAGE,
            showMeaningfulUnclassifiedNote = unclassifiedDurationMillis >= MIN_MEANINGFUL_UNCLASSIFIED_MILLIS &&
                unclassifiedDurationMillis.toDouble() / totalTrackedDurationMillis >= MIN_UNCLASSIFIED_SHARE,
        )
    }

    private fun SurfaceUsageSummary.surfaceDurations(): List<Pair<HomeSurfaceKind, Long>> = when (app) {
        AttentionApp.INSTAGRAM -> listOf(
            HomeSurfaceKind.REELS to durationOf(DetectedSurface.INSTAGRAM_REELS),
            HomeSurfaceKind.MESSAGES to durationOf(DetectedSurface.INSTAGRAM_MESSAGES),
            HomeSurfaceKind.EXPLORE to durationOf(DetectedSurface.INSTAGRAM_EXPLORE),
            HomeSurfaceKind.OTHER to (durationOf(DetectedSurface.INSTAGRAM_HOME) + durationOf(DetectedSurface.INSTAGRAM_OTHER)),
            HomeSurfaceKind.UNCLASSIFIED to unclassifiedDurationMillis,
        )
        AttentionApp.YOUTUBE -> listOf(
            HomeSurfaceKind.VIDEO to durationOf(DetectedSurface.YOUTUBE_VIDEO),
            HomeSurfaceKind.SHORTS to durationOf(DetectedSurface.YOUTUBE_SHORTS),
            HomeSurfaceKind.SEARCH to durationOf(DetectedSurface.YOUTUBE_SEARCH),
            HomeSurfaceKind.OTHER to durationOf(DetectedSurface.YOUTUBE_OTHER),
            HomeSurfaceKind.UNCLASSIFIED to unclassifiedDurationMillis,
        )
        AttentionApp.REDDIT -> emptyList()
    }

    private fun SurfaceUsageSummary.durationOf(surface: DetectedSurface): Long = bySurface[surface] ?: 0L

    private fun midnightRefreshes(): Flow<Unit> = flow {
        while (true) {
            val (_, nextMidnightMillis) = todayRange(System.currentTimeMillis())
            delay((nextMidnightMillis - System.currentTimeMillis()).coerceAtLeast(1L))
            emit(Unit)
        }
    }

    private fun todayRange(nowMillis: Long): Pair<Long, Long> {
        val day = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMillis = day.timeInMillis
        val endMillis = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        return startMillis to endMillis
    }

    private enum class RefreshSignal { DataChanged, Unavailable }

    private companion object {
        val HOME_APPS = listOf(AttentionApp.INSTAGRAM, AttentionApp.YOUTUBE)
        const val MIN_COMPOSITION_COVERAGE = 0.5
        const val MIN_UNCLASSIFIED_SHARE = 0.1
        const val MIN_MEANINGFUL_UNCLASSIFIED_MILLIS = 60_000L
    }
}
