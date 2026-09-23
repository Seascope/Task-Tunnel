package com.example.tasktunnel.detector

import com.example.tasktunnel.accessibility.SanitizedNode
import java.util.Locale

enum class InstagramSurface {
    INSTAGRAM_MESSAGES,
    INSTAGRAM_EXPLORE,
    INSTAGRAM_REELS,
    INSTAGRAM_HOME,
    INSTAGRAM_PROFILE,
    INSTAGRAM_CREATE,
    INSTAGRAM_OTHER,
    UNKNOWN,
}

data class InstagramDetection(
    val surface: InstagramSurface,
    /** Evidence strength from 0.0 to 1.0; this is not a probability. */
    val confidence: Double,
    val strongestSignals: List<String>,
)

/** Deterministic, content-free classification over one bounded sanitized Instagram tree. */
object InstagramSurfaceDetector {
    const val INSTAGRAM_PACKAGE = "com.instagram.android"
    private const val MAX_SIGNALS = 6

    fun detect(packageName: String, nodes: List<SanitizedNode>): InstagramDetection {
        if (packageName != INSTAGRAM_PACKAGE) return unknown()

        val indexed = nodes.mapNotNull { node -> node.resourceId?.normalizedId()?.let { it to node } }
        fun present(id: String) = indexed.any { (candidate) -> candidate == id }
        fun active(
            id: String,
            visible: Boolean = false,
            selected: Boolean = false,
            clickable: Boolean = false,
            scrollable: Boolean = false,
            editable: Boolean = false,
        ) = indexed.any { (candidate, node) ->
            candidate == id &&
                (!visible || node.visibleToUser) &&
                (!selected || node.selected) &&
                (!clickable || node.clickable) &&
                (!scrollable || node.scrollable) &&
                (!editable || node.editable)
        }

        val feedSelected = active("feed_tab", visible = true, selected = true, clickable = true)
        val directSelected = active("direct_tab", visible = true, selected = true, clickable = true)
        val searchSelected = active("search_tab", visible = true, selected = true, clickable = true)
        val clipsSelected = active("clips_tab", visible = true, selected = true, clickable = true)
        val profileSelected = active("profile_tab", visible = true, selected = true, clickable = true)
        val createSelected = active("creation_tab", visible = true, selected = true, clickable = true)
        val createFlowSupportIds = setOf(
            "gallery_picker_grid_item_container",
            "gallery_picker_container",
            "media_picker_container",
            "creation_root",
            "creation_main_container",
        )
        val createFlowSupport = createFlowSupportIds.filter { id ->
            active(id, visible = true)
        }.sorted()

        val inboxStrong = directSelected &&
            active("inbox_refreshable_thread_list_recyclerview", visible = true, scrollable = true)
        val conversationStrong = present("thread_fragment_container") &&
            active("message_list", visible = true) &&
            active("message_composer_bar", visible = true) &&
            active("message_composer_reply_bar_container", visible = true) &&
            active("row_thread_composer_edittext", visible = true, clickable = true, editable = true)
        val messagesStrong = inboxStrong || conversationStrong
        val exploreStrong = searchSelected &&
            active("action_bar_search_edit_text", visible = true, clickable = true, editable = true) &&
            active("recycler_view", visible = true, scrollable = true)
        val reelSupportIds = setOf(
            "clips_viewer_view_pager",
            "clips_media_component",
            "clips_single_media_component",
            "clips_video_container",
            "clips_ufi_component",
            "clips_item_overlay_component",
        )
        val reelSupport = reelSupportIds.filter(::present).sorted()
        val reelsStrong = active("clips_expanded_touch_view", visible = true) && reelSupport.size >= 2
        val homeStrong = feedSelected

        val selectedSurfaces = buildSet {
            if (feedSelected) add(InstagramSurface.INSTAGRAM_HOME)
            if (directSelected) add(InstagramSurface.INSTAGRAM_MESSAGES)
            if (searchSelected) add(InstagramSurface.INSTAGRAM_EXPLORE)
            if (clipsSelected) add(InstagramSurface.INSTAGRAM_REELS)
            if (profileSelected) add(InstagramSurface.INSTAGRAM_PROFILE)
            if (createSelected) add(InstagramSurface.INSTAGRAM_CREATE)
        }
        val strongSurfaces = buildSet {
            if (messagesStrong) add(InstagramSurface.INSTAGRAM_MESSAGES)
            if (exploreStrong) add(InstagramSurface.INSTAGRAM_EXPLORE)
            if (reelsStrong) add(InstagramSurface.INSTAGRAM_REELS)
            if (homeStrong) add(InstagramSurface.INSTAGRAM_HOME)
        }
        // The creation picker can be presented over a still-selected main tab. Its dedicated
        // picker IDs are stronger evidence than stale selected-tab state underneath the modal.
        if (createFlowSupport.isNotEmpty()) return result(
            InstagramSurface.INSTAGRAM_CREATE,
            0.90,
            createFlowSupport.map { "id:$it" },
        )
        if (selectedSurfaces.size > 1 || strongSurfaces.size > 1) {
            return unknown(signals(indexed, reelSupport))
        }

        if (reelsStrong) return result(
            InstagramSurface.INSTAGRAM_REELS,
            if (clipsSelected) 0.98 else 0.95,
            listOf("active:clips_expanded_touch_view") +
                (if (clipsSelected) listOf("active:clips_tab") else emptyList()) +
                reelSupport.map { "id:$it" },
        )
        if (conversationStrong) return result(
            InstagramSurface.INSTAGRAM_MESSAGES,
            0.95,
            listOf(
                "id:thread_fragment_container", "active:message_list", "active:message_composer_bar",
                "active:message_composer_reply_bar_container", "active:row_thread_composer_edittext",
            ),
        )
        if (inboxStrong) return result(
            InstagramSurface.INSTAGRAM_MESSAGES,
            0.90,
            listOf("active:direct_tab", "active:inbox_refreshable_thread_list_recyclerview"),
        )
        if (exploreStrong) return result(
            InstagramSurface.INSTAGRAM_EXPLORE,
            0.90,
            listOf("active:search_tab", "active:action_bar_search_edit_text", "active:recycler_view"),
        )
        if (homeStrong) return result(InstagramSurface.INSTAGRAM_HOME, 0.85, listOf("active:feed_tab"))
        if (profileSelected) return result(InstagramSurface.INSTAGRAM_PROFILE, 0.80, listOf("active:profile_tab"))
        if (createSelected) return result(InstagramSurface.INSTAGRAM_CREATE, 0.80, listOf("active:creation_tab"))
        return unknown(signals(indexed, reelSupport))
    }

    private fun signals(indexed: List<Pair<String, SanitizedNode>>, reelSupport: List<String>) = buildList {
        indexed.filter { (_, node) -> node.visibleToUser && (node.selected || node.scrollable || node.editable) }
            .map { (id, _) -> "active:$id" }.distinct().sorted().forEach(::add)
        reelSupport.forEach { add("id:$it") }
    }.take(MAX_SIGNALS)

    private fun result(surface: InstagramSurface, confidence: Double, signals: List<String>) =
        InstagramDetection(surface, confidence, signals.distinct().take(MAX_SIGNALS))

    private fun unknown(signals: List<String> = emptyList()) =
        InstagramDetection(InstagramSurface.UNKNOWN, 0.0, signals.distinct().take(MAX_SIGNALS))

    private fun String.normalizedId() = substringAfterLast('/').lowercase(Locale.ROOT)
}
