# Task Tunnel Release-Candidate Static Audit

Audit date: 23 September 2026. Scope: repository/static Play-readiness checks after the focused beta-hardening passes. This is not Play approval and does not replace physical-device validation.

## Verified in the repository

- Protected Task Tunnel apps: Instagram, YouTube, and TikTok.
- Drift app selection can include arbitrary installed launchable apps; Drift-only apps contribute foreground package identity only.
- Manifest declares `POST_NOTIFICATIONS` and does not declare `INTERNET` or `QUERY_ALL_PACKAGES`.
- No Formspree endpoint, analytics SDK, account system, telemetry SDK, or network client dependency is present in the app module.
- Accessibility service metadata declares `isAccessibilityTool=false`, `canRetrieveWindowContent=true`, and no gesture/filter-key capability.
- Accessibility service is exported for system discovery and protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`.
- Notification action receiver is not exported; notification action PendingIntents are immutable and session-ID scoped.
- Database and shared preferences are excluded from cloud backup and device-to-device transfer by both legacy and Android 12+ backup rules.
- Developer inspector/fingerprint UI is guarded by `BuildConfig.DEBUG`; production troubleshooting exposes only the bounded diagnostic report.
- Production diagnostics contain device/app versions, permission/service state, Drift configuration, database availability, and timestamps. They do not include arbitrary accessibility text or Attention-history rows.
- Current manual gate is `MANUAL_TEST_RELEASE_CANDIDATE.md`.

## Intentional publication blockers

1. `applicationId = "com.example.tasktunnel"` is still a placeholder. Do not upload it as the permanent Play identity.
2. Final signing/upload-key configuration has not been committed and should not be committed as secrets.
3. A public privacy-policy URL is still required.
4. Final Play Data Safety and AccessibilityService declarations must be completed against the exact signed AAB.
5. Closed testing and the physical P0/P1 matrix must be completed on the exact candidate build.

## Review-sensitive behavior to describe accurately

- Task Tunnel is a digital-wellbeing app, not a disability accessibility tool.
- Accessibility is used to recognize supported in-app semantic surfaces, observe foreground app changes for Drift, present user-controlled overlays, and perform narrow deterministic return navigation only after explicit user actions.
- Detector uncertainty fails open.
- Raw accessibility content is not retained as activity history or transmitted.
- The app has no Internet permission in the current Play-bound build.
- The Drift picker relies on launcher-intent package visibility rather than `QUERY_ALL_PACKAGES`.

## Final artifact checks

Run:

```text
python scripts/release_preflight.py
```

The final Play-bound tree must pass without `--allow-placeholder-id`. Then inspect the merged manifest/AAB, record the exact tested supported-app versions, and complete `MANUAL_TEST_RELEASE_CANDIDATE.md`.
