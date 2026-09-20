package com.example.tasktunnel.diagnostics

import com.example.tasktunnel.accessibility.SanitizedNode
import com.example.tasktunnel.accessibility.TreeSnapshot
import com.example.tasktunnel.detector.YouTubeDetection
import com.example.tasktunnel.detector.YouTubeSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeFingerprintTest {
    @Test fun normalizesAndCountsDistinctIds() {
        val output = format(listOf(node(id = "com.google.android.youtube:id/PIVOT_BAR"), node(id = "pivot_bar")))
        assertTrue(output.contains("ids(1)=pivot_bar:2"))
    }

    @Test fun reportsClassDepthFlagsSelectionAndRelationships() {
        val output = format(listOf(
            node(depth = 0, className = "android.view.ViewGroup", id = "root", childCount = 1, scrollable = true),
            node(depth = 1, className = "android.widget.Button", id = "item", clickable = true, selected = true, parentIndex = 0),
        ))
        assertTrue(output.contains("classes(2)=Button:1 | ViewGroup:1"))
        assertTrue(output.contains("depths=0:1,1:1"))
        assertTrue(output.contains("selected(1)=id:item@d1/ch0[c,selected]"))
        assertTrue(output.contains("id:root@d0/ch1[s] -> id:item@d1/ch0[c,selected]"))
    }

    @Test fun outputIsDeterministicAndBoundedWithExplicitOmissions() {
        val nodes = (0 until 200).map { index -> node(id = "id_$index", className = "type.Class$index") }
        val first = format(nodes, truncated = true)
        assertEquals(first, format(nodes, truncated = true))
        assertTrue(first.length <= 14_000)
        assertTrue(first.contains("truncated=true"))
        assertTrue(first.contains("ids omitted=120"))
        assertTrue(first.contains("classes omitted=170"))
    }

    @Test fun fingerprintHasNoContentFields() {
        val output = format(listOf(node(id = "safe_id")))
        assertFalse(output.contains("text=", ignoreCase = true))
        assertFalse(output.contains("contentDescription", ignoreCase = true))
    }

    private fun format(nodes: List<SanitizedNode>, truncated: Boolean = false) = YouTubeFingerprint.format(
        TreeSnapshot("com.google.android.youtube", 123L, nodes, truncated),
        YouTubeDetection(YouTubeSurface.UNKNOWN, 0.0, emptyList()),
    )

    private fun node(
        depth: Int = 0,
        className: String = "android.view.View",
        id: String? = null,
        childCount: Int = 0,
        clickable: Boolean = false,
        scrollable: Boolean = false,
        selected: Boolean = false,
        parentIndex: Int? = null,
    ) = SanitizedNode(
        depth, className, id, childCount, clickable, scrollable, false, true, false, selected, parentIndex,
    )
}
