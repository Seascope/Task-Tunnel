package com.example.tasktunnel.detector

import com.example.tasktunnel.accessibility.SanitizedNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class InstagramSurfaceDetectorTest {
    @Test fun homeRequiresActiveFeedAndIgnoresInactiveIds() {
        assertDetection(
            InstagramSurface.INSTAGRAM_HOME, 0.85,
            activeTab("feed_tab"), node("direct_tab"), node("profile_tab"), node("clips_media_component"),
        )
    }

    @Test fun inboxRequiresActiveDirectAndVisibleScrollableList() {
        assertDetection(
            InstagramSurface.INSTAGRAM_MESSAGES, 0.90,
            activeTab("direct_tab"), node("inbox_refreshable_thread_list_recyclerview", visible = true, scrollable = true),
        )
    }

    @Test fun fullConversationIsMessages() {
        assertDetection(
            InstagramSurface.INSTAGRAM_MESSAGES, 0.95,
            node("thread_fragment_container"),
            node("message_list", visible = true, scrollable = true),
            node("message_composer_bar", visible = true),
            node("message_composer_reply_bar_container", visible = true),
            node("row_thread_composer_edittext", visible = true, clickable = true, editable = true),
        )
    }

    @Test fun fullConversationWithNonScrollableMessageListIsMessages() {
        assertDetection(
            InstagramSurface.INSTAGRAM_MESSAGES, 0.95,
            node("thread_fragment_container"),
            node("message_list", visible = true),
            node("message_composer_bar", visible = true),
            node("message_composer_reply_bar_container", visible = true),
            node("row_thread_composer_edittext", visible = true, clickable = true, editable = true),
        )
    }

    @Test fun messageListAloneIsUnknown() = assertUnknown(
        node("message_list", visible = true),
    )

    @Test fun composerEditTextAloneIsUnknown() = assertUnknown(
        node("row_thread_composer_edittext", visible = true, clickable = true, editable = true),
    )

    @Test fun partialConversationStructureIsUnknown() = assertUnknown(
        node("thread_fragment_container"),
        node("message_list", visible = true),
        node("message_composer_bar", visible = true),
    )

    @Test fun exploreRequiresFullActiveCombo() {
        assertDetection(
            InstagramSurface.INSTAGRAM_EXPLORE, 0.90,
            activeTab("search_tab"),
            node("action_bar_search_edit_text", visible = true, clickable = true, editable = true),
            node("recycler_view", visible = true, scrollable = true),
            node("grid_card_layout_container"),
        )
    }

    @Test fun reelsWithSelectedTabHasHigherEvidenceStrength() {
        val detection = detect(
            activeTab("clips_tab"), node("clips_expanded_touch_view", visible = true),
            node("clips_media_component"), node("clips_video_container"),
        )
        assertEquals(InstagramSurface.INSTAGRAM_REELS, detection.surface)
        assertEquals(0.98, detection.confidence, 0.0)
        assertEquals("active:clips_expanded_touch_view", detection.strongestSignals[0])
        assertEquals("active:clips_tab", detection.strongestSignals[1])
    }

    @Test fun reelsOpenedFromExploreDoesNotRequireSelectedClipsTab() {
        assertDetection(
            InstagramSurface.INSTAGRAM_REELS, 0.95,
            node("clips_expanded_touch_view", visible = true),
            node("clips_viewer_view_pager"), node("clips_single_media_component"),
        )
    }

    @Test fun profileWithStaleClipIdsIsProfileNotReels() {
        assertDetection(
            InstagramSurface.INSTAGRAM_PROFILE, 0.80,
            activeTab("profile_tab"), node("clips_media_component"), node("clips_video_container"),
            node("clips_viewer_view_pager"), node("clips_single_media_component"),
        )
    }


    @Test fun selectedCreationTabIsCreate() {
        assertDetection(
            InstagramSurface.INSTAGRAM_CREATE, 0.80,
            activeTab("creation_tab"),
        )
    }

    @Test fun visibleGalleryPickerIsCreateEvenWithoutSelectedCreationTab() {
        assertDetection(
            InstagramSurface.INSTAGRAM_CREATE, 0.90,
            node("gallery_picker_grid_item_container", visible = true),
        )
    }

    @Test fun visibleGalleryPickerOverridesStaleSelectedTabUnderModal() {
        assertDetection(
            InstagramSurface.INSTAGRAM_CREATE, 0.90,
            activeTab("feed_tab"),
            node("gallery_picker_grid_item_container", visible = true),
        )
    }

    @Test fun invisibleGalleryPickerDoesNotOverrideCurrentSurface() {
        assertDetection(
            InstagramSurface.INSTAGRAM_HOME, 0.85,
            activeTab("feed_tab"),
            node("gallery_picker_grid_item_container"),
        )
    }

    @Test fun invisibleCreateSupportAloneIsUnknown() = assertUnknown(
        node("creation_root"),
        node("media_picker_container"),
    )

    @Test fun clipsVideoAloneIsUnknown() = assertUnknown(node("clips_video_container"))

    @Test fun clipsViewPagerAloneIsUnknown() = assertUnknown(node("clips_viewer_view_pager"))

    @Test fun clipsTabAloneIsUnknown() = assertUnknown(activeTab("clips_tab"))

    @Test fun invisibleInboxListIsNotMessages() = assertUnknown(
        activeTab("direct_tab"), node("inbox_refreshable_thread_list_recyclerview", scrollable = true),
    )

    @Test fun inactiveSearchIdsAreNotExplore() = assertUnknown(
        node("search_tab"), node("action_bar_search_edit_text", editable = true),
        node("recycler_view", scrollable = true),
    )

    @Test fun conflictingStrongEvidenceFailsOpen() = assertUnknown(
        activeTab("direct_tab"),
        node("inbox_refreshable_thread_list_recyclerview", visible = true, scrollable = true),
        node("clips_expanded_touch_view", visible = true),
        node("clips_media_component"), node("clips_video_container"),
    )

    @Test fun exactIdSegmentDoesNotSubstringMatch() = assertUnknown(
        node("prefix_feed_tab_suffix", visible = true, clickable = true, selected = true),
    )

    @Test fun nonInstagramPackageIsUnknownAtZeroConfidence() {
        val detection = InstagramSurfaceDetector.detect("com.example.other", listOf(activeTab("feed_tab")))
        assertEquals(InstagramSurface.UNKNOWN, detection.surface)
        assertEquals(0.0, detection.confidence, 0.0)
        assertEquals(emptyList<String>(), detection.strongestSignals)
    }

    @Test fun twoSelectedNavigationTabsAreContradictory() = assertUnknown(
        activeTab("feed_tab"), activeTab("profile_tab"),
    )

    @Test fun reelSupportWithoutVisibleExpandedTouchIsUnknown() = assertUnknown(
        node("clips_expanded_touch_view"), node("clips_media_component"), node("clips_video_container"),
    )

    private fun assertUnknown(vararg nodes: SanitizedNode) {
        val detection = detect(*nodes)
        assertEquals(InstagramSurface.UNKNOWN, detection.surface)
        assertEquals(0.0, detection.confidence, 0.0)
        assertNotEquals(InstagramSurface.INSTAGRAM_REELS, detection.surface)
    }

    private fun assertDetection(surface: InstagramSurface, confidence: Double, vararg nodes: SanitizedNode) {
        val detection = detect(*nodes)
        assertEquals(surface, detection.surface)
        assertEquals(confidence, detection.confidence, 0.0)
        if (surface != InstagramSurface.UNKNOWN) assertNotEquals(emptyList<String>(), detection.strongestSignals)
    }

    private fun detect(vararg nodes: SanitizedNode) =
        InstagramSurfaceDetector.detect(InstagramSurfaceDetector.INSTAGRAM_PACKAGE, nodes.toList())

    private fun activeTab(id: String) = node(id, visible = true, clickable = true, selected = true)

    private fun node(
        id: String,
        visible: Boolean = false,
        clickable: Boolean = false,
        scrollable: Boolean = false,
        editable: Boolean = false,
        selected: Boolean = false,
    ) = SanitizedNode(
        depth = 0,
        className = "android.view.View",
        resourceId = "com.instagram.android:id/$id",
        childCount = 0,
        clickable = clickable,
        scrollable = scrollable,
        editable = editable,
        enabled = true,
        visibleToUser = visible,
        selected = selected,
    )
}
