# Current State

## Active Tunnel notification controls — 2026-09-22

Status: stabilized implementation in the working tree; physical notification validation required.

- An active Task Tunnel posts a silent low-importance notification only while a session exists. The expanded view uses a Hevy-style control hierarchy: a compact purpose label as the primary line, app/state beneath it, the protected app icon, a live countdown plus remaining-time progress bar for timed tunnels, and large rounded controls. The custom view is intentionally compact enough to avoid OEM clipping/truncation on long purpose names. Open-ended sessions show **No time limit**. Lock-screen public content is reduced to **Task Tunnel active**.
- Normal and temporary-detour/intervention actions are **Change purpose** and **End**. Expired/open-ended check-in states expose **Continue**, **Change purpose**, and **End**.
- Notification actions no longer launch a trampoline Activity or a second control overlay. On Android 12+ the AccessibilityService dismisses the notification shade with `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE`, waits until the protected app is actually foreground, and only then shows/changing tunnel UI. This avoids SystemUI/foreground lifecycle races.
- **Change purpose** preserves the original tunnel while the chooser is open and inherits the actual remaining timer when a purpose is selected. Re-selecting the current purpose creates a fresh session with the same remaining time, clearing any temporary detour allowance and effectively recommitting to that purpose. **Keep current purpose** cancels the replacement safely.
- Android 13+ notification permission is optional. Settings includes **Tunnel notification controls** as a fallback route to Android notification settings. Core Task Tunnel behavior remains functional if notification permission is denied.
- The notification is rendered directly from `TunnelRuntimeState`; no second foreground service or notification-owned state machine exists. Rendering is deduplicated so routine accessibility events do not constantly repost an unchanged notification.


## Purpose expansion and directed routing — 2026-09-22

Status: implementation complete in the working tree; physical validation required on the installed Instagram and YouTube versions.

- Instagram Purpose Gate now offers **Reply to messages**, **Search / look something up**, **Post something**, and **Browse intentionally**. Search routes toward Explore/Search; Post routes toward Instagram Create; nested screens use a bounded Back-to-main-shell fallback when the target tab is not exposed.
- Instagram Profile and Create are now separate semantic surfaces instead of being folded into Other. Search and Messages are intentionally compatible purposes: a Messages tunnel may move into Explore/Search, and a Search tunnel may move into DMs, while Home/Reels remain out of tunnel. Search still allows Profile/content-detail lookup paths; Post allows the Create/creation-detail path while blocking browsing surfaces.
- YouTube Purpose Gate now offers **Search / watch something specific**, **Check subscriptions**, **Watch Shorts intentionally**, and **Browse intentionally**. Directed tab routing clicks exact whitelisted YouTube chrome and may unwind only detector-confirmed nested Search/Video screens; it never blindly presses Back from the top-level YouTube shell.
- YouTube now distinguishes top-level **Home**, **Subscriptions**, and **You** surfaces in addition to Search, Video, Shorts, and Other. Detection is content-first: an expanded/full-size watch player outranks a still-selected Home/Subscriptions/You tab, while a small mini-player does not override the feed underneath it. For the **Check subscriptions** purpose, the sanitizer may also derive only the fixed semantic state **Subscribed / Not subscribed** from YouTube's Subscribe control; channel identity and raw accessibility labels are discarded immediately and are never stored in snapshots, fingerprints, Attention history, or Room.
- YouTube Search/Watch treats Home, Shorts, Subscriptions, You, and generic Other shell/detail surfaces as outside the tunnel, allowing only Search and normal Video. **Check subscriptions** allows the Subscriptions feed plus Videos or Shorts when the creator is explicitly subscribed (or subscription state is unavailable and therefore fails open), and intervenes when YouTube explicitly exposes that the current Video/Short creator is not subscribed. Intentional Shorts allows Shorts and intervenes on other confidently classified YouTube surfaces. UNKNOWN still fails open.
- Attention labels, Today surface rows, Protection/Settings intention summaries, and focused detector/policy tests were updated for the new tasks and surfaces.

## TikTok T2 integration

Status: implementation complete; physical validation remains pending.

- TikTok 47.0.3 (versionCode 2024700030) now uses the existing semantic surface
	detector and accessibility-based Surface Usage segment tracker. Feed, Friends,
	Search, Inbox, Profile, and Other remain distinct classified surfaces; UNKNOWN
	is stored as Unclassified after the existing stabilization grace.
- Home includes TikTok only when tracked activity exists and preserves the compact
	Today hierarchy and coverage treatment. Search-origin videos remain Search and
	Inbox conversations remain Inbox.
- Attention, Review, deterministic Patterns/Trends, Drift, Protection, onboarding,
	and supported-app presentation now consume TikTok through their existing generic
	models. Drift thresholds and Review/Pattern thresholds are unchanged.
- No Room migration was required: app and surface values are existing string
	fields. TikTok remains local-only and no UsageStats permission was added.
- T2 does not add Facebook or new TikTok-specific analytics/storage systems.

## UI polish pass 5

Status: implementation, debug assembly, and requested device-capture verification are complete.

- Production-facing copy now uses plain product language for Android Accessibility access, local processing, Attention history, and setup. MVP, backend, service-class, and structured-event wording was removed from normal UI while technical diagnostics remain unchanged.
- Protection keeps the existing off-state explanation and repair path, with a compact 48dp action labeled **Turn protection on**. The nearby explanation continues to state that Android Accessibility access is required.
- Privacy disclosure retains its complete meaning: what Accessibility can see, why it is needed, what is stored and not stored, and that processing stays on the device with no account or cloud connection required.
- No runtime behavior, detector, policy, persistence, developer/debug UI, navigation structure, or Attention/Episode Detail design changed in this pass.
- `GRADLE_USER_HOME=C:\\Users\\rubin\\.gradle .\\gradlew.bat :app:assembleDebug --no-daemon` passes. `git diff --check` is clean. Fresh 1080x2354 captures for Protection off, Privacy & Accessibility, and Settings/About are retained in `artifacts/ui-polish-pass-5/`; the existing app data was preserved with `adb install -r`. No runtime overlays were captured.

## UI polish pass 4

Status: implementation, JVM verification, debug assembly, six requested physical captures, and narrow-viewport inspection are complete. Fresh Pass 4 captures are retained in `artifacts/ui-polish-pass-4/`; the existing Attention database, WAL, and SHM were temporarily backed up inside the app sandbox for the empty-state capture and restored afterward. Populated history returned successfully.

- The primary production shell now uses a compact inset-aware header, a quiet two-destination bottom navigation state, and a consistent Material outlined icon family.
- Attention uses compact protection repair routing when protection is off, outcome-led episode summaries, text-first Drift rows, and less divider-driven grouping. Episode detail suppresses adjacent duplicate displayed timestamps and differentiates Intent, Transition, Intervention, and Decision with small semantic labels and node color.
- Protection, Privacy & Accessibility, and Settings retain the existing information architecture and callbacks while using denser rhythm, moderate action geometry, and clearer disclosure question/answer hierarchy.
- No runtime overlay, AccessibilityService, detector, policy, persistence, debug/developer UI, or product behavior changed in this pass.

## Frontend/UI redesign pass

Status: implementation, JVM/instrumented regression verification, debug/release builds, and static visual QA are complete. Runtime overlay captures and complete physical-device visual review remain pending because Android did not permit the automated test session to grant Accessibility access; those states require the developer's affirmative system-settings action.

- The Compose frontend uses the shared Task Tunnel dark canvas, semantic color roles, typography, spacing tokens, reusable list rows, a two-destination shell, and secondary Settings/navigation surfaces.
- Attention remains chronological and defaults to the app shell. Protection uses utility-style rows and health text. Episode detail uses the semantic causal path. Onboarding and privacy/accessibility disclosure use the same typography and spacing system.
- The onboarding explanation rows keep the lead title in a weighted column with the supporting copy below it, so normal and larger text do not collapse into narrow vertical word columns. Attention and Protection list content include bottom breathing room above navigation controls.
- Secondary screens now handle Android gesture/hardware Back consistently with their visible top-bar Back action.
- Seven reviewed device captures are retained in `artifacts/frontend-redesign/`: Attention empty, Attention populated, Episode detail, Protection off/repair, corrected onboarding, Accessibility disclosure, and Settings/privacy. A Protection-active capture plus Purpose Gate, intervention, Drift check-in, and session-expiry captures still require Accessibility access to be enabled manually on a physical device.
- The complete debug JVM suite passes 107 tests, the connected Android 16 instrumentation suite passes 2 tests, `:app:assembleDebug` and unsigned `:app:assembleRelease` pass, and `git diff --check` reports no whitespace errors.
- No detector, policy, persistence, M0-M6 runtime behavior, or product feature was changed by this pass.

## M6 Hardening, Trust, Reliability, and External-Beta Readiness

Status: automated implementation and repository verification are complete; physical M6 UX/runtime validation and external-device validation are pending. M0 through M4 remain physically validated per user confirmation. M5 remains implementation-complete with physical-device UX validation pending; M6 does not change that validation status.

- The main app now uses the approved information architecture: Attention remains the default primary destination, Protection is the other primary destination, and Settings is a small secondary destination. No Home, Focus, Insights, Profile, or account concept was added.
- First-run onboarding persists only completion/current-step state and follows value -> operation -> prominent privacy/Accessibility disclosure -> affirmative Android-settings action -> verification -> supported-app/optional Drift setup. The user can defer setup, decline access, resume an interrupted flow, or enable access later without being trapped or repeatedly sent to settings.
- Protection presents **Is it working?**, **What is protected?**, and **How is it configured?** in that order. Its pure health model maps disabled access to Protection off; enabled/running service state to Protection active; and a disconnected expected service, known persistent compatibility issue, or explicit persistent detector concern to Limited protection. A single `UNKNOWN` remains normal fail-open behavior and never limits protection.
- Instagram and YouTube remain the only protected Task Tunnel apps and retain the exact approved purposes. The normal UI is intention-centric and exposes no Reels/Explore/Shorts rule switches. Drift remains optional and uses only the existing Instagram/YouTube/official Reddit pool.
- The service publishes a bounded in-memory connection/activity heartbeat. Installed versions for the fixed app set are read through targeted package visibility. A compatibility registry exists but is intentionally empty because earlier physical validation did not record exact app versions; unrecorded versions are reported internally/diagnostically without being called incompatible or disabled.
- The normal diagnostic page reports Task Tunnel version, Android/device, Accessibility access, service state/last activity, installed supported-app versions, Drift state/pool, database availability, and report time. Its copy action emits only this explicit sanitized schema and no content, usernames, accessibility text, Attention events, raw trees/fingerprints, resource IDs, node classes, or detector confidence.
- Developer diagnostics remain useful in debug builds. Navigation and routing to detector cards, confidence, fingerprints, raw package transitions, inspector nodes, and the test overlay are gated by `BuildConfig.DEBUG`; release-oriented routing falls back to production-safe diagnostics.
- Settings now provides the normal-user **Clear Attention history** action with confirmation, local-storage explanation, privacy/disclosure access, diagnostics, app version, and debug-only developer options. Clearing uses the existing Room repository and returns Attention to its empty state; database failures degrade to a recoverable unavailable state instead of a stack trace.
- OEM/background handling is deliberately small: enabled access with a disconnected service shows Limited protection, explains that interventions may not appear, offers the normal Accessibility repair flow, and suggests checking background restriction only if interruption continues. M6 requests no battery-optimization exemption and adds no manufacturer database.
- Release hardening adds fixed `<queries>` visibility for Instagram, YouTube, and Reddit, retains no Internet or dangerous runtime permissions, updates production service labels/descriptions, and excludes the Attention database/WAL/SHM plus onboarding/Drift preferences from backup/device transfer. `QUERY_ALL_PACKAGES` remains absent.
- `compileSdk` and `targetSdk` are API 37, satisfying the dossier's API 36-or-higher baseline. The release manifest keeps the launcher activity exported, the AccessibilityService non-exported and protected by `BIND_ACCESSIBILITY_SERVICE`, and the required service metadata.
- `com.example.tasktunnel` remains the application ID and is an explicit publication blocker. No permanent company/domain namespace, signing key, trademark clearance, privacy-policy URL, or Play Console approval is claimed or invented.
- Play/privacy preparation now lives in `ACCESSIBILITY_DISCLOSURE.md`, `PRIVACY_SUMMARY.md`, `PLAY_REVIEW_VIDEO_SCRIPT.md`, `PLAY_REVIEW_CHECKLIST.md`, and `RELEASE_AUDIT_M6.md`. `MANUAL_TEST_M6.md` covers first run, repair, runtime regression, lifecycle, Android UI modes, app updates, OEMs, privacy, and release builds. These are preparation materials and require live policy/Console verification at submission time.
- Focused M6 JVM tests cover active/off/limited health, single-UNKNOWN fail-open behavior, missing apps, disconnected service, empty compatibility evidence, sanitized diagnostics/release gating, first run, settings return, declined access, completion, and non-spamming re-entry. All 13 focused M6 tests pass.
- The complete debug JVM suite passes 107 tests with zero failures. The connected Android 16 instrumentation suite passes 2 tests, including Attention Room persistence/reopen/clear behavior. `:app:assembleDebug` and unsigned `:app:assembleRelease` pass; release lint-vital also passes as part of assembly. `git diff --check` reports no whitespace errors. APKs: `app\build\outputs\apk\debug\app-debug.apk` and `app\build\outputs\apk\release\app-release-unsigned.apk`.
- Physical M6 validation is still required. The attached Android 16 device ran instrumentation only; it did not complete the manual M6 UX/runtime matrix. Pixel-class and Samsung-class release-candidate validation remain pending, with unavailable hardware to be covered by the external beta.
- No post-MVP feature work was started: no additional protected apps, generic discovery/blocking, accounts/cloud, telemetry, ML/LLM behavior, downloaded rules, monetization, gamification, browser/VPN control, advanced personalization, or automatic task-completion inference was added.

## M5 Local Attention Debugger

Status: automated implementation is complete; physical-device UX validation is pending. M6 implementation is recorded above. M0 through M4 have passed physical-device validation per user confirmation.

- Attention is now the default main-app destination. It leads with a chronological list of recent Task Tunnel and Drift episodes, opens a natural-language detail timeline, shows a quiet active-Tunnel context when applicable, and keeps the existing Protection/setup and debug diagnostics reachable without introducing the full M6 navigation system.
- A minimal Room v1 database persists structured semantic events only. The schema stores timestamp, event family, semantic subtype, known app, known surface, declared task, Tunnel ID, Drift episode ID, decision, and known involved apps. It never accepts accessibility text, message content, usernames, screenshots, raw trees, resource IDs, node classes, fingerprints, confidence, or raw AccessibilityEvents.
- The explicit event families are **Intent**, **Transition**, **Intervention**, and **Decision**. Presentation copy is generated from structured enum-backed data instead of storing complete user-facing sentences.
- The AccessibilityService records only meaningful state boundaries: purpose selection/session start, recognized Messages/Reels/Explore/Search/Video/Shorts changes, surface interventions when actually shown, Return/Allow Anyway/End Tunnel, session-expiry intervention and Finish/Continue/Choose another purpose, Drift sequence/check-in, and Keep going/Set an intention.
- A pure semantic filter suppresses repeated observations of the same surface and ignores `UNKNOWN` and generic/other surfaces. Recording uses a single serialized coroutine IO lane and never blocks AccessibilityService event handling.
- Events sharing a Tunnel ID group into one Task Tunnel episode. Events sharing a Drift episode ID group into one Drift episode with its ordered human-readable app sequence. Unrelated IDs stay separate.
- The Attention detail screen presents time plus natural descriptions of intention, transitions, interventions, and decisions. Normal UI uses Instagram, YouTube, and Reddit labels only; it never exposes package identifiers or detector internals.
- The only rolling metric is a quiet distinct Drift-episode count for the last seven days. No focus/productivity score, grade, streak, XP, raw screen-time hero, or override-failure metric was added.
- Empty history explains that intentions, meaningful transitions, check-ins, and choices will appear locally over time. Debug builds provide a separate **Clear Attention history** action for physical testing.
- Focused JVM tests cover structured intent, meaningful/deduplicated surface transitions, fail-open/noise handling, intervention deduplication, Return/Allow Anyway/End and all expiry decisions, Drift sequence/check-in/decisions, forbidden diagnostic fields, Tunnel/Drift grouping, unrelated episodes, weekly Drift metrics, and empty history.
- The Room instrumentation test persists an event, closes and reopens the on-device database, reloads it, clears it, and verifies the database is empty. `MANUAL_TEST_M5.md` contains the full physical timeline, persistence, noise, privacy, active-Tunnel, Drift, and clear-history matrix.
- All 13 focused M5 JVM tests pass; the focused Room instrumentation test passes on the connected Android 16 device; the complete debug JVM suite passes 94 tests with zero failures; `:app:assembleDebug` passes; and `git diff --check` reports no whitespace errors. APK: `app\build\outputs\apk\debug\app-debug.apk`.

## M4 MVP Drift Detection

Status: complete, including user-confirmed physical-device validation. M5 preserves the validated detector, episode, intervention-priority, and choice behavior.

- Drift Detection now operates on foreground package transitions only. The pure in-memory detector ignores repeated observations from the same package, excludes non-selected apps from the qualifying count, keeps a bounded 24-transition rolling window, and stores no app content.
- Central beta policy values are three distinct selected apps within 60 seconds, followed by a 60-second quiet period before a new episode can form. These are explicit `DriftPolicy` defaults rather than scattered literals.
- An episode records an ID, start time, ordered involved package names, latest qualifying transition time, and whether its single check-in was shown or acknowledged. **Keep going** acknowledges the current episode without creating a Tunnel or treating the choice as failure. A new episode can trigger after the quiet reset.
- The local Drift pool uses a small known-app catalog for Instagram, YouTube, and Reddit. The production home and debug app shell expose simple local switches backed by `SharedPreferences`. The implementation does not query installed apps, request `QUERY_ALL_PACKAGES`, or expand Task Tunnel surface policy beyond Instagram and YouTube.
- The soft overlay uses human app labels and the approved observational copy direction. **Set an intention** returns to the existing Purpose Gate when the current app is Instagram or YouTube, without selecting a task. On Reddit or another unsupported Task Tunnel app it safely acknowledges and dismisses without creating a generic Tunnel.
- Existing Task Tunnel UI has strict priority over Drift. Drift never stacks over a Purpose Gate, surface intervention, or expiry decision. An active Tunnel, including its M3.1 quick-return grace, clears/suppresses transient Drift accumulation. A Drift overlay is removed when the foreground becomes a non-selected or internal app.
- Accessibility-service restart and Drift-pool changes clear transient detector and episode state. All runtime Drift state is in memory; only the user's selected pool is persisted locally.
- Focused tests cover threshold/window behavior, repeated and non-selected packages, one check-in per episode, acknowledgement, quiet reset/new episode, supported and unsupported Set an intention routing, active-Tunnel suppression, overlay priority, unrelated-app behavior, and restart semantics.
- All 18 focused Drift tests pass; all 24 M3/M3.1 policy and coordinator regression tests pass; the complete debug JVM suite passes 81 tests with zero failures; `:app:assembleDebug` passes; and `git diff --check` reports no whitespace errors. APK: `app\build\outputs\apk\debug\app-debug.apk`.
- `MANUAL_TEST_M4.md` contains the completed physical positive, Keep Going, Set an intention, pool, slow-switching, existing-Tunnel, overlay-priority, lifecycle, and privacy matrix.

## M3.1 Task Tunnel timing alignment

Status: complete, including user-confirmed physical-device validation. M4 preserves the validated timing, expiry, grace, override, Purpose Gate, detector, and policy behavior.

- **Allow anyway** now creates a temporary allowance scoped to the current Tunnel ID and incompatible surface. The beta allowance is an explicit coordinator policy value of five minutes. Allowed-surface navigation and brief app switches do not cancel it; another incompatible surface does not inherit it; the next incompatible detection after expiry may intervene again.
- Active Tunnels now survive a short switch away from their protected app. The beta quick-return grace is an explicit coordinator policy value of 60 seconds. Returning inside grace resumes the same Tunnel without a Purpose Gate; grace expiry clears it silently, and the next protected-app foreground visit receives a fresh gate. Explicit End Tunnel and accessibility-service restart still clear state immediately.
- The Purpose Gate offers **No limit**, **5 min**, **10 min**, and **20 min** as optional duration choices while retaining the two existing, equally presented task choices for each supported app. No limit remains the default, including for intentional browsing.
- A timed Tunnel derives its expiry from its start time and selected duration. Foreground expiry shows a neutral re-decision with **Finish**, **Continue**, and **Choose another purpose**. Finish ends the Tunnel without closing the host app; Continue starts a fresh window of the same duration; Choose another purpose returns to the Purpose Gate.
- Timed expiry while the protected app is backgrounded ends silently. The service schedules only the next coordinator deadline and verifies the current active-root package before applying it, so expiry and grace do not place Task Tunnel UI over an unrelated app.
- Timing remains in the pure `TunnelCoordinator`; detector classification and `SessionPolicy` rules are unchanged. `UNKNOWN` continues to fail open, and normal user UI still excludes diagnostic details.
- Focused M3.1 tests cover allowance scope/expiry, quick return, explicit end/service-reset semantics, unrelated-app behavior, optional/no-limit duration, foreground expiry decisions, silent background expiry, and deadline selection. All 24 focused coordinator/policy tests pass; the complete debug JVM suite passes 63 tests; and `:app:assembleDebug` passes. Physical M3.1 validation passed per user confirmation.
- `MANUAL_TEST_M3_1.md` contains the physical Allow Anyway, quick-return, timed-session, background-expiry, fail-open, and privacy matrix.

## M3 Task Tunnel interaction

Status: complete, including user-confirmed physical-device validation. M3.1 supersedes the original allowance lifetime and adds grace/session-window behavior without changing the validated detector and policy flow.

- Added a pure local session model with a unique ID, target app, selected task, start time, optional intended duration, active status, and an override-occurrence marker. One in-memory session is owned by the accessibility-service process; a service restart intentionally clears it and returns to a safe Purpose Gate on the next supported-app foreground visit.
- Purpose Gates appear once per supported-app foreground visit when no applicable Tunnel exists. Instagram offers **Reply to messages** and **Browse intentionally**; YouTube offers **Search / watch something** and **Browse intentionally**. **Not now** dismisses the gate until the app leaves and returns.
- Detector outputs are adapted to a neutral surface enum before a separate deterministic policy evaluates them. Instagram Messages allows Messages and intervenes on Reels/Explore. YouTube Search/Watch allows Search/Video and intervenes on Shorts. Intentional Browse allows the selected app's known surfaces. `UNKNOWN` and app/surface mismatches fail open.
- Confident incompatible surfaces show a user-facing accessibility overlay with **Return**, **End Tunnel**, and **Allow anyway**. Return dismisses first, applies a two-second stale-event cooldown for the exact session/surface, and then attempts one Android global Back action. It does not perform autonomous navigation.
- At the M3 checkpoint, Allow Anyway suppressed the same surface until a confident transition. M3.1 replaces that rule with a bounded five-minute session/surface allowance. End Tunnel still clears active policy and prompt state without closing the supported app or immediately reopening the Purpose Gate.
- Instagram capture now runs in release builds as required for M3 policy evaluation; developer fingerprints and tree UI remain debug-only. Normal overlays never expose resource IDs, confidence internals, node trees, fingerprints, or detector signal names.
- M3 introduced the optional intended-duration field; M3.1 now exposes the documented lightweight duration choices and neutral expiry re-decision. No app is forcibly terminated.
- Focused JVM tests cover Instagram Messages policy, YouTube Search/Watch policy, intentional browse, fail-open unknowns, Purpose Gate idempotence, override scoping, End Tunnel cleanup, Return cooldown cleanup, and active-session reuse.
- The complete debug JVM suite passes 49 tests, `:app:assembleDebug` passes, and `git diff --check` reports no whitespace errors. APK: `app\build\outputs\apk\debug\app-debug.apk`.
- `MANUAL_TEST_M3.md` contains the completed physical Instagram, YouTube, lifecycle, anti-loop, fail-open, privacy, and usability matrix.

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
