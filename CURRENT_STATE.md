# Current State

## M1 YouTube surface-detection proof

Status: implementation present; focused detector tests and debug assembly pass; physical-phone validation is pending. M1 is not complete until `MANUAL_TEST_M1.md` is run and the user records a go/no-go decision.

- Added a pure deterministic detector over immutable sanitized nodes. Its stable outputs are `YOUTUBE_SHORTS`, `YOUTUBE_VIDEO`, `YOUTUBE_SEARCH`, `YOUTUBE_OTHER`, and fail-open `UNKNOWN`.
- Detection uses exact normalized resource-ID segments plus bounded class/structure/flag signals. It never uses text, content descriptions, screenshots, or raw trees. Candidate IDs remain unverified against a physical YouTube build.
- Shorts requires two Shorts-specific IDs. Conflicting specialized evidence returns `UNKNOWN`; non-YouTube packages return `UNKNOWN` at zero confidence.
- The service now runs one bounded sanitizer/capture path for active YouTube snapshots even when the debug inspector is not armed. Content/scroll updates use a one-second throttle with a trailing capture.
- Current detection clears when leaving YouTube, the root is unavailable, or the service disconnects. The last observed YouTube result remains bounded in memory for the developer UI and is never persisted.
- The developer UI displays current or clearly labeled last-observed surface, evidence-strength confidence, capture time, package, and bounded strongest sanitized signals.
- Nine focused JVM detector tests passed, covering strong Shorts, two-ID and structural video, two-ID and structural search, conflicting evidence, weak Shorts evidence, non-YouTube input, and generic YouTube shell.
- `:app:assembleDebug` passed. APK: `app\build\outputs\apk\debug\app-debug.apk`.
- Physical matrix: all cases NOT RUN. Main risk is YouTube resource-ID variation across versions/accounts/experiments; any non-Shorts → Shorts result is a no-go severity failure.

M0: complete per user-provided device validation; baseline retained.
