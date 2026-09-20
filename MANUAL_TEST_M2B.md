# M2B Instagram surface detector manual validation

Use a debug build with Task Tunnel's accessibility service enabled. For every row, wait at least one second after the Instagram surface has fully loaded, return to Task Tunnel, and inspect the last result. Record the exact observed surface and evidence strength. `UNKNOWN` is preferable to a false positive.

| Scenario | Expected | Actual | Confidence | UNKNOWN | False positive | Pass | Notes |
|---|---|---|---|---|---|---|---|
| Home | `INSTAGRAM_HOME` | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | |
| DM inbox | `INSTAGRAM_MESSAGES` | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | |
| DM conversation | `INSTAGRAM_MESSAGES` | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | |
| Explore | `INSTAGRAM_EXPLORE` | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | |
| Reels tab | `INSTAGRAM_REELS` | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | |
| Reel from Explore | `INSTAGRAM_REELS` | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | |
| Back navigation | Destination surface | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | |
| Profile | `INSTAGRAM_OTHER` | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | |
| Repeated Messages / Explore / Reels | Matching surface each time | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | |
| Background / resume | Resumed surface | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | |
| Cold restart | Loaded surface | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | |

Highest-severity failures are legitimate Messages, Home, or Profile surfaces classified as `INSTAGRAM_REELS` or `INSTAGRAM_EXPLORE`. Preserve the sanitized fingerprint and notes for any mismatch; do not record message text, content descriptions, screenshots, or other user content.
