package com.example.tasktunnel.detector

import com.example.tasktunnel.accessibility.SanitizedNode
import java.util.Locale

enum class TikTokSurface {
    TIKTOK_FEED,
    TIKTOK_FRIENDS,
    TIKTOK_SEARCH,
    TIKTOK_INBOX,
    TIKTOK_PROFILE,
    TIKTOK_OTHER,
    UNKNOWN,
}

data class TikTokDetection(
    val surface: TikTokSurface,
    val confidence: Double,
    val strongestSignals: List<String>,
)

/** Deterministic, content-free TikTok classification for version 47.0.3 evidence. */
object TikTokSurfaceDetector {
    const val TIKTOK_PACKAGE = "com.zhiliaoapp.musically"
    private const val NAVIGATION_ID = "omy"
    private const val MAX_SIGNALS = 6

    fun detect(packageName: String, nodes: List<SanitizedNode>): TikTokDetection {
        if (packageName != TIKTOK_PACKAGE) return unknown()
        val indexed = nodes.mapIndexedNotNull { index, node ->
            node.resourceId?.normalizedId()?.let { it to (index to node) }
        }
        fun present(id: String) = indexed.any { (candidate) -> candidate == id }
        fun active(id: String, selected: Boolean = false, editable: Boolean = false, scrollable: Boolean = false) =
            indexed.any { (candidate, indexedNode) ->
                candidate == id && indexedNode.second.visibleToUser &&
                    (!selected || indexedNode.second.selected) &&
                    (!editable || indexedNode.second.editable) &&
                    (!scrollable || indexedNode.second.scrollable)
            }
        fun selectedNav(id: String): Boolean = indexed.any { (candidate, indexedNode) ->
            candidate == id && indexedNode.second.visibleToUser && indexedNode.second.selected && indexedNode.second.clickable
        }

        val selected = listOf(
            "inbox" to selectedNav("omr"),
            "profile" to selectedNav("oms"),
            "friends" to selectedNav("omp"),
            "feed" to selectedNav("omq"),
        ).filter { it.second }.map { it.first }
        if (selected.size > 1) return unknown(selected.map { "conflict:selected:$it" })

        // Inbox/Profile expose their own search-like controls in TikTok 47.x. Those controls are
        // not the global Search destination and must not let a Search/Watch tunnel bypass the
        // Profile guard (saved videos are reachable there). Our directed global Search route
        // deliberately returns to For You first, so a selected Inbox/Profile tab is stronger
        // evidence than generic search widgets on those screens.
        when (selected.singleOrNull()) {
            "inbox" -> return result(TikTokSurface.TIKTOK_INBOX, 0.95, listOf("active:selected:omr", "navigation:$NAVIGATION_ID"))
            "profile" -> return result(TikTokSurface.TIKTOK_PROFILE, 0.95, listOf("active:selected:oms", "navigation:$NAVIGATION_ID"))
        }

        val searchEntry = active("hu0", editable = true)
        val searchResults = active("viewpager_search") ||
            (active("hu0", editable = true) && (active("pzk") || active("nhr")))
        val searchVideo = active("tv_bar_search") || active("tv_search_sug_word") ||
            (active("o8c") && (active("viewpager_search") || active("hu0", editable = true)))
        // Search-origin evidence wins before Feed/Friends because result videos can retain the
        // previously selected source tab while their content is already in global Search.
        if (searchEntry || searchResults || searchVideo) {
            return result(
                TikTokSurface.TIKTOK_SEARCH,
                if (searchVideo || searchResults) 0.95 else 0.92,
                buildList {
                    if (active("hu0", editable = true)) add("active:hu0(editable)")
                    if (active("tv_search_textview")) add("active:tv_search_textview")
                    if (active("lkh", scrollable = true)) add("active:lkh(scrollable)")
                    if (active("viewpager_search")) add("active:viewpager_search")
                    if (active("pzk")) add("active:pzk")
                    if (active("nhr")) add("active:nhr")
                    if (active("tv_bar_search")) add("active:tv_bar_search")
                    if (active("tv_search_sug_word")) add("active:tv_search_sug_word")
                    if (active("o8c")) add("active:o8c")
                },
            )
        }

        return when (selected.singleOrNull()) {
            "friends" -> result(TikTokSurface.TIKTOK_FRIENDS, 0.95, listOf("active:selected:omp", "navigation:$NAVIGATION_ID"))
            "feed" -> result(TikTokSurface.TIKTOK_FEED, 0.95, listOf("active:selected:omq", "navigation:$NAVIGATION_ID"))
            null -> if (active(NAVIGATION_ID)) result(TikTokSurface.TIKTOK_OTHER, 0.60, listOf("active:$NAVIGATION_ID")) else unknown()
            else -> unknown()
        }
    }

    private fun String.normalizedId() = substringAfterLast('/').lowercase(Locale.ROOT)

    private fun result(surface: TikTokSurface, confidence: Double, signals: List<String>) =
        TikTokDetection(surface, confidence, signals.distinct().take(MAX_SIGNALS))

    private fun unknown(signals: List<String> = emptyList()) =
        TikTokDetection(TikTokSurface.UNKNOWN, 0.0, signals.distinct().take(MAX_SIGNALS))
}