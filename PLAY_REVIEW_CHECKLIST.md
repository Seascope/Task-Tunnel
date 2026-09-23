# Task Tunnel Play Review Checklist

This checklist prepares an eventual submission. Re-verify every policy item and form against the live Google Play Console at submission time; repository documents do not guarantee approval.

## Release blockers requiring a human decision

- [ ] Choose the permanent application ID. The current `com.example.tasktunnel` namespace is a placeholder and must not be published accidentally.
- [ ] Decide the final publisher/company domain namespace before changing the application ID.
- [ ] Create and securely manage the Play App Signing/upload-key workflow. No signing key is created or committed by M6.
- [ ] Decide whether **Task Tunnel** remains the public name and complete appropriate trademark/name review. It is a working name only.
- [ ] Choose final `versionName`/`versionCode` for the first external build; the project currently uses `1.0` / `1`.
- [ ] Record exact physically validated Instagram and YouTube versions, locale, surfaces, device, and date in the compatibility registry. Historic M1/M2 validation did not record version numbers.
- [ ] Provide a public privacy-policy URL that matches the shipped build and Play Data safety answers.
- [ ] Complete Pixel-class and Samsung-class release-candidate validation. Any unavailable device remains external-beta pending.

## Build and manifest audit

- [x] `compileSdk` and `targetSdk` are API 37, meeting the dossier's API 36-or-higher baseline.
- [x] AccessibilityService is explicitly exported for Android system discovery, requires `BIND_ACCESSIBILITY_SERVICE`, and has service metadata.
- [x] Launcher activity is explicitly exported; the notification action receiver is not exported. No unprotected internal component is externally exposed.
- [x] Service configuration declares window/content events, content retrieval, generic feedback, and view-ID reporting.
- [x] Package visibility uses targeted entries for known apps plus `MAIN`/`LAUNCHER` intent visibility so the user-facing Drift picker can list launchable apps; `QUERY_ALL_PACKAGES` is absent.
- [x] Detailed accessibility-tree parsing remains limited to Instagram, YouTube, and TikTok; arbitrary Drift-only apps contribute foreground package identity only.
- [x] `QUERY_ALL_PACKAGES` is absent.
- [x] Internet permission is absent; no analytics, telemetry, remote rules, or activity-history upload exists.
- [x] No unused dangerous permissions are declared.
- [x] Application and service labels describe Task Tunnel protection rather than a developer spike.
- [x] Attention database, WAL, SHM, onboarding, and Drift preference files are excluded from cloud backup and device transfer.
- [x] Release configuration does not require private signing credentials for an unsigned assemble check.
- [x] No production logging statements were found in app source.
- [ ] Re-run manifest/aapt inspection on the final signed artifact.
- [ ] Run `python scripts/release_preflight.py` on the final Play-bound tree; it must pass without the placeholder-ID override.

## Accessibility declaration and disclosure

- [x] Value and operation appear before the Accessibility disclosure.
- [x] The app presents a prominent, separate disclosure before opening Android settings.
- [x] Android settings opens only after the affirmative **Agree & open settings** action; **Not now** remains available.
- [x] Disclosure explains visible-interface access, purpose, retained data, excluded data, and local processing.
- [x] Copy identifies the feature as digital wellbeing and does not claim disability-support status.
- [x] Interventions are narrow, deterministic, explainable, and user-controlled.
- [x] Detector uncertainty fails open.
- [ ] Complete the live Play Accessibility declaration with wording matching the shipped experience.
- [ ] Record the review video from the final release candidate using `PLAY_REVIEW_VIDEO_SCRIPT.md`.

## Store and policy submission

- [ ] Re-check the current target API deadline and all AccessibilityService policy pages at submission time.
- [ ] Complete the Data safety form from actual release behavior; do not copy assumptions from this checklist.
- [ ] Confirm the public privacy policy, store description, screenshots, and review notes use consistent claims.
- [ ] Explain why AccessibilityService is necessary for the user-facing Task Tunnel feature and why narrower APIs cannot identify native app surfaces.
- [ ] Explain the launcher-intent package visibility used by the Drift app picker and confirm the final merged manifest still has no `QUERY_ALL_PACKAGES` permission.
- [ ] Upload the reviewer video and provide reproducible reviewer steps.
- [ ] Verify that release UI contains no inspector, fingerprint, detector confidence, resource ID, package ID, node count, or developer copy action.

## External beta gate

- [ ] Complete every applicable P0/P1 row in `MANUAL_TEST_RELEASE_CANDIDATE.md` on the exact release candidate.
- [ ] Record installed supported-app versions for every physical detector run.
- [ ] Confirm repair behavior after permission removal, process death, and reboot.
- [ ] Inspect a copied diagnostic report manually for sensitive data.
- [ ] Confirm Attention history clearing returns Attention to the empty state.
- [ ] Confirm release APK/AAB launches and production-safe diagnostics remain available.
