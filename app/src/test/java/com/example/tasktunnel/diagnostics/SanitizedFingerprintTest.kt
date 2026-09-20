package com.example.tasktunnel.diagnostics

import com.example.tasktunnel.accessibility.SanitizedNode
import com.example.tasktunnel.accessibility.TreeSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SanitizedFingerprintTest {
    @Test fun instagramMetadataIsExplicitAndContentFree() {
        val output = SanitizedFingerprint.format(
            snapshot = TreeSnapshot(
                packageName = "com.instagram.android",
                capturedAtMillis = 123L,
                nodes = listOf(
                    SanitizedNode(0, "android.view.ViewGroup", "com.instagram.android:id/ROOT", 1, false, true, false, true, true),
                    SanitizedNode(1, "android.widget.Button", "com.instagram.android:id/ITEM", 0, true, false, false, true, true, selected = true, parentIndex = 0),
                ),
                truncated = false,
            ),
            appLabel = "Instagram",
            classification = "UNCLASSIFIED",
            confidence = "n/a",
        )

        assertTrue(output.startsWith("Task Tunnel sanitized Instagram fingerprint"))
        assertTrue(output.contains("source=com.instagram.android"))
        assertTrue(output.contains("classification=UNCLASSIFIED confidence=n/a"))
        assertTrue(output.contains("ids(2)=item:1 | root:1"))
        assertTrue(output.contains("id:root@d0/ch1[v,s] -> id:item@d1/ch0[v,c,selected]"))
        assertTrue(output.length <= 14_000)
        assertFalse(output.contains("text=", ignoreCase = true))
        assertFalse(output.contains("contentDescription", ignoreCase = true))
    }
}
