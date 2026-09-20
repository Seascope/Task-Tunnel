# M6 Manual Hardening Matrix

Record device, Android build, Task Tunnel build, Instagram version, YouTube version, Reddit version, date, and tester for every run. Automated implementation does not satisfy these physical checks.

## First run

- [ ] Fresh install or clear app data. Confirm the value proposition appears before any Android permission UI.
- [ ] Advance through how it works. Confirm Return, Allow anyway, End Tunnel, intentional browsing, and fail-open wording are understandable.
- [ ] Confirm the prominent privacy/Accessibility disclosure answers what can be seen, why, what is stored/not stored, and where processing occurs.
- [ ] Confirm Android Accessibility settings opens only after **Continue to Accessibility settings**.
- [ ] Decline or leave settings without enabling. Confirm verification says access is off and allows continued setup.
- [ ] Finish without access. Confirm Attention remains usable, Protection says **Protection off**, and a repair action exists.
- [ ] Enable access later. Return automatically and confirm Protection changes without restarting the app.
- [ ] Exit onboarding on each step and reopen. Confirm the saved step is reasonable and no permission screen launches automatically.
- [ ] Finish onboarding, force-stop/reopen, and confirm onboarding does not repeat.
- [ ] Enable the service externally before returning to a saved verification step. Confirm the app recognizes access.

## Permission repair and health

- [ ] With protection active, disable Task Tunnel externally. Reopen/resume and confirm **Protection off**.
- [ ] Tap **Enable Accessibility access**. Confirm the in-app disclosure appears before Android settings.
- [ ] Re-enable, return, and confirm **Protection active** when the service connects.
- [ ] Exercise an enabled-but-disconnected/restarting state if reproducible. Confirm **Limited protection**, what may fail, what still works, and small background guidance.
- [ ] Confirm no single `UNKNOWN` surface produces a warning or blocks use.
- [ ] Confirm a service restart clears transient Tunnel state safely and resumes with a new Purpose Gate.

## Protection and configuration

- [ ] Confirm Protection presents in order: Is it working, What is protected, How is it configured.
- [ ] Confirm Instagram lists Reply to messages and Browse intentionally; no per-surface blocking switches appear.
- [ ] Confirm YouTube lists Search / watch something specific and Browse intentionally; no per-surface blocking switches appear.
- [ ] Confirm missing supported apps are shown calmly as not installed and do not falsely limit the installed app.
- [ ] Confirm installed Instagram/YouTube/Reddit versions appear correctly in diagnostics.
- [ ] Because historic validated versions were not recorded, confirm versions report **version not yet verified** rather than incompatible.
- [ ] Toggle Drift off. Confirm it remains off across reopen and no Drift check-in appears.
- [ ] Toggle Drift on and select from only Instagram, YouTube, and Reddit. Confirm selection persists and existing threshold behavior is unchanged.

## Runtime regression

- [ ] Instagram > Reply to messages > Messages works without intervention.
- [ ] Instagram > Reply to messages > Reels shows intervention.
- [ ] Instagram > Reply to messages > Explore shows intervention.
- [ ] YouTube > Search / watch something specific > Search and normal video work.
- [ ] YouTube > Search / watch something specific > Shorts shows intervention.
- [ ] **Return** dismisses and performs one Back action without a loop.
- [ ] **Allow anyway** permits the same incompatible surface for the existing five-minute beta window.
- [ ] **End Tunnel** ends without closing the supported app.
- [ ] Timed expiry presents Finish, Continue, and Choose another purpose; background expiry stays silent.
- [ ] Return inside the existing 60-second grace preserves the Tunnel; after grace, the next open receives a fresh gate.
- [ ] Drift forms only from three distinct selected apps within the existing window and shows one soft check-in per episode.
- [ ] Attention events persist through process recreation and remain understandable.

## Process and lifecycle

- [ ] Force-stop/reopen Task Tunnel. Confirm state is safe and Protection health settles correctly.
- [ ] Reboot the phone. Confirm access/service health, repair copy, and fresh Purpose Gate behavior.
- [ ] Background/resume Task Tunnel and supported apps repeatedly. Confirm no repeated permission launch or stale overlay.
- [ ] Kill Task Tunnel while onboarding is incomplete, reopen, and confirm the flow is recoverable.

## Android UI modes

- [ ] Split screen: exercise Task Tunnel with Instagram and YouTube; record detector/overlay limitations without claiming support that was not observed.
- [ ] Picture-in-picture: leave/return from a YouTube video and confirm no unrelated overlay is placed over another app.
- [ ] Open supported apps through a notification deep link and an external app link. Confirm Purpose Gate/Tunnel behavior remains coherent.
- [ ] Rotate or use a narrow screen where supported. Confirm onboarding, Protection, Settings, dialogs, and intervention actions remain reachable.

## App updates and compatibility

- [ ] Record installed Instagram and YouTube versions shown by diagnostics before testing.
- [ ] Repeat all supported-surface smoke tests after either app updates.
- [ ] Add a version to the verified registry only after its detector matrix passes; never infer a version range from one version.
- [ ] Confirm a newer unrecorded version stays usable and fail-open rather than hard-disabled.
- [ ] If a known persistent incompatibility is deliberately seeded in a test build, confirm **Limited protection** and app-specific human copy.

## OEM validation

- [ ] Pixel-class device: full release-candidate matrix. Status: PHYSICAL VALIDATION REQUIRED.
- [ ] Samsung-class device: full release-candidate matrix. Status: EXTERNAL BETA PENDING unless a device is available.
- [ ] On each OEM, observe service behavior after backgrounding/reboot. Add battery/autostart guidance only if failure evidence requires it.

## Privacy and data controls

- [ ] Inspect all normal release UI. Confirm there is no detector confidence, package ID, resource ID, node class/count, tree, fingerprint, or raw detector evidence.
- [ ] Copy diagnostics and inspect every line. Confirm no message/accessibility text, username, screenshot, raw tree/fingerprint, identifier, or Attention history content appears.
- [ ] Create Attention history, tap **Clear Attention history**, cancel once, then confirm clearing. Confirm Attention returns cleanly to its empty state.
- [ ] Confirm backup/data-extraction rules in the merged release manifest/resource package exclude the Attention database, WAL, and SHM.

## Release build

- [ ] Install and launch the release candidate without relying on debug signing assumptions.
- [ ] Confirm Developer options, YouTube/Instagram fingerprints, sanitized inspector, raw package transitions, confidence, and test overlay are inaccessible.
- [ ] Confirm production-safe Protection diagnostics remain available and copy correctly.
- [ ] Confirm onboarding/disclosure copy matches `ACCESSIBILITY_DISCLOSURE.md` and the Play declaration draft.
- [ ] Confirm application ID, public name, version, signing, privacy-policy URL, and store assets have received human decisions before publication.
