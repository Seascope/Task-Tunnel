# M5 Attention Debugger — Physical Test Guide

## Preconditions

- Install the M5 debug APK and enable the Task Tunnel accessibility service.
- Keep Instagram, YouTube, and Reddit selected in the Drift pool.
- Use test activity that does not reveal private content while another person can see the device.
- Open **Developer diagnostics → Clear Attention history** once if a clean timeline is useful.

## Task Tunnel timeline — Instagram

1. Open Instagram and select **Reply to messages** with **No limit**.
2. Enter Messages or a conversation.
3. Enter Reels.
4. At the Task Tunnel intervention, tap **Return**.
5. Confirm Instagram returns to the previous surface, then trigger an intervention again and tap **End Tunnel**.
6. Open the Task Tunnel app.
7. Confirm **Attention** is the default screen and one Instagram episode explains the sequence in chronological order.
8. Open the episode and verify the detail includes the declared intention, the meaningful surface transition, the intervention, Return, and End Tunnel where recorded.

Expected:

- The sequence is readable as an account of what happened, rather than a raw event log.
- App, task, surface, intervention, and choice use human language.
- Ordinary allowed-surface accessibility updates do not flood the timeline.

## Allow Anyway

1. Start an Instagram **Reply to messages** Tunnel.
2. Enter Reels and tap **Allow anyway**.
3. Open the resulting Attention episode.

Expected:

- **Allow anyway** appears as a neutral conscious choice.
- It is not styled or described as a failure.
- The existing temporary allowance behavior still works.

## YouTube

1. Open YouTube and select **Search / watch something specific**.
2. Visit Search or a long video, then enter Shorts.
3. Choose **Return** or **Allow anyway**.
4. Open Task Tunnel and inspect the YouTube episode.

Expected:

- The chronology shows the YouTube intention, meaningful Search/Video/Shorts movement, intervention, and chosen response.
- Repeated detector captures on the same surface do not create duplicate rows.

## Timed-session expiry

1. Start a short timed Tunnel and remain in the supported app until it expires.
2. Test **Continue** and inspect the episode.
3. Repeat with **Finish**, then with **Choose another purpose**.

Expected:

- The neutral expiry intervention and each expiry choice appear in the correct Tunnel episode.
- Continue does not create an immediate expiry loop.
- The native app remains open.

## Drift — Keep going

1. Switch quickly through Instagram → Reddit → YouTube within the configured Drift window.
2. Confirm exactly one soft Drift check-in appears.
3. Tap **Keep going**.
4. Open Task Tunnel and select the Drift episode.

Expected:

- The episode summary uses **Instagram → Reddit → YouTube**.
- The detail shows the observed sequence, Drift check-in, and **Keep going** decision.
- Package identifiers are not shown.

## Drift — Set an intention

1. Trigger a Drift sequence that ends on Instagram or YouTube.
2. Tap **Set an intention**.
3. Choose a purpose in the existing Purpose Gate.
4. Open Attention.

Expected:

- The Drift episode contains the Set an intention decision.
- The newly selected Task Tunnel appears as a separate Task Tunnel episode.
- No purpose was selected automatically.

## Persistence

1. Create at least one Task Tunnel episode and one Drift episode.
2. Force-stop the Task Tunnel app without clearing its data.
3. Reopen Task Tunnel.

Expected:

- Attention history remains present.
- Episode order and detail remain understandable after process restart.

## Noise control

1. Start an allowed Tunnel.
2. Navigate and scroll normally on the same allowed surface for several minutes.
3. Open Attention and inspect the episode.

Expected:

- The episode is not flooded by repeated accessibility events.
- `UNKNOWN` detector results do not create misleading transitions or interventions.

## Active Tunnel context

1. Start a Task Tunnel.
2. Manually open the Task Tunnel app during the active session.

Expected:

- Attention shows a quiet active-Tunnel context near the top.
- No persistent runtime timer, glowing focus widget, or separate Focus destination appears.

## Privacy and production language

Inspect the Attention episode list, detail, empty state, and weekly Drift summary.

Expected:

- History remains available with the device offline.
- Normal Attention UI does not reveal package IDs, resource IDs, detector confidence, accessibility node classes, fingerprints, raw trees, or millisecond timestamps.
- There is no score, grade, streak, XP, shame language, cloud account, or upload control.
- Developer diagnostics remain a separate debug-only destination.

## Clear history — debug build

1. Open **Developer diagnostics**.
2. Tap **Clear Attention history**.
3. Return to Attention.

Expected:

- Recent episodes are removed.
- The calm empty-state explanation appears.
- Runtime protection and Drift configuration remain unchanged.

