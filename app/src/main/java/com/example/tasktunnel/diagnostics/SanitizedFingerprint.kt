package com.example.tasktunnel.diagnostics

import com.example.tasktunnel.accessibility.SanitizedNode
import com.example.tasktunnel.accessibility.TreeSnapshot
import java.util.Locale

/** A deterministic, bounded, content-free summary of one sanitized accessibility tree. */
object SanitizedFingerprint {
    private const val MAX_IDS = 80
    private const val MAX_CLASSES = 30
    private const val MAX_SELECTED = 20
    private const val MAX_RELATIONSHIPS = 40
    private const val MAX_CONTAINERS = 30
    private const val MAX_SECTION_CHARS = 2_200
    private const val MAX_OUTPUT_CHARS = 14_000

    fun format(
        snapshot: TreeSnapshot,
        appLabel: String,
        classification: String,
        confidence: String,
        classificationLabel: String = "classification",
    ): String {
        val nodes = snapshot.nodes
        val idCounts = nodes.mapNotNull { it.resourceId.normalizedId() }.groupingBy { it }.eachCount()
        val classCounts = nodes.map { it.className.simpleClass() }.groupingBy { it }.eachCount()
        val depthCounts = nodes.groupingBy { it.depth }.eachCount().toSortedMap()
        val selected = nodes.filter { it.selected }.map { it.descriptor() }.distinct().sorted()
        val relationships = nodes.mapIndexedNotNull { index, node ->
            val parent = node.parentIndex?.takeIf { it in 0 until index }?.let(nodes::get) ?: return@mapIndexedNotNull null
            "${parent.descriptor()} -> ${node.descriptor()}"
        }.distinct().sorted()
        val containers = nodes.filter { it.scrollable || (it.clickable && it.childCount > 0) }
            .map { it.descriptor() }.distinct().sorted()

        val lines = mutableListOf(
            "Task Tunnel sanitized $appLabel fingerprint",
            "source=${snapshot.packageName}",
            "capturedAtMillis=${snapshot.capturedAtMillis}",
            "$classificationLabel=$classification confidence=$confidence",
            "nodes=${nodes.size} sourceLimit=200 truncated=${snapshot.truncated} maxDepth=${nodes.maxOfOrNull { it.depth } ?: 0}",
            "flags visible=${nodes.count { it.visibleToUser }} clickable=${nodes.count { it.clickable }} scrollable=${nodes.count { it.scrollable }} editable=${nodes.count { it.editable }} selected=${nodes.count { it.selected }}",
            "combinations visible+clickable=${nodes.count { it.visibleToUser && it.clickable }} visible+scrollable=${nodes.count { it.visibleToUser && it.scrollable }} visible+editable=${nodes.count { it.visibleToUser && it.editable }} selected+clickable=${nodes.count { it.selected && it.clickable }}",
            "depths=${depthCounts.entries.joinToString(",") { "${it.key}:${it.value}" }}",
        )
        lines.addSection("ids", idCounts.entries.sortedBy { it.key }.map { "${it.key}:${it.value}" }, MAX_IDS)
        lines.addSection("classes", classCounts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { "${it.key}:${it.value}" }, MAX_CLASSES)
        lines.addSection("selected", selected, MAX_SELECTED)
        lines.addSection("relationships", relationships, MAX_RELATIONSHIPS)
        lines.addSection("containers", containers, MAX_CONTAINERS)
        val output = lines.joinToString("\n")
        return if (output.length <= MAX_OUTPUT_CHARS) output else output.take(MAX_OUTPUT_CHARS - 18) + "\n[output truncated]"
    }

    private fun MutableList<String>.addSection(name: String, values: List<String>, limit: Int) {
        val shown = mutableListOf<String>()
        var usedChars = 0
        for (value in values.take(limit)) {
            val addedChars = value.length + if (shown.isEmpty()) 0 else 3
            if (usedChars + addedChars > MAX_SECTION_CHARS) break
            shown += value
            usedChars += addedChars
        }
        add("$name(${values.size})=${if (shown.isEmpty()) "none" else shown.joinToString(" | ")}")
        if (values.size > shown.size) add("$name omitted=${values.size - shown.size}")
    }

    private fun String?.normalizedId(): String? = this?.substringAfterLast('/')
        ?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }?.take(200)

    private fun String?.simpleClass(): String = this?.substringAfterLast('.')
        ?.takeIf { it.isNotBlank() }?.take(80) ?: "unknown"

    private fun SanitizedNode.descriptor(): String {
        val identity = resourceId.normalizedId()?.let { "id:$it" } ?: "class:${className.simpleClass()}"
        val flags = buildList {
            if (visibleToUser) add("v")
            if (clickable) add("c")
            if (scrollable) add("s")
            if (editable) add("e")
            if (selected) add("selected")
        }.joinToString(",")
        return "$identity@d$depth/ch$childCount${if (flags.isEmpty()) "" else "[$flags]"}"
    }
}
