# Task Tunnel Release-Candidate Static Audit

Audit date: 3 October 2026. Scope: final repository/static hardening before tester/Play distribution. This is not Play approval and does not replace building, signing, and physically validating the exact distributed artifact.

## Verified in the repository

- Protected Task Tunnel apps are Instagram, YouTube, and TikTok.
- Drift app selection can include arbitrary installed launchable apps; Drift-only apps contribute foreground package identity only.
- Manifest declares `POST_NOTIFICATIONS` and `INTERNET`; `QUERY_ALL_PACKAGES` is absent.
- Internet use is limited to the explicit in-app feedback client. No analytics SDK, account system, telemetry SDK, remote detector-rule client, or background uploader is present in the app module.
- Feedback payload is fixed: category, user-written message, source label, and optional safe app/device status. It does not attach Attention history, Drift paths, accessibility text, screenshots, usernames, searches, messages, or app content.
- Accessibility service metadata declares `isAccessibilityTool=false`, `canRetrieveWindowContent=true`, and no gesture/filter-key capability.
- Accessibility service is exported for system discovery and protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`.
- Notification action receiver is not exported; notification actions are session-ID scoped.
- Database and shared preferences are excluded from cloud backup and device-to-device transfer by both legacy and Android 12+ backup rules.
- Developer inspector/fingerprint UI is guarded by `BuildConfig.DEBUG`; production troubleshooting exposes only the bounded diagnostic report.
- Production diagnostics contain device/app versions, permission/service state, Drift configuration, database availability, and timestamps. They do not include arbitrary accessibility text or Attention-history rows.
- The Play-bound `applicationId` is `com.rubin.tasktunnel`. Runtime Drift self-exclusion uses the actual app package rather than a hard-coded placeholder.
- Final hardening also covers notification-driven purpose replacement while away from the protected app and duplicate dynamic screen-receiver registration on AccessibilityService reconnect.

## Remaining external release requirements

1. Confirm `com.rubin.tasktunnel` is the permanent package identity you want before the first Play upload.
2. Configure `TASK_TUNNEL_FEEDBACK_FORM_ID` and verify a real feedback submission reaches the intended inbox.
3. Build and run the full Gradle unit/lint/release tasks locally; the final audit sandbox could not download uncached Gradle 9.7.1.
4. Complete the physical P0/P1 matrix on the exact APK/AAB being distributed.
5. Create/manage the Play App Signing/upload key outside source control.
6. Publish the privacy-policy URL and complete Data Safety plus AccessibilityService declarations against the exact shipped behavior, including the explicit feedback path.

## Review-sensitive behavior to describe accurately

- Task Tunnel is a digital-wellbeing app, not a disability accessibility tool.
- Accessibility is used to recognize supported in-app semantic surfaces, observe foreground app changes for Drift, present user-controlled overlays, and perform narrow deterministic return navigation only after explicit user actions.
- Detector uncertainty fails open.
- Raw accessibility content is not retained as activity history or transmitted.
- The only network data path is the explicit feedback form after the user taps Send.
- The Drift picker relies on launcher-intent package visibility rather than `QUERY_ALL_PACKAGES`.

## Final artifact checks

Configure feedback, then run:

```text
python scripts/release_preflight.py
```

The final Play-bound tree must pass with no override flags. Then run the Gradle/unit/lint/release commands in `FINAL_AUDIT_2026-10-03.md`, inspect the merged manifest/AAB, record the exact supported-app versions tested, and complete `MANUAL_TEST_RELEASE_CANDIDATE.md`.
