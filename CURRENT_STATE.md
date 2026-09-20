# Current State

## M3 Task Tunnel interaction

Status: automated implementation is complete; physical-device validation is pending.

- Added a pure local session model with a unique ID, target app, selected task, start time, optional intended duration, active status, and an override-occurrence marker. One in-memory session is owned by the accessibility-service process; a service restart intentionally clears it and returns to a safe Purpose Gate on the next supported-app foreground visit.
- Purpose Gates appear once per supported-app foreground visit when no applicable Tunnel exists. Instagram offers **Reply to messages** and **Browse intentionally**; YouTube offers **Search / watch something** and **Browse intentionally**. **Not now** dismisses the gate until the app leaves and returns.
- Detector outputs are adapted to a neutral surface enum before a separate deterministic policy evaluates them. Instagram Messages allows Messages and intervenes on Reels/Explore. YouTube Search/Watch allows Search/Video and intervenes on Shorts. Intentional Browse allows the selected app's known surfaces. `UNKNOWN` and app/surface mismatches fail open.
- Confident incompatible surfaces show a user-facing accessibility overlay with **Return**, **End Tunnel**, and **Allow anyway**. Return dismisses first, applies a two-second stale-event cooldown for the exact session/surface, and then attempts one Android global Back action. It does not perform autonomous navigation.
- Allow Anyway records that an override occurred and suppresses the same surface for the current Tunnel until a confidently different surface is observed. Unknown detector frames preserve that override instead of creating a prompt loop. End Tunnel clears active policy and prompt state without closing the supported app or immediately reopening the Purpose Gate.
- Instagram capture now runs in release builds as required for M3 policy evaluation; developer fingerprints and tree UI remain debug-only. Normal overlays never expose resource IDs, confidence internals, node trees, fingerprints, or detector signal names.
- The model includes an optional intended-duration field, but M3 does not expose timed sessions or expiry UI. This avoids introducing an unvalidated second intervention flow; no app is forcibly terminated.
- Focused JVM tests cover Instagram Messages policy, YouTube Search/Watch policy, intentional browse, fail-open unknowns, Purpose Gate idempotence, override scoping, End Tunnel cleanup, Return cooldown cleanup, and active-session reuse.
- The complete debug JVM suite passes 49 tests, `:app:assembleDebug` passes, and `git diff --check` reports no whitespace errors. APK: `app\build\outputs\apk\debug\app-debug.apk`.
- `MANUAL_TEST_M3.md` contains the physical Instagram, YouTube, lifecycle, anti-loop, fail-open, privacy, and usability matrix. Every physical row remains pending.

## M2B Instagram surface-detection proof

Status: complete per user-confirmed physical-device validation; implementation and regression coverage are retained for M3.

- Exact normalized resource-ID segments plus visible/selected/clickable/scrollable/editable flags classify Home, Messages inbox/conversation, Explore, Reels, and Profile as `INSTAGRAM_OTHER`. Weak, inactive, non-Instagram, or conflicting evidence fails open to `UNKNOWN` at zero confidence.
- Reels requires visible `clips_expanded_touch_view` plus at least two approved exact component IDs. A selected clips tab raises evidence strength but is optional, supporting Reels opened from Explore. Stale clip IDs without the visible expanded touch view cannot classify Reels.
- Messages requires either the active Direct tab plus visible scrollable inbox list, or the full conversation container/list/composer structure. Physical-device validation found that Instagram's visible `message_list` scrollable semantics vary, so the conversation rule now requires it to be visible within the otherwise full required structure. Explore requires the active Search tab, active editable search field, and visible scrollable recycler. Home and Profile require their visible clickable selected tabs.
- Detection runs on the same one-second-throttled, 200-node/depth-12 sanitized snapshot used by the inspector and fingerprint; no duplicate traversal or content-bearing input was added. Current and last observations remain in memory, and current clears on leaving Instagram, root failure, or disconnect.
- The developer-only card shows current/last surface, evidence strength, capture metadata, and bounded strongest signals, while retaining the sanitized fingerprint copy action and clipboard warning.
- Twenty-one Instagram detector tests cover the physical-evidence routes, conversation structure boundaries including non-scrollable visible message lists, inactive/stale IDs, exact matching, package boundary, and conflicts.
- M2A evidence collection and M2B physical validation are complete.
- M1 YouTube behavior and detector rules remain intact.

## M1 YouTube surface-detection proof

Status: complete per user-confirmed physical-device validation; implementation and regression coverage are retained for M3.

- Added a pure deterministic detector over immutable sanitized nodes. Its stable outputs are `YOUTUBE_SHORTS`, `YOUTUBE_VIDEO`, `YOUTUBE_SEARCH`, `YOUTUBE_OTHER`, and fail-open `UNKNOWN`.
- Detection uses exact normalized resource-ID segments plus bounded class/structure/flag signals. It never uses text, content descriptions, screenshots, or raw trees. Physical sanitized fingerprints established the exact Shorts pair used by the detector.
- Shorts is strong only when the exact normalized ID set contains both `reel_recycler` and `reel_player_page_container`, at 95% evidence strength. Generic playback/progress IDs (`reel_time_bar`, `reel_progress_bar`, and `nerd_stats_container`) are not Shorts evidence. Conflicting specialized evidence returns `UNKNOWN`; non-YouTube packages return `UNKNOWN` at zero confidence.
- The service now runs one bounded sanitizer/capture path for active YouTube snapshots even when the debug inspector is not armed. Content/scroll updates use a one-second throttle with a trailing capture.
- Current detection clears when leaving YouTube, the root is unavailable, or the service disconnects. The last observed YouTube result remains bounded in memory for the developer UI and is never persisted.
- The developer UI displays current or clearly labeled last-observed surface, evidence-strength confidence, capture time, package, and bounded strongest sanitized signals.
- Physical fingerprints showed both Shorts entry paths contain `reel_recycler` plus `reel_player_page_container`; Home, Search, and long video lack both. `reel_time_bar`, `pivot_bar`, `reel_progress_bar`, and `nerd_stats_container` are excluded as generic or non-distinguishing evidence.
- Debug builds expose a `Copy sanitized fingerprint` action for the current/last YouTube capture. The deterministic summary includes normalized ID and class/depth counts, structural flags, selected nodes, and bounded parent-child/container relationships. It remains in memory only and never reads text or content descriptions. Copying intentionally places the summary in Android's OS-managed clipboard, which may retain it outside the app process.
- Sixteen focused JVM tests passed: twelve detector cases covering the verified Shorts pair and fail-open boundaries, plus four fingerprint tests covering normalization/counts, histograms/selection/relationships, deterministic bounds/omissions, and absence of content fields.
- `:app:assembleDebug` passed. APK: `app\build\outputs\apk\debug\app-debug.apk`.
- Main ongoing risk remains resource-ID and hierarchy variation across versions/accounts/experiments.

M0: complete per user-provided device validation; baseline retained.
