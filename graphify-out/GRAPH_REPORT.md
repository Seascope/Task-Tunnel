# Graph Report - android-saas  (2026-09-20)

## Corpus Check
- Corpus is ~11,393 words - fits in a single context window. You may not need a graph.

## Summary
- 267 nodes · 485 edges · 24 communities (16 shown, 8 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 5 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- TaskTunnelAccessibilityService.kt
- MainActivity.kt
- SanitizedNode
- InstagramSurfaceDetectorTest
- YouTubeSurfaceDetectorTest
- Task Tunnel — Agent Operating Policy
- TreeSnapshot
- .detect
- AccessibilityRuntime.kt
- DeveloperScreen
- Theme.kt
- .format
- ExampleInstrumentedTest.kt
- .onCreate
- Type.kt
- M0 Manual Test Plan
- M1 YouTube surface detection — physical phone matrix
- M2A Instagram diagnostic evidence collection
- Current State
- MANUAL_TEST_M2B.md

## God Nodes (most connected - your core abstractions)
1. `InstagramSurfaceDetectorTest` - 27 edges
2. `SanitizedNode` - 20 edges
3. `TaskTunnelAccessibilityService` - 19 edges
4. `YouTubeSurfaceDetectorTest` - 15 edges
5. `Task Tunnel — Agent Operating Policy` - 13 edges
6. `TreeSnapshot` - 10 edges
7. `AccessibilityRuntime` - 9 edges
8. `InstagramSurface` - 9 edges
9. `YouTubeSurface` - 9 edges
10. `YouTubeDetection` - 9 edges

## Surprising Connections (you probably didn't know these)
- `NodeRow()` --references--> `SanitizedNode`  [EXTRACTED]
  app/src/main/java/com/example/tasktunnel/MainActivity.kt → app/src/main/java/com/example/tasktunnel/accessibility/AccessibilityRuntime.kt
- `DetectionCard()` --references--> `ObservedYouTubeDetection`  [EXTRACTED]
  app/src/main/java/com/example/tasktunnel/MainActivity.kt → app/src/main/java/com/example/tasktunnel/accessibility/AccessibilityRuntime.kt
- `InstagramDiagnosticCard()` --references--> `ObservedInstagramDetection`  [EXTRACTED]
  app/src/main/java/com/example/tasktunnel/MainActivity.kt → app/src/main/java/com/example/tasktunnel/accessibility/AccessibilityRuntime.kt
- `DeveloperScreen()` --references--> `AccessibilityState`  [EXTRACTED]
  app/src/main/java/com/example/tasktunnel/MainActivity.kt → app/src/main/java/com/example/tasktunnel/accessibility/AccessibilityRuntime.kt
- `InspectorScreen()` --references--> `AccessibilityState`  [EXTRACTED]
  app/src/main/java/com/example/tasktunnel/MainActivity.kt → app/src/main/java/com/example/tasktunnel/accessibility/AccessibilityRuntime.kt

## Import Cycles
- None detected.

## Communities (24 total, 8 thin omitted)

### Community 0 - "TaskTunnelAccessibilityService.kt"
Cohesion: 0.10
Nodes (17): AccessibilityEvent, AccessibilityNodeInfo, AccessibilityService, AccessibilityRuntime, TaskTunnelAccessibilityService, arraydeque, buildconfig, button (+9 more)

### Community 1 - "MainActivity.kt"
Cohesion: 0.07
Nodes (29): accessibilitymanager, accessibilityserviceinfo, alignment, arrangement, card, clipboardmanager, clipdata, collectasstate (+21 more)

### Community 2 - "SanitizedNode"
Cohesion: 0.12
Nodes (16): SanitizedNode, InstagramDetection, InstagramSurface, INSTAGRAM_EXPLORE, INSTAGRAM_HOME, INSTAGRAM_MESSAGES, INSTAGRAM_OTHER, INSTAGRAM_REELS (+8 more)

### Community 5 - "Task Tunnel — Agent Operating Policy"
Cohesion: 0.14
Nodes (13): Astra's role, Coding discipline, Context efficiency, Delegation rules, Escalation protocol, M0 spike constraints, Model routing, Primary objective (+5 more)

### Community 6 - "TreeSnapshot"
Cohesion: 0.25
Nodes (5): TreeSnapshot, SanitizedFingerprint, YouTubeFingerprint, SanitizedFingerprintTest, locale

### Community 7 - ".detect"
Cohesion: 0.23
Nodes (8): YouTubeDetection, YouTubeSurface, UNKNOWN, YOUTUBE_OTHER, YOUTUBE_SEARCH, YOUTUBE_SHORTS, YOUTUBE_VIDEO, YouTubeSurfaceDetector

### Community 8 - "AccessibilityRuntime.kt"
Cohesion: 0.17
Nodes (11): InspectionStatus, ARMED, CAPTURED, ERROR, IDLE, ROOT_UNAVAILABLE, UNSUPPORTED_APP, PackageTransition (+3 more)

### Community 9 - "DeveloperScreen"
Cohesion: 0.23
Nodes (11): AccessibilityState, ObservedInstagramDetection, ObservedYouTubeDetection, DetectionCard(), DeveloperScreen(), FingerprintCopyAction(), formatTime(), InspectorScreen() (+3 more)

### Community 10 - "Theme.kt"
Cohesion: 0.18
Nodes (10): activity, build, composable, darkcolorscheme, dynamicdarkcolorscheme, dynamiclightcolorscheme, issystemindarktheme, lightcolorscheme (+2 more)

### Community 12 - "ExampleInstrumentedTest.kt"
Cohesion: 0.33
Nodes (4): androidjunit4, ExampleInstrumentedTest, instrumentationregistry, runwith

### Community 13 - ".onCreate"
Cohesion: 0.33
Nodes (4): MainActivity, TaskTunnelTheme(), Bundle, ComponentActivity

### Community 14 - "Type.kt"
Cohesion: 0.33
Nodes (5): fontfamily, fontweight, sp, textstyle, typography

### Community 15 - "M0 Manual Test Plan"
Cohesion: 0.33
Nodes (5): Checks, Environment readiness, Evidence to record, M0 Manual Test Plan, Setup

### Community 16 - "M1 YouTube surface detection — physical phone matrix"
Cohesion: 0.40
Nodes (4): Go / no-go decision (user), Install and run, M1 YouTube surface detection — physical phone matrix, Matrix

### Community 17 - "M2A Instagram diagnostic evidence collection"
Cohesion: 0.40
Nodes (4): Capture each surface, Evidence matrix, Install and prepare, M2A Instagram diagnostic evidence collection

### Community 18 - "Current State"
Cohesion: 0.50
Nodes (3): Current State, M1 YouTube surface-detection proof, M2B Instagram surface-detection proof

## Knowledge Gaps
- **42 isolated node(s):** `Usage-budget policy`, `Primary objective`, `Model routing`, `Astra's role`, `Delegation rules` (+37 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 112 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **8 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SanitizedNode` connect `SanitizedNode` to `TaskTunnelAccessibilityService.kt`, `MainActivity.kt`, `InstagramSurfaceDetectorTest`, `YouTubeSurfaceDetectorTest`, `TreeSnapshot`, `.detect`, `AccessibilityRuntime.kt`, `DeveloperScreen`, `.format`?**
  _High betweenness centrality (0.348) - this node is a cross-community bridge._
- **Why does `InstagramSurface` connect `SanitizedNode` to `InstagramSurfaceDetectorTest`?**
  _High betweenness centrality (0.038) - this node is a cross-community bridge._
- **What connects `Usage-budget policy`, `Primary objective`, `Model routing` to the rest of the system?**
  _42 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `TaskTunnelAccessibilityService.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.09716599190283401 - nodes in this community are weakly interconnected._
- **Should `MainActivity.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.06666666666666667 - nodes in this community are weakly interconnected._
- **Should `SanitizedNode` be split into smaller, more focused modules?**
  _Cohesion score 0.1164021164021164 - nodes in this community are weakly interconnected._
- **Should `Task Tunnel — Agent Operating Policy` be split into smaller, more focused modules?**
  _Cohesion score 0.14285714285714285 - nodes in this community are weakly interconnected._