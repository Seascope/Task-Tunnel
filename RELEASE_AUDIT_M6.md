# M6 Release Audit

Audit date: 20 September 2026. Scope: repository configuration and beta-readiness preparation, not Play approval.

## Findings

- SDK: compile/target API 37, above the dossier's API 36-or-higher submission baseline; minimum API 24.
- Identity: `applicationId` and namespace are `com.example.tasktunnel`. This is a publication blocker because it is placeholder-like.
- Version: `versionCode 1`, `versionName 1.0`; a human must confirm final release numbering.
- Components: launcher activity exported as required by its intent filter; AccessibilityService not exported and protected by `BIND_ACCESSIBILITY_SERVICE`.
- Service metadata: content retrieval, relevant events, feedback type, settings activity, view-ID reporting, and no gesture/filter-key request are declared.
- Visibility: fixed `<queries>` entries for Instagram, YouTube, and official Reddit; no `QUERY_ALL_PACKAGES`.
- Permissions: no Internet permission and no dangerous runtime permission declarations.
- Privacy: Attention stores structured semantic events only. Database/WAL/SHM and Task Tunnel preference files are excluded from backup and device transfer. No screenshots, accessibility text, raw tree, or fingerprint is persisted by the production path.
- UI separation: normal diagnostics are an explicit sanitized schema. Inspector, fingerprint copy, confidence, package transitions, resource IDs, and node details require `BuildConfig.DEBUG`; release routing falls back to production-safe diagnostics.
- Logging: no app-source `Log`, `println`, or stack-trace output found.
- Release build: no private signing configuration or key is present. M6 does not create signing material.
- Compatibility: the registry exists but contains no fabricated ranges. Installed versions are reported. Unknown/unrecorded versions do not disable protection or create false incompatibility warnings.
- Health: access off maps to Protection off; access enabled with a disconnected service maps to Limited protection and small background guidance; known persistent compatibility/degradation inputs can map to Limited; individual `UNKNOWN` surfaces remain fail-open and do not affect health.

## Human release blockers

1. Choose a permanent package/application ID and publisher namespace.
2. Choose and protect the Play signing/upload-key arrangement.
3. Decide the final public product name; no trademark clearance is claimed.
4. Record exact tested Instagram/YouTube versions and complete the compatibility matrix.
5. Complete Pixel-class and Samsung-class release validation.
6. Publish a final privacy-policy URL and complete live Play Console Accessibility/Data safety declarations.
7. Record the review video from the final release candidate.

## Known risks

- Instagram and YouTube UI experiments or updates may break deterministic signatures. `UNKNOWN` continues to fail open, so failure can mean a missed intervention rather than a false block.
- An enabled-but-disconnected service can have OEM, process-lifecycle, or Android causes. M6 provides restrained repair guidance but intentionally does not request battery-optimization exemptions.
- No automatic detector-degradation heuristic was added. With no reliable historical version baseline or calibrated sample threshold, version/status reporting and manual diagnostics are safer for this beta.
- The release route is source-gated and covered by JVM policy tests, but the final release APK still requires manual device inspection.
