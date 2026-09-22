# TikTok Detector Evidence

## Scope

This document is the handoff for TikTok T1 detector research and records the
implemented evidence-backed rules. They were derived from TikTok 47.0.3 only;
they are not proven across other versions.

T2 now consumes these semantic surfaces through the existing Surface Usage,
Home, Attention, Review, Drift, and Protection systems. No new TikTok-specific
storage or analytics pipeline was added.

- Package: `com.zhiliaoapp.musically`
- Installed version: 47.0.3 (versionCode 2024700030)
- Evidence source: bounded sanitized accessibility hierarchy
- Persistence: none; captures remain in memory for the service session

## Manual Workflow

1. Open Task Tunnel Settings and enter **Developer options**.
2. Open **sanitized inspector** and arm **Capture supported app**.
3. Open TikTok and wait for the inspector status to become captured.
4. Navigate to one surface and wait for a fresh capture timestamp.
5. Return to Task Tunnel, open Developer diagnostics, and inspect or copy the latest TikTok fingerprint.
6. Record only structural evidence below, then repeat for the next surface.

## Surfaces To Inspect

| Surface hypothesis | Installed-version evidence | Stable visible IDs/classes | Selected nodes | Ambiguity / notes |
|---|---|---|---|---|
| Main video feed / For You |  |  |  |  |
| Following or alternate feed |  |  |  |  |
| Search entry/results |  |  |  |  |
| Inbox / notifications / messages |  |  |  |  |
| Profile |  |  |  |  |
| LIVE |  |  |  |  |
| Shop |  |  |  |  |
| Other distinct destination |  |  |  |  |

## Evidence Notes

### Strong signatures

- Search is evaluated first. Entry uses visible/editable `hu0` or its captured
	supporting `tv_search_textview`/`lkh` structure.
- Search results use visible `viewpager_search`, or visible/editable `hu0` with
	`pzk` or `nhr` evidence.
- Search-origin video uses visible `tv_bar_search` or `tv_search_sug_word`, or
	visible `o8c` with search context. This wins over generic feed/video IDs.
- Inbox, Profile, Friends, and For You use the selected visible/clickable
	`omr`, `oms`, `omp`, or `omq` node respectively. `omy` is supporting
	navigation context, not a mandatory parent-index gate.
- A visible `omy` without a recognized selected destination is `TIKTOK_OTHER`.

### Weak signatures

Generic feed/video IDs such as `ewa`, `bql`, `ep7`, `g75`, `i7r`, and `png`
are supporting context only and are not sufficient to classify a surface.

### Rejected signatures

Search-result video feed IDs are rejected as Feed evidence when search-origin
IDs are present. Content text, usernames, captions, URLs, and message data are
never detector inputs.

### Open Questions

- Which nodes remain after changing tabs or destinations?
- Which selected/clickable/scrollable nodes correspond to the visible destination?
- Does the installed version expose distinct structures for LIVE or Shop?
- Inbox conversations remain classified as Inbox while `omr` remains selected;
	conversation-specific IDs are not required.