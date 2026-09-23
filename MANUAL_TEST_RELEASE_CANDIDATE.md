# Task Tunnel Release-Candidate Torture Matrix

This is the current physical release gate. It supersedes the old milestone-by-milestone manual matrices for deciding whether a beta/Play candidate is stable enough to ship.

The goal is not to prove every screen looks perfect. The goal is to catch state, lifecycle, routing, arbitration, privacy, and external-app-version failures that automated JVM tests cannot prove.

## Run record

Record this before every full run:

- Task Tunnel version/build:
- Device / OEM:
- Android version/build:
- Instagram version:
- YouTube version:
- TikTok version:
- Tester:
- Date:
- Fresh install or upgrade:

Mark a test **BLOCKER** if it can strand an overlay, route out of the target app, apply the wrong purpose, lose an active tunnel/check-in, corrupt visible history, or violate privacy/fail-open behavior.

## P0 — release blockers

All P0 checks must pass on at least one real device before cutting a release candidate.

### 1. Master protection hard boundary

- [ ] With no tunnel active, turn protection OFF while Instagram/YouTube/TikTok is foreground. Return to each app. No Purpose Gate, Drift, check-in, capture, or notification may appear.
- [ ] Turn protection OFF while a Purpose Gate is visible. The overlay disappears immediately and does not return.
- [ ] Turn protection OFF during an intervention. The overlay and active notification disappear; no delayed Return/navigation callback fires afterward.
- [ ] Turn protection OFF during an intention check-in. The check-in disappears and does not re-arm while protection is off.
- [ ] Turn protection OFF while a Drift check-in is visible. The Drift overlay disappears and does not return from stale state.
- [ ] Turn protection OFF during directed navigation. No delayed routing step executes after OFF.
- [ ] Toggle OFF -> ON rapidly several times. Exactly one coherent Purpose Gate may appear; never duplicate overlays or notifications.
- [ ] Enable protection while a supported app is already open and idle. Purpose Gate/reconciliation must happen without requiring an extra tap or navigation event.
- [ ] Enable protection while Home/another unsupported app is foreground. No overlay should appear until a supported app is opened.
- [ ] Disable Accessibility permission while master protection is ON, leave a supported app open, then re-enable permission. The service must reconcile the already-open app without stale tunnel state.

### 2. Purpose Gate and remembered duration

Repeat for Instagram, YouTube, and TikTok.

- [ ] Fresh gate shows all supported purposes and no inappropriate remembered timers.
- [ ] Start purpose A with a time limit. Leave/end it, reopen the app. Purpose A moves to the top, is the only row marked **Last used**, and is the only row allowed to show its remembered duration.
- [ ] Start purpose B without changing the timer selector. Purpose B defaults to **No limit** rather than inheriting purpose A's duration.
- [ ] Change purpose during a timed live tunnel. Leave the chooser open for ~30 seconds. The replacement tunnel inherits actual remaining time, not the pre-chooser value.
- [ ] From Change purpose, choose **Keep current purpose**. The existing session and remaining time stay intact.
- [ ] Repeatedly open/dismiss Purpose Gate and Change purpose. No stale click from an old gate may start or navigate a rejected session.

### 3. Check-ins and temporary allowances

- [ ] Trigger a detour intervention and choose **Allow for now**. The same detour remains temporarily allowed.
- [ ] Leave the protected app during the allowance and return within quick-return grace. The allowance retains its remaining **active-use** time; time away must not consume it.
- [ ] Disable intentional check-ins, repeat the previous test, and confirm the temporary allowance still pauses outside the app.
- [ ] Start an allowance with check-ins enabled, then disable check-ins mid-allowance. Leave/return. The allowance must still preserve remaining active-use time.
- [ ] While a detour check-in is visible, switch to Home/another app. The check-in overlay must disappear there.
- [ ] Return within quick-return grace. The unresolved check-in re-arms once without consuming an extra check-in slot.
- [ ] Visit an allowed surface while a detour allowance exists, then return to the detour. The allowance/check-in must not be destroyed just because an allowed surface was visited.
- [ ] Exhaust the maximum intentional check-ins. A later **Allow for now** stays finite and does not reset the check-in counter or begin nagging again.
- [ ] Let the final temporary allowance expire while staying on the detour without touching the screen. Intervention must return from the coordinator deadline; it must not require another accessibility event.
- [ ] Use Return from an intention check-in. Directed recovery must have the same stale-surface cooldown protection as normal intervention Return.

### 4. Notification controls and SystemUI

- [ ] No active tunnel -> no active-tunnel notification.
- [ ] Start an open-ended tunnel -> notification shows app, purpose, and **No limit**.
- [ ] Start a timed tunnel -> collapsed/expanded views show a live countdown without duplicate platform timer text.
- [ ] Open the notification shade for 10+ seconds. SystemUI must not count as leaving the protected app, contaminate Drift, or start quick-return grace.
- [ ] Normal active tunnel -> **Change purpose** works and does not render the picker over an open shade.
- [ ] Normal active tunnel -> **End** ends exactly that session and removes the notification.
- [ ] Expired tunnel -> **Continue**, **Change purpose**, and **End** each perform exactly one action.
- [ ] Intention check-in -> **Continue**, **Change purpose**, and **End** each perform exactly one action.
- [ ] Rapidly tap Continue -> Change purpose and Change purpose -> Continue while the shade is closing. Latest tap wins; never execute two mutations sequentially.
- [ ] Repeatedly tap the same notification action. No duplicate overlay, duplicate navigation, or duplicate Attention decision appears.
- [ ] If an old notification action is somehow delivered after a new tunnel starts, it must not affect the new session.
- [ ] Turn master protection OFF while the shade/action-dismissal callback is pending. Nothing from that pending action may execute afterward.
- [ ] Deny notification permission, use Task Tunnel normally, then re-enable notifications. Controls may resume, but tunnel state must never depend on notification availability.

### 5. Drift arbitration

Use at least three selected apps, including one arbitrary Drift-only app if available.

- [ ] Three distinct selected apps inside the Drift window produce one Drift check-in with exactly those trigger apps frozen in the episode.
- [ ] Repeated transitions within one/two apps never create a displayed Drift episode.
- [ ] A malformed historical Drift row containing fewer than three distinct apps is not displayed in Attention/Review.
- [ ] While Drift is visible, switch between selected apps. The existing episode remains stable; it must not mutate to include later apps.
- [ ] Leave the selected Drift pool while its overlay is visible. Overlay disappears cleanly and detection can form a fresh later episode.
- [ ] A supported current app offers **Set an intention** and **Keep going**.
- [ ] An arbitrary Drift-only app offers only **Understood**; never dead Set intention/Keep going actions.
- [ ] A Drift episode can be formed from recent switching even if a tunnel was started during that recent history; starting a tunnel must not erase Drift history.
- [ ] A Change-purpose chooser belonging to a live tunnel wins over Drift. No stacked overlays.
- [ ] A tunnel intervention/check-in/expiry prompt wins over Drift. No stacked overlays.
- [ ] While a live tunnel is in quick-return grace after switching to another selected app, Drift history may continue accumulating but no Drift overlay may appear until that tunnel actually ends.
- [ ] Drift beats an ordinary fresh Purpose Gate. Choosing **Set an intention** then deliberately opens a fresh gate for the current supported app.
- [ ] After acknowledging Drift, the current app becomes the first point of the next sequence. A fresh three-app sequence can trigger without an arbitrary cooldown.

### 6. Cross-feature switching torture sequences

Run these quickly; do not wait for UI to settle unless the step says to.

- [ ] Instagram active tunnel -> YouTube -> TikTok -> Instagram inside 60 seconds. Confirm tunnel quick-return, Drift, and Purpose Gate arbitration never stack or corrupt each other.
- [ ] Active tunnel -> incompatible surface -> Allow for now -> Home -> notification shade -> return to app. Remaining tunnel time and allowance state must both be coherent.
- [ ] Active tunnel -> detour check-in appears -> immediately Home -> notification shade -> return. Check-in must not appear over Home/SystemUI and must re-arm correctly in the protected app.
- [ ] Drift overlay -> immediately open notification shade -> close shade. Drift must remain a single valid episode; SystemUI must not become one of its apps.
- [ ] Drift overlay -> **Set an intention** -> choose purpose -> immediately hit an incompatible surface. Only the new tunnel's intervention may appear.
- [ ] Active tunnel -> Change purpose from notification -> while shade closes, switch apps. The stale chooser/action must not mutate an unrelated/new session.
- [ ] Directed Return -> immediately turn master protection OFF. No remaining route step or detector recapture may wake the old interaction.
- [ ] Directed Return -> immediately open another supported app. Old app navigation must stop when ownership package changes.
- [ ] Directed Return -> immediately press Home. The delayed route must cancel; Task Tunnel must not relaunch the protected app and steal foreground.
- [ ] Timed tunnel reaches expiry at roughly the same moment the user leaves the app. The result may be a fresh gate on return, but never an expired overlay over another app.
- [ ] Purpose Gate visible -> lock screen -> unlock. No Task Tunnel overlay may remain attached over keyguard; the gate restores once, only after the protected app owns foreground again.
- [ ] Check-in visible -> lock screen -> unlock within quick-return grace. It must be absent over keyguard and restore cleanly once; never duplicate.
- [ ] Tap a directed Return and immediately lock the phone. No delayed Back/click/app-launch step may continue behind keyguard; after unlock the coordinator must recover from fresh foreground evidence.
- [ ] Leave a tunnel app, keep phone locked/off long enough to exceed quick-return grace, unlock and return. Old tunnel must not resurrect.

## P0 — app-specific routing and detection

### Instagram

- [ ] Reply to messages selected while already inside a DM conversation: stay in that conversation.
- [ ] Messages tunnel: DM inbox/conversation allowed; Profile allowed; Reels corrected.
- [ ] Search tunnel: global Explore/Search allowed; Profile allowed; DM search must not masquerade as global Search.
- [ ] Search Return from Reels/nested screens reaches global Explore/Search and never backs out of Instagram.
- [ ] Post tunnel from Home/Profile/DM/Reels reaches Create/media picker.
- [ ] Visible Create/media-picker state is allowed; hidden stale Create nodes must not keep Home/Profile falsely classified as Create after leaving it.
- [ ] Repeated directed Return from odd nested screens never backgrounds Instagram.

### TikTok

- [ ] Search/watch selected from Profile routes through safe intermediate state to global Search.
- [ ] Inbox-local Search remains Inbox, not global Search.
- [ ] Profile/saved videos are not allowed as Search/watch simply because Search-like nodes exist there.
- [ ] Feed -> global Search -> result -> video remains coherent.
- [ ] Check Inbox from Feed/Friends/Profile/Search/nested screens reaches Inbox.
- [ ] Inbox recovery never repeatedly backs out of TikTok once the main shell is visible.
- [ ] Intermediate For You during directed routing does not create a self-inflicted Task Tunnel intervention.

### YouTube

- [ ] Search/watch: Search/results and full video are allowed; Shorts/Home are corrected as intended.
- [ ] Subscriptions -> Search -> results retains Search context until real top-level navigation clears it.
- [ ] A full watch page overrides stale Search/tab state.
- [ ] Subscriptions tunnel allows visible reliably subscribed creator video/Short.
- [ ] Reliably unsubscribed creator video/Short is corrected.
- [ ] Ambiguous/UNKNOWN creator subscription state fails open.
- [ ] Hidden stale Subscribe/Subscribed controls alone cannot decide creator subscription state.
- [ ] Hidden stale Shorts/player/Search nodes cannot override the actually visible top-level surface.
- [ ] Return to Subscriptions only completes after detector confirms the actual Subscriptions feed; intent launch success alone is insufficient.
- [ ] Repeated Subscriptions recovery never backs out of YouTube.

## P1 — lifecycle and device resilience

- [ ] Screen OFF/ON during open-ended tunnel. Check-in cadence pauses while locked and resumes from the remaining active-use time after unlock.
- [ ] Screen OFF/ON during timed tunnel. The explicit session duration remains wall-clock time; if it expires while locked, unlock shows one expiry state rather than resurrecting the session.
- [ ] Screen OFF/ON during temporary allowance. Lock longer than the allowance duration, then unlock; the allowance still has roughly the same active-use time remaining.
- [ ] Lock while a Purpose Gate/intervention/check-in is visible. No Task Tunnel overlay appears over keyguard, and the same unresolved prompt restores exactly once after unlock.
- [ ] Lock while a Drift check-in is visible for longer than Drift's quiet-reset window. The exact frozen Drift episode returns once after unlock; locking must not count as Keep going/dismissal.
- [ ] Force-stop Task Tunnel, reopen, and verify service/health state is safe.
- [ ] Disable/re-enable Accessibility service from Android settings.
- [ ] Reboot phone and confirm service health, master preference, configuration, and fresh runtime state.
- [ ] Open supported apps through notifications/deep links/external links.
- [ ] Picture-in-picture YouTube transition does not place an unrelated overlay over another app.
- [ ] Exercise split screen and document limitations; do not claim support that was not physically validated.
- [ ] Rotate where supported and test narrow display/font scaling. All primary/secondary actions remain reachable and unclipped.
- [ ] At the largest practical system font/display size, open Instagram's four-option Purpose Gate. If the sheet exceeds the screen it scrolls, and **Time limit** plus **Not now / Keep current purpose** remain reachable.
- [ ] At large font size, trigger intervention/check-in/expiry overlays and expand the active-tunnel notification. Action labels remain readable without fixed-height clipping.
- [ ] Repeat critical detector/routing smoke tests after Instagram, YouTube, or TikTok updates.

## P1 — Home, Attention, Review, Protection, Settings

- [ ] Home daily total and app ranking update after real use.
- [ ] Details expands surfaces with icons for every displayed `HomeSurfaceKind`; top categories plus `Other` remain readable.
- [ ] Surface usage does not accrue while Task Tunnel overlay is covering the target app.
- [ ] Surface usage does not accrue while screen is off.
- [ ] Master OFF does not keep attributing the last pre-pause surface after protection is re-enabled.
- [ ] Attention groups sessions chronologically under Today / Yesterday / actual dates.
- [ ] Normal sessions remain quiet: app + purpose + duration/time, with extra text only for exceptional events.
- [ ] Live tunnel appears once as a Live row and disappears/reconciles when it ends.
- [ ] Drift path in Attention contains at least three trigger apps and matches the frozen episode.
- [ ] Episode detail reads like a short human story, not an internal event log.
- [ ] Review shows at most a few high-confidence insights and is allowed to be empty.
- [ ] Common detour/recovery behavior is not duplicated into multiple near-identical insights.
- [ ] Raw seven-day counts stay behind the disclosure.
- [ ] Protection remains consumer-facing; Android/accessibility diagnostics are hidden unless broken or Advanced is opened.
- [ ] Settings sections remain Everyday / Privacy / activity history / Advanced / About without developer terminology leaking into normal UI.

## P1 — privacy and release integrity

- [ ] Inspect release manifest: no INTERNET permission, no feedback/Formspree backend, no analytics SDK/account dependency.
- [ ] Run `python scripts/release_preflight.py --allow-placeholder-id` during beta hardening; before Play upload run it again without the override and require PASS.
- [ ] Accessibility service remains `isAccessibilityTool=false` and the service description names Instagram, YouTube, and TikTok appropriately.
- [ ] Onboarding requires affirmative disclosure consent before opening Accessibility settings and provides a clear **Not now** path.
- [ ] Normal UI never exposes detector confidence, resource IDs, package IDs, node trees/counts, fingerprints, or raw accessibility text.
- [ ] Diagnostics/fingerprints never contain message text, search text, video/channel titles, usernames, or other arbitrary accessibility text.
- [ ] Clear activity history, cancel once, then confirm. After confirmed clear, old queued writes must not reappear.
- [ ] Backup/data-extraction rules exclude local history database/WAL/SHM as intended.
- [ ] Release build does not expose debug inspector, developer fingerprint controls, raw package transitions, visual-QA overlay, or test controls.
- [ ] Permanent application ID decision is made before Play upload; do not ship `com.example.tasktunnel` as the production identity.
- [ ] Signed AAB, upload key, privacy-policy URL, store listing, Data Safety answers, Accessibility declaration/video, and closed testing are complete before production review.

## Release decision

A release candidate is acceptable when:

1. Every P0 item passes on the primary physical test device.
2. No known BLOCKER remains open.
3. Instagram/YouTube/TikTok versions used for the run are recorded.
4. P1 lifecycle/privacy checks have either passed or have an explicitly documented device-specific limitation that does not violate core product behavior or privacy.
5. The exact candidate APK/AAB being distributed is the build that was tested.
